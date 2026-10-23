package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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

import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Marriage-ring abilities that Ne, Ri, Heavy Cruiser Hime and Ru give themselves, checked through each concrete aiStep. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarriageSelfAuraGameTests {
    private static final Vec3 SPAWN = new Vec3(1.5D, 2D, 1.5D);
    private static final long NIGHT = 18000L;
    private static final long NOON = 6000L;
    private static final int DURATION = 150;
    /** Boundaries around every divisor used (40, 50, 60, 70, 140) plus the ends of the level range. */
    private static final int[] LEVELS = {1, 39, 40, 41, 49, 50, 51, 59, 60, 61, 69, 70, 71, 75, 100, 139, 140, 141, 150};

    private MarriageSelfAuraGameTests() {
    }

    /** One self effect: the vanilla type and its amplifier as a function of the ship level. All last 150 ticks. */
    private record Aura(MobEffect effect, IntUnaryOperator amplifier) {
    }

    /** {@code night} selects the time of day in which the ship gives itself the effects. */
    private record Case(String name, RegistryObject<? extends EntityType<? extends BasicEntityShip>> type,
                        boolean night, Aura... auras) {
        long time() {
            return this.night ? NIGHT : NOON;
        }
    }

    private static List<Case> nightShips() {
        return List.of(
                new Case("Ne", ModEntities.CA_NE, true,
                        new Aura(MobEffects.MOVEMENT_SPEED, l -> l / 70), new Aura(MobEffects.JUMP, l -> l / 60)),
                new Case("Ri", ModEntities.CA_RI, true,
                        new Aura(MobEffects.MOVEMENT_SPEED, l -> l / 70), new Aura(MobEffects.JUMP, l -> l / 60)),
                new Case("Heavy Cruiser Hime", ModEntities.CA_HIME, true,
                        new Aura(MobEffects.MOVEMENT_SPEED, l -> l / 50), new Aura(MobEffects.JUMP, l -> l / 40)));
    }

    private static Case ru() {
        return new Case("Ru", ModEntities.BB_RU, false, new Aura(MobEffects.LUCK, l -> l / 140));
    }

    private static List<Case> allShips() {
        return List.of(nightShips().get(0), nightShips().get(1), nightShips().get(2), ru());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void nightSelfAurasWorkUnmarriedWithoutFuelOrOwner(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            helper.assertTrue(nightShips().size() == 3, "Expected three night ships");
            for (Case entry : nightShips()) {
                scene.atTime(entry.time(), () -> {
                    BasicEntityShip ship = scene.ship(entry);
                    scene.prepare(ship, 120, false, true, 0, true);
                    helper.assertTrue(ship.getOwnerUUID() == null, "Owner fixture was ignored for " + entry.name());
                    scene.fire(ship);
                    scene.assertSelfEffects(entry, ship, 120);
                    helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) == 0, "Grudge changed for " + entry.name());
                    scene.assertNoBystanderEffects(entry);
                    ship.discard();

                    // a distant owner makes no difference, and the owner and a nearby ship get nothing
                    BasicEntityShip owned = scene.ship(entry);
                    BasicEntityShip neighbour = scene.ship(entry);
                    scene.prepare(owned, 120, false, true, 0, true);
                    scene.prepare(neighbour, 120, false, true, 0, true);
                    owned.setOwnerUUID(scene.owner.getUUID());
                    neighbour.setOwnerUUID(scene.owner.getUUID());
                    scene.owner.setPos(scene.at.add(200D, 0D, 0D));
                    helper.assertTrue(owned.getOwner() == scene.owner && owned.distanceToSqr(scene.owner) > 256D,
                            "Distant owner fixture was ignored for " + entry.name());
                    scene.fire(owned);
                    scene.assertSelfEffects(entry, owned, 120);
                    helper.assertTrue(neighbour.getActiveEffects().isEmpty(),
                            entry.name() + " gave a nearby ship an effect");
                    scene.assertNoBystanderEffects(entry);
                });
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void nightSelfAurasUseNotDayRatherThanNight(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The Nether is required to reach a level that is neither day nor night");
        helper.assertTrue(!nether.isDay() && !nether.isNight(),
                "The Nether must be neither day nor night for this fixture");
        try (Scene scene = new Scene(helper)) {
            for (Case entry : nightShips()) {
                scene.atTime(NIGHT, () -> {
                    helper.assertTrue(!helper.getLevel().isDay() && helper.getLevel().isNight(), "Night fixture was ignored");
                    BasicEntityShip ship = scene.ship(entry);
                    scene.prepare(ship, 120, true, true, 500, false);
                    scene.fire(ship);
                    scene.assertSelfEffects(entry, ship, 120);
                    scene.prepare(ship, 120, true, false, 500, false);
                    scene.fire(ship);
                    helper.assertTrue(ship.getActiveEffects().isEmpty(), entry.name() + " ignored the ring switch");
                });
                scene.atTime(NOON, () -> {
                    helper.assertTrue(helper.getLevel().isDay(), "Day fixture was ignored");
                    BasicEntityShip ship = scene.ship(entry);
                    scene.prepare(ship, 120, true, true, 500, false);
                    scene.fire(ship);
                    helper.assertTrue(ship.getActiveEffects().isEmpty(), entry.name() + " gave itself an effect by day");
                });
                // a fixed-time level is neither day nor night, so only "not day" gives the effects
                BasicEntityShip far = entry.type().get().create(nether);
                helper.assertTrue(far != null, "Could not create " + entry.name() + " in the Nether");
                far.setNoAi(true);
                far.setInvulnerable(true);
                scene.prepare(far, 120, true, true, 500, false);
                helper.assertTrue(far.level() == nether && !far.level().isDay() && !far.level().isNight(),
                        "Fixed-time fixture was ignored for " + entry.name());
                scene.fire(far);
                scene.assertSelfEffects(entry, far, 120);
                far.discard();
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void selfAuraValuesAndPeriodMatchEachFormula(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            for (Case entry : allShips()) {
                scene.atTime(entry.time(), () -> {
                    BasicEntityShip ship = scene.ship(entry);
                    for (int level : LEVELS) {
                        scene.prepare(ship, level, false, true, 0, true);
                        ship.tickCount = 127;
                        ship.aiStep();
                        helper.assertTrue(ship.getActiveEffects().isEmpty(), entry.name() + " fired at tick 127");
                        scene.fire(ship);
                        scene.assertSelfEffects(entry, ship, level);
                        // the next period renews the effect to the full duration
                        ship.removeAllEffects();
                        ship.tickCount = 256;
                        ship.aiStep();
                        scene.assertSelfEffects(entry, ship, level);
                    }
                    // switching the ring off stops new effects but does not remove the existing ones
                    scene.prepare(ship, 75, false, true, 0, true);
                    MobEffect effect = entry.auras()[0].effect();
                    ship.addEffect(new MobEffectInstance(effect, 40, 0, false, false));
                    ship.setStateFlag(ID.F.UseRingEffect, false);
                    helper.assertTrue(!ship.getStateFlag(ID.F.UseRingEffect), "Ring switch fixture was ignored");
                    scene.fire(ship);
                    MobEffectInstance left = ship.getEffect(effect);
                    helper.assertTrue(left != null && left.getAmplifier() == 0 && left.getDuration() <= 40
                                    && ship.getActiveEffects().size() == 1,
                            entry.name() + " renewed, removed or added effects while switched off");
                });
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ruDayLuckIsServerOwnedAndIgnoresMarriageAndFuel(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            Case entry = ru();
            helper.assertTrue(!helper.getLevel().isClientSide(), "The fixture level must be the server");
            scene.atTime(NOON, () -> {
                BasicEntityShip ship = scene.ship(entry);
                BasicEntityShip neighbour = scene.ship(entry);
                scene.prepare(ship, 150, false, true, 0, true);
                scene.prepare(neighbour, 150, false, true, 0, true);
                helper.assertTrue(ship.getOwnerUUID() == null, "Owner fixture was ignored");
                scene.fire(ship);
                scene.assertSelfEffects(entry, ship, 150);
                MobEffectInstance luck = ship.getEffect(MobEffects.LUCK);
                helper.assertTrue(luck != null && luck.getAmplifier() == 1 && luck.getDuration() == DURATION,
                        "Expected Ru luck a1/d150");
                helper.assertTrue(neighbour.getActiveEffects().isEmpty(), "Ru gave a nearby ship luck");
                scene.assertNoBystanderEffects(entry);
                // a married ship with fuel and an owner gets the same luck and still shares it with nobody
                ship.setOwnerUUID(scene.owner.getUUID());
                scene.prepare(ship, 150, true, true, 500, false);
                scene.owner.setPos(scene.at);
                scene.fire(ship);
                scene.assertSelfEffects(entry, ship, 150);
                helper.assertTrue(scene.owner.getActiveEffects().isEmpty(), "Ru gave its admiral an effect");
                // the switch stops new luck
                scene.prepare(ship, 150, true, false, 500, false);
                scene.fire(ship);
                helper.assertTrue(ship.getActiveEffects().isEmpty(), "Ru ignored the ring switch");
            });
            scene.atTime(NIGHT, () -> {
                BasicEntityShip ship = scene.ship(entry);
                scene.prepare(ship, 150, true, true, 500, false);
                scene.fire(ship);
                helper.assertTrue(ship.getActiveEffects().isEmpty(), "Ru gave itself luck at night");
            });
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void selfAuraEffectsReachShipAttributes(GameTestHelper helper) {
        try (Scene scene = new Scene(helper)) {
            for (Case entry : allShips()) {
                scene.atTime(entry.time(), () -> {
                    BasicEntityShip ship = scene.ship(entry);
                    scene.prepare(ship, 150, false, true, 0, true);
                    ship.calcShipAttributes(15, false);
                    Attrs control = ship.getAttrs();
                    float[] before = snapshot(control);
                    float[] potionBefore = potions(control);
                    scene.fire(ship);
                    scene.assertSelfEffects(entry, ship, 150);
                    ship.calcShipAttributes(15, false);
                    Attrs attrs = ship.getAttrs();
                    float[] after = snapshot(attrs);
                    float[] potionAfter = potions(attrs);
                    if (entry.auras()[0].effect() == MobEffects.LUCK) {
                        float expected = 0.2F * Math.min(150 / 140 + 1, 5);
                        for (int id : new int[]{ID.Attrs.CRI, ID.Attrs.DHIT, ID.Attrs.THIT}) {
                            helper.assertTrue(Math.abs(potionAfter[id] - potionBefore[id] - expected) < 1.0E-4F,
                                    entry.name() + " luck did not reach potion attribute " + id);
                            helper.assertTrue(after[id] > before[id], entry.name() + " luck did not reach final attribute " + id);
                        }
                    } else {
                        float speed = 0.08F * Math.min(entry.auras()[0].amplifier().applyAsInt(150) + 1, 5);
                        float jump = 2F * Math.min(entry.auras()[1].amplifier().applyAsInt(150) + 1, 5);
                        helper.assertTrue(Math.abs(potionAfter[ID.Attrs.MOV] - potionBefore[ID.Attrs.MOV] - speed) < 1.0E-4F,
                                entry.name() + " speed did not reach the potion attribute");
                        helper.assertTrue(Math.abs(potionAfter[ID.Attrs.HIT] - potionBefore[ID.Attrs.HIT] - jump) < 1.0E-4F,
                                entry.name() + " jump did not reach the potion attribute");
                        helper.assertTrue(after[ID.Attrs.MOV] > before[ID.Attrs.MOV],
                                entry.name() + " speed did not reach the final movement attribute");
                        helper.assertTrue(after[ID.Attrs.HIT] > before[ID.Attrs.HIT],
                                entry.name() + " jump did not reach the final hit attribute");
                    }
                });
            }
            helper.succeed();
        }
    }

    private static float[] snapshot(Attrs attrs) {
        float[] values = new float[Attrs.AttrsLength];
        for (int i = 0; i < values.length; i++) {
            values[i] = attrs.getAttrsBuffed(i);
        }
        return values;
    }

    private static float[] potions(Attrs attrs) {
        float[] values = new float[Attrs.AttrsLength];
        for (int i = 0; i < values.length; i++) {
            values[i] = attrs.getAttrsPotion(i);
        }
        return values;
    }

    /** An admiral and a bystander kept away from the ships, plus the ships under test, all removed on close. */
    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final FakePlayer owner;
        final FakePlayer other;
        final Vec3 at;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "self_aura_admiral"));
            this.other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "self_aura_bystander"));
            this.at = helper.absoluteVec(SPAWN);
            this.owner.setPos(this.at);
            this.other.setPos(this.at);
            helper.getLevel().addNewPlayer(this.owner);
            helper.getLevel().addNewPlayer(this.other);
        }

        /** Runs the body at the given time of day, then restores the time and the sky brightness derived from it. */
        void atTime(long time, Runnable body) {
            ServerLevel level = this.helper.getLevel();
            long saved = level.getDayTime();
            try {
                level.setDayTime(time);
                level.updateSkyBrightness();
                this.helper.assertTrue(level.getDayTime() == time, "Time fixture was ignored");
                body.run();
            } finally {
                level.setDayTime(saved);
                level.updateSkyBrightness();
            }
        }

        BasicEntityShip ship(Case entry) {
            BasicEntityShip ship = this.entities.add(entry.type().get().create(this.helper.getLevel()));
            this.helper.assertTrue(ship != null, "Could not create " + entry.name());
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            ship.setPos(this.at);
            return ship;
        }

        /** Sets every input, reads it back and clears all effects. {@code unowned} also drops the owner. */
        void prepare(BasicEntityShip ship, int level, boolean married, boolean ring, int grudge, boolean noFuel) {
            ship.setStateMinor(ID.M.ShipLevel, level);
            ship.setStateMinor(ID.M.NumGrudge, grudge);
            ship.setStateFlag(ID.F.NoFuel, noFuel);
            ship.setStateFlag(ID.F.IsMarried, married);
            ship.setStateFlag(ID.F.UseRingEffect, ring);
            ship.setGrudgeConsumption(0);
            ship.setPos(this.at);
            this.owner.removeAllEffects();
            this.other.removeAllEffects();
            ship.removeAllEffects();
            this.helper.assertTrue(ship.getStateMinor(ID.M.ShipLevel) == level
                            && ship.getStateMinor(ID.M.NumGrudge) == grudge
                            && ship.getStateFlag(ID.F.NoFuel) == noFuel
                            && ship.getStateFlag(ID.F.IsMarried) == married
                            && ship.getStateFlag(ID.F.UseRingEffect) == ring
                            && ship.getGrudgeConsumption() == 0
                            && ship.getActiveEffects().isEmpty(),
                    "A fixture value was ignored");
        }

        /** Runs the concrete aiStep once at the first tick of a period. */
        void fire(BasicEntityShip ship) {
            ship.tickCount = 128;
            ship.aiStep();
        }

        void assertSelfEffects(Case entry, BasicEntityShip ship, int level) {
            for (Aura aura : entry.auras()) {
                MobEffectInstance found = ship.getEffect(aura.effect());
                int amplifier = aura.amplifier().applyAsInt(level);
                this.helper.assertTrue(found != null && found.getAmplifier() == amplifier && found.getDuration() == DURATION,
                        entry.name() + " Lv" + level + " expected " + aura.effect().getDescriptionId() + " a" + amplifier
                                + "/d" + DURATION + ", found "
                                + (found == null ? "none" : "a" + found.getAmplifier() + "/d" + found.getDuration()));
                this.helper.assertTrue(!found.isAmbient() && !found.isVisible(), entry.name() + " effect is not hidden");
            }
            this.helper.assertTrue(ship.getActiveEffects().size() == entry.auras().length,
                    entry.name() + " Lv" + level + " gave itself " + ship.getActiveEffects().size() + " effect kinds");
        }

        void assertNoBystanderEffects(Case entry) {
            this.helper.assertTrue(this.owner.getActiveEffects().isEmpty() && this.other.getActiveEffects().isEmpty(),
                    entry.name() + " gave an effect to a player");
        }

        @Override
        public void close() {
            this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
            this.helper.getLevel().removePlayerImmediately(this.other, Entity.RemovalReason.DISCARDED);
            this.entities.close();
        }
    }
}
