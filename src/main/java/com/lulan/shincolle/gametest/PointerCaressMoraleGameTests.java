package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointerCaressMoraleGameTests {

    private PointerCaressMoraleGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void caressModesAddBaseMoraleAndPersistIt(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "modes", 96701)) {
            BasicEntityShip ship = fixture.ship();
            for (int mode = 3; mode <= 5; mode++) {
                prepare(helper, fixture, mode, 2000, false);
                helper.assertTrue(TeamHelper.checkSameOwner(fixture.player(), ship),
                        "Fixture must have the same owner UID");
                InteractionResult result = ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
                int expected = 2000 + ConfigHandler.caressBaseMorale();
                helper.assertTrue(result == InteractionResult.SUCCESS && ship.getMorale() == expected,
                        "Caress mode " + mode + " did not add base morale: " + ship.getMorale());
                helper.assertTrue(ship.getAITarget() == null, "Caress retained its temporary AI target");

                CompoundTag saved = new CompoundTag();
                CapaShipSavedValues.saveNBTData(saved, ship);
                helper.assertTrue(saved.getIntArray("StateMinor")[ID.M.Morale] == expected,
                        "Saved morale did not include the caress gain");
                BasicEntityShip loaded = fixture.entities().add(
                        ModEntities.DESTROYER_RO.get().create(helper.getLevel()));
                helper.assertTrue(loaded != null, "Could not create save round-trip ship");
                CapaShipSavedValues.loadNBTData(saved, loaded);
                helper.assertTrue(loaded.getMorale() == expected, "Reload lost the caress gain");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void staleOwnerUidIsRepairedBeforeCaressMoraleGain(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "stale_uid", 96702)) {
            BasicEntityShip ship = fixture.ship();
            for (int staleUid : new int[]{0, 1234}) {
                prepare(helper, fixture, 3, 2000, false);
                ship.setPlayerUID(staleUid);
                helper.assertTrue(ship.getPlayerUID() == staleUid
                        && !TeamHelper.checkSameOwner(fixture.player(), ship),
                        "Fixture must begin with a stale owner UID");
                ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
                helper.assertTrue(ship.getPlayerUID() == 96702
                        && TeamHelper.checkSameOwner(fixture.player(), ship),
                        "Owner UID was not repaired by the interaction");
                helper.assertTrue(ship.getMorale() == 2000 + ConfigHandler.caressBaseMorale(),
                        "Caress did not gain morale in the same interaction that repaired the UID");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void caressKeepsOwnerFuelAndMoraleThresholdConditions(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "conditions", 96703)) {
            BasicEntityShip ship = fixture.ship();
            prepare(helper, fixture, 3, 2000, true);
            ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
            helper.assertTrue(ship.getMorale() == 2000, "Fuel-empty caress must not add base morale");

            prepare(helper, fixture, 3, 2000, false);
            ship.setOwnerUUID(UUID.nameUUIDFromBytes("caress_other_owner".getBytes(StandardCharsets.UTF_8)));
            ship.setPlayerUID(96799);
            helper.assertTrue(!TeamHelper.checkSameOwner(fixture.player(), ship),
                    "Fixture must be a different owner");
            ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
            helper.assertTrue(ship.getMorale() == 2000 && ship.getPlayerUID() == 96799,
                    "Stranger caress changed morale or transferred ownership");

            ship.setOwnerUUID(fixture.player().getUUID());
            ship.setPlayerUID(96703);
            int threshold = (int) (ID.Morale.L_Excited * 1.3F);
            prepare(helper, fixture, 3, threshold, false);
            ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
            helper.assertTrue(ship.getMorale() == threshold, "Caress ignored the base morale threshold");
            prepare(helper, fixture, 3, threshold - 1, false);
            ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
            helper.assertTrue(ship.getMorale() == threshold - 1 + ConfigHandler.caressBaseMorale(),
                    "Caress below the threshold must add the full configured gain");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void commandModesAndOffhandDoNotAddCaressMorale(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "non_caress", 96704)) {
            BasicEntityShip ship = fixture.ship();
            for (int mode = 0; mode <= 2; mode++) {
                prepare(helper, fixture, mode, 2000, false);
                ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
                helper.assertTrue(ship.getMorale() == 2000,
                        "Command mode " + mode + " added caress morale");
            }
            prepare(helper, fixture, 3, 2000, false);
            ItemStack pointer = fixture.player().getMainHandItem();
            fixture.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fixture.player().setItemInHand(InteractionHand.OFF_HAND, pointer);
            helper.assertTrue(ship.mobInteract(fixture.player(), InteractionHand.OFF_HAND) == InteractionResult.FAIL
                    && ship.getMorale() == 2000, "Offhand caress added morale");
            helper.succeed();
        }
    }

    private static void prepare(GameTestHelper helper, Fixture fixture, int mode, int morale, boolean noFuel) {
        ItemStack pointer = new ItemStack(ModItems.POINTER.get());
        PointerItem.setMode(pointer, mode);
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, pointer);
        fixture.player().setShiftKeyDown(false);
        BasicEntityShip ship = fixture.ship();
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateFlag(ID.F.NoFuel, noFuel);
        ship.setMorale(morale);
        // Hold the normal reaction cooldown to measure the unconditional base gain.
        ship.setEmotesTick(100);
        helper.assertTrue(PointerItem.getMode(fixture.player().getMainHandItem()) == mode
                && ship.getMorale() == morale && ship.getStateFlag(ID.F.NoFuel) == noFuel
                && ship.getStateMinor(ID.M.NumGrudge) > 0 && ship.getEmotesTick() == 100,
                "Caress fixture state was not applied");
    }

    private static Fixture fixture(GameTestHelper helper, String name, int uid) {
        GameTestEntities entities = GameTestEntities.open(helper);
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_RO.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create caress ship");
        UUID uuid = UUID.nameUUIDFromBytes(("caress_morale_" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(uuid, "caress_" + name));
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        helper.assertTrue(capa != null, "Caress player has no Teitoku capability");
        capa.setPlayerUID(uid);
        ship.setOwnerUUID(player.getUUID());
        ship.setPlayerUID(uid);
        helper.assertTrue(ship.getOwnerUUID().equals(player.getUUID())
                && TeamHelper.getPlayerUID(player) == uid && ship.getPlayerUID() == uid,
                "Caress fixture owner was not applied");
        return new Fixture(ship, player, entities);
    }

    private record Fixture(BasicEntityShip ship, FakePlayer player, GameTestEntities entities) implements AutoCloseable {
        @Override
        public void close() {
            if (this.ship.getShipUID() > 0) {
                ServerDataManager.removeShipData(this.ship.getShipUID());
            }
            this.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            this.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            this.entities.close();
        }
    }
}
