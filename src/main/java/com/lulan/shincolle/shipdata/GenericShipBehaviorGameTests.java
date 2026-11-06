package com.lulan.shincolle.shipdata;

import com.google.gson.JsonObject;
import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipSpecialAttackGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.registry.GenericShipHost;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenericShipBehaviorGameTests {
    private static final ShipDefinitionId SAMPLE = ShipDefinitionId.parse("shincolle_test:sample_ship");
    private static final Vec3 POSITION = new Vec3(2, 4, 2);
    private static final Set<WeaponChannel> CANNONS = Set.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY);

    private GenericShipBehaviorGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void omittedBehaviorKeepsCurrentLoadout(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity ally = friendly(helper, entities, SAMPLE);
            GenericHostileShipEntity enemy = hostile(helper, entities, SAMPLE);
            rebuild(ally);
            rebuild(enemy);
            helper.assertTrue(weapons(ally).equals(Set.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY, WeaponChannel.MELEE)),
                    "Omitted friendly attacks changed");
            helper.assertTrue(weapons(enemy).equals(CANNONS), "Omitted hostile attacks changed");
            helper.assertTrue(goals(ally).equals(friendlyGoals()), "Friendly goal composition changed: " + goals(ally));
            helper.assertTrue(goals(enemy).equals(hostileGoals()), "Hostile goal composition changed: " + goals(enemy));
            for (Mob ship : List.of(ally, enemy)) {
                var targets = selector(ship, "targetSelector").getAvailableGoals();
                helper.assertTrue(targets.size() == 1 && targets.stream().allMatch(goal -> goal.getPriority() == 1
                        && goal.getGoal() instanceof com.lulan.shincolle.ai.ShipTargetAuthorityGoal),
                        "Default target authority composition changed");
            }
            GenericShipEntity second = friendly(helper, entities, SAMPLE);
            rebuild(second);
            for (var firstGoal : ally.behaviorGoals().getAvailableGoals()) {
                helper.assertTrue(second.behaviorGoals().getAvailableGoals().stream()
                        .noneMatch(other -> other.getGoal() == firstGoal.getGoal()), "Ships share a mutable goal");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void selectedChannelsExcludeOtherWeaponsAndGuiCannotAddMelee(GameTestHelper helper) {
        fixture(helper, entities -> {
            var registry = ShipDefinitionRegistry.server();
            var original = registry.all();
            try {
                for (String attack : List.of("light_cannon", "heavy_cannon", "melee")) {
                    var id = ShipDefinitionId.parse("shincolle_test:selected_" + attack);
                    publish(original, selected(id, attack));
                    for (GenericShipHost ship : List.of(friendly(helper, entities, id), hostile(helper, entities, id))) {
                        rebuild(ship.behaviorEntity());
                        WeaponChannel channel = attack.equals("melee") ? WeaponChannel.MELEE
                                : attack.equals("light_cannon") ? WeaponChannel.LIGHT : WeaponChannel.HEAVY;
                        helper.assertTrue(weapons(ship).equals(Set.of(channel)), "Selected weapons leaked: " + weapons(ship));
                        if (ship instanceof GenericShipEntity ally) ally.setStateFlag(ID.F.UseMelee, true);
                        helper.assertTrue(weapons(ship).equals(Set.of(channel)), "GUI flag expanded the loadout");
                        helper.assertTrue(ship.behaviorGoals().getAvailableGoals().stream()
                                .anyMatch(goal -> goal.getGoal() instanceof ShipAttackOnCollideGoal)
                                == (channel == WeaponChannel.MELEE), "Melee goal disagrees with selected attacks");
                    }
                }
            } finally {
                registry.reload(() -> original);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void builtinSkillShipsKeepSpecialAttackGoal(GameTestHelper helper) {
        fixture(helper, entities -> {
            for (Mob builtin : List.of(entities.add(ModEntities.CL_TENRYUU.get().create(helper.getLevel())),
                    entities.add(ModEntities.BB_NAGATO.get().create(helper.getLevel())),
                    entities.add(ModEntities.CL_TENRYUU_MOB.get().create(helper.getLevel())),
                    entities.add(ModEntities.BB_NAGATO_MOB.get().create(helper.getLevel())))) {
                rebuild(builtin);
                Map<String, Integer> expected = builtin instanceof BasicEntityShip ? new HashMap<>(friendlyGoals())
                        : new HashMap<>(hostileGoals());
                expected.put("0:ShipSpecialAttackGoal", 1);
                if (builtin instanceof com.lulan.shincolle.entity.cruiser.EntityCLTenryuu) {
                    expected.put("0:ShipSkillAttackGoal", 1);
                }
                helper.assertTrue(goals(builtin).equals(expected), "Built-in goal composition changed: " + goals(builtin));
                helper.assertTrue(weapons((com.lulan.shincolle.ai.ShipCombatHost) builtin).equals(
                                builtin instanceof BasicEntityShip
                                        ? Set.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY, WeaponChannel.MELEE) : CANNONS),
                        "Built-in weapons changed");
            }
            for (GenericShipHost generic : List.of(friendly(helper, entities, SAMPLE), hostile(helper, entities, SAMPLE))) {
                rebuild(generic.behaviorEntity());
                helper.assertTrue(generic.behaviorGoals().getAvailableGoals().stream()
                        .noneMatch(goal -> goal.getGoal() instanceof ShipSpecialAttackGoal), "Generic ship acquired a skill");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptyAttacksRejectionKeepsLiveShipDormant(GameTestHelper helper) {
        fixture(helper, entities -> {
            var registry = ShipDefinitionRegistry.server();
            var original = registry.all();
            var id = ShipDefinitionId.parse("shincolle_test:empty_attacks_live");
            ShipDefinition good = selected(id, "light_cannon");
            publish(original, good);
            try {
                List<Mob> live = List.of(friendly(helper, entities, id), hostile(helper, entities, id));
                for (Mob ship : live) {
                    rebuild(ship);
                    if (ship instanceof GenericShipEntity ally) {
                        ally.setShipLevel(37, false);
                        ally.getCapaShipInventory().setStackInSlot(0, new ItemStack(ModItems.EQUIP_CANNON.get()));
                        helper.assertTrue(ally.getLevel() == 37
                                && ally.getCapaShipInventory().getStackInSlot(0).is(ModItems.EQUIP_CANNON.get()),
                                "Saved level/inventory fixture was ignored");
                    }
                }
                JsonObject rejected = json();
                rejected.add("behavior", com.google.gson.JsonParser.parseString("{\"attacks\":[]}"));
                List<LogEvent> warnings = new ArrayList<>();
                var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ShipDefinitionLoader.class);
                var appender = new AbstractAppender("emptyAttackCapture", null, null, false,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override
                    public void append(LogEvent event) {
                        if (event.getMessage().getFormattedMessage().startsWith("Rejected ship definition " + id + ":")) {
                            warnings.add(event.toImmutable());
                        }
                    }
                };
                appender.start();
                logger.addAppender(appender);
                try {
                    new ShipDefinitionLoader().apply(Map.of(id.location(), rejected), null, null);
                } finally {
                    logger.removeAppender(appender);
                    appender.stop();
                }
                helper.assertTrue(warnings.size() == 1 && warnings.get(0).getLevel() == org.apache.logging.log4j.Level.WARN,
                        "Expected exactly one rejection WARN, got " + warnings.size());
                helper.assertTrue(registry.get(id) == null, "Loader retained the rejected definition");
                List<Mob> all = new ArrayList<>(live);
                for (Mob ship : live) {
                    ship.tick();
                    helper.assertTrue(((DefinitionBackedShip) ship).definitionState().dormant(), "Live ship did not sleep");
                    CompoundTag snapshot = ship.saveWithoutId(new CompoundTag());
                    Vec3 position = ship.position();
                    for (int tick = 0; tick < 4; tick++) ship.tick();
                    ship.setPos(position.x + 3, position.y, position.z);
                    helper.assertTrue(ship.position().equals(position), "Dormant ship moved");
                    Entity other = ship == live.get(0) ? live.get(1) : live.get(0);
                    helper.assertTrue(!((com.lulan.shincolle.entity.IShipCannonAttack) ship).attackEntityWithAmmo(other)
                            && !((com.lulan.shincolle.entity.IShipCannonAttack) ship).attackEntityWithHeavyAmmo(other)
                            && !ship.doHurtTarget(other), "Dormant ship attacked");
                    helper.assertTrue(snapshot.equals(ship.saveWithoutId(new CompoundTag())), "Live snapshot changed");
                    CompoundTag saved = new CompoundTag();
                    helper.assertTrue(ship.save(saved), "Dormant world save failed");
                    saved.putString("UnknownAddonState", "preserve verbatim");
                    Entity restored = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
                    helper.assertTrue(restored instanceof Mob, "Dormant world load failed");
                    entities.add(restored);
                    restored.tick();
                    CompoundTag again = new CompoundTag();
                    helper.assertTrue(restored.save(again) && saved.equals(again), "World load changed dormant NBT");
                    all.add((Mob) restored);
                }
                publish(original, good);
                for (Mob ship : all) {
                    ship.tick();
                    helper.assertTrue(!((DefinitionBackedShip) ship).definitionState().dormant(), "Ship did not resume");
                    Vec3 before = ship.position();
                    ship.teleportTo(before.x + 2, before.y, before.z);
                    helper.assertTrue(ship.getX() == before.x + 2, "Resumed ship remained frozen");
                }
                GenericShipEntity ally = (GenericShipEntity) live.get(0);
                helper.assertTrue(ally.getLevel() == 37
                        && ally.getCapaShipInventory().getStackInSlot(0).is(ModItems.EQUIP_CANNON.get()), "Individual state lost");
            } finally {
                registry.reload(() -> original);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 300,
            batch = "isolated_generic_light_only_fire")
    public static void lightCannonOnlyNeverFiresHeavy(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var registry = ShipDefinitionRegistry.server();
            var original = registry.all();
            var id = ShipDefinitionId.parse("shincolle_test:light_only_fire");
            publish(original, selected(id, "light_cannon"));
            GameTestEntities entities = GameTestEntities.open(helper);
            var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "generic_fire_owner"));
            try {
                helper.getLevel().addNewPlayer(owner);
                RecordingShip ship = entities.add(new RecordingShip(helper.getLevel()));
                ship.initialize(id);
                ship.setOwnerUUID(owner.getUUID());
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setStateFlag(ID.F.UseAmmoLight, true);
                ship.setStateFlag(ID.F.UseAmmoHeavy, true);
                ship.setStateFlag(ID.F.AtkType_Light, true);
                ship.setStateFlag(ID.F.AtkType_Heavy, true);
                ship.setAmmoLight(1_000);
                ship.setAmmoHeavy(1_000);
                ship.setInvulnerable(true);
                ship.setHealth(ship.getMaxHealth());
                ship.moveTo(helper.absoluteVec(POSITION));
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Attacker not added");
                GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof ShipWanderGoal);
                helper.runAfterDelay(24, () -> {
                    try {
                        owner.moveTo(ship.position());
                        Mob target = entities.add(EntityType.COW.create(helper.getLevel()));
                        target.setNoAi(true);
                        // Manual target validity rejects invulnerable mobs; recorded shots do no damage.
                        helper.assertTrue(!target.isInvulnerable(), "Manual target fixture is invulnerable");
                        target.setNoGravity(true);
                        target.moveTo(ship.getX() + 3, ship.getY(), ship.getZ());
                        helper.assertTrue(helper.getLevel().addFreshEntity(target), "Target not added");
                        helper.assertTrue(ship.tickCount >= 24 && ship.getStateMinor(ID.M.NumGrudge) > 0
                                && !ship.getStateFlag(ID.F.NoFuel), "Attacker fixture not ready");
                        helper.assertTrue(ship.getStateFlag(ID.F.UseAmmoHeavy) && ship.hasAmmoHeavy(), "Heavy ammo fixture invalid");
                        helper.assertTrue(ship.useAmmoLight() && ship.hasAmmoLight() && !ship.getIsSitting()
                                && ship.distanceToSqr(target) < ship.getAttrs().getAttackRange() * ship.getAttrs().getAttackRange()
                                && ship.getSensing().hasLineOfSight(target), "Light fire fixture is not ready/in range/on sight");
                        ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()),
                                new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(target))));
                        helper.assertTrue(ship.getManualTarget() == target, "Manual attack command was not applied");
                    } catch (Throwable failure) {
                        registry.reload(() -> original);
                        helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                        entities.close();
                        throw failure;
                    }
                });
                helper.runAfterDelay(180, () -> {
                    try {
                        helper.assertTrue(weapons(ship).equals(Set.of(WeaponChannel.LIGHT)), "Not LIGHT-only");
                        helper.assertTrue(ship.currentTargetLock().isPresent(), "Manual target lock did not survive");
                        helper.assertTrue(ship.light > 0, "No light shots: " + com.lulan.shincolle.ai.ShipCombatGate.plan(ship,
                                com.lulan.shincolle.ai.ShipCombatGate.engagement(ship)));
                        helper.assertTrue(ship.heavy == 0, "LIGHT-only ship fired heavy cannons");
                        helper.succeed();
                    } finally {
                        registry.reload(() -> original);
                        helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                        entities.close();
                    }
                });
            } catch (Throwable failure) {
                registry.reload(() -> original);
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                entities.close();
                throw failure;
            }
        }, POSITION, POSITION.add(3, 0, 0));
    }

    private static final class RecordingShip extends GenericShipEntity {
        private int light;
        private int heavy;

        private RecordingShip(Level level) {
            super(ModEntities.GENERIC_SHIP.get(), level);
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            light++;
            return true;
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            heavy++;
            return true;
        }
    }

    private static Set<WeaponChannel> weapons(com.lulan.shincolle.ai.ShipCombatHost host) {
        return java.util.Arrays.stream(WeaponChannel.values()).filter(host.shipCombatState().loadout()::has)
                .collect(Collectors.toSet());
    }

    private static Map<String, Integer> friendlyGoals() {
        return counts("1:ShipSitGoal", "2:ShipFleeGoal", "3:ShipCranePositionGoal", "3:ShipGuardingGoal",
                "4:ShipFollowOwnerGoal", "5:ShipOpenDoorGoal", "10:ShipFireControlGoal", "15:ShipAttackOnCollideGoal",
                "23:ShipFloatingGoal", "24:ShipWanderGoal", "25:ShipWatchClosestGoal", "26:RandomLookAroundGoal",
                "11:ShipRangeAttackGoal", "20:ShipPickItemGoal");
    }

    private static Map<String, Integer> hostileGoals() {
        return counts("0:ShipFloatingGoal", "10:ShipFireControlGoal", "21:ShipOpenDoorGoal", "23:ShipHostileWanderGoal",
                "11:ShipRangeAttackGoal", "25:ShipWatchClosestGoal", "26:RandomLookAroundGoal");
    }

    private static Map<String, Integer> counts(String... keys) {
        return java.util.Arrays.stream(keys).collect(Collectors.toMap(key -> key, key -> 1));
    }

    private static GoalSelector selector(Mob ship, String name) {
        try {
            Field field = Mob.class.getDeclaredField(name);
            field.setAccessible(true);
            return (GoalSelector) field.get(ship);
        } catch (ReflectiveOperationException exception) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Cannot inspect goals: " + exception);
        }
    }

    private static Map<String, Integer> goals(Mob ship) {
        Map<String, Integer> result = new HashMap<>();
        for (var wrapped : selector(ship, "goalSelector").getAvailableGoals()) {
            Class<?> type = wrapped.getGoal().getClass();
            if (type.isAnonymousClass()) type = type.getSuperclass();
            result.merge(wrapped.getPriority() + ":" + type.getSimpleName(), 1, Integer::sum);
        }
        return result;
    }

    private static void rebuild(Mob ship) {
        try {
            Class<?> base = ship instanceof BasicEntityShip ? BasicEntityShip.class : BasicEntityShipHostile.class;
            for (String name : List.of("clearAITasks", "clearAITargetTasks", "setAIList", "setAITargetList")) {
                Method method = base.getDeclaredMethod(name);
                method.setAccessible(true);
                method.invoke(ship);
            }
        } catch (ReflectiveOperationException exception) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Cannot rebuild goals: " + exception);
        }
    }

    private static JsonObject json() {
        return com.google.gson.JsonParser.parseString("""
                {"schema": 1, "ship_type": "shincolle_kai:destroyer",
                 "visual": "shincolle_test:sample_ship",
                 "base_stats": {"hp": 30, "atk": 10, "spd": 1, "mov": 0.2, "hit": 16}}
                """).getAsJsonObject();
    }

    private static ShipDefinition selected(ShipDefinitionId id, String attack) {
        JsonObject json = json();
        JsonObject behavior = new JsonObject();
        var attacks = new com.google.gson.JsonArray();
        attacks.add("shincolle_kai:" + attack);
        behavior.add("attacks", attacks);
        behavior.addProperty("ai", "shincolle_kai:standard");
        json.add("behavior", behavior);
        return new ShipDefinitionValidator().validate(id, json, warning -> { });
    }

    private static void publish(Map<ShipDefinitionId, ShipDefinition> original, ShipDefinition definition) {
        var next = new HashMap<>(original);
        next.put(definition.id(), definition);
        ShipDefinitionRegistry.server().reload(() -> next);
    }

    private static GenericShipEntity friendly(GameTestHelper helper, GameTestEntities entities, ShipDefinitionId id) {
        return place(helper, entities, (GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), id, false, null));
    }

    private static GenericHostileShipEntity hostile(GameTestHelper helper, GameTestEntities entities, ShipDefinitionId id) {
        return place(helper, entities, (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), id, true, null));
    }

    private static <T extends Mob> T place(GameTestHelper helper, GameTestEntities entities, T ship) {
        helper.assertTrue(ship != null, "Definition fixture did not spawn");
        entities.add(ship);
        ship.setInvulnerable(true);
        ship.moveTo(helper.absoluteVec(POSITION));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture not added");
        return ship;
    }

    private static void fixture(GameTestHelper helper, java.util.function.Consumer<GameTestEntities> check) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                check.accept(entities);
                helper.succeed();
            }
        }, POSITION);
    }
}
