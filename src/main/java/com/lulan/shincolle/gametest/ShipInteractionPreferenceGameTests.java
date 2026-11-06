package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerRo;
import com.lulan.shincolle.handler.ShipInteractionPreferences;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipInteractionPreferenceGameTests {
    private static final Vec3 POSITION = new Vec3(1.5D, 2D, 1.5D);

    private ShipInteractionPreferenceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerClicksFollowSessionPreference(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var entities = GameTestEntities.open(helper)) {
                FakePlayer owner = owner(helper);
                try {
                    ClickProbe first = ship(helper, entities, owner);
                    ClickProbe second = ship(helper, entities, owner);
                    helper.assertTrue(!ShipInteractionPreferences.swapClicks(owner.getUUID()),
                            "A player with no received preference must default to off");
                    for (boolean swapped : List.of(false, true, false)) {
                        ShipInteractionPreferences.setSwapClicks(owner.getUUID(), swapped);
                        for (ClickProbe ship : List.of(first, second)) {
                            for (boolean sneaking : List.of(false, true)) {
                                owner.setShiftKeyDown(sneaking);
                                for (boolean sitting : List.of(false, true)) {
                                    ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()),
                                            new CommandStateOp.Apply(new ShipCommand.SetSitting(sitting)));
                                    helper.assertTrue(ship.getCommandState().sitting() == sitting
                                                    && owner.isShiftKeyDown() == sneaking,
                                            "Click fixture must apply sitting and sneaking");
                                    int openedBefore = ship.opened;
                                    InteractionResult result = ship.mobInteract(owner, InteractionHand.MAIN_HAND);
                                    boolean opensGui = sneaking != swapped;
                                    helper.assertTrue(result == InteractionResult.SUCCESS
                                                    && ship.getCommandState().sitting() == (opensGui ? sitting : !sitting)
                                                    && ship.opened == openedBefore + (opensGui ? 1 : 0),
                                            "Owner fallback mismatch: swapped=" + swapped + ", sneak=" + sneaking
                                                    + ", initially sitting=" + sitting);
                                }
                            }
                        }
                    }
                    helper.succeed();
                } finally {
                    ShipInteractionPreferences.setSwapClicks(owner.getUUID(), false);
                }
            }
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pointerStillBlocksOnlySittingFallback(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var entities = GameTestEntities.open(helper)) {
                FakePlayer owner = owner(helper);
                try {
                    ClickProbe ship = ship(helper, entities, owner);
                    for (boolean swapped : List.of(false, true)) {
                        ShipInteractionPreferences.setSwapClicks(owner.getUUID(), swapped);
                        for (InteractionHand pointerHand : InteractionHand.values()) {
                            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                            ItemStack pointer = new ItemStack(ModItems.POINTER.get());
                            PointerItem.setMode(pointer, PointerItem.MODE_FORMATION);
                            owner.setItemInHand(pointerHand, pointer);
                            helper.assertTrue(owner.getItemInHand(pointerHand).is(ModItems.POINTER.get())
                                            && PointerItem.getMode(pointer) == PointerItem.MODE_FORMATION,
                                    "Fixture must hold a non-caress pointer");
                            for (boolean sitting : List.of(false, true)) {
                                ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()),
                                        new CommandStateOp.Apply(new ShipCommand.SetSitting(sitting)));
                                for (boolean sneaking : List.of(false, true)) {
                                    owner.setShiftKeyDown(sneaking);
                                    int openedBefore = ship.opened;
                                    ship.mobInteract(owner, InteractionHand.MAIN_HAND);
                                    helper.assertTrue(ship.getCommandState().sitting() == sitting
                                                    && ship.opened == openedBefore + (sneaking != swapped ? 1 : 0),
                                            "Pointer in either hand must block sitting, not the GUI fallback");
                                }
                            }
                        }
                    }
                    helper.succeed();
                } finally {
                    ShipInteractionPreferences.setSwapClicks(owner.getUUID(), false);
                }
            }
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void preferencesArePlayerLocalAndClearedOnLogout(GameTestHelper helper) {
        FakePlayer first = owner(helper);
        FakePlayer second = owner(helper);
        try {
            ShipInteractionPreferences.setSwapClicks(first.getUUID(), true);
            helper.assertTrue(ShipInteractionPreferences.swapClicks(first.getUUID())
                            && !ShipInteractionPreferences.swapClicks(second.getUUID()),
                    "One player's preference must not change another's default");
            ShipInteractionPreferences.setSwapClicks(second.getUUID(), true);
            ShipInteractionPreferences.onLogout(new PlayerEvent.PlayerLoggedOutEvent(first));
            helper.assertTrue(!ShipInteractionPreferences.swapClicks(first.getUUID())
                            && ShipInteractionPreferences.swapClicks(second.getUUID()),
                    "Logout must discard only the departing player's preference");
            ShipInteractionPreferences.setSwapClicks(second.getUUID(), false);
            helper.assertTrue(!ShipInteractionPreferences.swapClicks(second.getUUID()),
                    "Receiving off must immediately restore the default");
            helper.succeed();
        } finally {
            ShipInteractionPreferences.setSwapClicks(first.getUUID(), false);
            ShipInteractionPreferences.setSwapClicks(second.getUUID(), false);
        }
    }

    private static FakePlayer owner(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "click_owner"));
        player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Missing owner capability")).setPlayerUID(35401);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(false);
        return player;
    }

    private static ClickProbe ship(GameTestHelper helper, GameTestEntities entities, FakePlayer owner) {
        ClickProbe ship = entities.add(new ClickProbe(helper.getLevel()));
        ship.moveTo(helper.absoluteVec(POSITION));
        helper.getLevel().addFreshEntity(ship);
        ship.setNoAi(true);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setOwnerUUID(owner.getUUID());
        ship.setPlayerUID(TeamHelper.getPlayerUID(owner));
        helper.assertTrue(owner.getUUID().equals(ship.getOwnerUUID()) && TeamHelper.checkSameOwner(ship, owner)
                        && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                "Click fixture must have an owner and fuel");
        return ship;
    }

    private static final class ClickProbe extends EntityDestroyerRo {
        private int opened;

        private ClickProbe(ServerLevel level) {
            super(ModEntities.DESTROYER_RO.get(), level);
        }

        @Override
        public void openGUI(ServerPlayer player) {
            opened++;
        }
    }
}
