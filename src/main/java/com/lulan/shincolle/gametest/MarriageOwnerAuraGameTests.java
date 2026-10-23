package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Marriage-ring effects a ship periodically grants its admiral, checked through each concrete aiStep. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarriageOwnerAuraGameTests {
    private static final Vec3 SPAWN = new Vec3(1.5D, 2D, 1.5D);
    private static final int SHIP_UID = 7_420_001;
    /** Boundaries around every divisor used (30, 35, 45, 50) plus the ends of the level range. */
    private static final int[] LEVELS = {1, 29, 30, 31, 34, 35, 36, 44, 45, 46, 49, 50, 51, 75, 99, 100, 101, 149, 150};

    private MarriageOwnerAuraGameTests() {
    }

    /** One effect: the vanilla type, its amplifier and its duration as functions of the ship level. */
    private record Aura(MobEffect effect, IntUnaryOperator amplifier, IntUnaryOperator duration) {
    }

    private record Case(String name, RegistryObject<? extends EntityType<? extends BasicEntityShip>> type, Aura... auras) {
    }

    private static Aura aura(MobEffect effect, IntUnaryOperator amplifier, int base) {
        return new Aura(effect, amplifier, level -> base + level);
    }

    /** The ten ships whose kind or amplifier changes. */
    private static List<Case> changed() {
        return List.of(
                new Case("destroyer I", ModEntities.DESTROYER_I, aura(MobEffects.MOVEMENT_SPEED, l -> l / 45, 80)),
                new Case("destroyer Ro", ModEntities.DESTROYER_RO, aura(MobEffects.DIG_SPEED, l -> l / 30, 80)),
                new Case("destroyer Ha", ModEntities.DESTROYER_HA, aura(MobEffects.JUMP, l -> l / 45 + 1, 80)),
                new Case("destroyer Ni", ModEntities.DESTROYER_NI, aura(MobEffects.DAMAGE_BOOST, l -> l / 50, 80)),
                new Case("Akatsuki", ModEntities.DESTROYER_AKATSUKI, aura(MobEffects.DIG_SPEED, l -> l / 30, 80)),
                new Case("Hibiki", ModEntities.DESTROYER_HIBIKI, aura(MobEffects.JUMP, l -> l / 45 + 1, 80)),
                new Case("Ikazuchi", ModEntities.DESTROYER_IKAZUCHI, aura(MobEffects.DAMAGE_BOOST, l -> l / 50, 80)),
                new Case("Inazuma", ModEntities.DESTROYER_INAZUMA, aura(MobEffects.MOVEMENT_SPEED, l -> l / 45, 80)),
                new Case("destroyer Hime", ModEntities.DD_HIME,
                        aura(MobEffects.MOVEMENT_SPEED, l -> l / 45 + 1, 80), aura(MobEffects.DIG_SPEED, l -> l / 30, 80)),
                new Case("battleship Re", ModEntities.BB_RE, aura(MobEffects.DAMAGE_RESISTANCE, l -> l / 50, 50)));
    }

    /** The three ships that already matched and must keep their values. */
    private static List<Case> unchanged() {
        return List.of(
                new Case("Shimakaze", ModEntities.DESTROYER_SHIMAKAZE, aura(MobEffects.MOVEMENT_SPEED, l -> l / 35 + 1, 80)),
                new Case("Tenryuu", ModEntities.CL_TENRYUU, aura(MobEffects.NIGHT_VISION, l -> 0, 100)),
                new Case("Tatsuta", ModEntities.CL_TATSUTA, aura(MobEffects.NIGHT_VISION, l -> 0, 100)));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ownerAuraValuesMatchEveryShipFormula(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            helper.assertTrue(changed().size() == 10, "Expected ten changed ships");
            for (Case entry : changed()) {
                BasicEntityShip ship = scene.ship(entry, true);
                for (int level : LEVELS) {
                    scene.prepare(ship, level, true, true, 500, 0D);
                    scene.fire(ship);
                    scene.assertOwnerEffects(entry, level);
                    scene.assertNoNeighbourEffects(entry, ship);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void akatsukiGivesHasteWithoutAddingSpeed(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            BasicEntityShip ship = scene.ship(changed().get(4), true);
            scene.prepare(ship, 75, true, true, 500, 0D);
            scene.fire(ship);
            MobEffectInstance haste = scene.owner.getEffect(MobEffects.DIG_SPEED);
            helper.assertTrue(haste != null && haste.getAmplifier() == 2 && haste.getDuration() == 155,
                    "Expected Akatsuki haste a2/d155, found " + haste);
            helper.assertTrue(!scene.owner.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "Akatsuki still gives its admiral speed");
            helper.assertTrue(scene.owner.getActiveEffects().size() == 1, "Unexpected extra effects on the admiral");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerAuraRequiresMarriageRingAndPositiveFuel(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            List<Case> all = new ArrayList<>(changed());
            all.addAll(unchanged());
            for (Case entry : all) {
                BasicEntityShip ship = scene.ship(entry, true);
                boolean[][] variants = {{false, true, true}, {true, false, true}, {true, true, false}, {true, true, true}};
                for (boolean[] variant : variants) {
                    int grudge = variant[2] ? 500 : 0;
                    scene.prepare(ship, 75, variant[0], variant[1], grudge, 0D);
                    scene.fire(ship);
                    helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) == grudge,
                            "Grudge changed while the aura fired for " + entry.name());
                    if (variant[0] && variant[1] && variant[2]) {
                        scene.assertOwnerHasEffects(entry);
                    } else {
                        helper.assertTrue(scene.owner.getActiveEffects().isEmpty(),
                                entry.name() + " gave an effect without its marriage, ring or fuel condition");
                    }
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerAuraUsesSameLevelOwnerAndStrictDistance(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            Case entry = changed().get(0);
            BasicEntityShip ship = scene.ship(entry, true);
            double[] distances = {0D, 15.99D, 16D, 16.01D};
            boolean[] expected = {true, true, false, false};
            for (int i = 0; i < distances.length; i++) {
                scene.prepare(ship, 90, true, true, 500, distances[i]);
                scene.fire(ship);
                helper.assertTrue(scene.owner.getActiveEffects().isEmpty() != expected[i],
                        "Wrong result at distance " + distances[i]);
            }
            // another admiral nearby is not the owner
            scene.prepare(ship, 90, true, true, 500, 0D);
            ship.setOwnerUUID(UUID.randomUUID());
            scene.fire(ship);
            helper.assertTrue(scene.owner.getActiveEffects().isEmpty() && scene.other.getActiveEffects().isEmpty(),
                    "An unrelated admiral received the effect");
            // owner not online
            scene.prepare(ship, 90, true, true, 500, 0D);
            ServerPlayer detached = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "owner_aura_absent"));
            ship.setOwnerUUID(detached.getUUID());
            scene.fire(ship);
            helper.assertTrue(detached.getActiveEffects().isEmpty() && scene.owner.getActiveEffects().isEmpty(),
                    "An absent owner's effect leaked");
            // owner in another dimension
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            if (nether != null) {
                FakePlayer far = FakePlayerFactory.get(nether, new GameProfile(UUID.randomUUID(), "owner_aura_nether"));
                far.setPos(ship.position());
                nether.addNewPlayer(far);
                try {
                    scene.prepare(ship, 90, true, true, 500, 0D);
                    ship.setOwnerUUID(far.getUUID());
                    scene.fire(ship);
                    helper.assertTrue(far.getActiveEffects().isEmpty(), "Other-dimension admiral received the effect");
                } finally {
                    nether.removePlayerImmediately(far, Entity.RemovalReason.DISCARDED);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerAuraRefreshesOnlyEvery128Ticks(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            for (Case entry : changed()) {
                BasicEntityShip ship = scene.ship(entry, true);
                scene.prepare(ship, 1, true, true, 500, 0D);
                ship.tickCount = 127;
                ship.aiStep();
                helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), entry.name() + " fired at tick 127");
                ship.tickCount = 129;
                ship.aiStep();
                helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), entry.name() + " fired at tick 129");
                ship.tickCount = 128;
                ship.aiStep();
                // a level 1 ship's 81-tick effect ends before the next 128-tick refresh
                scene.assertOwnerHasEffects(entry);
                for (MobEffectInstance active : scene.owner.getActiveEffects()) {
                    helper.assertTrue(active.getDuration() < 128,
                            "Level 1 duration is shorter than the period for " + entry.name());
                }
                scene.owner.removeAllEffects();
                ship.setStateFlag(ID.F.UseRingEffect, false);
                helper.assertTrue(!ship.getStateFlag(ID.F.UseRingEffect), "Ring switch fixture was ignored");
                ship.tickCount = 128;
                ship.aiStep();
                helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), entry.name() + " refreshed while switched off");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void unchangedOwnerAuraShipsKeepTheirValues(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                for (Case entry : unchanged()) {
                    BasicEntityShip ship = scene.ship(entry, true);
                    for (int level : LEVELS) {
                        scene.prepare(ship, level, true, true, 500, 0D);
                        scene.fire(ship);
                        scene.assertOwnerEffects(entry, level);
                    }
                    scene.prepare(ship, 75, true, false, 500, 0D);
                    scene.fire(ship);
                    helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), entry.name() + " ignored the ring switch");
                    scene.prepare(ship, 75, true, true, 0, 0D);
                    scene.fire(ship);
                    helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), entry.name() + " ignored empty fuel");
                    scene.prepare(ship, 75, true, true, 500, 15.99D);
                    scene.fire(ship);
                    scene.assertOwnerEffects(entry, 75);
                    scene.prepare(ship, 75, true, true, 500, 16D);
                    scene.fire(ship);
                    helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), entry.name() + " reached exactly 16 blocks");

                    // world load and a saved egg keep the four inputs and the next period gives the same effect
                    scene.prepare(ship, 75, true, true, 500, 0D);
                    CompoundTag saved = ShipConsumptionDefaultsGameTests.savedFixture(ship, SHIP_UID);
                    BasicEntityShip loaded = scene.ship(entry, false);
                    loaded.readAdditionalSaveData(saved);
                    BasicEntityShip egg = ShipConsumptionDefaultsGameTests.hatch(helper, scene.entities, saved);
                    for (BasicEntityShip copy : List.of(loaded, egg)) {
                        helper.assertTrue(copy.getStateMinor(ID.M.ShipLevel) == 37 && copy.getStateMinor(ID.M.NumGrudge) == 503
                                        && copy.getStateFlag(ID.F.IsMarried) && copy.getStateFlag(ID.F.UseRingEffect),
                                "Saved inputs were not restored for " + entry.name());
                        copy.setGrudgeConsumption(0);
                        copy.setOwnerUUID(scene.owner.getUUID());
                        copy.setPos(scene.owner.position());
                        scene.owner.removeAllEffects();
                        scene.fire(copy);
                        scene.assertOwnerEffects(entry, 37);
                    }
                    egg.discard();
                    ServerDataManager.removeShipData(SHIP_UID);
                }
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(SHIP_UID);
            }
        }), SPAWN);
    }

    /** A freshly built admiral, a bystander and the ships under test, all removed on close. */
    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final FakePlayer owner;
        final FakePlayer other;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "owner_aura_admiral"));
            this.other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "owner_aura_bystander"));
            Vec3 origin = helper.absoluteVec(SPAWN);
            this.owner.setPos(origin);
            this.other.setPos(origin);
            helper.getLevel().addNewPlayer(this.owner);
            helper.getLevel().addNewPlayer(this.other);
        }

        BasicEntityShip ship(Case entry, boolean owned) {
            BasicEntityShip ship = this.entities.add(entry.type().get().create(this.helper.getLevel()));
            this.helper.assertTrue(ship != null, "Could not create " + entry.name());
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            ship.setPos(this.helper.absoluteVec(SPAWN));
            if (owned) {
                ship.setOwnerUUID(this.owner.getUUID());
                this.helper.assertTrue(ship.getOwner() == this.owner, "Owner fixture is unresolved for " + entry.name());
            }
            return ship;
        }

        /** Sets every input, reads it back, and moves the admiral to the given distance with no effects. */
        void prepare(BasicEntityShip ship, int level, boolean married, boolean ring, int grudge, double distance) {
            ship.setOwnerUUID(this.owner.getUUID());
            ship.setStateMinor(ID.M.ShipLevel, level);
            ship.setStateMinor(ID.M.NumGrudge, grudge);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.IsMarried, married);
            ship.setStateFlag(ID.F.UseRingEffect, ring);
            ship.setGrudgeConsumption(0);
            Vec3 at = this.helper.absoluteVec(SPAWN);
            ship.setPos(at);
            this.owner.setPos(at.add(distance, 0D, 0D));
            this.owner.removeAllEffects();
            this.other.removeAllEffects();
            ship.removeAllEffects();
            this.helper.assertTrue(ship.getOwner() == this.owner
                            && ship.getStateMinor(ID.M.ShipLevel) == level
                            && ship.getStateMinor(ID.M.NumGrudge) == grudge
                            && ship.getStateFlag(ID.F.IsMarried) == married
                            && ship.getStateFlag(ID.F.UseRingEffect) == ring
                            && !ship.getStateFlag(ID.F.NoFuel)
                            && ship.getGrudgeConsumption() == 0
                            && Math.abs(ship.distanceToSqr(this.owner) - distance * distance) < 1.0E-6D,
                    "A fixture value was ignored");
        }

        /** Runs the concrete aiStep once at the first tick of a period. */
        void fire(BasicEntityShip ship) {
            ship.tickCount = 128;
            ship.aiStep();
        }

        void assertOwnerEffects(Case entry, int level) {
            for (Aura aura : entry.auras()) {
                MobEffectInstance found = this.owner.getEffect(aura.effect());
                int amplifier = aura.amplifier().applyAsInt(level);
                int duration = aura.duration().applyAsInt(level);
                this.helper.assertTrue(found != null && found.getAmplifier() == amplifier && found.getDuration() == duration,
                        entry.name() + " Lv" + level + " expected " + aura.effect().getDescriptionId() + " a" + amplifier
                                + "/d" + duration + ", found "
                                + (found == null ? "none" : "a" + found.getAmplifier() + "/d" + found.getDuration()));
            }
            this.helper.assertTrue(this.owner.getActiveEffects().size() == entry.auras().length,
                    entry.name() + " Lv" + level + " gave " + this.owner.getActiveEffects().size() + " effect kinds");
        }

        /** The admiral received the expected number of effect kinds; which kind and strength is the formula test's subject. */
        void assertOwnerHasEffects(Case entry) {
            this.helper.assertTrue(this.owner.getActiveEffects().size() == entry.auras().length,
                    entry.name() + " gave " + this.owner.getActiveEffects().size() + " effect kinds");
        }

        void assertNoNeighbourEffects(Case entry, BasicEntityShip ship) {
            this.helper.assertTrue(this.other.getActiveEffects().isEmpty(),
                    entry.name() + " gave an effect to a player who is not its admiral");
            for (Aura aura : entry.auras()) {
                this.helper.assertTrue(!ship.hasEffect(aura.effect()), entry.name() + " gave itself an admiral effect");
            }
        }

        @Override
        public void close() {
            this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
            this.helper.getLevel().removePlayerImmediately(this.other, Entity.RemovalReason.DISCARDED);
            this.entities.close();
        }
    }
}
