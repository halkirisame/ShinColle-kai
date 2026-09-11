package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountLifecycleGameTests {
    private MountLifecycleGameTests() {
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.CV_WD.get().create(helper.getLevel()));
        helper.assertTrue(ship != null && ship.hasShipMounts(), "fixture must summon a mount");
        ship.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        ship.setNoGravity(true);
        ship.setStateMinor(ID.M.NumGrudge, 10000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.calcShipAttributes(31, false);
        helper.assertTrue(ship.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "fixture speed");
        helper.getLevel().addFreshEntity(ship);
        ship.tickCount = 16;
        ship.aiStep(); // Register the delayed AI before exercising the toggle.
        ship.setNoAi(true);
        return ship;
    }

    private static BasicEntityMount summon(GameTestHelper helper, GameTestEntities entities, BasicEntityShip ship) {
        ship.setStateEmotion(ID.S.State, 1, false);
        helper.assertTrue(ship.canSummonMounts(), "fixture must have fuel and enabled toggle");
        ship.tickCount = 32;
        ship.aiStep();
        helper.assertTrue(ship.getVehicle() instanceof BasicEntityMount, "ON must create a mount");
        return entities.add((BasicEntityMount) ship.getVehicle());
    }

    private static void disable(BasicEntityShip ship) {
        ship.setStateEmotion(ID.S.State, 0, false);
        ship.tickCount = 48;
        ship.aiStep(); // Observe immediately after the real cancellation branch, before mount.tick().
    }

    private static long count(GameTestHelper helper, BasicEntityShip ship) {
        long count = 0;
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof BasicEntityMount mount && mount.isAlive() && mount.getHost() == ship) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void threeToggleCyclesNeverAccumulateMounts(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            for (int cycle = 0; cycle < 3; cycle++) {
                BasicEntityMount mount = summon(helper, entities, ship);
                helper.assertTrue(count(helper, ship) == 1, "ON count at cycle " + cycle);
                disable(ship);
                mount.tick();
                helper.assertTrue(count(helper, ship) == 0, "OFF count at cycle " + cycle);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void disableImmediatelyRemovesMountAndReleasesPlayer(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BasicEntityMount mount = summon(helper, entities, ship);
            Player player = entities.add(helper.makeMockPlayer());
            helper.assertTrue(player.startRiding(mount, true), "player must board before OFF");
            disable(ship);
            helper.assertTrue(!player.isPassenger() && mount.getPassengers().isEmpty(), "OFF must release all riders");
            helper.assertTrue(mount.isRemoved() && count(helper, ship) == 0, "OFF must remove before next mount tick");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dismountedOrphanPreventsDuplicateThenDiscards(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BasicEntityMount mount = summon(helper, entities, ship);
            ship.stopRiding();
            try {
                ship.updateMountSummon();
                helper.assertTrue(count(helper, ship) == 1, "existing orphan must prevent duplicate before its tick");
                mount.tick();
                helper.assertTrue(mount.isRemoved(), "dismounted orphan must discard on first tick");
            } finally {
                if (ship.getVehicle() instanceof BasicEntityMount extra) {
                    entities.add(extra);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deadHostMountDiscards(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BasicEntityMount mount = summon(helper, entities, ship);
            ship.setHealth(0);
            mount.tick();
            helper.assertTrue(mount.isRemoved() && !ship.isPassenger(), "dead host mount must discard");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void removedHostMountDiscards(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BasicEntityMount mount = summon(helper, entities, ship);
            ship.discard();
            mount.tick();
            helper.assertTrue(mount.isRemoved(), "removed host mount must discard");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedOrphanDiscardsWithoutDrops(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BasicEntityMount original = summon(helper, entities, ship);
            CompoundTag tag = new CompoundTag();
            original.addAdditionalSaveData(tag);
            BasicEntityMount orphan = entities.add(ship.summonMountEntity());
            orphan.readAdditionalSaveData(tag);
            orphan.setPos(ship.position());
            helper.getLevel().addFreshEntity(orphan);
            long itemsBefore = helper.getLevel().getEntitiesOfClass(ItemEntity.class, orphan.getBoundingBox().inflate(4)).size();
            orphan.tick();
            helper.assertTrue(orphan.isRemoved(), "saved orphan must discard on first resolved tick");
            helper.assertTrue(original.isAlive() && ship.getVehicle() == original, "valid mount must survive");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, orphan.getBoundingBox().inflate(4)).size()
                    == itemsBefore, "orphan removal must not drop items");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unresolvedSavedHostGetsGraceThenDiscards(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_CAWD.get().create(helper.getLevel()));
            CompoundTag tag = new CompoundTag();
            tag.putUUID("HostUUID", UUID.randomUUID());
            mount.readAdditionalSaveData(tag);
            mount.setNoGravity(true);
            mount.setInvulnerable(true);
            mount.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 200, 1)));
            helper.assertTrue(mount.isNoGravity(), "unresolved fixture must not fall");
            for (int tick = 1; tick < 100; tick++) {
                mount.tick();
                helper.assertTrue(!mount.isRemoved(), "unresolved host grace tick " + tick
                        + " reason=" + mount.getRemovalReason() + " health=" + mount.getHealth()
                        + " pos=" + mount.position());
            }
            mount.tick();
            helper.assertTrue(mount.isRemoved(), "unresolved orphan must expire on tick 100");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void delayedSavedHostResolutionPreservesRiddenMount(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.CV_WD.get().create(helper.getLevel()));
            ship.calcShipAttributes(31, false);
            BasicEntityMount mount = entities.add(ship.summonMountEntity());
            CompoundTag tag = new CompoundTag();
            tag.putUUID("HostUUID", ship.getUUID());
            mount.readAdditionalSaveData(tag);
            mount.setNoGravity(true);
            mount.setInvulnerable(true);
            mount.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 200, 1)));
            for (int tick = 0; tick < 20; tick++) {
                mount.tick();
            }
            helper.getLevel().addFreshEntity(ship);
            helper.assertTrue(ship.startRiding(mount, true), "restore saved passenger");
            mount.tick();
            helper.assertTrue(mount.getHost() == ship && mount.isAlive() && ship.getVehicle() == mount,
                    "delayed valid host must resolve and survive");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountWithoutHostIdentityDiscards(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_CAWD.get().create(helper.getLevel()));
            mount.tick();
            helper.assertTrue(mount.isRemoved(), "mount without host identity must discard on first tick");
            helper.succeed();
        }
    }
}
