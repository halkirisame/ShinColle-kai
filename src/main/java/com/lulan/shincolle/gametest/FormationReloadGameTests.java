package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.FormationHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FormationReloadGameTests {
    private FormationReloadGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newFormationSurvivesOrdinaryLoadBeforeRegistration(GameTestHelper helper) {
        verify(helper, ConfigHandler.ShipAiTargetAuthority.NEW, 26401, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyOrdinaryLoadKeepsItsExistingFormationBehavior(GameTestHelper helper) {
        verify(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, 26402, false);
    }

    private static void verify(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority authority,
                               int id, boolean retains) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var mode = ShipAiAuthorityOverride.use(authority);
                 var context = PointerSingleModeGameTests.createContext(helper, "formation_reload_" + id, id);
                 var online = new FormationGameTestOwner(context.player())) {
                List<BasicEntityShip> loaded = new ArrayList<>();
                context.capa().setFormatID(0, 1);
                helper.assertTrue(ServerDataManager.getPlayerByUID(context.capa().getPlayerUID()) == context.player(),
                        "Fixture owner must resolve through the production lookup");
                List<CompoundTag> saves = new ArrayList<>();
                for (int slot = 0; slot < 5; slot++) {
                    BasicEntityShip source = context.entities().add(ModEntities.BB_KONGOU.get().create(context.level()));
                    source.setNoAi(true);
                    source.setOwnerUUID(context.player().getUUID());
                    source.setPlayerUID(context.capa().getPlayerUID());
                    source.setShipUID(id * 100 + slot);
                    Vec3 pos = helper.absoluteVec(new Vec3(4.5D + slot, 2D, 1.5D));
                    source.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
                    source.setStateMinor(ID.M.FormatType, 1);
                    source.setStateMinor(ID.M.FormatPos, slot);
                    source.setStateMinor(ID.M.NumGrudge, 100_000);
                    context.capa().setTeamMember(0, slot, source.getShipUID());
                    context.capa().setTeamSID(0, slot, -1);
                    saves.add(source.saveWithoutId(new CompoundTag()));
                }
                helper.assertTrue(context.capa().getNumberOfShip(context.level(), 0) == 0,
                        "Fixture must have no registered ships before ordinary load");
                try {
                    for (int slot = 0; slot < 5; slot++) {
                        BasicEntityShip ship = context.entities().add(ModEntities.BB_KONGOU.get().create(context.level()));
                        loaded.add(ship);
                        ship.readAdditionalSaveData(saves.get(slot));
                        helper.assertTrue(ship.getShipUID() == id * 100 + slot, "Saved UID must load");
                        helper.assertTrue(ship.getStateMinor(ID.M.FormatType) == (retains ? 1 : 0)
                                        && ship.getStateMinor(ID.M.FormatPos) == slot,
                                "Ordinary load before registration must retain saved formation type/slot");
                        helper.assertTrue(((AttrsAdv) ship.getAttrs()).getMinMOV() == 0F,
                                "Unregistered formation must not grant minimum movement");
                    }
                    for (int slot = 0; slot < 5; slot++) {
                        BasicEntityShip ship = loaded.get(slot);
                        Vec3 pos = helper.absoluteVec(new Vec3(4.5D + slot, 2D, 1.5D));
                        ship.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
                        ship.setNoAi(true);
                        helper.assertTrue(context.level().addFreshEntity(ship), "Loaded ship must register");
                        ship.updateShipCacheData(true);
                        context.capa().setTeamSID(0, slot, ship.getId());
                    }
                    helper.assertTrue(context.capa().getNumberOfShip(context.level(), 0) == 5,
                            "Fixture must resolve five living ships after registration");
                    for (int slot = 0; slot < 5; slot++) {
                        BasicEntityShip ship = loaded.get(slot);
                        ship.calcShipAttributes(16, false);
                        helper.assertTrue(Arrays.equals(((AttrsAdv) ship.getAttrs()).getAttrsFormation(),
                                        FormationHelper.getFormationBuffValue(retains ? 1 : 0, slot)),
                                "Formation buff must follow its authority's established load behavior");
                        BasicEntityShip resaved = context.entities().add(ModEntities.BB_KONGOU.get().create(context.level()));
                        resaved.readAdditionalSaveData(ship.saveWithoutId(new CompoundTag()));
                        helper.assertTrue(resaved.getStateMinor(ID.M.FormatType) == (retains ? 1 : 0)
                                        && resaved.getStateMinor(ID.M.FormatPos) == slot,
                                "Resaved formation must survive another ordinary load");
                    }
                    helper.succeed();
                } finally {
                    for (BasicEntityShip ship : loaded) ServerDataManager.removeShipData(ship.getShipUID());
                }
            }
        });
    }
}
