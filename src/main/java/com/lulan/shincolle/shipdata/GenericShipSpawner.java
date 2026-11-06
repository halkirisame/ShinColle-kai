package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Creates, but does not add, administrator/egg ships through one initialization path. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class GenericShipSpawner {
    private GenericShipSpawner() {
    }

    @SubscribeEvent
    public static void rejectEmptyIdentity(EntityJoinLevelEvent event) {
        // Client identities arrive after spawn packets; named missing definitions must remain recoverable.
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof DefinitionBackedShip ship
                && ship.getShipId().isEmpty()) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }

    public static Mob create(ServerLevel level, ShipDefinitionId id, boolean hostile, Player owner) {
        if (ShipDefinitionRegistry.server().get(id) == null) return null;
        if (hostile) {
            GenericHostileShipEntity ship = ModEntities.GENERIC_SHIP_HOSTILE.get().create(level);
            if (ship == null) return null;
            ship.initialize(id);
            return ship;
        }
        GenericShipEntity ship = ModEntities.GENERIC_SHIP.get().create(level);
        if (ship == null) return null;
        ship.setShipLevel(1, false);
        ship.setStateMinor(ID.M.NumGrudge, Values.N.BaseGrudge);
        ship.setStateMinor(ID.M.NumAmmoLight, Values.N.BaseLightAmmo);
        ship.setStateMinor(ID.M.NumAmmoHeavy, Values.N.BaseHeavyAmmo);
        ship.setStateFlag(ID.F.NoFuel, false);
        if (owner != null) {
            ship.tame(owner);
            owner.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> ship.setPlayerUID(capa.getPlayerUID()));
        }
        ship.initialize(id);
        return ship;
    }
}
