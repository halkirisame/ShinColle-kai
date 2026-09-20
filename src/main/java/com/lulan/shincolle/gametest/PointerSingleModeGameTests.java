package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointerSingleModeGameTests {

    private static final int TEAM_ID = 0;

    private PointerSingleModeGameTests() {
    }

    @GameTest(template = "arena")
    public static void singleModeAffectsOnlyLowestSelectedRealShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "single_first", 21)) {
            verifySingleModeAffectsOnlyLowestSelectedRealShip(helper, context);
        }
    }

    private static void verifySingleModeAffectsOnlyLowestSelectedRealShip(
            GameTestHelper helper, TestContext context) {
        BasicEntityShip first = addShip(context, 1, 2101, new Vec3(4.5D, 2D, 1.5D));
        BasicEntityShip second = addShip(context, 3, 2103, new Vec3(6.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 5, 2105, new Vec3(8.5D, 2D, 1.5D));
        select(context.capa(), 1, 3, 5);

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSitting,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, second.getId()}),
                "handleSetSitting", context.player());

        helper.assertTrue(first.isOrderedToSit(),
                "Single mode did not affect the lowest selected real ship.");
        helper.assertTrue(!second.isOrderedToSit() && !third.isOrderedToSit(),
                "Single mode affected a selected ship after the lowest selected real slot.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void singleModeOutOfRangeFirstShipDoesNotFallBack(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "single_range", 22)) {
            verifySingleModeOutOfRangeFirstShipDoesNotFallBack(helper, context);
        }
    }

    private static void verifySingleModeOutOfRangeFirstShipDoesNotFallBack(
            GameTestHelper helper, TestContext context) {
        Vec3 playerPos = context.player().position();
        BasicEntityShip first = addShip(context, 0, 2200,
                new Vec3(playerPos.x, playerPos.y + 65D, playerPos.z), false);
        BasicEntityShip second = addShip(context, 2, 2202, new Vec3(5.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 4, 2204, new Vec3(7.5D, 2D, 1.5D));
        select(context.capa(), 0, 2, 4);
        Zombie target = addTarget(context, new Vec3(2.5D, 2D, 4.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.AttackTarget,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                "handleAttackTarget", context.player());

        helper.assertTrue(first.getTarget() == null && second.getTarget() == null && third.getTarget() == null,
                "Single mode fell back after the lowest selected real ship was out of range.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void singleModeSkipsSelectedSlotWithoutRealShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "single_missing", 23)) {
            verifySingleModeSkipsSelectedSlotWithoutRealShip(helper, context);
        }
    }

    private static void verifySingleModeSkipsSelectedSlotWithoutRealShip(
            GameTestHelper helper, TestContext context) {
        context.capa().setTeamMember(TEAM_ID, 0, 2300);
        context.capa().setTeamSID(TEAM_ID, 0, -1);
        context.capa().setShipSelected(TEAM_ID, 0, true);
        BasicEntityShip ship = addShip(context, 2, 2302, new Vec3(5.5D, 2D, 1.5D));
        context.capa().setShipSelected(TEAM_ID, 2, true);
        Zombie target = addTarget(context, new Vec3(2.5D, 2D, 4.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.GuardEntity,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                "handleGuardEntity", context.player());

        helper.assertTrue(ship.getGuardedEntity() == target,
                "Single mode did not advance past a selected slot without a real ship.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void groupModeAffectsEverySelectedShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "group", 24)) {
            verifyGroupModeAffectsEverySelectedShip(helper, context);
        }
    }

    private static void verifyGroupModeAffectsEverySelectedShip(GameTestHelper helper, TestContext context) {
        BasicEntityShip first = addShip(context, 0, 2400, new Vec3(4.5D, 2D, 1.5D));
        BasicEntityShip second = addShip(context, 2, 2402, new Vec3(6.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 4, 2404, new Vec3(8.5D, 2D, 1.5D));
        select(context.capa(), 0, 2, 4);
        BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 4));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                new int[]{context.player().getId(), 0, PointerItem.MODE_GROUP, 1,
                        destination.getX(), destination.getY(), destination.getZ()}),
                "handleSetMove", context.player());

        assertGuardDestination(helper, first, destination, "first selected group ship");
        assertGuardDestination(helper, second, destination, "second selected group ship");
        assertGuardDestination(helper, third, destination, "third selected group ship");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void formationModeAffectsWholeTeamWithoutSelection(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "formation", 25)) {
            verifyFormationModeAffectsWholeTeamWithoutSelection(helper, context);
        }
    }

    private static void verifyFormationModeAffectsWholeTeamWithoutSelection(
            GameTestHelper helper, TestContext context) {
        BasicEntityShip first = addShip(context, 0, 2500, new Vec3(4.5D, 2D, 1.5D));
        BasicEntityShip second = addShip(context, 2, 2502, new Vec3(6.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 4, 2504, new Vec3(8.5D, 2D, 1.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSitting,
                new int[]{context.player().getId(), 0, PointerItem.MODE_FORMATION, second.getId()}),
                "handleSetSitting", context.player());

        helper.assertTrue(first.isOrderedToSit() && second.isOrderedToSit() && third.isOrderedToSit(),
                "Formation mode did not affect the whole team when no ships were selected.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void singleModeOtherDimensionFirstShipDoesNotFallBack(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "single_dimension", 26)) {
            verifySingleModeOtherDimensionFirstShipDoesNotFallBack(helper, context);
        }
    }

    private static void verifySingleModeOtherDimensionFirstShipDoesNotFallBack(
            GameTestHelper helper, TestContext context) {
        ServerLevel otherLevel = context.level().getServer().getLevel(Level.NETHER);
        if (otherLevel == null) {
            throw new AssertionError("Nether level is unavailable for pointer dimension test.");
        }

        BasicEntityShip remote = context.entities().add(ModEntities.BB_KONGOU.get().create(otherLevel));
        if (remote == null) {
            throw new AssertionError("Failed to create remote-dimension ship.");
        }
        remote.setNoAi(true);
        remote.setPlayerUID(context.capa().getPlayerUID());
        int remoteUid = 260000 + remote.getId();
        remote.setShipUID(remoteUid);
        remote.moveTo(0.5D, 64D, 0.5D, 0F, 0F);
        if (!otherLevel.addFreshEntity(remote)) {
            throw new AssertionError("Failed to add remote-dimension ship.");
        }
        ServerDataManager.updateShipID(remote);
        context.capa().setTeamMember(TEAM_ID, 0, remoteUid);
        context.capa().setTeamSID(TEAM_ID, 0, remote.getId());
        context.capa().setShipSelected(TEAM_ID, 0, true);

        BasicEntityShip local = addShip(context, 2, 2602, new Vec3(5.5D, 2D, 1.5D));
        context.capa().setShipSelected(TEAM_ID, 2, true);
        Zombie target = addTarget(context, new Vec3(2.5D, 2D, 4.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.AttackTarget,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                "handleAttackTarget", context.player());

        boolean localUntargeted = local.getTarget() == null;
        ServerDataManager.removeShipData(remoteUid);
        helper.assertTrue(localUntargeted,
                "Single mode fell back to a local ship after the first selected ship was in another dimension.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void marriageRingLivingEntityHookPerformsWedding(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "marriage_hook", 27)) {
            verifyMarriageRingLivingEntityHookPerformsWedding(helper, context);
        }
    }

    private static void verifyMarriageRingLivingEntityHookPerformsWedding(
            GameTestHelper helper, TestContext context) {
        BasicEntityShip ship = addShip(context, 0, 2700, new Vec3(4.5D, 2D, 1.5D));
        ItemStack ring = new ItemStack(ModItems.MARRIAGE_RING.get());
        context.player().setItemInHand(InteractionHand.MAIN_HAND, ring);
        context.player().setShiftKeyDown(true);

        InteractionResult result = ring.interactLivingEntity(
                context.player(), ship, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "Marriage ring living-entity hook did not consume a valid wedding interaction.");
        helper.assertTrue(ship.getStateFlag(com.lulan.shincolle.reference.ID.F.IsMarried),
                "Marriage ring living-entity hook did not marry the owned ship.");
        helper.assertTrue(context.capa().getMarriageNum() == 1,
                "Marriage ring living-entity hook did not increment the admiral marriage count.");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 240)
    public static void plainMoveReleasesDestinationAndRestoresFollow(GameTestHelper helper) {
        verifyArrivalCommand(helper, false, false, 28);
    }

    @GameTest(template = "arena", timeoutTicks = 240)
    public static void shiftGuardRetainsDestinationAfterArrival(GameTestHelper helper) {
        verifyArrivalCommand(helper, true, false, 29);
    }

    @GameTest(template = "arena", timeoutTicks = 240)
    public static void entityGuardSurvivesCompletedPath(GameTestHelper helper) {
        verifyArrivalCommand(helper, false, true, 30);
    }

    private static void verifyArrivalCommand(GameTestHelper helper, boolean persistent,
                                             boolean entityGuard, int id) {
        // The arena's implicit ground is below relative Y=0. Build the floor whose
        // top actually matches the commanded feet Y=1 instead of guessing its height.
        for (int x = 2; x <= 11; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        TestContext context = createContext(helper, "arrival_" + id, id);
        BasicEntityShip ship = addShip(context, 0, 2800 + id, new Vec3(4.5D, 1D, 4.5D));
        select(context.capa(), 0);
        ship.calcShipAttributes(31, false);
        ship.setStateMinor(com.lulan.shincolle.reference.ID.M.NumGrudge, 1000);
        ship.setStateMinor(com.lulan.shincolle.reference.ID.M.FollowMin, 0);
        ship.setStateMinor(com.lulan.shincolle.reference.ID.M.FollowMax, 0);
        ship.setStateFlag(com.lulan.shincolle.reference.ID.F.PassiveAI, true);
        ship.setNoAi(false);
        BlockPos destination = helper.absolutePos(new BlockPos(9, 1, 4));
        boolean[] reached = {false};
        // Commands are issued after the entity's tick-16 deferred AI registration.
        helper.runAtTickTime(25, () -> {
            invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                    new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 1,
                            destination.getX(), destination.getY(), destination.getZ(), persistent ? 0 : 1}),
                    "handleSetMove", context.player());
            if (entityGuard) {
                Zombie target = addTarget(context, new Vec3(9.5D, 1D, 4.5D));
                target.setInvulnerable(true);
                invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.GuardEntity,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                        "handleGuardEntity", context.player());
            }
        });
        helper.onEachTick(() -> {
            var path = ship.getNavigation().getPath();
            if (path != null && path.isDone() && path.canReach()
                    && path.getTarget().equals(destination)) {
                reached[0] = true;
            }
        });
        helper.runAtTickTime(220, () -> {
            try (context) {
                helper.assertTrue(reached[0], "Ship never completed a reachable path to " + destination
                        + ": " + ship.position() + ", path=" + ship.getNavigation().getPath());
                if (entityGuard) {
                    helper.assertTrue(ship.getStateMinor(com.lulan.shincolle.reference.ID.M.GuardType) == 2
                                    && ship.getGuardedEntity() != null
                                    && !ship.getStateFlag(com.lulan.shincolle.reference.ID.F.CanFollow),
                            "Entity guard was released by move completion");
                } else {
                    helper.assertTrue(ship.hasGuardDestination() == persistent,
                            "Arrival guard state: expected active=" + persistent
                                    + ", actual=" + ship.hasGuardDestination());
                    helper.assertTrue(ship.getStateFlag(com.lulan.shincolle.reference.ID.F.CanFollow) != persistent,
                            "Arrival did not restore the expected follow state");
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "arena")
    public static void shiftLeftAddsUnassignedShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "left_add", 41)) {
            BasicEntityShip ship = addShip(context, 0, 4100, new Vec3(4.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 0, 0);
            context.capa().setTeamSID(TEAM_ID, 0, 0);
            clickPointer(context, ship, true);
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 4100
                            && context.capa().isShipSelected(TEAM_ID, 0),
                    "Shift-left did not add and select the unassigned owned ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void shiftLeftRemovesSelectedShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "left_remove", 42)) {
            BasicEntityShip ship = addShip(context, 0, 4200, new Vec3(4.5D, 2D, 1.5D));
            select(context.capa(), 0);
            clickPointer(context, ship, true);
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0
                            && !context.capa().isShipSelected(TEAM_ID, 0),
                    "Shift-left did not remove and deselect the selected ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void shiftLeftSelectsUnselectedTeamShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "left_select", 43)) {
            BasicEntityShip ship = addShip(context, 0, 4300, new Vec3(4.5D, 2D, 1.5D));
            clickPointer(context, ship, true);
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 4300
                            && context.capa().isShipSelected(TEAM_ID, 0),
                    "Shift-left removed an unselected team ship instead of selecting it");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void plainLeftLeavesTeamAndSelectionUnchanged(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "left_plain", 44)) {
            BasicEntityShip ship = addShip(context, 0, 4400, new Vec3(4.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 0, 0);
            context.capa().setTeamSID(TEAM_ID, 0, 0);
            clickPointer(context, ship, false);
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0,
                    "Plain left changed team membership");
            context.capa().setTeamMember(TEAM_ID, 0, 4400);
            context.capa().setTeamSID(TEAM_ID, 0, ship.getId());
            clickPointer(context, ship, false);
            helper.assertTrue(!context.capa().isShipSelected(TEAM_ID, 0),
                    "Plain left changed the selected ship");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void explicitTeamInputAddsSelectedAndRemovesWithoutSneaking(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "key_team", 10201)) {
            BasicEntityShip ship = addShip(context, 0, 1020100, new Vec3(4.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 0, 0);
            context.capa().setTeamSID(TEAM_ID, 0, 0);
            context.player().setShiftKeyDown(false);
            C2SGUIInputPacket command = new C2SGUIInputPacket(C2SGUIInputPacket.AddTeam,
                    new int[]{context.player().getId(), 0, ship.getId(), 1});
            invokePacketHandler(command, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 1020100
                            && context.capa().isShipSelected(TEAM_ID, 0),
                    "Explicit team input without sneak did not add and select the owned ship");
            invokePacketHandler(command, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0
                            && !context.capa().isShipSelected(TEAM_ID, 0),
                    "Explicit team input without sneak did not remove the selected ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void singleModeAddingShipSelectsOnlyAddedShip(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "add_single", 47)) {
            addShip(context, 0, 4700, new Vec3(4.5D, 2D, 1.5D));
            BasicEntityShip added = addShip(context, 1, 4701, new Vec3(5.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 1, 0);
            context.capa().setTeamSID(TEAM_ID, 1, 0);
            select(context.capa(), 0);
            PointerItem.setMode(context.player().getMainHandItem(), PointerItem.MODE_SINGLE);

            clickPointer(context, added, true);

            helper.assertTrue(!context.capa().isShipSelected(TEAM_ID, 0)
                            && context.capa().isShipSelected(TEAM_ID, 1),
                    "Single mode did not select only the newly added ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void groupModeAddingShipPreservesExistingSelection(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "add_group", 48)) {
            addShip(context, 0, 4800, new Vec3(4.5D, 2D, 1.5D));
            BasicEntityShip added = addShip(context, 1, 4801, new Vec3(5.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 1, 0);
            context.capa().setTeamSID(TEAM_ID, 1, 0);
            select(context.capa(), 0);
            PointerItem.setMode(context.player().getMainHandItem(), PointerItem.MODE_GROUP);

            clickPointer(context, added, true);

            helper.assertTrue(context.capa().isShipSelected(TEAM_ID, 0)
                            && context.capa().isShipSelected(TEAM_ID, 1),
                    "Group mode did not preserve selection while selecting the newly added ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void moveCommandReachesShipImmediatelyAfterAdding(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "add_move", 49)) {
            BasicEntityShip ship = addShip(context, 0, 4900, new Vec3(4.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 0, 0);
            context.capa().setTeamSID(TEAM_ID, 0, 0);
            PointerItem.setMode(context.player().getMainHandItem(), PointerItem.MODE_SINGLE);
            clickPointer(context, ship, true);
            BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 4));

            invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                    new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 1,
                            destination.getX(), destination.getY(), destination.getZ(), 1}),
                    "handleSetMove", context.player());

            assertGuardDestination(helper, ship, destination, "newly added ship");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void teamInputRetainsLegacyAndAuthorizationChecks(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "key_checks", 10202)) {
            BasicEntityShip ship = addShip(context, 0, 1020200, new Vec3(4.5D, 2D, 1.5D));
            context.capa().setTeamMember(TEAM_ID, 0, 0);
            context.capa().setTeamSID(TEAM_ID, 0, 0);
            context.player().setShiftKeyDown(true);
            for (int flag : new int[]{0, -1, 2}) {
                invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.AddTeam,
                        new int[]{context.player().getId(), 0, ship.getId(), flag}),
                        "handleAddTeam", context.player());
                helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0,
                        "Inactive/invalid explicit input bypassed rejection while sneaking: " + flag);
            }
            C2SGUIInputPacket legacy = new C2SGUIInputPacket(C2SGUIInputPacket.AddTeam,
                    new int[]{context.player().getId(), 0, ship.getId()});
            context.player().setShiftKeyDown(false);
            invokePacketHandler(legacy, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0,
                    "Legacy packet acted without sneak");

            C2SGUIInputPacket explicit = new C2SGUIInputPacket(C2SGUIInputPacket.AddTeam,
                    new int[]{context.player().getId(), 0, ship.getId(), 1});
            context.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            invokePacketHandler(explicit, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0,
                    "Explicit input bypassed the held pointer check");
            context.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));
            ship.setPlayerUID(context.capa().getPlayerUID() + 1);
            invokePacketHandler(explicit, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0,
                    "Explicit input bypassed ownership");
            ship.setPlayerUID(context.capa().getPlayerUID());
            Vec3 originalPos = ship.position();
            ship.moveTo(originalPos.x, originalPos.y + 65D, originalPos.z);
            invokePacketHandler(explicit, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 0,
                    "Explicit input bypassed pointer range");
            ship.moveTo(originalPos.x, originalPos.y, originalPos.z);
            context.player().setShiftKeyDown(true);
            invokePacketHandler(legacy, "handleAddTeam", context.player());
            helper.assertTrue(context.capa().getTeamMember(TEAM_ID, 0) == 1020200,
                    "Legacy packet no longer works with sneak");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void everyModeChangePreservesSelectionAndGuard(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "left_mode", 45)) {
            BasicEntityShip ship = addShip(context, 0, 4500, new Vec3(4.5D, 2D, 1.5D));
            addShip(context, 1, 4501, new Vec3(5.5D, 2D, 1.5D));
            BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 4));
            select(context.capa(), 0);
            invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                    new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 1,
                            destination.getX(), destination.getY(), destination.getZ(), 0}),
                    "handleSetMove", context.player());
            for (int oldMode = 0; oldMode < 6; oldMode++) {
                for (int newMode = 0; newMode < 6; newMode++) {
                    PointerItem.setMode(context.player().getMainHandItem(), oldMode);
                    select(context.capa(), 0, 1);
                    invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SyncPlayerItem,
                            new int[]{context.player().getId(), 0, newMode}),
                            "handleSyncPlayerItem", context.player());
                    helper.assertTrue(context.capa().isShipSelected(TEAM_ID, 0)
                                    && context.capa().isShipSelected(TEAM_ID, 1)
                                    && PointerItem.getMode(context.player().getMainHandItem()) == newMode,
                            "Mode selection mismatch: " + oldMode + " -> " + newMode);
                    assertGuardDestination(helper, ship, destination, "mode-switch guard");
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void leftOnOtherEntitySendsNoCommand(GameTestHelper helper) {
        try (TestContext context = createContext(helper, "left_other", 46)) {
            Zombie target = addTarget(context, new Vec3(2.5D, 2D, 4.5D));
            for (boolean shift : new boolean[]{false, true}) {
                context.player().setShiftKeyDown(shift);
                invokeLeftClick(context, target, packet -> {
                    throw new AssertionError("Left click on another entity emitted a command");
                });
            }
            helper.succeed();
        }
    }

    private static void clickPointer(TestContext context, net.minecraft.world.entity.Entity target,
                                     boolean shift) {
        context.player().setShiftKeyDown(shift);
        invokeLeftClick(context, target, packet -> {
            try {
                var field = C2SGUIInputPacket.class.getDeclaredField("type");
                field.setAccessible(true);
                String handler = switch (field.getByte(packet)) {
                    case C2SGUIInputPacket.AddTeam -> "handleAddTeam";
                    case C2SGUIInputPacket.SetSelect -> "handleSetSelect";
                    default -> throw new AssertionError("Unexpected left-click packet");
                };
                invokePacketHandler(packet, handler, context.player());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Cannot dispatch pointer packet", e);
            }
        });
    }

    private static void invokeLeftClick(TestContext context, net.minecraft.world.entity.Entity target,
                                       java.util.function.Consumer<C2SGUIInputPacket> sendPacket) {
        try {
            Method method = PointerItem.class.getDeclaredMethod("handleLeftClick", ItemStack.class,
                    net.minecraft.world.entity.player.Player.class,
                    net.minecraft.world.phys.EntityHitResult.class, java.util.function.Consumer.class);
            method.setAccessible(true);
            method.invoke(ModItems.POINTER.get(), context.player().getMainHandItem(), context.player(),
                    new net.minecraft.world.phys.EntityHitResult(target), sendPacket);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot execute pointer left-click input", e);
        }
    }

    private static TestContext createContext(GameTestHelper helper, String name, int id) {
        ServerLevel level = helper.getLevel();
        UUID uuid = UUID.fromString(String.format("6b00b41e-2c24-45a1-9d20-%012d", id));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(uuid, "pointer_" + name));
        Vec3 playerPos = helper.absoluteVec(new Vec3(2.5D, 2D, 1.5D));
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0F, 0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            throw new AssertionError("Pointer test player has no CapaTeitoku capability.");
        }
        capa.setPlayerUID(2000 + id);
        capa.setSelectTeam(TEAM_ID);
        capa.clearShipSelection(TEAM_ID);
        return new TestContext(helper, level, player, capa, GameTestEntities.open(helper));
    }

    private static BasicEntityShip addShip(TestContext context, int slot, int shipUid, Vec3 relativePos) {
        return addShip(context, slot, shipUid, relativePos, true);
    }

    private static BasicEntityShip addShip(TestContext context, int slot, int shipUid, Vec3 pos,
                                           boolean relativePos) {
        Vec3 worldPos = relativePos ? context.helper().absoluteVec(pos) : pos;
        BasicEntityShip ship = context.entities().add(ModEntities.BB_KONGOU.get().create(context.level()));
        if (ship == null) {
            throw new AssertionError("Failed to create ship for pointer mode test.");
        }
        ship.setNoAi(true);
        ship.setPlayerUID(context.capa().getPlayerUID());
        ship.setShipUID(shipUid);
        ship.moveTo(worldPos.x, worldPos.y, worldPos.z, 0F, 0F);
        if (!context.level().addFreshEntity(ship)) {
            throw new AssertionError("Failed to add ship for pointer mode test.");
        }
        context.capa().setTeamMember(TEAM_ID, slot, shipUid);
        context.capa().setTeamSID(TEAM_ID, slot, ship.getId());
        return ship;
    }

    private static Zombie addTarget(TestContext context, Vec3 relativePos) {
        Zombie target = context.entities().add(EntityType.ZOMBIE.create(context.level()));
        if (target == null) {
            throw new AssertionError("Failed to create target for pointer mode test.");
        }
        Vec3 pos = context.helper().absoluteVec(relativePos);
        target.setNoAi(true);
        target.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        if (!context.level().addFreshEntity(target)) {
            throw new AssertionError("Failed to add target for pointer mode test.");
        }
        return target;
    }

    private static void select(CapaTeitoku capa, int... slots) {
        for (int slot : slots) {
            capa.setShipSelected(TEAM_ID, slot, true);
        }
    }

    private static void assertGuardDestination(GameTestHelper helper, BasicEntityShip ship,
                                               BlockPos expected, String description) {
        helper.assertTrue(ship.hasGuardDestination()
                        && ship.getGuardedPos(0) == expected.getX()
                        && ship.getGuardedPos(1) == expected.getY()
                        && ship.getGuardedPos(2) == expected.getZ(),
                "Move command did not reach " + description + ".");
    }

    private static void invokePacketHandler(C2SGUIInputPacket packet, String methodName, ServerPlayer player) {
        try {
            Method method = C2SGUIInputPacket.class.getDeclaredMethod(methodName, ServerPlayer.class);
            method.setAccessible(true);
            method.invoke(packet, player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke pointer packet handler: " + methodName, e);
        }
    }

    private record TestContext(GameTestHelper helper, ServerLevel level, ServerPlayer player, CapaTeitoku capa,
                               GameTestEntities entities) implements AutoCloseable {
        @Override
        public void close() {
            this.entities.close();
        }
    }
}
