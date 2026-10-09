package com.lulan.shincolle.handler;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-local reading position for the handheld manual. */
public final class BookClientConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec.IntValue CHAPTER;
    private static final ForgeConfigSpec.IntValue PAGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Last reading position of the handheld manual").push("bookmark");
        CHAPTER = builder.defineInRange("chapter", 0, 0, 6);
        PAGE = builder.defineInRange("page", 0, 0, Integer.MAX_VALUE);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private BookClientConfig() {
    }

    public record Bookmark(int chapter, int page) {
    }

    public static Bookmark read(int[] pageLimits) {
        int chapter = Math.max(0, Math.min(CHAPTER.get(), pageLimits.length - 1));
        int page = Math.max(0, Math.min(PAGE.get(), pageLimits[chapter]));
        return new Bookmark(chapter, page);
    }

    public static void save(int chapter, int page) {
        CHAPTER.set(chapter);
        PAGE.set(page);
        CLIENT_SPEC.save();
    }
}
