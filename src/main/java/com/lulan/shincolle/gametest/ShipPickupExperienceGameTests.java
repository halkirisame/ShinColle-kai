package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPickupExperienceGameTests {
    private static final Vec3 AT = new Vec3(8.5D, 4D, 8.5D);
    private static final int REWARD = 7;

    private ShipPickupExperienceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_transport")
    public static void transportReceivesOneRewardPerWholePickup(GameTestHelper helper) {
        wholePickups(helper, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_nagato")
    public static void nonTransportReceivesOneRewardPerWholePickup(GameTestHelper helper) {
        wholePickups(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_full")
    public static void stackableCargoStillNeedsAnEmptySlotToStart(GameTestHelper helper) {
        fixture(helper, true, REWARD, f -> {
            var inventory = f.ship.getCapaShipInventory();
            f.fillCargo();
            inventory.setStackInSlot(6, f.stack(Items.DIAMOND, 32));
            ItemEntity item = f.drop(1, 0);
            helper.assertTrue(inventory.getFirstSlotForItem() == -1
                            && inventory.canAddItemStackToInventory(item.getItem())
                            && !new ShipPickItemGoal(f.ship, 4F).canUse(),
                    "Fixture: stackable capacity alone must not satisfy the existing empty-slot start condition");
            helper.startSequence().thenIdle(40).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(item.isAlive() && item.getItem().getCount() == 1 && f.cargo() == 32,
                        "No pickup without an empty cargo slot");
                inventory.setStackInSlot(7, ItemStack.EMPTY);
                helper.assertTrue(inventory.getFirstSlotForItem() == 7, "Fixture: empty slot released");
            }).thenWaitUntil(() -> f.stored(item, 33)).thenExecute(() -> {
                f.experience(f.reward());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_capacity")
    public static void thirtyTwoUnitsOfCapacityCannotPartiallyStoreSixtyFour(GameTestHelper helper) {
        fixture(helper, true, REWARD, f -> {
            var inventory = f.ship.getCapaShipInventory();
            f.fillCargo();
            inventory.setStackInSlot(6, ItemStack.EMPTY);
            inventory.setStackInSlot(7, ItemStack.EMPTY);
            // An oversized dropped stack exercises whole-stack capacity while retaining the empty-slot start condition.
            ItemEntity item = f.drop(f.stack(Items.SNOWBALL, 64), 0);
            helper.assertTrue(item.getItem().getMaxStackSize() == 16
                            && !inventory.canAddItemStackToInventory(item.getItem())
                            && new ShipPickItemGoal(f.ship, 4F).canUse(),
                    "Fixture: two empty slots provide 32 units, but the goal can start");
            helper.startSequence().thenIdle(40).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(item.isAlive() && item.getItem().getCount() == 64 && f.cargo(Items.SNOWBALL) == 0
                                && inventory.getStackInSlot(6).isEmpty() && inventory.getStackInSlot(7).isEmpty(),
                        "Failed whole-stack insertion must not partially mutate either side");
                inventory.setStackInSlot(8, ItemStack.EMPTY);
                inventory.setStackInSlot(9, ItemStack.EMPTY);
                helper.assertTrue(inventory.canAddItemStackToInventory(item.getItem()), "Fixture: whole capacity now fits");
            }).thenWaitUntil(() -> {
                helper.assertTrue(!item.isAlive() && item.getItem().isEmpty() && f.cargo(Items.SNOWBALL) == 64,
                        "All 64 snowballs must be stored after capacity is released");
            }).thenExecute(() -> {
                f.experience(f.reward());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_delay")
    public static void delayedItemAwardsNothingUntilPickupIsAllowed(GameTestHelper helper) {
        fixture(helper, true, REWARD, f -> {
            ItemEntity item = f.drop(1, 32767);
            helper.startSequence().thenIdle(40).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(item.isAlive() && item.hasPickUpDelay() && f.cargo() == 0,
                        "Delayed item remains outside cargo without a reward");
                item.setPickUpDelay(0);
            }).thenWaitUntil(() -> f.stored(item, 1)).thenExecute(() -> {
                f.experience(f.reward());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_distance")
    public static void exactlyThreeBlocksIsOutsideThePickupDistance(GameTestHelper helper) {
        fixture(helper, true, REWARD, f -> {
            Vec3 origin = f.ship.position();
            ItemEntity item = f.drop(1, 0);
            item.setPos(origin.x + 3D, origin.y, origin.z);
            Consumer<LivingEvent.LivingTickEvent> pin = event -> {
                if (event.getEntity() == f.ship) {
                    f.ship.moveTo(origin);
                    f.ship.setDeltaMovement(Vec3.ZERO);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(pin);
            GameTestEntities.onCompletion(helper, () -> MinecraftForge.EVENT_BUS.unregister(pin), "pickup distance pin cleanup");
            helper.assertTrue(f.ship.distanceToSqr(item) == 9D, "Fixture: exact squared distance nine");
            helper.startSequence().thenIdle(40).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(item.isAlive() && f.cargo() == 0, "Distance nine must not be picked up");
                f.ship.moveTo(origin);
                item.setPos(origin.x + 2.5D, origin.y, origin.z);
                helper.assertTrue(f.ship.distanceToSqr(item) < 9D, "Fixture: moved inside pickup reach");
            }).thenWaitUntil(() -> f.stored(item, 1)).thenExecute(() -> {
                f.experience(f.reward());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_removed")
    public static void removedCachedItemCannotBeStoredOrRewarded(GameTestHelper helper) {
        fixture(helper, true, REWARD, f -> {
            f.ship.setShipLevel(150, true);
            f.ship.setMorale(3000);
            f.ship.getAttrs().setAttrsBonus(ID.AttrsBase.SPD, 3);
            f.ship.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 4));
            f.ship.calcShipAttributes(31, false);
            helper.assertTrue(f.ship.getLevel() == 150 && f.ship.getAttrs().getAttackSpeed() > 10F / 3F,
                    "Fixture: natural attack speed retries before the next 16-tick scan: level=" + f.ship.getLevel()
                            + " speed=" + f.ship.getAttrs().getAttackSpeed() + " morale=" + f.ship.getMorale());
            ItemEntity old = f.drop(1, 32767);
            ItemEntity[] next = new ItemEntity[1];
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(f.targets(old), "Waiting for the registered goal to cache the delayed item");
            }).thenExecute(() -> {
                old.discard();
                old.setPickUpDelay(0);
                next[0] = f.drop(1, 32767);
                helper.assertTrue(!old.isAlive() && !old.getItem().isEmpty(), "Fixture: removed item retains its old stack");
            }).thenIdle(12).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(f.cargo() == 0, "A stale removed target cannot insert its still-nonempty stack");
                next[0].setItem(ItemStack.EMPTY);
            }).thenIdle(20).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(f.cargo() == 0, "Empty/removed targets cannot award a pickup");
                f.ship.setShipLevel(20, true);
                f.ship.setExpNext();
                next[0] = f.drop(1, 0);
            }).thenWaitUntil(() -> f.stored(next[0], 1)).thenExecute(() -> {
                f.experience(f.reward());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_zero")
    public static void zeroConfiguredRewardDoesNotBlockStorage(GameTestHelper helper) {
        fixture(helper, false, 0, f -> {
            ItemEntity item = f.drop(64, 0);
            helper.startSequence().thenWaitUntil(() -> f.stored(item, 64)).thenIdle(20).thenExecute(() -> {
                f.experience(0);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_cap")
    public static void levelCapStillAllowsStorageWithoutExperience(GameTestHelper helper) {
        fixture(helper, false, REWARD, f -> {
            int cap = Math.min(ConfigHandler.maxLevelUnmarried, ConfigHandler.maxLevel);
            f.ship.setShipLevel(cap, true);
            f.ship.setExpNext();
            helper.assertTrue(f.ship.getLevel() == cap && !f.ship.getStateFlag(ID.F.IsMarried), "Fixture: unmarried cap retained");
            ItemEntity item = f.drop(1, 0);
            helper.startSequence().thenWaitUntil(() -> f.stored(item, 1)).thenIdle(20).thenExecute(() -> {
                f.experience(0);
                helper.assertTrue(f.ship.getLevel() == cap, "Pickup does not bypass the existing level cap");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pickup_exp_inventory")
    public static void genericInventoryInsertionDoesNotGrantPickupExperience(GameTestHelper helper) {
        fixture(helper, true, REWARD, f -> {
            ItemStack inserted = f.stack(Items.DIAMOND, 64);
            helper.assertTrue(f.ship.getCapaShipInventory().addItemStackToInventory(inserted)
                            && inserted.isEmpty() && f.cargo() == 64, "Fixture: generic whole-stack storage succeeds");
            f.experience(0);
            ItemEntity item = f.drop(1, 0);
            helper.startSequence().thenWaitUntil(() -> f.stored(item, 65)).thenExecute(() -> {
                f.experience(f.reward());
                helper.succeed();
            });
        });
    }

    private static void wholePickups(GameTestHelper helper, boolean transport) {
        fixture(helper, transport, REWARD, f -> {
            if (!transport) {
                var compatibility = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                GameTestEntities.onCompletion(helper, compatibility::close, "pickup compatibility input restoration");
            }
            helper.assertTrue(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW,
                    "The compatibility input still executes NEW, not a revived legacy goal");
            ItemEntity[] item = {f.drop(1, 0)};
            int reward = f.reward();
            helper.startSequence().thenWaitUntil(() -> f.stored(item[0], 1)).thenExecute(() -> f.experience(reward))
                    .thenIdle(1).thenExecute(() -> f.experience(reward))
                    .thenIdle(20).thenExecute(() -> {
                        f.experience(reward);
                        item[0] = f.drop(64, 0);
                    }).thenWaitUntil(() -> f.stored(item[0], 65)).thenExecute(() -> f.experience(reward * 2))
                    .thenIdle(1).thenExecute(() -> {
                        f.experience(reward * 2);
                        item[0] = f.drop(1, 0);
                    }).thenWaitUntil(() -> f.stored(item[0], 66)).thenExecute(() -> f.experience(reward * 3))
                    .thenIdle(20).thenExecute(() -> {
                        f.experience(reward * 3);
                        helper.succeed();
                    });
        });
    }

    private static void fixture(GameTestHelper helper, boolean transport, int configuredReward, Consumer<Fixture> scenario) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip ship = entities.add(transport ? ModEntities.AP_WA.get().create(helper.getLevel())
                    : ModEntities.BB_NAGATO.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Fixture: ship created");
            ship.setInvulnerable(true);
            ship.setNoGravity(true);
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PickItem, true);
            ship.setStateFlag(ID.F.AutoPump, false);
            ship.setStateFlag(ID.F.IsMarried, false);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setStateFlag(ID.F.PassiveAI, false);
            ship.setStateMinor(ID.M.FleeHP, 0);
            ship.setStateMinor(ID.M.CraneState, 0);
            ship.setShipLevel(20, true);
            ship.setExpNext();
            ship.setStateMinor(ID.M.ExpCurrent, 0);
            ship.moveTo(helper.absoluteVec(AT));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture: ship added");
            // Keep the registered pickup goal; remove wandering and combat, including after the initial rebuild.
            GameTestEntities.removeGoalsEachTick(helper, ship, goal -> !(goal instanceof ShipPickItemGoal));
            helper.startSequence().thenIdle(24).thenExecute(() -> {
                helper.assertTrue(ship.tickCount >= 24, "Fixture: natural ticks have passed the initial goal rebuild");
                helper.assertTrue(ship.getShipType() == (transport ? ID.ShipType.TRANSPORT : ID.ShipType.BATTLESHIP),
                        "Fixture: actual transport or non-transport type");
                helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                                && ship.getHealth() == ship.getMaxHealth() && ship.getStateFlag(ID.F.PickItem)
                                && !ship.isOrderedToSit() && !ship.getStateFlag(ID.F.AutoPump),
                        "Fixture: fuel, health and pickup settings retained");
                int oldReward = ConfigHandler.expGain[6];
                int oldOtherReward = ConfigHandler.expGain[5];
                ConfigHandler.expGain[6] = configuredReward;
                ConfigHandler.expGain[5] = 31;
                GameTestEntities.onCompletion(helper, () -> {
                    ConfigHandler.expGain[6] = oldReward;
                    ConfigHandler.expGain[5] = oldOtherReward;
                }, "pickup experience config restoration");
                Fixture f = new Fixture(helper, entities, ship, configuredReward);
                f.experience(0);
                scenario.accept(f);
            });
        }, AT, AT.add(4D, 0D, 0D));
    }

    private record Fixture(GameTestHelper helper, GameTestEntities entities, BasicEntityShip ship, int configuredReward) {
        ItemEntity drop(int count, int delay) {
            return drop(stack(Items.DIAMOND, count), delay);
        }

        ItemStack stack(Item item, int count) {
            ItemStack stack = new ItemStack(item, count);
            stack.getOrCreateTag().putBoolean("PickupFixture", true);
            return stack;
        }

        ItemEntity drop(ItemStack stack, int delay) {
            ItemEntity item = entities.add(new ItemEntity(helper.getLevel(), ship.getX() + 1D,
                    ship.getY(), ship.getZ(), stack));
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            item.setPickUpDelay(delay);
            helper.assertTrue(helper.getLevel().addFreshEntity(item), "Fixture: item added");
            GameTestEntities.assertRegistered(helper, item);
            return item;
        }

        int reward() {
            int value = (int) (configuredReward * ship.getAttrs().getAttrsBuffed(ID.Attrs.XP));
            helper.assertTrue(configuredReward == 0 || value > 0, "Fixture: nonzero scaled pickup reward");
            return value;
        }

        int cargo() {
            return cargo(Items.DIAMOND);
        }

        int cargo(Item item) {
            int count = 0;
            var inventory = ship.getCapaShipInventory();
            for (int slot = 6; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.is(item) && stack.hasTag() && stack.getTag().getBoolean("PickupFixture")) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        void fillCargo() {
            var inventory = ship.getCapaShipInventory();
            for (int slot = 6; slot < inventory.getSlots() && inventory.isSlotAvailable(slot); slot++) {
                inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            }
        }

        boolean targets(ItemEntity item) {
            try {
                Field selector = Mob.class.getDeclaredField("goalSelector");
                selector.setAccessible(true);
                Field target = ShipPickItemGoal.class.getDeclaredField("entItem");
                target.setAccessible(true);
                for (var wrapped : ((GoalSelector) selector.get(ship)).getAvailableGoals()) {
                    if (wrapped.isRunning() && wrapped.getGoal() instanceof ShipPickItemGoal goal && target.get(goal) == item) {
                        return true;
                    }
                }
                return false;
            } catch (ReflectiveOperationException failure) {
                throw new GameTestAssertException("Cannot observe registered pickup goal: " + failure);
            }
        }

        void stored(ItemEntity item, int count) {
            helper.assertTrue(!item.isAlive() && item.getItem().isEmpty() && cargo() == count,
                    "Whole pickup must consume the item and store cargo: alive=" + item.isAlive() + " cargo=" + cargo());
        }

        void experience(int expected) {
            helper.assertTrue(ConfigHandler.expGain[6] == configuredReward && ConfigHandler.expGain[5] == 31,
                    "Fixture: isolated reward configuration retained");
            helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == expected,
                    "Pickup experience: expected=" + expected + " actual=" + ship.getStateMinor(ID.M.ExpCurrent)
                            + " cargo=" + cargo());
        }
    }
}
