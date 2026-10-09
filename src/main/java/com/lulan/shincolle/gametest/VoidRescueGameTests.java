package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.TeleportDenial;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VoidRescueGameTests {
    private static final TicketType<UUID> FIXTURE = TicketType.create("ship_void_rescue_fixture", UUID::compareTo);

    private VoidRescueGameTests() { }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_void_fall", timeoutTicks = 600)
    public static void endNaturalFallReturnsWithinFortyFallingTicks(GameTestHelper helper) {
        scene(helper, false, false, false, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_void_early", timeoutTicks = 600)
    public static void negativeYRescuesBeforeFortyTicksAndBypassesCooldownTwice(GameTestHelper helper) {
        scene(helper, true, false, false, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_void_airborne_owner", timeoutTicks = 600)
    public static void airborneOwnerDefersRescueUntilTwentyTickRetry(GameTestHelper helper) {
        scene(helper, true, true, false, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_void_disabled", timeoutTicks = 600)
    public static void emptyDimensionListDisablesRescue(GameTestHelper helper) {
        scene(helper, true, false, true, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_void_legacy", timeoutTicks = 600)
    public static void legacyFallDoesNotUseOwnerRescue(GameTestHelper helper) {
        scene(helper, false, false, false, true);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_void_overworld", timeoutTicks = 600)
    public static void defaultOverworldFallDoesNotRescue(GameTestHelper helper) {
        scene(helper, false, false, false, false, true);
    }

    private static void scene(GameTestHelper helper, boolean early, boolean airborne, boolean disabled, boolean legacy) {
        scene(helper, early, airborne, disabled, legacy, false);
    }

    private static void scene(GameTestHelper helper, boolean early, boolean airborne,
                              boolean disabled, boolean legacy, boolean overworld) {
        ShipAiAuthorityOverride.useUntilComplete(helper, legacy ? ConfigHandler.ShipAiTargetAuthority.LEGACY
                : ConfigHandler.ShipAiTargetAuthority.NEW);
        List<? extends String> previous = ConfigHandler.COMMON.voidRescueDimensions.get();
        ConfigHandler.COMMON.voidRescueDimensions.set(disabled ? List.of() : List.of("minecraft:the_end"));
        VerticalSeparationRecallGameTests.cleanup(helper, () -> ConfigHandler.COMMON.voidRescueDimensions.set(previous));
        helper.assertTrue(ConfigHandler.COMMON.voidRescueDimensions.get().equals(disabled ? List.of() : List.of("minecraft:the_end")),
                "Rescue config fixture did not apply");
        ServerLevel level = helper.getLevel().getServer().getLevel(overworld ? Level.OVERWORLD : Level.END);
        helper.assertTrue(level != null, "Target dimension missing");
        Vec3 marker = helper.absoluteVec(new Vec3(8192.5D, 0D, 8192.5D));
        BlockPos column = new BlockPos((int) marker.x, 200, (int) marker.z);
        ChunkPos chunk = new ChunkPos(column);
        UUID ticket = UUID.randomUUID();
        level.getChunkSource().addRegionTicket(FIXTURE, chunk, 3, ticket);
        VerticalSeparationRecallGameTests.cleanup(helper, () -> level.getChunkSource().removeRegionTicket(FIXTURE, chunk, 3, ticket));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.getChunk(chunk.x + x, chunk.z + z);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(column.offset(12, 0, 0)),
                "Waiting for rescue fixture chunk")).thenExecute(() -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            ServerPlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "void_rescue"));
            owner.moveTo(column.getX() + 0.5D, column.getY(), column.getZ() + 0.5D);
            owner.setOnGround(!airborne);
            level.addNewPlayer(owner);
            VerticalSeparationRecallGameTests.cleanup(helper,
                    () -> level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED));
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                level.setBlockAndUpdate(column.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(level));
            helper.assertTrue(ship != null, "Could not create falling ship");
            ship.setPlayerUID(15500);
            ship.setShipUID(1550000);
            ship.setNoAi(false);
            ship.setPos(owner.getX() + 12D, 200D, owner.getZ());
            VerticalSeparationRecallGameTests.prepare(helper, ship, owner);
            helper.assertTrue(level.addFreshEntity(ship), "Could not add falling ship");
            long begun = helper.getTick();
            boolean[] released = {false};
            int[] fallTicks = {0};
            boolean[] rescued = {false};
            long[] firstRescue = {0};
            helper.onEachTick(() -> {
                if (!released[0] || rescued[0]) return;
                if (ship.distanceToSqr(owner) < 16D) {
                    rescued[0] = true;
                    firstRescue[0] = helper.getTick();
                    helper.assertTrue(!disabled && !legacy && !overworld, "Rescue ran when it should be disabled");
                    helper.assertTrue(fallTicks[0] <= (airborne ? 24 : 40), "Rescue arrived late: " + fallTicks[0]);
                    helper.assertTrue(ship.shipMovementExecutor().last().orElseThrow().step().reason() == MovementReason.VOID_RESCUE,
                            "Return was not made by the rescue executor");
                    helper.assertTrue(ship.getDeltaMovement().y >= -0.08D && ship.fallDistance == 0F,
                            "Rescue did not clear falling motion");
                } else if (!ship.onGround() && ship.getDeltaMovement().y < 0D) {
                    fallTicks[0]++;
                }
            });
            helper.runAtTickTime(begun + 25, () -> {
                helper.assertTrue(ship.tickCount >= 24 && level.getEntity(ship.getUUID()) == ship,
                        "Falling fixture was not ticking before release");
                MovementPlanParityGameTests.selector(ship).removeAllGoals(goal -> true);
                ship.setNoGravity(false);
                ship.setOnGround(false);
                if (early) {
                    MovementPoint at = new MovementPoint(ship.getX(), ship.getY(), ship.getZ());
                    ShipMovementExecutor.run(ship, MovementPlan.of(new MovementStep.Teleport(MovementBody.SELF, at,
                            MovementReason.COMMAND_APPLIED, ShipCommandStateAdapter.handle(ship).dimension())));
                    helper.assertTrue(ship.shipMovementExecutor().lastDenials().isEmpty(), "First cooldown-setting teleport failed");
                    ship.setPos(ship.getX(), -1D, ship.getZ());
                    ship.setDeltaMovement(new Vec3(0D, -0.1D, 0D));
                }
                released[0] = true;
            });
            if (airborne) {
                helper.runAtTickTime(begun + 32, () -> {
                    helper.assertTrue(!rescued[0] && ship.getY() < 0D, "Airborne owner was used as a landing");
                    owner.setOnGround(true);
                    helper.assertTrue(owner.onGround(), "Owner did not become grounded");
                });
                helper.runAtTickTime(begun + 41, () -> helper.assertTrue(!rescued[0], "Retry did not wait twenty ticks"));
            }
            helper.runAtTickTime(begun + 80, () -> {
                if (disabled || legacy || overworld) {
                    helper.assertTrue(!rescued[0] && ship.getY() < 160D, "Excluded ship did not fall without owner rescue");
                    helper.succeed();
                } else {
                    helper.assertTrue(rescued[0], "Falling ship was not rescued; at=" + ship.position() + ", fallTicks=" + fallTicks[0]);
                    if (!early || airborne) {
                        helper.succeed();
                    } else {
                        helper.assertTrue(helper.getTick() - firstRescue[0] < 100, "Second rescue fixture missed the cooldown");
                        ship.setPos(owner.getX() + 12D, -1D, owner.getZ());
                        ship.setOnGround(false);
                        ship.setDeltaMovement(new Vec3(0D, -0.1D, 0D));
                        rescued[0] = false;
                        fallTicks[0] = 0;
                    }
                }
            });
            if (early && !airborne && !disabled && !legacy && !overworld) {
                helper.runAtTickTime(begun + 84, () -> {
                    helper.assertTrue(rescued[0], "Second rescue was refused by the teleport cooldown");
                    helper.assertTrue(!ship.shipMovementExecutor().lastDenials().contains(TeleportDenial.COOLDOWN),
                            "Second rescue retained a cooldown denial");
                    helper.succeed();
                });
            }
        });
    }
}
