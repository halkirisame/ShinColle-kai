package com.lulan.shincolle.equip.curios;

import com.lulan.shincolle.api.equipment.ShipEquipmentResolver;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Curios only auto-attaches its {@code ICurio} capability to items whose
 * class directly implements {@link ICurioItem} - see that interface's own
 * doc. Third-party equipment recognised by a canonical equipment provider
 * (e.g. a plain Tinkers' Construct tool, which
 * can't implement {@code ICurioItem} itself - that's Tinkers' own item
 * class) would otherwise never satisfy Curios' own slot-insertion check and
 * silently bounce out of the ship-equip slot.
 *
 * <p>This attaches an equivalent capability from the outside via Forge's
 * standard {@link AttachCapabilitiesEvent}, which doesn't require the
 * item's own class to know about Curios at all.
 */
public final class ShipEquipCurioCapabilityHandler {

    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(Reference.MOD_ID, "ship_equip_curio");
    private static final ThreadLocal<Boolean> CHECKING_DYNAMIC_SOURCE =
            ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public void onAttachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();

        // Items implementing ICurioItem themselves already get the capability
        // from Curios directly - attaching a second one would be redundant.
        if (stack.getItem() instanceof ICurioItem) {
            return;
        }
        // Provider predicates receive a defensive ItemStack copy. Creating
        // that copy can itself fire AttachCapabilitiesEvent, so suppress only
        // the nested attachment pass to avoid unbounded recursion.
        if (CHECKING_DYNAMIC_SOURCE.get()) {
            return;
        }
        boolean accepted;
        CHECKING_DYNAMIC_SOURCE.set(true);
        try {
            accepted = ShipEquipmentResolver.hasDynamicSource(stack);
        } finally {
            CHECKING_DYNAMIC_SOURCE.remove();
        }
        if (!accepted) {
            return;
        }

        LazyOptional<ICurio> instance = LazyOptional.of(() -> (ICurio) () -> stack);
        event.addCapability(CAPABILITY_ID, new ICapabilityProvider() {
            @NotNull
            @Override
            public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
                return CuriosCapability.ITEM.orEmpty(capability, instance.cast());
            }
        });
        event.addListener(instance::invalidate);
    }
}
