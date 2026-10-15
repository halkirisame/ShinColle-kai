package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.ShipSupportAbilities;
import com.lulan.shincolle.entity.ShipSupportAbilities.HealingProfile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipSupportAbilitiesGameTests {
    private static final Vec3 ORIGIN = new Vec3(2, 3, 2);
    private static final AtomicInteger NEXT_OWNER = new AtomicInteger(6_000_000);

    private ShipSupportAbilitiesGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void selfHealingUsesEachProfilePeriodAndRequiresFuel(GameTestHelper helper) {
        ready(helper, () -> {
            for (HealingProfile profile : HealingProfile.values()) {
                try (Fixture fixture = new Fixture(helper)) {
                    BasicEntityShip source = fixture.healer(profile, 0);
                    source.setStateFlag(ID.F.IsMarried, false);
                    source.setHealth(50F);
                    source.tickCount = period(profile) - 1;
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, source, 50F, "Before profile period");
                    fixture.fuel(source, 0);
                    source.tickCount = period(profile);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, source, 50F, "Empty fuel must prevent self healing");
                    fixture.fuel(source, 1);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, source, profile == HealingProfile.NORTHERN ? 54F : 57F,
                            "Profile self healing formula");
                    helper.assertTrue(source.getGrudge() == 1, "Self healing must not charge extra grudge");
                    source.setHealth(100F);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, source, 100F, "Full health must stay full");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void healingRequiresMarriageRingAndStrictInitialFuelThreshold(GameTestHelper helper) {
        ready(helper, () -> {
            for (HealingProfile profile : HealingProfile.values()) {
                try (Fixture fixture = new Fixture(helper)) {
                    BasicEntityShip source = fixture.healer(profile, 10);
                    BasicEntityShip target = fixture.ship(fixture.owner, new Vec3(-1, 3, 2));
                    target.setHealth(50F);
                    source.setStateFlag(ID.F.IsMarried, false);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, target, 50F, "Unmarried source");
                    source.setStateFlag(ID.F.IsMarried, true);
                    source.setStateFlag(ID.F.UseRingEffect, false);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, target, 50F, "Disabled ring effect");
                    source.setStateFlag(ID.F.UseRingEffect, true);
                    fixture.fuel(source, cost(profile));
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, target, 50F, "Initial fuel equal to cost must not start");
                    fixture.fuel(source, cost(profile) + 1);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, target, 50F + shipHeal(profile, 10), "Immediate ship heal formula");
                    helper.assertTrue(source.getGrudge() == 1, "Exactly one base cost must be charged");
                    helper.assertTrue(!target.hasEffect(MobEffects.REGENERATION), "Support must heal immediately");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void healingHpEqualityIsExcludedAndEachTargetNeedsRemainingFuel(GameTestHelper helper) {
        ready(helper, () -> {
            for (HealingProfile profile : HealingProfile.values()) {
                try (Fixture fixture = new Fixture(helper)) {
                    BasicEntityShip source = fixture.healer(profile, 50);
                    BasicEntityShip boundary = fixture.ship(fixture.owner, new Vec3(3, 3, 2));
                    float threshold = profile == HealingProfile.AIRFIELD ? 96F
                            : profile == HealingProfile.HARBOUR ? 94F : 98F;
                    boundary.setHealth(threshold);
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, boundary, threshold, "HP threshold equality must be excluded");
                    health(helper, source, 100F, "Source must not count as a target");
                    List<BasicEntityShip> injured = fixture.injured(4);
                    fixture.fuel(source, cost(profile) * 2);
                    ShipSupportAbilities.tickHealing(source, profile);
                    helper.assertTrue(healed(injured) == 2, "Two base costs must permit exactly two targets");
                    helper.assertTrue(source.getGrudge() == 0, "Fuel must be exhausted after two targets");
                    health(helper, boundary, threshold, "Excluded target must not take a heal slot");
                    for (BasicEntityShip target : injured) {
                        health(helper, target, target.getHealth() > 50F ? 50F + shipHeal(profile, 50) : 50F,
                                "Only paid targets may change HP");
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void healingQuotasChangeAtShipLevelBoundaries(GameTestHelper helper) {
        ready(helper, () -> {
            for (HealingProfile profile : HealingProfile.values()) {
                int divisor = profile == HealingProfile.AIRFIELD ? 15
                        : profile == HealingProfile.HARBOUR ? 50 : 25;
                for (int shipLevel : new int[] {divisor - 1, divisor}) {
                    try (Fixture fixture = new Fixture(helper)) {
                        BasicEntityShip source = fixture.healer(profile, shipLevel);
                        List<BasicEntityShip> injured = fixture.injured(5);
                        int quota = shipLevel / divisor + (profile == HealingProfile.AIRFIELD ? 2 : 1);
                        ShipSupportAbilities.tickHealing(source, profile);
                        helper.assertTrue(healed(injured) == quota, "Heal quota at ship level " + shipLevel);
                        helper.assertTrue(source.getGrudge() == 100_000 - quota * cost(profile),
                                "Only successful quota slots must consume fuel");
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void healingIncludesOwnerPlayerAndAlliedShipsButExcludesOtherTargets(GameTestHelper helper) {
        ready(helper, () -> {
            for (HealingProfile profile : HealingProfile.values()) {
                try (Fixture fixture = new Fixture(helper)) {
                    BasicEntityShip source = fixture.healer(profile, 100);
                    fixture.alliance();
                    BasicEntityShip same = fixture.ship(fixture.owner, new Vec3(-1, 3, 2));
                    BasicEntityShip allied = fixture.ship(fixture.owner + 1, new Vec3(2, 3, -1));
                    BasicEntityShip foreign = fixture.ship(fixture.owner + 2, new Vec3(3, 3, 2));
                    BasicEntityShip outside = fixture.ship(fixture.owner, new Vec3(24, 3, 2));
                    for (BasicEntityShip target : List.of(same, allied, foreign, outside)) target.setHealth(50F);
                    FakePlayer player = fixture.player();
                    player.setHealth(10F);
                    Wolf wolf = fixture.wolf(player);
                    helper.assertTrue(TeamHelper.checkIsAlly(source, allied), "Allied ship fixture");
                    helper.assertTrue(TeamHelper.checkIsAlly(source, player), "Owner player fixture");
                    helper.assertTrue(TeamHelper.checkIsAlly(source, wolf), "Allied non-ship fixture");
                    helper.assertTrue(!TeamHelper.checkIsAlly(source, foreign), "Non-allied owner fixture");
                    ShipSupportAbilities.tickHealing(source, profile);
                    health(helper, same, 50F + shipHeal(profile, 100), "Same owner ship must heal");
                    health(helper, allied, 50F + shipHeal(profile, 100), "Allied owner ship must heal");
                    health(helper, player, Math.min(20F, 10F + playerHeal(profile, 100)), "Owner player formula");
                    health(helper, wolf, 5F, "Allied vanilla animal is not a supported target");
                    health(helper, foreign, 50F, "Non-allied ship must not heal");
                    health(helper, outside, 50F, "Outside ship must not heal");
                    helper.assertTrue(source.getGrudge() == 100_000 - cost(profile) * 3,
                            "Only three supported allies must be charged");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void healingUsesExistingHungerConsumptionAndInventoryRefuel(GameTestHelper helper) {
        ready(helper, () -> {
            boolean easyMode = ConfigHandler.easyMode;
            ConfigHandler.easyMode = false;
            try (Fixture fixture = new Fixture(helper)) {
                BasicEntityShip source = fixture.healer(HealingProfile.AIRFIELD, 0);
                List<BasicEntityShip> injured = fixture.injured(2);
                fixture.fuel(source, 51);
                source.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
                source.getCapaShipInventory().setStackInSlot(6, new ItemStack(ModItems.GRUDGE.get()));
                helper.assertTrue(source.hasEffect(MobEffects.HUNGER)
                        && source.getCapaShipInventory().getStackInSlot(6).getCount() == 1,
                        "Hunger and refuel item fixture");
                ShipSupportAbilities.tickHealing(source, HealingProfile.AIRFIELD);
                helper.assertTrue(healed(injured) == 2, "Refuel must allow the next paid heal");
                helper.assertTrue(source.getGrudge() == 150, "Each heal must use existing triple hunger cost");
                helper.assertTrue(source.getCapaShipInventory().getStackInSlot(6).isEmpty(),
                        "Existing consumption API must consume the refuel item");
                helper.assertTrue(!source.getStateFlag(ID.F.NoFuel), "Refuel must leave fuel state usable");
                helper.succeed();
            } finally {
                ConfigHandler.easyMode = easyMode;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void transportTransfersThreeResourcesWithOneQuotaAndRecipientMultipliers(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                BasicEntityShip source = fixture.transport(49);
                List<BasicEntityShip> targets = fixture.injured(2);
                for (BasicEntityShip target : targets) {
                    fixture.resources(target, 0, 0, 0);
                    fixture.multipliers(target, 2F, 3F);
                }
                fixture.resources(source, 5401, 540, 270);
                int beforeExp = source.getStateMinor(ID.M.ExpCurrent);
                ShipSupportAbilities.tickTransport(source);
                helper.assertTrue(targets.stream().filter(target -> target.getGrudge() > 0).count() == 1,
                        "Below level 50 only one target may be supplied");
                helper.assertTrue(targets.stream().mapToInt(BasicEntityShip::getGrudge).sum() == 10800,
                        "Recipient grudge modifier must apply");
                helper.assertTrue(targets.stream().mapToInt(target -> target.getStateMinor(ID.M.NumAmmoLight)).sum() == 1620,
                        "Recipient light ammo modifier must apply");
                helper.assertTrue(targets.stream().mapToInt(target -> target.getStateMinor(ID.M.NumAmmoHeavy)).sum() == 810,
                        "Recipient heavy ammo modifier must apply");
                fixture.assertResources(source, 1, 0, 0);
                helper.assertTrue(source.getStateMinor(ID.M.ExpCurrent) == beforeExp + ConfigHandler.expGain[6] * 20,
                        "Three resources on one target must award experience once");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void transportRespectsResourceThresholdsOwnersPeriodAndNoFuel(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                BasicEntityShip source = fixture.transport(50);
                BasicEntityShip target = fixture.ship(fixture.owner, new Vec3(-1, 3, 2));
                fixture.alliance();
                BasicEntityShip allied = fixture.ship(fixture.owner + 1, new Vec3(2, 3, -1));
                fixture.resources(allied, 0, 0, 0);
                helper.assertTrue(TeamHelper.checkIsAlly(source, allied)
                        && !TeamHelper.checkSameOwner(source, allied), "Allied but different owner fixture");
                fixture.resources(source, 5400, 539, 269);
                fixture.resources(target, 0, 0, 0);
                ShipSupportAbilities.tickTransport(source);
                fixture.assertResources(target, 0, 0, 0);
                fixture.resources(source, 5401, 540, 270);
                fixture.resources(target, 2700, 270, 135);
                ShipSupportAbilities.tickTransport(source);
                fixture.assertResources(target, 2700, 270, 135);
                fixture.assertResources(source, 5401, 540, 270);
                fixture.resources(target, 2699, 269, 134);
                source.tickCount = 255;
                ShipSupportAbilities.tickTransport(source);
                fixture.assertResources(target, 2699, 269, 134);
                source.tickCount = 256;
                source.setStateFlag(ID.F.NoFuel, true);
                ShipSupportAbilities.tickTransport(source);
                fixture.assertResources(target, 2699, 269, 134);
                source.setStateFlag(ID.F.NoFuel, false);
                ShipSupportAbilities.tickTransport(source);
                fixture.assertResources(target, 8099, 809, 404);
                fixture.assertResources(allied, 0, 0, 0);
                fixture.assertResources(source, 1, 0, 0);
                int[][] independent = {{5401, 0, 0, 5400, 0, 0},
                        {1, 540, 0, 0, 540, 0}, {1, 0, 270, 0, 0, 270}};
                for (int[] values : independent) {
                    fixture.resources(source, values[0], values[1], values[2]);
                    fixture.resources(target, 0, 0, 0);
                    ShipSupportAbilities.tickTransport(source);
                    fixture.assertResources(target, values[3], values[4], values[5]);
                    fixture.assertResources(allied, 0, 0, 0);
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void transportSkipsUnneededTargetsWithoutSpendingQuotaOrExperience(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                BasicEntityShip source = fixture.transport(50);
                fixture.injured(3);
                List<BasicEntityShip> ordered = fixture.level.getEntitiesOfClass(BasicEntityShip.class,
                                source.getBoundingBox().inflate(4D)).stream()
                        .filter(target -> target != source && target.getPlayerUID() == fixture.owner).toList();
                helper.assertTrue(ordered.size() == 3, "Three indexed targets in actual iteration order");
                fixture.resources(ordered.get(0), 0, 0, 0);
                fixture.resources(ordered.get(1), 2700, 270, 135);
                fixture.resources(ordered.get(2), 0, 0, 0);
                fixture.resources(source, 10801, 1080, 540);
                int beforeExp = source.getStateMinor(ID.M.ExpCurrent);
                ShipSupportAbilities.tickTransport(source);
                fixture.assertResources(ordered.get(0), 5400, 540, 270);
                fixture.assertResources(ordered.get(1), 2700, 270, 135);
                fixture.assertResources(ordered.get(2), 5400, 540, 270);
                helper.assertTrue(source.getStateMinor(ID.M.ExpCurrent) == beforeExp + ConfigHandler.expGain[6] * 40,
                        "Only two actual supply successes must award experience");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_support")
    public static void transportRestocksCargoBeforeSupplyingAndHonorsContainersAndEasyMode(GameTestHelper helper) {
        ready(helper, () -> {
            boolean easyMode = ConfigHandler.easyMode;
            try {
                ConfigHandler.easyMode = false;
                try (Fixture fixture = new Fixture(helper)) {
                    BasicEntityShip source = fixture.transport(0);
                    fixture.multipliers(source, 2F, 3F);
                    fixture.resources(source, 5400, 540, 270);
                    source.getCapaShipInventory().setStackInSlot(6, new ItemStack(ModItems.GRUDGE.get(), 2));
                    source.getCapaShipInventory().setStackInSlot(7, new ItemStack(ModItems.AMMO.get(), 2));
                    source.getCapaShipInventory().setStackInSlot(8, new ItemStack(ModItems.AMMO_2.get(), 2));
                    source.getCapaShipInventory().setStackInSlot(59, new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get()));
                    source.tickCount = 127;
                    ShipSupportAbilities.tickTransport(source);
                    fixture.assertResources(source, 5400, 540, 270);
                    source.tickCount = 128;
                    ShipSupportAbilities.tickTransport(source);
                    fixture.assertResources(source, 6000, 630, 315);
                    for (int slot : new int[] {6, 7, 8}) {
                        helper.assertTrue(source.getCapaShipInventory().getStackInSlot(slot).getCount() == 1,
                                "Restock must consume only one ordinary item per resource");
                    }
                    helper.assertTrue(source.getCapaShipInventory().getStackInSlot(59).getCount() == 1,
                            "Ordinary item must be preferred over block");
                    BasicEntityShip target = fixture.ship(fixture.owner, new Vec3(-1, 3, 2));
                    fixture.resources(target, 0, 0, 0);
                    fixture.resources(source, 5400, 540, 270);
                    source.tickCount = 256;
                    ShipSupportAbilities.tickTransport(source);
                    fixture.assertResources(source, 600, 90, 45);
                    fixture.assertResources(target, 5400, 540, 270);
                }
                for (boolean easy : new boolean[] {false, true}) {
                    ConfigHandler.easyMode = easy;
                    try (Fixture fixture = new Fixture(helper)) {
                        BasicEntityShip source = fixture.transport(0);
                        fixture.resources(source, 1, 0, 0);
                        source.getCapaShipInventory().setStackInSlot(57, new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get()));
                        source.getCapaShipInventory().setStackInSlot(58, new ItemStack(ModItems.AMMO_1.get()));
                        source.getCapaShipInventory().setStackInSlot(59, new ItemStack(ModItems.AMMO_3.get()));
                        source.tickCount = 128;
                        ShipSupportAbilities.tickTransport(source);
                        int multiplier = easy ? 10 : 1;
                        fixture.assertResources(source, 1 + 2700 * multiplier, 270 * multiplier, 135 * multiplier);
                        for (int slot : new int[] {57, 58, 59}) {
                            helper.assertTrue(source.getCapaShipInventory().getStackInSlot(slot).isEmpty(),
                                    "Restock must search all usable cargo pages");
                        }
                    }
                }
                helper.succeed();
            } finally {
                ConfigHandler.easyMode = easyMode;
            }
        });
    }

    private static void ready(GameTestHelper helper, Runnable verification) {
        GameTestEntities.whenPositionsTicking(helper, verification, ORIGIN, new Vec3(-2, 3, -2), new Vec3(24, 3, 2));
    }

    private static int period(HealingProfile profile) {
        return profile == HealingProfile.NORTHERN ? 64 : 128;
    }

    private static int cost(HealingProfile profile) {
        return profile == HealingProfile.NORTHERN ? 25 : 50;
    }

    private static float shipHeal(HealingProfile profile, int shipLevel) {
        return switch (profile) {
            case AIRFIELD -> 5F + shipLevel * 0.1F;
            case HARBOUR -> 10F + shipLevel * 0.2F;
            case NORTHERN -> 3F + shipLevel * 0.1F;
        };
    }

    private static float playerHeal(HealingProfile profile, int shipLevel) {
        return switch (profile) {
            case AIRFIELD -> 1.8F + shipLevel * 0.04F;
            case HARBOUR -> 5.2F + shipLevel * 0.06F;
            case NORTHERN -> 1.4F + shipLevel * 0.02F;
        };
    }

    private static long healed(List<BasicEntityShip> ships) {
        return ships.stream().filter(ship -> ship.getHealth() > 50F).count();
    }

    private static void health(GameTestHelper helper, LivingEntity entity, float expected, String message) {
        helper.assertTrue(Math.abs(entity.getHealth() - expected) < 0.001F,
                message + ": expected " + expected + ", actual " + entity.getHealth());
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestEntities entities;
        private final int owner = NEXT_OWNER.getAndAdd(3);
        private final List<Integer> teams = new ArrayList<>();
        private FakePlayer player;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.entities = GameTestEntities.open(helper);
        }

        private BasicEntityShip healer(HealingProfile profile, int shipLevel) {
            BasicEntityShip source = switch (profile) {
                case AIRFIELD -> ModEntities.AIRFIELD_HIME.get().create(level);
                case HARBOUR -> ModEntities.HARBOUR_HIME.get().create(level);
                case NORTHERN -> ModEntities.NORTHERN_HIME.get().create(level);
            };
            prepare(source, owner, ORIGIN);
            configureSource(source, shipLevel, period(profile));
            return source;
        }

        private BasicEntityShip transport(int shipLevel) {
            BasicEntityShip source = ModEntities.AP_WA.get().create(level);
            prepare(source, owner, ORIGIN);
            configureSource(source, shipLevel, 256);
            source.calcShipAttributesAddEquip();
            helper.assertTrue(source.getInventoryPageSize() == 2, "Transport must provide three cargo pages");
            return source;
        }

        private void configureSource(BasicEntityShip source, int shipLevel, int tick) {
            source.setStateFlag(ID.F.IsMarried, true);
            source.setStateFlag(ID.F.UseRingEffect, true);
            source.setShipLevel(shipLevel, false);
            source.setStateMinor(ID.M.ExpCurrent, 0);
            source.setStateMinor(ID.M.ExpNext, 1_000_000);
            source.getAttrs().setAttrsBuffed(ID.Attrs.XP, 1F);
            source.tickCount = tick;
            helper.assertTrue(source.getLevel() == shipLevel && source.getStateFlag(ID.F.IsMarried)
                    && source.getStateFlag(ID.F.UseRingEffect) && source.tickCount == tick
                    && source.getAttrs().getAttrsBuffed(ID.Attrs.XP) == 1F,
                    "Support level, marriage, ring and period fixture");
        }

        private BasicEntityShip ship(int ownerUid, Vec3 position) {
            return prepare(ModEntities.BB_KONGOU.get().create(level), ownerUid, position);
        }

        private BasicEntityShip prepare(BasicEntityShip ship, int ownerUid, Vec3 position) {
            helper.assertTrue(ship != null, "Ship entity fixture must be constructible");
            entities.add(ship);
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(true);
            ship.setPlayerUID(ownerUid);
            ship.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            ship.setHealth(100F);
            resources(ship, 100_000, 1000, 1000);
            multipliers(ship, 1F, 1F);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 4F);
            ship.moveTo(helper.absoluteVec(position));
            helper.assertTrue(level.addFreshEntity(ship), "Ship must enter level");
            GameTestEntities.assertRegistered(helper, ship);
            helper.assertTrue(ship.getPlayerUID() == ownerUid && ship.getMaxHealth() == 100F
                    && ship.getHealth() == 100F && ship.getAttrs().getAttackRange() == 4F,
                    "Owner, HP and range fixture");
            return ship;
        }

        private List<BasicEntityShip> injured(int count) {
            List<BasicEntityShip> result = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                BasicEntityShip target = ship(owner, new Vec3(1 + index * 0.5D, 3, 3));
                target.setHealth(50F);
                health(helper, target, 50F, "Injured target fixture");
                result.add(target);
            }
            return result;
        }

        private void fuel(BasicEntityShip ship, int grudge) {
            ship.setStateMinor(ID.M.NumGrudge, grudge);
            ship.setStateFlag(ID.F.NoFuel, grudge <= 0);
            helper.assertTrue(ship.getGrudge() == grudge && ship.getStateFlag(ID.F.NoFuel) == (grudge <= 0),
                    "Fuel fixture");
        }

        private void resources(BasicEntityShip ship, int grudge, int light, int heavy) {
            fuel(ship, grudge);
            ship.setStateMinor(ID.M.NumAmmoLight, light);
            ship.setStateMinor(ID.M.NumAmmoHeavy, heavy);
            assertResources(ship, grudge, light, heavy);
        }

        private void assertResources(BasicEntityShip ship, int grudge, int light, int heavy) {
            helper.assertTrue(ship.getGrudge() == grudge
                    && ship.getStateMinor(ID.M.NumAmmoLight) == light
                    && ship.getStateMinor(ID.M.NumAmmoHeavy) == heavy,
                    "Expected resources " + grudge + "/" + light + "/" + heavy + ", actual "
                            + ship.getGrudge() + "/" + ship.getStateMinor(ID.M.NumAmmoLight) + "/"
                            + ship.getStateMinor(ID.M.NumAmmoHeavy));
        }

        private void multipliers(BasicEntityShip ship, float grudge, float ammo) {
            ship.getAttrs().setAttrsBuffed(ID.Attrs.GRUDGE, grudge);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.AMMO, ammo);
            helper.assertTrue(ship.getAttrs().getAttrsBuffed(ID.Attrs.GRUDGE) == grudge
                    && ship.getAttrs().getAttrsBuffed(ID.Attrs.AMMO) == ammo, "Resource multiplier fixture");
        }

        private void alliance() {
            helper.assertTrue(ServerDataManager.getTeamData(owner) == null
                    && ServerDataManager.getTeamData(owner + 1) == null, "Fresh team identifiers fixture");
            TeamData sourceTeam = new TeamData(owner, "support_source", "support_source");
            TeamData alliedTeam = new TeamData(owner + 1, "support_ally", "support_ally");
            sourceTeam.addTeamAlly(owner + 1);
            ServerDataManager.setTeamData(sourceTeam);
            ServerDataManager.setTeamData(alliedTeam);
            teams.add(owner);
            teams.add(owner + 1);
            helper.assertTrue(ServerDataManager.getTeamData(owner) == sourceTeam
                    && ServerDataManager.getTeamData(owner + 1) == alliedTeam, "Registered teams fixture");
        }

        private FakePlayer player() {
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "support_owner"));
            player.moveTo(helper.absoluteVec(new Vec3(2, 3, 4)));
            player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                    () -> new IllegalStateException("Player capability fixture")).setPlayerUID(owner);
            level.addNewPlayer(player);
            GameTestEntities.assertRegistered(helper, player);
            helper.assertTrue(TeamHelper.getPlayerUID(player) == owner, "Player owner UID fixture");
            return player;
        }

        private Wolf wolf(FakePlayer ownerPlayer) {
            Wolf wolf = entities.add(EntityType.WOLF.create(level));
            helper.assertTrue(wolf != null, "Wolf fixture");
            wolf.setTame(true);
            wolf.setOwnerUUID(ownerPlayer.getUUID());
            wolf.setNoAi(true);
            wolf.setNoGravity(true);
            wolf.setInvulnerable(true);
            wolf.setHealth(5F);
            wolf.moveTo(helper.absoluteVec(new Vec3(4, 3, 2)));
            helper.assertTrue(level.addFreshEntity(wolf), "Wolf must enter level");
            GameTestEntities.assertRegistered(helper, wolf);
            health(helper, wolf, 5F, "Injured animal fixture");
            return wolf;
        }

        @Override
        public void close() {
            entities.close();
            if (player != null) {
                level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                player = null;
            }
            for (int team : teams) ServerDataManager.removeTeamData(team);
        }
    }
}
