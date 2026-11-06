package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttrs;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeamDefenseGameTests {
    private BeamDefenseGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ordinaryBeamAppliesFriendlyDefenseOnce(GameTestHelper helper) {
        check(helper, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ordinaryBeamAppliesHostileDefenseOnce(GameTestHelper helper) {
        check(helper, false);
    }

    private static void check(GameTestHelper helper, boolean friendly) {
        Vec3 point = new Vec3(4D, 4D, 2D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ally = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                BasicEntityShipHostile enemy = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                helper.assertTrue(ally != null && enemy != null, "Both ship fixtures must exist.");
                ally.setPlayerUID(98211);
                ally.setStateMinor(ID.M.NumGrudge, 100);
                ally.setStateFlag(ID.F.NoFuel, false);
                Mob target = friendly ? ally : enemy;
                Mob host = friendly ? enemy : ally;
                IShipAttrs attrs = (IShipAttrs) target;
                attrs.getAttrs().setAttrsBuffed(ID.Attrs.DEF, 0.5F);
                attrs.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
                target.setHealth(1000F);
                target.moveTo(helper.absoluteVec(point));
                host.moveTo(helper.absoluteVec(new Vec3(0D, 4D, 2D)));
                helper.getLevel().addFreshEntity(host);
                helper.getLevel().addFreshEntity(target);
                helper.assertTrue(attrs.getAttrs().getDefense() == 0.5F, "Defense fixture must retain its value.");
                target.getRandom().setSeed(4123L);
                helper.assertTrue(target.hurt(target.damageSources().mobAttack(host), 100F), "Direct reference hit must land.");
                float expected = 1000F - target.getHealth();
                helper.assertTrue(expected > 0F, "Reference hurt must deal real damage.");
                target.setHealth(1000F);
                target.invulnerableTime = 0;
                target.getRandom().setSeed(4123L);
                EntityProjectileBeam beam = entities.add(ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel()));
                beam.initBeam((com.lulan.shincolle.entity.IShipAttackBase) host, 1D, 0D, 0D, 100F);
                beam.setPos(target.position().subtract(4D, 0D, 0D));
                beam.tick();
                float actual = 1000F - target.getHealth();
                helper.assertTrue(Math.abs(expected - actual) < 0.001F,
                        "Beam must equal one seeded hurt defense pass: expected " + expected + ", got " + actual);
                helper.succeed();
            }
        }, point, new Vec3(0D, 4D, 2D));
    }
}
