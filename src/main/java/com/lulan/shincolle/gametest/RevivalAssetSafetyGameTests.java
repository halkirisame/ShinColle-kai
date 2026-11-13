package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RevivalAssetSafetyGameTests {
    private static final Vec3 POSITION = new Vec3(2.5D, 2D, 2.5D);

    private RevivalAssetSafetyGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unknownSavedShipClassPreservesEggAndAllExperience(GameTestHelper helper) {
        FakePlayer owner = owner(helper, false);
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        egg.getOrCreateTag().putInt("ShipClass", Integer.MAX_VALUE);
        egg.getOrCreateTag().putIntArray("StateMinor", new int[]{30});
        ItemStack expected = egg.copy();
        helper.assertTrue(use(helper, owner, egg) == InteractionResult.FAIL, "unregistered ship must fail creation");
        helper.assertTrue(ItemStack.matches(egg, expected), "failed creation must retain the complete saved egg");
        assertExperience(helper, owner, 40);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void rejectedBuiltInRevivalPreservesEggAndExperience(GameTestHelper helper) {
        rejectedRevival(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void rejectedDefinedRevivalPreservesEggAndExperience(GameTestHelper helper) {
        rejectedRevival(helper, true);
    }

    private static void rejectedRevival(GameTestHelper helper, boolean defined) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            FakePlayer owner = owner(helper, false);
            BasicEntityShip source = entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
            helper.assertTrue(source != null, "saved state fixture must exist");
            source.setStateMinor(ID.M.ShipLevel, 30);
            source.setStateMinor(ID.M.NumGrudge, 100_000);
            source.setStateFlag(ID.F.NoFuel, false);
            source.tame(owner);
            CompoundTag saved = new CompoundTag();
            CapaShipSavedValues.saveNBTData(saved, source);
            ItemStack egg = defined
                    ? GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), false)
                    : new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
            egg.getOrCreateTag().merge(saved);
            egg.getOrCreateTag().putInt("ShipClass", source.getShipClass());
            helper.assertTrue(egg.getTag().getIntArray("StateMinor")[0] == 30, "saved level must require 10 XP levels");
            ItemStack expected = egg.copy();
            int[] rejected = {0};
            Consumer<EntityJoinLevelEvent> reject = event -> {
                if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof BasicEntityShip ship
                        && ship.position().equals(helper.absoluteVec(POSITION))) {
                    rejected[0]++;
                    entities.add(ship);
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(reject);
            InteractionResult result;
            try {
                result = use(helper, owner, egg);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(reject);
            }
            helper.assertTrue(rejected[0] == 1, "real Forge entity-add event must reject exactly one revival");
            helper.assertTrue(ItemStack.matches(egg, expected), "rejected insertion must retain the complete egg");
            assertExperience(helper, owner, 40);
            helper.assertTrue(result == InteractionResult.FAIL, "rejected insertion must return failure");
            helper.assertTrue(use(helper, owner, egg).consumesAction(), "removing rejection must allow the same egg");
            var hatched = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class,
                    source.getBoundingBox().move(helper.absoluteVec(POSITION)).inflate(3D));
            hatched.forEach(entities::add);
            helper.assertTrue(egg.isEmpty() && owner.experienceLevel == 30 && hatched.size() == 1,
                    "a successful retry must create one ship and consume egg and XP exactly once");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creativeFullInventoryKeepsBuiltInSavedEggUntilSpaceAvailable(GameTestHelper helper) {
        pickup(helper, false, true);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creativeFullInventoryKeepsDefinedSavedEggUntilSpaceAvailable(GameTestHelper helper) {
        pickup(helper, true, true);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void survivalFullInventoryKeepsBothSavedEggKinds(GameTestHelper helper) {
        pickup(helper, false, false);
        pickup(helper, true, false);
        helper.succeed();
    }

    private static void pickup(GameTestHelper helper, boolean defined, boolean creative) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FakePlayer owner = owner(helper, creative);
            for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                owner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            }
            ItemStack egg = defined
                    ? GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), false)
                    : new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
            egg.setCount(3);
            egg.getOrCreateTag().putIntArray("StateMinor", new int[]{30});
            egg.getOrCreateTag().putString("owner", owner.getUUID().toString());
            ItemStack expected = egg.copy();
            BasicEntityItem drop = entities.add(new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(),
                    helper.getLevel(), 0, 0, 0, egg));
            for (int tick = 0; tick < 11; tick++) drop.tick();
            helper.assertTrue(!drop.isRemoved() && owner.getInventory().getFreeSlot() == -1
                            && owner.getInventory().getSlotWithRemainingSpace(egg) == -1,
                    "fixture must have no free slot, no merge target and a live saved-egg drop");
            drop.playerTouch(owner);
            helper.assertTrue(!drop.isRemoved() && ItemStack.matches(expected, drop.getEntityItem()),
                    "full inventory pickup must retain all saved eggs and their NBT");
            owner.getInventory().setItem(1, expected.copyWithCount(expected.getMaxStackSize() - 1));
            drop.playerTouch(owner);
            helper.assertTrue(!drop.isRemoved() && drop.getEntityItem().getCount() == 2
                            && owner.getInventory().getItem(1).getCount() == expected.getMaxStackSize(),
                    "partial pickup must merge only available space and retain the remainder");
            owner.getInventory().setItem(2, ItemStack.EMPTY);
            drop.playerTouch(owner);
            helper.assertTrue(drop.isRemoved() && ItemStack.matches(expected.copyWithCount(2),
                            owner.getInventory().getItem(2)), "remaining eggs must be recoverable without changing NBT");
        }
    }

    private static FakePlayer owner(GameTestHelper helper, boolean creative) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "revive_safety"));
        owner.getAbilities().instabuild = creative;
        owner.experienceLevel = 40;
        owner.experienceProgress = 0.25F;
        owner.totalExperience = 1234;
        return owner;
    }

    private static void assertExperience(GameTestHelper helper, FakePlayer owner, int levels) {
        helper.assertTrue(owner.experienceLevel == levels && owner.experienceProgress == 0.25F
                        && owner.totalExperience == 1234, "failed revival must preserve all experience fields");
    }

    private static InteractionResult use(GameTestHelper helper, FakePlayer owner, ItemStack egg) {
        BlockPos floor = BlockPos.containing(helper.absoluteVec(POSITION)).below();
        helper.setBlock(helper.relativePos(floor), Blocks.STONE);
        return egg.getItem().useOn(new UseOnContext(helper.getLevel(), owner, InteractionHand.MAIN_HAND, egg,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
    }
}
