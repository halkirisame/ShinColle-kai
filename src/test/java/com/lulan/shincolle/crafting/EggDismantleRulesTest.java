package com.lulan.shincolle.crafting;

import com.lulan.shincolle.crafting.EggDismantleRules.BuildTypeField;
import com.lulan.shincolle.crafting.EggDismantleRules.EggDismantleKind;
import com.lulan.shincolle.crafting.EggDismantleRules.EggSnapshot;
import com.lulan.shincolle.crafting.EggDismantleRules.ShipClassField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EggDismantleRulesTest {

    private static EggSnapshot clean(BuildTypeField buildType, ShipClassField shipClass) {
        return new EggSnapshot(buildType, shipClass, true, false);
    }

    @Test
    void acceptsOnlyExplicitSmallAndLarge() {
        assertEquals(EggDismantleKind.SMALL, EggDismantleRules.classify(clean(BuildTypeField.SMALL, ShipClassField.ABSENT)));
        assertEquals(8, EggDismantleKind.SMALL.yield());
        assertEquals(EggDismantleKind.LARGE, EggDismantleRules.classify(clean(BuildTypeField.LARGE, ShipClassField.ABSENT)));
        assertEquals(64, EggDismantleKind.LARGE.yield());
        assertEquals(EggDismantleKind.REJECTED, EggDismantleRules.classify(clean(BuildTypeField.MISSING, ShipClassField.ABSENT)));
        assertEquals(EggDismantleKind.REJECTED, EggDismantleRules.classify(clean(BuildTypeField.INVALID, ShipClassField.ABSENT)));
        assertEquals(0, EggDismantleKind.REJECTED.yield());
    }

    @Test
    void acceptsFriendlyClassAlongsideBuildType() {
        assertEquals(EggDismantleKind.SMALL,
                EggDismantleRules.classify(clean(BuildTypeField.SMALL, ShipClassField.VALID_FRIENDLY)));
        assertEquals(EggDismantleKind.LARGE,
                EggDismantleRules.classify(clean(BuildTypeField.LARGE, ShipClassField.VALID_FRIENDLY)));
        assertEquals(EggDismantleKind.REJECTED,
                EggDismantleRules.classify(clean(BuildTypeField.SMALL, ShipClassField.INVALID)));
        assertEquals(EggDismantleKind.REJECTED,
                EggDismantleRules.classify(clean(BuildTypeField.LARGE, ShipClassField.INVALID)));
        assertEquals(EggDismantleKind.REJECTED,
                EggDismantleRules.classify(clean(BuildTypeField.MISSING, ShipClassField.VALID_FRIENDLY)));
    }

    @Test
    void savedOrUnknownPayloadAlwaysWins() {
        for (BuildTypeField buildType : BuildTypeField.values()) {
            for (ShipClassField shipClass : ShipClassField.values()) {
                for (boolean schemaValid : new boolean[]{true, false}) {
                    for (boolean extraPayload : new boolean[]{true, false}) {
                        if (schemaValid && !extraPayload) {
                            continue;
                        }
                        assertEquals(EggDismantleKind.REJECTED, EggDismantleRules.classify(
                                new EggSnapshot(buildType, shipClass, schemaValid, extraPayload)),
                                buildType + "/" + shipClass + "/" + schemaValid + "/" + extraPayload);
                    }
                }
            }
        }
    }

    @Test
    void classificationIsDeterministicAndDoesNotMutateInput() {
        EggSnapshot egg = clean(BuildTypeField.LARGE, ShipClassField.VALID_FRIENDLY);
        EggSnapshot before = new EggSnapshot(egg.buildType(), egg.shipClass(), egg.schemaValid(), egg.extraPayload());
        EggDismantleKind first = EggDismantleRules.classify(egg);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, EggDismantleRules.classify(egg));
        }
        assertEquals(before, egg);
    }
}
