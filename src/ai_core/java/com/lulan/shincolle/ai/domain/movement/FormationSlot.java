package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.LegacyDecodeResult;

/**
 * A ship's slot in its formation: 0 is the flagship's, which is also what a ship outside any
 * formation holds, and 1 to 5 are the slots placed around it.
 */
public record FormationSlot(int index) {
    public static final int LAST = 5;
    public static final FormationSlot FLAGSHIP = new FormationSlot(0);

    public FormationSlot {
        if (index < 0 || index > LAST) {
            throw new IllegalArgumentException("Formation slot out of range: " + index);
        }
    }

    public static LegacyDecodeResult<FormationSlot> fromLegacy(int raw) {
        return raw < 0 || raw > LAST ? new LegacyDecodeResult.Unknown<>(raw)
                : new LegacyDecodeResult.Known<>(new FormationSlot(raw));
    }

    /** The slot a decoded value is placed as: a slot outside the table is worked out as the flagship's. */
    public static FormationSlot placedAs(LegacyDecodeResult<FormationSlot> decoded) {
        return decoded instanceof LegacyDecodeResult.Known<FormationSlot> known ? known.value() : FLAGSHIP;
    }

    public boolean flagship() {
        return this.index == 0;
    }
}
