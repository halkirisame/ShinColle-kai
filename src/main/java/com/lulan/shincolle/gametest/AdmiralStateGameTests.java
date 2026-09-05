package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdmiralStateGameTests {

    private static final Set<String> NBT_KEYS = Set.of(
            "HasRing", "RingActive", "RingFlying", "MarriageNum", "BossCD", "TeamCD",
            "PlayerUID", "SelectTeam", "ColledShip", "ColledEquip", "Teams", "TeamName",
            "TargetClassList", "AllyList", "BanList", "KnownTeamIds", "ColledShipList",
            "ColledEquipList", "ShipList");

    private AdmiralStateGameTests() {
    }

    @GameTest(template = "arena")
    public static void serializedNbtUsesLegacyKeySet(GameTestHelper helper) {
        CompoundTag nbt = new CapaTeitoku().serializeNBT();

        helper.assertTrue(nbt.getAllKeys().equals(NBT_KEYS),
                "Admiral NBT key set changed: " + nbt.getAllKeys());
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void populatedStateRoundTripsWithoutNbtChanges(GameTestHelper helper) {
        CapaTeitoku source = populatedState();
        CompoundTag serialized = source.serializeNBT();
        serialized.putInt("SelectTeam", 99);
        CapaTeitoku restored = new CapaTeitoku();

        restored.deserializeNBT(serialized.copy());

        helper.assertTrue(serialized.equals(restored.serializeNBT()),
                "Admiral NBT changed after a serialize/deserialize round trip.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void shortTeamsPayloadKeepsRemainingDefaults(GameTestHelper helper) {
        CompoundTag nbt = populatedState().serializeNBT();
        ListTag teams = nbt.getList("Teams", Tag.TAG_COMPOUND);
        ListTag shortened = new ListTag();
        for (int team = 0; team < 3; team++) {
            shortened.add(teams.getCompound(team).copy());
        }
        nbt.put("Teams", shortened);
        CapaTeitoku restored = new CapaTeitoku();

        restored.deserializeNBT(nbt);

        for (int team = 3; team < CapaTeitoku.TEAM_NUM; team++) {
            helper.assertTrue(restored.getFormatID(team) == 0,
                    "Short Teams payload overwrote a missing formation default.");
            for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                helper.assertTrue(restored.getTeamMember(team, slot) == -1,
                        "Short Teams payload overwrote a missing team default.");
                helper.assertTrue(!restored.isShipSelected(team, slot),
                        "Short Teams payload overwrote a missing selection default.");
            }
            helper.assertTrue(restored.getUnitName(team).equals("Team " + (team + 1)),
                    "Short Teams payload overwrote a missing team name default.");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void shortEidsPayloadKeepsRemainingSlotDefaults(GameTestHelper helper) {
        CompoundTag nbt = new CapaTeitoku().serializeNBT();
        nbt.getList("Teams", Tag.TAG_COMPOUND).getCompound(0).putIntArray("EIDs", new int[]{7, 8});
        CapaTeitoku restored = new CapaTeitoku();

        restored.deserializeNBT(nbt);

        helper.assertTrue(restored.getTeamMember(0, 0) == 7 && restored.getTeamMember(0, 1) == 8,
                "Short EIDs payload did not load available slots.");
        for (int slot = 2; slot < CapaTeitoku.SLOT_NUM; slot++) {
            helper.assertTrue(restored.getTeamMember(0, slot) == -1,
                    "Short EIDs payload overwrote a missing slot default.");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void missingSelectedMigratesOnlyFirstOccupiedSlot(GameTestHelper helper) {
        CompoundTag nbt = new CapaTeitoku().serializeNBT();
        CompoundTag team = nbt.getList("Teams", Tag.TAG_COMPOUND).getCompound(0);
        team.putIntArray("EIDs", new int[]{-1, 5, 9, -1, 3, -1});
        team.remove("Selected");
        CapaTeitoku restored = new CapaTeitoku();

        restored.deserializeNBT(nbt);

        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            helper.assertTrue(restored.isShipSelected(0, slot) == (slot == 1),
                    "Missing Selected migration did not choose only the first occupied slot.");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void missingCooldownsUseLegacyDefaults(GameTestHelper helper) {
        CompoundTag nbt = new CapaTeitoku().serializeNBT();
        nbt.remove("BossCD");
        nbt.remove("TeamCD");
        CapaTeitoku restored = new CapaTeitoku();

        restored.deserializeNBT(nbt);

        helper.assertTrue(restored.getBossCooldown() == ConfigHandler.bossCooldown(),
                "Missing BossCD did not use the configured default.");
        helper.assertTrue(restored.getTeamCooldown() == 0,
                "Missing TeamCD did not use the legacy zero default.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void respawnCopyPreservesOwnedStateButNotGuiFlag(GameTestHelper helper) {
        CapaTeitoku source = populatedState();
        source.setOpeningGUI(true);
        CapaTeitoku target = new CapaTeitoku();

        target.copyFrom(source);

        helper.assertTrue(source.serializeNBT().equals(target.serializeNBT()),
                "Respawn copy did not preserve persistent admiral state.");
        helper.assertTrue(target.getTeamSID(0, 0) == 701,
                "Respawn copy did not preserve runtime team entity IDs.");
        helper.assertTrue(!target.isOpeningGUI(),
                "Respawn copy preserved the old player's GUI-open flag.");
        helper.assertTrue(target.getTargetClassNames().equals(source.getTargetClassNames())
                        && target.isInitSID() == source.isInitSID()
                        && target.isShowPlayerSkill() == source.isShowPlayerSkill()
                        && target.getEntityItemList()[0] == source.getEntityItemList()[0],
                "Respawn copy did not preserve client mirror state.");
        helper.succeed();
    }

    private static CapaTeitoku populatedState() {
        CapaTeitoku state = new CapaTeitoku();
        state.setHasRing(true);
        state.setRingActive(true);
        state.setRingFlying(true);
        state.setMarriageNum(4);
        state.setBossCooldown(101);
        state.setTeamCooldown(202);
        state.setPlayerUID(303);
        state.setSelectTeam(8);
        state.setColledShipNum(404);
        state.setColledEquipNum(505);
        state.setTeamMember(0, 0, 601);
        state.setTeamSID(0, 0, 701);
        state.setShipSelected(0, 0, true);
        state.setFormatID(0, 2);
        state.setUnitName(0, "First Fleet");
        state.setTargetClassList(List.of(11, 12));
        state.setTargetClassNames(List.of("ship", "submarine"));
        state.setTeamName("Admiral Team");
        state.setAllyList(List.of(21));
        state.setBanList(List.of(22));
        state.setKnownTeamIds(List.of(23));
        state.setColledShipList(List.of(24));
        state.setColledEquipList(List.of(25));
        state.setShipList(List.of(26));
        state.setInitSID(true);
        state.setShowPlayerSkill(true);
        state.setEntityItemList(new float[]{27F});
        return state;
    }
}
