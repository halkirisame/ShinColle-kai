package com.lulan.shincolle.handler;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-local ship sound preferences. */
public final class ShipSoundClientConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    private ShipSoundClientConfig() {
    }

    public static void save() {
        CLIENT_SPEC.save();
    }

    /** Values stored only on this Minecraft client. */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue shipVoiceEnabled;
        public final ForgeConfigSpec.DoubleValue shipVoiceVolume;
        public final ForgeConfigSpec.BooleanValue timekeepingEnabled;
        public final ForgeConfigSpec.DoubleValue timekeepingVolume;
        public final ForgeConfigSpec.BooleanValue hostileShipVoiceEnabled;
        public final ForgeConfigSpec.DoubleValue hostileShipVoiceVolume;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Client-local ship sound settings").push("sound");
            shipVoiceEnabled = builder
                    .comment("Play friendly ship voice sounds on this client")
                    .define("shipVoiceEnabled", false);
            shipVoiceVolume = builder
                    .comment("Friendly ship voice volume on this client")
                    .defineInRange("shipVoiceVolume", 1.0D, 0.0D, 1.0D);
            timekeepingEnabled = builder
                    .comment("Play friendly ship timekeeping voices on this client")
                    .define("timekeepingEnabled", false);
            timekeepingVolume = builder
                    .comment("Friendly ship timekeeping volume on this client")
                    .defineInRange("timekeepingVolume", 1.0D, 0.0D, 1.0D);
            hostileShipVoiceEnabled = builder
                    .comment("Play hostile ship voice sounds on this client")
                    .define("hostileShipVoiceEnabled", false);
            hostileShipVoiceVolume = builder
                    .comment("Hostile ship voice volume on this client")
                    .defineInRange("hostileShipVoiceVolume", 1.0D, 0.0D, 1.0D);
            builder.pop();
        }
    }
}
