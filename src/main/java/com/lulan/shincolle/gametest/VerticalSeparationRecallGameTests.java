package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.TeleportDenial;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VerticalSeparationRecallGameTests {
    private static final Vec3 HOME = new Vec3(2.5D, 2D, 1.5D);

    private VerticalSeparationRecallGameTests() { }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_formation", timeoutTicks = 90)
    public static void formationBlockCommandUsesEachShipsAssignedLanding(GameTestHelper helper) {
        fixture(helper, 15009, false, (context, ships) -> {
            FormationGameTestOwner online = new FormationGameTestOwner(context.player());
            cleanup(helper, online::close);
            for (int slot = 1; slot < 6; slot++) {
                BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, 1500900 + slot, HOME.add(slot, 0, 0));
                prepare(helper, ship, context.player());
                ships.add(ship);
            }
            context.capa().setFormatID(0, 1);
            for (int slot = 0; slot < ships.size(); slot++) {
                ships.get(slot).setStateMinor(ID.M.FormatType, 1);
                ships.get(slot).setStateMinor(ID.M.FormatPos, slot);
            }
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> {
                for (BasicEntityShip ship : ships) {
                    ship.calcShipAttributes(16, false);
                    helper.assertTrue(com.lulan.shincolle.ai.ShipFormationStateAdapter.active(ship).isPresent(),
                            "Six-ship formation fixture did not activate");
                    ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ());
                }
            });
            helper.runAtTickTime(begun + 29, () -> {
                BlockPos to = context.player().blockPosition().offset(4, 0, 2);
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_FORMATION, 0, to.getX(), to.getY(), to.getZ(), 1}),
                        "handleSetMove", context.player());
                java.util.Set<BlockPos> assigned = new java.util.HashSet<>();
                for (BasicEntityShip ship : ships) {
                    helper.assertTrue(ship.hasGuardDestination(), "Formation command did not assign a point");
                    BlockPos point = new BlockPos(ship.getGuardedPos(0), ship.getGuardedPos(1), ship.getGuardedPos(2));
                    assigned.add(point);
                    assertNear(helper, ship, point);
                }
                helper.assertTrue(assigned.size() == 6, "Formation command collapsed all landings to the clicked point");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_group", timeoutTicks = 90)
    public static void groupBlockCommandRecallsSixOwnedShips(GameTestHelper helper) {
        fixture(helper, 15002, false, (context, ships) -> {
            for (int slot = 1; slot < 6; slot++) {
                BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, 1500200 + slot, HOME.add(slot, 0, 0));
                prepare(helper, ship, context.player());
                ships.add(ship);
            }
            PointerSingleModeGameTests.select(context.capa(), 0, 1, 2, 3, 4, 5);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> ships.forEach(ship -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ())));
            helper.runAtTickTime(begun + 29, () -> {
                BlockPos to = context.player().blockPosition().offset(4, 0, 2);
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_GROUP, 1, to.getX(), to.getY(), to.getZ(), 1}),
                        "handleSetMove", context.player());
                for (BasicEntityShip ship : ships) assertNear(helper, ship, to);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_excluded", timeoutTicks = 90)
    public static void seatedLeashedAndCraneShipsKeepNormalCommandBehavior(GameTestHelper helper) {
        fixture(helper, 15003, false, (context, ships) -> {
            BasicEntityShip seated = ships.get(0);
            BasicEntityShip leashed = PointerSingleModeGameTests.addShip(context, 1, 1500301, HOME.add(1D, 0D, 0D));
            BasicEntityShip crane = PointerSingleModeGameTests.addShip(context, 2, 1500302, HOME.add(2D, 0D, 0D));
            prepare(helper, leashed, context.player());
            prepare(helper, crane, context.player());
            ships.add(leashed);
            ships.add(crane);
            PointerSingleModeGameTests.select(context.capa(), 0, 1, 2);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> {
                ships.forEach(ship -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ()));
                seated.setEntitySit(true);
                leashed.setLeashedTo(context.player(), false);
                crane.setStateTimer(ID.T.CrandDelay, 0);
                crane.setStateMinor(ID.M.CraneState, 1);
                helper.assertTrue(seated.isOrderedToSit() && leashed.isLeashed() && crane.getStateMinor(ID.M.CraneState) == 1,
                        "Movement exclusions were not applied to fixtures");
            });
            helper.runAtTickTime(begun + 29, () -> {
                BlockPos to = context.player().blockPosition().offset(4, 0, 2);
                leashed.setLeashedTo(context.player(), false);
                helper.assertTrue(seated.isOrderedToSit() && leashed.isLeashed() && crane.getStateMinor(ID.M.CraneState) == 1,
                        "Movement exclusions did not hold at the command boundary");
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_GROUP, 0, to.getX(), to.getY(), to.getZ(), 1}),
                        "handleSetMove", context.player());
                for (BasicEntityShip ship : ships) helper.assertTrue(ship.getY() > context.player().getY() + 10D,
                        "A prohibited ship teleported");
                helper.assertTrue(!seated.isOrderedToSit(), "Existing pointer command did not stand the seated ship");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_return", timeoutTicks = 90)
    public static void returnedShipAndEntityPositionMoveDoNotTeleport(GameTestHelper helper) {
        fixture(helper, 15004, false, (context, ships) -> {
            BasicEntityShip ship = ships.get(0);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ()));
            helper.runAtTickTime(begun + 29, () -> {
                BlockPos to = context.player().blockPosition().offset(4, 0, 2);
                command(context, to, false);
                helper.assertTrue(ship.getY() > context.player().getY() + 10D, "Entity-position move consumed a recall");
                ship.setPos(ship.getX(), context.player().getY(), ship.getZ());
                command(context, to.offset(1, 0, 0), true);
                ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ());
                command(context, to.offset(2, 0, 0), true);
                helper.assertTrue(ship.getY() > context.player().getY() + 10D, "Returned ship kept the old recall window");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_expiry", timeoutTicks = 700)
    public static void expiredWindowLeavesBlockCommandWalking(GameTestHelper helper) {
        fixture(helper, 15005, false, (context, ships) -> {
            BasicEntityShip ship = ships.get(0);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ()));
            helper.runAtTickTime(begun + 630, () -> {
                command(context, context.player().blockPosition().offset(4, 0, 0), true);
                helper.assertTrue(ship.getY() > context.player().getY() + 10D, "Expired recall teleported");
                helper.assertTrue(ship.shipMovementExecutor().last().orElseThrow().step() instanceof MovementStep.PathTo,
                        "Expired recall did not retain the normal walk request");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_landing", timeoutTicks = 90)
    public static void blockedLandingRetainsWindowForAnotherDestination(GameTestHelper helper) {
        fixture(helper, 15006, false, (context, ships) -> {
            BasicEntityShip ship = ships.get(0);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ()));
            helper.runAtTickTime(begun + 29, () -> {
                BlockPos blocked = context.player().blockPosition().offset(9, 0, 0);
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y <= 5; y++) {
                    context.level().setBlockAndUpdate(blocked.offset(x, y, z), Blocks.STONE.defaultBlockState());
                }
                command(context, blocked, true);
                helper.assertTrue(ship.getY() > context.player().getY() + 10D, "Blocked landing teleported");
                helper.assertTrue(ship.shipMovementExecutor().lastDenials().contains(TeleportDenial.NO_FREE_SPACE),
                        "Fixture did not reject a genuinely blocked landing");
                BlockPos free = context.player().blockPosition().offset(3, 0, 4);
                command(context, free, true);
                assertNear(helper, ship, free);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_cooldown", timeoutTicks = 180)
    public static void recallRespectsCooldownWithoutConsumingWindow(GameTestHelper helper) {
        fixture(helper, 15007, false, (context, ships) -> {
            BasicEntityShip ship = ships.get(0);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ()));
            helper.runAtTickTime(begun + 29, () -> {
                ShipMovementExecutor.run(ship, MovementPlan.of(new MovementStep.Teleport(MovementBody.SELF,
                        new MovementPoint(ship.getX(), ship.getY(), ship.getZ()), MovementReason.COMMAND_APPLIED,
                        ShipCommandStateAdapter.handle(ship).dimension())));
                helper.assertTrue(ship.shipMovementExecutor().lastDenials().isEmpty(), "Fixture's first teleport failed");
                command(context, context.player().blockPosition().offset(4, 0, 0), true);
                helper.assertTrue(ship.getY() > context.player().getY() + 10D
                        && ship.shipMovementExecutor().lastDenials().contains(TeleportDenial.COOLDOWN),
                        "Recall did not honor the normal teleport cooldown");
            });
            helper.runAtTickTime(begun + 134, () -> {
                BlockPos free = context.player().blockPosition().offset(5, 0, 1);
                command(context, free, true);
                assertNear(helper, ship, free);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_vertical_legacy", timeoutTicks = 90)
    public static void legacySeparatedShipKeepsWalking(GameTestHelper helper) {
        fixture(helper, 15008, true, (context, ships) -> {
            BasicEntityShip ship = ships.get(0);
            long begun = helper.getTick();
            helper.runAtTickTime(begun + 25, () -> ship.setPos(ship.getX(), context.player().getY() + 12D, ship.getZ()));
            helper.runAtTickTime(begun + 29, () -> {
                command(context, context.player().blockPosition().offset(4, 0, 0), true);
                helper.assertTrue(ship.getY() > context.player().getY() + 10D, "LEGACY command gained a teleport");
                helper.succeed();
            });
        });
    }

    private static void fixture(GameTestHelper helper, int id, boolean legacy,
                                BiConsumer<PointerSingleModeGameTests.TestContext, List<BasicEntityShip>> body) {
        ShipAiAuthorityOverride.useUntilComplete(helper, legacy ? ConfigHandler.ShipAiTargetAuthority.LEGACY
                : ConfigHandler.ShipAiTargetAuthority.NEW);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, "vertical" + id, id);
            context.level().addNewPlayer(context.player());
            cleanup(helper, () -> context.level().removePlayerImmediately(context.player(),
                    net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, id * 100, HOME);
            prepare(helper, ship, context.player());
            PointerSingleModeGameTests.select(context.capa(), 0);
            List<BasicEntityShip> ships = new ArrayList<>();
            ships.add(ship);
            body.accept(context, ships);
        }, HOME, HOME.add(16D, 0D, 0D));
    }

    private static void assertNear(GameTestHelper helper, BasicEntityShip ship, BlockPos destination) {
        helper.assertTrue(ship.position().distanceTo(new Vec3(destination.getX(), destination.getY(), destination.getZ())) < 3D,
                "Recall did not reach the commanded destination: " + ship.position() + " vs " + destination);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "isolated_vertical_recall_physics", timeoutTicks = 180)
    public static void naturalFallThenBlockMoveRecallsSeparatedShip(GameTestHelper helper) {
        ShipAiAuthorityOverride.useUntilComplete(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, "vertical", 15001);
            ServerPlayer owner = context.player();
            context.level().addNewPlayer(owner);
            cleanup(helper, () -> context.level().removePlayerImmediately(owner, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 1500100, HOME);
            prepare(helper, ship, owner);
            PointerSingleModeGameTests.select(context.capa(), 0);
            long begun = helper.getTick();
            double[] distance = new double[3];
            double[] velocity = new double[3];
            double top = owner.getY() + 180D;
            helper.runAtTickTime(begun + 25, () -> {
                ship.setNoAi(false);
                MovementPlanParityGameTests.selector(ship).removeAllGoals(goal -> true);
                ship.setPos(owner.getX(), top, owner.getZ());
                ship.setNoGravity(false);
                ship.setDeltaMovement(Vec3.ZERO);
                ship.setOnGround(false);
            });
            for (int i = 0; i < 3; i++) {
                int index = i;
                int ticks = new int[]{40, 60, 70}[i];
                helper.runAtTickTime(begun + 25 + ticks, () -> {
                    distance[index] = top - ship.getY();
                    velocity[index] = ship.getDeltaMovement().y;
                    helper.assertTrue(distance[index] > 40D, "Natural falling fixture did not fall");
                });
            }
            helper.runAtTickTime(begun + 97, () -> {
                ship.setNoAi(true);
                ship.setNoGravity(true);
                ship.setDeltaMovement(Vec3.ZERO);
                ship.setPos(owner.getX(), owner.getY(), owner.getZ());
            });
            helper.runAtTickTime(begun + 120, () -> ship.setPos(owner.getX(), owner.getY() + 12D, owner.getZ()));
            helper.runAtTickTime(begun + 124, () -> {
                BlockPos destination = owner.blockPosition().offset(4, 0, 0);
                command(context, destination, true);
                helper.assertTrue(ship.position().distanceTo(new Vec3(destination.getX(), destination.getY(), destination.getZ())) < 3D,
                        "Separated block command did not teleport; measured natural fall distance="
                                + java.util.Arrays.toString(distance) + ", velocity=" + java.util.Arrays.toString(velocity));
                helper.succeed();
            });
        }, HOME);
    }

    static void prepare(GameTestHelper helper, BasicEntityShip ship, ServerPlayer owner) {
        ship.setOwnerUUID(owner.getUUID());
        ship.setInvulnerable(true);
        ship.setNoGravity(true);
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateMinor(ID.M.FleeHP, 0);
        ship.setStateFlag(ID.F.PassiveAI, true);
        helper.assertTrue(ship.getOwnerUUID().equals(owner.getUUID()) && ship.getOwner() == owner,
                "Owner fixture did not resolve");
        helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                "Ship fixture has no fuel");
    }

    static void command(PointerSingleModeGameTests.TestContext context, BlockPos at, boolean block) {
        int[] values = block
                ? new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 0, at.getX(), at.getY(), at.getZ(), 1}
                : new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 0, at.getX(), at.getY(), at.getZ()};
        PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove, values),
                "handleSetMove", context.player());
    }

    static void cleanup(GameTestHelper helper, Runnable action) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) { }
                @Override public void testPassed(GameTestInfo info) { action.run(); }
                @Override public void testFailed(GameTestInfo info) { action.run(); }
            });
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot register fixture cleanup", error);
        }
    }
}
