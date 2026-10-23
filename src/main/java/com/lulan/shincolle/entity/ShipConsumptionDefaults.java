package com.lulan.shincolle.entity;

import com.lulan.shincolle.entity.battleship.EntityBattleshipRe;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRu;
import com.lulan.shincolle.entity.battleship.EntityBattleshipTa;
import com.lulan.shincolle.entity.cruiser.EntityCANe;
import com.lulan.shincolle.entity.cruiser.EntityCARi;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerHa;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerI;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerNi;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerRo;
import com.lulan.shincolle.entity.hime.EntityAirfieldHime;
import com.lulan.shincolle.entity.hime.EntityBattleshipHime;
import com.lulan.shincolle.entity.hime.EntityCAHime;
import com.lulan.shincolle.entity.hime.EntityCarrierHime;
import com.lulan.shincolle.entity.hime.EntityCarrierWD;
import com.lulan.shincolle.entity.hime.EntityDestroyerHime;
import com.lulan.shincolle.entity.hime.EntityHarbourHime;
import com.lulan.shincolle.entity.hime.EntityIsolatedHime;
import com.lulan.shincolle.entity.hime.EntityMidwayHime;
import com.lulan.shincolle.entity.hime.EntityNorthernHime;
import com.lulan.shincolle.entity.hime.EntitySSNH;
import com.lulan.shincolle.entity.hime.EntitySubmHime;
import com.lulan.shincolle.entity.submarine.EntitySubmKa;
import com.lulan.shincolle.entity.submarine.EntitySubmSo;
import com.lulan.shincolle.entity.submarine.EntitySubmYo;
import com.lulan.shincolle.entity.transport.EntityTransportWa;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import java.util.Map;

/** Internal save-load normalization for ships whose consumption is derived from class config. */
public final class ShipConsumptionDefaults {
    private static final Map<Class<? extends BasicEntityShip>, Byte> INDICES = Map.ofEntries(
            Map.entry(EntityBattleshipRe.class, ID.ShipConsume.BBV),
            Map.entry(EntityBattleshipRu.class, ID.ShipConsume.BB),
            Map.entry(EntityBattleshipTa.class, ID.ShipConsume.BB),
            Map.entry(EntityCANe.class, ID.ShipConsume.CA),
            Map.entry(EntityCARi.class, ID.ShipConsume.CA),
            Map.entry(EntityDestroyerI.class, ID.ShipConsume.DD),
            Map.entry(EntityDestroyerRo.class, ID.ShipConsume.DD),
            Map.entry(EntityDestroyerHa.class, ID.ShipConsume.DD),
            Map.entry(EntityDestroyerNi.class, ID.ShipConsume.DD),
            Map.entry(EntityAirfieldHime.class, ID.ShipConsume.BBV),
            Map.entry(EntityBattleshipHime.class, ID.ShipConsume.BB),
            Map.entry(EntityCAHime.class, ID.ShipConsume.CA),
            Map.entry(EntityCarrierHime.class, ID.ShipConsume.CV),
            Map.entry(EntityCarrierWD.class, ID.ShipConsume.CV),
            Map.entry(EntityDestroyerHime.class, ID.ShipConsume.DD),
            Map.entry(EntityHarbourHime.class, ID.ShipConsume.BBV),
            Map.entry(EntityIsolatedHime.class, ID.ShipConsume.BBV),
            Map.entry(EntityMidwayHime.class, ID.ShipConsume.BBV),
            Map.entry(EntityNorthernHime.class, ID.ShipConsume.BBV),
            Map.entry(EntitySubmHime.class, ID.ShipConsume.SS),
            Map.entry(EntitySSNH.class, ID.ShipConsume.SS),
            Map.entry(EntitySubmKa.class, ID.ShipConsume.SS),
            Map.entry(EntitySubmYo.class, ID.ShipConsume.SS),
            Map.entry(EntitySubmSo.class, ID.ShipConsume.SS),
            Map.entry(EntityTransportWa.class, ID.ShipConsume.AP));

    private ShipConsumptionDefaults() {
    }

    public static void restore(BasicEntityShip ship) {
        Byte index = INDICES.get(ship.getClass());
        if (index == null) return;
        ship.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[index]);
        ship.setAmmoConsumption(ConfigHandler.consumeAmmoShip[index]);
    }
}
