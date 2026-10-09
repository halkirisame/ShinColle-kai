package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.combat.CombatLoadout;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The fire control goal fires only the weapons of the attack goals a ship registers. The guard
 * goal used to fire on the move by the type flags alone, so a flag without its attack goal would
 * mean a guarding ship fires less under NEW. Every ship type is checked for such a flag.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatLoadoutGameTests {
    private CombatLoadoutGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void everyWeaponAGuardingShipFiredIsInItsLoadout(GameTestHelper helper) {
        List<String> gaps = new ArrayList<>();
        int checked = 0;
        try {
            Method setAIList = BasicEntityShip.class.getDeclaredMethod("setAIList");
            setAIList.setAccessible(true);
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
                if (key == null || !Reference.MOD_ID.equals(key.getNamespace())) continue;
                Entity entity = type.create(helper.getLevel());
                if (!(entity instanceof BasicEntityShip ship)) {
                    if (entity != null) entity.discard();
                    continue;
                }
                setAIList.invoke(ship);
                CombatLoadout loadout = ship.shipCombatState().loadout();
                if (ship.getStateFlag(ID.F.AtkType_Light) && !loadout.has(WeaponChannel.LIGHT)) {
                    gaps.add(key.getPath() + ":LIGHT");
                }
                if (ship.getStateFlag(ID.F.AtkType_Heavy) && !loadout.has(WeaponChannel.HEAVY)) {
                    gaps.add(key.getPath() + ":HEAVY");
                }
                if (ship instanceof IShipAircraftAttack
                        && (ship.getStateFlag(ID.F.UseAirLight) || ship.getStateFlag(ID.F.UseAirHeavy))
                        && !loadout.has(WeaponChannel.AIR)) {
                    gaps.add(key.getPath() + ":AIR");
                }
                checked++;
                ship.discard();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        LogHelper.info("Combat loadout: " + checked + " ship types checked, gaps " + gaps);
        helper.assertTrue(checked > 0, "no ship types found");
        helper.assertTrue(gaps.isEmpty(), "Type flags without their attack goal: " + gaps);
        helper.succeed();
    }
}
