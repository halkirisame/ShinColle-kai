package com.lulan.shincolle.compat.mekanism;

import com.lulan.shincolle.reference.Reference;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Mekanism ore processing for polymetal ore. Only this package touches the
 * Mekanism API; call {@link #register} only when Mekanism is loaded.
 */
public final class MekanismIntegration {

    /** Slurry render tint (RRGGBB), a lighter cut of the polymetal nodule gray. */
    public static final int POLYMETAL_SLURRY_TINT = 0x6A6E78;

    private static final TagKey<Item> POLYMETAL_ORES =
            ItemTags.create(new ResourceLocation("forge", "ores/polymetal"));

    private static final DeferredRegister<Slurry> SLURRIES =
            DeferredRegister.create(MekanismAPI.SLURRY_REGISTRY_NAME, Reference.MOD_ID);

    public static final RegistryObject<Slurry> DIRTY_POLYMETAL = SLURRIES.register("dirty_polymetal",
            () -> new Slurry(SlurryBuilder.dirty().ore(POLYMETAL_ORES).tint(POLYMETAL_SLURRY_TINT)));
    public static final RegistryObject<Slurry> CLEAN_POLYMETAL = SLURRIES.register("clean_polymetal",
            () -> new Slurry(SlurryBuilder.clean().ore(POLYMETAL_ORES).tint(POLYMETAL_SLURRY_TINT)));

    private MekanismIntegration() {
    }

    public static void register(IEventBus modEventBus) {
        SLURRIES.register(modEventBus);
    }
}
