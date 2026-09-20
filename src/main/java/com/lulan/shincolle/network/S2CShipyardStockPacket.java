package com.lulan.shincolle.network;

import com.lulan.shincolle.client.gui.inventory.ContainerLargeShipyard;
import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Synchronizes the four shipyard material stocks as full-width integers.
 */
public class S2CShipyardStockPacket {

    private static final int MATERIAL_COUNT = 4;
    private static final int MAX_STOCK = 1000000;

    private final int containerId;
    private final int[] stocks;

    public S2CShipyardStockPacket(int containerId, int[] stocks) {
        this.containerId = containerId;
        this.stocks = sanitize(stocks);
    }

    public S2CShipyardStockPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
        this.stocks = new int[MATERIAL_COUNT];
        for (int i = 0; i < MATERIAL_COUNT; i++) {
            this.stocks[i] = clamp(buf.readInt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        for (int stock : stocks) {
            buf.writeInt(stock);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> this::handleClient));
        ctx.setPacketHandled(true);
    }

    private void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu.containerId != containerId) {
            return;
        }
        if (menu instanceof ContainerSmallShipyard smallShipyard) {
            smallShipyard.setMatStockFromServer(stocks);
        } else if (menu instanceof ContainerLargeShipyard largeShipyard) {
            largeShipyard.setMatStockFromServer(stocks);
        }
    }

    public int getContainerId() {
        return containerId;
    }

    public int[] getStocks() {
        return Arrays.copyOf(stocks, stocks.length);
    }

    private static int[] sanitize(int[] values) {
        int[] sanitized = new int[MATERIAL_COUNT];
        if (values != null) {
            for (int i = 0; i < Math.min(values.length, MATERIAL_COUNT); i++) {
                sanitized[i] = clamp(values[i]);
            }
        }
        return sanitized;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(value, MAX_STOCK));
    }
}
