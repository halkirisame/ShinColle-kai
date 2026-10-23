package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.reference.ID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityDimensions;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-entity identity and lossless hibernation; no numeric ship-class allocation. */
public final class GenericShipState {
    private static final Set<String> REPORTED_MISSING = ConcurrentHashMap.newKeySet();
    private String shipId = "";
    private long generation = -1;
    private boolean dormant = true;
    private CompoundTag preserved;

    public String shipId() {
        return shipId;
    }

    public long generation() {
        return generation;
    }

    public boolean dormant() {
        return dormant;
    }

    public ShipDefinition definition() {
        try {
            return ShipDefinitionRegistry.server().get(ShipDefinitionId.parse(shipId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void identify(String id) {
        if (!shipId.equals(id)) {
            shipId = id;
            generation = -1;
        }
    }

    public void loaded(CompoundTag nbt) {
        identify(nbt.getString("ShipId"));
        generation = -1;
        dormant = definition() == null;
        preserved = dormant ? nbt.copy() : null;
        if (dormant) {
            reportMissing();
        }
    }

    /** Refresh before any entity tick, so missing data cannot run an old combat/movement tick. */
    public boolean refresh(Supplier<CompoundTag> save, Consumer<CompoundTag> restore, Runnable apply) {
        long current = ShipDefinitionRegistry.server().generation();
        if (generation == current) {
            return false;
        }
        ShipDefinition definition = definition();
        if (definition == null) {
            if (preserved == null) {
                preserved = save.get().copy();
            }
            dormant = true;
            reportMissing();
        } else {
            dormant = false;
            CompoundTag original = preserved;
            preserved = null;
            if (original != null) {
                restore.accept(original.copy());
            }
            apply.run();
        }
        generation = current;
        return true;
    }

    private void reportMissing() {
        if (REPORTED_MISSING.add(shipId)) {
            ShinColle.LOGGER.error("Missing ship definition {}; keeping entity dormant and preserving its NBT", shipId);
        }
    }

    /** Copy rather than alias either the load input or the caller's output tag. */
    public boolean savePreserved(CompoundTag output) {
        if (preserved == null) {
            return false;
        }
        // Entity.saveAsPassenger writes the type before calling saveWithoutId.
        // A live-loss snapshot was captured without that outer wrapper key.
        var entityType = output.get("id");
        for (String key : new HashSet<>(output.getAllKeys())) {
            output.remove(key);
        }
        output.merge(preserved.copy());
        if (!output.contains("id") && entityType != null) {
            output.put("id", entityType.copy());
        }
        return true;
    }

    public void receive(String id, long revision, boolean sleeping) {
        shipId = id;
        generation = revision;
        dormant = sleeping;
    }

    public ShipPresentation presentation(boolean client) {
        if (client) {
            return ShipPresentationClientAccess.get(shipId, generation);
        }
        ShipDefinition definition = definition();
        return definition == null ? null : ShipPresentation.of(definition);
    }

    public Component name(boolean client) {
        ShipPresentation view = presentation(client);
        return Component.literal(view == null ? (shipId.isEmpty() ? "Unknown ship" : shipId) : view.displayName());
    }

    public EntityDimensions dimensions(boolean client, int scaleLevel) {
        ShipPresentation view = presentation(client);
        ShipDefinition.Dimensions size = view == null ? ShipDefinitionFormat.DEFAULT_DIMENSIONS : view.dimensions();
        return EntityDimensions.scalable(size.width(), size.height()).scale(1 + Math.max(0, scaleLevel) * 0.5F);
    }

    public int shipType(boolean client) {
        ShipPresentation view = presentation(client);
        if (view == null) {
            return ID.ShipType.DESTROYER;
        }
        return switch (view.shipType().getPath()) {
            case "light_cruiser" -> ID.ShipType.LIGHT_CRUISER;
            case "heavy_cruiser" -> ID.ShipType.HEAVY_CRUISER;
            case "torpedo_cruiser" -> ID.ShipType.TORPEDO_CRUISER;
            case "light_carrier" -> ID.ShipType.LIGHT_CARRIER;
            case "standard_carrier" -> ID.ShipType.STANDARD_CARRIER;
            case "battleship" -> ID.ShipType.BATTLESHIP;
            case "transport" -> ID.ShipType.TRANSPORT;
            case "submarine" -> ID.ShipType.SUBMARINE;
            case "demon" -> ID.ShipType.DEMON;
            case "hime" -> ID.ShipType.HIME;
            default -> ID.ShipType.DESTROYER;
        };
    }
}
