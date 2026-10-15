package com.lulan.shincolle.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Client-only creation of fixed and entity-following text particles. */
public final class CustomTextVisuals {
    private CustomTextVisuals() {}

    public static void atPosition(Level level, double x, double y, double z, String text, int lines) {
        if (level instanceof ClientLevel clientLevel && text != null && !text.isEmpty() && lines > 0) {
            add(null, clientLevel, x, y, z, text, lines, 0);
        }
    }

    public static void atEntity(Entity host, double x, double y, double z, String text, int lines) {
        if (host != null && host.isAlive() && host.level() instanceof ClientLevel clientLevel
                && text != null && !text.isEmpty() && lines > 0) {
            add(host, clientLevel, x, y, z, text, lines, 1);
        }
    }

    private static void add(Entity host, ClientLevel level, double x, double y, double z,
                            String text, int lines, int type) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = 0;
        for (String line : text.split("\n")) {
            width = Math.max(width, minecraft.font.width(line));
        }
        minecraft.particleEngine.add(new ParticleTextsCustom(host, level, x, y, z, 1F, type, text, lines, width + 1));
    }
}
