package com.lulan.shincolle.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.lulan.shincolle.client.ShipItemPeekHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server-to-Client: the stacks a ship is carrying, split into the three
 * sections the peek overlay draws separately (equipment / third-party
 * Curios equipment / cargo) - a ship's inventory only exists server side
 * unless its screen is open, so the peek overlay has to ask for it.
 * Sent in reply to {@link C2SInputPacket#Request_ShipItemList}.
 */
public class S2CShipItemListPacket {

    /** Guards against a malformed packet asking the client to allocate wildly, per section. */
    private static final int MaxStacksPerSection = 64;

    private final int entityId;
    private final List<ItemStack> equipStacks;
    private final List<ItemStack> curiosStacks;
    private final List<ItemStack> cargoStacks;

    public S2CShipItemListPacket(int entityId, List<ItemStack> equipStacks, List<ItemStack> curiosStacks,
                                  List<ItemStack> cargoStacks) {
        this.entityId = entityId;
        this.equipStacks = equipStacks;
        this.curiosStacks = curiosStacks;
        this.cargoStacks = cargoStacks;
    }

    /**
     * Decoder constructor
     */
    public S2CShipItemListPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.equipStacks = readSection(buf);
        this.curiosStacks = readSection(buf);
        this.cargoStacks = readSection(buf);
    }

    private static List<ItemStack> readSection(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MaxStacksPerSection);
        List<ItemStack> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(buf.readItem());
        }
        return result;
    }

    /**
     * Encode
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        writeSection(buf, this.equipStacks);
        writeSection(buf, this.curiosStacks);
        writeSection(buf, this.cargoStacks);
    }

    private static void writeSection(FriendlyByteBuf buf, List<ItemStack> stacks) {
        int count = Math.min(stacks.size(), MaxStacksPerSection);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeItem(stacks.get(i));
        }
    }

    // ========== Handler ==========

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShipItemPeekHandler.acceptItemList(
                        this.entityId, this.equipStacks, this.curiosStacks, this.cargoStacks)));
        ctx.setPacketHandled(true);
    }
}
