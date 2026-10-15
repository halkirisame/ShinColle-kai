package com.lulan.shincolle.gametest;

import com.lulan.shincolle.command.ShipCmdEmotes;
import com.lulan.shincolle.network.ParticlePacketRoute;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.reference.Reference;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCmdEmotesGameTests {
    private ShipCmdEmotesGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void commandBlockEmotionUsesBlockBaseAndExistingPositionPayload(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try {
                verifyCommandBlock(helper);
            } catch (Exception failure) {
                helper.assertTrue(false, "Command block verification failed: " + failure);
            }
        }, new Vec3(1, 1, 1), new Vec3(2.25D, 2.75D, 3.25D));
    }

    private static void verifyCommandBlock(GameTestHelper helper) throws Exception {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.COMMAND_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        helper.assertTrue(helper.getLevel().getBlockEntity(absolute) instanceof CommandBlockEntity,
                "command block fixture must exist");
        CommandBlockEntity commandBlock = (CommandBlockEntity) helper.getLevel().getBlockEntity(absolute);
        CommandSourceStack source = commandBlock.getCommandBlock().createCommandSourceStack();
        helper.assertTrue(source.getEntity() == null && source.getLevel() == helper.getLevel(),
                "command block source must be non-entity in this dimension");
        Vec3 executionPosition = helper.absoluteVec(new Vec3(2.25D, 2.75D, 3.25D));
        source = source.withPosition(executionPosition);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        helper.assertTrue(dispatcher.execute("shipemotes heart", source) == 1,
                "command block must execute a named emotion");
        BlockPos executionBlock = BlockPos.containing(executionPosition);
        assertPacket(helper, packet(source, 1),
                new Vec3(executionBlock.getX() + 0.5D, executionBlock.getY(), executionBlock.getZ() + 0.5D), 2, 1);
        helper.assertTrue(dispatcher.execute("em 34", source) == 1,
                "command block alias must accept the highest valid emotion ID");
        helper.assertTrue(dispatcher.execute("shipemotes 35", source) == 0,
                "out-of-range emotion must still be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void consoleEmotionKeepsExecutionPositionAndArgumentRules(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try {
                verifyConsole(helper);
            } catch (Exception failure) {
                helper.assertTrue(false, "Console verification failed: " + failure);
            }
        }, new Vec3(1.25D, 2.75D, 3.5D));
    }

    private static void verifyConsole(GameTestHelper helper) throws Exception {
        Vec3 position = helper.absoluteVec(new Vec3(1.25D, 2.75D, 3.5D));
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPosition(position);
        helper.assertTrue(source.getEntity() == null && source.getLevel() == helper.getLevel(),
                "console fixture must be non-entity in this dimension");
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        helper.assertTrue(dispatcher.execute("emo 34", source) == 1,
                "console must execute a numeric emotion through the alias");
        assertPacket(helper, packet(source, 34), position, 0, 34);
        helper.assertTrue(dispatcher.execute("shipemotes", source) == 1,
                "argument-free command must still list emotions");
        helper.assertTrue(dispatcher.execute("shipemotes unknown_emotion", source) == 0,
                "unknown emotion must still be rejected");
        helper.succeed();
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        ShipCmdEmotes.register(dispatcher);
        return dispatcher;
    }

    private static S2CSpawnParticlePacket packet(CommandSourceStack source, int emotion) throws Exception {
        Method factory = ShipCmdEmotes.class.getDeclaredMethod(
                "nonEntityEmotionPacket", CommandSourceStack.class, int.class);
        factory.setAccessible(true);
        return (S2CSpawnParticlePacket) factory.invoke(null, source, emotion);
    }

    private static void assertPacket(GameTestHelper helper, S2CSpawnParticlePacket packet,
                                     Vec3 position, int entityType, int emotion) {
        helper.assertTrue(packet.getType() == 36 && packet.getEntityId() == 0,
                "non-entity emotion must use existing type 36 without a host");
        helper.assertTrue(packet.route() == ParticlePacketRoute.POSITION && packet.getPayload().length == 48,
                "non-entity emotion must use the six-double position payload");
        ByteBuffer payload = ByteBuffer.wrap(packet.getPayload());
        helper.assertTrue(payload.getDouble() == position.x && payload.getDouble() == position.y
                        && payload.getDouble() == position.z,
                "emotion payload must preserve execution position");
        helper.assertTrue(payload.getDouble() == 0.5D && payload.getDouble() == entityType
                        && payload.getDouble() == emotion && !payload.hasRemaining(),
                "emotion payload must contain height, source type and requested emotion");
    }
}
