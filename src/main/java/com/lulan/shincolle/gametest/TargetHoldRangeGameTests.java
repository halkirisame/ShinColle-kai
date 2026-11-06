package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipHostileWanderGoal;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerInazumaMob;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TargetHoldRangeGameTests {
    private TargetHoldRangeGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240,
            batch = "isolated_hold_fractional_player")
    public static void fractionalRangeHoldsAndAttacksPlayer(GameTestHelper helper) {
        verifyHoldAndAttack(helper, true, 15.25D, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240,
            batch = "isolated_hold_fractional_friendly")
    public static void fractionalRangeHoldsAndAttacksFriendlyShip(GameTestHelper helper) {
        verifyHoldAndAttack(helper, false, 15.25D, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240,
            batch = "isolated_hold_integer_control")
    public static void integerBoundaryStillAttacksAndOutsideRangeReleases(GameTestHelper helper) {
        verifyHoldAndAttack(helper, true, 15D, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240,
            batch = "isolated_hold_revenge_live_range")
    public static void revengeFixedRangeUsesLiveAttackRangeAndReleasesAfterShrink(GameTestHelper helper) {
        verifyHoldAndAttack(helper, true, 15.25D, true);
    }

    private static void verifyHoldAndAttack(GameTestHelper helper, boolean playerTarget,
                                            double distance, boolean revenge) {
        Vec3 relativeHost = new Vec3(0D, 8D, 2048D);
        Vec3 relativeTarget = relativeHost.add(32D, 0D, 0D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            StationaryInazuma host = entities.add(new StationaryInazuma(helper.getLevel()));
            host.initAttrs(revenge ? 1 : 3);
            host.setNoGravity(true);
            host.setInvulnerable(true);
            Vec3 origin = helper.absoluteVec(relativeHost);
            host.moveTo(origin);
            helper.assertTrue(helper.getLevel().addFreshEntity(host), "Host registration failed");
            GameTestEntities.removeGoalsEachTick(helper, host, goal -> goal instanceof ShipHostileWanderGoal);
            Entity[] target = {null};
            int[] elapsed = {0};
            int[] samples = {0};
            int[] held = {0};
            int[] drops = {0};
            boolean[] acquired = {false};
            helper.onEachTick(() -> {
                int tick = elapsed[0]++;
                if (tick < 32) {
                    return;
                }
                if (tick == 32) {
                    helper.assertTrue(host.tickCount >= 24 && !host.isNoAi(),
                            "Host must naturally tick past goal re-registration");
                    if (revenge) {
                        helper.assertTrue(Math.round(host.getAttrs().getAttackRange()) == 11,
                                "Revenge fixture must initialize the fixed range at eleven");
                        host.initAttrs(3);
                    }
                    if (revenge) {
                        Zombie zombie = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                        zombie.setNoAi(true);
                        zombie.setInvulnerable(true);
                        zombie.setNoGravity(true);
                        zombie.moveTo(origin.add(distance, 0D, 0D));
                        helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Revenge registration failed");
                        target[0] = zombie;
                    } else if (playerTarget) {
                        ServerPlayer player = entities.add(FakePlayerFactory.get(helper.getLevel(),
                                new GameProfile(UUID.randomUUID(), "hold_range")));
                        player.setGameMode(GameType.SURVIVAL);
                        player.getAbilities().invulnerable = false;
                        player.setInvulnerable(true);
                        player.setNoGravity(true);
                        player.moveTo(origin.add(distance, 0D, 0D));
                        helper.getLevel().addNewPlayer(player);
                        GameTestEntities.onCompletion(helper, () -> helper.getLevel().removePlayerImmediately(
                                player, Entity.RemovalReason.DISCARDED), "hold player cleanup");
                        helper.assertTrue(!player.isCreative() && !player.isSpectator()
                                        && !player.getAbilities().invulnerable,
                                "Target player must be targetable survival player");
                        target[0] = player;
                    } else {
                        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_INAZUMA.get().create(helper.getLevel()));
                        ship.setPlayerUID(9501);
                        ship.setMorale(3000);
                        for (int slot = 0; slot < 3; slot++) {
                            ItemStack radar = new ItemStack(ModItems.EQUIP_RADAR.get());
                            BasicEquip.setEquipMeta(radar, 8);
                            ship.getCapaShipInventory().setStackInSlot(slot, radar);
                        }
                        ship.calcShipAttributes(31, false);
                        helper.assertTrue(ship.getAttrs().getAttackRange() >= 15.4F,
                                "Friendly fixture must allow firing at the fractional hostile boundary");
                        ship.setStateMinor(ID.M.NumGrudge, 100_000);
                        ship.setStateFlag(ID.F.NoFuel, false);
                        ship.setInvulnerable(true);
                        ship.setNoGravity(true);
                        ship.setHealth(ship.getMaxHealth());
                        GameTestEntities.removeAllGoalsEachTick(helper, ship);
                        ship.moveTo(origin.add(distance, 0D, 0D));
                        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Friendly registration failed");
                        helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0
                                        && !ship.getStateFlag(ID.F.NoFuel), "Friendly fixture needs fuel");
                        target[0] = ship;
                    }
                    GameTestEntities.assertRegistered(helper, host);
                    GameTestEntities.assertRegistered(helper, target[0]);
                    if (revenge) {
                        host.setEntityRevengeTarget(target[0]);
                        host.setEntityRevengeTime();
                    }
                }
                if (tick > 32 && tick <= 127) {
                    helper.assertTrue(Math.abs(host.getAttrs().getAttackRange() - 15.4F) < 0.001F,
                            "Fixture must keep the real scale-three range, including attribute refreshes");
                    helper.assertTrue(host.distanceToSqr(origin) < 0.001D
                                    && Math.abs(host.distanceTo(target[0]) - distance) < 0.001D,
                            "Fixture distance changed");
                    boolean locked = host.getEntityTarget() == target[0];
                    if (locked) {
                        held[0]++;
                        acquired[0] = true;
                        TargetSource expected = revenge ? TargetSource.REVENGE : TargetSource.AUTO;
                        helper.assertTrue(host.currentTargetLock().filter(lock -> lock.source() == expected).isPresent(),
                                "Target source changed from " + expected);
                    } else if (acquired[0]) {
                        drops[0]++;
                    }
                    samples[0]++;
                }
                if (tick == 127) {
                    LogUtils.getLogger().info("HOLD_RANGE target={} distance={} revenge={} samples={} held={} drops={} shots={}",
                            revenge ? "zombie" : playerTarget ? "player" : "friendly", distance, revenge,
                            samples[0], held[0], drops[0], host.shots);
                    helper.assertTrue(samples[0] == 95 && held[0] >= 93 && drops[0] == 0 && host.shots > 0,
                            "Expected stable hold and attack: samples=" + samples[0] + " held=" + held[0]
                                    + " drops=" + drops[0] + " shots=" + host.shots);
                    if (revenge) {
                        host.initAttrs(2);
                        target[0].moveTo(origin.add(14D, 0D, 0D));
                    } else {
                        target[0].moveTo(origin.add(27D, 0D, 0D));
                    }
                }
                if (tick == 143) {
                    if (revenge) {
                        helper.assertTrue(Math.abs(host.getAttrs().getAttackRange() - 12.6F) < 0.001F,
                                "Range shrink must survive recalculation");
                    }
                    helper.assertTrue(host.currentTargetLock().isEmpty() && host.getEntityTarget() == null,
                            "Target outside current hold range must be released");
                    helper.succeed();
                }
            });
        }, relativeHost, relativeTarget);
    }

    private static final class StationaryInazuma extends EntityDestroyerInazumaMob {
        private int shots;

        private StationaryInazuma(Level level) {
            super(ModEntities.DESTROYER_INAZUMA_MOB.get(), level);
        }

        @Override
        public void travel(Vec3 movement) {
            setDeltaMovement(Vec3.ZERO);
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.shots++;
            return true;
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            this.shots++;
            return true;
        }
    }
}
