package com.lulan.shincolle.command;

import com.lulan.shincolle.ai.diag.TickStallProbe;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCmdTickProbeGameTests {
    private ShipCmdTickProbeGameTests() {
    }

    @GameTest(template = "arena", batch = "ship_tick_probe")
    public static void tickProbeRecordsNothingWhileOff(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TickStallProbe.disable();
            TickStallProbe.reset();
            BasicEntityShip ship = createShip(helper, entities);
            Vec3 target = helper.absoluteVec(new Vec3(8.5D, 2D, 2.5D));
            helper.assertTrue(ship.getNavigation().moveTo(target.x, target.y, target.z, 1D),
                    "Fixture must be able to start a path");
            ship.tick();
            helper.assertTrue(TickStallProbe.pendingEntities() == 0, "An off probe must record no entity");
            helper.assertTrue(TickStallProbe.pendingPathRequests() == 0L, "An off probe must record no path request");
            helper.succeed();
        } finally {
            TickStallProbe.disable();
            TickStallProbe.reset();
        }
    }

    @GameTest(template = "arena", batch = "ship_tick_probe")
    public static void tickProbeRecordsPathRequestAndEntityTickWhileOn(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TickStallProbe.reset();
            BasicEntityShip ship = createShip(helper, entities);
            TickStallProbe.enable(50);
            Vec3 target = helper.absoluteVec(new Vec3(8.5D, 2D, 2.5D));
            helper.assertTrue(ship.getNavigation().moveTo(target.x, target.y, target.z, 1D),
                    "Fixture must be able to start a path");
            helper.assertTrue(TickStallProbe.pendingPathRequests() >= 1L, "The path request must be recorded");
            ship.tick();
            helper.assertTrue(TickStallProbe.pendingEntities() >= 1, "The ship's tick must be recorded");
            helper.succeed();
        } finally {
            TickStallProbe.disable();
            TickStallProbe.reset();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void tickProbeCommandSwitchesAndReports(GameTestHelper helper) {
        try {
            MinecraftServer server = helper.getLevel().getServer();
            run(server, "shiptickprobe off");
            helper.assertTrue(!TickStallProbe.isEnabled(), "off must leave the probe disabled");
            run(server, "shiptickprobe on 30");
            helper.assertTrue(TickStallProbe.isEnabled(), "on must enable the probe");
            helper.assertTrue(TickStallProbe.thresholdMs() == 30, "on 30 must set the threshold");
            run(server, "shiptickprobe report");
            run(server, "shiptickprobe reset");
            helper.assertTrue(TickStallProbe.cumulativeTicks() == 0L, "reset must clear the tick count");
            run(server, "shiptickprobe off");
            helper.assertTrue(!TickStallProbe.isEnabled(), "off must disable the probe");
            helper.succeed();
        } finally {
            TickStallProbe.disable();
            TickStallProbe.reset();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void tickProbeCountsServerTicksWhileOn(GameTestHelper helper) {
        TickStallProbe.disable();
        TickStallProbe.reset();
        TickStallProbe.enable(1);
        // Safety net: a failing run must not leave the probe on for the tests that follow.
        helper.runAfterDelay(60L, () -> {
            TickStallProbe.disable();
            TickStallProbe.reset();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(TickStallProbe.cumulativeTicks() >= 2L,
                    "Server tick events must reach the probe: ticks=" + TickStallProbe.cumulativeTicks());
            TickStallProbe.disable();
            TickStallProbe.reset();
        });
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static BasicEntityShip createShip(GameTestHelper helper, GameTestEntities entities) {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 6; y++) {
                    boolean wall = x == 0 || x == 11 || z == 0 || z == 5;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        BasicEntityShip ship = entities.add(ModEntities.BB_RE.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create Re-class ship");
        ship.calcShipAttributes(31, false);
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateFlag(ID.F.PickItem, false);
        ship.setStateFlag(ID.F.PassiveAI, true);
        ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 2.5D)));
        ship.setOnGround(true);
        return ship;
    }
}
