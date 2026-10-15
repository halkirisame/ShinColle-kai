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
    /** The length and String hash of the recorded geometry of all formations (see the fixture below). */
    private static final int GEOMETRY_LENGTH = 2716;
    private static final int GEOMETRY_HASH = 1438877056;

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
    public static void allFormationSlotsKeepSafePlacementAndMountAnchor(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var context = PointerSingleModeGameTests.createContext(helper, "formation_layout", 26404);
                 var online = new FormationGameTestOwner(context.player())) {
                StringBuilder trace = new StringBuilder();
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
                            int[] placed = FormationHelper.calcFormationPos(type, slot, position,
                                    new boolean[]{alongX, positive});
                            trace.append(type).append('/').append(slot).append('/').append(alongX).append(positive)
                                    .append(Arrays.toString(relative(placed, anchor)));
                            if (type == 2 || type == 3 || type == 5) {
                                // The safe place is the formation place or one within reach of it that can be stood on,
                                // else the anchor: it depends on the blocks around, so it is not recorded.
                                int[] safe = FormationHelper.calculateFormationPosition2(ship, type, alongX, positive,
                                        anchor.getX(), anchor.getY(), anchor.getZ(), context.level());
                                boolean nearPlace = Math.abs(safe[0] - placed[0]) <= 3 && Math.abs(safe[1] - placed[1]) <= 4
                                        && Math.abs(safe[2] - placed[2]) <= 3;
                                helper.assertTrue(nearPlace || Arrays.equals(safe, new int[]{anchor.getX(), anchor.getY(),
                                                anchor.getZ()}),
                                        "Safe placement must stay near the formation place or fall back to the anchor: "
                                                + Arrays.toString(safe) + " for " + Arrays.toString(placed));
                            }
                        }
                    }
                    double[] shipPlace = FormationHelper.getFormationGuardingPos(ships.get(0), context.player(), 0, 0);
                    helper.assertTrue(Arrays.equals(shipPlace, FormationHelper.getFormationGuardingPos(mount,
                                    context.player(), 0, 0)), "Mount must use its host's effective formation slot");
                }
                // Every slot and direction of every formation, relative to the anchor, as recorded
                helper.assertTrue(trace.length() == GEOMETRY_LENGTH && trace.toString().hashCode() == GEOMETRY_HASH,
                        "Formation geometry differs from the recorded one: length=" + trace.length()
                                + " hash=" + trace.toString().hashCode());
                helper.succeed();
            }
        });
    }

    private static int[] relative(int[] placed, BlockPos anchor) {
        return new int[]{placed[0] - anchor.getX(), placed[1] - anchor.getY(), placed[2] - anchor.getZ()};
    }
}
