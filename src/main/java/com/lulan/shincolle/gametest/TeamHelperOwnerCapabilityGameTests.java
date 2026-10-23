package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.TargetEligibilityEvaluator;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.observation.MinecraftTargetClassificationAdapter;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.AllySpellDamageGuard;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamHelperOwnerCapabilityGameTests {

    private TeamHelperOwnerCapabilityGameTests() {
    }

    /** A player whose capabilities were invalidated (death, removal) has no owner UID, not a crash. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void playerWithoutCapabilityHasNoOwnerUid(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = player(helper, entities);
            player.invalidateCaps();
            helper.assertTrue(!player.getCapability(CapaTeitokuProvider.CAPABILITY).isPresent(),
                    "Fixture must have no admiral capability");
            helper.assertTrue(TeamHelper.getPlayerUID(player) == 0, "Capability-less player must have owner UID 0");
            helper.succeed();
        }
    }

    /** An ownable entity whose owner player lost its capabilities is not owned by any admiral. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownableEntityOfPlayerWithoutCapabilityHasNoOwnerUid(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = player(helper, entities);
            Wolf wolf = ownedWolf(helper, entities, player);

            player.invalidateCaps();
            helper.assertTrue(!player.getCapability(CapaTeitokuProvider.CAPABILITY).isPresent(),
                    "Fixture must have no admiral capability");
            helper.assertTrue(TeamHelper.getPlayerUID(wolf) == 0,
                    "Wolf of a capability-less player must have owner UID 0");
            helper.succeed();
        }
    }

    /** The owner UID of a player and of its pet survives the loss of the admiral capability. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void registeredOwnerUidSurvivesCapabilityLoss(GameTestHelper helper) {
        int uid = 0;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = player(helper, entities);
            uid = register(helper, player);
            Wolf wolf = ownedWolf(helper, entities, player);
            helper.assertTrue(TeamHelper.getPlayerUID(player) == uid && TeamHelper.getPlayerUID(wolf) == uid,
                    "UID must resolve through the capability while it exists");

            player.invalidateCaps();
            helper.assertTrue(!player.getCapability(CapaTeitokuProvider.CAPABILITY).isPresent(),
                    "Fixture must have no admiral capability");
            helper.assertTrue(TeamHelper.getPlayerUID(player) == uid,
                    "Registered player must keep its UID without the capability");
            helper.assertTrue(TeamHelper.getPlayerUID(wolf) == uid,
                    "Wolf of a registered player must keep the owner UID without the capability");
            helper.succeed();
        } finally {
            unregister(uid);
        }
    }

    /** A player that was never registered has no UID to fall back on, in either branch. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unregisteredPlayerHasNoFallbackUid(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = player(helper, entities);
            Wolf wolf = ownedWolf(helper, entities, player);
            player.invalidateCaps();
            helper.assertTrue(TeamHelper.getPlayerUID(player) == 0, "Unregistered player must have UID 0");
            helper.assertTrue(TeamHelper.getPlayerUID(wolf) == 0, "Wolf of an unregistered player must have UID 0");
            helper.succeed();
        }
    }

    /** Registering a player whose capability is gone neither throws nor invents a UID. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void updatePlayerIdWithoutCapabilityIsHarmless(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = player(helper, entities);
            player.invalidateCaps();
            ServerDataManager.updatePlayerID(player);
            helper.assertTrue(TeamHelper.getPlayerUID(player) == 0, "No UID may be invented for a capability-less player");
            helper.succeed();
        }
    }

    /** While the capability exists the UID comes from it, not from the remembered one. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void capabilityUidWinsWhilePresent(GameTestHelper helper) {
        int uid = 0;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = player(helper, entities);
            uid = register(helper, player);
            Wolf wolf = ownedWolf(helper, entities, player);
            capability(player).setPlayerUID(uid + 1);
            helper.assertTrue(TeamHelper.getPlayerUID(player) == uid + 1 && TeamHelper.getPlayerUID(wolf) == uid + 1,
                    "Present capability must take precedence over the remembered UID");
            helper.succeed();
        } finally {
            unregister(uid);
        }
    }

    /** A pet registered as a custom target stays protected for its owner's ship after the owner lost the capability. */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_owner_capability_loss")
    public static void ownedWolfStaysProtectedAfterCapabilityLoss(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ownedWolfProtection(helper),
                new Vec3(1, 3, 1), new Vec3(2, 3, 1));
    }

    private static void ownedWolfProtection(GameTestHelper helper) {
        int uid = 0;
        HashMap<Integer, String> oldTargets = null;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer player = entities.add(new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "team_helper_owner")));
            player.moveTo(helper.absoluteVec(new Vec3(1, 3, 1)));
            helper.getLevel().addNewPlayer(player);
            GameTestEntities.assertRegistered(helper, player);
            uid = register(helper, player);
            oldTargets = ServerDataManager.getPlayerTargetClass(uid);
            Wolf wolf = ownedWolf(helper, entities, player);
            wolf.moveTo(helper.absoluteVec(new Vec3(2, 3, 1)));
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            ship.setNoAi(true);
            ship.setPlayerUID(uid);
            ship.setOwnerUUID(player.getUUID());
            ship.setStateFlag(ID.F.OnSightChase, false);
            ship.moveTo(helper.absoluteVec(new Vec3(1, 3, 1)));
            ServerDataManager.setPlayerTargetClass(uid, new HashMap<>());
            helper.assertTrue(ServerDataManager.setPlayerTargetClass(uid, "Wolf"), "Custom target fixture");
            TargetPredicatePolicy policy = new TargetPredicatePolicy(false, false, false, 0, false);

            var before = MinecraftTargetClassificationAdapter.classify(ship, wolf, TargetPredicateKind.FRIENDLY_AUTOMATIC,
                    policy, 1, TargetObservationProfiler.NOOP).classification().value();
            helper.assertTrue(before.relation().allied() && AllySpellDamageGuard.isAllied(wolf, ship)
                    && !TargetEligibilityEvaluator.test(TargetPredicateKind.FRIENDLY_AUTOMATIC, before, policy),
                    "Owned wolf must be protected while the capability exists");
            helper.assertTrue(before.traits().playerOwned(), "Owned wolf must be player-owned while the capability exists");

            player.invalidateCaps();
            helper.assertTrue(!player.getCapability(CapaTeitokuProvider.CAPABILITY).isPresent() && wolf.getOwner() == player,
                    "Fixture must have a resolvable owner without the capability");
            var after = MinecraftTargetClassificationAdapter.classify(ship, wolf, TargetPredicateKind.FRIENDLY_AUTOMATIC,
                    policy, 2, TargetObservationProfiler.NOOP).classification().value();
            helper.assertTrue(after.relation().allied() && AllySpellDamageGuard.isAllied(wolf, ship)
                    && !TargetEligibilityEvaluator.test(TargetPredicateKind.FRIENDLY_AUTOMATIC, after, policy),
                    "Owned wolf must stay protected after the capability is lost");
            helper.assertTrue(after.traits().playerOwned(), "Owned wolf must stay player-owned after the capability is lost");
            helper.succeed();
        } finally {
            if (uid > 0) {
                if (oldTargets == null) {
                    ServerDataManager.getAllPlayerTargetClassList().remove(uid);
                } else {
                    ServerDataManager.setPlayerTargetClass(uid, oldTargets);
                }
            }
            unregister(uid);
        }
    }

    private static int register(GameTestHelper helper, FakePlayer player) {
        ServerDataManager.updatePlayerID(player);
        int uid = capability(player).getPlayerUID();
        helper.assertTrue(uid > 0, "Registration must assign a UID");
        return uid;
    }

    private static CapaTeitoku capability(FakePlayer player) {
        return player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Player capability fixture"));
    }

    /** Drop the per-player cache entry a registration leaves in the shared server data. */
    private static void unregister(int uid) {
        if (uid > 0 && ServerDataManager.getAllPlayerWorldData() != null) {
            ServerDataManager.getAllPlayerWorldData().remove(uid);
        }
    }

    private static Wolf ownedWolf(GameTestHelper helper, GameTestEntities entities, FakePlayer player) {
        Wolf wolf = entities.add(EntityType.WOLF.create(helper.getLevel()));
        helper.assertTrue(wolf != null, "Wolf fixture");
        wolf.setTame(true);
        wolf.setOwnerUUID(player.getUUID());
        wolf.setNoAi(true);
        wolf.moveTo(helper.absoluteVec(new Vec3(2.5D, 3D, 2.5D)));
        helper.getLevel().addFreshEntity(wolf);
        helper.assertTrue(wolf.getOwner() == player, "Wolf must resolve the fake player as its owner");
        return wolf;
    }

    private static FakePlayer player(GameTestHelper helper, GameTestEntities entities) {
        FakePlayer player = entities.add(new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "team_helper_owner")));
        player.moveTo(helper.absoluteVec(new Vec3(0.5D, 3D, 0.5D)));
        helper.getLevel().addNewPlayer(player);
        GameTestEntities.assertRegistered(helper, player);
        return player;
    }
}
