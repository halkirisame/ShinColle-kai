package com.lulan.shincolle.client;

import com.lulan.shincolle.network.C2SShipClickPreferencePacket;
import com.lulan.shincolle.network.ModNetworking;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client-local interaction preference, shared by all of this player's ships. */
public final class ShipInteractionClientConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec.BooleanValue SWAP_CLICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SWAP_CLICKS = builder.comment("Swap owner right-click and sneak-right-click actions on ships")
                .define("swapShipClicks", false);
        CLIENT_SPEC = builder.build();
    }

    private ShipInteractionClientConfig() { }

    public static boolean swapClicks() {
        return SWAP_CLICKS.get();
    }

    public static void setSwapClicks(boolean swapped) {
        SWAP_CLICKS.set(swapped);
        CLIENT_SPEC.save();
        ModNetworking.sendToServer(new C2SShipClickPreferencePacket(swapped));
    }
}
