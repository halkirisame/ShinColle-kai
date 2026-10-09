package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerShimakaze;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShimakazeRensouhouFixedGameTests {

    private ShimakazeRensouhouFixedGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_shimakaze_normal_summon")
    public static void lightAttackAlwaysSpawnsNormalRensouhou(GameTestHelper helper) {
        Vec3 position = new Vec3(0.5D, 2D, 0.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                EntityDestroyerShimakaze ship = shimakaze(helper, entities);
                Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
                helper.assertTrue(target != null, "Could not create target");
                ship.moveTo(helper.absoluteVec(position));
                target.moveTo(helper.absoluteVec(position.add(2D, 0D, 0D)));
                ship.setStateMinor(ID.M.NumGrudge, 1000);
                ship.setStateFlag(ID.F.NoFuel, false);
                helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0
                        && !ship.getStateFlag(ID.F.NoFuel), "Fixture must have fuel");

                for (int state : new int[]{0, 0b111111}) {
                    ship.setStateEmotion(ID.S.State, state, false);
                    ship.setStateMinor(ID.M.NumAmmoLight, 100);
                    int ammoBefore = ship.getStateMinor(ID.M.NumAmmoLight);
                    int summonsBefore = ship.getNumServant();
                    helper.assertTrue(ship.getStateEmotion(ID.S.State) == state,
                            "Fixture appearance state was not applied");
                    helper.assertTrue(ammoBefore >= 4 * ship.getAmmoConsumption(),
                            "Fixture must have enough light ammunition");
                    helper.assertTrue(ship.attackEntityWithAmmo(target), "Light attack did not summon");
                    List<EntityRensouhou> spawned = helper.getLevel().getEntitiesOfClass(
                            EntityRensouhou.class, ship.getBoundingBox().inflate(1D));
                    spawned.forEach(entities::add);
                    helper.assertTrue(spawned.size() == 1, "Expected exactly one registered turret");
                    EntityRensouhou summon = spawned.get(0);
                    helper.assertTrue(summon.getClass() == EntityRensouhou.class
                                    && summon.getType() == ModEntities.RENSOUHOU.get(),
                            "Shimakaze must summon normal Rensouhou even when appearance bit0 is set");
                    helper.assertTrue(summon.getTarget() == target, "Summon lost the assigned target");
                    helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight)
                                    == ammoBefore - 4 * ship.getAmmoConsumption(),
                            "Summon ammunition cost changed");
                    helper.assertTrue(ship.getNumServant() == summonsBefore - 1,
                            "Summon stock cost changed");
                    helper.assertTrue(ship.getStateEmotion(ID.S.State) == state,
                            "Summoning must not change appearance bits");
                    summon.discard();
                }
                helper.succeed();
            }
        }, position);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shimakazeOnlyAllowsVisibleAppearanceBits(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            EntityDestroyerShimakaze ship = shimakaze(helper, entities);
            helper.assertTrue(!ship.isAppearanceBitToggleable(-1)
                    && !ship.isAppearanceBitToggleable(0), "Hidden and negative bits must be rejected");
            for (int bit = 1; bit <= 5; bit++) {
                helper.assertTrue(ship.isAppearanceBitToggleable(bit), "Visible bit must remain usable: " + bit);
            }
            helper.assertTrue(!ship.isAppearanceBitToggleable(6)
                    && !ship.isAppearanceBitToggleable(16), "Out-of-range bits must be rejected");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hibikiKeepsItsFirstAppearanceToggle(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            helper.assertTrue(ship != null && ship.isAppearanceBitToggleable(0),
                    "Hibiki must retain its visible first bit");
            ship.setStateEmotion(ID.S.State, 0, false);
            C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_ModelState01, 1);
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 1, "Hibiki first toggle stopped working");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void serverRejectsHiddenAppearanceBitButTogglesVisibleBit(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            EntityDestroyerShimakaze ship = shimakaze(helper, entities);
            ship.setStateEmotion(ID.S.State, 0b111111, false);
            C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_ModelState01, 0);
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 0b111111,
                    "Server must reject the hidden appearance bit instead of toggling it");
            C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_ModelState01 + 1, 0);
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 0b111101,
                    "Server must still toggle visible bit1");
            C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_ModelState01 + 6, 1);
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 0b111101,
                    "Server must reject an out-of-range appearance bit");
            helper.succeed();
        }
    }

    private static EntityDestroyerShimakaze shimakaze(GameTestHelper helper, GameTestEntities entities) {
        EntityDestroyerShimakaze ship = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create Shimakaze");
        return ship;
    }
}
