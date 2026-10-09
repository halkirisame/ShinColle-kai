package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.hime.HimeRiding;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HimeRidingGameTests {
    private HimeRidingGameTests() { }

    private static BasicEntityShip create(GameTestHelper helper, GameTestEntities entities,
            EntityType<? extends BasicEntityShip> type) {
        BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
        ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
        helper.getLevel().addFreshEntity(ship);
        ship.setNoAi(true);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.enableCommandProjectionCheckForTest();
        GameTestEntities.assertRegistered(helper, ship);
        helper.assertTrue(helper.getLevel().getEntity(ship.getId()) == ship,
                "Fixture must be addressable by network entity ID");
        return ship;
    }

    private static FakePlayer owner(GameTestHelper helper, BasicEntityShip ship) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "hime_riding_owner"));
        int uid = Math.floorMod(ship.getUUID().hashCode(), 100000) + 30000;
        player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Missing owner capability")).setPlayerUID(uid);
        ship.setOwnerUUID(player.getUUID());
        ship.setPlayerUID(uid);
        player.moveTo(ship.position());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        return player;
    }

    private static void sit(BasicEntityShip ship, FakePlayer owner, boolean modern) {
        if (modern) {
            ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
        } else {
            ship.setEntitySit(true);
        }
    }

    private static void click(GameTestHelper helper, EntityType<? extends BasicEntityShip> type,
            ConfigHandler.ShipAiTargetAuthority mode) {
        try (var authority = ShipAiAuthorityOverride.use(mode);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = create(helper, entities, type);
            FakePlayer player = owner(helper, ship);
            sit(ship, player, mode == ConfigHandler.ShipAiTargetAuthority.NEW);
            ship.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
            ship.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(ship.getVehicle() == player, "Bored seated hime should ride owner");
            helper.assertTrue(!ship.isOrderedToSit() && (mode != ConfigHandler.ShipAiTargetAuthority.NEW
                            || !ship.getCommandState().sitting()),
                    "Click should stand once without the normal sit toggle");
            ship.assertCommandProjection();
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void northernClickNew(GameTestHelper helper) {
        click(helper, ModEntities.NORTHERN_HIME.get(), ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void northernClickLegacy(GameTestHelper helper) {
        click(helper, ModEntities.NORTHERN_HIME.get(), ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ssnhClickNew(GameTestHelper helper) {
        click(helper, ModEntities.SSNH.get(), ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ssnhClickLegacy(GameTestHelper helper) {
        click(helper, ModEntities.SSNH.get(), ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    private static void rejectedClicks(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (var authority = ShipAiAuthorityOverride.use(mode);
                var entities = GameTestEntities.open(helper)) {
            for (EntityType<? extends BasicEntityShip> type :
                    java.util.List.<EntityType<? extends BasicEntityShip>>of(
                            ModEntities.NORTHERN_HIME.get(), ModEntities.SSNH.get(), ModEntities.BB_KONGOU.get())) {
                for (int condition = 0; condition < (type == ModEntities.BB_KONGOU.get() ? 6 : 5);
                        condition++) {
                    BasicEntityShip ship = create(helper, entities, type);
                    FakePlayer player = owner(helper, ship);
                    boolean modern = mode == ConfigHandler.ShipAiTargetAuthority.NEW;
                    sit(ship, player, modern);
                    ship.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
                    if (condition == 0) {
                        if (modern) {
                            ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                                    new CommandStateOp.StandUp());
                        } else {
                            ship.setEntitySit(false);
                        }
                    } else if (condition == 1) {
                        ship.setStateEmotion(ID.S.Emotion, ID.Emotion.NORMAL, false);
                    } else if (condition == 2) {
                        player.setItemInHand(InteractionHand.MAIN_HAND,
                                new ItemStack(ModItems.POINTER.get()));
                    } else if (condition == 3) {
                        ship.setOwnerUUID(UUID.randomUUID());
                        ship.setPlayerUID(-1);
                    }
                    boolean wasSitting = modern ? ship.getCommandState().sitting() : ship.isOrderedToSit();
                    ship.mobInteract(player, condition == 4 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    helper.assertTrue(ship.getVehicle() == null, "Rejected interaction mounted the ship");
                    boolean sitting = modern ? ship.getCommandState().sitting() : ship.isOrderedToSit();
                    if (condition == 0 || condition == 1 || condition == 5) {
                        helper.assertTrue(sitting != wasSitting, "Ordinary click should toggle sitting");
                    }
                    if (condition == 2 || condition == 3 || condition == 4) {
                        helper.assertTrue(sitting == wasSitting,
                                "Pointer, non-owner or off-hand should not toggle sitting");
                    }
                    ship.assertCommandProjection();
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectedClicksNew(GameTestHelper helper) {
        rejectedClicks(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectedClicksLegacy(GameTestHelper helper) {
        rejectedClicks(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    private static void crouchingClick(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (var authority = ShipAiAuthorityOverride.use(mode);
                var entities = GameTestEntities.open(helper)) {
            for (EntityType<? extends BasicEntityShip> type :
                    java.util.List.<EntityType<? extends BasicEntityShip>>of(
                            ModEntities.NORTHERN_HIME.get(), ModEntities.SSNH.get())) {
                BasicEntityShip ship = create(helper, entities, type);
                FakePlayer player = owner(helper, ship);
                sit(ship, player, mode == ConfigHandler.ShipAiTargetAuthority.NEW);
                ship.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
                player.setShiftKeyDown(true);
                InteractionResult result = ship.mobInteract(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.SUCCESS, "Crouching owner should reach GUI interaction");
                helper.assertTrue(!ship.isPassenger() && ship.isOrderedToSit(),
                        "Crouching click should leave the seated hime off the player");
                ship.assertCommandProjection();
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void crouchingClickNew(GameTestHelper helper) {
        crouchingClick(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void crouchingClickLegacy(GameTestHelper helper) {
        crouchingClick(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void heldItemDoesNotMount(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = create(helper, entities, ModEntities.SSNH.get());
            FakePlayer player = owner(helper, ship);
            sit(ship, player, true);
            ship.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            ship.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!ship.isPassenger() && !ship.isOrderedToSit(),
                    "Held non-pointer item should reach the ordinary interaction");
            ship.assertCommandProjection();
            helper.succeed();
        }
    }

    private static void packetRejected(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (var authority = ShipAiAuthorityOverride.use(mode);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = create(helper, entities, ModEntities.SSNH.get());
            FakePlayer player = owner(helper, ship);
            sit(ship, player, mode == ConfigHandler.ShipAiTargetAuthority.NEW);
            ship.setStateEmotion(ID.S.Emotion, ID.Emotion.NORMAL, false);
            Method handler = C2SInputPacket.class.getDeclaredMethod("handleRequestRiding",
                    net.minecraft.server.level.ServerPlayer.class);
            handler.setAccessible(true);
            handler.invoke(new C2SInputPacket(C2SInputPacket.Request_Riding, ship.getId()), player);
            helper.assertTrue(ship.getVehicle() == null && ship.isOrderedToSit(),
                    "Packet bypassed boredom requirement");
            ship.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
            handler.invoke(new C2SInputPacket(C2SInputPacket.Request_Riding, ship.getId()), player);
            helper.assertTrue(ship.getVehicle() == player,
                    "A valid packet must reach the registered hime");
            ship.stopRiding();
            ship.assertCommandProjection();
            helper.succeed();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void packetRejectedNew(GameTestHelper helper) {
        packetRejected(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void packetRejectedLegacy(GameTestHelper helper) {
        packetRejected(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void packetRejectsRemoteOwner(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = create(helper, entities, ModEntities.SSNH.get());
            FakePlayer player = owner(helper, ship);
            sit(ship, player, true);
            ship.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
            Method handler = C2SInputPacket.class.getDeclaredMethod("handleRequestRiding",
                    net.minecraft.server.level.ServerPlayer.class);
            handler.setAccessible(true);
            C2SInputPacket request = new C2SInputPacket(C2SInputPacket.Request_Riding, ship.getId());
            player.moveTo(ship.getX() + 9D, ship.getY(), ship.getZ());
            handler.invoke(request, player);
            helper.assertTrue(!ship.isPassenger() && ship.isOrderedToSit(),
                    "Remote owner should not mount by packet");
            FakePlayer otherLevel = FakePlayerFactory.get(helper.getLevel().getServer().getLevel(Level.NETHER),
                    new GameProfile(UUID.randomUUID(), "hime_other_level"));
            helper.assertTrue(otherLevel.level() != ship.level(), "Fixture needs a distinct level");
            otherLevel.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing owner capability"))
                    .setPlayerUID(ship.getPlayerUID());
            ship.setOwnerUUID(otherLevel.getUUID());
            handler.invoke(request, otherLevel);
            helper.assertTrue(!ship.isPassenger() && ship.isOrderedToSit(),
                    "Different-level owner should not mount by packet");
            ship.assertCommandProjection();
            helper.succeed();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void dismount(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (var authority = ShipAiAuthorityOverride.use(mode);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = create(helper, entities, ModEntities.SSNH.get());
            FakePlayer player = owner(helper, ship);
            helper.assertTrue(ship.startRiding(player, true), "Fixture needs rider");
            player.setShiftKeyDown(true);
            ship.aiStep();
            helper.assertTrue(!ship.isPassenger(), "Crouching carrier should release hime");
            player.setShiftKeyDown(false);
            helper.assertTrue(ship.startRiding(player, true), "Fixture needs rider after crouch");
            ship.hurt(helper.getLevel().damageSources().generic(), 1F);
            helper.assertTrue(!ship.isPassenger(), "Damage should release hime");
            ship.assertCommandProjection();
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dismountNew(GameTestHelper helper) {
        dismount(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dismountLegacy(GameTestHelper helper) {
        dismount(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    private static void forceDraw(BasicEntityShip ship) {
        ship.tickCount = 256;
        long seed = 0;
        do {
            ship.getRandom().setSeed(seed++);
        } while (ship.getRandom().nextInt(3) != 0);
        ship.getRandom().setSeed(seed - 1);
    }

    private static void rideTick(BasicEntityShip ship) {
        try {
            Field field = ship.getClass().getDeclaredField("riding");
            field.setAccessible(true);
            ((HimeRiding) field.get(ship)).tick();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void wandering(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (var authority = ShipAiAuthorityOverride.use(mode);
                var entities = GameTestEntities.open(helper)) {
            for (EntityType<? extends BasicEntityShip> type :
                    java.util.List.<EntityType<? extends BasicEntityShip>>of(
                            ModEntities.NORTHERN_HIME.get(), ModEntities.SSNH.get())) {
                BasicEntityShip ship = create(helper, entities, type);
                FakePlayer player = owner(helper, ship);
                BasicEntityShip target = create(helper, entities, ModEntities.BB_KONGOU.get());
                target.setOwnerUUID(player.getUUID());
                target.setPlayerUID(ship.getPlayerUID());
                forceDraw(ship);
                rideTick(ship);
                helper.assertTrue(ship.getVehicle() == target, "Hime did not board the nearby allied ship");
                ship.stopRiding();
                ship.assertCommandProjection();
                if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                    ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                            new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                    new DimensionKey("minecraft", "overworld"), new CommandPos(10, 64, 10), false)));
                } else {
                    ship.setStateFlag(ID.F.CanFollow, false);
                }
                forceDraw(ship);
                rideTick(ship);
                helper.assertTrue(!ship.isPassenger(), "Guarding hime should not wander");
                if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                    ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                            new CommandStateOp.Apply(new ShipCommand.Follow()));
                } else {
                    ship.setStateFlag(ID.F.CanFollow, true);
                }
                sit(ship, player, mode == ConfigHandler.ShipAiTargetAuthority.NEW);
                forceDraw(ship);
                rideTick(ship);
                helper.assertTrue(!ship.isPassenger(), "Seated hime should not wander");
                ship.assertCommandProjection();
                target.discard();
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void wanderingNew(GameTestHelper helper) {
        wandering(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void wanderingLegacy(GameTestHelper helper) {
        wandering(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void spectatorIsNotRideTarget(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = create(helper, entities, ModEntities.SSNH.get());
            FakePlayer player = owner(helper, ship);
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(player.isSpectator(), "Fixture needs a spectator");
            entities.add(player);
            helper.getLevel().addFreshEntity(player);
            GameTestEntities.assertRegistered(helper, player);
            forceDraw(ship);
            rideTick(ship);
            helper.assertTrue(!ship.isPassenger(), "Hime should not board a spectator");
            ship.assertCommandProjection();
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savingPlayerPassengerDismounts(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            for (EntityType<? extends BasicEntityShip> type :
                    java.util.List.<EntityType<? extends BasicEntityShip>>of(
                            ModEntities.NORTHERN_HIME.get(), ModEntities.SSNH.get())) {
                BasicEntityShip ship = create(helper, entities, type);
                FakePlayer player = owner(helper, ship);
                helper.assertTrue(ship.startRiding(player, true), "Fixture needs player carrier");
                ship.addAdditionalSaveData(new CompoundTag());
                helper.assertTrue(!ship.isPassenger(), "Save hook should detach player passenger");
                ship.assertCommandProjection();
            }
            helper.succeed();
        }
    }
}
