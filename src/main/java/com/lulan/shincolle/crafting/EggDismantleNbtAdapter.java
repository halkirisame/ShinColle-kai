package com.lulan.shincolle.crafting;

import com.lulan.shincolle.crafting.EggDismantleRules.BuildTypeField;
import com.lulan.shincolle.crafting.EggDismantleRules.EggSnapshot;
import com.lulan.shincolle.crafting.EggDismantleRules.ShipClassField;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Reads a ship egg into an {@link EggSnapshot} without touching it.
 * <p>
 * Every key of the tag is inspected with its exact tag type; nothing is converted, defaulted or
 * normalized. The accepted keys are a closed list, so a key this class has never heard of counts as
 * extra payload and keeps the egg from being consumed.
 */
public final class EggDismantleNbtAdapter {

    private static final String BUILD_TYPE = "BuildType";
    private static final String SHIP_CLASS = "ShipClass";
    private static final String DISPLAY = "display";
    private static final String FORGE_CAPS = "ForgeCaps";
    private static final Set<String> MATERIAL_KEYS = Set.of("Grudge", "Abyssium", "Ammo", "Polymetal");

    private EggDismantleNbtAdapter() {
    }

    public static boolean isShipEgg(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.SHIP_SPAWN_EGG.get();
    }

    public static EggSnapshot read(ItemStack egg) {
        boolean extraPayload = hasSerializedCapabilities(egg);
        boolean schemaValid = true;
        BuildTypeField buildType = BuildTypeField.MISSING;
        ShipClassField shipClass = ShipClassField.ABSENT;

        CompoundTag tag = egg.getTag();
        if (tag != null) {
            for (String key : tag.getAllKeys()) {
                Tag value = tag.get(key);
                if (BUILD_TYPE.equals(key)) {
                    buildType = readBuildType(value);
                } else if (SHIP_CLASS.equals(key)) {
                    shipClass = readShipClass(value);
                } else if (MATERIAL_KEYS.contains(key)) {
                    if (!(value instanceof IntTag materialTag) || materialTag.getAsInt() < 0) {
                        schemaValid = false;
                    }
                } else if (DISPLAY.equals(key)) {
                    if (!isPlainDisplay(value)) {
                        schemaValid = false;
                    }
                } else {
                    extraPayload = true;
                }
            }
        }
        return new EggSnapshot(buildType, shipClass, schemaValid, extraPayload);
    }

    private static BuildTypeField readBuildType(Tag value) {
        if (value instanceof ByteTag byteTag) {
            return switch (byteTag.getAsByte()) {
                case 0 -> BuildTypeField.SMALL;
                case 1 -> BuildTypeField.LARGE;
                default -> BuildTypeField.INVALID;
            };
        }
        return BuildTypeField.INVALID;
    }

    private static ShipClassField readShipClass(Tag value) {
        if (value instanceof IntTag intTag) {
            int shipClass = intTag.getAsInt();
            if (shipClass >= 0 && shipClass < ShipSpawnEgg.MOB_OFFSET
                    && ShipSpawnEgg.getEntityTypeForClass(shipClass) != null) {
                return ShipClassField.VALID_FRIENDLY;
            }
        }
        return ShipClassField.INVALID;
    }

    /** A name and a lore list as an anvil writes them, nothing else. */
    private static boolean isPlainDisplay(Tag value) {
        if (!(value instanceof CompoundTag display)) {
            return false;
        }
        for (String key : display.getAllKeys()) {
            Tag child = display.get(key);
            if ("Name".equals(key)) {
                if (!(child instanceof StringTag)) {
                    return false;
                }
            } else if ("Lore".equals(key)) {
                if (!(child instanceof ListTag lore)) {
                    return false;
                }
                if (!lore.isEmpty() && lore.getElementType() != Tag.TAG_STRING) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /** Looks at the serialized form only; the stack is not changed. */
    private static boolean hasSerializedCapabilities(ItemStack stack) {
        CompoundTag saved = stack.serializeNBT();
        Tag caps = saved.get(FORGE_CAPS);
        if (caps == null) {
            return false;
        }
        return !(caps instanceof CompoundTag compound) || !compound.isEmpty();
    }
}
