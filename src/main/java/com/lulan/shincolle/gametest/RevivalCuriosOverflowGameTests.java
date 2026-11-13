package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.GenericShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RevivalCuriosOverflowGameTests {
    private static final Vec3 SPAWN = new Vec3(2.5D, 2D, 2.5D);
    private static final Vec3 PLAYER = new Vec3(8.5D, 2D, 2.5D);

    private RevivalCuriosOverflowGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void revivalReturnsOnlyOutOfRangeEquipmentToReviver(GameTestHelper helper) {
        revive(helper, false, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void fullSurvivalRevivalDropsOverflowAtReviverFeet(GameTestHelper helper) {
        revive(helper, false, false, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void fullCreativeRevivalMergesThenDropsOverflowWithoutLoss(GameTestHelper helper) {
        revive(helper, false, false, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void revivalWithoutCuriosStorageReturnsEverySavedStack(GameTestHelper helper) {
        revive(helper, false, true, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void definedShipRevivalReturnsUnavailableCuriosEquipment(GameTestHelper helper) {
        // GameTest data gives every generic ship the ship-equip slot, so only out-of-range entries are unavailable.
        revive(helper, true, false, false, false);
    }

    private static void revive(GameTestHelper helper, boolean defined, boolean missing, boolean full, boolean creative) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            FakePlayer reviver = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "curios_reviver"));
            reviver.moveTo(helper.absoluteVec(PLAYER));
            reviver.experienceLevel = 40;
            reviver.getAbilities().instabuild = creative;
            BasicEntityShip source = entities.add((missing ? ModEntities.DESTROYER_AKATSUKI : ModEntities.DESTROYER_I)
                    .get().create(helper.getLevel()));
            helper.assertTrue(source != null && ShipEquipSlots.slotCount() > 0, "fixture requires a ship and slots");
            source.setStateMinor(ID.M.ShipLevel, 30);
            source.setStateMinor(ID.M.NumGrudge, 100_000);
            source.setStateFlag(ID.F.NoFuel, false);
            source.setTame(true);
            source.setOwnerUUID(UUID.randomUUID());
            ItemStack fitted = new ItemStack(ModItems.EQUIP_ARMOR.get());
            fitted.getOrCreateTag().putString("Fixture", UUID.randomUUID().toString());
            ItemStack overflow = new ItemStack(Items.DIAMOND, 3);
            overflow.getOrCreateTag().putString("Fixture", UUID.randomUUID().toString());
            ListTag savedEquipment = new ListTag();
            savedEquipment.add(entry(fitted, 0));
            savedEquipment.add(entry(overflow, ShipEquipSlots.slotCount()));
            CompoundTag saved = new CompoundTag();
            CapaShipSavedValues.saveNBTData(saved, source);
            saved.putInt("ShipClass", source.getShipClass());
            saved.putString("owner", source.getOwnerUUID().toString());
            saved.put(BasicEntityShip.CURIOS_EGG_TAG, savedEquipment);
            ItemStack egg = defined ? GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), false)
                    : new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
            egg.getOrCreateTag().merge(saved);
            ItemStack originalEgg = egg.copy();
            if (full) {
                for (int i = 0; i < reviver.getInventory().getContainerSize(); i++) {
                    reviver.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
                }
                if (creative) reviver.getInventory().setItem(1, overflow.copyWithCount(63));
                helper.assertTrue(reviver.getInventory().getFreeSlot() == -1, "fixture inventory must really be full");
            }
            int[] rejected = {0};
            Consumer<EntityJoinLevelEvent> reject = event -> {
                if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof BasicEntityShip ship
                        && ship.position().equals(helper.absoluteVec(SPAWN))) {
                    rejected[0]++;
                    entities.add(ship);
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(reject);
            InteractionResult failed;
            try {
                failed = use(helper, reviver, egg);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(reject);
            }
            helper.assertTrue(rejected[0] == 1 && failed == InteractionResult.FAIL
                            && ItemStack.matches(egg, originalEgg) && reviver.experienceLevel == 40,
                    "rejected revival must retain the egg and XP");
            helper.assertTrue(count(reviver, overflow) == (creative && full ? 63 : 0)
                            && drops(helper, entities, overflow).isEmpty() && count(reviver, fitted) == 0,
                    "a rejected revival must not refund equipment or duplicate the retained egg");
            helper.assertTrue(use(helper, reviver, egg).consumesAction(), "overflow must not prevent revival");
            List<BasicEntityShip> hatched = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class,
                    new AABB(BlockPos.containing(helper.absoluteVec(SPAWN))).inflate(2D));
            hatched.forEach(entities::add);
            helper.assertTrue(hatched.size() == 1 && !hatched.get(0).isOwnedBy(reviver),
                    "saved owner must stay distinct from the player receiving the refund");
            boolean hasStorage = !missing && ModList.get().isLoaded("curios");
            if (ModList.get().isLoaded("curios")) {
                ListTag equipped = ShipCuriosIntegration.saveEquipped(hatched.get(0));
                ListTag expected = new ListTag();
                if (hasStorage) expected.add(entry(fitted, 0));
                helper.assertTrue(equipped.equals(expected), "only equipment fitting current storage must be restored");
            }
            helper.assertTrue(egg.getCount() == (creative ? 1 : 0) && reviver.experienceLevel == (creative ? 40 : 30),
                    "successful revival must retain its ordinary egg and XP costs");
            List<ItemEntity> loose = drops(helper, entities, overflow);
            helper.assertTrue(count(reviver, overflow) == (full ? (creative ? 64 : 0) : 3),
                    "overflow must enter the reviver inventory up to its real remaining capacity");
            helper.assertTrue(loose.size() == (full ? 1 : 0), "only the unmerged overflow must drop");
            if (full) {
                ItemEntity drop = loose.get(0);
                helper.assertTrue(ItemStack.matches(drop.getItem(), overflow.copyWithCount(creative ? 2 : 3))
                                && drop.position().equals(reviver.position()) && drop.lifespan == 6000,
                        "overflow must be a normal expiring item at the reviver feet, with count and NBT intact");
            }
            helper.assertTrue(count(reviver, fitted) == (hasStorage || full ? 0 : 1)
                            && drops(helper, entities, fitted).size() == (!hasStorage && full ? 1 : 0),
                    "equipment without any available storage must also return to the reviver");
            helper.succeed();
        }, SPAWN, PLAYER);
    }

    private static CompoundTag entry(ItemStack stack, int slot) {
        CompoundTag entry = stack.save(new CompoundTag());
        entry.putByte("Slot", (byte) slot);
        return entry;
    }

    private static int count(FakePlayer player, ItemStack expected) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(stack, expected)) count += stack.getCount();
        }
        return count;
    }

    private static List<ItemEntity> drops(GameTestHelper helper, GameTestEntities entities, ItemStack expected) {
        List<ItemEntity> result = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(BlockPos.containing(helper.absoluteVec(PLAYER))).inflate(2D),
                item -> ItemStack.isSameItemSameTags(item.getItem(), expected));
        result.forEach(entities::add);
        return result;
    }

    private static InteractionResult use(GameTestHelper helper, FakePlayer player, ItemStack egg) {
        BlockPos floor = BlockPos.containing(helper.absoluteVec(SPAWN)).below();
        helper.setBlock(helper.relativePos(floor), Blocks.STONE);
        return egg.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, egg,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
    }
}
