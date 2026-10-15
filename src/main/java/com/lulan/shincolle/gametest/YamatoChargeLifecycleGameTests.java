package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.battleship.EntityBattleshipYamato;
import com.lulan.shincolle.entity.battleship.EntityBattleshipYamatoMob;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoChargeLifecycleGameTests {
    private YamatoChargeLifecycleGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "yamato_charge_lifecycle")
    public static void friendlyLostTargetClearsChargeAndNextAttackChargesAgain(GameTestHelper helper) {
        EntityBattleshipYamato ship = friendly(helper);
        Zombie target = target(helper);
        ship.setTarget(target);
        helper.assertTrue(!ship.attackEntityWithHeavyAmmo(target), "First heavy attack must charge");
        helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 1, "Charge fixture");
        int ammo = ship.getAmmoHeavy();
        int grudge = ship.getStateMinor(ID.M.NumGrudge);
        int morale = ship.getStateMinor(ID.M.Morale);
        ship.setTarget(null);
        ship.aiStep();
        helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0, "Lost target must clear friendly charge");
        helper.assertTrue(ship.getAmmoHeavy() == ammo && ship.getStateMinor(ID.M.NumGrudge) == grudge
                && ship.getStateMinor(ID.M.Morale) == morale, "Cancellation must not refund spent resources");
        ship.setTarget(target);
        helper.assertTrue(!ship.attackEntityWithHeavyAmmo(target), "Next fight must charge instead of firing");
        helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 1, "Next fight charge");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "yamato_charge_lifecycle")
    public static void hostileLostTargetClearsChargeAndNextAttackChargesAgain(GameTestHelper helper) {
        EntityBattleshipYamatoMob ship = hostile(helper);
        Zombie target = target(helper);
        ship.setTarget(target);
        helper.assertTrue(!ship.attackEntityWithHeavyAmmo(target), "Hostile first attack must charge");
        ship.setTarget(null);
        ship.aiStep();
        helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0, "Lost target must clear hostile charge");
        ship.setTarget(target);
        helper.assertTrue(!ship.attackEntityWithHeavyAmmo(target), "Hostile next fight must charge");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "yamato_charge_lifecycle")
    public static void livingTargetRetainsChargeForBothYamatos(GameTestHelper helper) {
        EntityBattleshipYamato ship = friendly(helper);
        EntityBattleshipYamatoMob hostile = hostile(helper);
        Zombie target = target(helper);
        ship.setTarget(target);
        hostile.setTarget(target);
        ship.setStateEmotion(ID.S.Phase, 1, false);
        hostile.setStateEmotion(ID.S.Phase, 1, false);
        ship.aiStep();
        hostile.aiStep();
        helper.assertTrue(target.isAlive() && ship.getEntityTarget() == target
                && hostile.getEntityTarget() == target, "Living target fixture must remain active");
        helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 1 && hostile.getStateEmotion(ID.S.Phase) == 1,
                "A living target must retain both charges");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "yamato_charge_lifecycle")
    public static void deadTargetClearsChargeForBothYamatos(GameTestHelper helper) {
        EntityBattleshipYamato ship = friendly(helper);
        EntityBattleshipYamatoMob hostile = hostile(helper);
        Zombie target = target(helper);
        ship.setTarget(target);
        hostile.setTarget(target);
        ship.setStateEmotion(ID.S.Phase, 1, false);
        hostile.setStateEmotion(ID.S.Phase, 1, false);
        target.setHealth(0F);
        helper.assertTrue(!target.isAlive(), "Dead target fixture");
        ship.aiStep();
        hostile.aiStep();
        helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0 && hostile.getStateEmotion(ID.S.Phase) == 0,
                "A dead target must clear both charges");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "yamato_charge_lifecycle")
    public static void chargedYamatoHoldsLightFireUntilTheBeamIsShot(GameTestHelper helper) {
        EntityBattleshipYamato ship = friendly(helper);
        EntityBattleshipYamatoMob hostile = hostile(helper);
        helper.assertTrue(!ship.holdsLightFire() && !hostile.holdsLightFire(), "Uncharged Yamato fires light cannons");
        ship.setStateEmotion(ID.S.Phase, 1, false);
        hostile.setStateEmotion(ID.S.Phase, 1, false);
        helper.assertTrue(ship.holdsLightFire() && hostile.holdsLightFire(), "Charged Yamato holds light fire");
        ship.setStateEmotion(ID.S.Phase, 0, false);
        hostile.setStateEmotion(ID.S.Phase, 0, false);
        helper.assertTrue(!ship.holdsLightFire() && !hostile.holdsLightFire(), "Light fire resumes after the shot");
        helper.succeed();
    }

    private static EntityBattleshipYamato friendly(GameTestHelper helper) {
        EntityBattleshipYamato ship = ModEntities.BB_YAMATO.get().create(helper.getLevel());
        ship.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)), 0F, 0F);
        ship.setNoAi(true);
        ship.tickCount = 25;
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setAmmoHeavy(10_000);
        helper.assertTrue(ship.getAmmoHeavy() == 10_000 && ship.getStateMinor(ID.M.NumGrudge) > 0
                && !ship.getStateFlag(ID.F.NoFuel), "Friendly ammo and fuel fixture");
        return ship;
    }

    private static EntityBattleshipYamatoMob hostile(GameTestHelper helper) {
        EntityBattleshipYamatoMob ship = ModEntities.BB_YAMATO_MOB.get().create(helper.getLevel());
        ship.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)), 0F, 0F);
        ship.setNoAi(true);
        ship.tickCount = 25;
        return ship;
    }

    private static Zombie target(GameTestHelper helper) {
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        target.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 1)), 0F, 0F);
        target.setNoAi(true);
        helper.assertTrue(target.isAlive(), "Target fixture");
        return target;
    }
}
