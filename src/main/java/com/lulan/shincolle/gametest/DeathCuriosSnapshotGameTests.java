package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeathCuriosSnapshotGameTests {
    private static final String SNAPSHOT_TAG = "DeathCuriosSnapshot";
    private static final Vec3 POSITION = new Vec3(2.5D, 2D, 2.5D);

    private DeathCuriosSnapshotGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 3800,
            batch = "isolated_death_curios_reload")
    public static void curiosDeathReloadKeepsEquipmentThroughHatching(GameTestHelper helper) {
        equippedDeath(helper, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 3800,
            batch = "isolated_death_curios_control")
    public static void curiosDeathWithoutReloadKeepsEquipmentThroughHatching(GameTestHelper helper) {
        equippedDeath(helper, false);
    }

    private static void equippedDeath(GameTestHelper helper, boolean reload) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "death_curios"));
            owner.moveTo(helper.absoluteVec(POSITION.add(5D, 0D, 0D)));
            owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing player capability")).setPlayerUID(67_456);
            owner.getAbilities().instabuild = false;
            owner.experienceLevel = 100;
            helper.getLevel().addNewPlayer(owner);
            GameTestEntities.onCompletion(helper, () -> helper.getLevel().removePlayerImmediately(owner,
                    Entity.RemovalReason.DISCARDED), "death Curios owner cleanup");

            BasicEntityShip source = fixture(helper, entities);
            source.tame(owner);
            source.setPlayerUID(67_456);
            helper.assertTrue(source.getOwner() == owner, "source must resolve its owner");
            ListTag expected = equipment();
            ShipCuriosIntegration.loadEquipped(source, expected);
            helper.assertTrue(expected.equals(ShipCuriosIntegration.saveEquipped(source)),
                    "real Curios slots must hold the complete fixture; expected=" + expected
                            + " actual=" + ShipCuriosIntegration.saveEquipped(source));
            kill(helper, source);
            helper.assertTrue(ShipCuriosIntegration.saveEquipped(source).isEmpty(),
                    "death must clear real Curios slots before their death drops");

            BasicEntityShip dying = reload ? reload(helper, entities, source, save(helper, source)) : source;
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(dying.isRemoved(),
                    "waiting for the natural delayed death tick")).thenExecute(() -> {
                ItemStack egg = deathEgg(helper, entities);
                helper.assertTrue(expected.equals(egg.getOrCreateTag().getList(
                                BasicEntityShip.CURIOS_EGG_TAG, Tag.TAG_COMPOUND)),
                        "death egg must retain Curios equipment, slots, counts and item NBT after reload=" + reload);
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper),
                                item -> item.getItem().is(ModItems.EQUIP_CANNON.get())
                                        || item.getItem().is(ModItems.EQUIP_ARMOR.get())).isEmpty(),
                        "Curios equipment must not also drop loose");

                BlockPos floor = BlockPos.containing(helper.absoluteVec(POSITION)).below();
                helper.setBlock(helper.relativePos(floor), Blocks.STONE);
                helper.assertTrue(egg.getItem().useOn(new UseOnContext(helper.getLevel(), owner,
                                InteractionHand.MAIN_HAND, egg,
                                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)))
                                .consumesAction(), "saved death egg must hatch through useOn");
                List<BasicEntityShip> hatched = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class,
                        bounds(helper), ship -> ship.isAlive());
                hatched.forEach(entities::add);
                helper.assertTrue(hatched.size() == 1 && egg.isEmpty(), "exactly one ship must hatch");
                helper.assertTrue(expected.equals(ShipCuriosIntegration.saveEquipped(hatched.get(0))),
                        "hatching must restore the original Curios slots, counts and item NBT");
                helper.succeed();
            });
        }, POSITION, POSITION.add(5D, 0D, 0D));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deathSnapshotNbtDistinguishesAbsentAndEmpty(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "ship fixture must exist");
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            CompoundTag absent = save(helper, ship);
            helper.assertTrue(!absent.contains(SNAPSHOT_TAG), "living ships must not gain a death snapshot");
            CompoundTag occupied = absent.copy();
            occupied.put(SNAPSHOT_TAG, equipment());
            ship.load(occupied);
            helper.assertTrue(equipment().equals(save(helper, ship).getList(SNAPSHOT_TAG, Tag.TAG_COMPOUND)),
                    "snapshot payload must survive without an optional-mod API");
            CompoundTag empty = absent.copy();
            empty.put(SNAPSHOT_TAG, new ListTag());
            ship.load(empty);
            CompoundTag savedEmpty = save(helper, ship);
            helper.assertTrue(savedEmpty.contains(SNAPSHOT_TAG, Tag.TAG_LIST)
                            && savedEmpty.getList(SNAPSHOT_TAG, Tag.TAG_COMPOUND).isEmpty(),
                    "an empty captured snapshot must remain present and empty");
            ship.load(absent);
            helper.assertTrue(!save(helper, ship).contains(SNAPSHOT_TAG),
                    "loading old NBT must clear an earlier snapshot back to absent");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 3800,
            batch = "isolated_death_snapshot_optional_boundary")
    public static void savedDeathSnapshotReachesEggWithoutOptionalApi(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip source = fixture(helper, entities);
            kill(helper, source);
            CompoundTag saved = save(helper, source);
            ListTag expected = equipment();
            saved.put(SNAPSHOT_TAG, expected.copy());
            BasicEntityShip restored = reload(helper, entities, source, saved);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(restored.isRemoved(),
                    "waiting for saved corpse to complete its natural death ticks")).thenExecute(() -> {
                ItemStack egg = deathEgg(helper, entities);
                helper.assertTrue(expected.equals(egg.getOrCreateTag().getList(
                                BasicEntityShip.CURIOS_EGG_TAG, Tag.TAG_COMPOUND)),
                        "persisted snapshot must reach the egg even when Curios is not installed");
                helper.succeed();
            });
        }, POSITION);
    }

    private static BasicEntityShip fixture(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "ship fixture must exist");
        ship.moveTo(helper.absoluteVec(POSITION));
        ship.setNoGravity(true);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.CanDrop, true);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "ship must enter the level");
        return ship;
    }

    private static void kill(GameTestHelper helper, BasicEntityShip ship) {
        helper.assertTrue(ship.hurt(ship.damageSources().magic(), ship.getMaxHealth() * 2F)
                        && ship.isDeadOrDying() && ship.deathTime == 0,
                "real lethal damage must start death waiting before its first tick");
    }

    private static CompoundTag save(GameTestHelper helper, BasicEntityShip ship) {
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(ship.save(saved), "full entity NBT must be saved");
        return saved;
    }

    private static BasicEntityShip reload(GameTestHelper helper, GameTestEntities entities,
                                          BasicEntityShip source, CompoundTag saved) {
        source.discard();
        Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(loaded instanceof BasicEntityShip, "full NBT must recreate the ship");
        BasicEntityShip restored = entities.add((BasicEntityShip) loaded);
        helper.assertTrue(restored != source && restored.getUUID().equals(source.getUUID())
                        && restored.isDeadOrDying(), "a new entity must restore the saved corpse state");
        helper.assertTrue(helper.getLevel().addFreshEntity(restored), "reloaded corpse must enter the level");
        return restored;
    }

    private static ItemStack deathEgg(GameTestHelper helper, GameTestEntities entities) {
        List<BasicEntityItem> eggs = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class, bounds(helper),
                item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
        eggs.forEach(entities::add);
        helper.assertTrue(eggs.size() == 1 && eggs.get(0).getEntityItem().getCount() == 1,
                "death must create exactly one saved egg");
        ItemStack egg = eggs.get(0).getEntityItem().copy();
        eggs.get(0).discard();
        return egg;
    }

    private static AABB bounds(GameTestHelper helper) {
        return new AABB(BlockPos.containing(helper.absoluteVec(POSITION))).inflate(3D);
    }

    private static ListTag equipment() {
        ListTag result = new ListTag();
        ItemStack cannon = new ItemStack(ModItems.EQUIP_CANNON.get());
        cannon.getOrCreateTag().putString("Fixture", "saved_curios_cannon");
        CompoundTag first = cannon.save(new CompoundTag());
        first.putByte("Slot", (byte) 2);
        result.add(first);
        CompoundTag second = new ItemStack(ModItems.EQUIP_ARMOR.get()).save(new CompoundTag());
        second.putByte("Slot", (byte) 0);
        result.add(0, second);
        return result;
    }
}
