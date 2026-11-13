package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.domain.traits.ShipInnateTraits;
import com.lulan.shincolle.ai.domain.traits.ShipInnateTraits.Profile;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Observes identity, effective authority and saved fleet membership only at bonus consumption. */
public final class ShipInnateTraitsGate {
    private ShipInnateTraitsGate() { }

    public static Profile profile(Entity entity) {
        if (!(entity instanceof GenericShipEntity ship)) return Profile.NONE;
        Profile known = switch (ship.getShipId()) {
            case "shincolle_kai:kashima" -> Profile.KASHIMA;
            case "shincolle_kai:yukikaze" -> Profile.YUKIKAZE;
            default -> Profile.NONE;
        };
        return known.enabled(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW,
                !ship.definitionState().dormant() && ship.definitionState().definition() != null);
    }

    public static int postAttackDelay(Entity entity, WeaponChannel channel, int delay) {
        return profile(entity).postAttackDelay(channel, delay);
    }

    public static boolean trainingSupported(BasicEntityShip receiver) {
        if (!(receiver.level() instanceof ServerLevel level) || !receiver.isAlive()
                || level.getEntity(receiver.getUUID()) != receiver
                || ConfigHandler.shipAiTargetAuthority() != ConfigHandler.ShipAiTargetAuthority.NEW) return false;
        if (supports(receiver, receiver, level, false)) return true;
        ServerPlayer owner = ServerDataManager.getPlayerByUID(receiver.getPlayerUID());
        if (owner == null || !owner.getUUID().equals(receiver.getOwnerUUID())) return false;
        CapaTeitoku capa = owner.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) return false;
        // Resolve only the finite slots of every saved fleet that contains the recipient UID.
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            if (!capa.isShipInTeam(team, receiver.getShipUID())) continue;
            for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                int uid = capa.getTeamMember(team, slot);
                if (uid <= 0 || uid == receiver.getShipUID()) continue;
                Entity bySid = level.getEntity(capa.getTeamSID(team, slot));
                BasicEntityShip source = bySid instanceof BasicEntityShip found && found.getShipUID() == uid
                        && found.getPlayerUID() == receiver.getPlayerUID() ? found : ServerDataManager.getShipByUID(uid);
                if (source != null && source.getShipUID() == uid && supports(source, receiver, level, true)) return true;
            }
        }
        return false;
    }

    private static boolean supports(BasicEntityShip source, BasicEntityShip receiver, ServerLevel level,
                                    boolean sharesFleet) {
        boolean sameOwner = source == receiver || source.getPlayerUID() == receiver.getPlayerUID()
                && receiver.getOwnerUUID() != null && receiver.getOwnerUUID().equals(source.getOwnerUUID());
        return ShipInnateTraits.supportsTraining(new ShipInnateTraits.TrainingSource(
                profile(source) == Profile.KASHIMA, source.isAlive(),
                source.getStateMinor(ID.M.NumGrudge) > 0 && !source.getStateFlag(ID.F.NoFuel),
                source.level() instanceof ServerLevel sourceLevel && sourceLevel.getEntity(source.getUUID()) == source,
                sameOwner, source.level() == level, sharesFleet, source == receiver));
    }
}
