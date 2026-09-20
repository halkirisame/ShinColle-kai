package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.SlotVolCore;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityVolCore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VolCoreGameTests {

    private static final BlockPos CORE_POS = new BlockPos(2, 2, 2);

    private VolCoreGameTests() {
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void liquidRecoveryRequiresSubmersionCombatDelayAndRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileEntityVolCore tile = placeWorkingCore(helper);
        BlockPos wetPos = helper.absolutePos(CORE_POS.offset(1, 0, 0));
        BlockPos combatPos = helper.absolutePos(CORE_POS.offset(-1, 0, 0));
        level.setBlock(wetPos, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(combatPos, Blocks.WATER.defaultBlockState(), 3);

        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip wetShip = spawnShip(helper, entities, wetPos);
            BasicEntityShip combatShip = spawnShip(helper, entities, combatPos);
            combatShip.setCombatTick(100);
            BasicEntityShip dryShip = spawnShip(helper, entities, helper.absolutePos(CORE_POS.offset(0, 0, 3)));
            BasicEntityShip distantShip = spawnShip(helper, entities,
                    helper.absolutePos(CORE_POS.offset(7, 0, 0)));
            float wetHealth = wetShip.getHealth();
            float combatHealth = combatShip.getHealth();
            float dryHealth = dryShip.getHealth();
            float distantHealth = distantShip.getHealth();
            int wetMorale = wetShip.getMorale();
            int combatMorale = combatShip.getMorale();
            int dryMorale = dryShip.getMorale();
            int distantMorale = distantShip.getMorale();

            runServerTicks(level, tile, 48);

            assertTrue(wetShip.getHealth() > wetHealth, "Submerged in-range ship did not recover health");
            assertTrue(wetShip.getMorale() == wetMorale + 80,
                    "Submerged in-range ship did not recover 80 morale");
            assertUnchanged(combatShip, combatHealth, combatMorale, "Recently fighting submerged ship");
            assertUnchanged(dryShip, dryHealth, dryMorale, "Dry in-range ship");
            assertUnchanged(distantShip, distantHealth, distantMorale, "Out-of-range ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void dryCoreIgnitesAndDamagesNonShipLivingEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileEntityVolCore tile = placeWorkingCore(helper);

        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Cow cow = entities.add(EntityType.COW.create(level));
            if (cow == null) {
                throw new AssertionError("Failed to create cow");
            }
            cow.moveTo(helper.absoluteVec(new Vec3(4.5D, 2.0D, 2.5D)));
            cow.setNoGravity(true);
            if (!level.addFreshEntity(cow)) {
                throw new AssertionError("Failed to add cow");
            }
            float health = cow.getHealth();

            runServerTicks(level, tile, 48);

            assertTrue(cow.isOnFire(), "Dry Volcano Core did not ignite cow");
            assertTrue(cow.getHealth() < health, "Dry Volcano Core did not damage cow");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void fuelSlotAndGrudgeBlockPowerMatchLegacyRules(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);

        assertTrue(SlotVolCore.isValidFuel(new ItemStack(ModItems.GRUDGE.get())),
                "Grudge should be valid Volcano Core fuel");
        assertTrue(SlotVolCore.isValidFuel(new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get())),
                "Grudge Block should be valid Volcano Core fuel");
        assertTrue(!SlotVolCore.isValidFuel(new ItemStack(ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get())),
                "Grudge Heavy Block should not be valid Volcano Core fuel");
        assertTrue(!SlotVolCore.isValidFuel(new ItemStack(Items.COAL)),
                "Coal should not be valid Volcano Core fuel");
        assertTrue(!SlotVolCore.isValidFuel(new ItemStack(Items.LAVA_BUCKET)),
                "Lava Bucket should not be valid Volcano Core fuel");

        tile.getInventory().setStackInSlot(0, new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get()));
        runServerTicks(helper.getLevel(), tile, 32);

        int expectedPower = (int) tile.getFuelMagni() * 9;
        assertTrue(tile.getPowerRemained() == expectedPower,
                "Grudge Block expected " + expectedPower + " power but got " + tile.getPowerRemained());
        assertTrue(tile.getInventory().getStackInSlot(0).isEmpty(), "Consumed Grudge Block remained in slot");
        helper.succeed();
    }

    private static TileEntityVolCore placeWorkingCore(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        tile.setPowerRemained(1000);
        tile.setBtnActive(true);
        return tile;
    }

    private static TileEntityVolCore placeCore(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(CORE_POS);
        helper.getLevel().setBlock(pos, ModBlocks.VOL_CORE.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof TileEntityVolCore tile)) {
            throw new AssertionError("Volcano Core block entity was not created");
        }
        return tile;
    }

    private static BasicEntityShip spawnShip(GameTestHelper helper, GameTestEntities entities, BlockPos pos) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        if (ship == null) {
            throw new AssertionError("Failed to create friendly ship");
        }
        ship.moveTo(Vec3.atBottomCenterOf(pos));
        ship.setNoGravity(true);
        ship.setHealth(ship.getMaxHealth() * 0.5F);
        ship.setMorale(100);
        ship.tickCount = 200;
        ship.setCombatTick(0);
        if (!helper.getLevel().addFreshEntity(ship)) {
            throw new AssertionError("Failed to add friendly ship");
        }
        return ship;
    }

    private static void runServerTicks(ServerLevel level, TileEntityVolCore tile, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TileEntityVolCore.serverTick(level, tile.getBlockPos(), tile.getBlockState(), tile);
        }
    }

    private static void assertUnchanged(BasicEntityShip ship, float health, int morale, String context) {
        assertTrue(Math.abs(ship.getHealth() - health) < 0.001F, context + " unexpectedly recovered health");
        assertTrue(ship.getMorale() == morale, context + " unexpectedly recovered morale");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
