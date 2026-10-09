package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.CombatMove;
import com.lulan.shincolle.ai.domain.movement.ConstraintSource;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementComposition;
import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementConstraints;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The movement gate's answers follow the ship's formation, follow distance and item pickup
 * settings: the region combat may move in, and whether picking items up is switched off.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementSettingsGameTests {
    private static final Vec3 SHIP = new Vec3(2.5D, 2D, 2.5D);
    private static final Vec3 TARGET = new Vec3(10.5D, 2D, 2.5D);

    private MovementSettingsGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void gateFollowsFormationFollowDistanceAndPickItemSettings(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verify(helper), SHIP, TARGET);
    }

    private static void verify(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "movement_settings_owner"));
        boolean ownerAdded = false;
        FormationGameTestTeam team = null;
        try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Failed to create a friendly ship");
            ship.setNoAi(true);
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateMinor(ID.M.FleeHP, 35);
            ship.setStateMinor(ID.M.FollowMin, 1);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.moveTo(helper.absoluteVec(SHIP));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add a friendly ship");

            owner.moveTo(ship.getX() + 1D, ship.getY(), ship.getZ());
            helper.getLevel().addNewPlayer(owner);
            ownerAdded = true;
            ship.setOwnerUUID(owner.getUUID());
            helper.assertTrue(ship.getOwner() == owner, "Fixture must resolve the owner");
            team = new FormationGameTestTeam(owner, ship, entities);

            Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
            helper.assertTrue(target != null, "Failed to create a target");
            target.setNoAi(true);
            target.moveTo(helper.absoluteVec(TARGET));
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Failed to add a target");

            ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Follow()));
            helper.assertTrue(ShipMovementGate.intent(ship) instanceof MovementIntent.FollowOwner,
                    "Fixture must follow, got " + ShipMovementGate.intent(ship));

            List<String> failures = new ArrayList<>();
            // the owner stands a block from the ship and the target seven from the owner
            region(helper, team, failures, "follow 4", ship, ship, owner, target, 0, 4, false, CombatMove.Reason.TO_REGION_EDGE);
            region(helper, team, failures, "follow 6", ship, ship, owner, target, 0, 6, false, CombatMove.Reason.TO_REGION_EDGE);
            region(helper, team, failures, "follow 4 picking", ship, ship, owner, target, 0, 4, true,
                    CombatMove.Reason.TOWARD_TARGET);
            region(helper, team, failures, "formation", ship, ship, owner, target, 1, 4, false,
                    CombatMove.Reason.NO_COMBAT_MOVEMENT);

            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            helper.assertTrue(mount != null, "Failed to create a mount");
            mount.setNoAi(true);
            mount.moveTo(ship.position());
            helper.assertTrue(helper.getLevel().addFreshEntity(mount), "Failed to add a mount");
            mount.setHost(ship);
            helper.assertTrue(ship.startRiding(mount, true), "Fixture must mount the ship");
            region(helper, team, failures, "mount follow 6", mount, ship, owner, target, 0, 6, false,
                    CombatMove.Reason.TO_REGION_EDGE);
            region(helper, team, failures, "mount formation", mount, ship, owner, target, 1, 4, false,
                    CombatMove.Reason.NO_COMBAT_MOVEMENT);

            helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
            helper.succeed();
        } catch (AssertionError error) {
            // an error from a sequence step would stop the server instead of failing this test
            throw new GameTestAssertException(String.valueOf(error.getMessage()));
        } finally {
            if (team != null) team.close();
            if (ownerAdded) {
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            }
            entities.close();
        }
    }

    /**
     * Sets the three settings on the ship and compares what the gate answers for {@code host} with the
     * region worked out from them here.
     */
    private static void region(GameTestHelper helper, FormationGameTestTeam team, List<String> failures, String name, Entity host,
                               BasicEntityShip ship, Entity owner, Entity target, int formatType, int followMax,
                               boolean pickItem, CombatMove.Reason reason) {
        team.setFormation(formatType);
        ship.setStateMinor(ID.M.FollowMax, followMax);
        ship.setStateFlag(ID.F.PickItem, pickItem);
        helper.assertTrue(ship.getStateMinor(ID.M.FormatType) == formatType
                        && ship.getStateMinor(ID.M.FollowMax) == followMax
                        && ship.getStateFlag(ID.F.PickItem) == pickItem,
                "Fixture must keep the settings it set: " + name);

        MovementConstraint constraint = formatType > 0
                ? new MovementConstraint.NoCombatMovement(ConstraintSource.FORMATION)
                : new MovementConstraint.Within(point(owner),
                        MovementConstraints.followRadius(followMax, host.getBbWidth(), pickItem),
                        ConstraintSource.FOLLOW_OWNER);
        CombatMove expected = MovementComposition.compose(constraint,
                new CombatMove.Request(point(host), point(target), false));
        CombatMove actual = ShipMovementGate.combatMove(host, target, false);
        if (expected.reason() != reason) {
            failures.add(name + ": the fixture no longer tells this setting apart, reason=" + expected.reason());
        }
        if (!expected.equals(actual)) {
            failures.add(name + ": region expected=" + expected + " actual=" + actual);
        }
        boolean pickOff = ShipMovementGate.decision(host).get(MovementActivity.PICK_ITEM).reasons()
                .contains(MovementInhibitReason.PICK_ITEM_OFF);
        if (pickOff == pickItem) {
            failures.add(name + ": pickItem=" + pickItem + " but the permission says off=" + pickOff);
        }
    }

    private static MovementPoint point(Entity entity) {
        return new MovementPoint(entity.getX(), entity.getY(), entity.getZ());
    }
}
