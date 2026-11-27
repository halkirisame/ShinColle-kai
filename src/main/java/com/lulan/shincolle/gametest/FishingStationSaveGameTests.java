package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipTransitionReason;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.item.SoulTalismanItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FishingStationSaveGameTests {
    private FishingStationSaveGameTests() { }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_station_death")
    public static void deathEggRetainsFishingStationUntilGuiOff(GameTestHelper helper) {
        deathRoundTrip(helper, true, 49310);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_guard_death")
    public static void ordinaryGuardSurvivesFishingDeathEggAndGuiOff(GameTestHelper helper) {
        deathRoundTrip(helper, false, 49311);
    }

    private static void deathRoundTrip(GameTestHelper helper, boolean station, int id) {
        FishingStationGameTests.fixture(helper, id, (context, ship) -> {
            BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
            if (station) FishingStationGameTests.task(context, ship, 2);
            FishingStationGameTests.order(context, spot, PointerItem.MODE_SINGLE, true, false);
            if (!station) FishingStationGameTests.task(context, ship, 2);
            assertGuard(helper, ship, spot, station, "Accepted guard before death");
            int uid = ship.getShipUID();
            GameTestEntities.onCompletion(helper, () -> ServerDataManager.removeShipData(uid), "death ship registry");
            helper.assertTrue(ConfigHandler.enableTask[1] && ship.getStateMinor(ID.M.Task) == 2
                            && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                            && context.player().getUUID().equals(ship.getOwnerUUID())
                            && ship.getPlayerUID() == context.capa().getPlayerUID(),
                    "Fixture: enabled fishing, fuel and original owner before death");
            ship.setStateFlag(ID.F.CanDrop, true);
            ship.setInvulnerable(false);
            helper.assertTrue(ship.getStateFlag(ID.F.CanDrop)
                            && context.entities().allowDamage(ship).hurt(ship.damageSources().magic(), ship.getMaxHealth() * 4F)
                            && ship.isDeadOrDying(),
                    "Fixture: lethal damage starts a death that may drop an egg");
            ship.deathTime = Math.max(0, ConfigHandler.deathTime() - 1);
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(ship.isRemoved(), "Natural death tick must remove the source ship");
                List<BasicEntityItem> eggs = context.level().getEntitiesOfClass(BasicEntityItem.class,
                        new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(4D),
                        item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
                helper.assertTrue(eggs.size() == 1, "Death must leave exactly one saved egg: " + eggs.size());
                BasicEntityItem dropped = context.entities().add(eggs.get(0));
                ItemStack egg = dropped.getEntityItem().copy();
                CompoundTag data = egg.getTag();
                helper.assertTrue(data != null && data.getInt("AiDataVersion") == 2
                                && data.contains("StateMinor") && data.getBoolean("FishingStation") == station,
                        "Actual death egg must retain version 2 and the accepted fishing-station mark");
                helper.assertTrue(!data.contains("GuardDimension") && !data.contains("ReleaseGuardOnArrival"),
                        "Death eggs must not gain unrelated guard persistence keys");
                dropped.discard();
                BasicEntityShip revived = hatch(context, egg, new BlockPos(4, 2, 6));
                helper.assertTrue(revived.getShipUID() == uid
                                && context.player().getUUID().equals(revived.getOwnerUUID())
                                && revived.getPlayerUID() == context.capa().getPlayerUID()
                                && revived.getStateMinor(ID.M.Task) == 2
                                && revived.getStateMinor(ID.M.NumGrudge) > 0
                                && !revived.getStateFlag(ID.F.NoFuel),
                        "Revival must retain the original ship, owner, task and fuel");
                assertGuard(helper, revived, spot, station, "Revived guard before GUI OFF");
                FishingStationGameTests.task(context, revived, 0);
                revived.setNoAi(false);
                helper.runAtTickTime(38, () -> {
                    if (station) {
                        helper.assertTrue(revived.getCommandState().movement() instanceof MovementOrder.Follow
                                        && revived.getLastCommandStateChange().issuer() instanceof CommandIssuer.Ship issuer
                                        && issuer.reason() == ShipTransitionReason.FISHING_DISABLED,
                                "Natural tick after GUI OFF must end the revived fishing station for FISHING_DISABLED");
                    } else {
                        assertGuard(helper, revived, spot, false, "Ordinary revived guard after GUI OFF");
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_old_eggs")
    public static void oldAndMissingStationDeathEggsNeverInferFishingOwnership(GameTestHelper helper) {
        FishingStationGameTests.fixture(helper, 49312, (context, source) -> {
            FishingStationGameTests.task(context, source, 2);
            BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
            FishingStationGameTests.order(context, spot, PointerItem.MODE_SINGLE, true, false);
            CompoundTag base = new CompoundTag();
            CapaShipSavedValues.saveNBTData(base, source);
            base.putInt("ShipClass", source.getShipClass());
            base.putString("owner", context.player().getUUID().toString());
            int baseUid = source.getShipUID();
            source.discard();
            for (int version = 0; version <= 2; version++) {
                CompoundTag old = base.copy();
                if (version == 0) old.remove("AiDataVersion");
                else old.putInt("AiDataVersion", version);
                if (version < 2) old.putBoolean("FishingStation", true);
                else old.remove("FishingStation");
                int uid = baseUid + version + 1;
                int[] minors = old.getIntArray("StateMinor");
                minors[ID.M.ShipUID] = uid;
                old.putIntArray("StateMinor", minors);
                ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
                egg.setTag(old);
                BasicEntityShip revived = hatch(context, egg, new BlockPos(2 + version * 2, 2, 6));
                GameTestEntities.onCompletion(helper, () -> ServerDataManager.removeShipData(uid), "old egg registry");
                helper.assertTrue(revived.getStateMinor(ID.M.Task) == 2, "Old egg must still select fishing");
                assertGuard(helper, revived, spot, false, "Old or missing-mark death egg");
                FishingStationGameTests.task(context, revived, 0);
                revived.setNoAi(false);
                helper.runAtTickTime(35, () -> assertGuard(helper, revived, spot, false,
                        "Old death egg after GUI OFF"));
            }
            helper.runAtTickTime(36, helper::succeed);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_normal_boundary")
    public static void normalSaveRetainsStationBeforeFirstTickAndClearsItOnReloadAndEnd(GameTestHelper helper) {
        boundaryRoundTrip(helper, true, 49313);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_common_boundary")
    public static void commonSaveRetainsStationBeforeFirstTickAndClearsItOnReloadAndEnd(GameTestHelper helper) {
        boundaryRoundTrip(helper, false, 49314);
    }

    private static void boundaryRoundTrip(GameTestHelper helper, boolean normal, int id) {
        FishingStationGameTests.fixture(helper, id, (context, source) -> {
            FishingStationGameTests.task(context, source, 2);
            BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
            FishingStationGameTests.order(context, spot, PointerItem.MODE_GROUP, true, true);
            assertGuard(helper, source, spot, true, "Accepted source guard");
            CompoundTag saved = save(source, normal);
            if (normal) saved.putString("GuardDimension", "minecraft:the_nether");
            BasicEntityShip restored = context.entities().add(ModEntities.BB_KONGOU.get().create(context.level()));
            helper.assertTrue(restored != null, "Fixture: create an uninitialized ship");
            load(restored, saved, normal);
            helper.assertTrue(restored.tickCount == 0, "Fixture: no first tick has occurred");
            CompoundTag immediately = save(restored, normal);
            helper.assertTrue(immediately.getBoolean("FishingStation") && immediately.getInt("AiDataVersion") == 2,
                    "Load then save before initialization must retain the station at current version 2");
            assertGuard(helper, restored, spot, true, "Loaded guard");
            if (normal) {
                var guard = (MovementOrder.GuardPosition) restored.getCommandState().movement();
                helper.assertTrue(guard.dimension().equals(new DimensionKey("minecraft", "the_nether"))
                                && guard.releaseOnArrival(),
                        "Normal load must defer command initialization until dimension and arrival flags are loaded");
            }
            CompoundTag missing = saved.copy();
            missing.remove("FishingStation");
            int[] minors = missing.getIntArray("StateMinor");
            minors[ID.M.GuardX] += 1;
            missing.putIntArray("StateMinor", minors);
            load(restored, missing, normal);
            helper.assertTrue(!save(restored, normal).getBoolean("FishingStation"),
                    "Reload on the same instance must not reuse its old loaded mark");
            assertGuard(helper, restored, spot.offset(1, 0, 0), false, "Reloaded unmarked guard");
            load(restored, saved, normal);
            assertGuard(helper, restored, spot, true, "Marked guard loaded again");
            restored.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.HELPER_COMMAND),
                    new CommandStateOp.EndMovement());
            helper.assertTrue(!save(restored, normal).getBoolean("FishingStation")
                            && restored.getCommandState().movement() instanceof MovementOrder.Follow,
                    "EndMovement must save no stale station mark");
            helper.succeed();
        });
    }

    private static CompoundTag save(BasicEntityShip ship, boolean normal) {
        CompoundTag data = new CompoundTag();
        if (normal) ship.addAdditionalSaveData(data);
        else CapaShipSavedValues.saveNBTData(data, ship);
        return data;
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_safari_save")
    public static void safariNetEntityDataRetainsStationUntilGuiOff(GameTestHelper helper) {
        captureRoundTrip(helper, false, 49315);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_talisman_save")
    public static void soulTalismanEntityDataRetainsStationUntilGuiOff(GameTestHelper helper) {
        captureRoundTrip(helper, true, 49316);
    }

    private static void captureRoundTrip(GameTestHelper helper, boolean talisman, int id) {
        FishingStationGameTests.fixture(helper, id, (context, source) -> {
            FishingStationGameTests.task(context, source, 2);
            BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
            FishingStationGameTests.order(context, spot, PointerItem.MODE_SINGLE, true, false);
            assertGuard(helper, source, spot, true, "Station before capture");
            ItemStack pointer = context.player().getMainHandItem().copy();
            context.player().setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(talisman ? ModItems.SOUL_TALISMAN.get() : ModItems.SAFARI_NET.get()));
            source.mobInteract(context.player(), InteractionHand.MAIN_HAND);
            helper.assertTrue(source.isRemoved(), "Item capture must remove the source after saving it");
            ItemStack item = context.player().getMainHandItem();
            CompoundTag stored = talisman
                    ? item.getOrCreateTag().getCompound("SoulTalisman").getList("Ships", Tag.TAG_COMPOUND).getCompound(0)
                    : item.getOrCreateTag().getCompound("SafariShip");
            helper.assertTrue(stored.getCompound("EntityData").getBoolean("FishingStation"),
                    "Captured EntityData must retain the station after location filtering");
            if (talisman) SoulTalismanItem.setMode(item, SoulTalismanItem.MODE_RELEASE_NEXT);
            BlockPos spawn = helper.absolutePos(new BlockPos(4, 2, 6));
            BlockPos floor = spawn.below();
            item.getItem().useOn(new UseOnContext(context.level(), context.player(), InteractionHand.MAIN_HAND,
                    item, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
            List<BasicEntityShip> ships = context.level().getEntitiesOfClass(BasicEntityShip.class, new AABB(spawn));
            helper.assertTrue(ships.size() == 1, "Captured item release must spawn exactly one ship");
            BasicEntityShip released = context.entities().add(ships.get(0));
            assertGuard(helper, released, spot, true, "Released station before OFF");
            context.player().setItemInHand(InteractionHand.MAIN_HAND, pointer);
            FishingStationGameTests.task(context, released, 0);
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(released.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Captured and released station must still end on a natural tick after OFF");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_restore_from")
    public static void entityRestoreFromRetainsStationAndDropsItAfterEndMovement(GameTestHelper helper) {
        FishingStationGameTests.fixture(helper, 49317, (context, source) -> {
            FishingStationGameTests.task(context, source, 2);
            BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
            FishingStationGameTests.order(context, spot, PointerItem.MODE_SINGLE, true, false);
            BasicEntityShip copy = context.entities().add(ModEntities.BB_KONGOU.get().create(context.level()));
            helper.assertTrue(copy != null, "Fixture: clone entity exists");
            copy.restoreFrom(source);
            helper.assertTrue(copy.saveWithoutId(new CompoundTag()).getBoolean("FishingStation"),
                    "Entity.restoreFrom must retain the root station mark");
            assertGuard(helper, copy, spot, true, "Cloned station");
            copy.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.HELPER_COMMAND),
                    new CommandStateOp.EndMovement());
            helper.assertTrue(!copy.saveWithoutId(new CompoundTag()).getBoolean("FishingStation"),
                    "A cloned command that ends must not save its original station mark");
            helper.succeed();
        });
    }

    private static void load(BasicEntityShip ship, CompoundTag data, boolean normal) {
        if (normal) ship.readAdditionalSaveData(data);
        else CapaShipSavedValues.loadNBTData(data, ship);
    }

    private static BasicEntityShip hatch(PointerSingleModeGameTests.TestContext context, ItemStack egg,
                                         BlockPos relativeSpawn) {
        GameTestHelper helper = context.helper();
        BlockPos spawn = helper.absolutePos(relativeSpawn);
        BlockPos floor = spawn.below();
        boolean creative = context.player().getAbilities().instabuild;
        context.player().getAbilities().instabuild = true;
        InteractionResult result;
        try {
            helper.assertTrue(context.player().getAbilities().instabuild,
                    "Fixture: creative mode satisfies the saved-egg experience cost");
            result = egg.getItem().useOn(new UseOnContext(context.level(), context.player(), InteractionHand.MAIN_HAND,
                    egg, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        } finally {
            context.player().getAbilities().instabuild = creative;
        }
        helper.assertTrue(result == InteractionResult.CONSUME, "Saved egg use must succeed: " + result);
        List<BasicEntityShip> ships = context.level().getEntitiesOfClass(BasicEntityShip.class, new AABB(spawn));
        helper.assertTrue(ships.size() == 1, "Saved egg must spawn exactly one ship: " + ships.size());
        BasicEntityShip revived = context.entities().add(ships.get(0));
        revived.setNoAi(true);
        revived.setInvulnerable(true);
        return revived;
    }

    private static void assertGuard(GameTestHelper helper, BasicEntityShip ship, BlockPos spot,
                                     boolean station, String label) {
        helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition guard
                        && guard.position().equals(new CommandPos(spot.getX(), spot.getY(), spot.getZ()))
                        && guard.fishingStation() == station && !ship.getStateFlag(ID.F.CanFollow),
                label + ": expected same guarded coordinates and station=" + station + ", got " + ship.getCommandState());
    }
}
