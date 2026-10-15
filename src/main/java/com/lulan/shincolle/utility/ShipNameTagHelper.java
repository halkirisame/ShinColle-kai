package com.lulan.shincolle.utility;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

/** Rebuilds the derived team-name list without sending synchronization packets. */
public final class ShipNameTagHelper {
    private ShipNameTagHelper() {}

    /** Returns whether a resolved owner's current memberships changed the name list. */
    public static boolean refresh(BasicEntityShip ship) {
        if (ship == null || ship.level().isClientSide() || ship.getPlayerUID() <= 0) {
            return false;
        }
        ServerPlayer cachedOwner = ServerDataManager.getPlayerByUID(ship.getPlayerUID());
        Player owner = cachedOwner != null ? cachedOwner
                : ship.getOwner() instanceof Player player ? player : null;
        CapaTeitoku capa = ServerDataManager.getTeitokuCapability(owner);
        if (capa == null || capa.getPlayerUID() != ship.getPlayerUID() || ship.getShipUID() <= 0) {
            return false;
        }

        ArrayList<String> names = collect(capa, ship.getShipUID());
        if (names.equals(ship.unitNames)) {
            return false;
        }
        ship.unitNames = names;
        return true;
    }

    private static ArrayList<String> collect(CapaTeitoku capa, int shipUid) {
        ArrayList<String> names = new ArrayList<>();
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                if (capa.getTeamMember(team, slot) == shipUid) {
                    names.add(capa.getUnitName(team));
                    break;
                }
            }
        }
        return names;
    }
}
