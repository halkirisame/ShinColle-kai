package com.lulan.shincolle.item;

import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.shipdata.GenericShipSpawner;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.UUID;

/** Shared ordinary-save transport; never initializes a ship as a spawn egg. */
final class ShipTransport {
    private static final List<String> LOCATION_KEYS = List.of("Passengers", "Pos", "Motion", "Rotation", "UUID");

    private ShipTransport() {
    }

    static String captureRejection(Player player, LivingEntity target) {
        if (target instanceof BasicEntityShipHostile) return "hostile";
        if (!(target instanceof BasicEntityShip ship)) return "unsupported";
        if (!TeamHelper.checkSameOwner(player, ship) && !ship.isOwnedBy(player)) return "not_owner";
        if (!ship.isAlive() || ship.isRemoved()) return "dead";
        for (ServerLevel level : target.getServer().getAllLevels()) {
            for (ServerPlayer viewer : level.players()) {
                if (viewer != player && viewer.containerMenu instanceof ContainerShipInventory menu && menu.getShip() == ship) {
                    return "busy";
                }
            }
        }
        return null;
    }

    static void detach(Player player, BasicEntityShip ship) {
        if (player.containerMenu instanceof ContainerShipInventory menu && menu.getShip() == ship) player.closeContainer();
        Entity vehicle = ship.getVehicle();
        ship.stopRiding();
        ship.ejectPassengers();
        if (vehicle instanceof BasicEntityMount mount) mount.clearRider();
        // A summoned mount can also be standing next to its host instead of carrying it.
        for (Entity entity : ((ServerLevel) ship.level()).getAllEntities()) {
            if (entity instanceof BasicEntityMount mount && mount.getHost() == ship && !mount.isRemoved()) {
                mount.clearRider();
            }
        }
        ship.sendSyncPacketRiders();
    }

    static CompoundTag save(Player player, BasicEntityShip ship) {
        CompoundTag data = ship.saveWithoutId(new CompoundTag());
        if (data == null || data.isEmpty()) return null;
        for (String key : LOCATION_KEYS) data.remove(key);
        CompoundTag stored = new CompoundTag();
        stored.put("EntityData", data);
        if (ModList.get().isLoaded("curios")) stored.put("CuriosEquipment", ShipCuriosIntegration.saveEquipped(ship));
        if (ship instanceof GenericShipEntity generic) {
            stored.putString("ShipId", generic.getShipId());
            var view = generic.definitionState().presentation(false);
            stored.putInt("Icon", genericIcon(view == null ? "" : view.shipType().getPath()));
        } else {
            stored.putInt("ShipClass", ship.getStateMinor(ID.M.ShipClass));
            stored.putInt("Icon", ShipSpawnEgg.getIconFromShipClass(ship.getStateMinor(ID.M.ShipClass)));
        }
        stored.putUUID("Owner", ship.getOwnerUUID() == null ? player.getUUID() : ship.getOwnerUUID());
        stored.putString("OwnerName", ship.getOwner() == null
                ? player.getName().getString() : ship.getOwner().getName().getString());
        stored.putString("Name", ship.getName().getString());
        stored.putString("TypeName", ship instanceof GenericShipEntity generic
                ? generic.definitionState().name(false).getString() : ship.getType().getDescription().getString());
        stored.putInt("Level", ship.getLevel());
        return stored;
    }

    static boolean isOwner(CompoundTag stored, Player player) {
        return stored.hasUUID("Owner") && stored.getUUID("Owner").equals(player.getUUID());
    }

    /** Null means world addition succeeded; otherwise the caller retains the stored save. */
    static String release(Player player, ServerLevel level, CompoundTag stored, List<BlockPos> positions, boolean supported) {
        if (!isOwner(stored, player)) return "not_owner";
        BasicEntityShip ship = null;
        try {
            if (!stored.contains("EntityData", Tag.TAG_COMPOUND) || stored.getCompound("EntityData").isEmpty()) {
                return "release_failed";
            }
            if (stored.contains("ShipId", Tag.TAG_STRING)) {
                ship = (BasicEntityShip) GenericShipSpawner.create(level,
                        ShipDefinitionId.parse(stored.getString("ShipId")), false, null);
            } else if (stored.contains("ShipClass", Tag.TAG_INT)) {
                EntityType<?> type = ShipSpawnEgg.getEntityTypeForClass(stored.getInt("ShipClass"));
                Entity entity = type == null ? null : type.create(level);
                if (entity instanceof BasicEntityShip friendly) ship = friendly;
            }
            if (ship == null) return "release_failed";
            CompoundTag data = stored.getCompound("EntityData").copy();
            for (String key : LOCATION_KEYS) data.remove(key);
            float health = data.getFloat("Health");
            ship.load(data);
            if (ModList.get().isLoaded("curios") && stored.contains("CuriosEquipment", Tag.TAG_LIST)) {
                ShipCuriosIntegration.loadEquipped(ship, stored.getList("CuriosEquipment", Tag.TAG_COMPOUND));
            }
            ship.setUUID(UUID.randomUUID());
            // The first move precedes attribute calculation, just as for a single-net release.
            BlockPos first = positions.get(0);
            ship.moveTo(first.getX() + 0.5D, first.getY(), first.getZ() + 0.5D, player.getYRot(), 0F);
            ship.setDeltaMovement(Vec3.ZERO);
            ship.calcShipAttributes(31, false);
            ship.setHealth(Math.min(health, ship.getMaxHealth()));
            boolean found = false;
            for (BlockPos pos : positions) {
                if (!pos.equals(first)) ship.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0F);
                if (supported && (!level.mayInteract(player, pos) || !hasSupport(level, ship, pos))) continue;
                if (supported && !level.getEntities(ship, ship.getBoundingBox(),
                        entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator()).isEmpty()) continue;
                if (level.noCollision(ship, ship.getBoundingBox())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ship.invalidateCaps();
                return "blocked";
            }
            if (!level.addFreshEntity(ship)) {
                ship.invalidateCaps();
                return "release_failed";
            }
        } catch (RuntimeException error) {
            if (ship != null && !ship.isAddedToWorld()) ship.invalidateCaps();
            return "release_failed";
        }
        return null;
    }

    private static boolean hasSupport(ServerLevel level, BasicEntityShip ship, BlockPos pos) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER)
                || !level.noCollision(ship, ship.getBoundingBox().move(0, -0.05D, 0));
    }

    static boolean storeInInventory(Player player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) slot = inventory.getFreeSlot();
            if (slot < 0) return false;
            ItemStack current = inventory.getItem(slot);
            int limit = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
            int moved = Math.min(stack.getCount(), limit - (current.isEmpty() ? 0 : current.getCount()));
            if (moved <= 0) return false;
            if (current.isEmpty()) {
                inventory.setItem(slot, stack.copyWithCount(moved));
            } else {
                current.grow(moved);
            }
            inventory.getItem(slot).setPopTime(5);
            stack.shrink(moved);
            inventory.setChanged();
        }
        return true;
    }

    static int genericIcon(String type) {
        return switch (type) {
            case "light_cruiser", "torpedo_cruiser" -> 3;
            case "heavy_cruiser" -> 4;
            case "battleship" -> 5;
            case "transport" -> 6;
            case "submarine" -> 7;
            case "demon" -> 8;
            case "hime" -> 9;
            case "light_carrier", "standard_carrier" -> 10;
            default -> 2;
        };
    }
}
