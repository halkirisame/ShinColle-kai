package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Synchronous cargo checks run only while the fixture's chunk is ticking. */
record CraneResourceFixture(GameTestHelper helper, TileEntityCrane crane, ChestBlockEntity chest, BasicEntityShip ship) {
    static final Vec3 POSITION = new Vec3(3.5D, 3D, 3.5D);

    static CraneResourceFixture open(GameTestHelper helper, GameTestEntities entities, int pumps) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 5, 3));
        helper.getLevel().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(pos.east(2), Blocks.CHEST.defaultBlockState(), 3);
        TileEntityCrane crane = (TileEntityCrane) helper.getLevel().getBlockEntity(pos);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos.east(2));
        UUID owner = UUID.randomUUID();
        CompoundTag tag = crane.saveWithFullMetadata();
        tag.putUUID("OwnerUUID", owner);
        crane.load(tag);
        crane.setPairedChest(pos.east(2));
        crane.setActive(true);
        crane.setEnabLoad(false);
        crane.setEnabUnload(false);
        crane.setCraneMode(24);
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Resource fixture must create its ship.");
        ship.setOwnerUUID(owner);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setInvulnerable(true);
        Vec3 absolute = helper.absoluteVec(POSITION);
        ship.moveTo(absolute.x, absolute.y, absolute.z);
        ship.setGuardedPos(pos.getX(), pos.getY(), pos.getZ(), helper.getLevel().dimension(), 1);
        ship.setStateFlag(ID.F.CanFollow, false);
        ship.setStateTimer(ID.T.CrandDelay, 0);
        ship.setStateMinor(ID.M.CraneState, 2);
        for (int slot = 0; slot < pumps; slot++) {
            ItemStack pump = new ItemStack(ModItems.EQUIP_DRUM.get());
            BasicEquip.setEquipMeta(pump, 1);
            ship.getCapaShipInventory().setStackInSlot(slot, pump);
            helper.assertTrue(BasicEquip.getEquipMeta(ship.getCapaShipInventory().getStackInSlot(slot)) == 1,
                    "Fixture must retain every liquid pump.");
        }
        helper.getLevel().addFreshEntity(ship);
        helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 2 && ship.hasGuardDestination()
                        && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                "Resource fixture must retain its phase, guard destination and fuel.");
        return new CraneResourceFixture(helper, crane, chest, ship);
    }

    void tick(int count) {
        for (int tick = 0; tick < count; tick++) {
            TileEntityCrane.serverTick(helper.getLevel(), crane.getBlockPos(), crane.getBlockState(), crane);
        }
    }

    void working() {
        ship.setStateTimer(ID.T.CrandDelay, 0);
        ship.setStateMinor(ID.M.CraneState, 2);
        helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 2, "Fixture must restart its work phase.");
    }
}
