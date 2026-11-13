package com.lulan.shincolle.gametest;

import com.lulan.shincolle.command.ShipCmdUpdateOwnerUID;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.SavedEggOwnership;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.server.ShinWorldData;
import com.lulan.shincolle.team.TeamData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyOwnerMigrationGameTests {
    private LegacyOwnerMigrationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyTeamBindsOnlyMatchingUidAndSurvivesSave(GameTestHelper helper) {
        try (var context = PointerSingleModeGameTests.createContext(helper, "identity_legacy_team", 48506)) {
            int uid = context.capa().getPlayerUID();
            TeamData team = new TeamData(uid, "Legacy", "BeforeRename");
            ServerDataManager.setTeamData(team);
            try {
                helper.assertTrue(ServerDataManager.authorizeTeam(context.player()), "Matching legacy UID must bind");
                helper.assertTrue(context.player().getUUID().equals(team.getTeamLeaderUUID()), "UUID must be retained");
                var world = ShinWorldData.load(worldTeam(team));
                var roundTrip = world.save(new CompoundTag()).getList("teamData", 10).getCompound(0);
                helper.assertTrue(roundTrip.hasUUID("LeaderUUID")
                                && roundTrip.getUUID("LeaderUUID").equals(context.player().getUUID()),
                        "Bound team UUID must survive world save/load");
                team.setTeamLeaderUUID(UUID.randomUUID());
                helper.assertTrue(!ServerDataManager.authorizeTeam(context.player()),
                        "Matching numeric UID must not override a different saved UUID");
                context.capa().setTeamCooldown(0);
                helper.assertTrue(!ServerDataManager.teamCreate(context.player(), "Replacement")
                                && ServerDataManager.getTeamData(uid) == team,
                        "Creation must not overwrite a UUID-conflicting registration");
                helper.succeed();
            } finally {
                ServerDataManager.removeTeamData(uid);
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyTeamAdminMigrationRekeysRelationsAndRejectsCollision(GameTestHelper helper) {
        try (var context = PointerSingleModeGameTests.createContext(helper, "mteam", 48507);
             var online = new FormationGameTestOwner(context.player())) {
            int target = context.capa().getPlayerUID();
            int oldId = 4850791;
            int peerId = 4850792;
            TeamData old = new TeamData(oldId, "OldTeam", "OldName");
            TeamData peer = new TeamData(peerId, "PeerTeam", "Peer");
            peer.addTeamAlly(oldId);
            old.addTeamBanned(peerId);
            ServerDataManager.setTeamData(old);
            ServerDataManager.setTeamData(peer);
            try {
                TeamData conflict = new TeamData(target, "Existing", "Existing");
                ServerDataManager.setTeamData(conflict);
                helper.assertTrue(!ServerDataManager.migrateLegacyTeam(oldId, context.player())
                                && ServerDataManager.getTeamData(oldId) == old
                                && ServerDataManager.getTeamData(target) == conflict,
                        "Target collision must preserve both registrations");
                ServerDataManager.removeTeamData(target);
                var dispatcher = new CommandDispatcher<CommandSourceStack>();
                ShipCmdUpdateOwnerUID.register(dispatcher);
                String command = "shipupdateowneruid migrateTeam " + oldId + " " + context.player().getName().getString();
                boolean denied = false;
                try {
                    dispatcher.execute(command, context.player().createCommandSourceStack().withPermission(0));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                    denied = true;
                }
                helper.assertTrue(denied && ServerDataManager.getTeamData(oldId) == old,
                        "A non-operator must not migrate teams");
                helper.assertTrue(dispatcher.execute(command, context.player().createCommandSourceStack().withPermission(2)) == 1,
                        "An explicit migration must rekey an unclaimed legacy team");
                helper.assertTrue(ServerDataManager.getTeamData(oldId) == null
                                && ServerDataManager.getTeamData(target) == old && old.getTeamID() == target
                                && context.player().getUUID().equals(old.getTeamLeaderUUID())
                                && peer.isTeamAlly(target) && !peer.isTeamAlly(oldId) && old.isTeamBanned(peerId),
                        "Migration must keep team contents and update every incoming relation");
                helper.succeed();
            } finally {
                ServerDataManager.removeTeamData(oldId);
                ServerDataManager.removeTeamData(peerId);
                ServerDataManager.removeTeamData(target);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("Team migration command fixture failed: " + e.getMessage());
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void eggOwnershipUsesUuidThenPositiveUidWithoutNameAuthority(GameTestHelper helper) {
        try (var context = PointerSingleModeGameTests.createContext(helper, "identity_legacy_egg", 48508)) {
            for (boolean generic : new boolean[]{false, true}) {
                ItemStack egg = new ItemStack(generic ? ModItems.GENERIC_SHIP_SPAWN_EGG.get() : ModItems.SHIP_SPAWN_EGG.get());
                CompoundTag tag = egg.getOrCreateTag();
                tag.putString("ownername", context.player().getName().getString());
                int[] minors = new int[ID.M.PlayerUID + 1];
                minors[ID.M.PlayerUID] = context.capa().getPlayerUID() + 1;
                tag.putIntArray("StateMinor", minors);
                helper.assertTrue(!SavedEggOwnership.mayPickUp(egg, context.player()) && !tag.contains("owner"),
                        "A reused name with mismatching UID must not bind the old egg");
                minors[ID.M.PlayerUID] = context.capa().getPlayerUID();
                tag.putIntArray("StateMinor", minors);
                helper.assertTrue(SavedEggOwnership.mayPickUp(egg, context.player())
                                && tag.getString("owner").equals(context.player().getUUID().toString()),
                        "Matching positive UID must bind and save the UUID");
                tag.putString("owner", UUID.randomUUID().toString());
                helper.assertTrue(!SavedEggOwnership.mayPickUp(egg, context.player()),
                        "An existing different UUID must win over numeric UID and name");
                tag.putString("owner", "broken");
                helper.assertTrue(!SavedEggOwnership.mayPickUp(egg, context.player()), "Malformed UUID must be held");
                tag.remove("owner");
                tag.remove("StateMinor");
                tag.putInt("PlayerID", context.capa().getPlayerUID());
                helper.assertTrue(SavedEggOwnership.mayPickUp(egg, context.player()),
                        "The original egg's flat PlayerID must also support UID-bound migration");
                tag.remove("owner");
                tag.remove("PlayerID");
                tag.putIntArray("StateMinor", new int[0]);
                helper.assertTrue(!SavedEggOwnership.mayPickUp(egg, context.player()), "Missing UID grants no authority");
                BasicEntityItem drop = context.entities().add(new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(), context.level()));
                CompoundTag saved = new CompoundTag();
                saved.put("Item", egg.save(new CompoundTag()));
                saved.putInt("PickupDelay", 0);
                drop.load(saved);
                drop.playerTouch(context.player());
                helper.assertTrue(!drop.isRemoved() && !drop.getEntityItem().isEmpty(),
                        "Unclaimed legacy data must remain recoverable");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyEggMigrationCommandRequiresOperatorAndPreservesPayload(GameTestHelper helper) {
        try (var context = PointerSingleModeGameTests.createContext(helper, "megg", 48509);
             var online = new FormationGameTestOwner(context.player())) {
            ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
            egg.getOrCreateTag().putString("ownername", "LegacyOwner");
            egg.getOrCreateTag().putString("Payload", "KeepMe");
            egg.getOrCreateTag().putString("Owner", "LegacyOwner");
            egg.getOrCreateTag().putIntArray("StateMinor", new int[ID.M.PlayerUID + 1]);
            context.player().setItemInHand(InteractionHand.MAIN_HAND, egg);
            var dispatcher = new CommandDispatcher<CommandSourceStack>();
            ShipCmdUpdateOwnerUID.register(dispatcher);
            String command = "shipupdateowneruid migrateEgg " + context.player().getName().getString();
            boolean denied = false;
            try {
                dispatcher.execute(command, context.player().createCommandSourceStack().withPermission(0));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                denied = true;
            }
            helper.assertTrue(denied && !egg.getTag().contains("owner"), "A non-operator must not migrate eggs");
            helper.assertTrue(dispatcher.execute(command, context.player().createCommandSourceStack().withPermission(2)) == 1,
                    "Operator must have an explicit migration path");
            helper.assertTrue(egg.getCount() == 1 && egg.getTag().getString("Payload").equals("KeepMe")
                            && egg.getTag().getString("Owner").equals(context.player().getName().getString())
                            && egg.getTag().getString("owner").equals(context.player().getUUID().toString()),
                    "Migration must retain the saved egg payload");
            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("Migration command fixture failed: " + e.getMessage());
        }
    }

    private static CompoundTag worldTeam(TeamData team) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tUID", team.getTeamID());
        tag.putString("tName", team.getTeamName());
        tag.putString("tLName", team.getTeamLeaderName());
        tag.putUUID("LeaderUUID", team.getTeamLeaderUUID());
        var teams = new net.minecraft.nbt.ListTag();
        teams.add(tag);
        CompoundTag world = new CompoundTag();
        world.put("teamData", teams);
        return world;
    }
}
