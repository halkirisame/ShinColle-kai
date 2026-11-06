package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Skill;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.network.C2SMountSkillPacket;
import com.lulan.shincolle.network.S2CMountSkillPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountSkillExtraGameTests {
    private MountSkillExtraGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 80)
    public static void mountCooldownCountsDownNaturallyAfterDismount(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var f = new MountSkillGameTests.Fixture(helper, false);
            GameTestEntities.onCompletion(helper, f::close, "mounted timer fixture");
            GameTestEntities.removeAllGoalsEachTick(helper, f.ship);
            GameTestEntities.removeAllGoalsEachTick(helper, f.mount);
            f.ship.setNoGravity(true);
            f.mount.setNoGravity(true);
            helper.runAfterDelay(25, () -> {
                f.ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
                f.ship.getAttrs().setAttrsBuffed(ID.Attrs.SPD, 1F);
                f.target.setPos(f.ship.position().add(3, 0, 0));
                f.cast(Skill.LIGHT);
                int cd = f.ship.getStateTimer(ID.T.MountSkillCD1);
                int tick = f.ship.tickCount;
                helper.assertTrue(cd > 3, "Natural fixture must accept the skill");
                f.player.stopRiding();
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(f.ship.tickCount > tick && f.ship.getStateTimer(ID.T.MountSkillCD1)
                            == cd - (f.ship.tickCount - tick), "Dismount must not clear or freeze natural manual cooldown");
                    helper.succeed();
                });
            });
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountSkillRejectsOwnerTargetRangeAndAbility(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var f = new MountSkillGameTests.Fixture(helper, false)) {
                f.ship.setPlayerUID(224002);
                f.cast(Skill.LIGHT);
                f.ship.setPlayerUID(224001);
                f.ship.setStateFlag(ID.F.AtkType_Light, false);
                f.cast(Skill.LIGHT);
                f.ship.setStateFlag(ID.F.AtkType_Light, true);
                f.target.setPos(f.ship.position().add(40, 0, 0));
                f.cast(Skill.LIGHT);
                f.cast(new C2SMountSkillPacket(Skill.LIGHT, new C2SMountSkillPacket.EntityTarget(f.ship.getId())));
                f.cast(new C2SMountSkillPacket(Skill.LIGHT, new C2SMountSkillPacket.EntityTarget(Integer.MAX_VALUE)));
                helper.assertTrue(f.ship.getAmmoLight() == 1000 && f.ship.getStateTimer(ID.T.MountSkillCD1) == 0,
                        "Rejected requests must not charge or start CD");
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountAcceptedEmptyAmmoStillStartsCooldown(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var f = new MountSkillGameTests.Fixture(helper, false)) {
                f.ship.setAmmoLight(0);
                f.ship.setStateMinor(ID.M.NumGrudge, 0);
                f.ship.setStateFlag(ID.F.NoFuel, true);
                f.ship.setStateFlag(ID.F.UseAmmoLight, false);
                f.ship.setNoAi(true);
                f.cast(Skill.LIGHT);
                helper.assertTrue(f.ship.getAmmoLight() == 0 && f.ship.getStateTimer(ID.T.MountSkillCD1)
                        == CombatHelper.getAttackDelay(f.ship.getAttrs().getAttackSpeed(), 1),
                        "Accepted false-returning body must start CD without consuming unavailable resources");
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountGunsAreIndependentAndSameSkillCannotRepeatAtZeroCooldown(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var f = new MountSkillGameTests.Fixture(helper, false)) {
                int cost = f.ship.getAmmoConsumption();
                f.cast(Skill.LIGHT);
                f.ship.setStateTimer(ID.T.MountSkillCD1, 0);
                f.cast(Skill.LIGHT);
                f.cast(Skill.HEAVY);
                helper.assertTrue(f.ship.getAmmoLight() == 1000 - cost && f.ship.getAmmoHeavy() == 1000 - cost,
                        "Each gun may execute once in the same tick, never twice for one skill even at CD zero");
                helper.assertTrue(f.ship.getStateTimer(ID.T.MountSkillCD2)
                        == CombatHelper.getAttackDelay(f.ship.getAttrs().getAttackSpeed(), 2), "Heavy CD formula");
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountHeavyAcceptsNegativeBlockY(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var f = new MountSkillGameTests.Fixture(helper, false)) {
                Vec3 at = f.ship.position();
                f.mount.setPos(at.x, -10, at.z);
                f.ship.setPos(at.x, -10, at.z);
                BlockPos pos = BlockPos.containing(at.x + 3, -10, at.z);
                f.cast(new C2SMountSkillPacket(Skill.HEAVY, new C2SMountSkillPacket.BlockTarget(pos)));
                helper.assertTrue(f.ship.getAmmoHeavy() == 1000 - f.ship.getAmmoConsumption()
                        && f.ship.getStateTimer(ID.T.MountSkillCD2) > 0, "Negative Y is a block coordinate, not an entity sentinel");
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountLightAircraftChargesOnceAndSharesCooldown(GameTestHelper helper) {
        aircraft(helper, Skill.AIR_LIGHT);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountHeavyAircraftChargesOnceAndSharesCooldown(GameTestHelper helper) {
        aircraft(helper, Skill.AIR_HEAVY);
    }

    private static void aircraft(GameTestHelper helper, Skill skill) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var f = new MountSkillGameTests.Fixture(helper, true)) {
                BasicEntityShipCV cv = (BasicEntityShipCV) f.ship;
                int cost = cv.getAmmoConsumption();
                int light = cv.getNumAircraftLight();
                int heavy = cv.getNumAircraftHeavy();
                helper.assertTrue(light > 0 && heavy > 0, "Aircraft fixture must be stocked");
                f.cast(skill);
                f.cast(skill == Skill.AIR_LIGHT ? Skill.AIR_HEAVY : Skill.AIR_LIGHT);
                boolean isLight = skill == Skill.AIR_LIGHT;
                helper.assertTrue(cv.getNumAircraftLight() == light - (isLight ? 1 : 0)
                        && cv.getNumAircraftHeavy() == heavy - (isLight ? 0 : 1), "Shared CD must allow only one aircraft");
                helper.assertTrue(cv.getAmmoLight() == 1000 - (isLight ? 6 * cost : 0)
                        && cv.getAmmoHeavy() == 1000 - (isLight ? 0 : 2 * cost), "Aircraft charges exactly once");
                int cd = CombatHelper.getAttackDelay(cv.getAttrs().getAttackSpeed(), skill.ordinal() + 1);
                helper.assertTrue(cv.getStateTimer(ID.T.MountSkillCD3) == cd && cv.getStateTimer(ID.T.MountSkillCD4) == cd,
                        "Both aircraft timers must use the selected weapon's formula");
                S2CMountSkillPacket sync = S2CMountSkillPacket.of(cv);
                helper.assertTrue(sync.planesLight() == cv.getNumAircraftLight() && sync.airHeavy() == cd,
                        "Sync captures aircraft and manual CD");
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountSkillCodecRoundTripsAndRejectsMalformedWire(GameTestHelper helper) {
        for (Skill skill : Skill.values()) {
            var request = new C2SMountSkillPacket(skill, new C2SMountSkillPacket.EntityTarget(15));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                request.encode(buf);
                helper.assertTrue(C2SMountSkillPacket.decode(buf).equals(request), "Entity wire roundtrip");
            } finally {
                buf.release();
            }
        }
        var block = new C2SMountSkillPacket(Skill.HEAVY, new C2SMountSkillPacket.BlockTarget(new BlockPos(2, -12, 3)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            block.encode(buf);
            helper.assertTrue(block.equals(C2SMountSkillPacket.decode(buf)), "Negative block wire roundtrip");
            buf.clear();
            var sync = new S2CMountSkillPacket(5, 1, 2, 3, 3, 4, 5);
            sync.encode(buf);
            helper.assertTrue(sync.equals(S2CMountSkillPacket.decode(buf)), "Sync wire roundtrip");
        } finally {
            buf.release();
        }
        byte[][] invalid = { {}, {0, 0, 0}, {4, 0, 0, 0, 0, 1}, {0, 2, 0, 0, 0, 1},
                {0, 0, -1, -1, -1, -1}, {0, 0, 0, 0, 0, 1, 0} };
        for (byte[] bytes : invalid) {
            FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            boolean rejected = false;
            try {
                C2SMountSkillPacket.decode(bad);
            } catch (DecoderException expected) {
                rejected = true;
            } finally {
                bad.release();
            }
            helper.assertTrue(rejected, "Malformed wire must be rejected before execution");
        }
        helper.succeed();
    }
}
