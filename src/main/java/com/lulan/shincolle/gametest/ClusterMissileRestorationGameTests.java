package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClusterMissileRestorationGameTests {
    private static final Vec3 START = new Vec3(1.5D, 80D, 1.5D);
    private static final Vec3 MOTION = new Vec3(.8D, .3D, -.4D);

    private ClusterMissileRestorationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void clusterMainEmitsAtFiveExactAgesThroughRealLaunches(GameTestHelper helper) {
        scene(helper, s -> {
            for (boolean equipment : new boolean[]{false, true}) {
                BasicEntityShip host = s.ship(equipment ? ModEntities.SS_KA.get() : ModEntities.BB_HIME.get());
                if (equipment) s.equip(host, 8);
                helper.assertTrue(host.getMissileData(2).type == 3, "Real launch fixture has no cluster profile");
                helper.assertTrue(host.attackEntityWithHeavyAmmo(BlockPos.containing(s.origin.add(32D, 0D, 0D))), "Heavy rejected");
                EntityAbyssMissile parent = s.onlySince(0, host);
                helper.assertTrue(parent.getMissileType() == 3, "Heavy entry did not launch type3");
                int total = 0;
                for (int age = 1; age <= 41; age++) {
                    int before = s.joined.size();
                    tick(parent);
                    List<EntityAbyssMissile> children = s.since(before, host);
                    int expected = age >= 8 && age <= 40 && age % 8 == 0 ? 1 : 0;
                    helper.assertTrue(children.size() == expected, "type3 age=" + age + " child count=" + children.size());
                    total += children.size();
                    children.forEach(Entity::discard);
                    helper.assertTrue(!parent.isRemoved(), "Parent stopped at age=" + age);
                }
                helper.assertTrue(total == 5, "Cluster total=" + total);
                parent.discard();
                s.equip(host, 5);
                helper.assertTrue(host.getMissileData(2).type == 5, "Class default overwrote equipment type");
                int before = s.joined.size();
                host.attackEntityWithHeavyAmmo(BlockPos.containing(s.origin.add(32D, 0D, 0D)));
                EntityAbyssMissile normal = s.onlySince(before, host);
                before = s.joined.size();
                for (int age = 1; age <= 41; age++) tick(normal);
                helper.assertTrue(s.since(before, host).isEmpty(), "Non-cluster emitted children");
                normal.discard();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void clusterChildSnapshotsLaunchInputsAndPreAccelerationMotion(GameTestHelper helper) {
        scene(helper, s -> {
            BasicEntityShip host = s.ship(ModEntities.SS_KA.get());
            for (int uid : new int[]{137, -100}) {
                host.setPlayerUID(uid);
                EntityAbyssMissile parent = s.parent(host, s.origin);
                parent.setPlayerUID(uid == 137 ? 138 : -101);
                // Change both host and projectile attributes after launch, not the captured inputs.
                host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_H, 600F);
                parent.getAttrs().setAttrsBuffed(ID.Attrs.ATK_H, 800F);
                parent.getAttrs().setAttrsBuffed(ID.Attrs.KB, 9F);
                ResourceLocation slow = new ResourceLocation("minecraft", "slowness");
                host.getAttackEffectMap().put(slow, new ShipAttackEffect(slow, 1, 100, 100));
                EntityAbyssMissile child = s.emit(parent, host);
                close(helper, child.position().distanceTo(s.origin.add(.8D, -.65D, -.4D)), 0D, "Child origin");
                velocity(helper, child, MOTION);
                for (int attribute : new int[]{ID.Attrs.ATK_L, ID.Attrs.ATK_H, ID.Attrs.ATK_AL, ID.Attrs.ATK_AH}) {
                    close(helper, child.getAttrs().getAttrsBuffed(attribute), 20D, "Launch input damage");
                }
                close(helper, child.getAttrs().getAttrsBuffed(ID.Attrs.KB), .15F, "Launch knockback");
                close(helper, child.getAttrs().getAttrsBuffed(ID.Attrs.DODGE), .5D, "Dodge");
                helper.assertTrue(child.life == 140 && child.moveType == 4 && child.getMissileType() == 4, "Child profile");
                close(helper, child.vel0, .5D, "Speed parameter");
                close(helper, child.accY1, -.06D, "Acceleration1");
                close(helper, child.accY2, -.06D, "Acceleration2");
                helper.assertTrue(child.t0 == 0 && child.t1 == 0 && child.getTrailDelay().remainingTicks() == 4, "Initial timing");
                helper.assertTrue(child.getHostEntity() == host && child.getPlayerUID() == uid, "Child host/owner");
                roundTrip(helper, child);
                helper.assertTrue(child.effectMap.equals(host.getAttackEffectMap()) && child.effectMap != host.getAttackEffectMap(), "Effect copy");
                host.getAttackEffectMap().clear();
                helper.assertTrue(!child.effectMap.isEmpty(), "Host mutation changed child effects");
                parent.discard();
                child.discard();
            }
            // Reinitialization must replace the launch snapshot, including the ordinary init entry.
            EntityAbyssMissile replaced = s.parent(host, s.origin);
            replaced.initMissile(host, 3, 0, 60F, .25F, (float) s.origin.y,
                    s.origin.x + 30D, (float) s.origin.y, s.origin.z, 160, 0F, .5F, 0F, 0F);
            s.prepare(replaced, s.origin);
            EntityAbyssMissile child = s.emit(replaced, host);
            close(helper, child.getAttrs().getAttackDamageHeavy(), 30D, "Reinitialized attack");
            close(helper, child.getAttrs().getAttrsBuffed(ID.Attrs.KB), .25D, "Reinitialized KB");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void clusterChildUsesTypeSpecificMotionWithoutRecursion(GameTestHelper helper) {
        scene(helper, s -> {
            BasicEntityShip host = s.ship(ModEntities.SS_KA.get());
            for (int move : new int[]{4, 2}) {
                EntityAbyssMissile child = s.parent(host, s.origin);
                child.setMissileType(4);
                child.setMovementType(move);
                child.tickCount = 0;
                child.accY1 = -.06D;
                child.life = 140;
                int before = s.joined.size();
                tick(child);
                close(helper, child.position().distanceTo(s.origin.add(MOTION)), 0D, "First displacement");
                velocity(helper, child, new Vec3(.76D, .24D, -.38D));
                for (int age = 2; age <= 140; age++) {
                    // Keep the lifetime/recursion fixture above terrain and other tests.
                    child.setPos(s.origin);
                    tick(child);
                    helper.assertTrue(!child.isRemoved(), "Child expired early at age=" + age);
                }
                helper.assertTrue(s.since(before, host).isEmpty(), "Child spawned grandchildren");
                child.setPos(s.origin);
                tick(child);
                helper.assertTrue(child.isRemoved() && child.tickCount == 141, "Child lifetime boundary");
            }
            EntityAbyssMissile normal = s.parent(host, s.origin);
            normal.setMissileType(0);
            normal.setMovementType(4);
            normal.tickCount = 0;
            tick(normal);
            velocity(helper, normal, MOTION);
            for (Vec3 origin : new Vec3[]{new Vec3(0D, 1D, 0D), s.origin}) {
                EntityAbyssMissile child = s.emit(s.parent(host, origin), host);
                velocity(helper, child, MOTION);
                helper.assertTrue(child.moveType == 4, "Raw velocity used target-distance fallback");
                close(helper, child.accY1, -.06D, "Raw acceleration");
                child.discard();
            }
            EntityAbyssMissile closeTarget = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel());
            closeTarget.initMissile(host, 0, 1, 40F, .15F, (float) host.getY(), host.getX() + 1D,
                    (float) host.getY(), host.getZ(), 140, .25F, .5F, -.06F, -.06F);
            helper.assertTrue(closeTarget.moveType == 0 && closeTarget.accY1 == 0, "Ordinary close fallback changed");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void clusterGenerationRespectsTerminalOrderingAndLoad(GameTestHelper helper) {
        scene(helper, s -> {
            BasicEntityShip host = s.ship(ModEntities.SS_KA.get());
            EntityAbyssMissile shortLife = s.parent(host, s.origin);
            shortLife.life = 7;
            int before = s.joined.size();
            tick(shortLife);
            helper.assertTrue(shortLife.isRemoved() && s.since(before, host).isEmpty(), "Expired parent emitted");
            EntityAbyssMissile noHost = s.entities.add(new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
            noHost.setMissileType(3);
            noHost.tickCount = 7;
            noHost.setPos(s.origin);
            tick(noHost);
            helper.assertTrue(noHost.isRemoved() && s.since(before, host).isEmpty(), "Host-null guard");
            EntityAbyssMissile parent = s.parent(host, s.origin);
            parent.life = 8;
            EntityAbyssMissile child = s.emit(parent, host);
            child.setPos(s.origin.add(12D, 0D, 0D));
            tick(parent);
            helper.assertTrue(parent.isRemoved() && !child.isRemoved(), "Parent expiry cleared child");
            int count = s.joined.size();
            for (int age = 1; age <= 4; age++) {
                tick(child);
                helper.assertTrue(child.getTrailDelay().remainingTicks() == 4 - age, "Child trail wait");
                roundTrip(helper, child);
            }
            CompoundTag saved = new CompoundTag();
            child.addAdditionalSaveData(saved);
            EntityAbyssMissile loaded = s.entities.add(new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
            loaded.readAdditionalSaveData(saved);
            loaded.setPos(s.origin);
            tick(loaded);
            helper.assertTrue(loaded.isRemoved() && loaded.getHostEntity() == null, "Load resumed flight");
            // Loading onto a previously initialized instance also drops its launch authority.
            EntityAbyssMissile reused = s.parent(host, s.origin);
            reused.addAdditionalSaveData(saved);
            reused.readAdditionalSaveData(saved);
            tick(reused);
            helper.assertTrue(reused.isRemoved() && s.joined.size() == count, "Same-instance load resumed emission");
            ImpactOrderMissile colliding = s.entities.add(new ImpactOrderMissile(helper.getLevel(), () -> s.since(count, host).size()));
            colliding.initMissile(host, 3, 0, 40F, .15F, (float) s.origin.y,
                    s.origin.x + 30D, (float) s.origin.y, s.origin.z, 160, 0F, .5F, 0F, 0F);
            s.prepare(colliding, s.origin);
            BlockPos block = BlockPos.containing(s.origin.add(MOTION));
            var old = helper.getLevel().getBlockState(block);
            try {
                helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                tick(colliding);
                helper.assertTrue(colliding.childrenAtImpact == 1 && colliding.isRemoved(), "Collision preceded emission");
            } finally { helper.getLevel().setBlockAndUpdate(block, old); }
        });
    }

    private static void roundTrip(GameTestHelper helper, EntityAbyssMissile child) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            child.writeSpawnData(buffer);
            EntityAbyssMissile copy = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel());
            copy.readSpawnData(buffer);
            helper.assertTrue(copy.getMissileType() == 4 && copy.moveType == 4 && copy.life == 140
                    && copy.getPlayerUID() == child.getPlayerUID()
                    && copy.getTrailDelay().remainingTicks() == child.getTrailDelay().remainingTicks(), "Child spawn snapshot");
            velocity(helper, copy, new Vec3(child.velX, child.velY, child.velZ));
            close(helper, copy.accY1, child.accY1, "Spawn acceleration");
            tick(copy);
            velocity(helper, copy, new Vec3(child.velX * .95D, child.velY + child.accY1, child.velZ * .95D));
        } finally { buffer.release(); }
    }

    private static void tick(EntityAbyssMissile shot) { shot.tickCount++; shot.tick(); }

    private static void velocity(GameTestHelper helper, EntityAbyssMissile shot, Vec3 value) {
        close(helper, shot.velX, value.x, "Velocity X");
        close(helper, shot.velY, value.y, "Velocity Y");
        close(helper, shot.velZ, value.z, "Velocity Z");
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < .00001D, label + ": " + actual + " != " + expected);
    }

    private static void scene(GameTestHelper helper, Consumer<Scene> body) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Scene scene = new Scene(helper, entities);
                MinecraftForge.EVENT_BUS.register(scene);
                try { body.accept(scene); helper.succeed(); }
                finally { MinecraftForge.EVENT_BUS.unregister(scene); }
            }
        }, START, START.add(40D, 0D, 0D));
    }

    private static final class Scene {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final Vec3 origin;
        final List<EntityAbyssMissile> joined = new ArrayList<>();

        Scene(GameTestHelper helper, GameTestEntities entities) {
            this.helper = helper;
            this.entities = entities;
            this.origin = helper.absoluteVec(START);
        }

        @SubscribeEvent
        public void joined(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof EntityAbyssMissile shot) joined.add(entities.add(shot));
        }

        BasicEntityShip ship(EntityType<? extends BasicEntityShip> type) {
            BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
            helper.assertTrue(ship != null, "Ship creation");
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            ship.setPos(origin);
            ship.setPlayerUID(137);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setAmmoHeavy(10_000);
            ship.calcShipAttributes(31, false);
            helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel) && ship.hasAmmoHeavy(), "Resource fixture");
            return ship;
        }

        void equip(BasicEntityShip ship, int meta) {
            ItemStack stack = new ItemStack(ModItems.EQUIP_AMMO.get());
            BasicEquip.setEquipMeta(stack, meta);
            ship.getCapaShipInventory().setStackInSlot(0, stack);
            ship.calcShipAttributes(31, false);
        }

        EntityAbyssMissile parent(IShipAttackBase host, Vec3 pos) {
            EntityAbyssMissile parent = entities.add(EntityAbyssMissile.createBuiltInLaunch(helper.getLevel(), host,
                    3, 0, 40F, .15F, pos, pos.x + 30D, (float) pos.y, pos.z, 160, 0F, .5F, 0F, 0F,
                    com.lulan.shincolle.ai.domain.combat.ProjectileTrailDelay.NONE));
            prepare(parent, pos);
            return parent;
        }

        void prepare(EntityAbyssMissile parent, Vec3 pos) {
            parent.setPos(pos);
            parent.velX = MOTION.x;
            parent.velY = MOTION.y;
            parent.velZ = MOTION.z;
            parent.moveType = 1;
            parent.t0 = 100D;
            parent.accY1 = .1D;
            parent.tickCount = 7;
        }

        EntityAbyssMissile emit(EntityAbyssMissile parent, Entity host) {
            int before = joined.size();
            tick(parent);
            return onlySince(before, host);
        }

        List<EntityAbyssMissile> since(int index, Entity host) {
            return joined.subList(index, joined.size()).stream().filter(shot -> shot.getHostEntity() == host).toList();
        }

        EntityAbyssMissile onlySince(int index, Entity host) {
            List<EntityAbyssMissile> shots = since(index, host);
            helper.assertTrue(shots.size() == 1, "Expected one spawned projectile, observed=" + shots.size());
            return shots.get(0);
        }
    }

    private static final class ImpactOrderMissile extends EntityAbyssMissile {
        final java.util.function.IntSupplier count;
        int childrenAtImpact = -1;
        ImpactOrderMissile(Level level, java.util.function.IntSupplier count) {
            super(ModEntities.ABYSS_MISSILE.get(), level);
            this.count = count;
        }
        @Override protected void onImpact(Entity hit) { childrenAtImpact = count.getAsInt(); super.onImpact(hit); }
    }
}
