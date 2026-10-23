package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFormationStateAdapter;
import com.lulan.shincolle.ai.domain.formation.FormationProjection;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

/** A ship that belongs to two teams follows the team that has a formation, as the original did. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FormationSharedShipGameTests {
    private static final int BASE_UID = 2640500;

    private FormationSharedShipGameTests() { }

    /** Six ships in team 0, the first {@code shared} of them also in team 1. */
    private static List<BasicEntityShip> sharedTeams(PointerSingleModeGameTests.TestContext context, int shared) {
        List<BasicEntityShip> ships = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, BASE_UID + slot,
                    new Vec3(4.5D + slot, 2D, 1.5D));
            // Fuel and grudge keep the fixture from idling; no combat is expected here.
            ship.setStateMinor(ID.M.NumGrudge, 100);
            ships.add(ship);
        }
        for (int slot = 0; slot < shared; slot++) {
            context.capa().setTeamMember(1, slot, BASE_UID + slot);
            context.capa().setTeamSID(1, slot, ships.get(slot).getId());
        }
        return ships;
    }

    @GameTest(template = "arena")
    public static void sharedShipFollowsTheTeamThatHasAFormation(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var context = PointerSingleModeGameTests.createContext(helper, "formation_shared", 26405);
                 var online = new FormationGameTestOwner(context.player())) {
                List<BasicEntityShip> ships = sharedTeams(context, 6);
                context.capa().setFormatID(0, 0);
                context.capa().setFormatID(1, 3);
                helper.assertTrue(context.capa().getFormatID(0) == 0 && context.capa().getFormatID(1) == 3
                                && context.capa().getTeamMember(1, 0) == BASE_UID
                                && context.capa().getNumberOfShip(context.level(), 1) == 6,
                        "Fixture must put the same six ships in team 0 (no formation) and team 1 (formation 3)");
                for (BasicEntityShip ship : ships) {
                    ship.formationState().invalidate();
                    var active = ShipFormationStateAdapter.active(ship);
                    helper.assertTrue(active.isPresent() && active.get().pattern().legacyId() == 3,
                            "A ship in a team without a formation and a team with one must use the latter: "
                                    + ship.formationState().current());
                }
                // Both teams carry a formation: the lowest one with a formation wins, as before
                context.capa().setFormatID(0, 1);
                BasicEntityShip first = ships.get(0);
                first.formationState().invalidate();
                var both = ShipFormationStateAdapter.active(first);
                helper.assertTrue(both.isPresent() && both.get().pattern().legacyId() == 1,
                        "With a formation in both teams the first team must be used");
                // Nothing in any team has a formation: the old reason is kept
                context.capa().setFormatID(0, 0);
                context.capa().setFormatID(1, 0);
                first.formationState().invalidate();
                helper.assertTrue(first.formationState().current() instanceof FormationProjection.Inactive,
                        "Without any formation the ship must stay inactive");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "arena")
    public static void settingFormationResetsOtherTeamsSharingAShip(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var context = PointerSingleModeGameTests.createContext(helper, "formation_reset", 26406);
                 var online = new FormationGameTestOwner(context.player())) {
                // Team 1 holds ships 0-4 (slot 0 included), team 0 holds ships 0-5: ship 5 is only in team 0.
                List<BasicEntityShip> ships = sharedTeams(context, 5);
                context.capa().setFormatID(0, 2);
                for (BasicEntityShip ship : ships) ship.setStateMinor(ID.M.FormatType, 2);
                helper.assertTrue(context.capa().getFormatID(0) == 2
                                && ships.get(5).getStateMinor(ID.M.FormatType) == 2
                                && context.capa().getNumberOfShip(context.level(), 1) == 5,
                        "Fixture must give team 0 formation 2 and team 1 five ships");
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetFormation,
                        new int[]{context.player().getId(), 1, 3}), "handleSetFormation", context.player());
                helper.assertTrue(context.capa().getFormatID(1) == 3,
                        "The requested team must get its formation: " + context.capa().getFormatID(1));
                helper.assertTrue(context.capa().getFormatID(0) == 0,
                        "A team sharing a ship must lose its formation: " + context.capa().getFormatID(0));
                helper.assertTrue(ships.get(5).getStateMinor(ID.M.FormatType) == 0,
                        "A ship only in the reset team must get formation 0");
                for (int slot = 0; slot < 5; slot++) {
                    helper.assertTrue(ships.get(slot).getStateMinor(ID.M.FormatType) == 3,
                            "A shared ship must end with the requested team's formation, slot " + slot);
                }
                helper.succeed();
            }
        });
    }
}
