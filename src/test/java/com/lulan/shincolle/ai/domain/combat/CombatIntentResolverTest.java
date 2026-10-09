package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.ai.domain.action.ActionConstraintPolicy;
import com.lulan.shincolle.ai.domain.action.ActionConstraints;
import com.lulan.shincolle.ai.domain.action.ActionFacts;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatIntentResolverTest {
    private static final TargetHandle TARGET =
            new TargetHandle(new UUID(1L, 2L), new DimensionKey("minecraft", "overworld"));
    private static final Optional<ActionConstraints> ALLOWED =
            Optional.of(ActionConstraintPolicy.evaluate(new ActionFacts(false, false, false)));

    private static Optional<TargetLock> lock(TargetSource source) {
        return Optional.of(new TargetLock(TARGET, source, 5L));
    }

    @Test
    void engagesTheLockedTargetWithItsSource() {
        for (TargetSource source : TargetSource.values()) {
            CombatIntent intent = CombatIntentResolver.resolve(new CombatFacts(lock(source), ALLOWED, false, false));
            assertEquals(new CombatIntent.Engage(TARGET, source), intent, source.name());
        }
    }

    @Test
    void eachReasonAloneHoldsFire() {
        assertHold(new CombatFacts(Optional.empty(), ALLOWED, false, false), HoldFireReason.NO_TARGET);
        assertHold(new CombatFacts(lock(TargetSource.AUTO),
                        Optional.of(ActionConstraintPolicy.evaluate(new ActionFacts(true, false, false))), false, false),
                HoldFireReason.FIRING_BLOCKED);
        assertHold(new CombatFacts(lock(TargetSource.AUTO),
                        Optional.of(ActionConstraintPolicy.evaluate(new ActionFacts(false, true, false))), false, false),
                HoldFireReason.FIRING_BLOCKED);
        assertHold(new CombatFacts(lock(TargetSource.AUTO), ALLOWED, true, false), HoldFireReason.SITTING);
        assertHold(new CombatFacts(lock(TargetSource.AUTO), ALLOWED, false, true), HoldFireReason.ON_SHIP_MOUNT);
    }

    @Test
    void collectsEveryReasonAtOnce() {
        CombatFacts facts = new CombatFacts(Optional.empty(),
                Optional.of(ActionConstraintPolicy.evaluate(new ActionFacts(true, true, false))), true, true);
        assertHold(facts, HoldFireReason.values());
    }

    @Test
    void playerControlDoesNotHoldFire() {
        CombatFacts facts = new CombatFacts(lock(TargetSource.MANUAL),
                Optional.of(ActionConstraintPolicy.evaluate(new ActionFacts(false, false, true))), false, false);
        assertEquals(new CombatIntent.Engage(TARGET, TargetSource.MANUAL), CombatIntentResolver.resolve(facts));
    }

    @Test
    void hostWithoutConstraintsIsNeverFiringBlocked() {
        CombatFacts facts = new CombatFacts(lock(TargetSource.AUTO), Optional.empty(), false, false);
        assertEquals(new CombatIntent.Engage(TARGET, TargetSource.AUTO), CombatIntentResolver.resolve(facts));
    }

    @Test
    void resolvingIsDeterministic() {
        CombatFacts facts = new CombatFacts(Optional.empty(), ALLOWED, true, false);
        assertEquals(CombatIntentResolver.resolve(facts), CombatIntentResolver.resolve(facts));
    }

    @Test
    void holdFireNeedsAReasonAndIsImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new CombatIntent.HoldFire(Set.of()));
        Set<HoldFireReason> source = EnumSet.of(HoldFireReason.SITTING);
        CombatIntent.HoldFire hold = new CombatIntent.HoldFire(source);
        source.add(HoldFireReason.NO_TARGET);
        assertEquals(Set.of(HoldFireReason.SITTING), hold.reasons());
        assertThrows(UnsupportedOperationException.class, () -> hold.reasons().add(HoldFireReason.NO_TARGET));
    }

    private static void assertHold(CombatFacts facts, HoldFireReason... expected) {
        CombatIntent intent = CombatIntentResolver.resolve(facts);
        CombatIntent.HoldFire hold = assertInstanceOf(CombatIntent.HoldFire.class, intent);
        assertEquals(Set.of(expected), hold.reasons());
    }
}
