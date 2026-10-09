package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardingTickParityGameTests {
    private GuardingTickParityGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void guardingCountersAdvanceEveryTick(GameTestHelper helper) {
        var ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        helper.assertTrue(ship != null, "Failed to construct the guarding ship");
        helper.assertTrue(new ShipGuardingGoal(ship).requiresUpdateEveryTick(),
                "Guarding goal must update attack and movement counters on every tick");
        helper.succeed();
    }
}
