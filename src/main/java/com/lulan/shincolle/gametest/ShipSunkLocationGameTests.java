package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.command.CommandDispatchResult;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.ai.domain.command.CommandRejectReason;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.CacheDataShip;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.server.ShinWorldData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipSunkLocationGameTests {

    private static final int OWNER_UID = 815_240;

    private ShipSunkLocationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deathStoresEggLocationInShipCache(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int uid = 815_241;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(entities, level, uid, OWNER_UID,
                    helper.absoluteVec(new Vec3(3.5D, 64.6D, 3.5D)));
            ship.setStateFlag(ID.F.CanDrop, true);
            BlockPos deathPosition = BlockPos.containing(ship.position());

            ship.die(level.damageSources().generic());
            CacheDataShip deathRecord = requireRecord(uid);
            helper.assertTrue(deathRecord.sunk
                            && deathRecord.sunkDimension.equals(level.dimension())
                            && deathRecord.sunkX == deathPosition.getX()
                            && deathRecord.sunkY == deathPosition.getY()
                            && deathRecord.sunkZ == deathPosition.getZ(),
                    "Death did not save its initial sunk location");

            setDeathTime(ship, ConfigHandler.deathTime() - 1);
            invokeTickDeath(ship);
            BasicEntityItem egg = level.getEntitiesOfClass(BasicEntityItem.class,
                            new AABB(deathPosition).inflate(3D)).stream()
                    .filter(item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()))
                    .findFirst().orElse(null);
            helper.assertTrue(egg != null, "Death did not create a saved ship egg");
            BlockPos eggPosition = BlockPos.containing(egg.getX(), egg.getY(), egg.getZ());
            helper.assertTrue(!eggPosition.equals(deathPosition),
                    "Egg and death positions must differ to test the cache overwrite");
            CacheDataShip eggRecord = requireRecord(uid);
            helper.assertTrue(eggRecord.sunk
                            && eggRecord.sunkDimension.equals(level.dimension())
                            && eggRecord.sunkX == eggPosition.getX()
                            && eggRecord.sunkY == eggPosition.getY()
                            && eggRecord.sunkZ == eggPosition.getZ()
                            && eggRecord.isDead,
                    "Ship cache did not retain the egg position or removal state");
            helper.succeed();
        } finally {
            ServerDataManager.removeShipData(uid);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sunkLocationReachesOwnerInAnotherDimensionOnlyWhenAllowed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Nether level is unavailable");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000815251");
        CapturingOwner owner = new CapturingOwner(nether, new GameProfile(ownerId, "sunk_remote_owner"));
        GameRules.BooleanValue showDeaths = level.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES);
        boolean previousRule = showDeaths.get();
        int uid = 815_251;
        Map<UUID, ServerPlayer> onlinePlayers = playerMap(level.getServer().getPlayerList());
        ServerPlayer previousOwner = onlinePlayers.put(ownerId, owner);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            showDeaths.set(true, level.getServer());
            BasicEntityShip ship = createShip(entities, level, uid, OWNER_UID,
                    helper.absoluteVec(new Vec3(3.5D, 64D, 3.5D)));
            ship.setOwnerUUID(ownerId);
            ship.die(level.damageSources().generic());
            helper.assertTrue(owner.lastMessage != null
                            && owner.lastMessage.getContents() instanceof TranslatableContents translated
                            && translated.getKey().equals("chat.shincolle_kai.ship.sunk_location_dimension")
                            && translated.getArgs().length == 5
                            && translated.getArgs()[4].equals(level.dimension().location().toString()),
                    "Remote owner did not receive the sunk location and dimension");

            owner.lastMessage = null;
            onlinePlayers.remove(ownerId);
            BasicEntityShip offlineShip = createShip(entities, level, uid + 1, OWNER_UID,
                    helper.absoluteVec(new Vec3(5.5D, 64D, 3.5D)));
            offlineShip.setOwnerUUID(ownerId);
            offlineShip.die(level.damageSources().generic());
            helper.assertTrue(owner.lastMessage == null, "Offline owner received a location");

            onlinePlayers.put(ownerId, owner);
            showDeaths.set(false, level.getServer());
            BasicEntityShip silentShip = createShip(entities, level, uid + 2, OWNER_UID,
                    helper.absoluteVec(new Vec3(7.5D, 64D, 3.5D)));
            silentShip.setOwnerUUID(ownerId);
            silentShip.die(level.damageSources().generic());
            helper.assertTrue(owner.lastMessage == null, "Location ignored the death message gamerule");
            helper.succeed();
        } finally {
            showDeaths.set(previousRule, level.getServer());
            if (previousOwner == null) {
                onlinePlayers.remove(ownerId);
            } else {
                onlinePlayers.put(ownerId, previousOwner);
            }
            for (int recordUid = uid; recordUid <= uid + 2; recordUid++) {
                ServerDataManager.removeShipData(recordUid);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playerMap(PlayerList playerList) {
        try {
            Field field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>) field.get(playerList);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not register the test owner", failure);
        }
    }

    private static final class CapturingOwner extends FakePlayer {
        private Component lastMessage;

        private CapturingOwner(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.lastMessage = message;
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void respawningFromSavedEggClearsSunkLocation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int uid = 815_242;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = entities.add(ModEntities.BB_KONGOU.get().create(level));
            if (source == null) {
                throw new AssertionError("Could not create saved-egg source ship");
            }
            source.setPlayerUID(OWNER_UID);
            source.setShipUID(uid);
            source.moveTo(3.5D, 64D, 3.5D, 0F, 0F);
            CacheDataShip sunk = new CacheDataShip(source.getId(), level.dimension(), source.getShipClass(),
                    true, source.getX(), source.getY(), source.getZ(), new CompoundTag());
            sunk.setSunkLocation(level.dimension(), 10, 65, -4);
            ServerDataManager.setShipWorldData(uid, sunk);

            FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(
                    UUID.fromString("00000000-0000-0000-0000-000000815242"), "sunk_egg_owner"));
            owner.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> capa.setPlayerUID(OWNER_UID));
            ItemStack egg = savedEgg(source, owner.getUUID());
            BasicEntityShip restored = entities.add(ModEntities.BB_KONGOU.get().create(level));
            if (restored == null) {
                throw new AssertionError("Could not create restored ship");
            }
            invokeInitShipFromEgg(restored, egg, owner);
            ServerDataManager.updateShipID(restored);

            CacheDataShip restoredRecord = requireRecord(uid);
            helper.assertTrue(restored.getShipUID() == uid && restored.isAlive()
                            && !restoredRecord.sunk && restoredRecord.sunkDimension == null,
                    "Restored ship did not clear the saved sunk location");
            helper.succeed();
        } finally {
            ServerDataManager.removeShipData(uid);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unloadedShipDoesNotBecomeSunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int uid = 815_243;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(entities, level, uid, OWNER_UID,
                    helper.absoluteVec(new Vec3(3.5D, 64D, 3.5D)));
            ship.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

            CacheDataShip record = requireRecord(uid);
            helper.assertTrue(!record.sunk && record.isDead,
                    "Unloading an alive ship changed sunk state or isDead semantics");
            helper.succeed();
        } finally {
            ServerDataManager.removeShipData(uid);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sunkLocationSurvivesWorldDataRoundTripAndOldDataLoadsFalse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int uid = 815_244;
        CacheDataShip record = new CacheDataShip(25, level.dimension(), ID.ShipClass.BBKongou,
                true, 1, 2, 3, new CompoundTag());
        record.setSunkLocation(level.dimension(), 11, 70, -4);
        try {
            ServerDataManager.setShipWorldData(uid, record);
            ShinWorldData data = level.getDataStorage().computeIfAbsent(
                    ShinWorldData::load, ShinWorldData::new, ShinWorldData.SAVE_ID);
            CompoundTag saved = data.save(new CompoundTag());
            CompoundTag savedShip = shipTag(saved, uid);
            helper.assertTrue(savedShip.getBoolean("sSunk")
                            && savedShip.getString("sSunkDim").equals(level.dimension().location().toString())
                            && java.util.Arrays.equals(savedShip.getIntArray("sSunkPOS"),
                            new int[]{11, 70, -4}),
                    "Sunk location was not written to world data");

            CompoundTag loaded = ShinWorldData.load(saved.copy()).save(new CompoundTag());
            CompoundTag loadedShip = shipTag(loaded, uid);
            helper.assertTrue(loadedShip.getBoolean("sSunk")
                            && loadedShip.getString("sSunkDim").equals(level.dimension().location().toString())
                            && java.util.Arrays.equals(loadedShip.getIntArray("sSunkPOS"),
                            new int[]{11, 70, -4}),
                    "Sunk location did not survive save and load");

            CompoundTag oldSaved = saved.copy();
            CompoundTag oldShip = shipTag(oldSaved, uid);
            oldShip.remove("sSunk");
            oldShip.remove("sSunkDim");
            oldShip.remove("sSunkPOS");
            CompoundTag oldLoaded = ShinWorldData.load(oldSaved).save(new CompoundTag());
            helper.assertTrue(!shipTag(oldLoaded, uid).getBoolean("sSunk"),
                    "Old world data without sunk keys did not default to false");
            helper.succeed();
        } finally {
            ServerDataManager.removeShipData(uid);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newSingleSkipsSunkShipAndReportsItsLocation(GameTestHelper helper) {
        int firstUid = 815_245;
        int secondUid = 815_246;
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                    var context = PointerSingleModeGameTests.createContext(helper, "sunk_single", 815)) {
                BasicEntityShip sunkShip = PointerSingleModeGameTests.addShip(context, 0, firstUid,
                        new Vec3(4.5D, 2D, 1.5D));
                BasicEntityShip nextShip = PointerSingleModeGameTests.addShip(context, 1, secondUid,
                        new Vec3(6.5D, 2D, 1.5D));
                ServerDataManager.updateShipID(sunkShip);
                CacheDataShip sunk = requireRecord(firstUid);
                sunk.setSunkLocation(context.level().dimension(), 4, 64, 9);
                ServerDataManager.setShipWorldData(firstUid, sunk);
                sunkShip.discard();
                PointerSingleModeGameTests.select(context.capa(), 0, 1);
                BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 5));

                CommandDispatchResult result = dispatchMove(context, PointerItem.MODE_SINGLE, destination);

                PointerSingleModeGameTests.assertGuardDestination(helper, nextShip, destination,
                        "ship after a sunk single-mode slot");
                helper.assertTrue(result.accepted().size() == 1
                                && result.accepted().get(0).recipient().shipUid() == secondUid
                                && result.rejected().size() == 1
                                && result.rejected().get(0).reason() == CommandRejectReason.SUNK,
                        "Single command did not skip and report the sunk ship");
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(firstUid);
                ServerDataManager.removeShipData(secondUid);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void groupRejectsSunkShipAndDeliversToOtherShips(GameTestHelper helper) {
        int sunkUid = 815_249;
        int activeUid = 815_250;
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                    var context = PointerSingleModeGameTests.createContext(helper, "sunk_group", 817)) {
                BasicEntityShip sunkShip = PointerSingleModeGameTests.addShip(context, 0, sunkUid,
                        new Vec3(4.5D, 2D, 1.5D));
                BasicEntityShip activeShip = PointerSingleModeGameTests.addShip(context, 1, activeUid,
                        new Vec3(6.5D, 2D, 1.5D));
                ServerDataManager.updateShipID(sunkShip);
                CacheDataShip sunk = requireRecord(sunkUid);
                sunk.setSunkLocation(context.level().dimension(), 4, 64, 9);
                ServerDataManager.setShipWorldData(sunkUid, sunk);
                sunkShip.discard();
                PointerSingleModeGameTests.select(context.capa(), 0, 1);
                BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 5));

                CommandDispatchResult result = dispatchMove(context, PointerItem.MODE_GROUP, destination);

                PointerSingleModeGameTests.assertGuardDestination(helper, activeShip, destination,
                        "active group member alongside a sunk ship");
                helper.assertTrue(result.accepted().size() == 1
                                && result.accepted().get(0).recipient().shipUid() == activeUid
                                && result.rejected().size() == 1
                                && result.rejected().get(0).reason() == CommandRejectReason.SUNK,
                        "Group command did not report only the sunk ship while delivering to the active ship");
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(sunkUid);
                ServerDataManager.removeShipData(activeUid);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacySingleStillStopsAtSunkShip(GameTestHelper helper) {
        int firstUid = 815_247;
        int secondUid = 815_248;
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                    var context = PointerSingleModeGameTests.createContext(helper, "legacy_sunk_single", 816)) {
                BasicEntityShip sunkShip = PointerSingleModeGameTests.addShip(context, 0, firstUid,
                        new Vec3(4.5D, 2D, 1.5D));
                BasicEntityShip nextShip = PointerSingleModeGameTests.addShip(context, 1, secondUid,
                        new Vec3(6.5D, 2D, 1.5D));
                ServerDataManager.updateShipID(sunkShip);
                CacheDataShip sunk = requireRecord(firstUid);
                sunk.setSunkLocation(context.level().dimension(), 4, 64, 9);
                ServerDataManager.setShipWorldData(firstUid, sunk);
                sunkShip.discard();
                PointerSingleModeGameTests.select(context.capa(), 0, 1);
                BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 5));

                PointerSingleModeGameTests.invokePacketHandler(new com.lulan.shincolle.network.C2SGUIInputPacket(
                        com.lulan.shincolle.network.C2SGUIInputPacket.SetMove,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 1,
                                destination.getX(), destination.getY(), destination.getZ()}),
                        "handleSetMove", context.player());

                helper.assertTrue(!nextShip.hasGuardDestination(),
                        "Legacy single command fell through a sunk first slot");
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(firstUid);
                ServerDataManager.removeShipData(secondUid);
            }
        });
    }

    private static BasicEntityShip createShip(GameTestEntities entities, ServerLevel level, int uid,
                                               int ownerUid, Vec3 position) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(level));
        if (ship == null) {
            throw new AssertionError("Could not create ship for sunk-location test");
        }
        ship.setNoAi(true);
        ship.setPlayerUID(ownerUid);
        ship.setShipUID(uid);
        ship.moveTo(position.x, position.y, position.z, 0F, 0F);
        if (!level.addFreshEntity(ship)) {
            throw new AssertionError("Could not add ship for sunk-location test");
        }
        ServerDataManager.updateShipID(ship);
        return ship;
    }

    private static CacheDataShip requireRecord(int uid) {
        CacheDataShip record = ServerDataManager.getShipWorldData(uid);
        if (record == null) {
            throw new AssertionError("Ship cache record is missing for uid " + uid);
        }
        return record;
    }

    private static CommandDispatchResult dispatchMove(
            PointerSingleModeGameTests.TestContext context, int mode, BlockPos destination) {
        return ShipCommandDispatcher.dispatch(context.player(), CommandKind.MOVE,
                new int[]{context.player().getId(), 0, mode, 1,
                        destination.getX(), destination.getY(), destination.getZ()},
                (level, capa, team, slot) -> {
                    int uid = capa.getTeamMember(team, slot);
                    Entity entity = level.getEntity(capa.getTeamSID(team, slot));
                    BasicEntityShip ship = entity instanceof BasicEntityShip candidate ? candidate : null;
                    return ship != null && ship.level() == level
                            && ship.getPlayerUID() == capa.getPlayerUID()
                            && ship.getStateMinor(ID.M.ShipUID) == uid ? ship : null;
                }, (level, capa, team, slot) -> capa.getTeamMember(team, slot) > 0,
                (player, ships, x, y, z) -> { });
    }

    private static ItemStack savedEgg(BasicEntityShip ship, UUID ownerUuid) {
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        egg.getOrCreateTag().putIntArray("StateMinor", ship.getStateMinorArray().clone());
        egg.getOrCreateTag().putString("owner", ownerUuid.toString());
        return egg;
    }

    private static void invokeInitShipFromEgg(BasicEntityShip ship, ItemStack egg, FakePlayer owner) {
        try {
            Method method = ShipSpawnEgg.class.getDeclaredMethod(
                    "initShipFromEgg", BasicEntityShip.class, ItemStack.class,
                    net.minecraft.world.entity.player.Player.class);
            method.setAccessible(true);
            method.invoke(null, ship, egg, owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not initialize ship from saved egg", failure);
        }
    }

    private static CompoundTag shipTag(CompoundTag root, int uid) {
        ListTag ships = root.getList("shipData", Tag.TAG_COMPOUND);
        for (int index = 0; index < ships.size(); index++) {
            CompoundTag ship = ships.getCompound(index);
            if (ship.getInt("sUID") == uid) {
                return ship;
            }
        }
        throw new AssertionError("Serialized ship is missing for uid " + uid);
    }

    private static void setDeathTime(BasicEntityShip ship, int deathTime) {
        try {
            Field field = LivingEntity.class.getDeclaredField("deathTime");
            field.setAccessible(true);
            field.setInt(ship, deathTime);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not set ship death timer", failure);
        }
    }

    private static void invokeTickDeath(BasicEntityShip ship) {
        try {
            Method method = BasicEntityShip.class.getDeclaredMethod("tickDeath");
            method.setAccessible(true);
            method.invoke(ship);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not advance ship death tick", failure);
        }
    }
}
