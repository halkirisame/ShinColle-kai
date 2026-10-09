package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Under NEW, a following ship attacking a target outside its follow circle stops short of the
 * circle's edge instead of walking out and being pulled back by the follow goal.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCombatRegionGameTests {
    private static final int LOCK = 10;
    private static final int WATCH = 30;
    private static final int END = 190;
    private static final int FOLLOW_MAX = 4;
    private static final double EDGE_MARGIN = 2D;
    private static final double EDGE_ARRIVAL = 1.5D;

    private ShipCombatRegionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_region_follow",
            timeoutTicks = END + 20)
    public static void followingShipHoldsAtRegionEdge(GameTestHelper helper) {
        ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
        GameTestEntities entities;
        try {
            entities = GameTestEntities.open(helper);
        } catch (RuntimeException | Error failure) {
            authority.close();
            throw failure;
        }
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "combat_region_owner"));
        boolean[] ownerAdded = {false};
        Runnable close = () -> {
            try {
                if (ownerAdded[0]) {
                    helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                    ownerAdded[0] = false;
                }
                entities.close();
            } finally {
                authority.close();
            }
        };
        try {
            owner.moveTo(helper.absoluteVec(new Vec3(2.5D, 0D, 2.5D)));
            helper.getLevel().addNewPlayer(owner);
            ownerAdded[0] = true;

            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "failed to create ship");
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.CraneState, 0);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.setStateMinor(ID.M.FollowMin, 1);
            ship.setStateMinor(ID.M.FollowMax, FOLLOW_MAX);
            ship.calcShipAttributes(31, false);
            // raising the attributes leaves the old health, low enough to flee
            ship.setHealth(ship.getMaxHealth());
            // with melee on, the cannon goal never stops for being in range, so the ship always closes in
            ship.setStateFlag(ID.F.UseMelee, true);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            ship.setAmmoLight(500);
            ship.setOwnerUUID(owner.getUUID());
            ship.moveTo(helper.absoluteVec(new Vec3(0.5D, 0D, 2.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add ship");

            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            helper.assertTrue(cow != null, "failed to create cow");
            cow.setNoAi(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500D);
            cow.setHealth(500F);
            cow.moveTo(helper.absoluteVec(new Vec3(8.5D, 0D, 2.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "failed to add cow");

            double radius = FOLLOW_MAX + ship.getBbWidth() * 0.75D;
            double inner = radius - EDGE_MARGIN;
            boolean[] followRan = {false};
            double[] farthest = {0D};

            helper.runAtTickTime(LOCK, () -> step(close, () -> ship.applyCommandState(
                    new CommandIssuer.Player(owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(cow))))));
            helper.runAtTickTime(WATCH, () -> step(close, () -> {
                helper.assertTrue(ship.getEntityTarget() == cow,
                        "Fixture must lock the cow, target=" + ship.getEntityTarget());
                helper.assertTrue(ship.getHealth() >= ship.getMaxHealth() * 0.99F, "Fixture must be at full health");
            }));
            for (int tick = WATCH; tick < END; tick++) {
                helper.runAtTickTime(tick, () -> step(close, () -> {
                    WrappedGoal follow = follow(ship);
                    if (follow != null && follow.isRunning()) followRan[0] = true;
                    farthest[0] = Math.max(farthest[0], Math.sqrt(ship.distanceToSqr(owner)));
                }));
            }
            helper.runAtTickTime(END, () -> {
                try {
                    helper.assertTrue(follow(ship) != null, "Fixture must register the follow goal");
                    // how far past the owner the ship went toward the cow
                    double advance = ship.getX() - owner.getX();
                    helper.assertTrue(!followRan[0], "The follow goal pulled the ship back (farthest="
                            + farthest[0] + " radius=" + radius + ")");
                    helper.assertTrue(farthest[0] <= radius, "The ship left its follow circle: farthest="
                            + farthest[0] + " radius=" + radius);
                    helper.assertTrue(advance >= inner - EDGE_ARRIVAL - 0.5D,
                            "The ship did not advance to the edge: advance=" + advance + " inner=" + inner
                                    + " farthest=" + farthest[0]);
                    helper.succeed();
                } finally {
                    close.run();
                }
            });
        } catch (Throwable error) {
            close.run();
            throw error;
        }
    }

    private static WrappedGoal follow(BasicEntityShip ship) {
        return ship.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof ShipFollowOwnerGoal)
                .findFirst().orElse(null);
    }

    private static void step(Runnable close, Runnable body) {
        try {
            body.run();
        } catch (Throwable error) {
            close.run();
            throw error;
        }
    }
}
