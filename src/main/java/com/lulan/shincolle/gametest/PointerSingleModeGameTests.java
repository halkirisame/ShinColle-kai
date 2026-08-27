package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointerSingleModeGameTests {

    private static final int TEAM_ID = 0;

    private PointerSingleModeGameTests() {
    }

    @GameTest(template = "arena")
    public static void singleModeAffectsOnlyLowestSelectedRealShip(GameTestHelper helper) {
        TestContext context = createContext(helper, "single_first", 21);
        BasicEntityShip first = addShip(context, 1, 2101, new Vec3(4.5D, 2D, 1.5D));
        BasicEntityShip second = addShip(context, 3, 2103, new Vec3(6.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 5, 2105, new Vec3(8.5D, 2D, 1.5D));
        select(context.capa(), 1, 3, 5);

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSitting,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, second.getId()}),
                "handleSetSitting", context.player());

        helper.assertTrue(first.isOrderedToSit(),
                "Single mode did not affect the lowest selected real ship.");
        helper.assertTrue(!second.isOrderedToSit() && !third.isOrderedToSit(),
                "Single mode affected a selected ship after the lowest selected real slot.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void singleModeOutOfRangeFirstShipDoesNotFallBack(GameTestHelper helper) {
        TestContext context = createContext(helper, "single_range", 22);
        Vec3 playerPos = context.player().position();
        BasicEntityShip first = addShip(context, 0, 2200,
                new Vec3(playerPos.x, playerPos.y + 65D, playerPos.z), false);
        BasicEntityShip second = addShip(context, 2, 2202, new Vec3(5.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 4, 2204, new Vec3(7.5D, 2D, 1.5D));
        select(context.capa(), 0, 2, 4);
        Zombie target = addTarget(context, new Vec3(2.5D, 2D, 4.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.AttackTarget,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                "handleAttackTarget", context.player());

        helper.assertTrue(first.getTarget() == null && second.getTarget() == null && third.getTarget() == null,
                "Single mode fell back after the lowest selected real ship was out of range.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void singleModeSkipsSelectedSlotWithoutRealShip(GameTestHelper helper) {
        TestContext context = createContext(helper, "single_missing", 23);
        context.capa().setTeamMember(TEAM_ID, 0, 2300);
        context.capa().setTeamSID(TEAM_ID, 0, -1);
        context.capa().setShipSelected(TEAM_ID, 0, true);
        BasicEntityShip ship = addShip(context, 2, 2302, new Vec3(5.5D, 2D, 1.5D));
        context.capa().setShipSelected(TEAM_ID, 2, true);
        Zombie target = addTarget(context, new Vec3(2.5D, 2D, 4.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.GuardEntity,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                "handleGuardEntity", context.player());

        helper.assertTrue(ship.getGuardedEntity() == target,
                "Single mode did not advance past a selected slot without a real ship.");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void groupModeAffectsEverySelectedShip(GameTestHelper helper) {
        TestContext context = createContext(helper, "group", 24);
        BasicEntityShip first = addShip(context, 0, 2400, new Vec3(4.5D, 2D, 1.5D));
        BasicEntityShip second = addShip(context, 2, 2402, new Vec3(6.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 4, 2404, new Vec3(8.5D, 2D, 1.5D));
        select(context.capa(), 0, 2, 4);
        BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 4));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                new int[]{context.player().getId(), 0, PointerItem.MODE_GROUP, 1,
                        destination.getX(), destination.getY(), destination.getZ()}),
                "handleSetMove", context.player());

        assertGuardDestination(helper, first, destination, "first selected group ship");
        assertGuardDestination(helper, second, destination, "second selected group ship");
        assertGuardDestination(helper, third, destination, "third selected group ship");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void formationModeAffectsWholeTeamWithoutSelection(GameTestHelper helper) {
        TestContext context = createContext(helper, "formation", 25);
        BasicEntityShip first = addShip(context, 0, 2500, new Vec3(4.5D, 2D, 1.5D));
        BasicEntityShip second = addShip(context, 2, 2502, new Vec3(6.5D, 2D, 1.5D));
        BasicEntityShip third = addShip(context, 4, 2504, new Vec3(8.5D, 2D, 1.5D));

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSitting,
                new int[]{context.player().getId(), 0, PointerItem.MODE_FORMATION, second.getId()}),
                "handleSetSitting", context.player());

        helper.assertTrue(first.isOrderedToSit() && second.isOrderedToSit() && third.isOrderedToSit(),
                "Formation mode did not affect the whole team when no ships were selected.");
        helper.succeed();
    }

    private static TestContext createContext(GameTestHelper helper, String name, int id) {
        ServerLevel level = helper.getLevel();
        UUID uuid = UUID.fromString(String.format("6b00b41e-2c24-45a1-9d20-%012d", id));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(uuid, "pointer_" + name));
        Vec3 playerPos = helper.absoluteVec(new Vec3(2.5D, 2D, 1.5D));
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0F, 0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            throw new AssertionError("Pointer test player has no CapaTeitoku capability.");
        }
        capa.setPlayerUID(2000 + id);
        capa.setSelectTeam(TEAM_ID);
        capa.clearShipSelection(TEAM_ID);
        return new TestContext(helper, level, player, capa);
    }

    private static BasicEntityShip addShip(TestContext context, int slot, int shipUid, Vec3 relativePos) {
        return addShip(context, slot, shipUid, relativePos, true);
    }

    private static BasicEntityShip addShip(TestContext context, int slot, int shipUid, Vec3 pos,
                                           boolean relativePos) {
        Vec3 worldPos = relativePos ? context.helper().absoluteVec(pos) : pos;
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(context.level());
        if (ship == null) {
            throw new AssertionError("Failed to create ship for pointer mode test.");
        }
        ship.setNoAi(true);
        ship.setPlayerUID(context.capa().getPlayerUID());
        ship.setShipUID(shipUid);
        ship.moveTo(worldPos.x, worldPos.y, worldPos.z, 0F, 0F);
        if (!context.level().addFreshEntity(ship)) {
            throw new AssertionError("Failed to add ship for pointer mode test.");
        }
        context.capa().setTeamMember(TEAM_ID, slot, shipUid);
        context.capa().setTeamSID(TEAM_ID, slot, ship.getId());
        return ship;
    }

    private static Zombie addTarget(TestContext context, Vec3 relativePos) {
        Zombie target = EntityType.ZOMBIE.create(context.level());
        if (target == null) {
            throw new AssertionError("Failed to create target for pointer mode test.");
        }
        Vec3 pos = context.helper().absoluteVec(relativePos);
        target.setNoAi(true);
        target.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        if (!context.level().addFreshEntity(target)) {
            throw new AssertionError("Failed to add target for pointer mode test.");
        }
        return target;
    }

    private static void select(CapaTeitoku capa, int... slots) {
        for (int slot : slots) {
            capa.setShipSelected(TEAM_ID, slot, true);
        }
    }

    private static void assertGuardDestination(GameTestHelper helper, BasicEntityShip ship,
                                               BlockPos expected, String description) {
        helper.assertTrue(ship.hasGuardDestination()
                        && ship.getGuardedPos(0) == expected.getX()
                        && ship.getGuardedPos(1) == expected.getY()
                        && ship.getGuardedPos(2) == expected.getZ(),
                "Move command did not reach " + description + ".");
    }

    private static void invokePacketHandler(C2SGUIInputPacket packet, String methodName, ServerPlayer player) {
        try {
            Method method = C2SGUIInputPacket.class.getDeclaredMethod(methodName, ServerPlayer.class);
            method.setAccessible(true);
            method.invoke(packet, player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke pointer packet handler: " + methodName, e);
        }
    }

    private record TestContext(GameTestHelper helper, ServerLevel level, ServerPlayer player, CapaTeitoku capa) {
    }
}
