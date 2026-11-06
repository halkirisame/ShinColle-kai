package com.lulan.shincolle.shipdata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipIndividualTest {
    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        ShipDefinitionTest.bootstrapMinecraft();
    }

    private static HostileVariants.Variant variant(String id, int weight) {
        return new HostileVariants.Variant(id, Optional.of("variant.addon." + id), HostileVariants.Rarity.EPIC,
                weight, List.of(new ResourceLocation("addon:missing_trait")), Map.of(),
                Optional.of(new ResourceLocation("addon:textures/entity/rare.png")), true);
    }

    @Test
    void everyWeightedBoundaryUsesExactlyOneBoundedDraw() {
        var first = variant("first", 2);
        var zero = variant("zero", 0);
        var last = variant("last", 3);
        HostileVariants variants = new HostileVariants(4, List.of(first, zero, last));
        for (int draw = 0; draw < 9; draw++) {
            final int roll = draw;
            AtomicInteger calls = new AtomicInteger();
            var selected = HostileVariantRoll.select(variants, bound -> {
                assertEquals(9, bound);
                calls.incrementAndGet();
                return roll;
            });
            assertEquals(draw < 4 ? null : draw < 6 ? first : last, selected);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void omittedAndZeroWeightDefinitionsDoNotConsumeRandom() {
        for (HostileVariants variants : List.of(HostileVariants.NONE,
                new HostileVariants(0, List.of(variant("zero", 0))))) {
            assertNull(HostileVariantRoll.select(variants, bound -> {
                throw new AssertionError("Unexpected random draw");
            }));
        }
        AtomicInteger calls = new AtomicInteger();
        assertNull(HostileVariantRoll.select(new HostileVariants(100, List.of()), bound -> {
            assertEquals(100, bound);
            calls.incrementAndGet();
            return 99;
        }));
        assertEquals(1, calls.get(), "An explicitly present empty table must draw its normal slot");
    }

    @Test
    void snapshotRoundTripsUnknownReferencesAndEveryPresentationField() {
        ShipIndividual selected = ShipIndividual.selected(variant("rare", 1));
        ShipIndividual restored = load(selected.save());
        assertEquals(selected.save(), restored.save());
        assertTrue(restored.rolled());
        assertEquals("rare", restored.variant());
        assertEquals(List.of(new ResourceLocation("addon:missing_trait")), restored.traits());
        assertEquals(selected.presentation(), restored.presentation());
    }

    @Test
    void futureVersionIsOpaqueCopiedAndNeverRolledAgain() {
        CompoundTag future = new CompoundTag();
        future.putInt("V", 99);
        future.putString("Unknown", "keep me");
        future.putBoolean("Rolled", false);
        ShipIndividual state = load(future);
        CompoundTag copy = state.save();
        assertEquals(future, copy);
        future.putString("Unknown", "changed input");
        copy.putString("Unknown", "changed output");
        assertEquals("keep me", state.save().getString("Unknown"));
        assertTrue(state.rolled());
        assertEquals(ShipIndividual.Presentation.NORMAL, state.presentation());
        assertTrue(state.traits().isEmpty());
    }

    @Test
    void legacySaveIsNormalButBareSummonStillNeedsDraw() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("ScaleLevel", 2);
        assertTrue(ShipIndividual.load(legacy, "addon:ship").rolled());
        assertFalse(ShipIndividual.load(new CompoundTag(), "addon:ship").rolled());
        assertTrue(load(ShipIndividual.normal(true).save()).rolled());
        assertFalse(load(ShipIndividual.normal(false).save()).rolled());
    }

    @Test
    void invalidFieldsAreDroppedWithoutErasingOtherFields() {
        CompoundTag tag = ShipIndividual.selected(variant("rare", 1)).save();
        tag.putString("Name", "a".repeat(129));
        tag.putInt("Visual", 1);
        tag.putString("Variant", "Bad/Variant");
        tag.putString("Rarity", "invalid");
        tag.putInt("RedAura", 1);
        ShipIndividual state = load(tag);
        assertEquals("", state.variant());
        assertEquals(ShipIndividual.Presentation.NORMAL, state.presentation());
        assertEquals(List.of(new ResourceLocation("addon:missing_trait")), state.traits());
        assertTrue(state.rolled());
    }

    @Test
    void traitLimitAndEntryValidationDoNotEraseValidSiblingFields() {
        CompoundTag tag = ShipIndividual.selected(variant("rare", 1)).save();
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("addon:valid"));
        list.add(StringTag.valueOf("Bad Id"));
        list.add(StringTag.valueOf("addon:valid"));
        list.add(StringTag.valueOf("a".repeat(129)));
        tag.put("Traits", list);
        assertEquals(List.of(new ResourceLocation("addon:valid")), load(tag).traits());
        list.add(StringTag.valueOf("addon:fifth"));
        assertTrue(load(tag).traits().isEmpty());
        assertEquals("rare", load(tag).variant());
        tag.putString("Traits", "not a list");
        assertTrue(load(tag).traits().isEmpty());
    }

    @Test
    void exactStringLimitsAndStableRarityIdsAreAccepted() {
        CompoundTag tag = ShipIndividual.normal(true).save();
        tag.putString("Name", "a".repeat(128));
        tag.putString("Variant", "a".repeat(32));
        tag.putString("Visual", "addon:" + "a".repeat(122));
        for (var rarity : HostileVariants.Rarity.values()) {
            tag.putString("Rarity", rarity.name().toLowerCase(java.util.Locale.ROOT));
            var result = load(tag);
            assertEquals(128, result.presentation().name().length());
            assertEquals(128, result.presentation().visual().length());
            assertEquals(32, result.variant().length());
            assertEquals(rarity, result.presentation().rarity());
        }
    }

    @Test
    void malformedCompoundAndBooleanNeverCauseAnAccidentalReroll() {
        CompoundTag entity = new CompoundTag();
        entity.putString(ShipIndividual.KEY, "broken");
        assertTrue(ShipIndividual.load(entity, "addon:ship").rolled());
        CompoundTag tag = ShipIndividual.normal(true).save();
        tag.putString("Rolled", "false");
        assertTrue(load(tag).rolled());
    }

    @Test
    void recruitmentUsesOnlyKnownFieldsAndDisablesAuraWithoutMutatingEnemy() {
        CompoundTag hostile = ShipIndividual.selected(variant("rare", 1)).save();
        hostile.putString("Owner", "do not copy");
        hostile.putInt("ScaleLevel", 3);
        hostile.putInt("Level", 100);
        hostile.put("EntityTag", new CompoundTag());
        var state = load(hostile);
        CompoundTag recruit = state.recruitmentData();
        assertEquals(java.util.Set.of("V", "Rolled", "Variant", "Name", "Rarity", "Traits", "Visual", "RedAura"),
                recruit.getAllKeys());
        assertFalse(recruit.getBoolean("RedAura"));
        assertTrue(recruit.getBoolean("Rolled"));
        assertTrue(state.presentation().redAura());
        assertEquals(state.traits(), load(recruit).traits());
        assertEquals(state.presentation().visual(), load(recruit).presentation().visual());
    }

    @Test
    void normalAndOpaqueEnemiesCannotExportUnknownRecruitmentFields() {
        assertNull(ShipIndividual.normal(true).recruitmentData());
        assertNull(ShipIndividual.normal(false).recruitmentData());
        CompoundTag future = new CompoundTag();
        future.putInt("V", 99);
        future.putString("Unknown", "retain only for saved restoration");
        var state = load(future);
        assertNull(state.recruitmentData());
        assertEquals(future, state.asFriendly().save());
        assertEquals(ShipIndividual.Presentation.NORMAL, state.asFriendly().presentation());
    }

    @Test
    void friendlyConversionMarksRolledAndKeepsAllIdentityExceptAura() {
        CompoundTag source = ShipIndividual.selected(variant("rare", 1)).save();
        source.putBoolean("Rolled", false);
        var friendly = load(source).asFriendly();
        assertTrue(friendly.rolled());
        assertFalse(friendly.presentation().redAura());
        source.putBoolean("Rolled", true);
        source.putBoolean("RedAura", false);
        assertEquals(source, friendly.save());
        assertEquals(friendly.save(), friendly.asFriendly().save());
    }

    private static ShipIndividual load(CompoundTag tag) {
        CompoundTag entity = new CompoundTag();
        entity.put(ShipIndividual.KEY, tag);
        return ShipIndividual.load(entity, "addon:ship");
    }
}
