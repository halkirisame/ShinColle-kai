package com.lulan.shincolle.ai.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Derived facts only; neither observation nor lock retention renews the attack. */
public record OwnerAssistObservation(
        RecentOwnerAttack attack, long observedAt, double distanceSquared, double retentionRange,
        boolean ownerAvailable, boolean sameDimension, boolean targetEligible, boolean actionAllowed) {
    public OwnerAssistObservation {
        Objects.requireNonNull(attack, "attack");
    }

    public long age() {
        return this.observedAt - this.attack.occurredAt();
    }

    public Set<OwnerAssistRejection> rejections() {
        EnumSet<OwnerAssistRejection> reasons = EnumSet.noneOf(OwnerAssistRejection.class);
        if (!this.attack.currentAt(this.observedAt)) reasons.add(OwnerAssistRejection.AGE);
        if (!this.ownerAvailable) reasons.add(OwnerAssistRejection.OWNER_UNAVAILABLE);
        if (!this.sameDimension) reasons.add(OwnerAssistRejection.DIMENSION);
        if (!this.targetEligible) reasons.add(OwnerAssistRejection.TARGET_INELIGIBLE);
        double radius = Math.max(64D, this.retentionRange);
        if (!Double.isFinite(this.distanceSquared) || this.distanceSquared < 0D
                || this.distanceSquared > radius * radius) reasons.add(OwnerAssistRejection.DISTANCE);
        if (!this.actionAllowed) reasons.add(OwnerAssistRejection.ACTION_BLOCKED);
        return Set.copyOf(reasons);
    }

    public boolean valid() {
        return rejections().isEmpty();
    }
}
