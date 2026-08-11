package com.lulan.shincolle.equipdata;

import java.util.Collection;

/**
 * Read-only lookup for {@link EquipDefinition}s loaded by {@link EquipDataLoader}.
 * The direct replacement for {@code Values.EquipAttrsMain.get(id)} / {@code
 * EquipAttrsMisc.get(id)}.
 */
public final class EquipDataRegistry {

    private EquipDataRegistry() {
    }

    /** The definition for this equip ID, or {@code null} if none is loaded. */
    public static EquipDefinition get(int equipId) {
        return EquipDataLoader.currentRegistry().get(equipId);
    }

    public static Collection<EquipDefinition> all() {
        return EquipDataLoader.currentRegistry().values();
    }
}
