package com.lulan.shincolle.shipdata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.registry.GenericShipHost;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenericShipReloadGameTests {
    private static final ShipDefinitionId IDENTITY = ShipDefinitionId.parse("shincolle_test:reload_behavior");
    private static final Vec3 POSITION = new Vec3(2, 4, 2);

    private GenericShipReloadGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_reload_locks", timeoutTicks = 200)
    public static void reloadChangesGenericShipAttacks(GameTestHelper helper) {
        asyncFixture(helper, scene -> {
            publish("light_cannon");
            GenericShipEntity ally = friendly(helper, scene.entities);
            GenericHostileShipEntity enemy = hostile(helper, scene.entities);
            GenericShipEntity sitting = friendly(helper, scene.entities);
            FakePlayer owner = scene.owner();
            ally.setOwnerUUID(owner.getUUID());
            sitting.setOwnerUUID(owner.getUUID());
            ally.setStateFlag(ID.F.UseAmmoLight, false);
            enemy.setStateFlag(ID.F.UseAmmoLight, false);
            ally.setStateFlag(ID.F.UseAmmoHeavy, false);
            enemy.setStateFlag(ID.F.UseAmmoHeavy, false);
            List<GenericShipHost> ships = List.of(ally, enemy, sitting);
            Map<GenericShipHost, Set<Goal>> old = new HashMap<>();
            Map<GenericShipHost, Set<Goal>> targetGoals = new HashMap<>();
            Map<GenericShipHost, Object> locks = new HashMap<>();
            Mob target = scene.entities.add(EntityType.COW.create(helper.getLevel()));
            target.setNoAi(true);
            target.setNoGravity(true);
            scene.after(24, () -> {
                owner.moveTo(ally.position());
                target.moveTo(ally.position().add(2, 0, 0));
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Reload target not added");
                enemy.moveTo(ally.position().add(0, 0, 1));
                command(ally, owner, new ShipCommand.Attack(ShipCommandStateAdapter.handle(target)));
                enemy.setEntityRevengeTarget(target);
                enemy.setEntityRevengeTime();
                command(sitting, owner, new ShipCommand.SetSitting(true));
            });
            scene.after(32, () -> {
                helper.assertTrue(target.isAlive() && !ally.getStateFlag(ID.F.UseAmmoLight) && !enemy.getStateFlag(ID.F.UseAmmoLight),
                        "Target-lock fixture must not damage its target");
                helper.assertTrue(ally.getEntityTarget() == target && enemy.getEntityTarget() == target,
                        "Target-lock fixture did not acquire both locks; ally=" + ally.getEntityTarget()
                                + "; enemy=" + enemy.getEntityTarget());
                helper.assertTrue(sitting.getIsSitting() && sitting.isInSittingPose(), "Sitting fixture ignored");
                locks.put(ally, ally.currentTargetLock());
                locks.put(enemy, enemy.currentTargetLock());
                for (GenericShipHost ship : ships) {
                    assertComposition(helper, ship, Set.of(WeaponChannel.LIGHT));
                    old.put(ship, instances(ship.behaviorGoals()));
                    targetGoals.put(ship, instances(targets(ship.behaviorEntity())));
                }
                publish();
            });
            scene.after(33, () -> {
                for (GenericShipHost ship : ships) {
                    assertComposition(helper, ship, defaults(ship));
                    assertReplaced(helper, old.get(ship), ship);
                    helper.assertTrue(instances(targets(ship.behaviorEntity())).equals(targetGoals.get(ship)),
                            "Reload replaced the target authority goal");
                }
                helper.assertTrue(ally.getEntityTarget() == target && enemy.getEntityTarget() == target
                        && ally.currentTargetLock().equals(locks.get(ally)) && enemy.currentTargetLock().equals(locks.get(enemy)),
                        "Reload changed the target or its lock");
                helper.assertTrue(sitting.getIsSitting() && sitting.isInSittingPose(), "Reload lost sitting");
                for (GenericShipHost ship : ships) old.put(ship, instances(ship.behaviorGoals()));
            });
            scene.after(34, GenericShipReloadGameTests::publish);
            scene.after(35, () -> {
                for (GenericShipHost ship : ships) {
                    assertReplaced(helper, old.get(ship), ship);
                    assertComposition(helper, ship, defaults(ship));
                }
                old.replaceAll((ship, ignored) -> instances(ship.behaviorGoals()));
            });
            scene.after(36, () -> new ShipDefinitionLoader().apply(Map.of(), null, null));
            scene.after(37, () -> {
                for (GenericShipHost ship : ships) {
                    helper.assertTrue(ship.definitionState().dormant(), "Missing definition did not sleep");
                    helper.assertTrue(instances(ship.behaviorGoals()).equals(old.get(ship)), "Dormancy rebuilt goals");
                }
            });
            scene.after(38, () -> publish("melee"));
            scene.after(39, () -> {
                for (GenericShipHost ship : ships) {
                    helper.assertTrue(!ship.definitionState().dormant(), "Corrected definition did not resume");
                    assertReplaced(helper, old.get(ship), ship);
                    assertComposition(helper, ship, Set.of(WeaponChannel.MELEE));
                }
                scene.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_reload")
    public static void reloadBeforeFirstInitAiDoesNotBuildGoals(GameTestHelper helper) {
        fixture(helper, entities -> {
            publish("light_cannon");
            List<GenericShipHost> ships = List.of(friendly(helper, entities), hostile(helper, entities));
            for (GenericShipHost ship : ships) {
                helper.assertTrue(ship.behaviorGoals().getAvailableGoals().isEmpty(), "New ship already has goals");
            }
            publish();
            for (GenericShipHost ship : ships) {
                advance(ship, 1);
                helper.assertTrue(ship.behaviorGoals().getAvailableGoals().isEmpty() && weapons(ship).isEmpty(),
                        "Reload installed goals before initial AI");
                advance(ship, 23);
                assertComposition(helper, ship, defaults(ship));
                helper.assertTrue(targets(ship.behaviorEntity()).getAvailableGoals().size() == 1, "Duplicate target authority");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_reload")
    public static void savedGenericShipRebuildsGoalsAfterLoad(GameTestHelper helper) {
        fixture(helper, entities -> {
            publish("melee");
            List<GenericShipHost> ships = List.of(friendly(helper, entities), hostile(helper, entities));
            for (GenericShipHost ship : ships) {
                advance(ship, 24);
                ((IShipAttackBase) ship).setStateFlag(ID.F.UseMelee, ship instanceof GenericShipEntity);
                CompoundTag saved = new CompoundTag();
                helper.assertTrue(ship.behaviorEntity().save(saved), "Ship save failed");
                ship.behaviorEntity().discard();
                Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
                helper.assertTrue(loaded instanceof GenericShipHost, "Ship load failed");
                entities.add(loaded);
                GenericShipHost restored = (GenericShipHost) loaded;
                helper.assertTrue(helper.getLevel().addFreshEntity(loaded), "Loaded ship not added");
                helper.assertTrue(restored.behaviorGoals().getAvailableGoals().isEmpty(), "Loaded ship built AI prematurely");
                advance(restored, 24);
                assertComposition(helper, restored, Set.of(WeaponChannel.MELEE));
                helper.assertTrue(((IShipAttackBase) restored).getStateFlag(ID.F.UseMelee), "Load did not restore melee policy");
                helper.assertTrue(targets(restored.behaviorEntity()).getAvailableGoals().size() == 1, "Loaded target duplicates");
                assertReplaced(helper, instances(ship.behaviorGoals()), restored);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_reload")
    public static void friendlyMeleeFlagUntouchedByDefinition(GameTestHelper helper) {
        fixture(helper, entities -> {
            publish();
            GenericShipEntity enabled = friendly(helper, entities);
            GenericShipEntity disabled = friendly(helper, entities);
            GenericHostileShipEntity enemy = hostile(helper, entities);
            enabled.setStateFlag(ID.F.UseMelee, true);
            disabled.setStateFlag(ID.F.UseMelee, false);
            for (GenericShipHost ship : List.of(enabled, disabled, enemy)) advance(ship, 24);
            for (String attack : List.of("light_cannon", "melee", "light_cannon")) {
                publish(attack);
                for (GenericShipHost ship : List.of(enabled, disabled, enemy)) advance(ship, 1);
                helper.assertTrue(enabled.getStateFlag(ID.F.UseMelee) && !disabled.getStateFlag(ID.F.UseMelee),
                        "Definition overwrote a friendly GUI flag");
                helper.assertTrue(enemy.getStateFlag(ID.F.UseMelee) == attack.equals("melee"), "Hostile flag ignored definition");
            }
            publish();
            advance(enemy, 1);
            helper.assertTrue(!enemy.getStateFlag(ID.F.UseMelee), "Omitted hostile behavior enabled melee");
            var builtin = entities.add(ModEntities.CL_TENRYUU_MOB.get().create(helper.getLevel()));
            builtin.setInvulnerable(true);
            builtin.moveTo(helper.absoluteVec(POSITION));
            helper.assertTrue(helper.getLevel().addFreshEntity(builtin), "Built-in enemy not added");
            for (int tick = 0; tick < 24; tick++) {
                builtin.tickCount++;
                builtin.tick();
            }
            publish("melee");
            builtin.tickCount++;
            builtin.tick();
            helper.assertTrue(!builtin.getStateFlag(ID.F.UseMelee), "Definition affected built-in hostile melee");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_reload_movement", timeoutTicks = 200)
    public static void reloadKeepsGuardAndFollowCommands(GameTestHelper helper) {
        asyncFixture(helper, scene -> {
            for (int x = -1; x <= 20; x++) {
                for (int z = 0; z <= 6; z++) {
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                    for (int y = 4; y <= 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            publish("light_cannon");
            GenericShipEntity follow = friendly(helper, scene.entities);
            GenericShipEntity guard = friendly(helper, scene.entities);
            FakePlayer owner = scene.owner();
            for (GenericShipEntity ship : List.of(follow, guard)) {
                ship.setOwnerUUID(owner.getUUID());
                ship.setNoGravity(false);
                GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof com.lulan.shincolle.ai.ShipWanderGoal);
            }
            Mob target = scene.entities.add(EntityType.COW.create(helper.getLevel()));
            target.setNoAi(true);
            Map<GenericShipHost, Set<Goal>> old = new HashMap<>();
            Map<GenericShipHost, Object> commands = new HashMap<>();
            Map<GenericShipHost, Vec3> positions = new HashMap<>();
            scene.after(24, () -> {
                for (GenericShipEntity ship : List.of(follow, guard)) {
                    helper.assertTrue(ship.onGround() && !ship.isNoGravity(), "Walking fixture did not reach the floor");
                    ship.setStateMinor(ID.M.FollowMin, 1);
                    ship.setStateMinor(ID.M.FollowMax, 2);
                    ship.setStateFlag(ID.F.PickItem, false);
                    helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == 1 && ship.getStateMinor(ID.M.FollowMax) == 2
                            && !ship.getStateFlag(ID.F.PickItem), "Movement range fixture ignored");
                }
                follow.moveTo(helper.absoluteVec(POSITION));
                guard.moveTo(helper.absoluteVec(POSITION.add(0, 0, 2)));
                owner.moveTo(helper.absoluteVec(POSITION.add(8, 0, 0)));
                target.moveTo(helper.absoluteVec(POSITION.add(8, 0, 2)));
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Guard target not added");
                command(follow, owner, new ShipCommand.Follow());
                command(guard, owner, new ShipCommand.GuardEntity(ShipCommandStateAdapter.handle(target)));
                helper.assertTrue(follow.getOwner() == owner && guard.getGuardedEntity() == target, "Movement target not projected");
                positions.put(follow, follow.position());
                positions.put(guard, guard.position());
            });
            scene.after(56, () -> {
                assertMoving(helper, follow, ShipFollowOwnerGoal.class);
                assertMoving(helper, guard, ShipGuardingGoal.class);
                for (GenericShipEntity ship : List.of(follow, guard)) {
                    helper.assertTrue(horizontalTravel(ship.position(), positions.get(ship)) > 0.01,
                            "Command did not move before reload; before=" + positions.get(ship) + "; now=" + ship.position());
                    commands.put(ship, ship.getCommandState());
                    old.put(ship, instances(ship.behaviorGoals()));
                    positions.put(ship, ship.position());
                }
                owner.moveTo(helper.absoluteVec(POSITION.add(12, 0, 0)));
                target.moveTo(helper.absoluteVec(POSITION.add(12, 0, 2)));
                publish();
            });
            scene.after(88, () -> {
                for (GenericShipEntity ship : List.of(follow, guard)) {
                    assertReplaced(helper, old.get(ship), ship);
                    helper.assertTrue(horizontalTravel(ship.position(), positions.get(ship)) > 0.01,
                            "Command did not move after reload; before=" + positions.get(ship) + "; now=" + ship.position());
                }
                helper.assertTrue(follow.getCommandState().equals(commands.get(follow)) && guard.getCommandState().equals(commands.get(guard))
                        && follow.getOwner() == owner && guard.getGuardedEntity() == target, "Reload lost movement commands");
                assertMoving(helper, follow, ShipFollowOwnerGoal.class);
                assertMoving(helper, guard, ShipGuardingGoal.class);
                scene.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_reload_melee", timeoutTicks = 250)
    public static void hostileMeleeDefinitionHitsTarget(GameTestHelper helper) {
        asyncFixture(helper, scene -> {
            publish("melee");
            RecordingHostile ship = place(helper, scene.entities, new RecordingHostile(helper.getLevel()));
            Mob target = scene.entities.add(EntityType.COW.create(helper.getLevel()));
            target.setNoAi(true);
            target.setNoGravity(true);
            GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof com.lulan.shincolle.ai.ShipHostileWanderGoal);
            scene.after(24, () -> {
                helper.assertTrue(ship.tickCount >= 24 && !ship.behaviorGoals().getAvailableGoals().isEmpty()
                        && ship.hasTargetAuthority(), "Initial AI was not installed");
                target.moveTo(ship.position().add(1, 0, 0));
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Melee target not added");
                ship.setEntityRevengeTarget(target);
                ship.setEntityRevengeTime();
                helper.assertTrue(ship.getEntityRevengeTarget() == target, "Revenge fixture ignored");
            });
            scene.after(120, () -> {
                helper.assertTrue(ship.getEntityTarget() == target, "Hostile target authority did not lock the target");
                helper.assertTrue(ship.hits > 0, "Selected hostile melee never hit; UseMelee=" + ship.getStateFlag(ID.F.UseMelee));
                helper.assertTrue(ship.getStateFlag(ID.F.UseMelee), "Selected hostile melee flag is false");
                helper.assertTrue(weapons(ship).equals(Set.of(WeaponChannel.MELEE)), "Hostile loadout is not melee-only");
                publish("light_cannon");
            });
            scene.after(121, () -> {
                helper.assertTrue(!ship.getStateFlag(ID.F.UseMelee), "Removing melee did not clear the hostile flag");
                helper.assertTrue(ship.behaviorGoals().getAvailableGoals().stream()
                        .noneMatch(goal -> goal.getGoal() instanceof ShipAttackOnCollideGoal), "Old melee goal survived reload");
                helper.assertTrue(weapons(ship).equals(Set.of(WeaponChannel.LIGHT)), "Reload did not replace hostile weapons");
                scene.succeed();
            });
        });
    }

    private static final class RecordingHostile extends GenericHostileShipEntity {
        private int hits;

        private RecordingHostile(Level level) {
            super(ModEntities.GENERIC_SHIP_HOSTILE.get(), level);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            hits++;
            return true;
        }
    }

    private static void advance(GenericShipHost host, int ticks) {
        Mob ship = host.behaviorEntity();
        Vec3 position = ship.position();
        for (int tick = 0; tick < ticks; tick++) {
            ship.tickCount++;
            ship.tick();
            ship.moveTo(position);
            ship.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static GenericShipEntity friendly(GameTestHelper helper, GameTestEntities entities) {
        return place(helper, entities, ModEntities.GENERIC_SHIP.get().create(helper.getLevel()));
    }

    private static GenericHostileShipEntity hostile(GameTestHelper helper, GameTestEntities entities) {
        return place(helper, entities, ModEntities.GENERIC_SHIP_HOSTILE.get().create(helper.getLevel()));
    }

    private static FakePlayer owner(GameTestHelper helper) {
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "reload_owner"));
        helper.getLevel().addNewPlayer(owner);
        return owner;
    }

    private static void command(GenericShipEntity ship, FakePlayer owner, ShipCommand command) {
        ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()), new CommandStateOp.Apply(command));
    }

    private static void assertMoving(GameTestHelper helper, GenericShipEntity ship, Class<? extends Goal> type) {
        helper.assertTrue(ship.behaviorGoals().getAvailableGoals().stream()
                .anyMatch(goal -> type.isInstance(goal.getGoal()) && goal.isRunning()),
                "Movement command did not continue through " + type.getSimpleName() + "; state=" + ship.getCommandState()
                        + "; running=" + ship.behaviorGoals().getRunningGoals().map(goal -> goal.getGoal().getClass().getSimpleName())
                        .toList() + "; path=" + ship.getNavigation().isInProgress() + "; tick=" + ship.tickCount
                        + "; alive=" + ship.isAlive() + "; fuel=" + ship.getStateMinor(ID.M.NumGrudge)
                        + "; noAi=" + ship.isNoAi() + "; intent=" + com.lulan.shincolle.ai.ShipMovementGate.decision(ship));
    }

    private static double horizontalTravel(Vec3 after, Vec3 before) {
        double dx = after.x - before.x;
        double dz = after.z - before.z;
        return dx * dx + dz * dz;
    }

    private static Set<WeaponChannel> defaults(GenericShipHost ship) {
        return ship instanceof GenericShipEntity ? Set.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY, WeaponChannel.MELEE)
                : Set.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY);
    }

    private static Set<Goal> instances(GoalSelector selector) {
        return selector.getAvailableGoals().stream().map(goal -> goal.getGoal()).collect(Collectors.toSet());
    }

    private static GoalSelector targets(Mob ship) {
        try {
            Field field = Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(ship);
        } catch (ReflectiveOperationException exception) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Cannot inspect targets: " + exception);
        }
    }

    private static void assertReplaced(GameTestHelper helper, Set<Goal> before, GenericShipHost ship) {
        helper.assertTrue(!before.isEmpty() && instances(ship.behaviorGoals()).stream().noneMatch(before::contains),
                "Old behavior goal instance survived reload/load");
    }

    private static void assertComposition(GameTestHelper helper, GenericShipHost ship, Set<WeaponChannel> expectedWeapons) {
        helper.assertTrue(weapons(ship).equals(expectedWeapons), "Wrong weapons after reload/load: " + weapons(ship));
        List<String> common = ship instanceof GenericShipEntity
                ? List.of("1:ShipSitGoal", "2:ShipFleeGoal", "3:ShipCranePositionGoal", "3:ShipGuardingGoal",
                        "4:ShipFollowOwnerGoal", "5:ShipOpenDoorGoal", "10:ShipFireControlGoal", "23:ShipFloatingGoal",
                        "24:ShipWanderGoal", "25:ShipWatchClosestGoal", "26:RandomLookAroundGoal", "20:ShipPickItemGoal")
                : List.of("0:ShipFloatingGoal", "10:ShipFireControlGoal", "21:ShipOpenDoorGoal", "23:ShipHostileWanderGoal",
                        "25:ShipWatchClosestGoal", "26:RandomLookAroundGoal");
        Map<String, Integer> expected = new HashMap<>();
        for (String key : common) expected.put(key, 1);
        if (expectedWeapons.contains(WeaponChannel.MELEE)) expected.put("15:ShipAttackOnCollideGoal", 1);
        if (expectedWeapons.contains(WeaponChannel.LIGHT) || expectedWeapons.contains(WeaponChannel.HEAVY)) {
            expected.put("11:ShipRangeAttackGoal", 1);
        }
        Map<String, Integer> actual = new HashMap<>();
        for (var wrapped : ship.behaviorGoals().getAvailableGoals()) {
            Class<?> type = wrapped.getGoal().getClass();
            if (type.isAnonymousClass()) type = type.getSuperclass();
            actual.merge(wrapped.getPriority() + ":" + type.getSimpleName(), 1, Integer::sum);
        }
        helper.assertTrue(actual.equals(expected), "Goal composition/duplicates after reload/load: " + actual);
    }

    private static Set<WeaponChannel> weapons(GenericShipHost ship) {
        return Arrays.stream(WeaponChannel.values()).filter(ship.shipCombatState().loadout()::has).collect(Collectors.toSet());
    }

    private static void publish(String... attacks) {
        JsonObject json = JsonParser.parseString("""
                {"schema": 1, "ship_type": "shincolle_kai:destroyer",
                 "visual": "shincolle_test:sample_ship",
                 "base_stats": {"hp": 30, "atk": 10, "spd": 1, "mov": 0.2, "hit": 16}}
                """).getAsJsonObject();
        if (attacks.length > 0) {
            JsonObject behavior = new JsonObject();
            var ids = new com.google.gson.JsonArray();
            for (String attack : attacks) ids.add("shincolle_kai:" + attack);
            behavior.add("attacks", ids);
            json.add("behavior", behavior);
        }
        new ShipDefinitionLoader().apply(Map.of(IDENTITY.location(), json), null, null);
    }

    private static <T extends Mob & GenericShipHost> T place(GameTestHelper helper, GameTestEntities entities, T ship) {
        entities.add(ship);
        if (ship instanceof GenericShipEntity ally) ally.initialize(IDENTITY);
        if (ship instanceof GenericHostileShipEntity enemy) enemy.initialize(IDENTITY);
        ship.setInvulnerable(true);
        ship.setNoGravity(true);
        ship.moveTo(helper.absoluteVec(POSITION));
        ((IShipAttackBase) ship).setStateMinor(ID.M.NumGrudge, 100_000);
        ((IShipAttackBase) ship).setStateFlag(ID.F.NoFuel, false);
        helper.assertTrue(((IShipAttackBase) ship).getStateMinor(ID.M.NumGrudge) > 0
                && !((IShipAttackBase) ship).getStateFlag(ID.F.NoFuel), "Fuel fixture ignored");
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship not added");
        return ship;
    }

    private static void fixture(GameTestHelper helper, java.util.function.Consumer<GameTestEntities> check) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var registry = ShipDefinitionRegistry.server();
            var original = registry.all();
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                check.accept(entities);
                helper.succeed();
            } finally {
                registry.reload(() -> original);
            }
        }, POSITION, POSITION.add(1, 0, 0));
    }

    private static void asyncFixture(GameTestHelper helper, java.util.function.Consumer<Scene> check) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper);
            try {
                check.accept(scene);
            } catch (Throwable failure) {
                scene.close();
                throw failure;
            }
        }, POSITION, POSITION.add(-3, 0, -2), POSITION.add(18, 0, 4));
    }

    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final Map<ShipDefinitionId, ShipDefinition> original;
        private final List<FakePlayer> owners = new ArrayList<>();
        private boolean closed;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.original = ShipDefinitionRegistry.server().all();
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo");
                field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                    @Override
                    public void testStructureLoaded(GameTestInfo info) { }

                    @Override
                    public void testPassed(GameTestInfo info) { close(); }

                    @Override
                    public void testFailed(GameTestInfo info) { close(); }
                });
            } catch (ReflectiveOperationException exception) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("Cannot register fixture cleanup: " + exception);
            }
        }

        private FakePlayer owner() {
            FakePlayer owner = GenericShipReloadGameTests.owner(helper);
            owner.setNoGravity(true);
            owners.add(owner);
            return owner;
        }

        private void after(int ticks, Runnable check) {
            helper.runAfterDelay(ticks, () -> {
                if (closed) return;
                try {
                    check.run();
                } catch (Throwable failure) {
                    close();
                    throw failure;
                }
            });
        }

        private void succeed() {
            close();
            helper.succeed();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            entities.close();
            for (FakePlayer owner : owners) helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            ShipDefinitionRegistry.server().reload(() -> original);
        }
    }
}
