package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipRevengeTargetGoal;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipRevengeGameTests {

    private ShipRevengeGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_revenge_edge_trigger_requires_a_new_tick")
    public static void revengeEdgeTriggerRequiresANewTick(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, null, 6201, new Vec3(1D, 4D, 1D));
            Zombie attacker = zombie(helper, entities, new Vec3(3D, 4D, 1D));
            ShipRevengeTargetGoal goal = new ShipRevengeTargetGoal(ship);

            queueRevenge(ship, attacker, 10);
            helper.assertTrue(goal.canUse(), "A new revenge tick did not trigger the goal");
            goal.start();
            ship.setEntityRevengeTarget(attacker);
            helper.assertTrue(!goal.canUse(), "The consumed revenge tick triggered twice");
            queueRevenge(ship, attacker, 11);
            helper.assertTrue(goal.canUse(), "A later revenge tick did not retrigger the goal");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_revenge_consumption_is_one_shot")
    public static void revengeConsumptionIsOneShot(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, null, 6202, new Vec3(1D, 4D, 1D));
            Zombie attacker = zombie(helper, entities, new Vec3(3D, 4D, 1D));
            ShipRevengeTargetGoal goal = new ShipRevengeTargetGoal(ship);

            queueRevenge(ship, attacker, 20);
            helper.assertTrue(goal.canUse(), "Revenge goal did not accept its queued target");
            goal.start();
            helper.assertTrue(ship.getEntityRevengeTarget() == null,
                    "Starting revenge did not consume the revenge target");
            ship.setEntityRevengeTarget(attacker);
            helper.assertTrue(!goal.canUse(), "Consumed revenge was reacquired without a new tick");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_revenge_expires_after_two_hundred_ticks")
    public static void revengeExpiresAfterTwoHundredTicks(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, null, 6203, new Vec3(1D, 4D, 1D));
            Zombie attacker = zombie(helper, entities, new Vec3(3D, 4D, 1D));

            queueRevenge(ship, attacker, 100);
            ship.tickCount = 300;
            TargetHelper.updateTarget(ship);
            helper.assertTrue(ship.getEntityRevengeTarget() == attacker,
                    "Revenge target expired at the 200-tick boundary");
            ship.tickCount = 301;
            TargetHelper.updateTarget(ship);
            helper.assertTrue(ship.getEntityRevengeTarget() == null,
                    "Revenge target remained after 201 ticks");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_revenge_range_is_fixed_at_construction")
    public static void revengeRangeIsFixedAtConstruction(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, null, 6204, new Vec3(1D, 4D, 1D));
            Zombie attacker = zombie(helper, entities, new Vec3(7D, 4D, 1D));
            ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 4F);
            ShipRevengeTargetGoal goal = new ShipRevengeTargetGoal(ship);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 20F);

            queueRevenge(ship, attacker, 30);
            helper.assertTrue(goal.canUse(), "Revenge goal did not accept its queued target");
            goal.start();
            helper.assertTrue(!goal.canContinueToUse(),
                    "Changing attack range after construction changed the revenge goal range");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_friendly_revenge_propagates_by_owner_and_range", timeoutTicks = 200)
    public static void friendlyRevengePropagatesByOwnerAndRange(GameTestHelper helper) {
        Vec3 origin = origin(helper);
        waitForChunks(helper, origin, origin.add(34D, 0D, 0D),
                () -> verifyFriendlyRevengePropagation(helper));
    }

    private static void verifyFriendlyRevengePropagation(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "revenge_owner", 6101,
                    "00000000-0000-0000-0000-000000006101");
            BasicEntityShip attacked = friendly(helper, entities, owner, 6101, new Vec3(1D, 4D, 1D));
            BasicEntityShip sameOwner = friendly(helper, entities, owner, 6101, new Vec3(3D, 4D, 1D));
            BasicEntityShip otherOwner = friendly(helper, entities, null, 6102, new Vec3(5D, 4D, 1D));
            BasicEntityShip outside = friendly(helper, entities, owner, 6101, new Vec3(35D, 4D, 1D));

            helper.assertTrue(attacked.hurt(attacked.damageSources().playerAttack(owner), 1F),
                    "Player attack did not enter the damage pipeline");
            helper.assertTrue(sameOwner.getEntityRevengeTarget() == attacked,
                    "Same-owner ship inside 32 blocks did not receive the attacked ship");
            helper.assertTrue(otherOwner.getEntityRevengeTarget() == null,
                    "Different-owner ship received friendly revenge propagation");
            helper.assertTrue(outside.getEntityRevengeTarget() == null,
                    "Same-owner ship outside 32 blocks received friendly revenge propagation");
            helper.assertTrue(attacked.getEntityRevengeTarget() == null,
                    "The attacked ship propagated revenge to itself");
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_hostile_revenge_propagates_unless_attacker_is_hostile")
    public static void hostileRevengePropagatesUnlessAttackerIsHostile(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShipHostile attacked = hostile(helper, entities, new Vec3(1D, 4D, 1D));
            BasicEntityShipHostile nearby = hostile(helper, entities, new Vec3(5D, 4D, 1D));
            BasicEntityShipHostile hostileAttacker = hostile(helper, entities, new Vec3(9D, 4D, 1D));
            Zombie attacker = zombie(helper, entities, new Vec3(3D, 4D, 1D));

            helper.assertTrue(attacked.hurt(attacked.damageSources().mobAttack(attacker), 1F),
                    "Non-ship attack did not enter the damage pipeline");
            helper.assertTrue(nearby.getEntityRevengeTarget() == attacker,
                    "Hostile ship inside 64 blocks did not receive the attacker");

            attacked.setEntityRevengeTarget(null);
            nearby.setEntityRevengeTarget(null);
            hostileAttacker.setEntityRevengeTarget(null);
            attacked.hurt(attacked.damageSources().mobAttack(hostileAttacker), 1F);
            helper.assertTrue(nearby.getEntityRevengeTarget() == null,
                    "Hostile attacker caused revenge propagation");
            helper.assertTrue(hostileAttacker.getEntityRevengeTarget() == null,
                    "Hostile attacker received its own propagation");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_passive_ships_receive_direct_and_propagated_revenge")
    public static void passiveShipsReceiveDirectAndPropagatedRevenge(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "passive_revenge_owner", 6301,
                    "00000000-0000-0000-0000-000000006301");
            BasicEntityShip direct = friendly(helper, entities, owner, 6301, new Vec3(1D, 4D, 1D));
            BasicEntityShip propagated = friendly(helper, entities, owner, 6301, new Vec3(3D, 4D, 1D));
            Zombie directAttacker = zombie(helper, entities, new Vec3(5D, 4D, 1D));
            Zombie playerTarget = zombie(helper, entities, new Vec3(7D, 4D, 1D));
            direct.setStateFlag(ID.F.PassiveAI, true);
            propagated.setStateFlag(ID.F.PassiveAI, true);
            direct.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);

            helper.assertTrue(direct.hurt(direct.damageSources().mobAttack(directAttacker), 1F),
                    "Passive ship attack did not enter the damage pipeline");
            helper.assertTrue(direct.getEntityRevengeTarget() == directAttacker,
                    "Passive ship did not retain its direct attacker");
            helper.assertTrue(playerTarget.hurt(playerTarget.damageSources().playerAttack(owner), 1F),
                    "Player attack did not enter the damage pipeline");
            helper.assertTrue(propagated.getEntityRevengeTarget() == playerTarget,
                    "Passive ship did not receive propagated revenge");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_projectile_direct_source_is_preserved_and_rejected")
    public static void projectileDirectSourceIsPreservedAndRejected(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "projectile_revenge_owner", 6401,
                    "00000000-0000-0000-0000-000000006401");
            BasicEntityShip ship = friendly(helper, entities, owner, 6401, new Vec3(3D, 4D, 1D));
            Arrow arrow = entities.add(EntityType.ARROW.create(helper.getLevel()));
            check(arrow != null, "Failed to create arrow");
            arrow.setOwner(owner);
            moveTo(helper, arrow, new Vec3(1D, 4D, 1D));
            check(helper.getLevel().addFreshEntity(arrow), "Failed to add arrow");

            DamageSource source = owner.damageSources().arrow(arrow, owner);
            helper.assertTrue(source.getDirectEntity() == arrow && source.getEntity() == owner,
                    "Arrow DamageSource did not preserve direct and causing entities");
            MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(owner, source, 1F));
            helper.assertTrue(ship.getEntityRevengeTarget() == arrow,
                    "Propagation did not retain the direct projectile: "
                            + describe(ship.getEntityRevengeTarget()));
            ShipRevengeTargetGoal goal = new ShipRevengeTargetGoal(ship);
            helper.assertTrue(!goal.canUse(), "Revenge goal accepted a projectile target");
            helper.succeed();
        }
    }

    private static ServerPlayer player(GameTestHelper helper, GameTestEntities entities,
                                       String name, int uid, String uuid) {
        ServerPlayer player = entities.add(FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)));
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new AssertionError("Test player has no admiral capability"));
        capa.setPlayerUID(uid);
        player.getAbilities().invulnerable = false;
        player.moveTo(origin(helper));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities,
                                            ServerPlayer owner, int uid, Vec3 relativePosition) {
        Entity entity = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        check(entity instanceof BasicEntityShip, "Failed to create friendly ship");
        BasicEntityShip ship = (BasicEntityShip) entity;
        ship.setNoAi(true);
        ship.setPlayerUID(uid);
        if (owner != null) {
            ship.setOwnerUUID(owner.getUUID());
        }
        moveTo(helper, ship, relativePosition);
        check(helper.getLevel().addFreshEntity(ship), "Failed to add friendly ship");
        return ship;
    }

    private static BasicEntityShipHostile hostile(GameTestHelper helper, GameTestEntities entities,
                                                   Vec3 relativePosition) {
        Entity entity = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        check(entity instanceof BasicEntityShipHostile, "Failed to create hostile ship");
        BasicEntityShipHostile ship = (BasicEntityShipHostile) entity;
        ship.setNoAi(true);
        moveTo(helper, ship, relativePosition);
        check(helper.getLevel().addFreshEntity(ship), "Failed to add hostile ship");
        return ship;
    }

    private static Zombie zombie(GameTestHelper helper, GameTestEntities entities, Vec3 relativePosition) {
        Zombie zombie = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
        check(zombie != null, "Failed to create zombie");
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        moveTo(helper, zombie, relativePosition);
        check(helper.getLevel().addFreshEntity(zombie), "Failed to add zombie");
        return zombie;
    }

    private static void queueRevenge(IShipAttackBase ship, Entity target, int tick) {
        ((Entity) ship).tickCount = tick;
        ship.setEntityRevengeTarget(target);
        ship.setEntityRevengeTime();
    }

    private static void waitForChunks(GameTestHelper helper, Vec3 first, Vec3 second, Runnable verification) {
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(first)),
                    "Waiting for the near revenge fixture chunk");
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(second)),
                    "Waiting for the far revenge fixture chunk");
        }).thenExecute(verification).thenSucceed();
    }

    private static Vec3 origin(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0.5D, 4D, 0.5D));
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relativePosition) {
        Vec3 position = helper.absoluteVec(relativePosition);
        entity.moveTo(position.x, position.y, position.z);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String describe(Entity entity) {
        return entity == null ? "null" : entity.getType().toShortString() + '/' + entity.getId();
    }
}
