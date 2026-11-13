package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RepeatedDeathCuriosGameTests {
    private static final Vec3 POSITION = new Vec3(2.5D, 2D, 2.5D);

    private RepeatedDeathCuriosGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 3800,
            batch = "isolated_repeated_death_curios")
    public static void missileGoddessRecoveryThenLethalDamagePreservesCuriosEquipment(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ListTag equipment = equipment(armor(), 0);
            BasicEntityShip ship = dyingShip(helper, entities, equipment);
            recoverWithMissile(helper, entities, ship);
            assertRestored(helper, ship, equipment);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.getStateTimer(ID.T.ImmuneTime) == 0,
                    "waiting for natural immunity expiry")).thenExecute(() -> {
                helper.assertTrue(!ship.isRemoved() && ship.deathTime < ConfigHandler.deathTime(),
                        "second lethal damage must occur before egg creation");
                ship.hurt(ship.damageSources().magic(), ship.getMaxHealth() * 2F);
                helper.assertTrue(ship.isDeadOrDying(), "second real lethal damage must reach death waiting");
                helper.assertTrue(snapshot(ship).equals(equipment),
                        "second death must capture the equipment restored to real slots");
            }).thenWaitUntil(() -> helper.assertTrue(ship.isRemoved(), "waiting for the natural death egg"))
                    .thenExecute(() -> {
                        var eggs = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                                ship.getBoundingBox().inflate(3D));
                        eggs.forEach(entities::add);
                        helper.assertTrue(eggs.size() == 1 && equipment.equals(eggs.get(0).getEntityItem()
                                        .getOrCreateTag().getList(BasicEntityShip.CURIOS_EGG_TAG, Tag.TAG_COMPOUND)),
                                "the final death egg must retain the first Curios snapshot");
                        helper.succeed();
                    });
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_repeated_death_curios")
    public static void goddessRecoveryThenMobInteractDismantleReturnsEquipmentOnce(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ItemStack armor = armor();
            ListTag equipment = equipment(armor, 0);
            BasicEntityShip ship = dyingShip(helper, entities, equipment);
            recoverWithMissile(helper, entities, ship);
            // Exercise dismantling even before checking the restored-slot invariant.
            FakePlayer operator = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "goddess_kaitai")) {
                @Override
                public boolean hasPermissions(int level) {
                    return true;
                }
            };
            operator.getAbilities().instabuild = false;
            operator.setShiftKeyDown(true);
            operator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KAITAI_HAMMER.get()));
            helper.assertTrue(operator.hasPermissions(2) && !operator.isCreative() && operator.isShiftKeyDown(),
                    "fixture needs a survival operator holding the dismantling hammer while sneaking");
            helper.assertTrue(ship.mobInteract(operator, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                            && ship.isRemoved(), "real mobInteract must dismantle the recovered ship");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(3D))
                    .forEach(entities::add);
            assertEquipmentDrop(helper, entities, ship, armor);
            helper.startSequence().thenIdle(25).thenExecute(() -> {
                assertEquipmentDrop(helper, entities, ship, armor);
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                                ship.getBoundingBox().inflate(3D)).isEmpty(),
                        "dismantling after recovery must not also generate a death egg");
                helper.succeed();
            });
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_repeated_death_curios")
    public static void goddessRecoveryDropsSnapshotOverflowAtShipFeet(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ItemStack armor = armor();
            ListTag equipment = equipment(armor, 1);
            BasicEntityShip ship = dyingShip(helper, entities, equipment);
            var stacks = CuriosApi.getCuriosInventory(ship).resolve().orElseThrow()
                    .getStacksHandler(ShipEquipSlots.SLOT_ID).orElseThrow().getStacks();
            stacks.shrink(stacks.getSlots() - 1);
            helper.assertTrue(stacks.getSlots() == 1 && snapshot(ship).equals(equipment),
                    "real storage must shrink below the saved slot after death");
            recoverWithMissile(helper, entities, ship);
            helper.assertTrue(!saved(ship).contains("DeathCuriosSnapshot")
                            && ShipCuriosIntegration.saveEquipped(ship).isEmpty(),
                    "overflow recovery must clear the snapshot without filling another slot");
            helper.assertTrue(assertEquipmentDrop(helper, entities, ship, armor).position().equals(ship.position()),
                    "overflow must drop at the recovering ship's feet");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_repeated_death_curios")
    public static void goddessRecoveryReturnsLoadedSnapshotWithoutAvailableCuriosSlot(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = fixture(helper, entities);
            ItemStack armor = armor();
            ListTag equipment = equipment(armor, 127);
            CompoundTag nbt = saved(ship);
            nbt.put("DeathCuriosSnapshot", equipment);
            ship.readAdditionalSaveData(nbt);
            helper.assertTrue(snapshot(ship).equals(equipment)
                            && ship.getCapaShipInventory().getStackInSlot(6).is(ModItems.REPAIR_GODDESS.get()),
                    "persisted snapshot and goddess must survive loading, including without Curios");
            recoverWithMissile(helper, entities, ship);
            helper.assertTrue(!saved(ship).contains("DeathCuriosSnapshot"),
                    "loaded snapshot must be cleared after recovery even without Curios");
            helper.assertTrue(assertEquipmentDrop(helper, entities, ship, armor).position().equals(ship.position()),
                    "loaded overflow must drop at the recovering ship's feet");
            helper.succeed();
        }, POSITION);
    }

    private static BasicEntityShip fixture(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
        ship.moveTo(helper.absoluteVec(POSITION));
        ship.setNoGravity(true);
        ship.setNoAi(true);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.CanDrop, true);
        ship.getCapaShipInventory().setStackInSlot(6, new ItemStack(ModItems.REPAIR_GODDESS.get()));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "victim must enter the ticking level");
        return ship;
    }

    private static BasicEntityShip dyingShip(GameTestHelper helper, GameTestEntities entities, ListTag equipment) {
        BasicEntityShip ship = fixture(helper, entities);
        ShipCuriosIntegration.loadEquipped(ship, equipment);
        helper.assertTrue(equipment.equals(ShipCuriosIntegration.saveEquipped(ship)), "real equipment must be worn");
        helper.assertTrue(ship.hurt(ship.damageSources().magic(), ship.getMaxHealth() * 2F)
                        && ship.isDeadOrDying() && ship.isPickable() && ship.deathTime == 0,
                "magic must start death waiting while leaving a pickable victim");
        helper.assertTrue(snapshot(ship).equals(equipment) && ShipCuriosIntegration.saveEquipped(ship).isEmpty()
                        && ship.getCapaShipInventory().getStackInSlot(6).is(ModItems.REPAIR_GODDESS.get()),
                "first death must capture and clear equipment and bypass the goddess");
        return ship;
    }

    private static void recoverWithMissile(GameTestHelper helper, GameTestEntities entities, BasicEntityShip ship) {
        var host = entities.add(ModEntities.DESTROYER_SHIMAKAZE_MOB.get().create(helper.getLevel()));
        host.moveTo(helper.absoluteVec(POSITION.add(10D, 0D, 0D)));
        host.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 1000F);
        host.getRand().setSeed(0L);
        ImpactMissile missile = entities.add(new ImpactMissile(helper.getLevel()));
        missile.initMissile(host, 0, 0, 1_000_000F, 0F, (float) ship.getY(),
                ship.getX(), (float) ship.getY(), ship.getZ(), 140, 0F, 1F, 0F, 0F);
        missile.moveTo(ship.position());
        missile.impact();
        helper.assertTrue(ship.getHealth() == ship.getMaxHealth() && !ship.isDeadOrDying()
                        && ship.getCapaShipInventory().getStackInSlot(6).isEmpty()
                        && ship.getStateTimer(ID.T.ImmuneTime) == 120,
                "real enemy missile splash must consume the goddess and recover the victim");
    }

    private static void assertRestored(GameTestHelper helper, BasicEntityShip ship, ListTag equipment) {
        helper.assertTrue(equipment.equals(ShipCuriosIntegration.saveEquipped(ship)),
                "goddess recovery must restore the saved count and NBT to real slots immediately");
        helper.assertTrue(!saved(ship).contains("DeathCuriosSnapshot"),
                "goddess recovery must reset the snapshot to null rather than an empty saved list");
    }

    private static ItemEntity assertEquipmentDrop(GameTestHelper helper, GameTestEntities entities,
                                                 BasicEntityShip ship, ItemStack armor) {
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(3D),
                item -> ItemStack.isSameItemSameTags(item.getItem(), armor));
        drops.forEach(entities::add);
        helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0).getItem(), armor),
                "saved equipment count and NBT must drop exactly once");
        helper.assertTrue(drops.get(0).lifespan == 6000, "overflow and dismantled equipment must use normal item lifetime");
        return drops.get(0);
    }

    private static ItemStack armor() {
        ItemStack armor = new ItemStack(ModItems.EQUIP_ARMOR.get(), 3);
        armor.getOrCreateTag().putString("Fixture", UUID.randomUUID().toString());
        return armor;
    }

    private static ListTag equipment(ItemStack armor, int slot) {
        CompoundTag entry = armor.save(new CompoundTag());
        entry.putByte("Slot", (byte) slot);
        ListTag equipment = new ListTag();
        equipment.add(entry);
        return equipment;
    }

    private static ListTag snapshot(BasicEntityShip ship) {
        return saved(ship).getList("DeathCuriosSnapshot", Tag.TAG_COMPOUND);
    }

    private static CompoundTag saved(BasicEntityShip ship) {
        CompoundTag saved = new CompoundTag();
        ship.save(saved);
        return saved;
    }

    private static final class ImpactMissile extends EntityAbyssMissile {
        private ImpactMissile(ServerLevel level) {
            super(ModEntities.ABYSS_MISSILE.get(), level);
        }

        private void impact() {
            super.onImpact(null);
        }
    }
}
