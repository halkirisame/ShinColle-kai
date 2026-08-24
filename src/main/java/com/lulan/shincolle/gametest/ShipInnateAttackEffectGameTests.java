package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.equip.ShipOnHitEffects;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Parity coverage for the intrinsic attack effects defined by the 1.10.2 ship classes. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipInnateAttackEffectGameTests {

    private ShipInnateAttackEffectGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyShipsRebuildAllOriginalInnateAttackEffects(GameTestHelper helper) {
        verifyFriendly(helper, ModEntities.BB_NAGATO.get(), ShipInnateAttackEffects.POISON, 2, 210);
        verifyFriendly(helper, ModEntities.BB_RE.get(), ShipInnateAttackEffects.MINING_FATIGUE, 1, 250);
        verifyFriendly(helper, ModEntities.BB_YAMATO.get(), ShipInnateAttackEffects.MINING_FATIGUE, 2, 250);
        verifyFriendly(helper, ModEntities.BB_HARUNA.get(), ShipInnateAttackEffects.UNLUCK, 1, 250);
        verifyFriendly(helper, ModEntities.BB_HIEI.get(), ShipInnateAttackEffects.POISON, 1, 230);
        verifyFriendly(helper, ModEntities.BB_KIRISHIMA.get(), ShipInnateAttackEffects.BLINDNESS, 0, 200);
        verifyFriendly(helper, ModEntities.BB_KONGOU.get(), ShipInnateAttackEffects.WEAKNESS, 2, 250);
        verifyFriendly(helper, ModEntities.CV_AKAGI.get(), ShipInnateAttackEffects.HUNGER, 1, 250);
        verifyFriendly(helper, ModEntities.CV_KAGA.get(), ShipInnateAttackEffects.HUNGER, 1, 250);
        verifyFriendly(helper, ModEntities.CA_ATAGO.get(), ShipInnateAttackEffects.SLOWNESS, 1, 250);
        verifyFriendly(helper, ModEntities.CA_TAKAO.get(), ShipInnateAttackEffects.SLOWNESS, 1, 250);
        verifyFriendly(helper, ModEntities.BB_HIME.get(), ShipInnateAttackEffects.MINING_FATIGUE, 2, 250);

        BasicEntityShip isolated = createMarriedFriendly(helper, ModEntities.ISOLATED_HIME.get(), 150);
        assertEffect(isolated, ShipInnateAttackEffects.BLINDNESS, 0, 250, 100);
        assertEffect(isolated, ShipInnateAttackEffects.POISON, 2, 230, 100);
        assertEffectCount(isolated, 2);
        helper.succeed();
    }

    /**
     * The original 1.10.2 ship gates this poison on marriage, never on level.
     * Level only scales the amplifier, so a married ship below level 75 still
     * receives poison I.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void isolatedHimePoisonRequiresMarriageNotLevel(GameTestHelper helper) {
        // Unmarried stays blindness-only no matter how high the level goes.
        BasicEntityShip unmarried = createFriendly(helper, ModEntities.ISOLATED_HIME.get(), 150);
        assertEffect(unmarried, ShipInnateAttackEffects.BLINDNESS, 0, 250, 100);
        assertEffectCount(unmarried, 1);

        // Married below level 75 still gets poison, at amplifier 0.
        BasicEntityShip married = createMarriedFriendly(helper, ModEntities.ISOLATED_HIME.get(), 74);
        assertEffect(married, ShipInnateAttackEffects.BLINDNESS, 0, 174, 74);
        assertEffect(married, ShipInnateAttackEffects.POISON, 0, 154, 74);
        assertEffectCount(married, 2);

        // The amplifier, not the effect itself, is what level 75 changes.
        married.setShipLevel(75, false);
        married.calcShipAttributesAddEquip();
        assertEffect(married, ShipInnateAttackEffects.POISON, 1, 155, 75);
        assertEffectCount(married, 2);

        // Disabling the ring effect removes the poison again.
        married.setStateFlag(ID.F.UseRingEffect, false);
        married.calcShipAttributesAddEquip();
        assertEffect(married, ShipInnateAttackEffects.BLINDNESS, 0, 175, 75);
        assertEffectCount(married, 1);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileShipsRebuildAllOriginalInnateAttackEffects(GameTestHelper helper) {
        verifyHostile(helper, ModEntities.BB_NAGATO_MOB.get(), ShipInnateAttackEffects.POISON,
                0, 60, 1, 180);
        verifyHostile(helper, ModEntities.BB_YAMATO_MOB.get(), ShipInnateAttackEffects.MINING_FATIGUE,
                0, 100, 2, 250);
        verifyHostile(helper, ModEntities.BB_HARUNA_MOB.get(), ShipInnateAttackEffects.UNLUCK,
                0, 100, 2, 250);
        verifyHostile(helper, ModEntities.BB_HIEI_MOB.get(), ShipInnateAttackEffects.POISON,
                0, 80, 2, 230);
        verifyHostile(helper, ModEntities.BB_KIRISHIMA_MOB.get(), ShipInnateAttackEffects.BLINDNESS,
                0, 40, 2, 130);
        verifyHostile(helper, ModEntities.BB_KONGOU_MOB.get(), ShipInnateAttackEffects.WEAKNESS,
                0, 80, 2, 230);
        verifyHostile(helper, ModEntities.CV_AKAGI_MOB.get(), ShipInnateAttackEffects.HUNGER,
                0, 100, 1, 250);
        verifyHostile(helper, ModEntities.CV_KAGA_MOB.get(), ShipInnateAttackEffects.HUNGER,
                0, 100, 1, 250);
        verifyHostile(helper, ModEntities.CA_ATAGO_MOB.get(), ShipInnateAttackEffects.SLOWNESS,
                0, 100, 1, 250);
        verifyHostile(helper, ModEntities.CA_TAKAO_MOB.get(), ShipInnateAttackEffects.SLOWNESS,
                0, 100, 1, 250);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void onHitDispatcherUsesLiveEffectsAndExplicitProjectileSnapshot(GameTestHelper helper) {
        BasicEntityShip source = createFriendly(helper, ModEntities.BB_KONGOU.get(), 1);
        source.getAttackEffectMap().clear();
        ShipInnateAttackEffects.put(source, ShipInnateAttackEffects.POISON, 1, 120, 100);

        LivingEntity target = createTarget(helper);
        ShipOnHitEffects.dispatch(source, target, 1F);
        assertApplied(target, MobEffects.POISON, 1, 120, "live effect map");

        target.removeAllEffects();
        ShipAttackEffect weakness = new ShipAttackEffect(ShipInnateAttackEffects.WEAKNESS, 2, 80, 100);
        ShipOnHitEffects.dispatch(source, target, 1F,
                Map.of(ShipInnateAttackEffects.WEAKNESS, weakness));
        assertApplied(target, MobEffects.WEAKNESS, 2, 80, "projectile snapshot");
        if (target.hasEffect(MobEffects.POISON)) {
            throw new AssertionError("Projectile dispatch used the live map instead of its launch snapshot");
        }
        helper.succeed();
    }

    private static void verifyFriendly(GameTestHelper helper, EntityType<?> type, ResourceLocation effectId,
                                       int amplifier, int duration) {
        BasicEntityShip ship = createFriendly(helper, type, 150);
        assertEffect(ship, effectId, amplifier, duration, 100);
        assertEffectCount(ship, 1);
    }

    private static void verifyHostile(GameTestHelper helper, EntityType<?> type, ResourceLocation effectId,
                                      int scaleZeroAmplifier, int scaleZeroDuration,
                                      int scaleThreeAmplifier, int scaleThreeDuration) {
        BasicEntityShipHostile scaleZero = createHostile(helper, type, 0);
        assertEffect(scaleZero, effectId, scaleZeroAmplifier, scaleZeroDuration, 25);
        assertEffectCount(scaleZero, 1);

        BasicEntityShipHostile scaleThree = createHostile(helper, type, 3);
        assertEffect(scaleThree, effectId, scaleThreeAmplifier, scaleThreeDuration, 100);
        assertEffectCount(scaleThree, 1);
    }

    private static BasicEntityShip createFriendly(GameTestHelper helper, EntityType<?> type, int level) {
        Entity entity = type.create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create friendly ship from " + type);
        }
        ship.setShipLevel(level, false);
        ship.calcShipAttributesAddEquip();
        return ship;
    }

    /** Creates a friendly ship whose marriage-gated innate effects are active. */
    private static BasicEntityShip createMarriedFriendly(GameTestHelper helper, EntityType<?> type, int level) {
        Entity entity = type.create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create friendly ship from " + type);
        }
        ship.setShipLevel(level, false);
        ship.setStateFlag(ID.F.IsMarried, true);
        ship.setStateFlag(ID.F.UseRingEffect, true);
        ship.calcShipAttributesAddEquip();
        return ship;
    }

    private static BasicEntityShipHostile createHostile(GameTestHelper helper, EntityType<?> type, int scale) {
        Entity entity = type.create(helper.getLevel());
        if (!(entity instanceof BasicEntityShipHostile ship)) {
            throw new AssertionError("Failed to create hostile ship from " + type);
        }
        ship.initAttrs(scale);
        return ship;
    }

    private static LivingEntity createTarget(GameTestHelper helper) {
        Entity entity = EntityType.COW.create(helper.getLevel());
        if (!(entity instanceof LivingEntity target)) {
            throw new AssertionError("Failed to create living attack-effect target");
        }
        return target;
    }

    private static void assertEffect(IShipAttackBase ship, ResourceLocation effectId,
                                     int amplifier, int duration, int chance) {
        ShipAttackEffect effect = ship.getAttackEffectMap().get(effectId);
        ShipAttackEffect expected = new ShipAttackEffect(effectId, amplifier, duration, chance);
        if (!expected.equals(effect)) {
            throw new AssertionError(ship.getClass().getSimpleName() + " expected " + expected + " but got " + effect);
        }
    }

    private static void assertEffectCount(IShipAttackBase ship, int expected) {
        if (ship.getAttackEffectMap().size() != expected) {
            throw new AssertionError(ship.getClass().getSimpleName() + " expected " + expected
                    + " intrinsic effects but got " + ship.getAttackEffectMap());
        }
    }

    private static void assertApplied(LivingEntity target, net.minecraft.world.effect.MobEffect effect,
                                      int amplifier, int duration, String context) {
        MobEffectInstance applied = target.getEffect(effect);
        if (applied == null || applied.getAmplifier() != amplifier || applied.getDuration() != duration) {
            throw new AssertionError(context + " was not applied correctly: " + applied);
        }
    }
}
