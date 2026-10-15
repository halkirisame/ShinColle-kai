package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipTankFillGameTests {

    private ShipTankFillGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_tank_fill")
    public static void allTankSizesCollectWaterAndLavaSources(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        try {
            List<ItemStack> tanks = List.of(new ItemStack(ModItems.SHIP_TANK.get()),
                    new ItemStack(ModItems.SHIP_TANK_1.get()), new ItemStack(ModItems.SHIP_TANK_2.get()),
                    new ItemStack(ModItems.SHIP_TANK_3.get()));
            int[] capacities = {32000, 128000, 512000, 2048000};
            for (int index = 0; index < tanks.size(); index++) {
                for (Fluid fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
                    ItemStack stack = tanks.get(index).copy();
                    fixture.hold(stack);
                    helper.assertTrue(handler(stack).getTankCapacity(0) == capacities[index], "Tank capacity fixture");
                    fixture.source(fluid);
                    helper.assertTrue(fixture.use().consumesAction(), "Source pickup must consume item use");
                    assertContents(helper, stack, fluid, 1000);
                    helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(), "Source must be removed");
                    fixture.assertPickupSound(helper, fluid == Fluids.WATER ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_FILL_LAVA);
                    ItemStack restored = ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
                    assertContents(helper, restored, fluid, 1000);
                }
            }
            helper.succeed();
        } finally {
            fixture.clear();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_tank_fill")
    public static void filledTankCollectsBeforeBlockPlacement(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        try {
            ItemStack stack = fixture.tank();
            helper.assertTrue(handler(stack).fill(new FluidStack(Fluids.WATER, 1500),
                    IFluidHandler.FluidAction.EXECUTE) == 1500, "Initial water fixture");
            fixture.source(Fluids.WATER);
            helper.assertTrue(fixture.useOn(fixture.pos.below()).consumesAction(), "Block path must collect source");
            assertContents(helper, stack, Fluids.WATER, 2500);
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).isAir(), "Block path must remove source");
            fixture.assertPickupSound(helper, SoundEvents.BUCKET_FILL);
            fixture.source(Fluids.WATER);
            helper.assertTrue(fixture.use().consumesAction(), "Air-use path must top up filled tank");
            assertContents(helper, stack, Fluids.WATER, 3500);
            fixture.assertPickupSound(helper, SoundEvents.BUCKET_FILL);
            helper.succeed();
        } finally {
            fixture.clear();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_tank_fill")
    public static void rejectedPickupPreservesSourceAndTank(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        try {
            for (int amount : new int[] {32000, 31500}) {
                ItemStack stack = fixture.tank();
                helper.assertTrue(handler(stack).fill(new FluidStack(Fluids.WATER, amount),
                        IFluidHandler.FluidAction.EXECUTE) == amount, "Capacity rejection fixture");
                fixture.source(Fluids.WATER);
                var before = stack.save(new net.minecraft.nbt.CompoundTag());
                helper.assertTrue(!fixture.useOn(fixture.pos.below()).consumesAction(), "Insufficient space must reject");
                helper.assertTrue(before.equals(stack.save(new net.minecraft.nbt.CompoundTag())), "Rejected tank is unchanged");
                helper.assertTrue(fixture.level.getFluidState(fixture.pos).isSource(), "Rejected source is unchanged");
                fixture.assertNoSound(helper);
            }
            ItemStack stack = fixture.tank();
            helper.assertTrue(handler(stack).fill(new FluidStack(Fluids.LAVA, 1000),
                    IFluidHandler.FluidAction.EXECUTE) == 1000, "Mixed-fluid fixture");
            fixture.source(Fluids.WATER);
            helper.assertTrue(!fixture.use().consumesAction(), "Different fluid must reject");
            assertContents(helper, stack, Fluids.LAVA, 1000);
            helper.assertTrue(fixture.level.getFluidState(fixture.pos).isSource(), "Mixed-fluid rejection retains source");
            fixture.assertNoSound(helper);
            helper.succeed();
        } finally {
            fixture.clear();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_tank_fill")
    public static void flowingFluidSolidAndMissDoNotFillTank(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        try {
            ItemStack stack = fixture.tank();
            for (BlockState state : List.of(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1),
                    Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 1),
                    Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState())) {
                fixture.level.setBlock(fixture.pos, state, 3);
                helper.assertTrue(!fixture.use().consumesAction(), "Non-source target must not collect");
                helper.assertTrue(handler(stack).getFluidInTank(0).isEmpty(), "Non-source target must not fill");
                helper.assertTrue(fixture.level.getBlockState(fixture.pos).equals(state), "Target must be unchanged");
                fixture.assertNoSound(helper);
            }
            helper.succeed();
        } finally {
            fixture.clear();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_tank_fill")
    public static void deniedPlayerCannotCollectSource(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, true);
        try {
            ItemStack stack = fixture.tank();
            fixture.source(Fluids.WATER);
            helper.assertTrue(!fixture.use().consumesAction(), "Denied air-use pickup must reject");
            fixture.assertNoSound(helper);
            helper.assertTrue(!fixture.useOn(fixture.pos.below()).consumesAction(), "Denied block-use pickup must reject");
            fixture.assertNoSound(helper);
            helper.assertTrue(handler(stack).getFluidInTank(0).isEmpty(), "Denied pickup must not fill");
            helper.assertTrue(fixture.level.getFluidState(fixture.pos).isSource(), "Denied pickup must retain source");
            helper.succeed();
        } finally {
            fixture.clear();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_tank_fill")
    public static void existingPlacementStillDrainsOneBucket(GameTestHelper helper) {
        for (Fluid fluid : new Fluid[] {Fluids.WATER, Fluids.LAVA}) {
            Fixture fixture = new Fixture(helper, false);
            try {
                ItemStack stack = fixture.tank();
                helper.assertTrue(handler(stack).fill(new FluidStack(fluid, 2500),
                        IFluidHandler.FluidAction.EXECUTE) == 2500, "Placement fixture");
                fixture.level.setBlock(fixture.pos, Blocks.STONE.defaultBlockState(), 3);
                helper.assertTrue(fixture.useOn(fixture.pos).consumesAction(), "Existing placement must succeed");
                assertContents(helper, stack, fluid, 1500);
                helper.assertTrue(fixture.level.getFluidState(fixture.pos.above()).isSource(), "Placement must create source");
                fixture.assertPlacementSound(helper);
            } finally {
                fixture.clear();
            }
        }
        helper.succeed();
    }

    private static IFluidHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                () -> new IllegalStateException("Tank fluid capability fixture"));
    }

    private static void assertContents(GameTestHelper helper, ItemStack stack, Fluid fluid, int amount) {
        FluidStack contents = handler(stack).getFluidInTank(0);
        helper.assertTrue(contents.getFluid() == fluid && contents.getAmount() == amount,
                "Expected fluid amount " + amount + ", actual " + contents.getAmount());
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos pos;
        private final FakePlayer player;
        private final FakePlayer nearby;
        private final SoundPackets operatorSounds;
        private final SoundPackets nearbySounds;

        private Fixture(GameTestHelper helper, boolean deny) {
            this.helper = helper;
            level = helper.getLevel();
            pos = helper.absolutePos(new BlockPos(1, 2, 1));
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "tank_fill")) {
                @Override
                public boolean mayUseItemAt(BlockPos target, Direction direction, ItemStack stack) {
                    return !deny && super.mayUseItemAt(target, direction, stack);
                }
            };
            player.setPos(pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5);
            player.setXRot(90);
            player.setYRot(0);
            nearby = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "tank_listener"));
            nearby.setPos(pos.getX() + 2.5, pos.getY() + 2, pos.getZ() + 0.5);
            operatorSounds = new SoundPackets(level, player);
            nearbySounds = new SoundPackets(level, nearby);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        }

        private ItemStack tank() {
            ItemStack stack = new ItemStack(ModItems.SHIP_TANK.get());
            hold(stack);
            return stack;
        }

        private void hold(ItemStack stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }

        private void source(Fluid fluid) {
            level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 3);
        }

        private InteractionResult use() {
            return recordUse(() -> player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND).getResult());
        }

        private InteractionResult useOn(BlockPos clicked) {
            return recordUse(() -> player.getMainHandItem().getItem().useOn(new UseOnContext(level, player,
                    InteractionHand.MAIN_HAND, player.getMainHandItem(),
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false))));
        }

        private InteractionResult recordUse(Supplier<InteractionResult> action) {
            operatorSounds.sounds.clear();
            nearbySounds.sounds.clear();
            // Broadcast uses this list, not the level's entity tracker. Register only during the synchronous item use.
            PlayerList playerList = level.getServer().getPlayerList();
            List<ServerPlayer> recipients = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class, playerList, "f_11196_");
            recipients.add(player);
            recipients.add(nearby);
            try {
                helper.assertTrue(playerList.getPlayers().contains(player) && playerList.getPlayers().contains(nearby),
                        "Both sound recipients must be registered for broadcast");
                return action.get();
            } finally {
                recipients.remove(player);
                recipients.remove(nearby);
            }
        }

        private void assertPickupSound(GameTestHelper helper, SoundEvent expected) {
            assertSound(helper, operatorSounds, expected, "Operator");
            assertSound(helper, nearbySounds, expected, "Nearby player");
        }

        private void assertSound(GameTestHelper helper, SoundPackets packets, SoundEvent expected, String recipient) {
            helper.assertTrue(packets.sounds.size() == 1,
                    recipient + " must receive exactly one pickup sound, actual " + packets.sounds.size());
            ClientboundSoundPacket sound = packets.sounds.get(0);
            helper.assertTrue(sound.getSound().value() == expected, recipient + " must receive the matching bucket sound");
            helper.assertTrue(sound.getSource() == SoundSource.PLAYERS, "Pickup sound category");
            helper.assertTrue(sound.getVolume() == 1F && sound.getPitch() == 1F, "Pickup sound volume and pitch");
            helper.assertTrue(sound.getX() == pos.getX() + 0.5 && sound.getY() == pos.getY() + 0.5
                    && sound.getZ() == pos.getZ() + 0.5, "Pickup sound must originate at the source block");
        }

        private void assertNoSound(GameTestHelper helper) {
            helper.assertTrue(operatorSounds.sounds.isEmpty() && nearbySounds.sounds.isEmpty(),
                    "Rejected pickup and placement must not send sound packets");
        }

        private void assertPlacementSound(GameTestHelper helper) {
            for (SoundPackets packets : new SoundPackets[] {operatorSounds, nearbySounds}) {
                helper.assertTrue(packets.sounds.size() == 1, "Placement must send exactly one sound to each player");
                ClientboundSoundPacket sound = packets.sounds.get(0);
                helper.assertTrue(sound.getSound().value() == SoundEvents.BUCKET_EMPTY, "Placement bucket sound");
                helper.assertTrue(sound.getSource() == SoundSource.BLOCKS, "Placement sound category");
                helper.assertTrue(sound.getVolume() == 1F && sound.getPitch() == 1F, "Placement sound volume and pitch");
                helper.assertTrue(sound.getX() == pos.getX() + 0.5 && sound.getY() == pos.getY() + 1.5
                        && sound.getZ() == pos.getZ() + 0.5, "Placement sound must originate at the placed block");
            }
        }

        private void clear() {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static final class SoundPackets extends ServerGamePacketListenerImpl {
        private final List<ClientboundSoundPacket> sounds = new ArrayList<>();

        private SoundPackets(ServerLevel level, FakePlayer player) {
            super(level.getServer(), new Connection(PacketFlow.CLIENTBOUND), player);
        }

        @Override
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundSoundPacket sound) {
                sounds.add(sound);
            }
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            send(packet);
        }
    }
}
