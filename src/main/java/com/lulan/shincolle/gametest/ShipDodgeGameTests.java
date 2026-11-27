package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttrs;
import com.lulan.shincolle.entity.IShipInvisible;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipDodgeGameTests {
    private static final Vec3 SHIP_AT = new Vec3(2D, 4D, 2D);
    private static final Vec3 ATTACKER_AT = new Vec3(22D, 4D, 2D);

    private ShipDodgeGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void submarineInvisibleBonusIsCappedBeforeRolling(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(0.1D);
            BasicEntityShip ship = scene.friendly(true, 0F);
            helper.assertTrue(((IShipInvisible) ship).getInvisibleLevel() == 0.2F, "Ka invisibility must be 0.2");
            scene.dodge(ship, 64F, 0.15F, false);
            scene.dodge(ship, 64F, 0.05F, true);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void submarineDistanceBoundariesAreStrict(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(0.1D);
            BasicEntityShip ship = scene.friendly(true, 0F);
            scene.dodge(ship, Math.nextDown(36F), 0.05F, false);
            scene.dodge(ship, 36F, 0.05F, false);
            scene.dodge(ship, Math.nextUp(36F), 0.05F, true);
            scene.dodge(ship, Math.nextDown(256F), 0.15F, false);
            scene.dodge(ship, 256F, 0.15F, false);
            scene.dodge(ship, Math.nextUp(256F), 0.55F, true);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void farSubmarineBonusRemainsOutsideTheCap(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(0.1D);
            BasicEntityShip ship = scene.friendly(true, 0F);
            scene.dodge(ship, 400F, 0.59F, true);
            scene.dodge(ship, 400F, 0.61F, false);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void positiveCapStillAppliesToFriendlyAndHostileSurfaceShips(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(0.1D);
            BasicEntityShip friendly = scene.friendly(false, 0.2F);
            BasicEntityShipHostile hostile = scene.hostile(false, 0.2F);
            for (IShipAttrs ship : new IShipAttrs[]{friendly, hostile}) {
                scene.dodge(ship, 400F, 0.15F, false);
                scene.dodge(ship, 400F, 0.05F, true);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0.04F);
                scene.dodge(ship, 400F, 0.05F, false);
                scene.dodge(ship, 400F, 0.02F, true);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void zeroCapRemovesDodgeButNotTheFarSubmarineBonus(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(0D);
            for (IShipAttrs ship : new IShipAttrs[]{
                    scene.friendly(false, 1F), scene.hostile(false, 1F),
                    scene.friendly(true, 1F), scene.hostile(true, 1F)}) {
                scene.dodge(ship, 36F, 0.05F, false);
                scene.dodge(ship, 64F, 0.05F, false);
                scene.dodge(ship, 256F, 0.05F, false);
                scene.dodge(ship, 400F, 0.49F, ship instanceof IShipInvisible);
                scene.dodge(ship, 400F, 0.51F, false);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void negativeCapsLeaveFriendlyAndHostileDodgeUnlimited(GameTestHelper helper) {
        run(helper, scene -> {
            for (double cap : new double[]{-1D, -0.25D}) {
                scene.limit.set(cap);
                for (IShipAttrs ship : new IShipAttrs[]{scene.friendly(false, 0.2F), scene.hostile(false, 0.2F)}) {
                    scene.dodge(ship, 64F, 0.15F, true);
                    scene.dodge(ship, 64F, 0.25F, false);
                }
                BasicEntityShip submarine = scene.friendly(true, 0.6F);
                scene.dodge(submarine, 64F, 0.79F, true);
                scene.dodge(submarine, 64F, 0.81F, false);
                BasicEntityShipHostile hostile = scene.hostile(true, 0.6F);
                scene.dodge(hostile, 64F, 0.89F, true);
                scene.dodge(hostile, 64F, 0.91F, false);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void hostileSelfDamageIsRejectedEvenWithZeroDodge(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(1D);
            BasicEntityShipHostile ship = scene.hostile(false, 0F);
            DamageSource source = ship.damageSources().mobAttack(ship);
            helper.assertTrue(source.getEntity() == ship, "The damage cause must be the defender itself");
            scene.hurt(ship, source, false);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void hostileNearAttacksContrastZeroAndOneDodge(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(1D);
            for (boolean submarine : new boolean[]{false, true}) {
                BasicEntityShipHostile dodging = scene.hostile(submarine, 1F);
                BasicEntityShipHostile control = scene.hostile(submarine, 0F);
                Pig attacker = scene.attacker(4D);
                helper.assertTrue(dodging.distanceToSqr(attacker) == 16D
                                && control.distanceToSqr(attacker) == 16D,
                        "Both defenders must be four blocks from the same attacker");
                DamageSource source = attacker.damageSources().mobAttack(attacker);
                scene.hurt(dodging, source, false);
                scene.hurt(control, source, true);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_dodge_config")
    public static void hostileSubmarineHurtUsesAttackerDistanceAndCappedInvisibility(GameTestHelper helper) {
        run(helper, scene -> {
            scene.limit.set(0.1D);
            for (double distance : new double[]{6D, 8D, 16D, 20D}) {
                BasicEntityShipHostile ship = scene.hostile(true, 0F);
                Pig attacker = scene.attacker(distance);
                helper.assertTrue(ship.distanceToSqr(attacker) == distance * distance, "Incorrect attack distance");
                float roll = distance == 6D ? 0.05F : 0.15F;
                scene.seedRoll(ship, roll);
                scene.hurt(ship, attacker.damageSources().mobAttack(attacker), distance <= 16D);
            }
        });
    }

    private static void run(GameTestHelper helper, Consumer<Scene> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                verification.accept(scene);
            }
            helper.succeed();
        }, SHIP_AT, ATTACKER_AT);
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final ResolvedLimit limit;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.limit = new ResolvedLimit(helper);
        }

        BasicEntityShip friendly(boolean submarine, float dodge) {
            BasicEntityShip ship = this.entities.add((submarine ? ModEntities.SS_KA.get() : ModEntities.BB_KONGOU.get())
                    .create(this.helper.getLevel()));
            this.helper.assertTrue(ship != null, "Could not create friendly defender");
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setMorale(3000);
            ship.calcShipAttributes(31, false);
            this.helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                    "Friendly fixture must have fuel");
            prepare(ship, ship, dodge);
            return ship;
        }

        BasicEntityShipHostile hostile(boolean submarine, float dodge) {
            BasicEntityShipHostile ship = this.entities.add(
                    (submarine ? ModEntities.SS_U511_MOB.get() : ModEntities.BB_KONGOU_MOB.get())
                            .create(this.helper.getLevel()));
            this.helper.assertTrue(ship != null, "Could not create hostile defender");
            ship.calcShipAttributes(31, false);
            prepare(ship, ship, dodge);
            return ship;
        }

        private void prepare(Mob ship, IShipAttrs attrs, float dodge) {
            ship.moveTo(this.helper.absoluteVec(SHIP_AT));
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(false);
            ship.invulnerableTime = 0;
            ship.removeAllEffects();
            ship.setHealth(ship.getMaxHealth());
            attrs.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, dodge);
            attrs.getAttrs().setAttrsBuffed(ID.Attrs.DEF, 0F);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(ship), "Could not add defender");
            GameTestEntities.assertRegistered(this.helper, ship);
            this.helper.assertTrue(attrs.shipAttribute(CoreShipAttributes.DODGE) == dodge
                            && attrs.getAttrs().getDefense() == 0F && ship.getHealth() > 20F
                            && !ship.isInvulnerable() && ship.invulnerableTime == 0,
                    "Defender requires the requested dodge, zero defense, vulnerable full health and no hurt cooldown");
        }

        Pig attacker(double distance) {
            Pig attacker = this.entities.add(EntityType.PIG.create(this.helper.getLevel()));
            this.helper.assertTrue(attacker != null, "Could not create attacker");
            attacker.moveTo(this.helper.absoluteVec(SHIP_AT.add(distance, 0D, 0D)));
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(attacker), "Could not add attacker");
            GameTestEntities.assertRegistered(this.helper, attacker);
            return attacker;
        }

        void seedRoll(Mob ship, float desired) {
            RandomSource probe = RandomSource.create();
            for (long seed = 0; seed < 100_000; seed++) {
                probe.setSeed(seed);
                float value = probe.nextFloat();
                if (Math.abs(value - desired) < 0.001F) {
                    ship.getRandom().setSeed(seed);
                    this.helper.assertTrue(ship.getRandom().nextFloat() == value, "The fixture RNG must match the probe");
                    ship.getRandom().setSeed(seed);
                    return;
                }
            }
            throw new GameTestAssertException("Could not seed dodge roll near " + desired);
        }

        void dodge(IShipAttrs ship, float distSq, float roll, boolean expected) {
            this.seedRoll((Mob) ship, roll);
            this.helper.assertTrue(CombatHelper.canDodge(ship, distSq) == expected,
                    "Unexpected dodge: distance squared=" + distSq + ", roll~" + roll
                            + ", cap=" + ConfigHandler.shipAttributeMaximum(CoreShipAttributes.DODGE));
        }

        void hurt(BasicEntityShipHostile ship, DamageSource source, boolean accepted) {
            this.helper.assertTrue(source.getEntity() != null && !ship.isInvulnerableTo(source),
                    "The test requires an attacker and a vulnerable defender");
            float before = ship.getHealth();
            boolean result = ship.hurt(source, 20F);
            this.helper.assertTrue(result == accepted, "Unexpected hostile hurt result: " + result + " expected " + accepted);
            this.helper.assertTrue(accepted ? ship.getHealth() < before : ship.getHealth() == before,
                    "Hostile health must decrease exactly when the hit is accepted");
        }

        @Override
        public void close() {
            try {
                this.limit.close();
            } finally {
                this.entities.close();
            }
        }
    }

    /** Replace only the immutable resolved snapshot; do not trigger config saves or asynchronous reloads. */
    private static final class ResolvedLimit implements AutoCloseable {
        private final GameTestHelper helper;
        private final Field field;
        private final Constructor<?> constructor;
        private final Object original;
        private final double originalMaximum;

        ResolvedLimit(GameTestHelper helper) {
            this.helper = helper;
            this.originalMaximum = ConfigHandler.shipAttributeMaximum(CoreShipAttributes.DODGE);
            try {
                this.field = ConfigHandler.class.getDeclaredField("shipAttributeLimits");
                this.field.setAccessible(true);
                this.original = this.field.get(null);
                this.constructor = this.original.getClass().getDeclaredConstructor(double[].class, Map.class);
                this.constructor.setAccessible(true);
            } catch (ReflectiveOperationException error) {
                throw new GameTestAssertException("Could not open resolved limits: " + error);
            }
        }

        void set(double maximum) {
            try {
                this.field.set(null, this.constructor.newInstance(ConfigHandler.limitShipAttrs.clone(),
                        Map.of(CoreShipAttributes.DODGE, maximum)));
            } catch (ReflectiveOperationException error) {
                throw new GameTestAssertException("Could not set resolved dodge limit: " + error);
            }
            this.helper.assertTrue(ConfigHandler.shipAttributeMaximum(CoreShipAttributes.DODGE) == maximum,
                    "The effective dodge limit must match the fixture");
        }

        @Override
        public void close() {
            try {
                this.field.set(null, this.original);
                this.helper.assertTrue(this.field.get(null) == this.original
                                && ConfigHandler.shipAttributeMaximum(CoreShipAttributes.DODGE) == this.originalMaximum,
                        "The original resolved limits must be restored");
            } catch (ReflectiveOperationException error) {
                throw new GameTestAssertException("Could not restore resolved limits: " + error);
            }
        }
    }
}
