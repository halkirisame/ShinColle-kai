package com.lulan.shincolle.ai.domain.movement;

import java.util.EnumSet;

import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Action.LIFT;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Action.NONE;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.ATTEMPT_LIMIT;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.FLYING;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.LEDGE_TOO_HIGH;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NOT_AFLOAT;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NOT_MOVING;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NO_HEADROOM;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NO_HORIZONTAL_COLLISION;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NO_LEDGE;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.ON_GROUND;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.PLAYER_CONTROLLED;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.TARGET_NOT_ON_LEDGE;

/** Shore decisions depend only on explicit facts and the previous attempt. */
public final class ShoreLandingRules {
    public static final int LIFT_TICK_LIMIT = 20;
    public static final int RETRY_DELAY = 40;

    private ShoreLandingRules() {
    }

    public static boolean isAfloat(boolean inLiquid, boolean onGround, boolean waterBelow,
                                   double feetY, double waterTopY, double maxUpStep) {
        return inLiquid || (!onGround && waterBelow && feetY - waterTopY <= maxUpStep + 0.01);
    }

    public static ShoreLandingDecision decide(ShoreFacts facts, ShoreLandingAttempt previous) {
        ShoreLandingAttempt attempt = previous;
        if (previous.targetKey() != facts.targetKey() || facts.onGround() || !facts.moving()
                || (previous.liftTicks() >= LIFT_TICK_LIMIT && facts.now() >= previous.blockedUntil())) {
            attempt = ShoreLandingAttempt.start(facts.targetKey());
        }
        EnumSet<ShoreLandingDecision.Reason> reasons = EnumSet.noneOf(ShoreLandingDecision.Reason.class);
        if (!facts.moving()) {
            reasons.add(NOT_MOVING);
            return new ShoreLandingDecision(NONE, reasons, attempt);
        }
        if (facts.flying()) reasons.add(FLYING);
        if (facts.playerControlled()) reasons.add(PLAYER_CONTROLLED);
        if (facts.onGround()) reasons.add(ON_GROUND);
        if (!facts.afloat()) reasons.add(NOT_AFLOAT);
        if (!facts.horizontalCollision()) reasons.add(NO_HORIZONTAL_COLLISION);
        if (facts.ledgeTopY().isEmpty()) {
            reasons.add(NO_LEDGE);
        } else {
            double top = facts.ledgeTopY().getAsDouble();
            if (facts.wantedY() < top - 0.5) reasons.add(TARGET_NOT_ON_LEDGE);
            if (facts.afloat() && top - facts.waterTopY() > facts.maxUpStep() + 0.01) {
                reasons.add(LEDGE_TOO_HIGH);
            }
            if (!facts.headroomClear()) reasons.add(NO_HEADROOM);
        }
        if (attempt.liftTicks() >= LIFT_TICK_LIMIT) reasons.add(ATTEMPT_LIMIT);
        if (!reasons.isEmpty()) return new ShoreLandingDecision(NONE, reasons, attempt);
        int ticks = attempt.liftTicks() + 1;
        return new ShoreLandingDecision(LIFT, reasons, new ShoreLandingAttempt(facts.targetKey(), ticks,
                ticks == LIFT_TICK_LIMIT ? facts.now() + RETRY_DELAY : 0));
    }
}
