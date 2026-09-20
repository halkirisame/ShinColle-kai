package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MultiplayerSummonedHostileInfightingGameTests {
    private static final int FIRST_PLAYER_UID = 157_001;
    private static final int SECOND_PLAYER_UID = 157_002;

    private MultiplayerSummonedHostileInfightingGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void differentPlayerOwnedShipsAreNotAutomaticTargets(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = friendly(helper, entities, FIRST_PLAYER_UID);
            BasicEntityShip otherPlayerShip = friendly(helper, entities, SECOND_PLAYER_UID);

            helper.assertTrue(!new TargetHelper.Selector(source).test(otherPlayerShip),
                    "A friendly ship automatically targeted a ship owned by another player.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileShipsRemainAutomaticTargets(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = friendly(helper, entities, FIRST_PLAYER_UID);
            BasicEntityShipHostile hostile = entities.add(
                    ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
            helper.assertTrue(hostile != null, "Failed to create hostile ship fixture.");

            helper.assertTrue(new TargetHelper.Selector(source).test(hostile),
                    "A hostile ship stopped being an automatic target.");
            helper.succeed();
        }
    }

    private static BasicEntityShip friendly(
            GameTestHelper helper, GameTestEntities entities, int playerUid) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create friendly ship fixture.");
        ship.setNoAi(true);
        ship.setPlayerUID(playerUid);
        ship.setStateFlag(ID.F.OnSightChase, false);
        return ship;
    }
}
