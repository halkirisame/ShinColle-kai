package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import com.lulan.shincolle.utility.BuffHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipClassRawBonusGameTests {
    private ShipClassRawBonusGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_ship_class_raw_bonus")
    public static void shipClassBonusesSurviveFullAttributeRecalculation(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verify(helper), new Vec3(2D, 4D, 2D));
    }

    private static void verify(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long savedTime = level.getDayTime();
        List<String> failures = new ArrayList<>();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BonusCase[] cases = {
                    new BonusCase(ModEntities.CL_TENRYUU.get(), true,
                            new int[]{ID.Attrs.CRI, ID.Attrs.DODGE}, new float[]{0.15F, 0.15F}),
                    new BonusCase(ModEntities.CL_TATSUTA.get(), true,
                            new int[]{ID.Attrs.CRI, ID.Attrs.DODGE}, new float[]{0.15F, 0.15F}),
                    new BonusCase(ModEntities.CA_RI.get(), true,
                            new int[]{ID.Attrs.CRI, ID.Attrs.MISS}, new float[]{0.15F, 0.15F}),
                    new BonusCase(ModEntities.CA_NE.get(), true,
                            new int[]{ID.Attrs.CRI}, new float[]{0.3F}),
                    new BonusCase(ModEntities.CA_HIME.get(), true,
                            new int[]{ID.Attrs.CRI, ID.Attrs.THIT}, new float[]{0.1F, 0.2F}),
                    new BonusCase(ModEntities.DESTROYER_HIBIKI.get(), false,
                            new int[]{ID.Attrs.DODGE}, new float[]{0.3F}),
                    new BonusCase(ModEntities.BB_RE.get(), false,
                            new int[]{ID.Attrs.DHIT, ID.Attrs.THIT}, new float[]{0.1F, 0.1F})
            };
            for (BonusCase entry : cases) {
                BasicEntityShip ship = entities.add(entry.type().create(level));
                helper.assertTrue(ship != null, "Could not create " + entry.type());
                ship.moveTo(helper.absoluteVec(new Vec3(2D, 4D, 2D)));
                ship.setNoAi(true);
                ship.setNoGravity(true);
                ship.setInvulnerable(true);
                ship.setStateMinor(ID.M.ShipLevel, 1);
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setMorale(3000);
                ship.removeAllEffects();
                for (int slot = 0; slot < ship.getCapaShipInventory().getSlots(); slot++) {
                    ship.getCapaShipInventory().setStackInSlot(slot, ItemStack.EMPTY);
                }
                helper.assertTrue(level.addFreshEntity(ship), "Could not add " + entry.type());
                GameTestEntities.assertRegistered(helper, ship);
                helper.assertTrue(ship.getLevel() == 1 && ship.getMorale() == 3000
                                && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                                && ship.getActiveEffects().isEmpty() && ship.getOwnerUUID() == null,
                        "The neutral fixture values must be applied");

                AttrsAdv baseline = new AttrsAdv(ship.getShipClass());
                BuffHelper.updateAttrsRaw(baseline, ship.getShipClass(), 1);
                float[] day = sample(helper, ship, entry, 6000L, true);
                float[] night = sample(helper, ship, entry, 18000L, false);
                float[] dayAgain = sample(helper, ship, entry, 6000L, true);
                for (int i = 0; i < entry.attributes().length; i++) {
                    int attr = entry.attributes()[i];
                    float expected = entry.bonuses()[i];
                    float base = baseline.getAttrsRaw(attr);
                    compare(failures, entry, attr, "day", day[i], base + (entry.nightOnly() ? 0F : expected));
                    compare(failures, entry, attr, "night", night[i], base + expected);
                    compare(failures, entry, attr, "day again", dayAgain[i], day[i]);
                }
                ship.discard();
            }
        } finally {
            level.setDayTime(savedTime);
            level.updateSkyBrightness();
        }
        helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    private static float[] sample(GameTestHelper helper, BasicEntityShip ship, BonusCase entry, long time,
                                  boolean day) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(time);
        level.updateSkyBrightness();
        helper.assertTrue(level.getDayTime() == time && level.isDay() == day, "Day/night fixture was ignored");
        ship.calcShipAttributes(31, false);
        float[] values = new float[entry.attributes().length];
        for (int i = 0; i < values.length; i++) {
            int attr = entry.attributes()[i];
            AttrsAdv attrs = (AttrsAdv) ship.getAttrs();
            float neutral = attr == ID.Attrs.DODGE ? 0F : 1F;
            helper.assertTrue(attrs.getAttrsEquip(attr) == 0F && attrs.getAttrsPotion(attr) == 0F
                            && attrs.getAttrsMorale(attr) == neutral && attrs.getAttrsFormation(attr) == neutral,
                    entry.type() + " has a non-neutral composition layer for " + attr);
            values[i] = attrs.getAttrsBuffed(attr);
        }
        return values;
    }

    private static void compare(List<String> failures, BonusCase entry, int attr, String when, float actual,
                                float expected) {
        if (Math.abs(actual - expected) > 1e-5F) {
            failures.add(entry.type() + " " + attr + " " + when + ": " + actual + " expected " + expected);
        }
    }

    private record BonusCase(EntityType<? extends BasicEntityShip> type, boolean nightOnly, int[] attributes,
                             float[] bonuses) {
    }
}
