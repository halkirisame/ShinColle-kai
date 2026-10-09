package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFormationStateAdapter;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.domain.formation.FormationProjection;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import com.lulan.shincolle.utility.FormationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FormationProjectionGameTests {
    private FormationProjectionGameTests() { }

    @GameTest(template = "arena")
    public static void unresolvedFormationDoesNotMoveOrBuffAndFormalDisableWins(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var context = PointerSingleModeGameTests.createContext(helper, "formation_projection", 26403);
                 var online = new FormationGameTestOwner(context.player())) {
                BasicEntityShip host = PointerSingleModeGameTests.addShip(context, 0, 2640300,
                        new Vec3(4.5D, 2D, 1.5D));
                context.capa().setFormatID(0, 1);
                for (int slot = 1; slot < 5; slot++) context.capa().setTeamMember(0, slot, 2640300 + slot);
                host.setStateMinor(ID.M.FormatType, 1);
                host.calcShipAttributes(16, false);
                helper.assertTrue(host.getStateMinor(ID.M.FormatType) == 1
                                && host.formationState().current() instanceof FormationProjection.Pending,
                        "Unresolved members must preserve the saved setting without activating it");
                helper.assertTrue(!ShipMovementGate.settings(host).formation()
                                && ((AttrsAdv) host.getAttrs()).getMinMOV() == 0F,
                        "Pending settings must grant neither formation movement nor minimum speed");
                for (int slot = 1; slot < 5; slot++) PointerSingleModeGameTests.addShip(context, slot,
                        2640300 + slot, new Vec3(4.5D + slot, 2D, 1.5D));
                host.setStateMinor(ID.M.FormatType, 0);
                host.calcShipAttributes(16, false);
                helper.assertTrue(host.getStateMinor(ID.M.FormatType) == 1 && ShipMovementGate.settings(host).formation(),
                        "Official team must restore a zero raw type after five members resolve");
                context.capa().setFormatID(0, 0);
                host.calcShipAttributes(16, false);
                helper.assertTrue(host.getStateMinor(ID.M.FormatType) == 0 && !ShipMovementGate.settings(host).formation(),
                        "Formal disable must defeat the previous active projection");
                context.capa().setFormatID(0, 7);
                host.setStateMinor(ID.M.FormatType, 7);
                host.calcShipAttributes(16, false);
                helper.assertTrue(host.getStateMinor(ID.M.FormatType) == 7
                                && !ShipMovementGate.settings(host).formation(), "Unknown format must remain raw and inactive");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "arena")
    public static void allFormationSlotsKeepSafePlacementAndMountAnchorParity(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var context = PointerSingleModeGameTests.createContext(helper, "formation_layout", 26404);
                 var online = new FormationGameTestOwner(context.player())) {
                List<BasicEntityShip> ships = new ArrayList<>();
                for (int slot = 0; slot < 6; slot++) ships.add(PointerSingleModeGameTests.addShip(context, slot,
                        2640400 + slot, new Vec3(4.5D + slot, 2D, 1.5D)));
                BlockPos anchor = helper.absolutePos(new BlockPos(8, 2, 8));
                BasicEntityMount mount = context.entities().add(ModEntities.MOUNT_BAH.get().create(context.level()));
                mount.setNoAi(true);
                mount.moveTo(ships.get(0).position());
                helper.assertTrue(context.level().addFreshEntity(mount), "Mount fixture must register");
                mount.setHost(ships.get(0));
                for (int type = 1; type <= 5; type++) {
                    context.capa().setFormatID(0, type);
                    for (int slot = 0; slot < 6; slot++) {
                        BasicEntityShip ship = ships.get(slot);
                        ship.setStateMinor(ID.M.FormatType, type);
                        ship.setStateMinor(ID.M.FormatPos, slot);
                        ship.calcShipAttributes(16, false);
                        helper.assertTrue(ShipFormationStateAdapter.active(ship).isPresent(), "Real six-member formation must activate");
                        for (boolean alongX : new boolean[]{true, false}) for (boolean positive : new boolean[]{true, false}) {
                            double[] position = {anchor.getX(), anchor.getY(), anchor.getZ()};
                            int[] expected;
                            try (var legacy = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
                                expected = FormationHelper.calcFormationPos(type, slot, position, new boolean[]{alongX, positive});
                            }
                            helper.assertTrue(Arrays.equals(expected, FormationHelper.calcFormationPos(type, slot,
                                            position, new boolean[]{alongX, positive})), "Formation geometry must preserve every slot and direction");
                            if (type == 2 || type == 3 || type == 5) {
                                try (var legacy = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
                                    expected = FormationHelper.calculateFormationPosition2(ship, type, alongX, positive,
                                            anchor.getX(), anchor.getY(), anchor.getZ(), context.level());
                                }
                                helper.assertTrue(Arrays.equals(expected, FormationHelper.calculateFormationPosition2(ship,
                                        type, alongX, positive, anchor.getX(), anchor.getY(), anchor.getZ(), context.level())),
                                        "Safe placement and failed-search fallback must remain identical");
                            }
                        }
                    }
                    double[] shipPlace = FormationHelper.getFormationGuardingPos(ships.get(0), context.player(), 0, 0);
                    helper.assertTrue(Arrays.equals(shipPlace, FormationHelper.getFormationGuardingPos(mount,
                                    context.player(), 0, 0)), "Mount must use its host's effective formation slot");
                }
                helper.succeed();
            }
        });
    }
}
