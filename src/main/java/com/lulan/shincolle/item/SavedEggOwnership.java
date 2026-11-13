package com.lulan.shincolle.item;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Ownership boundary for dropped saved eggs and explicit administrator recovery. */
public final class SavedEggOwnership {
    private SavedEggOwnership() { }

    public static boolean mayPickUp(ItemStack egg, Player player) {
        CompoundTag tag = egg.getTag();
        if (tag == null) return true;
        if (tag.contains("owner")) {
            if (!tag.contains("owner", Tag.TAG_STRING)) return false;
            String owner = tag.getString("owner");
            if (!owner.isEmpty()) {
                try {
                    return UUID.fromString(owner).equals(player.getUUID());
                } catch (IllegalArgumentException malformed) {
                    return false;
                }
            }
        }
        var capa = ServerDataManager.getTeitokuCapability(player);
        if (capa == null || capa.getPlayerUID() <= 0) return false;
        int savedUid;
        if (tag.contains("StateMinor", Tag.TAG_INT_ARRAY)) {
            int[] minors = tag.getIntArray("StateMinor");
            savedUid = minors.length > ID.M.PlayerUID ? minors[ID.M.PlayerUID] : 0;
        } else if (tag.getCompound("ShipExtProps").contains("Minor", Tag.TAG_COMPOUND)) {
            savedUid = tag.getCompound("ShipExtProps").getCompound("Minor").getInt("PlayerUID");
        } else {
            savedUid = tag.getInt("PlayerID");
        }
        if (savedUid <= 0 || savedUid != capa.getPlayerUID()) return false;
        tag.putString("owner", player.getUUID().toString());
        return true;
    }

    public static boolean migrateLegacyEgg(ItemStack egg, Player target) {
        if (egg.getItem() != ModItems.SHIP_SPAWN_EGG.get()
                && egg.getItem() != ModItems.GENERIC_SHIP_SPAWN_EGG.get()) return false;
        CompoundTag tag = egg.getTag();
        if (tag == null || tag.contains("owner") && !tag.getString("owner").isEmpty()
                || tag.contains("owner") && !tag.contains("owner", Tag.TAG_STRING)) return false;
        var capa = ServerDataManager.getTeitokuCapability(target);
        if (capa == null || capa.getPlayerUID() <= 0) return false;
        int[] minors = tag.getIntArray("StateMinor");
        if (tag.contains("StateMinor") && (!tag.contains("StateMinor", Tag.TAG_INT_ARRAY)
                || minors.length <= ID.M.PlayerUID)) return false;
        if (minors.length > ID.M.PlayerUID) {
            minors[ID.M.PlayerUID] = capa.getPlayerUID();
            tag.putIntArray("StateMinor", minors);
        } else {
            CompoundTag legacy = tag.getCompound("ShipExtProps");
            CompoundTag minor = legacy.getCompound("Minor");
            minor.putInt("PlayerUID", capa.getPlayerUID());
            legacy.put("Minor", minor);
            tag.put("ShipExtProps", legacy);
        }
        if (tag.contains("PlayerID")) tag.putInt("PlayerID", capa.getPlayerUID());
        tag.putString("owner", target.getUUID().toString());
        tag.putString("ownername", target.getGameProfile().getName());
        if (tag.contains("Owner", Tag.TAG_STRING)) tag.putString("Owner", target.getGameProfile().getName());
        return true;
    }
}
