package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipFleeGoal;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipSitGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * For every combination of order, sitting, flee, leash and crane, the movement goal that wins
 * under each authority. They agree except where NEW deliberately changed the behaviour: a
 * leashed guard stays put, and a low-health ship stays beside its owner.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipMovementIntentGameTests {
    private enum Winner { SIT, FLEE, GUARD, FOLLOW, NONE }

    private record Scenario(String name, Winner legacy, Winner modern, Consumer<Setup> setup) {
        Scenario(String name, Winner expected, Consumer<Setup> setup) {
            this(name, expected, expected, setup);
        }
    }

    private ShipMovementIntentGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_movement_intent_parity", timeoutTicks = 60)
    public static void newIntentPicksTheLegacyMovementGoal(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "movement_intent_owner"));
        boolean[] ownerAdded = {false};
        Runnable cleanup = () -> {
            if (ownerAdded[0]) {
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                ownerAdded[0] = false;
            }
            entities.close();
        };
        try {
            BasicEntityShip ship = ship(helper, entities);
            owner.moveTo(helper.absoluteVec(new Vec3(12.5D, 2D, 2.5D)));
            helper.getLevel().addNewPlayer(owner);
            ownerAdded[0] = true;
            ship.setOwnerUUID(owner.getUUID());
            helper.runAtTickTime(20, () -> {
                try {
                    List<String> failures = new ArrayList<>();
                    for (Scenario scenario : scenarios()) {
                        Setup setup = new Setup(helper, ship, owner);
                        setup.reset();
                        scenario.setup().accept(setup);
                        Winner legacy = winner(ship, ConfigHandler.ShipAiTargetAuthority.LEGACY);
                        Winner modern = winner(ship, ConfigHandler.ShipAiTargetAuthority.NEW);
                        if (legacy != scenario.legacy() || modern != scenario.modern()) {
                            failures.add(scenario.name() + ": expected legacy=" + scenario.legacy()
                                    + " new=" + scenario.modern() + ", got legacy=" + legacy + " new=" + modern);
                        }
                        setup.reset();
                    }
                    helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        } catch (Throwable error) {
            cleanup.run();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_movement_intent_pick_item", timeoutTicks = 60)
    public static void newPickItemPermissionMatchesLegacy(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            BasicEntityShip ship = ship(helper, entities);
            ItemEntity item = entities.add(new ItemEntity(helper.getLevel(), ship.getX(), ship.getY(), ship.getZ() + 1D,
                    new ItemStack(Items.STICK)));
            item.setNeverPickUp();
            helper.getLevel().addFreshEntity(item);
            helper.runAtTickTime(20, () -> {
                try {
                    helper.assertTrue(!ship.getStateFlag(ID.F.NoFuel), "Fixture must have fuel");
                    helper.assertTrue(ship.getCapaShipInventory().getFirstSlotForItem() >= 0,
                            "Fixture must have a free inventory slot");
                    List<String> failures = new ArrayList<>();
                    pickCase(failures, "enabled", ship, true, () -> ship.setStateFlag(ID.F.PickItem, true));
                    pickCase(failures, "disabled", ship, false, () -> ship.setStateFlag(ID.F.PickItem, false));
                    pickCase(failures, "crane", ship, false, () -> {
                        ship.setStateFlag(ID.F.PickItem, true);
                        ship.setStateTimer(ID.T.CrandDelay, 0);
                        ship.setStateMinor(ID.M.CraneState, 1);
                        helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 1, "Fixture must raise the crane");
                    });
                    pickCase(failures, "sitting", ship, false, () -> {
                        ship.setStateMinor(ID.M.CraneState, 0);
                        try (ShipAiAuthorityOverride ignored =
                                     ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
                            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
                        }
                    });
                    helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
                    helper.succeed();
                } finally {
                    entities.close();
                }
            });
        } catch (Throwable error) {
            entities.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_movement_intent_flag_toggle", timeoutTicks = 60)
    public static void newFlagTogglesKeepTheRegisteredGoals(GameTestHelper helper) {
        ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
        GameTestEntities entities = GameTestEntities.open(helper);
        Runnable cleanup = () -> {
            try {
                entities.close();
            } finally {
                authority.close();
            }
        };
        try {
            BasicEntityShip ship = ship(helper, entities);
            // the target authority only locks while the target goals tick
            ship.setNoAi(false);
            ship.setStateFlag(ID.F.UseMelee, false);
            ship.setStateFlag(ID.F.UseAmmoLight, false);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateFlag(ID.F.AtkType_Light, false);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.setNoAi(true);
            cow.moveTo(helper.absoluteVec(new Vec3(4.5D, 2D, 2.5D)));
            helper.getLevel().addFreshEntity(cow);
            helper.runAtTickTime(5, () -> ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(cow)))));
            helper.runAtTickTime(20, () -> {
                try {
                    helper.assertTrue(ship.getEntityTarget() == cow, "Fixture must lock the cow");
                    List<WrappedGoal> goals = List.copyOf(ship.goalSelector.getAvailableGoals());
                    List<WrappedGoal> targets = List.copyOf(ship.targetSelector.getAvailableGoals());
                    helper.assertTrue(!goals.isEmpty() && !targets.isEmpty(), "Fixture must register its goals");
                    Goal melee = goals.stream().map(WrappedGoal::getGoal)
                            .filter(goal -> goal instanceof ShipAttackOnCollideGoal).findFirst().orElse(null);
                    helper.assertTrue(melee != null, "NEW must register the melee goal even with melee off");

                    helper.assertTrue(!melee.canUse(), "Melee must not start while the flag is off");
                    ship.setStateFlag(ID.F.UseMelee, true);
                    helper.assertTrue(melee.canUse(), "Melee must start once the flag is on");
                    ship.setStateFlag(ID.F.UseMelee, false);
                    ship.setStateFlag(ID.F.PassiveAI, true);
                    ship.setStateFlag(ID.F.PassiveAI, false);

                    helper.assertTrue(goals.equals(List.copyOf(ship.goalSelector.getAvailableGoals())),
                            "Toggling melee must not re-register goals");
                    helper.assertTrue(targets.equals(List.copyOf(ship.targetSelector.getAvailableGoals())),
                            "Toggling passive must not re-register target goals");
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        } catch (Throwable error) {
            cleanup.run();
            throw error;
        }
    }

    private static void pickCase(List<String> failures, String name, BasicEntityShip ship, boolean expected,
                                 Runnable setup) {
        setup.run();
        boolean legacy = pick(ship, ConfigHandler.ShipAiTargetAuthority.LEGACY);
        boolean modern = pick(ship, ConfigHandler.ShipAiTargetAuthority.NEW);
        if (legacy != expected || modern != expected) {
            failures.add(name + ": expected=" + expected + " legacy=" + legacy + " new=" + modern);
        }
    }

    private static boolean pick(BasicEntityShip ship, ConfigHandler.ShipAiTargetAuthority mode) {
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode)) {
            return new ShipPickItemGoal(ship, 4F).canUse();
        }
    }

    private static List<Scenario> scenarios() {
        return List.of(
                new Scenario("follow", Winner.FOLLOW, s -> { }),
                new Scenario("follow leashed", Winner.NONE, Setup::leash),
                new Scenario("follow crane", Winner.NONE, s -> s.crane()),
                new Scenario("follow no grudge", Winner.NONE, s -> s.ship.setStateMinor(ID.M.NumGrudge, 0)),
                new Scenario("guard", Winner.GUARD, Setup::guard),
                new Scenario("guard leashed", Winner.GUARD, Winner.NONE, s -> { s.guard(); s.leash(); }),
                new Scenario("guard crane", Winner.NONE, s -> { s.guard(); s.crane(); }),
                new Scenario("sit", Winner.SIT, Setup::sit),
                new Scenario("sit while guarding", Winner.SIT, s -> { s.guard(); s.sit(); }),
                new Scenario("flee from follow", Winner.FLEE, Setup::lowHealth),
                new Scenario("flee from guard", Winner.FLEE, s -> { s.guard(); s.lowHealth(); }),
                new Scenario("flee ignores crane", Winner.FLEE, s -> { s.lowHealth(); s.crane(); }),
                new Scenario("flee not while leashed", Winner.GUARD, Winner.NONE, s -> { s.guard(); s.lowHealth(); s.leash(); }),
                new Scenario("low health beside owner while guarding", Winner.GUARD, Winner.NONE, s -> {
                    s.guard();
                    s.lowHealth();
                    s.owner.moveTo(s.ship.getX() + 1D, s.ship.getY(), s.ship.getZ());
                }),
                new Scenario("low health 3 blocks from owner", Winner.FLEE, Winner.NONE, s -> {
                    s.lowHealth();
                    s.owner.moveTo(s.ship.getX() + 3D, s.ship.getY(), s.ship.getZ());
                }),
                new Scenario("sit beats flee", Winner.SIT, s -> { s.lowHealth(); s.sit(); }));
    }

    private static Winner winner(BasicEntityShip ship, ConfigHandler.ShipAiTargetAuthority mode) {
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode)) {
            if (new ShipSitGoal(ship).canUse()) return Winner.SIT;
            if (new ShipFleeGoal(ship).canUse()) return Winner.FLEE;
            if (new ShipGuardingGoal(ship).canUse()) return Winner.GUARD;
            if (new ShipFollowOwnerGoal(ship).canUse()) return Winner.FOLLOW;
            return Winner.NONE;
        }
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create a friendly ship");
        ship.setNoAi(true);
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.FormatType, 0);
        ship.setStateMinor(ID.M.FollowMin, 1);
        ship.setStateMinor(ID.M.FollowMax, 2);
        ship.setStateMinor(ID.M.FleeHP, 35);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.calcShipAttributes(31, false);
        ship.moveTo(helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D)));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add a friendly ship");
        return ship;
    }

    /** One scenario's state; {@link #reset()} returns the ship to a healthy follower. */
    private static final class Setup {
        private final GameTestHelper helper;
        private final BasicEntityShip ship;
        private final FakePlayer owner;

        Setup(GameTestHelper helper, BasicEntityShip ship, FakePlayer owner) {
            this.helper = helper;
            this.ship = ship;
            this.owner = owner;
        }

        void reset() {
            this.ship.dropLeash(false, false);
            command(new ShipCommand.SetSitting(false));
            command(new ShipCommand.Follow());
            this.ship.setEntitySit(false);
            this.ship.setStateMinor(ID.M.CraneState, 0);
            this.ship.setStateMinor(ID.M.NumGrudge, 100_000);
            this.ship.setStateFlag(ID.F.NoFuel, false);
            this.ship.setHealth(this.ship.getMaxHealth());
            this.owner.moveTo(this.helper.absoluteVec(new Vec3(12.5D, 2D, 2.5D)));
        }

        void guard() {
            BlockPos pos = this.helper.absolutePos(new BlockPos(2, 2, 12));
            command(new ShipCommand.GuardPosition(dimension(), new CommandPos(pos.getX(), pos.getY(), pos.getZ()),
                    false));
        }

        void sit() {
            command(new ShipCommand.SetSitting(true));
        }

        void leash() {
            this.ship.setLeashedTo(this.owner, false);
        }

        void crane() {
            // setting the crane state is ignored for 20 ticks after the previous set
            this.ship.setStateTimer(ID.T.CrandDelay, 0);
            this.ship.setStateMinor(ID.M.CraneState, 1);
            this.helper.assertTrue(this.ship.getStateMinor(ID.M.CraneState) == 1, "Fixture must raise the crane");
        }

        void lowHealth() {
            this.ship.setHealth(this.ship.getMaxHealth() * 0.2F);
        }

        private void command(ShipCommand command) {
            try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
                this.ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                        new CommandStateOp.Apply(command));
            }
        }

        private DimensionKey dimension() {
            var id = this.helper.getLevel().dimension().location();
            return new DimensionKey(id.getNamespace(), id.getPath());
        }
    }
}
