package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.equip.ShipOnHitEffects;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Marriage-ring effects that battleships and carriers give to the ships around them, checked through each concrete aiStep. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarriageFleetAuraGameTests {
    private static final Vec3 SPAWN = new Vec3(1.5D, 22D, 1.5D);
    private static final double EPSILON = 0.01D;
    /** Boundaries around every divisor used (70, 80, 85, 120) and the old ones (75, 95), plus the ends of the level range. */
    private static final int[] LEVELS = {1, 69, 70, 71, 79, 80, 81, 84, 85, 86, 94, 95, 96, 119, 120, 121, 149, 150};

    private MarriageFleetAuraGameTests() {
    }

    /** One effect given to the fleet: the vanilla type with its amplifier and duration as functions of the ship level. */
    private record Aura(MobEffect effect, IntUnaryOperator amplifier, IntUnaryOperator duration) {
    }

    /**
     * {@code nightVision} marks a ship that also keeps a fixed night vision on itself regardless of the ring;
     * {@code admiral} marks a ship that gives the same effects to its nearby owner as well.
     */
    private record Case(String name, RegistryObject<? extends EntityType<? extends BasicEntityShip>> type,
                        boolean nightVision, boolean admiral, Aura... auras) {
        Case(String name, RegistryObject<? extends EntityType<? extends BasicEntityShip>> type,
             boolean nightVision, Aura... auras) {
            this(name, type, nightVision, false, auras);
        }
    }

    private static Aura aura(MobEffect effect, int divisor, int base, int perLevel) {
        return new Aura(effect, l -> l / divisor, l -> base + perLevel * l);
    }

    /** Every ship that gives the fleet effects. */
    private static List<Case> fleetShips() {
        List<Case> ships = new ArrayList<>();
        ships.add(new Case("Kongou", ModEntities.BB_KONGOU, false, aura(MobEffects.HEALTH_BOOST, 120, 140, 1)));
        ships.add(new Case("Hiei", ModEntities.BB_HIEI, false, aura(MobEffects.SATURATION, 120, 100, 2)));
        ships.add(new Case("Haruna", ModEntities.BB_HARUNA, false, aura(MobEffects.LUCK, 120, 100, 1)));
        ships.add(new Case("Kirishima", ModEntities.BB_KIRISHIMA, false,
                new Aura(MobEffects.NIGHT_VISION, l -> 0, l -> 100 + l)));
        ships.add(new Case("Nagato", ModEntities.BB_NAGATO, false, aura(MobEffects.DAMAGE_BOOST, 70, 50, 1)));
        ships.add(new Case("Yamato", ModEntities.BB_YAMATO, false,
                aura(MobEffects.DAMAGE_RESISTANCE, 70, 50, 1), aura(MobEffects.FIRE_RESISTANCE, 70, 50, 1)));
        ships.add(new Case("Ta", ModEntities.BB_TA, false, aura(MobEffects.MOVEMENT_SPEED, 80, 50, 1)));
        ships.add(new Case("Battleship Hime", ModEntities.BB_HIME, false,
                aura(MobEffects.DAMAGE_RESISTANCE, 70, 50, 1), aura(MobEffects.FIRE_RESISTANCE, 70, 50, 1)));
        ships.add(new Case("Carrier Hime", ModEntities.CV_HIME, false, aura(MobEffects.JUMP, 70, 50, 1)));
        ships.add(new Case("Carrier Water Demon", ModEntities.CV_WD, true, aura(MobEffects.DIG_SPEED, 70, 50, 1)));
        ships.add(new Case("Akagi", ModEntities.CV_AKAGI, false, aura(MobEffects.JUMP, 85, 50, 1)));
        ships.add(new Case("Kaga", ModEntities.CV_KAGA, false, aura(MobEffects.JUMP, 85, 50, 1)));
        ships.add(new Case("Wo", ModEntities.CV_WO, false, aura(MobEffects.DAMAGE_BOOST, 80, 30, 1)));
        ships.add(new Case("Midway", ModEntities.MIDWAY_HIME, false, true, aura(MobEffects.ABSORPTION, 50, 50, 1)));
        return ships;
    }

    private static Vec3[] fixtureArea() {
        Vec3[] area = new Vec3[9];
        int index = 0;
        for (int x = -20; x <= 20; x += 20) {
            for (int z = -20; z <= 20; z += 20) {
                area[index++] = SPAWN.add(x, 0D, z);
            }
        }
        return area;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void fleetAuraValuesMatchAllFourteenSources(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                List<Case> ships = fleetShips();
                helper.assertTrue(ships.size() == 14, "Expected fourteen fleet sources");
                for (Case entry : ships) {
                    BasicEntityShip source = scene.source(entry);
                    BasicEntityShip target = scene.target(3D);
                    for (int level : LEVELS) {
                        scene.prepare(source, level, true, true, 500);
                        scene.prepareTarget(target);
                        scene.fire(source);
                        scene.assertSourceEffects(entry, source, level);
                        scene.assertFleetEffects(entry, target, level);
                    }
                    source.discard();
                    target.discard();
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void fleetAuraIncludesItsSource(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                for (Case entry : fleetShips()) {
                    // nobody named by the owner id is online; the same id on the ship is all that counts
                    BasicEntityShip source = scene.source(entry);
                    scene.prepare(source, 120, true, true, 500);
                    helper.assertTrue(source.getOwner() == null && source.getOwnerUUID() != null,
                            "Owner fixture should be an offline id for " + entry.name());
                    scene.fire(source);
                    scene.assertSourceEffects(entry, source, 120);
                    source.discard();
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void fleetAuraUsesSymmetricAabbWithoutSphereFilter(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                for (Case entry : fleetShips()) {
                    BasicEntityShip source = scene.source(entry);
                    BasicEntityShip target = scene.target(3D);
                    scene.prepare(source, 120, true, true, 500);
                    scene.fire(source);
                    AABB box = source.getBoundingBox();
                    double tw = target.getBbWidth();
                    double th = target.getBbHeight();
                    double reach = 16D;

                    List<Probe> probes = new ArrayList<>();
                    probes.add(new Probe("+X inside", box.maxX + reach - EPSILON, box.minY, box.minZ, true));
                    probes.add(new Probe("+X outside", box.maxX + reach + EPSILON, box.minY, box.minZ, false));
                    probes.add(new Probe("-X inside", box.minX - reach + EPSILON - tw, box.minY, box.minZ, true));
                    probes.add(new Probe("-X outside", box.minX - reach - EPSILON - tw, box.minY, box.minZ, false));
                    probes.add(new Probe("+Y inside", box.minX, box.maxY + reach - EPSILON, box.minZ, true));
                    probes.add(new Probe("+Y outside", box.minX, box.maxY + reach + EPSILON, box.minZ, false));
                    probes.add(new Probe("-Y inside", box.minX, box.minY - reach + EPSILON - th, box.minZ, true));
                    probes.add(new Probe("-Y outside", box.minX, box.minY - reach - EPSILON - th, box.minZ, false));
                    probes.add(new Probe("+Z inside", box.minX, box.minY, box.maxZ + reach - EPSILON, true));
                    probes.add(new Probe("+Z outside", box.minX, box.minY, box.maxZ + reach + EPSILON, false));
                    probes.add(new Probe("-Z inside", box.minX, box.minY, box.minZ - reach + EPSILON - tw, true));
                    probes.add(new Probe("-Z outside", box.minX, box.minY, box.minZ - reach - EPSILON - tw, false));
                    probes.add(new Probe("+++ corner inside", box.maxX + reach - EPSILON, box.maxY + reach - EPSILON,
                            box.maxZ + reach - EPSILON, true));
                    probes.add(new Probe("+++ corner outside", box.maxX + reach + EPSILON, box.maxY + reach - EPSILON,
                            box.maxZ + reach - EPSILON, false));
                    probes.add(new Probe("--- corner inside", box.minX - reach + EPSILON - tw,
                            box.minY - reach + EPSILON - th, box.minZ - reach + EPSILON - tw, true));
                    probes.add(new Probe("--- corner outside", box.minX - reach + EPSILON - tw,
                            box.minY - reach + EPSILON - th, box.minZ - reach - EPSILON - tw, false));
                    // well inside the box but outside any sphere of radius 16 (or 12) around the source
                    probes.add(new Probe("diagonal 12,12", source.getX() + 12D - tw / 2D, box.minY, source.getZ() + 12D - tw / 2D, true));
                    probes.add(new Probe("axis 13.5", source.getX() + 13.5D - tw / 2D, box.minY, source.getZ() - tw / 2D, true));

                    for (Probe probe : probes) {
                        scene.prepareTarget(target);
                        scene.prepare(source, 120, true, true, 500);
                        target.setPos(probe.minX + tw / 2D, probe.minY, probe.minZ + tw / 2D);
                        AABB placed = target.getBoundingBox();
                        helper.assertTrue(Math.abs(placed.minX - probe.minX) < 1.0E-6D
                                        && Math.abs(placed.minY - probe.minY) < 1.0E-6D
                                        && Math.abs(placed.minZ - probe.minZ) < 1.0E-6D,
                                "Target box fixture was ignored for " + entry.name() + " " + probe.name);
                        if (probe.name.startsWith("diagonal")) {
                            helper.assertTrue(source.distanceToSqr(target) > 256D,
                                    "Diagonal fixture should lie outside the sphere for " + entry.name());
                        }
                        scene.fire(source);
                        helper.assertTrue(source.getBoundingBox().equals(box),
                                "Source box changed during the aura for " + entry.name());
                        if (probe.expected) {
                            scene.assertFleetEffects(entry, target, 120);
                        } else {
                            helper.assertTrue(target.getActiveEffects().isEmpty(),
                                    entry.name() + " reached a target outside the box: " + probe.name);
                        }
                    }
                    source.discard();
                    target.discard();
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    private record Probe(String name, double minX, double minY, double minZ, boolean expected) {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void fleetAuraHasNoLevelBasedTargetCap(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                for (Case entry : fleetShips()) {
                    BasicEntityShip source = scene.source(entry);
                    List<BasicEntityShip> fleet = new ArrayList<>();
                    for (int i = 0; i < 6; i++) {
                        fleet.add(scene.target(2D + 2D * i));
                    }
                    scene.prepare(source, 50, true, true, 500);
                    for (BasicEntityShip member : fleet) {
                        scene.prepareTarget(member);
                    }
                    scene.fire(source);
                    scene.assertSourceEffects(entry, source, 50);
                    for (BasicEntityShip member : fleet) {
                        scene.assertFleetEffects(entry, member, 50);
                    }
                    for (BasicEntityShip member : fleet) {
                        member.discard();
                    }
                    source.discard();
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void fleetAuraExcludesOtherOwnersPlayersMobsAndDeadShips(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                scene.joinPlayers();
                for (Case entry : fleetShips()) {
                    BasicEntityShip source = scene.source(entry);
                    BasicEntityShip same = scene.target(3D);
                    BasicEntityShip stranger = scene.target(4D);
                    stranger.setOwnerUUID(UUID.randomUUID());
                    BasicEntityShip unowned = scene.target(5D);
                    unowned.setOwnerUUID(null);
                    BasicEntityShip dead = scene.target(6D);
                    Mob mob = scene.entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                    helper.assertTrue(mob != null, "Could not create a mob");
                    mob.setNoAi(true);
                    mob.setPos(scene.at.add(7D, 0D, 0D));
                    helper.getLevel().addFreshEntity(mob);
                    scene.owner.setPos(scene.at.add(2D, 0D, 2D));
                    scene.other.setPos(scene.at.add(2D, 0D, -2D));

                    scene.prepare(source, 120, true, true, 500);
                    for (BasicEntityShip member : List.of(same, stranger, unowned, dead)) {
                        scene.prepareTarget(member);
                    }
                    stranger.setOwnerUUID(UUID.randomUUID());
                    unowned.setOwnerUUID(null);
                    dead.setHealth(0F);
                    mob.removeAllEffects();
                    scene.owner.removeAllEffects();
                    scene.other.removeAllEffects();
                    helper.assertTrue(!dead.isAlive() && !dead.isRemoved()
                                    && dead.getBoundingBox().intersects(source.getBoundingBox().inflate(16D)),
                            "The dying ship fixture must be dead, not yet removed, and inside the search box");
                    helper.assertTrue(stranger.getOwnerUUID() != null && unowned.getOwnerUUID() == null
                                    && !source.getOwnerUUID().equals(stranger.getOwnerUUID()),
                            "Owner fixtures were ignored for " + entry.name());
                    scene.fire(source);
                    scene.assertFleetPresent(entry, same);
                    helper.assertTrue(stranger.getActiveEffects().isEmpty(), entry.name() + " reached another owner's ship");
                    helper.assertTrue(unowned.getActiveEffects().isEmpty(), entry.name() + " reached an ownerless ship");
                    helper.assertTrue(dead.getActiveEffects().isEmpty(), entry.name() + " reached a dying ship");
                    helper.assertTrue(mob.getActiveEffects().isEmpty(), entry.name() + " reached a mob");
                    helper.assertTrue(scene.other.getActiveEffects().isEmpty(), entry.name() + " reached a bystander");
                    // only a ship that also strengthens its admiral may reach the owner
                    helper.assertTrue(entry.admiral() || scene.owner.getActiveEffects().isEmpty(),
                            entry.name() + " reached the admiral");

                    // a source and a target that both have no owner never match
                    scene.prepare(source, 120, true, true, 500);
                    source.setOwnerUUID(null);
                    scene.prepareTarget(unowned);
                    unowned.setOwnerUUID(null);
                    scene.fire(source);
                    helper.assertTrue(unowned.getActiveEffects().isEmpty() && !hasFleetEffect(entry, source),
                            entry.name() + " matched two ships that both have no owner");
                    for (Entity discard : List.of(same, stranger, unowned, dead, mob, source)) {
                        discard.discard();
                    }
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    private static boolean hasFleetEffect(Case entry, BasicEntityShip ship) {
        for (Aura aura : entry.auras()) {
            if (ship.hasEffect(aura.effect())) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void fleetAuraKeepsMarriageFuelRingAndPeriodGates(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                for (Case entry : fleetShips()) {
                    BasicEntityShip source = scene.source(entry);
                    BasicEntityShip target = scene.target(3D);

                    scene.prepare(source, 120, true, true, 500);
                    scene.prepareTarget(target);
                    scene.fire(source);
                    scene.assertFleetPresent(entry, target);

                    scene.prepare(source, 120, false, true, 500);
                    scene.prepareTarget(target);
                    scene.fire(source);
                    scene.assertNoFleetEffects(entry, source, target, "was not married", true);

                    scene.prepare(source, 120, true, false, 500);
                    scene.prepareTarget(target);
                    scene.fire(source);
                    scene.assertNoFleetEffects(entry, source, target, "had the ring switched off", true);

                    scene.prepare(source, 120, true, true, 0);
                    scene.prepareTarget(target);
                    scene.fire(source);
                    helper.assertTrue(source.getStateMinor(ID.M.NumGrudge) == 0, "Grudge fixture drifted for " + entry.name());
                    scene.assertNoFleetEffects(entry, source, target, "had no grudge", true);

                    // one grudge is enough, and the aura itself spends none
                    scene.prepare(source, 120, true, true, 1);
                    scene.prepareTarget(target);
                    scene.fire(source);
                    helper.assertTrue(source.getStateMinor(ID.M.NumGrudge) == 1,
                            entry.name() + " changed the grudge from 1 to " + source.getStateMinor(ID.M.NumGrudge));
                    scene.assertFleetPresent(entry, target);

                    // only the first tick of each 128 tick period gives the effects
                    for (int tick : new int[]{127, 129, 130}) {
                        scene.prepare(source, 120, true, true, 500);
                        scene.prepareTarget(target);
                        source.tickCount = tick;
                        source.aiStep();
                        scene.assertNoFleetEffects(entry, source, target, "fired at tick " + tick, false);
                    }
                    scene.prepare(source, 120, true, true, 500);
                    scene.prepareTarget(target);
                    source.tickCount = 256;
                    source.aiStep();
                    scene.assertFleetPresent(entry, target);
                    source.discard();
                    target.discard();
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void fleetAurasReachDirectAttributesAndPeriodicSaturation(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                List<Case> ships = fleetShips();
                for (int index : new int[]{0, 1, 2, 13}) {
                    Case entry = ships.get(index);
                    BasicEntityShip source = scene.source(entry);
                    BasicEntityShip target = scene.target(3D);
                    scene.prepare(source, 120, true, true, 500);
                    scene.prepareTarget(target);
                    // the contribution factor is min(amplifier + 1, 5)
                    float factor = Math.min(entry.auras()[0].amplifier().applyAsInt(120) + 1, 5);
                    scene.step(target);
                    float[] before = snapshot(target.getAttrs());
                    float[] potionBefore = potions(target.getAttrs());
                    scene.fire(source);
                    scene.assertFleetEffects(entry, target, 120);
                    MobEffect effect = entry.auras()[0].effect();
                    if (effect == MobEffects.SATURATION) {
                        // the usual 16 and 32 tick steps turn the effect into attributes, healing and morale
                        target.setStateMinor(ID.M.Morale, 1000);
                        target.setHealth(Math.max(1F, target.getMaxHealth() / 2F));
                        float health = target.getHealth();
                        scene.step(target);
                        float[] after = snapshot(target.getAttrs());
                        float[] potionAfter = potions(target.getAttrs());
                        scene.assertPotion(entry, potionBefore, potionAfter, ID.Attrs.GRUDGE, 0.5F * factor);
                        scene.assertPotion(entry, potionBefore, potionAfter, ID.Attrs.AMMO, 0.5F * factor);
                        helper.assertTrue(after[ID.Attrs.GRUDGE] > before[ID.Attrs.GRUDGE],
                                "Saturation did not reach the final grudge attribute");
                        helper.assertTrue(target.getStateMinor(ID.M.Morale) == 1000 + 100 * (int) factor,
                                "Saturation morale gain was " + (target.getStateMinor(ID.M.Morale) - 1000));
                        float input = (Math.max(target.getMaxHealth() * 0.01F, 1F) + 2F) * (0.8F + factor * 0.2F);
                        float expected = Math.min(target.getMaxHealth() - health,
                                input * target.getAttrs().getAttrsBuffed(ID.Attrs.HPRES));
                        helper.assertTrue(expected > 1F && Math.abs(target.getHealth() - health - expected) < 1.0E-3F,
                                "Saturation healed " + (target.getHealth() - health) + ", expected " + expected);
                    } else {
                        scene.step(target);
                        float[] after = snapshot(target.getAttrs());
                        float[] potionAfter = potions(target.getAttrs());
                        if (effect == MobEffects.HEALTH_BOOST) {
                            scene.assertPotion(entry, potionBefore, potionAfter, ID.Attrs.HP, 150F * factor);
                            scene.assertPotion(entry, potionBefore, potionAfter, ID.Attrs.HPRES, 0.5F * factor);
                            helper.assertTrue(after[ID.Attrs.HP] > before[ID.Attrs.HP],
                                    "Health boost did not reach the final HP");
                        } else if (effect == MobEffects.ABSORPTION) {
                            scene.assertPotion(entry, potionBefore, potionAfter, ID.Attrs.HP, 100F * factor);
                            scene.assertPotion(entry, potionBefore, potionAfter, ID.Attrs.DEF, 0.2F * factor);
                            helper.assertTrue(after[ID.Attrs.HP] > before[ID.Attrs.HP],
                                    "Absorption did not reach the final HP");
                        } else {
                            for (int id : new int[]{ID.Attrs.CRI, ID.Attrs.DHIT, ID.Attrs.THIT}) {
                                scene.assertPotion(entry, potionBefore, potionAfter, id, 0.2F * factor);
                                helper.assertTrue(after[id] > before[id], "Luck did not reach the final attribute " + id);
                            }
                        }
                    }
                    source.discard();
                    target.discard();
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void fleetAuraExpirationAndStrongerEffectsUseVanillaRules(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper);
            try {
                Case wo = fleetShips().get(12);
                helper.assertTrue(wo.name().equals("Wo"), "The expiry case is expected to be Wo");
                BasicEntityShip source = scene.source(wo);
                BasicEntityShip target = scene.target(3D);
                BasicEntityShip strong = scene.target(4D);

                // a stronger effect the target already has is not lowered or replaced by the weaker one
                scene.prepare(source, 1, true, true, 500);
                scene.prepareTarget(target);
                scene.prepareTarget(strong);
                strong.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 3, false, false));
                scene.fire(source);
                MobEffectInstance kept = strong.getEffect(MobEffects.DAMAGE_BOOST);
                helper.assertTrue(kept != null && kept.getAmplifier() == 3 && kept.getDuration() >= 195,
                        "A stronger effect was lowered: "
                                + (kept == null ? "none" : "a" + kept.getAmplifier() + "/d" + kept.getDuration()));
                MobEffectInstance given = target.getEffect(MobEffects.DAMAGE_BOOST);
                helper.assertTrue(given != null && given.getAmplifier() == 0 && given.getDuration() == 31,
                        "Expected Wo level 1 effect a0/d31");

                // the admiral's escort ship follows the same rule for absorption
                BasicEntityShip admiral = scene.source(fleetShips().get(13));
                scene.prepare(admiral, 100, true, true, 500);
                scene.prepareTarget(target);
                scene.prepareTarget(strong);
                strong.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4, false, false));
                scene.fire(admiral);
                MobEffectInstance keptAbsorption = strong.getEffect(MobEffects.ABSORPTION);
                helper.assertTrue(keptAbsorption != null && keptAbsorption.getAmplifier() == 4
                                && keptAbsorption.getDuration() >= 195,
                        "A stronger absorption was lowered");
                MobEffectInstance givenAbsorption = target.getEffect(MobEffects.ABSORPTION);
                helper.assertTrue(givenAbsorption != null && givenAbsorption.getAmplifier() == 2
                                && givenAbsorption.getDuration() == 150,
                        "Expected Midway level 100 absorption a2/d150");
                admiral.discard();
                scene.prepareTarget(target);
                scene.prepareTarget(strong);

                // the water demon keeps its own night vision however the ring is set
                Case demon = fleetShips().get(9);
                BasicEntityShip water = scene.source(demon);
                scene.prepare(water, 120, false, false, 0);
                scene.fire(water);
                MobEffectInstance sight = water.getEffect(MobEffects.NIGHT_VISION);
                helper.assertTrue(sight != null && sight.getAmplifier() == 0 && sight.getDuration() == 150
                                && water.getActiveEffects().size() == 1,
                        "The water demon should have only night vision a0/d150 when unmarried, empty and switched off");

                // saved ship values survive a reload and the next period gives the effect again
                scene.prepare(source, 77, true, true, 321);
                CompoundTag tag = source.saveWithoutId(new CompoundTag());
                BasicEntityShip reloaded = scene.entities.add(wo.type().get().create(helper.getLevel()));
                reloaded.load(tag);
                reloaded.setUUID(UUID.randomUUID());
                helper.assertTrue(reloaded.getStateMinor(ID.M.ShipLevel) == 77 && reloaded.getStateMinor(ID.M.NumGrudge) == 321
                                && reloaded.getStateFlag(ID.F.IsMarried) && reloaded.getStateFlag(ID.F.UseRingEffect)
                                && scene.ownerId.equals(reloaded.getOwnerUUID()),
                        "A reloaded ship lost its level, grudge, marriage, ring switch or owner");
                reloaded.setPos(scene.at.add(0D, 0D, 8D));
                helper.getLevel().addFreshEntity(reloaded);
                reloaded.setNoAi(true);
                reloaded.setInvulnerable(true);
                scene.prepareTarget(target);
                reloaded.setGrudgeConsumption(0);
                reloaded.removeAllEffects();
                scene.fire(reloaded);
                MobEffectInstance again = target.getEffect(MobEffects.DAMAGE_BOOST);
                helper.assertTrue(again != null && again.getAmplifier() == 0 && again.getDuration() == 107,
                        "A reloaded ship should give a0/d107 at level 77");
                reloaded.discard();

                // natural expiry: switch the ring off and let the short effect run out
                scene.prepare(source, 1, true, true, 500);
                scene.prepareTarget(target);
                scene.fire(source);
                helper.assertTrue(target.hasEffect(MobEffects.DAMAGE_BOOST), "Level 1 effect was not given");
                source.setStateFlag(ID.F.UseRingEffect, false);
                helper.assertTrue(!source.getStateFlag(ID.F.UseRingEffect), "Ring switch fixture was ignored");
                helper.runAfterDelay(40, () -> {
                    try {
                        helper.assertTrue(!target.hasEffect(MobEffects.DAMAGE_BOOST),
                                "The effect did not run out by itself after the ring was switched off");
                        scene.close();
                        helper.succeed();
                    } catch (RuntimeException | AssertionError failure) {
                        scene.close();
                        throw failure;
                    }
                });
            } catch (RuntimeException | AssertionError failure) {
                scene.close();
                throw failure;
            }
        }, fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void midwaySelfHealingAddsOneAndRequiresFuel(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                BasicEntityShip midway = scene.source(fleetShips().get(13));
                for (int level : new int[]{1, 75, 150}) {
                    // grudge 1, hurt: the heal input is 9% of the maximum health plus one
                    for (boolean nearlyFull : new boolean[]{false, true}) {
                        scene.prepare(midway, level, false, false, 1);
                        midway.calcShipAttributes(31, false);
                        float hurt = nearlyFull ? midway.getMaxHealth() - 0.25F : 1F;
                        midway.setHealth(hurt);
                        helper.assertTrue(midway.getHealth() == hurt, "Health fixture was ignored");
                        scene.fire(midway);
                        float max = midway.getMaxHealth();
                        float resistance = midway.getAttrs().getAttrsBuffed(ID.Attrs.HPRES);
                        helper.assertTrue(resistance > 0.1F, "Heal resistance fixture drifted: " + resistance);
                        float input = max * 0.09F + 1F;
                        float expected = Math.min(max - hurt, input * resistance);
                        helper.assertTrue(midway.getStateMinor(ID.M.NumGrudge) == 1,
                                "Midway Lv" + level + " changed the grudge from 1");
                        helper.assertTrue(Math.abs(midway.getHealth() - hurt - expected) < 1.0E-3F,
                                "Midway Lv" + level + " healed " + (midway.getHealth() - hurt) + ", expected " + expected
                                        + " from input " + input);
                    }

                    // no grudge: no heal of its own, and nothing else heals at this tick
                    scene.prepare(midway, level, false, false, 0);
                    midway.calcShipAttributes(31, false);
                    midway.setHealth(1F);
                    scene.fire(midway);
                    helper.assertTrue(midway.getStateMinor(ID.M.NumGrudge) == 0 && midway.getHealth() == 1F,
                            "Midway Lv" + level + " healed without grudge: " + midway.getHealth());

                    // full health: nothing to heal
                    scene.prepare(midway, level, false, false, 1);
                    midway.calcShipAttributes(31, false);
                    midway.setHealth(midway.getMaxHealth());
                    float full = midway.getHealth();
                    scene.fire(midway);
                    helper.assertTrue(midway.getHealth() == full && midway.getHealth() <= midway.getMaxHealth(),
                            "Midway Lv" + level + " changed full health");

                    // off the period: no heal
                    scene.prepare(midway, level, false, false, 1);
                    midway.calcShipAttributes(31, false);
                    midway.setHealth(1F);
                    midway.tickCount = 127;
                    midway.aiStep();
                    helper.assertTrue(midway.getHealth() == 1F, "Midway Lv" + level + " healed at tick 127");
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void midwayAbsorptionStillReachesOnlyNearbyOwner(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                Case entry = fleetShips().get(13);
                BasicEntityShip midway = scene.source(entry);
                List<BasicEntityShip> fleet = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    fleet.add(scene.target(2D + 2D * i));
                }

                // an admiral with the same id who is in another dimension is not found
                ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
                helper.assertTrue(nether != null, "The nether is needed for the other dimension fixture");
                FakePlayer faraway = new FakePlayer(nether, new GameProfile(scene.ownerId, "fleet_aura_other_dimension"));
                faraway.setPos(scene.at);
                nether.addNewPlayer(faraway);
                try {
                    scene.prepare(midway, 120, true, true, 500);
                    for (BasicEntityShip member : fleet) {
                        scene.prepareTarget(member);
                    }
                    helper.assertTrue(midway.level().getPlayerByUUID(scene.ownerId) == null,
                            "The other dimension admiral was found in this level");
                    scene.fire(midway);
                    helper.assertTrue(faraway.getActiveEffects().isEmpty(), "An admiral in another dimension was reached");
                    scene.assertSourceEffects(entry, midway, 120);
                    for (BasicEntityShip member : fleet) {
                        scene.assertFleetEffects(entry, member, 120);
                    }
                } finally {
                    nether.removePlayerImmediately(faraway, Entity.RemovalReason.DISCARDED);
                }

                scene.joinPlayers();
                // the admiral gets the same amplifier and duration as the ships at every level boundary
                for (int level : new int[]{1, 49, 50, 51, 99, 100, 101, 149, 150}) {
                    scene.prepare(midway, level, true, true, 500);
                    for (BasicEntityShip member : fleet) {
                        scene.prepareTarget(member);
                    }
                    scene.owner.removeAllEffects();
                    scene.other.removeAllEffects();
                    scene.owner.setPos(scene.at.add(5D, 0D, 0D));
                    scene.other.setPos(scene.at.add(4D, 0D, 0D));
                    scene.fire(midway);
                    assertAbsorption(helper, scene.owner, level, "the admiral");
                    helper.assertTrue(scene.other.getActiveEffects().isEmpty(), "Midway reached another player");
                    scene.assertSourceEffects(entry, midway, level);
                    for (BasicEntityShip member : fleet) {
                        scene.assertFleetEffects(entry, member, level);
                    }
                }

                // the distance is strict: below 16 yes, exactly 16 and beyond no
                for (double distance : new double[]{0D, 15.99D, 16D, 16.01D}) {
                    scene.prepare(midway, 120, true, true, 500);
                    scene.owner.removeAllEffects();
                    scene.owner.setPos(scene.at.add(distance, 0D, 0D));
                    helper.assertTrue(midway.distanceToSqr(scene.owner) < 256D == distance < 16D,
                            "Distance fixture is not where expected: " + distance);
                    scene.fire(midway);
                    if (distance < 16D) {
                        assertAbsorption(helper, scene.owner, 120, "the admiral at " + distance);
                    } else {
                        helper.assertTrue(scene.owner.getActiveEffects().isEmpty(),
                                "The admiral at " + distance + " was reached");
                    }
                }

                // the marriage ring and fuel conditions apply to the admiral as to the ships
                scene.owner.setPos(scene.at.add(5D, 0D, 0D));
                boolean[][] conditions = {{false, true}, {true, false}};
                for (boolean[] condition : conditions) {
                    scene.prepare(midway, 120, condition[0], condition[1], 500);
                    scene.owner.removeAllEffects();
                    scene.fire(midway);
                    helper.assertTrue(scene.owner.getActiveEffects().isEmpty(),
                            "The admiral was reached with marriage " + condition[0] + " and ring " + condition[1]);
                }
                scene.prepare(midway, 120, true, true, 0);
                scene.owner.removeAllEffects();
                scene.fire(midway);
                helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), "The admiral was reached without grudge");

                // not on the ticks around the period
                for (int tick : new int[]{127, 129}) {
                    scene.prepare(midway, 120, true, true, 500);
                    scene.owner.removeAllEffects();
                    midway.tickCount = tick;
                    midway.aiStep();
                    helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), "The admiral was reached at tick " + tick);
                }
                helper.succeed();
            }
        }), fixtureArea());
    }

    private static void assertAbsorption(GameTestHelper helper, Player player, int level, String who) {
        MobEffectInstance found = player.getEffect(MobEffects.ABSORPTION);
        helper.assertTrue(found != null && found.getAmplifier() == level / 50 && found.getDuration() == 50 + level
                        && !found.isAmbient() && !found.isVisible() && player.getActiveEffects().size() == 1,
                "Midway Lv" + level + " should give " + who + " absorption a" + (level / 50) + "/d" + (50 + level)
                        + ", found " + (found == null ? "none" : "a" + found.getAmplifier() + "/d" + found.getDuration()));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void isolatedHimeNoLongerAddsSurroundingStrength(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                BasicEntityShip isolated = scene.spawn(ModEntities.ISOLATED_HIME, "Isolated", scene.at);
                BasicEntityShip near = scene.target(3D);
                BasicEntityShip far = scene.target(9D);
                for (int level : new int[]{1, 75, 120, 150}) {
                    scene.prepare(isolated, level, true, true, 500);
                    scene.prepareTarget(near);
                    scene.prepareTarget(far);
                    for (int tick : new int[]{128, 256, 384}) {
                        isolated.tickCount = tick;
                        isolated.aiStep();
                    }
                    helper.assertTrue(near.getActiveEffects().isEmpty() && far.getActiveEffects().isEmpty(),
                            "Isolated Hime Lv" + level + " still gives its surrounding ships an effect");
                    helper.assertTrue(isolated.getActiveEffects().isEmpty(),
                            "Isolated Hime Lv" + level + " gives itself an effect");
                }

                // a Strength the ship already has stays, and other sources still refresh it
                scene.prepare(isolated, 120, true, true, 500);
                scene.prepareTarget(near);
                near.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, false));
                isolated.tickCount = 128;
                isolated.aiStep();
                MobEffectInstance kept = near.getEffect(MobEffects.DAMAGE_BOOST);
                helper.assertTrue(kept != null && kept.getAmplifier() == 1 && kept.getDuration() >= 195
                                && near.getActiveEffects().size() == 1,
                        "An existing Strength was changed by the Isolated Hime: "
                                + (kept == null ? "none" : "a" + kept.getAmplifier() + "/d" + kept.getDuration()));
                BasicEntityShip wo = scene.source(fleetShips().get(12));
                scene.prepare(wo, 80, true, true, 500);
                scene.fire(wo);
                MobEffectInstance refreshed = near.getEffect(MobEffects.DAMAGE_BOOST);
                helper.assertTrue(refreshed != null && refreshed.getAmplifier() == 1 && refreshed.getDuration() >= 195,
                        "A stronger Strength was lowered by another source");
                scene.prepareTarget(near);
                scene.fire(wo);
                refreshed = near.getEffect(MobEffects.DAMAGE_BOOST);
                helper.assertTrue(refreshed != null && refreshed.getAmplifier() == 1 && refreshed.getDuration() == 110,
                        "Wo should still give Strength a1/d110 at level 80");
                helper.succeed();
            }
        }), fixtureArea());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void isolatedHimeKeepsItsInnateAttackEffects(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                BasicEntityShip isolated = scene.spawn(ModEntities.ISOLATED_HIME, "Isolated", scene.at);
                for (int level : new int[]{1, 74, 75, 100, 150}) {
                    for (boolean married : new boolean[]{false, true}) {
                        for (boolean ring : new boolean[]{false, true}) {
                            scene.prepare(isolated, level, married, ring, 500);
                            isolated.calcShipAttributes(31, false);
                            assertInnateEffects(helper, isolated, level, married && ring);
                            // the aura period must not touch what was built
                            scene.fire(isolated);
                            assertInnateEffects(helper, isolated, level, married && ring);
                        }
                    }
                }

                // an actual hit applies them to a target; at level 100 the chance is 100%
                Mob victim = scene.entities.add(EntityType.COW.create(helper.getLevel()));
                helper.assertTrue(victim != null, "Could not create the target");
                scene.prepare(isolated, 100, true, true, 500);
                isolated.calcShipAttributes(31, false);
                ShipOnHitEffects.dispatch(isolated, victim, 1F);
                MobEffectInstance blind = victim.getEffect(MobEffects.BLINDNESS);
                MobEffectInstance poison = victim.getEffect(MobEffects.POISON);
                helper.assertTrue(blind != null && blind.getAmplifier() == 0 && blind.getDuration() == 200
                                && poison != null && poison.getAmplifier() == 1 && poison.getDuration() == 180,
                        "A hit by a married Isolated Hime should blind a0/d200 and poison a1/d180");
                victim.removeAllEffects();
                scene.prepare(isolated, 100, false, true, 500);
                isolated.calcShipAttributes(31, false);
                ShipOnHitEffects.dispatch(isolated, victim, 1F);
                helper.assertTrue(victim.hasEffect(MobEffects.BLINDNESS) && !victim.hasEffect(MobEffects.POISON),
                        "An unmarried Isolated Hime should only blind");
                helper.succeed();
            }
        }), fixtureArea());
    }

    private static void assertInnateEffects(GameTestHelper helper, BasicEntityShip ship, int level, boolean poison) {
        ShipAttackEffect blind = ship.getAttackEffectMap().get(ShipInnateAttackEffects.BLINDNESS);
        helper.assertTrue(new ShipAttackEffect(ShipInnateAttackEffects.BLINDNESS, 0, 100 + level, Math.min(level, 100))
                        .equals(blind),
                "Isolated Hime Lv" + level + " blindness was " + blind);
        ResourceLocation poisonId = ShipInnateAttackEffects.POISON;
        ShipAttackEffect found = ship.getAttackEffectMap().get(poisonId);
        if (poison) {
            helper.assertTrue(new ShipAttackEffect(poisonId, level / 75, 80 + level, Math.min(level, 100)).equals(found),
                    "Isolated Hime Lv" + level + " poison was " + found);
        } else {
            helper.assertTrue(found == null, "Isolated Hime Lv" + level + " has poison without marriage and ring");
        }
        helper.assertTrue(ship.getAttackEffectMap().size() == (poison ? 2 : 1),
                "Isolated Hime Lv" + level + " has " + ship.getAttackEffectMap().size() + " attack effects");
    }

    private static float[] snapshot(Attrs attrs) {
        float[] values = new float[Attrs.AttrsLength];
        for (int i = 0; i < values.length; i++) {
            values[i] = attrs.getAttrsBuffed(i);
        }
        return values;
    }

    private static float[] potions(Attrs attrs) {
        float[] values = new float[Attrs.AttrsLength];
        for (int i = 0; i < values.length; i++) {
            values[i] = attrs.getAttrsPotion(i);
        }
        return values;
    }

    /** Ships and an offline owner id, all removed on close; the admiral and a bystander are added to the level on request. */
    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final UUID ownerId;
        final FakePlayer owner;
        final FakePlayer other;
        final Vec3 at;
        private boolean playersJoined;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.ownerId = UUID.randomUUID();
            this.owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(this.ownerId, "fleet_aura_admiral"));
            this.other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fleet_aura_bystander"));
            this.at = helper.absoluteVec(SPAWN);
        }

        void joinPlayers() {
            this.owner.setPos(this.at);
            this.other.setPos(this.at);
            this.helper.getLevel().addNewPlayer(this.owner);
            this.helper.getLevel().addNewPlayer(this.other);
            this.playersJoined = true;
        }

        BasicEntityShip spawn(RegistryObject<? extends EntityType<? extends BasicEntityShip>> type, String name,
                                      Vec3 position) {
            BasicEntityShip ship = this.entities.add(type.get().create(this.helper.getLevel()));
            this.helper.assertTrue(ship != null, "Could not create " + name);
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            ship.setPos(position);
            ship.setOwnerUUID(this.ownerId);
            this.helper.getLevel().addFreshEntity(ship);
            this.helper.assertTrue(this.helper.getLevel().getEntity(ship.getUUID()) == ship,
                    name + " was not added to the level");
            return ship;
        }

        BasicEntityShip source(Case entry) {
            return this.spawn(entry.type(), entry.name(), this.at);
        }

        /** An ordinary ship of the same owner {@code distance} blocks east of the source. */
        BasicEntityShip target(double distance) {
            return this.spawn(ModEntities.DD_HIME, "target ship", this.at.add(distance, 0D, 0D));
        }

        /** Sets every input, reads it back and clears all effects. */
        void prepare(BasicEntityShip ship, int level, boolean married, boolean ring, int grudge) {
            ship.setOwnerUUID(this.ownerId);
            ship.setStateMinor(ID.M.ShipLevel, level);
            ship.setStateMinor(ID.M.NumGrudge, grudge);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.IsMarried, married);
            ship.setStateFlag(ID.F.UseRingEffect, ring);
            ship.setGrudgeConsumption(0);
            ship.removeAllEffects();
            this.helper.assertTrue(ship.getStateMinor(ID.M.ShipLevel) == level
                            && ship.getStateMinor(ID.M.NumGrudge) == grudge
                            && !ship.getStateFlag(ID.F.NoFuel)
                            && ship.getStateFlag(ID.F.IsMarried) == married
                            && ship.getStateFlag(ID.F.UseRingEffect) == ring
                            && ship.getGrudgeConsumption() == 0
                            && this.ownerId.equals(ship.getOwnerUUID())
                            && ship.getActiveEffects().isEmpty(),
                    "A fixture value was ignored");
        }

        /** Clears the effects of a receiving ship and puts it back among the owner's fleet. */
        void prepareTarget(BasicEntityShip ship) {
            ship.setOwnerUUID(this.ownerId);
            ship.setHealth(ship.getMaxHealth());
            ship.removeAllEffects();
            this.helper.assertTrue(ship.isAlive() && ship.getActiveEffects().isEmpty()
                            && this.ownerId.equals(ship.getOwnerUUID()),
                    "A target fixture value was ignored");
        }

        /** Runs the ordinary 16 and 32 tick updates of a ship once, as the entity tick would. */
        void step(BasicEntityShip ship) {
            ship.tickCount = 32;
            ship.aiStep();
        }

        /** Runs the concrete aiStep once at the first tick of a period. */
        void fire(BasicEntityShip ship) {
            ship.tickCount = 128;
            ship.aiStep();
        }

        /** The fleet effects of the case, exactly, and nothing else. */
        void assertFleetEffects(Case entry, BasicEntityShip ship, int level) {
            this.assertEffects(entry, ship, level, 0);
        }

        /** Every effect kind of the case is present on the ship, whatever its strength, and nothing else. */
        void assertFleetPresent(Case entry, BasicEntityShip ship) {
            for (Aura aura : entry.auras()) {
                this.helper.assertTrue(ship.hasEffect(aura.effect()),
                        entry.name() + " did not give " + aura.effect().getDescriptionId());
            }
            this.helper.assertTrue(ship.getActiveEffects().size() == entry.auras().length,
                    entry.name() + " gave " + ship.getActiveEffects().size() + " effect kinds");
        }

        /** The same for the source, which may also carry its fixed night vision. */
        void assertSourceEffects(Case entry, BasicEntityShip ship, int level) {
            this.assertEffects(entry, ship, level, entry.nightVision() ? 1 : 0);
            if (entry.nightVision()) {
                MobEffectInstance sight = ship.getEffect(MobEffects.NIGHT_VISION);
                this.helper.assertTrue(sight != null && sight.getAmplifier() == 0 && sight.getDuration() == 150,
                        entry.name() + " lacks its fixed night vision a0/d150");
            }
        }

        private void assertEffects(Case entry, BasicEntityShip ship, int level, int extraKinds) {
            for (Aura aura : entry.auras()) {
                MobEffectInstance found = ship.getEffect(aura.effect());
                int amplifier = aura.amplifier().applyAsInt(level);
                int duration = aura.duration().applyAsInt(level);
                this.helper.assertTrue(found != null && found.getAmplifier() == amplifier && found.getDuration() == duration,
                        entry.name() + " Lv" + level + " expected " + aura.effect().getDescriptionId() + " a" + amplifier
                                + "/d" + duration + ", found "
                                + (found == null ? "none" : "a" + found.getAmplifier() + "/d" + found.getDuration()));
                this.helper.assertTrue(!found.isAmbient() && !found.isVisible(), entry.name() + " effect is not hidden");
            }
            int kinds = entry.auras().length + extraKinds;
            this.helper.assertTrue(ship.getActiveEffects().size() == kinds,
                    entry.name() + " Lv" + level + " has " + ship.getActiveEffects().size() + " effect kinds, expected "
                            + kinds);
        }

        /** Neither the source nor the target holds a fleet effect; after a period fired, the fixed night vision may remain. */
        void assertNoFleetEffects(Case entry, BasicEntityShip source, BasicEntityShip target, String reason,
                                  boolean periodFired) {
            this.helper.assertTrue(target.getActiveEffects().isEmpty(), entry.name() + " gave an effect to its target but "
                    + reason);
            int allowed = entry.nightVision() && periodFired ? 1 : 0;
            this.helper.assertTrue(source.getActiveEffects().size() == allowed,
                    entry.name() + " gave itself an effect but " + reason);
        }

        void assertPotion(Case entry, float[] before, float[] after, int id, float expected) {
            this.helper.assertTrue(Math.abs(after[id] - before[id] - expected) < 1.0E-3F,
                    entry.name() + " potion contribution to attribute " + id + " was " + (after[id] - before[id])
                            + ", expected " + expected);
        }

        @Override
        public void close() {
            if (this.playersJoined) {
                this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                this.helper.getLevel().removePlayerImmediately(this.other, Entity.RemovalReason.DISCARDED);
                this.playersJoined = false;
            }
            this.entities.close();
        }
    }
}
