package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityAirplaneTMob;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.BuffHelper;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.PacketHelper;
import com.lulan.shincolle.utility.TaskHelper;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipEffectMapParityGameTests {
    private static final String BATCH = "isolated_ship_effect_map";
    private static final int LONG = 20_000;

    /** Legacy protocol ids 1..27 and the effect each stands for, written out independently of production. */
    private static final MobEffect[] LEGACY = {
            null,
            MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SPEED,
            MobEffects.DIG_SLOWDOWN, MobEffects.DAMAGE_BOOST, MobEffects.HEAL,
            MobEffects.HARM, MobEffects.JUMP, MobEffects.CONFUSION,
            MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE,
            MobEffects.WATER_BREATHING, MobEffects.INVISIBILITY, MobEffects.BLINDNESS,
            MobEffects.NIGHT_VISION, MobEffects.HUNGER, MobEffects.WEAKNESS,
            MobEffects.POISON, MobEffects.WITHER, MobEffects.HEALTH_BOOST,
            MobEffects.ABSORPTION, MobEffects.SATURATION, MobEffects.GLOWING,
            MobEffects.LEVITATION, MobEffects.LUCK, MobEffects.UNLUCK
    };
    private static final int RESISTANCE = 11;
    private static final int FIRE_RESISTANCE = 12;
    private static final int NIGHT_VISION = 16;
    private static final int LUCK = 26;

    private ShipEffectMapParityGameTests() {
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void activeEffectsRebuildAllLegacyBuffIds(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            helper.assertTrue(LEGACY.length == 28, "Fixture: 27 legacy pairs");
            for (int id = 1; id <= 27; id++) {
                ship.addEffect(new MobEffectInstance(LEGACY[id], LONG, id));
                MobEffectInstance applied = ship.getEffect(LEGACY[id]);
                helper.assertTrue(applied != null && applied.getAmplifier() == id,
                        "Fixture: legacy id " + id + " is applied with amplifier " + id);
            }
            ship.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, LONG, 3));
            ship.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LONG, 4));
            helper.assertTrue(ship.getEffect(MobEffects.DOLPHINS_GRACE) != null
                    && ship.getEffect(MobEffects.SLOW_FALLING) != null, "Fixture: modern effects applied");
            Map<Integer, Integer> map = ship.getBuffMap();
            for (int id = 1; id <= 27; id++) {
                helper.assertTrue(map.containsKey(id) && map.get(id) == id,
                        "Legacy id " + id + " maps to its amplifier, got " + map.get(id));
                helper.assertTrue(BuffHelper.getPotionLevel(map, id) == id + 1, "Level is amplifier + 1 for id " + id);
            }
            helper.assertTrue(map.size() == 27, "Modern effects get no legacy id, size=" + map.size());
            helper.assertTrue(ship.getEffect(MobEffects.DOLPHINS_GRACE) != null, "The real effect is kept");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void expiredRemovedAndHiddenEffectsReplaceSnapshot(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            ship.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LONG, 0));
            ship.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 3));
            MobEffectInstance strong = ship.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(strong != null && strong.getAmplifier() == 3 && strong.getDuration() == 5,
                    "Fixture: strong short effect on top of a hidden weak one");
            helper.assertTrue(ship.getBuffMap().get(RESISTANCE) != null && ship.getBuffMap().get(RESISTANCE) == 3,
                    "The visible effect is mapped in the same tick it is applied");
            for (int i = 0; i < 5; i++) {
                strong.tick(ship, () -> { });
            }
            MobEffectInstance weak = ship.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(weak != null && weak.getAmplifier() == 0, "Fixture: the hidden effect surfaced");
            helper.assertTrue(ship.getBuffMap().get(RESISTANCE) != null && ship.getBuffMap().get(RESISTANCE) == 0,
                    "The map falls back to the surfaced effect, got " + ship.getBuffMap());
            ship.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(ship.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, "Fixture: effect removed");
            helper.assertTrue(!ship.getBuffMap().containsKey(RESISTANCE), "Removal drops the key at once");
            ship.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 0));
            ship.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 2));
            helper.assertTrue(ship.getBuffMap().size() == 2, "Two effects, two keys");
            ship.removeAllEffects();
            helper.assertTrue(ship.getActiveEffects().isEmpty(), "Fixture: no effects left");
            helper.assertTrue(ship.getBuffMap().isEmpty(), "No effects, an empty map replaces the old one");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void resistanceBuffReducesMissileCustomDamage(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            BasicEntityShipHostile attacker = hostile(helper, entities);
            EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
            helper.assertTrue(missile != null, "Fixture: missile");
            DamageSource viaMissile = helper.getLevel().damageSources().thrown(missile, attacker);
            float baseline = observedHurt(ship, viaMissile, 7L);
            helper.assertTrue(baseline > 0F, "Fixture: an undisturbed hit reaches the damage event");
            int[] amplifiers = {0, 1, 3, 6};
            float[] expected = {80F, 60F, 20F, 20F};
            for (int i = 0; i < amplifiers.length; i++) {
                ship.removeAllEffects();
                ship.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LONG, amplifiers[i]));
                helper.assertTrue(ship.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == amplifiers[i],
                        "Fixture: resistance amplifier");
                float custom = BuffHelper.applyBuffOnDamageByResist(ship, viaMissile, 100F);
                helper.assertTrue(Math.abs(custom - expected[i]) < 0.001F,
                        "Amplifier " + amplifiers[i] + " custom damage " + custom + " expected " + expected[i]);
                float seen = observedHurt(ship, viaMissile, 7L);
                helper.assertTrue(Math.abs(seen / baseline - expected[i] / 100F) < 0.01F,
                        "Real hit at amplifier " + amplifiers[i] + " ratio " + seen / baseline);
            }
            ship.removeAllEffects();
            ship.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 1));
            helper.assertTrue(Math.abs(observedHurt(ship, viaMissile, 7L) / baseline - 1F) < 0.01F,
                    "Fire resistance alone does not reduce a missile hit");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void fireResistanceBuffReducesOnlyNonMissileShipCustomDamage(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            BasicEntityShipHostile attacker = hostile(helper, entities);
            EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
            helper.assertTrue(missile != null, "Fixture: missile");
            DamageSource direct = helper.getLevel().damageSources().mobAttack(attacker);
            DamageSource viaMissile = helper.getLevel().damageSources().thrown(missile, attacker);
            float baseline = observedHurt(ship, direct, 11L);
            helper.assertTrue(baseline > 0F, "Fixture: an undisturbed hit reaches the damage event");
            ship.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 1));
            helper.assertTrue(ship.getEffect(MobEffects.FIRE_RESISTANCE).getAmplifier() == 1, "Fixture: amplifier 1");
            helper.assertTrue(Math.abs(BuffHelper.applyBuffOnDamageByResist(ship, direct, 100F) - 60F) < 0.001F,
                    "A direct ship hit is cut from 100 to 60");
            helper.assertTrue(Math.abs(BuffHelper.applyBuffOnDamageByResist(ship, viaMissile, 100F) - 100F) < 0.001F,
                    "A missile hit is not touched by fire resistance");
            helper.assertTrue(Math.abs(observedHurt(ship, direct, 11L) / baseline - 0.6F) < 0.01F,
                    "Real direct hit ratio");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void attackerNightVisionChangesCustomLightDamage(GameTestHelper helper) {
        fixture(helper, true, (ship, entities) -> {
            BasicEntityShipHostile attacker = hostile(helper, entities);
            DamageSource direct = helper.getLevel().damageSources().mobAttack(attacker);
            int light = ship.level().getMaxLocalRawBrightness(ship.blockPosition());
            helper.assertTrue(light <= 2, "Fixture: a sealed dark room, light " + light);
            int atkType = attacker.getDamageType();
            int defType = ship.getDamageType();
            helper.assertTrue(atkType > 0 && defType > 0, "Fixture: both sides have a damage type");
            float plain = CombatHelper.modDamageByLight(100F, atkType, defType, 0F);
            float withNightVision = CombatHelper.modDamageByLight(100F, atkType, defType, 0.8F);
            helper.assertTrue(Math.abs(plain - withNightVision) > 1F,
                    "Fixture: the table differs between night and night vision, " + plain + " " + withNightVision);
            float baseline = observedHurt(ship, direct, 5L);
            helper.assertTrue(baseline > 0F, "Fixture: an undisturbed hit reaches the damage event");
            ship.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 0));
            helper.assertTrue(Math.abs(observedHurt(ship, direct, 5L) / baseline - 1F) < 0.01F,
                    "Night vision on the defender changes nothing");
            ship.removeAllEffects();
            attacker.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 0));
            helper.assertTrue(attacker.getEffect(MobEffects.NIGHT_VISION) != null, "Fixture: attacker effect");
            float lit = BuffHelper.applyBuffOnDamageByLight(ship, direct, 100F);
            helper.assertTrue(Math.abs(lit - withNightVision) < 0.01F,
                    "Helper uses the attacker's night vision, got " + lit + " expected " + withNightVision);
            float seen = observedHurt(ship, direct, 5L);
            helper.assertTrue(Math.abs(seen / baseline - withNightVision / plain) < 0.01F,
                    "Real hit ratio " + seen / baseline + " expected " + withNightVision / plain);
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void hostileAndSummonMapsUseCurrentHostEffects(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            BasicEntityShipHostile enemy = hostile(helper, entities);
            enemy.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 2));
            helper.assertTrue(enemy.getEffect(MobEffects.NIGHT_VISION) != null, "Fixture: enemy effect");
            helper.assertTrue(enemy.getBuffMap().get(NIGHT_VISION) != null && enemy.getBuffMap().get(NIGHT_VISION) == 2,
                    "The enemy map follows its effect, got " + enemy.getBuffMap());

            EntityAirplaneTMob plane = entities.add(ModEntities.AIRPLANE_T_MOB.get().create(helper.getLevel()));
            helper.assertTrue(plane != null, "Fixture: aircraft");
            plane.initAttrs(enemy, null, 1, (float) enemy.getY());
            plane.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LONG, 1));
            helper.assertTrue(plane.getBuffMap().get(NIGHT_VISION) != null && plane.getBuffMap().get(NIGHT_VISION) == 2,
                    "An aircraft reads its host's map");
            helper.assertTrue(!plane.getBuffMap().containsKey(RESISTANCE),
                    "The aircraft's own effect does not flow into the map");
            helper.assertTrue(!enemy.getBuffMap().containsKey(RESISTANCE), "...nor back to the host");

            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            helper.assertTrue(mount != null, "Fixture: mount");
            ship.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 3));
            mount.setHost(ship);
            helper.assertTrue(mount.getBuffMap().get(FIRE_RESISTANCE) != null
                    && mount.getBuffMap().get(FIRE_RESISTANCE) == 3, "A mount reads its host's map");
            mount.setHost(null);
            helper.assertTrue(mount.getBuffMap().isEmpty(), "Without a host the mount map is empty again");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void luckEffectFeedsFishingWithoutDirectMapWrites(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            ship.addEffect(new MobEffectInstance(MobEffects.LUCK, LONG, 99));
            helper.assertTrue(ship.getEffect(MobEffects.LUCK).getAmplifier() == 99, "Fixture: luck amplifier 99");
            helper.assertTrue(ship.getBuffMap().get(LUCK) != null && ship.getBuffMap().get(LUCK) == 99,
                    "Luck maps to id 26 with amplifier 99, got " + ship.getBuffMap());
            helper.assertTrue(BuffHelper.getPotionLevel(ship, LUCK) == 100, "Luck level is 100");
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            Set<net.minecraft.world.item.Item> treasures = Set.of(Items.BOW, Items.ENCHANTED_BOOK,
                    Items.FISHING_ROD, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE);
            TaskHelper.generateFishingResult(ship);
            boolean treasure = false;
            for (int i = 0; i < ship.getCapaShipInventory().getSlots(); i++) {
                if (i != 22 && treasures.contains(ship.getCapaShipInventory().getStackInSlot(i).getItem())) {
                    treasure = true;
                }
            }
            helper.assertTrue(treasure, "At luck 100 fish/junk weights are zero, so a treasure must be selected");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void buffMapWireRoundTripsEmptyAndNonEmptySnapshots(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            ship.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LONG, 2));
            ship.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 0));
            Map<Integer, Integer> sent = decode(S2CEntitySyncPacket.syncBuffMap(ship));
            helper.assertTrue(sent.size() == 2 && sent.get(RESISTANCE) != null && sent.get(RESISTANCE) == 2
                    && sent.get(NIGHT_VISION) != null && sent.get(NIGHT_VISION) == 0,
                    "The packet carries the current effects, got " + sent);
            ship.removeAllEffects();
            helper.assertTrue(decode(S2CEntitySyncPacket.syncBuffMap(ship)).isEmpty(),
                    "An empty packet replaces the old map");

            ship.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LONG, 2));
            ship.getBuffMap();
            helper.assertTrue(pending(ship), "A changed map is marked for a periodic send");
            ship.tickCount = 16;
            ship.tick();
            helper.assertTrue(ship.tickCount % 16 == 0, "Fixture: the tick sits on a 16-tick boundary");
            helper.assertTrue(!pending(ship), "The periodic step sent the change and cleared the mark");
            ship.tickCount = 32;
            ship.tick();
            helper.assertTrue(!pending(ship), "No change, nothing new to send");
            ship.removeAllEffects();
            ship.getBuffMap();
            helper.assertTrue(pending(ship), "Clearing the map also needs a send");
            ship.tickCount = 48;
            ship.tick();
            helper.assertTrue(!pending(ship), "The empty map was sent and the mark cleared");
        });
    }

    @GameTest(template = "arena", batch = BATCH)
    public static void buffMapsRebuildAcrossLoadDimensionAndDeath(GameTestHelper helper) {
        fixture(helper, false, (ship, entities) -> {
            ship.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LONG, 2));
            CompoundTag saved = new CompoundTag();
            ship.saveWithoutId(saved);
            BasicEntityShip loaded = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(loaded != null, "Fixture: second ship");
            loaded.load(saved);
            helper.assertTrue(loaded.getEffect(MobEffects.MOVEMENT_SPEED) != null, "Fixture: effect restored by load");
            helper.assertTrue(loaded.getBuffMap().get(1) != null && loaded.getBuffMap().get(1) == 2,
                    "The map is rebuilt from the loaded effect, got " + loaded.getBuffMap());

            BasicEntityShip fresh = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(fresh != null && fresh.getActiveEffects().isEmpty(), "Fixture: a new ship has no effects");
            helper.assertTrue(fresh.getBuffMap().isEmpty(), "A new ship without effects has an empty map");

            HashMap<Integer, Integer> forged = new HashMap<>();
            forged.put(RESISTANCE, 5);
            ship.setBuffMap(forged);
            helper.assertTrue(ship.getBuffMap().get(1) != null && ship.getBuffMap().get(1) == 2
                    && !ship.getBuffMap().containsKey(RESISTANCE), "A stored map never outranks the real effects");

            ship.setNoAi(true);
            ship.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 0));
            ship.tickCount = 16;
            ship.tick();
            helper.assertTrue(pending(ship) || ship.getBuffMap().containsKey(NIGHT_VISION),
                    "The map keeps following effects while the ship is idle");
            helper.assertTrue(ship.getBuffMap().containsKey(NIGHT_VISION), "The new effect is in the map");

            ship.setHealth(0F);
            helper.assertTrue(!ship.isAlive(), "Fixture: dead ship");
            helper.assertTrue(ship.getBuffMap().isEmpty(), "A dead ship keeps no old keys");
        });
    }

    // ---- fixtures ----

    private static BasicEntityShipHostile hostile(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShipHostile enemy = entities.add(ModEntities.BB_KONGOU_MOB.get().create(helper.getLevel()));
        helper.assertTrue(enemy != null, "Fixture: enemy ship");
        enemy.setNoAi(true);
        enemy.setNoGravity(true);
        enemy.setInvulnerable(true);
        Vec3 at = helper.absoluteVec(new Vec3(4.5D, 4D, 4.5D));
        enemy.moveTo(at);
        helper.assertTrue(helper.getLevel().addFreshEntity(enemy), "Fixture: add enemy");
        GameTestEntities.assertRegistered(helper, enemy);
        return enemy;
    }

    /** The amount a real hit carries into the damage event, with the random stream pinned. */
    private static float observedHurt(BasicEntityShip ship, DamageSource source, long seed) {
        HurtProbe probe = new HurtProbe(ship);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.DEF, 0F);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
        ship.setInvulnerable(false);
        ship.invulnerableTime = 0;
        ship.getRandom().setSeed(seed);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            ship.hurt(source, 100F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            ship.setInvulnerable(true);
            ship.invulnerableTime = 0;
        }
        return probe.amount;
    }

    public static final class HurtProbe {
        private final BasicEntityShip target;
        private float amount = Float.NaN;

        HurtProbe(BasicEntityShip target) {
            this.target = target;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onHurt(LivingHurtEvent event) {
            if (event.getEntity() == this.target) {
                this.amount = event.getAmount();
                event.setCanceled(true);
            }
        }
    }

    private static Map<Integer, Integer> decode(S2CEntitySyncPacket packet) {
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(wire);
        wire.readByte();
        wire.readInt();
        wire.readVarInt();
        byte[] payload = wire.readByteArray();
        return PacketHelper.readIntMap(new FriendlyByteBuf(Unpooled.wrappedBuffer(payload)));
    }

    private static boolean pending(BasicEntityShip ship) {
        try {
            Field field = BasicEntityShip.class.getDeclaredField("buffMapSyncPending");
            field.setAccessible(true);
            return field.getBoolean(ship);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("The pending mark is missing: " + error.getMessage());
        }
    }

    private static void fixture(GameTestHelper helper, boolean darkRoom,
                                BiConsumer<BasicEntityShip, GameTestEntities> check) {
        Vec3 relative = new Vec3(4.5D, 4D, 5.5D);
        Runnable body = () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                helper.assertTrue(ship != null, "Fixture: create ship");
                ship.setNoAi(true);
                ship.setNoGravity(true);
                ship.setInvulnerable(true);
                ship.setEntitySit(false);
                ship.setStateFlag(ID.F.CanFollow, false);
                ship.setShipLevel(50, true);
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.moveTo(helper.absoluteVec(relative));
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture: add ship");
                GameTestEntities.assertRegistered(helper, ship);
                ship.setHealth(ship.getMaxHealth());
                ship.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(ship.getActiveEffects().isEmpty(), "Fixture: no effects to start with");
                check.accept(ship, entities);
                helper.succeed();
            }
        };
        GameTestEntities.whenPositionsTicking(helper, () -> {
            if (!darkRoom) {
                body.run();
                return;
            }
            for (int x = 2; x <= 6; x++) {
                for (int y = 2; y <= 8; y++) {
                    for (int z = 3; z <= 8; z++) {
                        boolean shell = x == 2 || x == 6 || y == 2 || y == 8 || z == 3 || z == 8;
                        helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                    }
                }
            }
            // The light engine settles over the following ticks; start only once the room is dark.
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    helper.getLevel().getMaxLocalRawBrightness(helper.absolutePos(new BlockPos(4, 4, 5))) <= 2,
                    "Waiting for the sealed room to go dark")).thenExecute(body);
        }, relative);
    }
}
