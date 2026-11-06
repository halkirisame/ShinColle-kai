package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompassChunkGameTests {
    private CompassChunkGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_compass_chunks")
    public static void compassUsesConfiguredRangeAndReleasesPreviousChunks(GameTestHelper helper) {
        int mode = ConfigHandler.chunkLoaderMode();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = fixture(helper, entities);
            ConfigHandler.COMMON.chunkLoaderMode.set(1);
            ship.updateChunkLoader();
            assertRange(helper, ship, 0);
            ConfigHandler.COMMON.chunkLoaderMode.set(2);
            ship.updateChunkLoader();
            assertRange(helper, ship, 1);
            ship.setPos(ship.getX() + 16D, ship.getY(), ship.getZ());
            ship.updateChunkLoader();
            assertRange(helper, ship, 1);
            ship.setStateMinor(ID.M.LevelChunkLoader, 0);
            ship.updateChunkLoader();
            helper.assertTrue(tickets(helper, ship).isEmpty(), "Removing the compass must release every chunk.");
            ship.setStateMinor(ID.M.LevelChunkLoader, 3);
            ship.updateChunkLoader();
            assertRange(helper, ship, 1);
            ConfigHandler.COMMON.chunkLoaderMode.set(0);
            ship.updateChunkLoader();
            helper.assertTrue(tickets(helper, ship).isEmpty(), "Mode zero must release every chunk.");
            ConfigHandler.COMMON.chunkLoaderMode.set(2);
            ship.updateChunkLoader();
            ship.setStateFlag(ID.F.NoFuel, true);
            ship.updateChunkLoader();
            helper.assertTrue(tickets(helper, ship).isEmpty(), "Losing fuel must release every chunk.");
            checkStartupCallback(helper);
        } finally {
            ConfigHandler.COMMON.chunkLoaderMode.set(mode);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_compass_lifecycle")
    public static void compassReleasesTicketsAtEveryRemovalBoundary(GameTestHelper helper) {
        int mode = ConfigHandler.chunkLoaderMode();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ConfigHandler.COMMON.chunkLoaderMode.set(2);
            for (int boundary = 0; boundary < 4; boundary++) {
                BasicEntityShip ship = fixture(helper, entities);
                ship.updateChunkLoader();
                assertRange(helper, ship, 1);
                switch (boundary) {
                    case 0 -> ship.remove(Entity.RemovalReason.DISCARDED);
                    case 1 -> ship.onRemovedFromWorld();
                    case 2 -> {
                        try {
                            Method method = BasicEntityShip.class.getDeclaredMethod("removeAfterChangingDimensions");
                            method.setAccessible(true);
                            method.invoke(ship);
                        } catch (ReflectiveOperationException error) {
                            throw new GameTestAssertException("Dimension boundary failed: " + error);
                        }
                    }
                    default -> ship.die(ship.damageSources().generic());
                }
                helper.assertTrue(tickets(helper, ship).isEmpty(), "Removal boundary " + boundary + " leaked tickets.");
            }
        } finally {
            ConfigHandler.COMMON.chunkLoaderMode.set(mode);
        }
        helper.succeed();
    }

    private static BasicEntityShip fixture(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Compass fixture ship must exist.");
        ship.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2D, 4D, 2D)));
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateMinor(ID.M.LevelChunkLoader, 1);
        helper.assertTrue(ship.getStateMinor(ID.M.LevelChunkLoader) == 1 && !ship.getStateFlag(ID.F.NoFuel),
                "Compass fixture must have equipment and fuel.");
        return ship;
    }

    private static Set<Long> tickets(GameTestHelper helper, BasicEntityShip ship) {
        ForcedChunksSavedData data = helper.getLevel().getDataStorage().computeIfAbsent(
                ForcedChunksSavedData::load, ForcedChunksSavedData::new, "chunks");
        LongSet chunks;
        try {
            var constructor = ForgeChunkManager.TicketOwner.class.getDeclaredConstructor(String.class, Comparable.class);
            constructor.setAccessible(true);
            chunks = data.getEntityForcedChunks().getTickingChunks().get(constructor.newInstance(Reference.MOD_ID, ship.getUUID()));
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect the ship's Forge ticket: " + error);
        }
        Set<Long> result = new HashSet<>();
        if (chunks != null) chunks.forEach((long chunk) -> result.add(chunk));
        return result;
    }

    private static void assertRange(GameTestHelper helper, BasicEntityShip ship, int radius) {
        Set<Long> expected = new HashSet<>();
        ChunkPos center = ship.chunkPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) expected.add(ChunkPos.asLong(center.x + x, center.z + z));
        }
        helper.assertTrue(tickets(helper, ship).equals(expected), "Forced chunks must equal the configured range.");
    }

    @SuppressWarnings("unchecked")
    private static void checkStartupCallback(GameTestHelper helper) {
        try {
            var field = ForgeChunkManager.class.getDeclaredField("callbacks");
            field.setAccessible(true);
            var callbacks = (java.util.Map<String, ForgeChunkManager.LoadingValidationCallback>) field.get(null);
            var callback = callbacks.get(Reference.MOD_ID);
            helper.assertTrue(callback != null, "Common setup must register the forced-chunk validation callback.");
            var data = new ForcedChunksSavedData();
            var uuid = java.util.UUID.randomUUID();
            var chunk = new net.minecraft.nbt.CompoundTag();
            chunk.putLong("Chunk", ChunkPos.asLong(1, 2));
            var owners = new net.minecraft.nbt.ListTag();
            owners.add(net.minecraft.nbt.NbtUtils.createUUID(uuid));
            chunk.put("Entities", owners);
            chunk.put("TickingEntities", owners.copy());
            var blocks = new net.minecraft.nbt.ListTag();
            blocks.add(net.minecraft.nbt.NbtUtils.writeBlockPos(net.minecraft.core.BlockPos.ZERO));
            chunk.put("Blocks", blocks);
            var chunks = new net.minecraft.nbt.ListTag();
            chunks.add(chunk);
            var mod = new net.minecraft.nbt.CompoundTag();
            mod.putString("Mod", Reference.MOD_ID);
            mod.put("ModForced", chunks);
            var mods = new net.minecraft.nbt.ListTag();
            mods.add(mod);
            var other = mod.copy();
            other.putString("Mod", "minecraft");
            mods.add(other);
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.put("ForgeForced", mods);
            ForgeChunkManager.readForgeForcedChunks(tag, data.getBlockForcedChunks(), data.getEntityForcedChunks());
            int blockOwners = data.getBlockForcedChunks().getChunks().size();
            helper.assertTrue(data.getEntityForcedChunks().getChunks().size() == 2 && blockOwners == 2,
                    "Startup fixture must contain this MOD and another MOD's tickets.");
            var constructor = ForgeChunkManager.TicketHelper.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var pair = com.mojang.datafixers.util.Pair.of(it.unimi.dsi.fastutil.longs.LongSets.singleton(1L),
                    it.unimi.dsi.fastutil.longs.LongSets.singleton(1L));
            var tickets = (ForgeChunkManager.TicketHelper) constructor.newInstance(data, Reference.MOD_ID,
                    java.util.Map.of(), java.util.Map.of(uuid, pair));
            callback.validateTickets(helper.getLevel(), tickets);
            helper.assertTrue(data.getEntityForcedChunks().getChunks().size() == 1
                            && data.getEntityForcedChunks().getTickingChunks().size() == 1
                            && data.getBlockForcedChunks().getChunks().size() == blockOwners,
                    "Startup cleanup must remove only this MOD's normal and ticking entity tickets.");
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect the startup ticket callback: " + error);
        }
    }
}
