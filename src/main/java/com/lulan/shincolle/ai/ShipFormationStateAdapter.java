package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.formation.FormationProjection;
import com.lulan.shincolle.ai.domain.formation.FormationProjector;
import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipFlags;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.Optional;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.LOAD_PREPARATION;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.OWNER_UNAVAILABLE;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.MEMBERSHIP_UNAVAILABLE;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.NOT_MEMBER;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.FORMATION_DISABLED;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.INSUFFICIENT_LIVING_MEMBERS;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.INVALID_OBSERVATION;

/** Server-session snapshot, refreshed at existing attribute and formal-operation boundaries. */
public final class ShipFormationStateAdapter {
    private final BasicEntityShip ship;
    private FormationProjection projection;
    private boolean registered;
    private ServerLevel observedLevel;
    private long nextDiagnostic;

    public ShipFormationStateAdapter(BasicEntityShip ship) { this.ship = ship; }
    public void invalidate() { this.projection = null; }
    public void remember(FormationProjection value) { this.projection = value; }

    public FormationProjection current() {
        if (!ShipCommandStateAdapter.isNew()) {
            invalidate();
            return new FormationProjection.Inactive(FORMATION_DISABLED);
        }
        if (!this.ship.isAlive()) return new FormationProjection.Inactive(INSUFFICIENT_LIVING_MEMBERS);
        boolean ready = this.ship.level() instanceof ServerLevel level && level.getEntity(this.ship.getUUID()) == this.ship;
        if (this.projection == null || this.registered != ready || this.observedLevel != this.ship.level()) return refresh();
        return this.projection;
    }

    public FormationProjection refresh() {
        this.observedLevel = this.ship.level() instanceof ServerLevel level ? level : null;
        this.registered = this.ship.level() instanceof ServerLevel level
                && level.getEntity(this.ship.getUUID()) == this.ship;
        this.projection = observe();
        if (this.projection instanceof FormationProjection.Pending pending
                && pending.reason() == INVALID_OBSERVATION
                && this.ship.level().getGameTime() >= this.nextDiagnostic) {
            this.nextDiagnostic = this.ship.level().getGameTime() + 128L;
            LogHelper.diag("DIAG: unresolved formation observation ship=" + this.ship.getShipUID());
        }
        return this.projection;
    }

    private FormationProjection observe() {
        if (!this.registered) return new FormationProjection.Pending(LOAD_PREPARATION);
        ServerPlayer owner = ServerDataManager.getPlayerByUID(this.ship.getPlayerUID());
        if (owner == null) return new FormationProjection.Pending(OWNER_UNAVAILABLE);
        CapaTeitoku capa = owner.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) return new FormationProjection.Pending(MEMBERSHIP_UNAVAILABLE);
        int team = capa.findTeamOfShip(this.ship.getShipUID());
        if (team < 0) return new FormationProjection.Inactive(NOT_MEMBER);
        ServerLevel level = (ServerLevel) this.ship.level();
        var members = new ArrayList<FormationProjector.Member>();
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            int uid = capa.getTeamMember(team, slot);
            if (uid <= 0) continue;
            Entity entity = level.getEntity(capa.getTeamSID(team, slot));
            BasicEntityShip member = entity instanceof BasicEntityShip found && found.getShipUID() == uid
                    ? found : ServerDataManager.getShipByUID(uid);
            boolean resolved = member != null && member.level() == level && level.getEntity(member.getUUID()) == member
                    && member.getPlayerUID() == capa.getPlayerUID() && member.getShipUID() == uid;
            var resolution = !resolved ? FormationProjector.Resolution.UNRESOLVED
                    : member.isAlive() ? FormationProjector.Resolution.ALIVE : FormationProjector.Resolution.DEAD;
            float movement = resolved && member.getAttrs() != null ? member.getAttrs().getMoveSpeed() : 0F;
            members.add(new FormationProjector.Member(new FormationSlot(slot), uid, resolution, movement));
        }
        return FormationProjector.project(new FormationProjector.Observation(true,
                FormationProjector.Membership.MEMBER, capa.getFormatID(team), this.ship.getShipUID(), members));
    }

    public static Optional<FormationProjection.Active> active(IShipFlags source) {
        BasicEntityShip ship = source instanceof BasicEntityShip own ? own
                : source instanceof BasicEntityMount mount ? mount.getHost() : null;
        return ship != null && ship.formationState().current() instanceof FormationProjection.Active active
                ? Optional.of(active) : Optional.empty();
    }
}
