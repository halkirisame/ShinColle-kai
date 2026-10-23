package com.lulan.shincolle.gametest;

import com.mojang.authlib.GameProfile;
import com.lulan.shincolle.ai.RuBurstAttackGate;
import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.combat.AttackHoldReason;
import com.lulan.shincolle.ai.domain.combat.RuBurstState;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRu;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuBurstAttackGameTests {
    private static final Vec3 START = new Vec3(1.5D, 5D, 1.5D);
    private static final Vec3 TARGET = new Vec3(31.75D, 5.8D, 7.25D);
    private static final int EGG_UID = 869_402;

    private RuBurstAttackGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ruHeavyAttackStartsFourTickBurstWithUpstreamShotCount(GameTestHelper helper) {
        scene(helper, s -> {
            for (int level : new int[]{1, 100}) {
                EntityBattleshipRu ship = s.ru(level);
                ship.tickCount = 101;
                helper.assertTrue(ship.attackEntityWithHeavyAmmo(s.target), "Stocked Ru rejected heavy start");
                List<Integer> ticks = new ArrayList<>();
                s.take(ship).forEach(shot -> { ticks.add(ship.tickCount); shot.discard(); });
                for (int tick = 102; tick <= 136; tick++) {
                    ship.tickCount = tick;
                    ship.aiStep();
                    for (EntityAbyssMissile shot : s.take(ship)) {
                        ticks.add(tick);
                        close(helper, shot.getAttrs().getAttackDamageHeavy(), ship.getAttrs().getAttackDamageHeavy() * .2F, "Shot H");
                        close(helper, shot.getAttrs().getAttrsBuffed(ID.Attrs.KB), .15F, "Shot KB");
                        helper.assertTrue(shot.life == 160, "Burst life=" + shot.life);
                        shot.discard();
                    }
                }
                List<Integer> expected = level == 1 ? List.of(104, 108, 112, 116, 120)
                        : List.of(104, 108, 112, 116, 120, 124, 128, 132);
                helper.assertTrue(ticks.equals(expected), "Actual burst ticks=" + ticks + " expected=" + expected);
                close(helper, ship.getAttackBaseDamage(0, null), ship.getAttrs().getAttackDamage() * 2F, "Ru melee");
                helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0, "Completed burst left Phase");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ruBurstConsumesResourcesOnlyOnceAndRejectsRepeatedInputs(GameTestHelper helper) {
        scene(helper, s -> {
            EntityBattleshipRu ship = s.ru(1);
            Snapshot before = Snapshot.of(ship);
            int expectedExp = (int) (ConfigHandler.expGain[2] * ship.getAttrs().getAttrsBuffed(ID.Attrs.XP));
            helper.assertTrue(ship.attackEntityWithHeavyAmmo(s.target), "Start rejected");
            helper.assertTrue(before.ammo() - ship.getStateMinor(ID.M.NumAmmoHeavy) == ship.getAmmoConsumption(), "Start ammo cost");
            helper.assertTrue(before.grudge() - ship.getGrudge() == ConfigHandler.consumeGrudgeAction[1], "Start grudge cost");
            helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) - before.exp() == expectedExp, "Start experience");
            Snapshot paid = Snapshot.of(ship);
            int count = s.take(ship).size();
            for (int tick = 101; tick <= 120; tick++) {
                ship.tickCount = tick;
                Snapshot beforeRejected = Snapshot.of(ship);
                helper.assertTrue(!ship.attackEntityWithHeavyAmmo(s.target), "BUSY accepted Entity input");
                helper.assertTrue(!ship.attackEntityWithHeavyAmmo(s.target.blockPosition()), "BUSY accepted BlockPos input");
                helper.assertTrue(Snapshot.of(ship).equals(beforeRejected), "BUSY changed accounting");
                ship.aiStep();
                for (EntityAbyssMissile shot : s.take(ship)) { count++; shot.discard(); }
            }
            helper.assertTrue(count == 5, "Repeated inputs changed burst count=" + count);
            helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoHeavy) == paid.ammo()
                    && ship.getStateMinor(ID.M.ExpCurrent) == paid.exp(), "Subsequent shots charged again");
            EntityBattleshipRu empty = s.ru(1);
            empty.setAmmoHeavy(0);
            Snapshot noAmmo = Snapshot.of(empty);
            helper.assertTrue(!empty.attackEntityWithHeavyAmmo(s.target)
                    && !empty.attackEntityWithHeavyAmmo(s.target.blockPosition()), "Empty Ru accepted start");
            helper.assertTrue(Snapshot.of(empty).equals(noAmmo) && s.take(empty).isEmpty(), "Empty start had effects");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ruBurstKeepsFlooredEntityAndBlockAim(GameTestHelper helper) {
        scene(helper, s -> {
            for (int fixture = 0; fixture < 6; fixture++) {
                boolean block = fixture >= 3;
                float scatter = new float[]{0F, .5F, .999F}[fixture % 3];
                EntityBattleshipRu ship = s.ru(1);
                fixedRandom(ship, 1F, scatter);
                BlockPos aim = block ? s.target.blockPosition()
                        : BlockPos.containing(s.target.getX(), s.target.getY() + s.target.getBbHeight() * .35D, s.target.getZ());
                helper.assertTrue(block ? ship.attackEntityWithHeavyAmmo(s.target.blockPosition())
                        : ship.attackEntityWithHeavyAmmo(s.target), "Aim start rejected");
                helper.assertTrue(s.take(ship).isEmpty(), "Expected deferred burst, observed immediate ordinary missile");
                RuBurstState.Burst payload = (RuBurstState.Burst) ship.shipCombatState().ruBurst().state().phase();
                helper.assertTrue(payload.fixedAim().equals(new RuBurstState.Aim(aim.getX(), aim.getY(), aim.getZ())), "Start did not floor aim");
                Vec3 original = s.target.position();
                s.target.setPos(original.add(40D, 10D, 40D));
                ship.setPos(ship.position().add(2D, 1D, 0D));
                for (int tick = 104; tick <= 120; tick += 4) {
                    float shotDamage = ship.getAttrs().getAttackDamageHeavy() * .2F;
                    ship.tickCount = tick;
                    ship.aiStep();
                    List<EntityAbyssMissile> shots = s.take(ship);
                    helper.assertTrue(shots.size() == 1, "Fixed aim missing shot at " + tick);
                    EntityAbyssMissile shot = shots.get(0);
                    EntityAbyssMissile expected = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel());
                    var md = ship.getMissileData(2);
                    expected.initMissile(ship, md.type, 1, ship.getAttackBaseDamage(2, null), .15F,
                            (float) ship.getY() + ship.getBbHeight() * .4F,
                            aim.getX() + scatter * 8F - 4F, aim.getY() + scatter * 4F - 2F,
                            aim.getZ() + scatter * 8F - 4F, 160, .35F, md.vel0, md.accY1, md.accY2);
                    close(helper, shot.getY(), expected.getY(), "Moving host launch height");
                    close(helper, shot.velX, expected.velX, "Fixed aim X");
                    close(helper, shot.velY, expected.velY, "Fixed aim Y/addHeight");
                    close(helper, shot.velZ, expected.velZ, "Fixed aim Z");
                    helper.assertTrue(shot.moveType == expected.moveType, "Burst ignored move=1 input/fallback");
                    close(helper, shot.getAttrs().getAttackDamageHeavy(), shotDamage, "Live H");
                    helper.assertTrue(shot.getMissileType() == md.type, "Live missile equipment type");
                    shot.discard();
                    // Set the next shot's inputs after periodic attribute recalculation has finished.
                    if (tick < 120) {
                        ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_H, 51F + tick);
                        ship.getMissileData(2).type = 2;
                        ship.getMissileData(2).movetype = 0;
                        ship.getMissileData(2).vel0 = .7F;
                    }
                }
                s.target.setPos(original);
            }
            EntityBattleshipRu missed = s.ru(1);
            Vec3 original = s.target.position();
            s.target.setPos(-20.3D, -1.6D, -10.9D);
            fixedRandom(missed, 0F, .25F);
            double[] offset = CombatHelper.calcMissAimPoint(s.target.getX(), (float) s.target.getY(), s.target.getZ(),
                    missed.getX(), missed.getZ(), s.target.getBbWidth(), .25F, .25F, .25F);
            BlockPos floor = BlockPos.containing(offset[0], offset[1] + s.target.getBbHeight() * .35D, offset[2]);
            missed.attackEntityWithHeavyAmmo(s.target);
            RuBurstState.Burst payload = (RuBurstState.Burst) missed.shipCombatState().ruBurst().state().phase();
            helper.assertTrue(payload.fixedAim().equals(new RuBurstState.Aim(floor.getX(), floor.getY(), floor.getZ())),
                    "Miss aim did not floor displaced negative coordinates");
            s.target.setPos(original);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ruBurstCancelsForActionConstraintsWithoutRefundOrResume(GameTestHelper helper) {
        scene(helper, s -> {
            for (int constraint = 0; constraint < 5; constraint++) {
                EntityBattleshipRu ship = s.ru(1);
                ship.attackEntityWithHeavyAmmo(s.target);
                helper.assertTrue(s.take(ship).isEmpty(), "Expected burst before cancellation, got ordinary immediate shot");
                ship.tickCount = 104;
                ship.aiStep();
                List<EntityAbyssMissile> airborne = s.take(ship);
                helper.assertTrue(airborne.size() == 1 && ship.getStateEmotion(ID.S.Phase) == 1, "No partially emitted burst");
                int paid = ship.getStateMinor(ID.M.NumAmmoHeavy);
                boolean oldStop = BasicEntityShip.stopAI;
                try {
                    switch (constraint) {
                        case 0 -> ship.setEntitySit(true);
                        case 1 -> { ship.setStateTimer(ID.T.CrandDelay, 0); ship.setStateMinor(ID.M.CraneState, 1); }
                        case 2 -> ship.setStateFlag(ID.F.NoFuel, true);
                        case 3 -> ship.setNoAi(true);
                        case 4 -> BasicEntityShip.stopAI = true;
                        default -> throw new IllegalStateException();
                    }
                    helper.assertTrue(switch (constraint) {
                        case 0 -> ship.getIsSitting();
                        case 1 -> ship.getStateMinor(ID.M.CraneState) == 1;
                        case 2 -> ship.getStateFlag(ID.F.NoFuel);
                        case 3 -> ship.isNoAi();
                        default -> BasicEntityShip.stopAI;
                    }, "Constraint fixture ignored");
                    Snapshot blocked = Snapshot.of(ship);
                    helper.assertTrue(!ship.attackEntityWithHeavyAmmo(s.target)
                            && !ship.attackEntityWithHeavyAmmo(s.target.blockPosition()), "Forbidden start was accepted");
                    helper.assertTrue(Snapshot.of(ship).equals(blocked), "Forbidden start changed accounting");
                    ship.tickCount = 108;
                    ship.aiStep();
                    helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0, "Constraint did not cancel Phase");
                    helper.assertTrue(!ship.shipCombatState().ruBurst().state().busy()
                            && !ship.shipCombatState().ruBurst().reasons().isEmpty(), "Cancellation lacks state/reason");
                    helper.assertTrue(s.take(ship).size() == 1 && !airborne.get(0).isRemoved(), "Cancellation changed airborne shots");
                } finally { BasicEntityShip.stopAI = oldStop; }
                ship.setEntitySit(false);
                ship.setStateTimer(ID.T.CrandDelay, 0);
                ship.setStateMinor(ID.M.CraneState, 0);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setNoAi(false);
                ship.tickCount = 112;
                ship.aiStep();
                helper.assertTrue(s.take(ship).size() == 1 && ship.getStateMinor(ID.M.NumAmmoHeavy) == paid,
                        "Permission recovery resumed or refunded burst");
                airborne.forEach(Entity::discard);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ruBurstLoadAndDeathEggNeverResumeSavedPhase(GameTestHelper helper) {
        scene(helper, s -> {
            EntityBattleshipRu source = s.ru(1);
            CompoundTag saved = ShipConsumptionDefaultsGameTests.savedFixture(source, EGG_UID);
            source.setNoAi(false);
            source.attackEntityWithHeavyAmmo(s.target);
            source.setStateEmotion(ID.S.Phase, 1, false);
            source.addAdditionalSaveData(saved);
            helper.assertTrue(saved.getIntArray("StateEmotion")[ID.S.Phase] == 1, "Saved Phase fixture ignored");
            try {
                for (int mode = 0; mode < 4; mode++) {
                    BasicEntityShip loaded = mode == 0 ? source : mode == 3
                            ? ShipConsumptionDefaultsGameTests.hatch(helper, s.entities, saved) : s.ru(1);
                    if (mode < 2) loaded.readAdditionalSaveData(saved);
                    if (mode == 2) {
                        // This loader restores ship values, not vanilla owner or inventory capabilities.
                        loaded.setOwnerUUID(source.getOwnerUUID());
                        loaded.getCapaShipInventory().deserializeNBT(saved.getCompound(CapaShipInventory.InvName));
                        loaded.attackEntityWithHeavyAmmo(s.target);
                        helper.assertTrue(RuBurstAttackGate.busy(loaded), "Direct loader did not have a running state to clear");
                        CapaShipSavedValues.loadNBTData(saved, loaded);
                    }
                    helper.assertTrue(loaded.getStateEmotion(ID.S.Phase) == 0, "Saved Phase resumed for loader=" + mode);
                    helper.assertTrue(source.getOwnerUUID().equals(loaded.getOwnerUUID()), "Load owner changed for loader=" + mode);
                    helper.assertTrue(loaded.getStateMinor(ID.M.NumAmmoHeavy) == saved.getIntArray("StateMinor")[ID.M.NumAmmoHeavy],
                            "Load inventory changed");
                    helper.assertTrue(saved.getCompound(CapaShipInventory.InvName).equals(loaded.getCapaShipInventory().serializeNBT()),
                            "Load changed equipment/cargo");
                    helper.assertTrue(!loaded.shipCombatState().ruBurst().state().busy()
                            && loaded.shipCombatState().ruBurst().state().lastEvaluatedTick().isEmpty(), "Loader retained burst/evaluation mark");
                    loaded.setNoAi(false);
                    int ammo = loaded.getStateMinor(ID.M.NumAmmoHeavy);
                    s.take(loaded).forEach(Entity::discard);
                    loaded.tickCount = 108;
                    loaded.aiStep();
                    helper.assertTrue(s.take(loaded).isEmpty() && loaded.getStateMinor(ID.M.NumAmmoHeavy) == ammo,
                            "Load emitted/charged a saved burst");
                }
                EntityBattleshipRu phaseOnly = s.ru(1);
                for (CompoundTag tag : List.of(new CompoundTag(), saved.copy())) {
                    phaseOnly.setStateEmotion(ID.S.Phase, 1, false);
                    phaseOnly.setStateEmotion(ID.S.State, 8, false);
                    CapaShipSavedValues.loadNBTData(tag, phaseOnly);
                    helper.assertTrue(phaseOnly.getStateEmotion(ID.S.Phase) == 0, "Phase-only/missing-fields load resumed burst");
                    if (tag.isEmpty()) helper.assertTrue(phaseOnly.getStateEmotion(ID.S.State) == 8, "Reset erased appearance state");
                }
            } finally { ServerDataManager.removeShipData(EGG_UID); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ruBurstClearsOnDeathRemovalAndDimensionTransfer(GameTestHelper helper) {
        scene(helper, s -> {
            for (int mode = 0; mode < 4; mode++) {
                EntityBattleshipRu ship = s.ru(1);
                ship.attackEntityWithHeavyAmmo(s.target);
                helper.assertTrue(s.take(ship).isEmpty(), "Lifecycle fixture got immediate ordinary shot instead of burst");
                ship.tickCount = 104;
                ship.aiStep();
                List<EntityAbyssMissile> airborne = s.take(ship);
                helper.assertTrue(airborne.size() == 1, "Lifecycle fixture has no emitted shot");
                if (mode == 0) ship.die(ship.damageSources().generic());
                if (mode == 1) ship.remove(Entity.RemovalReason.DISCARDED);
                if (mode == 3) clearGoals(ship);
                if (mode == 2) {
                    helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Dimension source not registered");
                    ServerLevel destination = helper.getLevel().getServer().getLevel(Level.NETHER);
                    helper.assertTrue(destination != null, "Nether unavailable");
                    Vec3 arrival = ship.position();
                    destination.getChunk(BlockPos.containing(arrival));
                    Entity moved = ship.changeDimension(destination, new ITeleporter() {
                        @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel level, Function<ServerLevel, PortalInfo> vanilla) {
                            return new PortalInfo(arrival, Vec3.ZERO, 0F, 0F);
                        }
                        @Override public Entity placeEntity(Entity entity, ServerLevel from, ServerLevel to, float yaw,
                                Function<Boolean, Entity> reposition) {
                            return reposition.apply(false);
                        }
                    });
                    if (moved != null) s.entities.add(moved);
                    helper.assertTrue(moved instanceof EntityBattleshipRu && moved != ship && ship.isRemoved(), "Real dimension transfer failed");
                    BasicEntityShip clone = (BasicEntityShip) moved;
                    helper.assertTrue(clone.getStateEmotion(ID.S.Phase) == 0, "Dimension clone resumed Phase");
                    clone.tickCount = 108;
                    clone.aiStep();
                    helper.assertTrue(destination.getEntitiesOfClass(EntityAbyssMissile.class, clone.getBoundingBox().inflate(4D),
                            shot -> shot.getHostEntity() == clone).isEmpty(), "Dimension clone emitted");
                }
                helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0, "Old entity retained Phase after lifecycle end=" + mode);
                helper.assertTrue(!RuBurstAttackGate.busy(ship), "Lifecycle retained burst authority");
                if (mode != 3) helper.assertTrue(ship.shipCombatState().ruBurst().state().lastEvaluatedTick().isEmpty(),
                        "Terminal lifecycle retained evaluation mark");
                helper.assertTrue(s.take(ship).size() == 1 && !airborne.get(0).isRemoved(), "Lifecycle removed airborne shots or emitted extra");
                airborne.forEach(Entity::discard);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240,
            batch = "isolated_ru_burst_natural")
    public static void ruBurstRunsWithoutTargetGoalAndDoesNotPauseOtherChannels(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene s = new Scene(helper, GameTestEntities.open(helper));
            EntityBattleshipRu ship = s.ru(1);
            ship.attackEntityWithHeavyAmmo(s.target);
            helper.assertTrue(s.take(ship).isEmpty(), "Expected deferred burst before channel guard");
            int light = ship.getStateMinor(ID.M.NumAmmoLight);
            ship.attackEntityWithAmmo(s.target);
            helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight) == light - ship.getAmmoConsumption(), "Burst paused light");
            ship.doHurtTarget(s.target);
            helper.assertTrue(ship.currentTargetLock().isEmpty(), "Direct fixture unexpectedly has a lock");
            int count = 0;
            for (int tick = 104; tick <= 120; tick += 4) {
                ship.tickCount = tick;
                ship.aiStep();
                for (EntityAbyssMissile shot : s.take(ship)) { count++; shot.discard(); }
            }
            helper.assertTrue(count == 5, "Lock-free burst stopped early");
            BasicEntityShip ordinary = s.entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            s.prepare(ordinary, 1);
            ordinary.attackEntityWithHeavyAmmo(s.target);
            helper.assertTrue(s.take(ordinary).size() == 1, "Ordinary heavy no longer fires immediately");
            s.take(ordinary).forEach(Entity::discard);
            NaturalBurst fixture = new NaturalBurst(s);
            for (int tick = 1; tick <= 180; tick++) helper.runAfterDelay(tick, fixture::tick);
        }, START, TARGET, ShipConsumptionDefaultsGameTests.SPAWN);
    }

    /** Uses real server ticks: goal-start parity depends on the server clock, not the entity clock. */
    private static final class NaturalBurst implements AutoCloseable {
        final Scene scene;
        final GameTestHelper helper;
        final EntityBattleshipRu ship;
        Vec3 burstStart;
        FakePlayer owner;
        int started = -1;
        int heavyReady;
        int lightReady;
        int meleeReady;
        int previousTick;
        int steps;
        boolean independentClocks;
        boolean busyHeld;
        boolean movedDuringBurst;
        boolean finished;
        final List<Integer> emissions = new ArrayList<>();

        NaturalBurst(Scene s) {
            this.scene = s;
            this.helper = s.helper;
            this.ship = s.ru(1);
            this.previousTick = this.ship.tickCount;
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo");
                field.setAccessible(true);
                ((GameTestInfo) field.get(this.helper)).addListener(new GameTestListener() {
                    @Override public void testStructureLoaded(GameTestInfo info) { }
                    @Override public void testPassed(GameTestInfo info) { close(); }
                    @Override public void testFailed(GameTestInfo info) { close(); }
                });
            } catch (ReflectiveOperationException failure) {
                throw new GameTestAssertException("Cannot attach natural burst cleanup: " + failure);
            }
            for (int x = -2; x <= 18; x++) {
                for (int z = -2; z <= 3; z++) {
                    this.helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                    for (int y = 5; y <= 10; y++) this.helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            this.owner = FakePlayerFactory.get(this.helper.getLevel(), new GameProfile(UUID.randomUUID(), "ru_burst_owner"));
            this.owner.setPos(this.ship.position().add(10D, 0D, 0D));
            this.owner.setNoGravity(true);
            this.helper.getLevel().addNewPlayer(this.owner);
            this.ship.setOwnerUUID(this.owner.getUUID());
            this.helper.assertTrue(this.ship.getOwner() == this.owner, "Natural owner unresolved");
            this.ship.setStateMinor(ID.M.FollowMin, 1);
            this.ship.setStateMinor(ID.M.FollowMax, 2);
            this.ship.setStateFlag(ID.F.UseMelee, true);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(this.ship), "Natural Ru registration failed");
            s.target.setPos(this.ship.position().add(2D, 0D, 0D));
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(s.target), "Natural target registration failed");
            this.ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Follow()));
            this.ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(s.target))));
            GameTestEntities.assertRegistered(this.helper, this.ship);
            GameTestEntities.assertRegistered(this.helper, s.target);
            this.helper.assertTrue(this.ship.getManualTarget() == s.target, "Natural manual order did not resolve target");
        }

        void tick() {
            if (this.finished) return;
            try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
                observeTick();
            }
        }

        private void observeTick() {
            Scene s = this.scene;
            GameTestHelper helper = this.helper;
            EntityBattleshipRu ship = this.ship;
            helper.assertTrue(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW, "Old setting did not fold into NEW");
            this.steps++;
            {
                if (started < 0 || ship.tickCount - started < 12) {
                    this.owner.setPos(ship.position().add(6D, 0D, 0D));
                    s.target.setPos(ship.position().add(2D, 0D, 0D));
                    s.target.setDeltaMovement(Vec3.ZERO);
                }
                ship.getAttrs().setAttrsBuffed(ID.Attrs.SPD, 100F);
                helper.assertTrue(ship.tickCount == this.previousTick + 1 || this.steps == 1 && ship.tickCount == this.previousTick,
                        "Natural fixture did not advance entity tick");
                this.previousTick = ship.tickCount;
                if (started < 0) {
                    helper.assertTrue(ship.getManualTarget() == s.target && TargetHelper.isValidAuthorityManualTarget(ship),
                            "Natural manual target became invalid at tick=" + ship.tickCount);
                    helper.assertTrue(ship.getSensing().hasLineOfSight(s.target),
                            "Natural fixture lost line of sight at " + ship.position() + " toward " + s.target.position());
                    helper.assertTrue(ShipCombatGate.active(ship), "Natural fixture has no target authority");
                }
                int beforeDuplicate = s.take(ship).size();
                RuBurstAttackGate.tick(ship);
                RuBurstAttackGate.tick(ship);
                helper.assertTrue(s.take(ship).size() == beforeDuplicate, "Duplicate gate evaluation emitted another shot");
                boolean busy = RuBurstAttackGate.busy(ship);
                if (started < 0 && busy) {
                    helper.assertTrue(ship.currentTargetLock().isPresent(), "Natural start bypassed lock");
                    started = ship.tickCount;
                    this.burstStart = ship.position();
                    heavyReady = ship.shipCombatState().timing(ship.tickCount).readyAt(WeaponChannel.HEAVY);
                    helper.assertTrue(heavyReady > started, "Successful start did not update ordinary heavy clock");
                    lightReady = ship.shipCombatState().timing(ship.tickCount).readyAt(WeaponChannel.LIGHT);
                    meleeReady = ship.shipCombatState().timing(ship.tickCount).readyAt(WeaponChannel.MELEE);
                }
                if (started >= 0 && busy) {
                    this.movedDuringBurst |= ship.position().distanceToSqr(this.burstStart) > .01D;
                    helper.assertTrue(ship.shipCombatState().timing(ship.tickCount).readyAt(WeaponChannel.HEAVY) == heavyReady,
                            "BUSY updated heavy readyAt");
                    if (ship.currentTargetLock().isPresent()) {
                        var plan = ShipCombatGate.plan(ship, ShipCombatGate.engagement(ship));
                        busyHeld |= plan.holds().getOrDefault(WeaponChannel.HEAVY, Set.of()).contains(AttackHoldReason.BUSY);
                    }
                    independentClocks |= ship.shipCombatState().timing(ship.tickCount).readyAt(WeaponChannel.LIGHT) > lightReady
                            && ship.shipCombatState().timing(ship.tickCount).readyAt(WeaponChannel.MELEE) > meleeReady;
                    if (ship.tickCount - started == 12) {
                        ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()), new CommandStateOp.Apply(new ShipCommand.CancelAttack()));
                        s.target.discard();
                        ship.applyCommandState(new CommandIssuer.Player(owner.getUUID()), new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                ShipCommandStateAdapter.handle(ship).dimension(), new CommandPos(ship.blockPosition().getX() + 6,
                                        ship.blockPosition().getY(), ship.blockPosition().getZ()), false)));
                    }
                }
                for (EntityAbyssMissile shot : s.take(ship)) { emissions.add(ship.tickCount); shot.discard(); }
                if (started >= 0 && !busy) {
                    helper.assertTrue(busyHeld && independentClocks, "BUSY held other clocks or did not reach readiness");
                    helper.assertTrue(this.movedDuringBurst, "Natural follow/guard fixture never moved during burst");
                    helper.assertTrue(ship.currentTargetLock().isEmpty(), "Natural lock-loss fixture retained target");
                    helper.assertTrue(emissions.size() == 5, "Natural burst count=" + emissions);
                    for (int i = 0; i < emissions.size(); i++) helper.assertTrue((emissions.get(i) & 3) == 0
                            && (i == 0 || emissions.get(i) - emissions.get(i - 1) == 4), "Natural burst tick sequence=" + emissions);
                    this.finished = true;
                    helper.succeed();
                    return;
                }
            }
            if (this.steps >= 180) helper.fail("Natural FireControl never completed a burst: tick=" + ship.tickCount
                    + " manual=" + (ship.getManualTarget() == s.target) + " lock=" + ship.currentTargetLock()
                    + " intent=" + ShipCombatGate.engagement(ship).intent()
                    + " plan=" + ShipCombatGate.plan(ship, ShipCombatGate.engagement(ship))
                    + " timing=" + ship.shipCombatState().timing(ship.tickCount)
                    + " range=" + ship.getAttrs().getAttackRange() + " position=" + ship.position()
                    + " target=" + s.target.position() + " noAI=" + ship.isNoAi() + " stopAI=" + BasicEntityShip.stopAI);
        }

        @Override
        public void close() {
            this.finished = true;
            if (this.owner != null) {
                this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                this.owner = null;
            }
        }
    }

    private record Snapshot(int ammo, int grudge, int exp, List<Integer> minor, int combatTick) {
        static Snapshot of(BasicEntityShip ship) {
            return new Snapshot(ship.getStateMinor(ID.M.NumAmmoHeavy), ship.getGrudge(), ship.getStateMinor(ID.M.ExpCurrent),
                    Arrays.stream(ship.getStateMinorArray()).boxed().toList(), ship.getCombatTick());
        }
    }

    private static void scene(GameTestHelper helper, Consumer<Scene> body) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                body.accept(new Scene(helper, entities));
                helper.succeed();
            }
        }, START, TARGET, ShipConsumptionDefaultsGameTests.SPAWN);
    }

    private static final class Scene {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final Cow target;

        Scene(GameTestHelper helper, GameTestEntities entities) {
            this.helper = helper;
            this.entities = entities;
            this.target = entities.add(EntityType.COW.create(helper.getLevel()));
            this.target.setNoAi(true);
            this.target.setInvulnerable(true);
            this.target.setNoGravity(true);
            this.target.setPos(helper.absoluteVec(TARGET));
        }

        EntityBattleshipRu ru(int level) {
            EntityBattleshipRu ship = this.entities.add(ModEntities.BB_RU.get().create(this.helper.getLevel()));
            prepare(ship, level);
            return ship;
        }

        void prepare(BasicEntityShip ship, int level) {
            ship.setNoAi(false);
            ship.setInvulnerable(true);
            ship.setNoGravity(true);
            ship.setPos(this.helper.absoluteVec(START));
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setAmmoLight(10_000);
            ship.setAmmoHeavy(10_000);
            ship.setShipLevel(level, false);
            ship.calcShipAttributes(31, false);
            ship.setStateMinor(ID.M.ExpCurrent, 0);
            ship.setStateMinor(ID.M.ExpNext, 1_000_000);
            ship.tickCount = 96;
            ship.aiStep();
            ship.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof ShipWanderGoal);
            ship.tickCount = 101;
            fixedRandom(ship);
            this.helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel)
                    && ship.hasAmmoHeavy() && !ship.isNoAi() && !ship.getIsSitting(), "Ru resources/permission fixture ignored");
        }

        List<EntityAbyssMissile> take(Entity host) {
            List<EntityAbyssMissile> shots = new ArrayList<>();
            for (Entity entity : this.helper.getLevel().getAllEntities()) {
                if (entity instanceof EntityAbyssMissile shot && shot.getHostEntity() == host && !shot.isRemoved()) {
                    shots.add(this.entities.add(shot));
                }
            }
            return shots;
        }
    }

    private static void fixedRandom(Entity entity) {
        fixedRandom(entity, 1F, .5F);
    }

    private static void clearGoals(BasicEntityShip ship) {
        try {
            var method = BasicEntityShip.class.getDeclaredMethod("clearAITasks");
            method.setAccessible(true);
            method.invoke(ship);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Cannot clear fixture goals: " + failure);
        }
    }

    private static void fixedRandom(Entity entity, float first, float subsequent) {
        try {
            Field field = Entity.class.getDeclaredField("random");
            field.setAccessible(true);
            field.set(entity, new LegacyRandomSource(42L) {
                int floats;
                @Override public float nextFloat() { return this.floats++ == 0 ? first : subsequent; }
            });
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Cannot set deterministic random: " + failure);
        }
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < .0001D, label + ": " + actual + " != " + expected);
    }
}
