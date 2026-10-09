package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Objects;

/** Checks attribute changes after the host has already mounted, without ticking the host fixture. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountAttributeRefreshGameTests {
    private MountAttributeRefreshGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_mount_attrs_new", timeoutTicks = 100)
    public static void newHarbourMountTracksAttributesWithoutHealing(GameTestHelper helper) {
        run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, fixture -> {
            for (int phase : new int[]{0, 1, 15, 31}) {
                fixture.mount.tickCount = 64 + phase;
                fixture.change(0.3F, 120F, 17F);
                fixture.advance(32);
                fixture.assertCopied(0.3F, 120F, 17F);
                fixture.change(0.08F, 40F, 4F);
                fixture.advance(32);
                fixture.assertCopied(0.08F, 40F, 4F);
            }
            // Changing the cap does not write current HP, even when HP is above the new cap.
            fixture.mount.setHealth(15F);
            fixture.change(0.12F, 20F, 6F);
            fixture.advance(32);
            helper.assertTrue(fixture.mount.getMaxHealth() == 10F, "lower maximum HP must be copied");
            helper.assertTrue(fixture.mount.getHealth() == 15F, "refresh must not add a health clamp");
        });
    }

    @GameTest(template = "arena", batch = "isolated_mount_attrs_legacy", timeoutTicks = 100)
    public static void legacyHarbourMountKeepsItsInitialAttributes(GameTestHelper helper) {
        run(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, fixture -> {
            fixture.change(0.3F, 120F, 17F);
            fixture.advance(64);
            fixture.assertCopied(0.1F, 80F, 2F);
        });
    }

    @GameTest(template = "arena", batch = "isolated_mount_attrs_switch", timeoutTicks = 100)
    public static void mountAttributeRefreshUsesCurrentAuthorityWithoutRebuildingGoals(GameTestHelper helper) {
        run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, fixture -> {
            fixture.change(0.2F, 100F, 12F);
            fixture.advance(32);
            fixture.assertCopied(0.2F, 100F, 12F);
            ConfigHandler.setShipAiTargetAuthorityForTest(ConfigHandler.ShipAiTargetAuthority.LEGACY);
            fixture.change(0.3F, 120F, 17F);
            fixture.advance(64);
            fixture.assertCopied(0.2F, 100F, 12F);
            ConfigHandler.setShipAiTargetAuthorityForTest(ConfigHandler.ShipAiTargetAuthority.NEW);
            fixture.advance(32);
            fixture.assertCopied(0.3F, 120F, 17F);
        });
    }

    private static void run(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode,
                            java.util.function.Consumer<Fixture> assertions) {
        Vec3 position = new Vec3(3.5D, 2D, 3.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
                 GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = new Fixture(helper, entities, position);
                assertions.accept(fixture);
                helper.succeed();
            }
        }, position);
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final BasicEntityShip ship;
        private final BasicEntityMount mount;

        Fixture(GameTestHelper helper, GameTestEntities entities, Vec3 position) {
            this.helper = helper;
            this.ship = entities.add(ModEntities.HARBOUR_HIME.get().create(helper.getLevel()));
            helper.assertTrue(this.ship != null, "fixture must create Harbour Hime");
            this.ship.setStateMinor(ID.M.NumGrudge, 100_000);
            this.ship.setStateFlag(ID.F.NoFuel, false);
            this.ship.setEntitySit(true);
            this.ship.setNoGravity(true);
            this.ship.calcShipAttributes(31, false);
            this.ship.moveTo(helper.absoluteVec(position));
            helper.assertTrue(helper.getLevel().addFreshEntity(this.ship), "fixture ship must be added");
            this.change(0.1F, 80F, 2F);
            this.mount = entities.add(ModEntities.MOUNT_HBH.get().create(helper.getLevel()));
            helper.assertTrue(this.mount != null, "fixture must create HbH");
            this.mount.moveTo(this.ship.position());
            this.mount.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(this.mount), "fixture mount must be added");
            this.mount.setHost(this.ship);
            helper.assertTrue(this.ship.startRiding(this.mount, true), "fixture must keep the ship riding HbH");
            this.mount.setHealth(3F);
            GameTestEntities.assertRegistered(helper, this.ship);
            GameTestEntities.assertRegistered(helper, this.mount);
            this.assertCopied(0.1F, 80F, 2F);
        }

        void change(float speed, float health, float defense) {
            this.ship.getAttrs().setAttrsBuffed(ID.Attrs.MOV, speed);
            this.ship.getAttrs().setAttrsBuffed(ID.Attrs.DEF, defense);
            Objects.requireNonNull(this.ship.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(health);
            helper.assertTrue(Math.abs(this.ship.getAttrs().getMoveSpeed() - speed) < 0.00001F,
                    "fixture must actually change host MOV");
            helper.assertTrue(this.ship.getMaxHealth() == health, "fixture must actually change host maximum HP");
        }

        void advance(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                float health = this.mount.getHealth();
                // ServerLevel advances entity age before calling tick.
                this.mount.tickCount++;
                this.mount.tick();
                helper.assertTrue(this.mount.isAlive() && this.mount.getHost() == this.ship
                                && this.ship.getVehicle() == this.mount,
                        "refresh must preserve the mount, host and riding relation");
                helper.assertTrue(this.ship.getStateMinor(ID.M.NumGrudge) > 0
                                && !this.ship.getStateFlag(ID.F.NoFuel), "fixture must retain fuel");
                helper.assertTrue(this.mount.getHealth() == health, "refresh must not heal the injured mount");
            }
        }

        void assertCopied(float speed, float hostHealth, float defense) {
            double actual = this.mount.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(actual - speed) < 0.00001D,
                    "mount MOV must follow host within 32 ticks: expected=" + speed + " actual=" + actual);
            helper.assertTrue(this.mount.getMaxHealth() == hostHealth * 0.5F, "mount maximum HP must follow host");
            try {
                Field field = BasicEntityMount.class.getDeclaredField("shipAttrs");
                field.setAccessible(true);
                AttrsAdv copy = (AttrsAdv) field.get(this.mount);
                helper.assertTrue(copy != this.ship.getAttrs() && copy.getAttrsBuffed(ID.Attrs.DEF) == defense,
                        "mount must retain an updated independent attribute copy");
            } catch (ReflectiveOperationException error) {
                helper.assertTrue(false, "cannot inspect mount attribute snapshot: " + error);
            }
        }
    }
}
