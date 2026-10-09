package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityFloatingFort;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.utility.CombatHelper;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerDamageReductionGameTests {
    private PlayerDamageReductionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_hostile_light")
    public static void hostileLightReducesAfterCombatRoll(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShipHostile host = fixture.hostile();
            fixture.checkDirect(host.getAttrs(), host::attackEntityWithAmmo);
            fixture.setDamage(host.getAttrs(), 100F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.THIT, 1F);
            helper.assertTrue(host.getAttrs().getAttrsBuffed(ID.Attrs.THIT) == 1F, "Triple hit fixture was ignored");
            fixture.checkInput(host::attackEntityWithAmmo, fixture.player, fixture.player.damage, 59F);
            fixture.checkInput(host::attackEntityWithAmmo, fixture.cow, fixture.cow.damage, 300F);
            fixture.checkMiss(host, host::attackEntityWithAmmo);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_aircraft_light")
    public static void aircraftLightReducesAfterCriticalRoll(GameTestHelper helper) {
        run(helper, fixture -> fixture.checkAircraft(false));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_aircraft_heavy")
    public static void aircraftHeavyReducesAfterCriticalRoll(GameTestHelper helper) {
        run(helper, fixture -> fixture.checkAircraft(true));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_beam_mixed_targets")
    public static void beamReducesEachPlayerWithoutChangingOtherTargets(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShipHostile host = fixture.hostile();
            for (float raw : new float[]{100F, 300F}) {
                EntityProjectileBeam beam = fixture.entities.add(ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel()));
                helper.assertTrue(beam != null, "Could not create beam");
                beam.initBeam(host, 1D, 0D, 0D, raw);
                beam.setPos(fixture.player.position().subtract(4D, 0D, 0D));
                fixture.player.damage.reset();
                fixture.cow.damage.reset();
                beam.tick();
                fixture.player.damage.assertInput(helper, raw == 100F ? 25F : 59F, "Beam player");
                fixture.cow.damage.assertInput(helper, raw, "Beam non-player");
                beam.setPos(fixture.player.position().subtract(4D, 0D, 0D));
                beam.tick();
                helper.assertTrue(fixture.player.damage.calls == 1 && fixture.cow.damage.calls == 1,
                        "Beam repeated a hit on an already visited target");
                beam.discard();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_rensouhou")
    public static void rensouhouVariantsReducePlayerInput(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShipHostile host = fixture.hostile();
            for (EntityRensouhou summon : new EntityRensouhou[]{
                    ModEntities.RENSOUHOU.get().create(helper.getLevel()),
                    ModEntities.RENSOUHOU_S.get().create(helper.getLevel())}) {
                helper.assertTrue(summon != null, "Could not create Rensouhou");
                fixture.entities.add(summon);
                summon.setHost(host);
                summon.setNumAmmoLight(20);
                fixture.checkDirect(summon.getAttrs(), summon::attackTarget);
                helper.assertTrue(summon.getNumAmmoLight() == 16, "Rensouhou ammo consumption changed");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_existing_friendly_light")
    public static void friendlyLightStillReducesExactlyOnce(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShip host = fixture.entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(host != null, "Could not create friendly ship");
            host.setNoAi(true);
            host.setStateMinor(ID.M.NumGrudge, 100_000);
            host.setStateFlag(ID.F.NoFuel, false);
            host.setAmmoLight(1_000);
            helper.assertTrue(host.getAmmoLight() == 1_000 && host.hasAmmoLight(), "Friendly ammo fixture was ignored");
            fixture.prepare(host);
            fixture.checkDirect(host.getAttrs(), host::attackEntityWithAmmo);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_existing_missile")
    public static void missileStillReducesExactlyOnce(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShipHostile host = fixture.hostile();
            for (float raw : new float[]{100F, 300F}) {
                TestMissile missile = fixture.entities.add(new TestMissile(helper.getLevel()));
                Vec3 position = fixture.player.position();
                missile.initMissile(host, 0, 0, raw, 0F, (float) position.y,
                        position.x, (float) position.y, position.z, 160, 0F, 0F, 0F, 0F);
                missile.setPos(position);
                fixture.player.damage.reset();
                fixture.cow.damage.reset();
                missile.impact();
                fixture.player.damage.assertInput(helper, raw == 100F ? 25F : 59F, "Missile player");
                fixture.cow.damage.assertInput(helper, raw, "Missile non-player");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_existing_fort")
    public static void floatingFortStillReducesExactlyOnce(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShipHostile host = fixture.hostile();
            for (float raw : new float[]{100F, 300F}) {
                EntityFloatingFort fort = fixture.entities.add(ModEntities.FLOATING_FORT.get().create(helper.getLevel()));
                helper.assertTrue(fort != null, "Could not create floating fort");
                fort.setHost(host);
                fixture.prepare(fort);
                fixture.setDamage(fort.getAttrs(), raw);
                fort.setPos(fixture.player.position());
                fixture.player.damage.reset();
                fixture.cow.damage.reset();
                fort.attackEntityWithHeavyAmmo(fixture.player);
                fixture.player.damage.assertInput(helper, raw == 100F ? 25F : 59F, "Fort player");
                fixture.cow.damage.assertInput(helper, raw, "Fort non-player");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_player_damage_melee_unchanged")
    public static void ordinaryMeleeRetainsItsSeparateMultiplier(GameTestHelper helper) {
        run(helper, fixture -> {
            BasicEntityShipHostile host = fixture.hostile();
            fixture.setDamage(host.getAttrs(), 800F);
            helper.assertTrue(host.getAttackBaseDamage(0, fixture.player) == 100F, "Melee base fixture was ignored");
            fixture.checkInput(host::doHurtTarget, fixture.player, fixture.player.damage, 100F);
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                verification.accept(fixture);
                helper.succeed();
            }
        }, new Vec3(1D, 4D, 5D), new Vec3(5D, 4D, 5D), new Vec3(5D, 4D, 6D));
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final CapturingPlayer player;
        private final CapturingCow cow;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            ServerLevel level = helper.getLevel();
            this.player = new CapturingPlayer(level);
            this.player.setPos(helper.absoluteVec(new Vec3(5D, 4D, 5D)));
            this.player.setGameMode(GameType.SURVIVAL);
            this.player.getAbilities().invulnerable = false;
            level.addNewPlayer(this.player);
            this.cow = this.entities.add(new CapturingCow(level));
            this.cow.setNoAi(true);
            this.cow.setPos(helper.absoluteVec(new Vec3(5D, 4D, 6D)));
            level.addFreshEntity(this.cow);
            helper.assertTrue(!this.player.isCreative() && !this.player.isSpectator()
                    && !this.player.getAbilities().invulnerable, "Player fixture must be survival and vulnerable");
            GameTestEntities.assertRegistered(helper, this.player);
            GameTestEntities.assertRegistered(helper, this.cow);
        }

        private BasicEntityShipHostile hostile() {
            BasicEntityShipHostile host = this.entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(this.helper.getLevel()));
            this.helper.assertTrue(host != null, "Could not create hostile ship");
            host.setNoAi(true);
            host.setStateFlag(ID.F.NoFuel, false);
            prepare(host);
            return host;
        }

        private void prepare(IShipAttackBase attacker) {
            ((Entity) attacker).setPos(this.helper.absoluteVec(new Vec3(1D, 4D, 5D)));
            attacker.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            attacker.getAttrs().setAttrsBuffed(ID.Attrs.CRI, 0F);
            attacker.getAttrs().setAttrsBuffed(ID.Attrs.DHIT, 0F);
            attacker.getAttrs().setAttrsBuffed(ID.Attrs.THIT, 0F);
            attacker.getRand().setSeed(0L);
            this.helper.assertTrue(CombatHelper.calcMissRate(attacker, 1F) == 0F
                    && attacker.getAttrs().getAttrsBuffed(ID.Attrs.CRI) == 0F
                    && attacker.getAttrs().getAttrsBuffed(ID.Attrs.DHIT) == 0F
                    && attacker.getAttrs().getAttrsBuffed(ID.Attrs.THIT) == 0F, "Combat roll fixture was ignored");
        }

        private void setDamage(Attrs attrs, float raw) {
            for (int attribute : new int[]{ID.Attrs.ATK_L, ID.Attrs.ATK_H, ID.Attrs.ATK_AL, ID.Attrs.ATK_AH}) {
                attrs.setAttrsBuffed(attribute, raw);
                this.helper.assertTrue(attrs.getAttrsBuffed(attribute) == raw, "Attack fixture was ignored");
            }
        }

        private void checkDirect(Attrs attrs, Consumer<Entity> attack) {
            for (float raw : new float[]{100F, 300F}) {
                setDamage(attrs, raw);
                checkInput(attack, this.player, this.player.damage, raw == 100F ? 25F : 59F);
                checkInput(attack, this.cow, this.cow.damage, raw);
            }
        }

        private void checkAircraft(boolean heavy) {
            BasicEntityShipHostile host = hostile();
            BasicEntityAirplane plane = this.entities.add(ModEntities.AIRPLANE.get().create(this.helper.getLevel()));
            this.helper.assertTrue(plane != null, "Could not create aircraft");
            plane.setHost(host);
            prepare(plane);
            Consumer<Entity> attack = heavy ? plane::attackEntityWithHeavyAmmo : plane::attackEntityWithAmmo;
            checkDirect(plane.getAttrs(), attack);
            setDamage(plane.getAttrs(), 100F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.CRI, 1F);
            this.helper.assertTrue(host.getAttrs().getAttrsBuffed(ID.Attrs.CRI) == 1F, "Critical fixture was ignored");
            checkInput(attack, this.player, this.player.damage, 37.5F);
            checkInput(attack, this.cow, this.cow.damage, 150F);
            checkMiss(host, attack);
        }

        private void checkMiss(IShipAttackBase host, Consumer<Entity> attack) {
            host.getAttrs().setAttrsBuffed(ID.Attrs.CRI, 0F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.THIT, 0F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 0F);
            host.getRand().setSeed(4096L);
            this.player.damage.reset();
            attack.accept(this.player);
            this.helper.assertTrue(this.player.damage.calls == 0, "Miss reached hurt");
        }

        private void checkInput(Consumer<Entity> attack, Entity target, DamageCapture capture, float expected) {
            capture.reset();
            attack.accept(target);
            capture.assertInput(this.helper, expected, target instanceof CapturingPlayer ? "Player" : "Non-player");
        }

        @Override
        public void close() {
            this.helper.getLevel().removePlayerImmediately(this.player, Entity.RemovalReason.DISCARDED);
            this.entities.close();
        }
    }

    /** Records the argument before vanilla difficulty, armour, effects and health processing. */
    private static final class DamageCapture {
        private float amount;
        private int calls;

        private boolean record(float amount) {
            this.amount = amount;
            this.calls++;
            return false;
        }

        private void reset() {
            this.amount = Float.NaN;
            this.calls = 0;
        }

        private void assertInput(GameTestHelper helper, float expected, String context) {
            helper.assertTrue(this.calls == 1 && Math.abs(this.amount - expected) < 0.001F,
                    context + " hurt input: expected=" + expected + " actual=" + this.amount + " calls=" + this.calls);
        }
    }

    private static final class CapturingPlayer extends FakePlayer {
        private final DamageCapture damage = new DamageCapture();

        private CapturingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "damage_input"));
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return this.damage.record(amount);
        }
    }

    private static final class CapturingCow extends Cow {
        private final DamageCapture damage = new DamageCapture();

        private CapturingCow(ServerLevel level) {
            super(EntityType.COW, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return this.damage.record(amount);
        }
    }

    private static final class TestMissile extends EntityAbyssMissile {
        private TestMissile(ServerLevel level) {
            super(ModEntities.ABYSS_MISSILE.get(), level);
        }

        private void impact() {
            onImpact(null);
        }
    }
}
