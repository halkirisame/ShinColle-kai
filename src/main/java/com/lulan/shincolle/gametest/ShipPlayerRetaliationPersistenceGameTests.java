package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPlayerRetaliationPersistenceGameTests {
    private ShipPlayerRetaliationPersistenceGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void oldShipFlagsFreshLoadForbidsRetaliationAndPreservesPrefix(GameTestHelper helper) {
        BasicEntityShip source = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        source.setStateFlag(ID.F.PassiveAI, true);
        source.setStateFlag(ID.F.RetaliatePlayer, true);
        CompoundTag nbt = new CompoundTag();
        CapaShipSavedValues.saveNBTData(nbt, source);
        byte[] oldFlags = Arrays.copyOf(nbt.getByteArray("StateFlag"), 27);
        nbt.putByteArray("StateFlag", oldFlags);
        BasicEntityShip fresh = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        helper.assertTrue(!fresh.getStateFlag(ID.F.RetaliatePlayer), "Fresh ship permission must start off");
        CapaShipSavedValues.loadNBTData(nbt, fresh);
        helper.assertTrue(!fresh.getStateFlag(ID.F.RetaliatePlayer), "Old flags enabled fresh ship permission");
        for (int i = 0; i < oldFlags.length; i++) {
            helper.assertTrue(fresh.getStateFlagArray()[i] == (oldFlags[i] != 0),
                    "Old flag prefix changed at " + i);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sameVersionShipSaveRetainsBothPermissionValues(GameTestHelper helper) {
        for (boolean permitted : new boolean[]{false, true}) {
            BasicEntityShip source = ModEntities.BB_KONGOU.get().create(helper.getLevel());
            source.setPlayerUID(732301);
            source.setShipUID(732302);
            source.setStateFlag(ID.F.RetaliatePlayer, permitted);
            CompoundTag nbt = new CompoundTag();
            source.addAdditionalSaveData(nbt);
            BasicEntityShip fresh = ModEntities.BB_KONGOU.get().create(helper.getLevel());
            fresh.readAdditionalSaveData(nbt.copy());
            helper.assertTrue(fresh.getStateFlag(ID.F.RetaliatePlayer) == permitted
                            && fresh.getPlayerUID() == 732301 && fresh.getShipUID() == 732302,
                    "Same-version entity save/load lost permission or identity: " + permitted);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fleetPermissionMissingKeysRoundTripAndCloneEvents(GameTestHelper helper) {
        CapaTeitoku source = new CapaTeitoku();
        source.setPlayerUID(732311);
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            source.setRetaliatePlayer(team, team % 2 == 0);
            source.setFormatID(team, team % 5);
            source.setTeamMember(team, 0, 732320 + team);
            source.setShipSelected(team, 0, team % 2 != 0);
        }
        CompoundTag saved = source.serializeNBT();
        CapaTeitoku restored = new CapaTeitoku();
        restored.deserializeNBT(saved.copy());
        helper.assertTrue(restored.serializeNBT().equals(saved), "Same-version fleet save/load changed data");

        CompoundTag old = saved.copy();
        ListTag teams = old.getList("Teams", Tag.TAG_COMPOUND);
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            teams.getCompound(team).remove("RetaliatePlayer");
        }
        CapaTeitoku fresh = new CapaTeitoku();
        fresh.deserializeNBT(old);
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            helper.assertTrue(!fresh.getRetaliatePlayer(team)
                            && fresh.getFormatID(team) == source.getFormatID(team)
                            && fresh.getTeamMember(team, 0) == source.getTeamMember(team, 0)
                            && fresh.isShipSelected(team, 0) == source.isShipSelected(team, 0),
                    "Missing fleet permission changed existing data at team " + team);
        }
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (boolean wasDeath : new boolean[]{false, true}) {
                ServerPlayer original = player(helper, entities);
                ServerPlayer clone = player(helper, entities);
                CapaTeitoku originalCapa = capability(original);
                originalCapa.deserializeNBT(saved.copy());
                original.invalidateCaps();
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, original, wasDeath));
                CapaTeitoku cloned = capability(clone);
                helper.assertTrue(cloned.serializeNBT().equals(saved),
                        "Clone event lost permission or membership: wasDeath=" + wasDeath);
                original.reviveCaps();
                originalCapa.setRetaliatePlayer(0, false);
                helper.assertTrue(cloned.getRetaliatePlayer(0), "Clone shares the old permission array");
                original.invalidateCaps();
            }
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, GameTestEntities entities) {
        return entities.add(FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "retaliation_save")));
    }

    private static CapaTeitoku capability(ServerPlayer player) {
        return player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new IllegalStateException("Fixture player has no admiral capability"));
    }
}
