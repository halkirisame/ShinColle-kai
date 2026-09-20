package com.lulan.shincolle.api.attribute;

import com.lulan.shincolle.api.ShinColleApi;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Stable public identifiers for ShinColle's original 21 ship attributes.
 */
public final class CoreShipAttributes {

    public static final ResourceLocation HP = id("hp");
    public static final ResourceLocation ATK_L = id("atk_l");
    public static final ResourceLocation ATK_H = id("atk_h");
    public static final ResourceLocation ATK_AL = id("atk_al");
    public static final ResourceLocation ATK_AH = id("atk_ah");
    public static final ResourceLocation DEF = id("def");
    public static final ResourceLocation SPD = id("spd");
    public static final ResourceLocation MOV = id("mov");
    public static final ResourceLocation HIT = id("hit");
    public static final ResourceLocation CRI = id("cri");
    public static final ResourceLocation DHIT = id("dhit");
    public static final ResourceLocation THIT = id("thit");
    public static final ResourceLocation MISS = id("miss");
    public static final ResourceLocation AA = id("aa");
    public static final ResourceLocation ASM = id("asm");
    public static final ResourceLocation DODGE = id("dodge");
    public static final ResourceLocation XP = id("xp");
    public static final ResourceLocation GRUDGE = id("grudge");
    public static final ResourceLocation AMMO = id("ammo");
    public static final ResourceLocation HPRES = id("hpres");
    public static final ResourceLocation KB = id("kb");

    /**
     * Explicit compatibility order. Never derive this from registry iteration or numeric IDs.
     */
    public static final List<ResourceLocation> LEGACY_ORDER = List.of(
            HP, ATK_L, ATK_H, ATK_AL, ATK_AH, DEF, SPD, MOV, HIT, CRI, DHIT,
            THIT, MISS, AA, ASM, DODGE, XP, GRUDGE, AMMO, HPRES, KB);

    private CoreShipAttributes() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ShinColleApi.MOD_ID, path);
    }
}
