package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenericShipProtectionGameTests {
    private static final Vec3 POSITION = new Vec3(3.1, 2, 3.5);
    private static final ShipDefinitionId SAMPLE = ShipDefinitionId.parse("shincolle_test:sample_ship");

    private GenericShipProtectionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericEggOwnerCanPickUp(GameTestHelper helper) {
        pickup(helper, true, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericEggNonOwnerCannotPickUp(GameTestHelper helper) {
        pickup(helper, true, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericEggOperatorCanPickUp(GameTestHelper helper) {
        pickup(helper, true, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void builtInEggOwnerCanPickUp(GameTestHelper helper) {
        pickup(helper, false, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void builtInEggNonOwnerCannotPickUp(GameTestHelper helper) {
        pickup(helper, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void builtInEggOperatorCanPickUp(GameTestHelper helper) {
        pickup(helper, false, false, true);
    }

    private static void pickup(GameTestHelper helper, boolean generic, boolean owner, boolean operator) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EggPickup")) {
                    @Override
                    public boolean hasPermissions(int level) {
                        return operator && level <= 2;
                    }
                };
                helper.assertTrue(ServerDataManager.checkOP(player) == operator, "OP fixture not effective");
                // Exercise both the existing name preference and its UUID fallback.
                for (boolean name : new boolean[]{true, false}) {
                    player.getInventory().clearContent();
                    ItemStack egg = new ItemStack(generic ? ModItems.GENERIC_SHIP_SPAWN_EGG.get() : ModItems.SHIP_SPAWN_EGG.get());
                    egg.getOrCreateTag().putString("ShipId", SAMPLE.toString());
                    egg.getOrCreateTag().putString("ownername", name ? (owner ? "EggPickup" : "OtherOwner") : "");
                    egg.getOrCreateTag().putString("owner", owner ? player.getUUID().toString() : UUID.randomUUID().toString());
                    var eggItem = egg.getItem();
                    BasicEntityItem item = entities.add(new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(), helper.getLevel(),
                            helper.absoluteVec(POSITION).x, helper.absoluteVec(POSITION).y, helper.absoluteVec(POSITION).z, egg));
                    helper.assertTrue(helper.getLevel().addFreshEntity(item), "item not registered");
                    item.playerTouch(player);
                    helper.assertTrue(!item.isRemoved() && player.getInventory().isEmpty(), "pickup delay ignored");
                    for (int tick = 0; tick < 10; tick++) item.tick();
                    item.playerTouch(player);
                    boolean allowed = owner || operator;
                    helper.assertTrue(item.isRemoved() == allowed, "wrong pickup decision for generic=" + generic + " name=" + name);
                    helper.assertTrue(player.getInventory().countItem(eggItem) == (allowed ? 1 : 0), "wrong inventory result");
                }
                player.getInventory().clearContent();
                helper.succeed();
            }
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dormantFriendlyRejectsExternalMovement(GameTestHelper helper) {
        externalMovement(helper, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dormantHostileRejectsExternalMovement(GameTestHelper helper) {
        externalMovement(helper, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void activeFriendlyStillMovesWithPistonAndBoat(GameTestHelper helper) {
        externalMovement(helper, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void activeHostileStillMovesWithPistonAndBoat(GameTestHelper helper) {
        externalMovement(helper, true, false);
    }

    private static void externalMovement(GameTestHelper helper, boolean hostile, boolean dormant) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Mob ship = entities.add(GenericShipSpawner.create(helper.getLevel(), SAMPLE, hostile, null));
                helper.assertTrue(ship != null, "sample definition missing");
                Vec3 origin = helper.absoluteVec(POSITION);
                ship.moveTo(origin.x, origin.y, origin.z, 0, 0);
                CompoundTag nbt = ship.saveWithoutId(new CompoundTag());
                if (dormant) {
                    nbt.putString("ShipId", "missing:external_movement");
                    // Load must restore Pos even though identifying the missing definition sets dormancy first.
                    ListTag position = new ListTag();
                    position.add(DoubleTag.valueOf(origin.x));
                    position.add(DoubleTag.valueOf(origin.y));
                    position.add(DoubleTag.valueOf(origin.z));
                    nbt.put("Pos", position);
                    ship.setPos(origin.x + 1, origin.y + 1, origin.z + 1);
                    ship.load(nbt);
                }
                helper.assertTrue(((DefinitionBackedShip) ship).definitionState().dormant() == dormant, "wrong dormancy fixture");
                helper.assertTrue(ship.position().equals(origin), "load did not restore position");
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "ship not registered");
                GameTestEntities.assertRegistered(helper, ship);
                List<String> failures = new ArrayList<>();
                BlockPos pistonPos = helper.absolutePos(new BlockPos(3, 2, 3));
                var state = Blocks.MOVING_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
                var piston = new PistonMovingBlockEntity(pistonPos, state, Blocks.STONE.defaultBlockState(), Direction.EAST, true, false);
                piston.setLevel(helper.getLevel());
                Vec3 before = ship.position();
                PistonMovingBlockEntity.tick(helper.getLevel(), pistonPos, state, piston);
                checkPosition(ship, before, dormant, "piston", failures);
                checkNbt(ship, nbt, dormant, "piston", failures);

                var boat = entities.add(EntityType.BOAT.create(helper.getLevel()));
                boat.setPos(ship.getX(), ship.getY(), ship.getZ());
                helper.assertTrue(helper.getLevel().addFreshEntity(boat), "boat not registered");
                helper.assertTrue(ship.startRiding(boat, true) && ship.getVehicle() == boat, "riding fixture rejected");
                before = ship.position();
                boat.teleportTo(boat.getX() + 4, boat.getY() + 1, boat.getZ());
                checkPosition(ship, before, dormant, "boat teleport", failures);
                checkNbt(ship, nbt, dormant, "boat teleport", failures);
                if (dormant) {
                    before = ship.position();
                    boat.positionRider(ship);
                    checkPosition(ship, before, true, "passenger setPos", failures);
                    checkNbt(ship, nbt, true, "passenger setPos", failures);
                }
                before = ship.position();
                boat.discard();
                helper.assertTrue(!ship.isPassenger(), "destroyed boat left stale passenger link");
                if (dormant) checkPosition(ship, before, true, "boat discard", failures);
                checkNbt(ship, nbt, dormant, "boat discard", failures);
                if (dormant) {
                    before = ship.position();
                    ship.setPos(before.x + 2, before.y + 1, before.z);
                    checkPosition(ship, before, true, "direct setPos", failures);
                    ship.teleportTo(before.x + 2, before.y + 1, before.z);
                    checkPosition(ship, before, true, "direct teleport", failures);
                    checkNbt(ship, nbt, true, "direct relocation", failures);
                }
                helper.assertTrue(failures.isEmpty(), String.join("; ", failures));

                if (dormant) {
                    // Restore the actual definition and check that the same object can move again.
                    CompoundTag resumed = nbt.copy();
                    resumed.putString("ShipId", SAMPLE.toString());
                    ship.load(resumed);
                    helper.assertTrue(!((DefinitionBackedShip) ship).definitionState().dormant(), "definition restoration did not wake ship");
                    ship.teleportTo(origin.x + 2, origin.y, origin.z);
                    helper.assertTrue(ship.getX() == origin.x + 2, "movement stayed locked after restoration");
                }
                helper.succeed();
            }
        }, POSITION);
    }

    private static void checkPosition(Mob ship, Vec3 before, boolean frozen, String path, List<String> failures) {
        if (ship.position().equals(before) != frozen) {
            failures.add(path + ": " + before + " -> " + ship.position() + " expected frozen=" + frozen);
        }
    }

    private static void checkNbt(Mob ship, CompoundTag nbt, boolean dormant, String path, List<String> failures) {
        if (dormant && !nbt.equals(ship.saveWithoutId(new CompoundTag()))) failures.add(path + " changed saved NBT");
    }
}
