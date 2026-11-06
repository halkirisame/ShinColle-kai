package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipHealSynergyGameTests {
    private static final Vec3 ORIGIN = new Vec3(2, 3, 2);
    private static final String BATCH = "isolated_ship_heal_synergy";

    private ShipHealSynergyGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = BATCH)
    public static void nearbyCloudAddsHealBeforeHpresExactlyOnce(GameTestHelper helper) {
        ready(helper, fixture -> {
            BasicEntityShip ship = fixture.ship();
            AreaEffectCloud cloud = fixture.cloud(Potions.HEALING);
            fixture.potion(Potions.STRONG_HEALING, Items.SPLASH_POTION);
            fixture.heal(ship, 10F, 32F);
            cloud.discard();
            AreaEffectCloud custom = fixture.cloud(Potions.EMPTY);
            custom.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            ship.tickCount += 20;
            fixture.heal(ship, 10F, 32F);
            custom.setWaitTime(custom.tickCount + 1);
            ship.tickCount += 20;
            // A waiting cloud does not take priority over a flying potion.
            fixture.heal(ship, 10F, 48F);
            custom.setWaitTime(0);
            custom.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 100));
            ship.tickCount += 20;
            fixture.heal(ship, 10F, 32F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = BATCH)
    public static void nearbyThrownPotionAddsHealWhenNoCloudMatches(GameTestHelper helper) {
        ready(helper, fixture -> {
            BasicEntityShip ship = fixture.ship();
            fixture.cloud(Potions.SWIFTNESS);
            ThrownPotion potion = fixture.potion(Potions.HEALING, Items.SPLASH_POTION);
            fixture.heal(ship, 10F, 41F);
            potion.discard();
            ThrownPotion lingering = fixture.potion(Potions.EMPTY, Items.LINGERING_POTION);
            PotionUtils.setCustomEffects(lingering.getItem(),
                    List.of(new MobEffectInstance(MobEffects.REGENERATION, 200, 100)));
            ship.tickCount += 20;
            fixture.heal(ship, 10F, 69F);
            lingering.discard();
            ship.tickCount += 20;
            fixture.heal(ship, 10F, 20F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = BATCH)
    public static void repeatedHealsShareOneTwentyTickSynergyWindow(GameTestHelper helper) {
        ready(helper, fixture -> {
            BasicEntityShip ship = fixture.ship();
            AreaEffectCloud cloud = fixture.cloud(Potions.HEALING);
            fixture.heal(ship, 10F, 32F);
            for (int i = 0; i < 100; i++) fixture.heal(ship, 10F, 20F);
            cloud.discard();
            fixture.potion(Potions.HEALING, Items.SPLASH_POTION);
            ship.tickCount += 19;
            fixture.heal(ship, 10F, 20F);
            ship.tickCount++;
            fixture.heal(ship, 10F, 41F);
        });
    }

    private static void ready(GameTestHelper helper, Consumer<Fixture> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                verification.accept(fixture);
                helper.succeed();
            }
        }, ORIGIN);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
        }

        private BasicEntityShip ship() {
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Fixture: ship exists");
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(true);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.HPRES, 2F);
            ship.setHealth(10F);
            ship.moveTo(helper.absoluteVec(ORIGIN));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture: ship added");
            GameTestEntities.assertRegistered(helper, ship);
            helper.assertTrue(ship.getMaxHealth() == 100F && ship.getHealth() == 10F
                    && ship.getAttrs().getAttrsBuffed(ID.Attrs.HPRES) == 2F
                    && ship.getGrudge() == 100_000 && !ship.getStateFlag(ID.F.NoFuel), "Fixture: HP, HPRES and fuel");
            return ship;
        }

        private AreaEffectCloud cloud(Potion potion) {
            AreaEffectCloud cloud = entities.add(new AreaEffectCloud(helper.getLevel(), 0, 0, 0));
            cloud.setPotion(potion);
            cloud.setWaitTime(0);
            cloud.setDuration(200);
            cloud.setRadius(1F);
            cloud.moveTo(helper.absoluteVec(ORIGIN));
            helper.assertTrue(helper.getLevel().addFreshEntity(cloud), "Fixture: cloud added");
            GameTestEntities.assertRegistered(helper, cloud);
            helper.assertTrue(cloud.getPotion() == potion && cloud.getRadius() == 1F
                    && cloud.getWaitTime() == 0 && cloud.getDuration() == 200 && cloud.getOwner() == null,
                    "Fixture: active ownerless cloud");
            return cloud;
        }

        private ThrownPotion potion(Potion potion, net.minecraft.world.item.Item item) {
            ThrownPotion thrown = entities.add(new ThrownPotion(helper.getLevel(), 0, 0, 0));
            thrown.setItem(PotionUtils.setPotion(new ItemStack(item), potion));
            thrown.setNoGravity(true);
            thrown.moveTo(helper.absoluteVec(ORIGIN));
            helper.assertTrue(helper.getLevel().addFreshEntity(thrown), "Fixture: flying potion added");
            GameTestEntities.assertRegistered(helper, thrown);
            helper.assertTrue(PotionUtils.getPotion(thrown.getItem()) == potion
                    && thrown.getItem().is(item) && !thrown.onGround() && thrown.getOwner() == null,
                    "Fixture: ownerless flying potion");
            return thrown;
        }

        private void heal(BasicEntityShip ship, float amount, float expected) {
            ship.setHealth(10F);
            float[] observed = {Float.NaN, 0F};
            Consumer<LivingHealEvent> listener = event -> {
                if (event.getEntity() == ship) {
                    observed[0] = event.getAmount();
                    observed[1]++;
                }
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                ship.heal(amount);
                helper.assertTrue(observed[1] == 1F && Math.abs(observed[0] - expected) < 0.001F,
                        "One heal event: expected " + expected + ", got " + observed[0] + " in " + observed[1] + " events");
                helper.assertTrue(Math.abs(ship.getHealth() - Math.min(100F, 10F + expected)) < 0.001F,
                        "Heal HP: expected " + expected + ", got " + (ship.getHealth() - 10F));
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
            }
        }

        @Override
        public void close() {
            entities.close();
        }
    }
}
