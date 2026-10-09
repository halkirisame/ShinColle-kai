package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.AiRandomStream;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.CurrentLockObservation;
import com.lulan.shincolle.ai.domain.ManualOrderObservation;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.RevengeObservation;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.ai.domain.ShipTargetAuthorityState;
import com.lulan.shincolle.ai.domain.SourceGate;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetAuthorityDecision;
import com.lulan.shincolle.ai.domain.TargetAuthorityInput;
import com.lulan.shincolle.ai.domain.TargetCandidate;
import com.lulan.shincolle.ai.domain.TargetCandidateSelector;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.TargetLockReducer;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.ai.domain.TargetState;
import com.lulan.shincolle.ai.observation.MinecraftEntityObservationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftSpatialCandidateProvider;
import com.lulan.shincolle.ai.observation.MinecraftTargetClassificationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.TargetHelper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Owns and projects the optional typed combat-target authority for one ship. */
public final class ShipTargetAuthorityGoal extends Goal {
    private final IShipAttackBase host;
    private final Mob entity;
    private final BasicEntityShip friendly;
    private final Predicate<Entity> revengeSelector;
    private final int fixedRange;
    private ShipTargetAuthorityState state = ShipTargetAuthorityState.initial();
    private int autoRange;

    public ShipTargetAuthorityGoal(IShipAttackBase host) {
        this.host = host;
        this.entity = (Mob) host;
        this.friendly = host instanceof BasicEntityShip ship ? ship : null;
        this.revengeSelector = host instanceof BasicEntityShipHostile
                ? new TargetHelper.RevengeSelectorForHostile(this.entity)
                : new TargetHelper.RevengeSelector(this.entity);
        int attackRange = Math.round(host.getAttrs().getAttackRange());
        this.fixedRange = attackRange < 2
                ? Math.max(2, host.getStateMinor(ID.M.FollowMax) + 2)
                : attackRange;
        this.autoRange = searchRange();
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return !ShipActionGate.blocked(this.entity, ActionKind.TARGET_ACQUISITION);
    }

    @Override
    public boolean canContinueToUse() {
        return !ShipActionGate.blocked(this.entity, ActionKind.TARGET_ACQUISITION);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void stop() {
        // Stopping only happens while actions are forbidden; the old AI rebuilt this goal
        // from scratch afterwards, so no lock survives.
        clearLock();
    }

    @Override
    public void tick() {
        if (!(this.entity.level() instanceof ServerLevel level)) {
            return;
        }
        int tick = this.host.getTickExisted();
        SourceGate gate = sourceGate();
        Optional<ManualOrderObservation> manual = observeManual();
        Optional<RevengeObservation> revenge = observeRevenge();
        Optional<CurrentLockObservation> current = observeCurrent(level);
        TargetAuthorityInput withoutScan = new TargetAuthorityInput(
                tick, this.state, gate, manual, revenge, current, Optional.empty());
        TargetAuthorityDecision decision = TargetLockReducer.reduce(withoutScan);

        if (decision.next().lock().isEmpty()
                && TargetLockReducer.autoScanDue(decision.next(), gate, tick)) {
            this.autoRange = searchRange();
            TargetState scan = scan(level, tick);
            decision = TargetLockReducer.reduce(new TargetAuthorityInput(
                    tick, this.state, gate, manual, revenge, current, Optional.of(scan)));
        }

        this.state = decision.next();
        if (decision.consumeRevenge()) {
            this.host.setEntityRevengeTarget(null);
        }
        Entity projected = this.state.lock()
                .flatMap(lock -> new MinecraftTargetResolver(level).resolve(lock.target()))
                .orElse(null);
        project(projected);
    }

    public void clearLock() {
        this.state = new ShipTargetAuthorityState(
                Optional.empty(), this.state.autoScan(), this.state.lastConsumedRevengeTick());
    }

    public Optional<TargetLock> currentLock() {
        return this.state.lock();
    }

    private SourceGate sourceGate() {
        return new SourceGate(
                this.friendly == null || !this.friendly.getStateFlag(ID.F.PassiveAI),
                this.host.getIsSitting(),
                ShipMovementGate.craneBusy(this.host),
                this.host.getStateFlag(ID.F.NoFuel));
    }

    private Optional<ManualOrderObservation> observeManual() {
        if (this.friendly == null || this.friendly.getManualTarget() == null) {
            return Optional.empty();
        }
        Entity target = this.friendly.getManualTarget();
        TargetHandle handle = handle(target);
        // An invisible target keeps its lock until the 64 tick cleanup in TargetHelper.updateTarget
        // drops it, and is not locked again until it can be seen; the command itself stays.
        boolean held = this.state.lock()
                .filter(lock -> lock.source() == TargetSource.MANUAL && lock.target().equals(handle))
                .isPresent();
        double range = ShipSkillAttackGate.targetRetentionRange(this.entity, this.fixedRange, held);
        return Optional.of(new ManualOrderObservation(
                handle,
                TargetHelper.isValidAuthorityManualTarget(this.friendly)
                        && (TargetHelper.canDetectTarget(this.host, target) || held),
                this.entity.distanceToSqr(target) <= range * range));
    }

    private Optional<RevengeObservation> observeRevenge() {
        Entity target = this.host.getEntityRevengeTarget();
        if (target == null) {
            return Optional.empty();
        }
        return Optional.of(new RevengeObservation(
                handle(target), this.host.getEntityRevengeTime(), this.revengeSelector.test(target)));
    }

    private Optional<CurrentLockObservation> observeCurrent(ServerLevel level) {
        TargetLock lock = this.state.lock().orElse(null);
        if (lock == null) {
            return Optional.empty();
        }
        Entity resolved = new MinecraftTargetResolver(level).resolve(lock.target()).orElse(null);
        double range = ShipSkillAttackGate.targetRetentionRange(this.entity,
                lock.source() == TargetSource.AUTO ? this.autoRange : this.fixedRange, true);
        return Optional.of(new CurrentLockObservation(
                resolved != null,
                resolved != null && resolved.isAlive() && !resolved.isRemoved(),
                resolved != null && this.entity.distanceToSqr(resolved) <= range * range,
                resolved instanceof Player player && player.getAbilities().invulnerable));
    }

    private TargetState scan(ServerLevel level, int tick) {
        RawEntityObservation self = MinecraftEntityObservationAdapter.observe(this.entity);
        SpatialQuery query = new SpatialQuery(
                self.handle(), self.position(), MinecraftEntityObservationAdapter.boundsOf(this.entity),
                this.autoRange, ShipAiCompatibilityRules.targetSearchVerticalInflation(this.autoRange));
        MinecraftSpatialCandidateProvider provider = new MinecraftSpatialCandidateProvider(level);
        MinecraftTargetResolver resolver = new MinecraftTargetResolver(level);
        TargetPredicateKind kind = this.friendly == null
                ? TargetPredicateKind.HOSTILE_AUTOMATIC : TargetPredicateKind.FRIENDLY_AUTOMATIC;
        TargetPredicatePolicy policy = targetPolicy(this.host);
        List<TargetCandidate> candidates = new ArrayList<>();
        for (RawEntityObservation raw : provider.query(query)) {
            Entity candidate = resolver.resolve(raw.handle()).orElse(null);
            if (candidate == null) {
                continue;
            }
            var classified = MinecraftTargetClassificationAdapter.classify(
                    this.entity, candidate, kind, policy, tick, TargetObservationProfiler.NOOP);
            double x = self.position().x() - raw.position().x();
            double y = self.position().y() - raw.position().y();
            double z = self.position().z() - raw.position().z();
            candidates.add(new TargetCandidate(
                    raw.handle(), classified.classification().value(), x * x + y * y + z * z));
        }
        return TargetCandidateSelector.select(
                tick, self.handle(), candidates, kind, policy,
                AiRandomSource.forEntity(this.entity, AiRandomStream.TARGET_SELECTION));
    }

    public static TargetPredicatePolicy targetPolicy(IShipAttackBase host) {
        BasicEntityShip friendly = host instanceof BasicEntityShip ship ? ship : null;
        return new TargetPredicatePolicy(
                friendly != null && friendly.getStateFlag(ID.F.PVPFirst),
                friendly != null && friendly.getStateFlag(ID.F.AntiAir),
                friendly != null && friendly.getStateFlag(ID.F.AntiSS),
                ConfigHandler.shipAttackPlayer(), ConfigHandler.mobShipsAttackPlayer());
    }

    private int searchRange() {
        return ShipAiCompatibilityRules.targetSearchRange(
                this.host.getAttrs().getAttackRange(), this.host.getStateMinor(ID.M.FollowMax));
    }

    private TargetHandle handle(Entity target) {
        return MinecraftEntityObservationAdapter.observe(target).handle();
    }

    private void project(Entity target) {
        if (this.friendly != null) {
            this.friendly.projectTargetLock(target);
        } else {
            ((BasicEntityShipHostile) this.host).projectTargetLock(target);
        }
    }
}
