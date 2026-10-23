package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Read-only identities of existing entity registrations; does not convert their attributes or behavior. */
public final class LegacyShipDefinitionIds {
    /** Registration family, not a player's current relationship to an individual ship. */
    public enum Registration {
        FRIENDLY, HOSTILE
    }

    public record Key(int shipClass, Registration registration) {
        public Key {
            Objects.requireNonNull(registration, "registration");
        }
    }

    private static final Map<Key, ShipDefinitionId> IDS = create();

    private LegacyShipDefinitionIds() {
    }

    /** Returns null for a class without an entity registration in the specified family. */
    public static ShipDefinitionId get(int shipClass, Registration registration) {
        return IDS.get(new Key(shipClass, registration));
    }

    public static Map<Key, ShipDefinitionId> all() {
        return IDS;
    }

    private static Map<Key, ShipDefinitionId> create() {
        Map<Key, ShipDefinitionId> result = new HashMap<>();
        add(result, ID.ShipClass.DDShimakaze, "destroyer_shimakaze", true);
        add(result, ID.ShipClass.DDAkatsuki, "destroyer_akatsuki", true);
        add(result, ID.ShipClass.DDHibiki, "destroyer_hibiki", true);
        add(result, ID.ShipClass.DDIkazuchi, "destroyer_ikazuchi", true);
        add(result, ID.ShipClass.DDInazuma, "destroyer_inazuma", true);
        add(result, ID.ShipClass.DDI, "destroyer_i", false);
        add(result, ID.ShipClass.DDRO, "destroyer_ro", false);
        add(result, ID.ShipClass.DDHA, "destroyer_ha", false);
        add(result, ID.ShipClass.DDNI, "destroyer_ni", false);
        add(result, ID.ShipClass.BBKongou, "bb_kongou", true);
        add(result, ID.ShipClass.BBHiei, "bb_hiei", true);
        add(result, ID.ShipClass.BBHaruna, "bb_haruna", true);
        add(result, ID.ShipClass.BBKirishima, "bb_kirishima", true);
        add(result, ID.ShipClass.BBNagato, "bb_nagato", true);
        add(result, ID.ShipClass.BBYamato, "bb_yamato", true);
        add(result, ID.ShipClass.BBRE, "bb_re", false);
        add(result, ID.ShipClass.BBRU, "bb_ru", false);
        add(result, ID.ShipClass.BBTA, "bb_ta", false);
        add(result, ID.ShipClass.CLTenryuu, "cl_tenryuu", true);
        add(result, ID.ShipClass.CLTatsuta, "cl_tatsuta", true);
        add(result, ID.ShipClass.CAAtago, "ca_atago", true);
        add(result, ID.ShipClass.CATakao, "ca_takao", true);
        add(result, ID.ShipClass.CANE, "ca_ne", false);
        add(result, ID.ShipClass.CARI, "ca_ri", false);
        add(result, ID.ShipClass.CVAkagi, "cv_akagi", true);
        add(result, ID.ShipClass.CVKaga, "cv_kaga", true);
        add(result, ID.ShipClass.CVWO, "cv_wo", false);
        add(result, ID.ShipClass.SSRo500, "ss_ro500", true);
        add(result, ID.ShipClass.SSU511, "ss_u511", true);
        add(result, ID.ShipClass.SSYO, "ss_yo", false);
        add(result, ID.ShipClass.SSKA, "ss_ka", false);
        add(result, ID.ShipClass.SSSO, "ss_so", false);
        add(result, ID.ShipClass.APWA, "ap_wa", false);
        add(result, ID.ShipClass.AirfieldHime, "airfield_hime", false);
        add(result, ID.ShipClass.BBHime, "bb_hime", false);
        add(result, ID.ShipClass.CAHime, "ca_hime", false);
        add(result, ID.ShipClass.CVHime, "cv_hime", false);
        add(result, ID.ShipClass.CVWD, "cv_wd", false);
        add(result, ID.ShipClass.DDHime, "dd_hime", false);
        add(result, ID.ShipClass.HarbourHime, "harbour_hime", false);
        add(result, ID.ShipClass.IsolatedHime, "isolated_hime", false);
        add(result, ID.ShipClass.MidwayHime, "midway_hime", false);
        add(result, ID.ShipClass.NorthernHime, "northern_hime", false);
        add(result, ID.ShipClass.SSNH, "ssnh", false);
        add(result, ID.ShipClass.SSHime, "ss_hime", false);
        return Map.copyOf(result);
    }

    private static void add(Map<Key, ShipDefinitionId> result, int shipClass, String path, boolean hasHostile) {
        put(result, new Key(shipClass, Registration.FRIENDLY), path);
        if (hasHostile) {
            put(result, new Key(shipClass, Registration.HOSTILE), path + "_mob");
        }
    }

    private static void put(Map<Key, ShipDefinitionId> result, Key key, String path) {
        if (result.put(key, new ShipDefinitionId(new ResourceLocation(Reference.MOD_ID, path))) != null) {
            throw new IllegalStateException("duplicate legacy ship registration " + key);
        }
    }
}
