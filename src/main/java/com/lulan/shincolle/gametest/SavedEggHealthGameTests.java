package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFleeGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SavedEggHealthGameTests {
    private static final Vec3 HATCH = new Vec3(4.5D, 2D, 2.5D);
    private static final Vec3 GUARD = new Vec3(16.5D, 2D, 2.5D);
    private static final Vec3 OWNER = new Vec3(24.5D, 2D, 2.5D);

    private SavedEggHealthGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_saved_egg_health_guard", timeoutTicks = 240)
    public static void actualDeathEggHealsFinalAttributesAndNaturallyExecutesSavedGuard(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            for (int x = 0; x <= 25; x++) {
                for (int z = 0; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "saved_egg_guard"));
            owner.moveTo(helper.absoluteVec(OWNER));
            owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing player capability")).setPlayerUID(67_441);
            helper.getLevel().addNewPlayer(owner);
            GameTestEntities.onCompletion(helper, () -> helper.getLevel().removePlayerImmediately(owner,
                    Entity.RemovalReason.DISCARDED), "saved egg owner cleanup");
            owner.getAbilities().instabuild = false;
            owner.experienceLevel = 100;

            BasicEntityShip source = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            helper.assertTrue(source != null, "Hibiki fixture must exist");
            source.moveTo(helper.absoluteVec(HATCH));
            source.tame(owner);
            source.setOwnerUUID(owner.getUUID());
            source.setPlayerUID(67_441);
            source.setStateMinor(ID.M.ShipLevel, 37);
            source.getAttrs().setAttrsBonus(ID.AttrsBase.HP, 2);
            source.getCapaShipInventory().setStackInSlot(0, new ItemStack(ModItems.EQUIP_ARMOR.get()));
            source.setStateMinor(ID.M.NumGrudge, 100_000);
            source.setStateMinor(ID.M.Morale, 3000);
            source.setStateMinor(ID.M.FleeHP, 50);
            source.setStateMinor(ID.M.FollowMin, 2);
            source.setStateMinor(ID.M.FollowMax, 6);
            source.setStateFlag(ID.F.NoFuel, false);
            source.setStateFlag(ID.F.CanDrop, true);
            source.setStateFlag(ID.F.PassiveAI, true);
            source.setStateFlag(ID.F.PickItem, false);
            source.setStateFlag(ID.F.UseMelee, false);
            source.setEntitySit(false);
            source.calcShipAttributes(31, true);
            source.setHealth(source.getMaxHealth());
            BlockPos target = BlockPos.containing(helper.absoluteVec(GUARD));
            var dimension = helper.getLevel().dimension().location();
            DimensionKey key = new DimensionKey(dimension.getNamespace(), dimension.getPath());
            CommandPos position = new CommandPos(target.getX(), target.getY(), target.getZ());
            source.applyCommandState(new CommandIssuer.Player(owner.getUUID()), new CommandStateOp.Apply(
                    new ShipCommand.GuardPosition(key, position, false)));
            MovementOrder expected = new MovementOrder.GuardPosition(key, position, false);
            helper.assertTrue(helper.getLevel().addFreshEntity(source), "source must be added");
            helper.assertTrue(source.getOwner() == owner && source.getLevel() == 37
                            && source.getMaxHealth() > 4F && expected.equals(source.getCommandState().movement()),
                    "source must resolve its owner and hold the saved guard and grown HP");
            float expectedMax = source.getMaxHealth();
            helper.assertTrue(source.hurt(source.damageSources().magic(), expectedMax * 2F)
                            && source.isDeadOrDying(), "real lethal damage must kill the source");
            source.deathTime = ConfigHandler.deathTime() - 1;
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(source.isRemoved(),
                    "waiting for the natural death tick")).thenExecute(() -> {
                List<BasicEntityItem> drops = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                        new AABB(BlockPos.containing(helper.absoluteVec(HATCH))).inflate(3D),
                        item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
                drops.forEach(entities::add);
                helper.assertTrue(drops.size() == 1, "real death must leave one saved egg");
                ItemStack egg = drops.get(0).getEntityItem().copy();
                drops.get(0).discard();
                helper.assertTrue(egg.hasTag() && egg.getTag().contains("StateMinor"), "egg must contain saved state");
                BasicEntityShip restored = hatch(helper, entities, owner, egg);
                helper.assertTrue(restored.getHealth() == restored.getMaxHealth(),
                        "actual death egg HP=" + restored.getHealth() + "/" + restored.getMaxHealth());
                helper.assertTrue(Math.abs(restored.getMaxHealth() - expectedMax) < 0.001F
                                && restored.getAttrs().getAttrsBonus()[ID.AttrsBase.HP] == 2
                                && restored.getCapaShipInventory().getStackInSlot(0).is(ModItems.EQUIP_ARMOR.get()),
                        "healing must use final saved level, bonus and equipment attributes");
                helper.assertTrue(egg.isEmpty() && owner.experienceLevel == 100 - 37 / 3,
                        "revival must keep the egg and XP cost contract");
                restored.setInvulnerable(true);
                GameTestEntities.removeGoalsEachTick(helper, restored, goal -> goal instanceof ShipWanderGoal);
                double initialDistance = restored.distanceToSqr(Vec3.atBottomCenterOf(target));
                boolean[] guarding = {false};
                helper.onEachTick(() -> {
                    if (restored.tickCount < 24 || restored.isRemoved()) return;
                    helper.assertTrue(restored.getOwner() == owner && restored.getStateMinor(ID.M.NumGrudge) > 0
                                    && !restored.getStateFlag(ID.F.NoFuel) && !restored.isNoAi()
                                    && expected.equals(restored.getCommandState().movement()),
                            "restored natural AI must retain owner, fuel and the original guard");
                    helper.assertTrue(!(ShipMovementGate.intent(restored) instanceof MovementIntent.Flee)
                                    && MovementPlanParityGameTests.selector(restored).getRunningGoals()
                                            .noneMatch(goal -> goal.getGoal() instanceof ShipFleeGoal),
                            "fully healed revival must not execute low-HP Flee");
                    guarding[0] |= ShipMovementGate.intent(restored) instanceof MovementIntent.GuardPosition
                            && MovementPlanParityGameTests.selector(restored).getRunningGoals()
                                    .anyMatch(goal -> goal.getGoal() instanceof ShipGuardingGoal);
                });
                helper.startSequence().thenWaitUntil(() -> helper.assertTrue(restored.tickCount >= 24,
                        "waiting for natural AI re-registration")).thenIdle(60).thenExecute(() -> {
                    helper.assertTrue(guarding[0] && restored.distanceToSqr(Vec3.atBottomCenterOf(target))
                                    < initialDistance - 1D,
                            "the naturally ticking revived ship must execute and walk toward its saved guard");
                    helper.succeed();
                });
            });
        }, HATCH, new Vec3(10.5D, 2D, 2.5D), GUARD, OWNER);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void freshBuiltInEggKeepsLevelOneFullHealthWithoutSavedState(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "fresh_egg_health"));
                player.getAbilities().instabuild = false;
                player.experienceLevel = 0;
                ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
                egg.getOrCreateTag().putInt("ShipClass", ID.ShipClass.DDHibiki);
                helper.assertTrue(!egg.getTag().contains("StateMinor"), "fresh egg must not look saved");
                BasicEntityShip ship = hatch(helper, entities, player, egg);
                helper.assertTrue(ship.getLevel() == 1 && ship.getHealth() == ship.getMaxHealth()
                                && player.experienceLevel == 0 && egg.isEmpty(),
                        "fresh egg must retain level one, full health, no XP cost and consumption");
                helper.succeed();
            }
        }, HATCH);
    }

    private static BasicEntityShip hatch(GameTestHelper helper, GameTestEntities entities, FakePlayer owner,
                                         ItemStack egg) {
        BlockPos spawn = BlockPos.containing(helper.absoluteVec(HATCH));
        BlockPos floor = spawn.below();
        helper.assertTrue(egg.getItem().useOn(new UseOnContext(helper.getLevel(), owner, InteractionHand.MAIN_HAND,
                        egg, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false))).consumesAction(),
                "actual useOn must hatch the egg");
        List<BasicEntityShip> ships = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class, new AABB(spawn));
        ships.forEach(entities::add);
        helper.assertTrue(ships.size() == 1, "useOn must spawn exactly one ship");
        return ships.get(0);
    }
}
