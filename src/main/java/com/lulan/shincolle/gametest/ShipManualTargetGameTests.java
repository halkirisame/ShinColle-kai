package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipManualTargetGoal;
import com.lulan.shincolle.ai.ShipRangeTargetGoal;
import com.lulan.shincolle.ai.ShipRevengeTargetGoal;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipManualTargetGameTests {
    private ShipManualTargetGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_manual_command_holds_forty_selector_passes")
    public static void manualCommandHoldsFortySelectorPasses(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            check(f.ship.getTarget() == null, "Ship must begin out of combat");
            f.command(f.manual);
            for (int i = 0; i < 40; i++) {
                f.step();
                check(f.ship.getTarget() == f.manual, "Automatic scan replaced manual target at pass " + i);
                check(f.ship.getManualTarget() == f.manual, "Manual command disappeared");
            }
            // Out of range permits automatic targeting without discarding the command.
            Vec3 position = f.manual.position();
            f.manual.moveTo(position.x + 1000D, position.y, position.z);
            for (int i = 0; i < 12; i++) {
                f.step();
            }
            check(f.ship.getManualTarget() == f.manual, "Range must not invalidate the command");
            check(f.ship.getTarget() != f.manual, "Out-of-range manual goal did not release TARGET");
            f.manual.moveTo(position.x, position.y, position.z);
            for (int i = 0; i < 4; i++) {
                f.step();
            }
            check(f.ship.getTarget() == f.manual, "Returning manual target did not preempt auto");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_revenge_preempts_manual_without_erasing_command")
    public static void revengePreemptsManualWithoutErasingCommand(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.startManual();
            f.revenge();
            check(f.ship.getTarget() == f.other, "Revenge did not preempt manual TARGET mutex");
            check(f.ship.getManualTarget() == f.manual, "Revenge erased manual command");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_manual_resumes_after_revenge_ends")
    public static void manualResumesAfterRevengeEnds(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.startManual();
            f.revenge();
            check(f.ship.getTarget() == f.other, "Revenge must run before testing resumption");
            f.other.setHealth(0F);
            for (int i = 0; i < 4; i++) {
                f.step();
            }
            check(f.ship.getTarget() == f.manual, "Manual target did not resume after revenge ended");
            check(f.ship.getManualTarget() == f.manual, "Resumption lost the stored command");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_repeated_pointer_command_toggles_manual_off")
    public static void repeatedPointerCommandTogglesManualOff(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.startManual();
            f.command(f.other);
            for (int i = 0; i < 4; i++) {
                f.step();
            }
            check(f.ship.getManualTarget() == f.other && f.ship.getTarget() == f.other,
                    "New command must replace the old command without stale-goal cleanup erasing it");
            f.command(f.other);
            check(f.ship.getManualTarget() == null && f.ship.getTarget() == null,
                    "Repeating pointer command must clear both command and current target");
            f.command(f.manual);
            f.ship.setEntitySit(true);
            check(f.ship.getManualTarget() == null, "Sitting must clear command immediately");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_manual_expires_on_death_but_never_from_elapsed_ticks")
    public static void manualExpiresOnDeathButNeverFromElapsedTicks(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.startManual();
            for (int i = 0; i < 600; i++) {
                if (i == 300) {
                    f.rebuildTargets();
                    for (int pass = 0; pass < 4; pass++) {
                        f.step();
                    }
                }
                f.step();
                check(f.ship.getManualTarget() == f.manual, "Elapsed server ticks expired manual command");
                check(f.ship.getTarget() == f.manual, "Manual goal did not hold/reacquire current target at pass " + i);
            }
            f.manual.setHealth(0F);
            f.step();
            check(f.ship.getManualTarget() == null, "Target death did not clear manual command");
            check(f.ship.getTarget() != f.manual, "Dead manual target remains current");
            f.command(f.other);
            f.removeOther();
            f.step();
            check(f.ship.getManualTarget() == null, "Removed target must clear manual command");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_manual_command_holds_target_the_auto_selector_rejects")
    public static void manualCommandHoldsTargetTheAutoSelectorRejects(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Cow neutral = f.spawnNeutral();
            check(!new TargetHelper.Selector(f.ship).test(neutral),
                    "Fixture premise: the automatic selector must reject this mob");
            f.command(neutral);
            check(f.ship.getManualTarget() == neutral, "Pointer command did not register the manual target");
            for (int i = 0; i < 8; i++) {
                f.step();
            }
            check(f.ship.getManualTarget() == neutral,
                    "Automatic eligibility cancelled a manual command the player is allowed to give");
            check(f.ship.getTarget() == neutral,
                    "Ship lost the ordered target, so it never fires at what the player pointed at");
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Drive real registered target goals while disabling unrelated movement and weapons. */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final GameTestEntities otherEntities;
        private final BasicEntityShip ship;
        private final BasicEntityShipHostile manual;
        private final BasicEntityShipHostile other;
        private final ServerPlayer player;
        private final GoalSelector selector;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.otherEntities = GameTestEntities.open(helper);
            try {
                this.ship = (BasicEntityShip) this.entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                this.manual = (BasicEntityShipHostile) this.entities.add(
                        ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                this.other = (BasicEntityShipHostile) this.otherEntities.add(
                        ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                check(this.ship != null && this.manual != null && this.other != null, "Fixture spawn failed");
                Vec3 origin = helper.absoluteVec(new Vec3(0.5D, 4D, 0.5D));
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(origin.add(-1D, -1D, -1D)),
                        BlockPos.containing(origin.add(6D, 5D, 2D)))) {
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                Mob[] mobs = {this.ship, this.manual, this.other};
                for (int i = 0; i < mobs.length; i++) {
                    mobs[i].setNoAi(true);
                    mobs[i].moveTo(origin.x + i * 2D, origin.y, origin.z);
                    check(helper.getLevel().addFreshEntity(mobs[i]), "Fixture entity not added");
                }
                this.ship.setStateFlag(ID.F.NoFuel, false);
                this.ship.setStateFlag(ID.F.OnSightChase, false);
                this.ship.setStateFlag(ID.F.PassiveAI, false);
                this.ship.setEntitySit(false);
                this.ship.tickCount = 100;
                try {
                    Method clear = BasicEntityShip.class.getDeclaredMethod("clearAITargetTasks");
                    clear.setAccessible(true);
                    clear.invoke(this.ship);
                    this.ship.setAITargetList();
                    Field field = Mob.class.getDeclaredField("targetSelector");
                    field.setAccessible(true);
                    this.selector = (GoalSelector) field.get(this.ship);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Cannot access registered target selector", e);
                }
                check(this.selector.getAvailableGoals().stream().anyMatch(g ->
                        g.getPriority() == 3 && g.getGoal() instanceof ShipManualTargetGoal), "Missing manual priority 3");
                check(this.selector.getAvailableGoals().stream().anyMatch(g ->
                        g.getPriority() == 1 && g.getGoal() instanceof ShipRevengeTargetGoal), "Missing revenge priority 1");
                check(this.selector.getAvailableGoals().stream().anyMatch(g ->
                        g.getPriority() == 5 && g.getGoal() instanceof ShipRangeTargetGoal), "Missing auto priority 5");
                this.player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "manual_target"));
                this.player.moveTo(origin.x, origin.y, origin.z);
                this.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));
                CapaTeitoku capa = this.player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                        () -> new AssertionError("Missing admiral capability"));
                int uid = this.ship.getId() + 100000;
                capa.setPlayerUID(uid);
                capa.setSelectTeam(0);
                this.ship.setPlayerUID(uid);
                this.ship.setShipUID(uid);
                capa.setTeamMember(0, 0, uid);
                capa.setTeamSID(0, 0, this.ship.getId());
            } catch (RuntimeException | Error failure) {
                this.otherEntities.close();
                this.entities.close();
                throw failure;
            }
        }

        /** A mob the automatic selector rejects but the pointer accepts. */
        private Cow spawnNeutral() {
            Cow cow = (Cow) this.entities.add(EntityType.COW.create(this.helper.getLevel()));
            check(cow != null, "Neutral spawn failed");
            cow.setNoAi(true);
            cow.moveTo(this.ship.getX() + 3D, this.ship.getY(), this.ship.getZ());
            check(this.helper.getLevel().addFreshEntity(cow), "Neutral entity not added");
            return cow;
        }

        private void command(Entity target) {
            C2SGUIInputPacket packet = new C2SGUIInputPacket(C2SGUIInputPacket.AttackTarget,
                    new int[]{this.player.getId(), 0, target.getId()});
            try {
                Method method = C2SGUIInputPacket.class.getDeclaredMethod("handleAttackTarget", ServerPlayer.class);
                method.setAccessible(true);
                method.invoke(packet, this.player);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Pointer attack handler failed", e);
            }
        }

        private void startManual() {
            this.command(this.manual);
            for (int i = 0; i < 4; i++) {
                this.step();
            }
            check(this.ship.getManualTarget() == this.manual && this.ship.getTarget() == this.manual,
                    "Manual command did not start");
        }

        private void rebuildTargets() {
            try {
                Method clear = BasicEntityShip.class.getDeclaredMethod("clearAITargetTasks");
                clear.setAccessible(true);
                clear.invoke(this.ship);
                this.ship.setAITargetList();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Cannot rebuild target goals", e);
            }
        }

        private void revenge() {
            this.ship.setEntityRevengeTarget(this.other);
            this.ship.setEntityRevengeTime();
            for (int i = 0; i < 4; i++) {
                this.step();
            }
        }

        private void removeOther() {
            this.otherEntities.close();
        }

        private void step() {
            this.ship.tickCount += 2;
            this.ship.getSensing().tick();
            TargetHelper.updateTarget(this.ship);
            this.selector.tick();
        }

        @Override
        public void close() {
            this.otherEntities.close();
            this.entities.close();
            this.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }
}
