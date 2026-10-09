package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFloatingGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipGuardian;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Whether a ship counts as standing at its guard place, which keeps it from floating up, is the
 * same under both authorities for every order: following, a one-shot move, a guarded block and a
 * guarded entity, each inside the distance, outside it, and below the guarded block.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardPositionCheckGameTests {
    private static final Vec3 SHIP = new Vec3(2.5D, 2D, 2.5D);
    private static final int FOLLOW_MIN = 2;
    private static final int FOLLOW_MAX = 4;

    private GuardPositionCheckGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void shipGuardPositionCheckAgreesAcrossAuthorities(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verify(helper, false), SHIP);
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void mountGuardPositionCheckAgreesAcrossAuthorities(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verify(helper, true), SHIP);
    }

    private static void verify(GameTestHelper helper, boolean mounted) {
        GameTestEntities entities = GameTestEntities.open(helper);
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "guard_position_owner"));
        boolean ownerAdded = false;
        BlockPos roof = null;
        try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Failed to create a friendly ship");
            ship.setNoAi(true);
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.setStateMinor(ID.M.FollowMin, FOLLOW_MIN);
            ship.setStateMinor(ID.M.FollowMax, FOLLOW_MAX);
            ship.calcShipAttributes(31, false);
            ship.moveTo(helper.absoluteVec(SHIP));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add a friendly ship");
            ship.enableCommandProjectionCheckForTest();
            helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == FOLLOW_MIN
                            && ship.getStateMinor(ID.M.FollowMax) == FOLLOW_MAX,
                    "Fixture must keep the follow distances it set");

            owner.moveTo(ship.position());
            helper.getLevel().addNewPlayer(owner);
            ownerAdded = true;
            ship.setOwnerUUID(owner.getUUID());
            helper.assertTrue(ship.getHostEntity() == owner, "Fixture must resolve the owner the ship follows");

            IShipGuardian host = ship;
            Entity body = ship;
            if (mounted) {
                BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
                helper.assertTrue(mount != null, "Failed to create a mount");
                mount.setNoAi(true);
                mount.moveTo(ship.position());
                helper.assertTrue(helper.getLevel().addFreshEntity(mount), "Failed to add a mount");
                mount.setHost(ship);
                helper.assertTrue(ship.startRiding(mount, true), "Fixture must mount the ship");
                helper.assertTrue(mount.getHost() == ship && mount.getHostEntity() == ship,
                        "Fixture mount must act for the ship it carries");
                host = mount;
                body = mount;
            }

            // with air above, the check answers false whatever the order is
            BlockPos at = body.blockPosition();
            roof = at.above();
            helper.getLevel().setBlockAndUpdate(roof, Blocks.GLASS.defaultBlockState());
            helper.assertTrue(!helper.getLevel().getBlockState(roof).isAir(), "Fixture must put a block above the host");
            helper.assertTrue(at.equals(BlockPos.containing(helper.absoluteVec(SHIP))),
                    "Fixture host must stand where it was placed, got " + at);

            List<String> failures = new ArrayList<>();

            command(ship, new ShipCommand.Follow());
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow
                    && ship.getStateFlag(ID.F.CanFollow), "Fixture must follow");
            if (mounted) {
                // a mount follows the ship on its back, which is always beside it
                expect(failures, "follow carried ship", host, true);
            } else {
                owner.moveTo(ship.getX() + 2D, ship.getY(), ship.getZ());
                expect(failures, "follow inside", host, true);
                owner.moveTo(ship.getX() + 9D, ship.getY(), ship.getZ());
                expect(failures, "follow outside", host, false);
            }

            for (boolean oneShot : new boolean[]{true, false}) {
                String order = oneShot ? "move" : "guard block";
                position(helper, ship, at, oneShot);
                expect(failures, order + " at the block", host, true);
                position(helper, ship, at.below(), oneShot);
                expect(failures, order + " above the block", host, true);
                position(helper, ship, at.offset(8, 0, 0), oneShot);
                expect(failures, order + " outside", host, false);
                position(helper, ship, at.above(), oneShot);
                expect(failures, order + " below the block", host, false);
            }

            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            helper.assertTrue(cow != null, "Failed to create a cow");
            cow.setNoAi(true);
            cow.moveTo(body.getX() + 1D, body.getY(), body.getZ());
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Failed to add a cow");
            command(ship, new ShipCommand.GuardEntity(ShipCommandStateAdapter.handle(cow)));
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardEntity
                            && ship.getGuardedEntity() == cow && ship.getGuardedPos(4) == 2
                            && !ship.getStateFlag(ID.F.CanFollow),
                    "Fixture must guard the cow");
            expect(failures, "guard entity inside", host, true);
            cow.moveTo(body.getX() + 9D, body.getY(), body.getZ());
            expect(failures, "guard entity outside", host, false);

            helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
            helper.succeed();
        } catch (AssertionError error) {
            // an error from a sequence step would stop the server instead of failing this test
            throw new GameTestAssertException(String.valueOf(error.getMessage()));
        } finally {
            if (roof != null) {
                helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
            }
            if (ownerAdded) {
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            }
            entities.close();
        }
    }

    /** Orders the ship to a block and checks that both the order and the fields the old goals read hold it. */
    private static void position(GameTestHelper helper, BasicEntityShip ship, BlockPos pos, boolean oneShot) {
        var id = helper.getLevel().dimension().location();
        DimensionKey dimension = new DimensionKey(id.getNamespace(), id.getPath());
        CommandPos target = new CommandPos(pos.getX(), pos.getY(), pos.getZ());
        command(ship, oneShot ? new ShipCommand.Move(dimension, target, true)
                : new ShipCommand.GuardPosition(dimension, target, false));
        MovementOrder order = ship.getCommandState().movement();
        helper.assertTrue(oneShot ? order instanceof MovementOrder.MoveTo : order instanceof MovementOrder.GuardPosition,
                "Fixture order was not taken: " + order);
        helper.assertTrue(!ship.getStateFlag(ID.F.CanFollow) && ship.getGuardedEntity() == null
                        && ship.getGuardedPos(0) == pos.getX() && ship.getGuardedPos(1) == pos.getY()
                        && ship.getGuardedPos(2) == pos.getZ() && ship.getGuardedPos(4) == (oneShot ? 0 : 1),
                "Fixture order did not reach the guard fields");
    }

    private static void command(BasicEntityShip ship, ShipCommand command) {
        ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(command));
    }

    private static void expect(List<String> failures, String name, IShipGuardian host, boolean expected) {
        boolean legacy;
        boolean modern;
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
            legacy = ShipFloatingGoal.isInGuardPosition(host);
        }
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
            modern = ShipFloatingGoal.isInGuardPosition(host);
        }
        if (legacy != expected || modern != expected) {
            failures.add(name + ": expected=" + expected + " legacy=" + legacy + " new=" + modern);
        }
    }
}
