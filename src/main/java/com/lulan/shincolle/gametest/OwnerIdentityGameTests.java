package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFormationStateAdapter;
import com.lulan.shincolle.command.ShipCmdUpdateOwnerUID;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.utility.EntityHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerIdentityGameTests {
    private OwnerIdentityGameTests() { }

    @GameTest(template = "arena")
    public static void clearingTeamResolvesUidBeforeResettingFormation(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "identity_clear", 48501)) {
                BasicEntityShip member = PointerSingleModeGameTests.addShip(context, 0, 4850101,
                        new Vec3(4.5, 2, 1.5));
                BasicEntityShip unrelated = PointerSingleModeGameTests.addShip(context, 1, 4850102,
                        new Vec3(6.5, 2, 1.5));
                context.capa().setTeamMember(0, 1, 0);
                context.capa().setTeamSID(0, 1, 0);
                context.capa().setTeamSID(0, 0, unrelated.getId());
                member.setStateMinor(ID.M.FormatType, 2);
                unrelated.setStateMinor(ID.M.FormatType, 3);
                ServerDataManager.updateShipID(member);
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.ClearTeam,
                        new int[0]), "handleClearTeam", context.player());
                helper.assertTrue(member.getStateMinor(ID.M.FormatType) == 0
                                && unrelated.getStateMinor(ID.M.FormatType) == 3,
                        "Clear must reset the resolved member, never the reused entity ID");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "arena")
    public static void settingFormationDoesNotResetTransferredShip(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "identity_transfer", 48506);
                 var recipient = PointerSingleModeGameTests.createContext(helper, "identity_recipient", 48507)) {
                var ships = sharedFormationShips(helper, context, 4850600);
                BasicEntityShip transferred = ships.get(5);
                helper.assertTrue(ServerDataManager.changeShipOwner(transferred, recipient.player()),
                        "Fixture must transfer the sixth ship to another owner");
                helper.assertTrue(transferred.getPlayerUID() == recipient.capa().getPlayerUID()
                                && transferred.getPlayerUID() != context.capa().getPlayerUID()
                                && recipient.player().getUUID().equals(transferred.getOwnerUUID())
                                && transferred.isAlive() && transferred.level() == context.level()
                                && context.capa().getTeamMember(0, 5) == transferred.getShipUID()
                                && context.capa().getTeamSID(0, 5) == transferred.getId()
                                && context.level().getEntity(context.capa().getTeamSID(0, 5)) == transferred
                                && transferred.getStateMinor(ID.M.FormatType) == 2,
                        "Fixture must retain the transferred ship's UID/SID only in the old owner's team 0");

                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetFormation,
                        new int[]{context.player().getId(), 1, 3}), "handleSetFormation", context.player());
                helper.assertTrue(context.capa().getFormatID(0) == 0 && context.capa().getFormatID(1) == 3,
                        "The shared team must reset and the selected team must get formation 3");
                for (int slot = 0; slot < 5; slot++) {
                    helper.assertTrue(ships.get(slot).getStateMinor(ID.M.FormatType) == 3,
                            "A shared ship must receive the selected team's formation, slot " + slot);
                }
                helper.assertTrue(transferred.getStateMinor(ID.M.FormatType) == 2,
                        "Setting formation must not reset a ship transferred to another owner");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "arena")
    public static void settingFormationResolvesOtherTeamUidBeforeResetting(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "identity_reset", 48508)) {
                var ships = sharedFormationShips(helper, context, 4850800);
                BasicEntityShip member = ships.get(5);
                BasicEntityShip unrelated = PointerSingleModeGameTests.addShip(context, 5, 4850899,
                        new Vec3(9.5, 2, 4.5));
                unrelated.setStateMinor(ID.M.NumGrudge, 100);
                unrelated.setStateFlag(ID.F.NoFuel, false);
                unrelated.setStateMinor(ID.M.FormatType, 4);
                context.capa().setTeamMember(0, 5, member.getShipUID());
                context.capa().setTeamSID(0, 5, unrelated.getId());
                ServerDataManager.updateShipID(member);
                helper.assertTrue(member.getPlayerUID() == context.capa().getPlayerUID()
                                && member.isAlive() && member.level() == context.level()
                                && context.capa().getTeamMember(0, 5) == member.getShipUID()
                                && context.capa().getTeamSID(0, 5) == unrelated.getId()
                                && context.level().getEntity(context.capa().getTeamSID(0, 5)) == unrelated
                                && unrelated.getShipUID() != member.getShipUID()
                                && unrelated.getPlayerUID() == context.capa().getPlayerUID()
                                && !context.capa().isShipInTeam(0, unrelated.getShipUID())
                                && !context.capa().isShipInTeam(1, unrelated.getShipUID())
                                && ServerDataManager.getShipByUID(member.getShipUID()) == member
                                && member.getStateMinor(ID.M.FormatType) == 2
                                && unrelated.getStateMinor(ID.M.FormatType) == 4,
                        "Fixture must cache another UID's SID while the real owned member remains resolvable");

                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetFormation,
                        new int[]{context.player().getId(), 1, 3}), "handleSetFormation", context.player());
                helper.assertTrue(context.capa().getFormatID(0) == 0 && context.capa().getFormatID(1) == 3,
                        "The shared team must reset and the selected team must get formation 3");
                for (int slot = 0; slot < 5; slot++) {
                    helper.assertTrue(ships.get(slot).getStateMinor(ID.M.FormatType) == 3,
                            "A shared ship must receive the selected team's formation, slot " + slot);
                }
                helper.assertTrue(unrelated.getStateMinor(ID.M.FormatType) == 4,
                        "Setting formation must not reset an unrelated ship at the stale SID");
                helper.assertTrue(member.getStateMinor(ID.M.FormatType) == 0
                                && context.capa().getTeamSID(0, 5) == member.getId(),
                        "The persistent UID must relink and reset the actual member of the other team");
                helper.succeed();
            }
        });
    }

    private static ArrayList<BasicEntityShip> sharedFormationShips(GameTestHelper helper,
            PointerSingleModeGameTests.TestContext context, int baseUid) {
        var ships = new ArrayList<BasicEntityShip>();
        for (int slot = 0; slot < 6; slot++) {
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, baseUid + slot,
                    new Vec3(4.5 + slot, 2, 1.5));
            ship.setOwnerUUID(context.player().getUUID());
            ship.setStateMinor(ID.M.NumGrudge, 100);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateMinor(ID.M.FormatType, 2);
            if (slot < 5) {
                context.capa().setTeamMember(1, slot, ship.getShipUID());
                context.capa().setTeamSID(1, slot, ship.getId());
            }
            helper.assertTrue(ship.isAlive() && ship.getPlayerUID() == context.capa().getPlayerUID()
                            && context.player().getUUID().equals(ship.getOwnerUUID())
                            && context.capa().getTeamMember(0, slot) == ship.getShipUID()
                            && context.level().getEntity(context.capa().getTeamSID(0, slot)) == ship
                            && ship.getStateMinor(ID.M.FormatType) == 2
                            && (slot == 5 || (context.capa().getTeamMember(1, slot) == ship.getShipUID()
                            && context.capa().getTeamSID(1, slot) == ship.getId())),
                    "Fixture must put six owned ships in team 0 and share exactly the first five with team 1");
            ships.add(ship);
        }
        context.capa().setFormatID(0, 2);
        context.capa().setFormatID(1, 0);
        helper.assertTrue(context.capa().getFormatID(0) == 2 && context.capa().getFormatID(1) == 0
                        && context.capa().getTeamMember(1, 5) <= 0
                        && context.capa().getNumberOfShip(context.level(), 1) == 5,
                "Fixture must give team 0 formation 2 and keep only five shared ships in team 1");
        return ships;
    }

    @GameTest(template = "arena")
    public static void ownerUidCommandRejectsReusedNameButAcceptsRenamedOwner(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "identity_command", 48502)) {
                BasicEntityShip stranger = PointerSingleModeGameTests.addShip(context, 0, 4850201,
                        new Vec3(4.5, 2, 1.5));
                stranger.setOwnerUUID(UUID.randomUUID());
                stranger.ownerName = context.player().getName().getString();
                stranger.setPlayerUID(4850299);
                BasicEntityShip own = PointerSingleModeGameTests.addShip(context, 1, 4850202,
                        new Vec3(6.5, 2, 1.5));
                own.setOwnerUUID(context.player().getUUID());
                own.ownerName = "BeforeRename";
                own.setPlayerUID(4850298);
                var dispatcher = new CommandDispatcher<CommandSourceStack>();
                ShipCmdUpdateOwnerUID.register(dispatcher);
                dispatcher.execute("shipupdateowneruid", context.player().createCommandSourceStack());
                helper.assertTrue(stranger.getPlayerUID() == 4850299
                                && own.getPlayerUID() == context.capa().getPlayerUID(),
                        "Only UUID ownership may authorize owner UID resynchronization");
                helper.succeed();
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                helper.fail("Owner command fixture failed: " + e.getMessage());
            }
        });
    }

    @GameTest(template = "arena")
    public static void renamedOwnerCanPickUpUuidEgg(GameTestHelper helper) {
        try (var context = PointerSingleModeGameTests.createContext(helper, "identity_pickup", 48503)) {
            ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
            egg.getOrCreateTag().putString("owner", context.player().getUUID().toString());
            egg.getOrCreateTag().putString("ownername", "BeforeRename");
            BasicEntityItem drop = context.entities().add(new BasicEntityItem(
                    ModEntities.BASIC_ENTITY_ITEM.get(), context.level()));
            CompoundTag saved = new CompoundTag();
            saved.put("Item", egg.save(new CompoundTag()));
            saved.putInt("PickupDelay", 0);
            drop.load(saved);
            helper.assertTrue(!ServerDataManager.checkOP(context.player()), "Fixture must not be an operator");
            drop.playerTouch(context.player());
            helper.assertTrue(drop.isRemoved() && context.player().getInventory().countItem(egg.getItem()) == 1,
                    "Renaming must not prevent the UUID owner from picking up a saved egg");
            context.player().getInventory().clearContent();
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void teamCreationDoesNotDeleteNamesakeRegistration(GameTestHelper helper) {
        try (var context = PointerSingleModeGameTests.createContext(helper, "identity_create", 48504)) {
            int oldId = 4850499;
            TeamData old = new TeamData(oldId, "KeepThisTeam", context.player().getName().getString());
            ServerDataManager.setTeamData(old);
            context.capa().setTeamCooldown(0);
            try {
                helper.assertTrue(ServerDataManager.teamCreate(context.player(), "NewTeam"),
                        "A different UID may create its own team");
                helper.assertTrue(ServerDataManager.getTeamData(oldId) == old,
                        "A reused leader name must never delete the previous registration");
                helper.succeed();
            } finally {
                ServerDataManager.removeTeamData(oldId);
                ServerDataManager.removeTeamData(context.capa().getPlayerUID());
            }
        }
    }

    @GameTest(template = "arena")
    public static void waypointUsesTheActiveFormationRoster(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "identity_waypoint", 48505);
                 var online = new FormationGameTestOwner(context.player())) {
                var ships = new ArrayList<BasicEntityShip>();
                for (int slot = 0; slot < 5; slot++) {
                    BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, 4850500 + slot,
                            new Vec3(4.5 + slot, 2, 1.5));
                    ship.setStateMinor(ID.M.NumGrudge, 100);
                    ServerDataManager.updateShipID(ship);
                    context.capa().setTeamMember(1, slot, ship.getShipUID());
                    context.capa().setTeamSID(1, slot, ship.getId());
                    if (slot > 0) context.capa().setTeamMember(0, slot, 0);
                    ships.add(ship);
                }
                context.capa().setFormatID(0, 0);
                context.capa().setFormatID(1, 1);
                for (BasicEntityShip ship : ships) {
                    ship.formationState().invalidate();
                    helper.assertTrue(ShipFormationStateAdapter.active(ship).isPresent(),
                            "Fixture must select the second roster's formation");
                }
                BlockPos first = helper.absolutePos(new BlockPos(4, 2, 1));
                BlockPos second = helper.absolutePos(new BlockPos(9, 2, 4));
                WaypointStructureGameTests.place(context.level(), first).setNextWaypoint(second);
                BasicEntityShip leader = ships.get(0);
                leader.setGuardedPos(first.getX(), first.getY(), first.getZ(), context.level().dimension(), 1);
                leader.setStateFlag(ID.F.CanFollow, false);
                helper.assertTrue(leader.hasGuardDestination(), "Fixture must install an active block guard");
                helper.assertTrue(EntityHelper.updateWaypointMove(leader), "Arrived flagship must advance");
                for (BasicEntityShip ship : ships) {
                    helper.assertTrue(ship.hasGuardDestination(),
                            "Every member of the active roster must receive the next destination: " + ship.getShipUID());
                }
                helper.succeed();
            }
        });
    }
}
