package com.lulan.shincolle.handler;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Client configuration for update notifications. */
public final class UpdateNotificationConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    private UpdateNotificationConfig() {
    }

    /** Values stored in {@code shincolle_kai-client.toml}. */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> notifyOnLaunches;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("updateNotification");
            enabled = builder
                    .comment("Show a chat notice when Forge detects a newer ShinColle-kai version.")
                    .define("enabled", true);
            notifyOnLaunches = builder
                    .comment("Launch numbers on which to show a notice for the same detected version.")
                    .defineListAllowEmpty("notifyOnLaunches", List.of(1, 2, 4, 8, 16),
                            value -> value instanceof Integer number && number >= 1 && number <= 10000);
            builder.pop();
        }
    }
}
