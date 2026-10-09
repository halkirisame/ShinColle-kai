package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

/** Pure MANUAL, REVENGE, AUTO target authority transition. */
public final class TargetLockReducer {
    private static final int AUTO_SCAN_INTERVAL = 8;

    private TargetLockReducer() {
    }

    public static TargetAuthorityDecision reduce(TargetAuthorityInput input) {
        Objects.requireNonNull(input, "input");
        ShipTargetAuthorityState previous = input.previous();
        SourceGate gate = input.gate();

        ManualOrderObservation manual = input.manual().orElse(null);
        if (manual != null && manual.valid() && manual.inManualRange()
                && !gate.sitting() && !gate.noFuel()) {
            TargetLock lock = keepAcquisitionTick(previous.lock(), manual.target(), TargetSource.MANUAL)
                    .orElseGet(() -> new TargetLock(manual.target(), TargetSource.MANUAL, input.tickExisted()));
            return decision(previous, Optional.of(lock), previous.autoScan(),
                    previous.lastConsumedRevengeTick(), false, false);
        }

        Optional<TargetLock> remaining = previous.lock();
        if (remaining.map(TargetLock::source).orElse(null) == TargetSource.MANUAL) {
            remaining = Optional.empty();
        }

        TargetLock existing = remaining.orElse(null);
        if (existing != null && existing.source() == TargetSource.REVENGE
                && input.current().map(CurrentLockObservation::valid).orElse(false)) {
            return decision(previous, remaining, previous.autoScan(),
                    previous.lastConsumedRevengeTick(), false, false);
        }

        RevengeObservation revenge = input.revenge().orElse(null);
        if (revenge != null
                && ShipAiCompatibilityRules.revengeTriggered(
                        previous.lastConsumedRevengeTick(), revenge.revengeTick(), true)
                && revenge.acceptedByRevengePredicate()) {
            TargetLock lock = new TargetLock(revenge.target(), TargetSource.REVENGE, input.tickExisted());
            return decision(previous, Optional.of(lock), previous.autoScan(),
                    revenge.revengeTick(), true, false);
        }

        // Sitting and crane only block acquisition, as in LEGACY ShipRangeTargetGoal.canUse;
        // its canContinueToUse keeps a held target through both.
        if (existing != null && existing.source() == TargetSource.AUTO
                && gate.autoEnabled()
                && input.current().map(CurrentLockObservation::valid).orElse(false)) {
            return decision(previous, remaining, previous.autoScan(),
                    previous.lastConsumedRevengeTick(), false, false);
        }

        ShipTargetAuthorityState unlocked = new ShipTargetAuthorityState(
                Optional.empty(), previous.autoScan(), previous.lastConsumedRevengeTick());
        TargetState scan = input.autoScan().orElse(null);
        if (scan != null && autoScanDue(unlocked, gate, input.tickExisted())) {
            Optional<TargetLock> selected = scan.selectedTarget().map(target ->
                    new TargetLock(target, TargetSource.AUTO, input.tickExisted()));
            TargetScanSchedule advanced = previous.autoScan().advanceFrom(
                    input.tickExisted(), AUTO_SCAN_INTERVAL);
            return decision(previous, selected, advanced,
                    previous.lastConsumedRevengeTick(), false, true);
        }
        return decision(previous, Optional.empty(), previous.autoScan(),
                previous.lastConsumedRevengeTick(), false, false);
    }

    public static boolean autoScanDue(
            ShipTargetAuthorityState state, SourceGate gate, int tickExisted) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(gate, "gate");
        return state.lock().isEmpty()
                && autoGateOpen(gate)
                && state.autoScan().isDue(tickExisted);
    }

    private static boolean autoGateOpen(SourceGate gate) {
        return gate.autoEnabled() && !gate.sitting() && !gate.craneActive();
    }

    private static Optional<TargetLock> keepAcquisitionTick(
            Optional<TargetLock> current, TargetHandle target, TargetSource source) {
        return current.filter(lock -> lock.source() == source && lock.target().equals(target));
    }

    private static TargetAuthorityDecision decision(
            ShipTargetAuthorityState previous,
            Optional<TargetLock> lock,
            TargetScanSchedule autoScan,
            int lastConsumedRevengeTick,
            boolean consumeRevenge,
            boolean autoScanPerformed) {
        ShipTargetAuthorityState next = new ShipTargetAuthorityState(
                lock, autoScan, lastConsumedRevengeTick);
        return new TargetAuthorityDecision(next, consumeRevenge, autoScanPerformed);
    }
}
