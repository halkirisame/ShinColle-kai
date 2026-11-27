package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerAccidentalDamageGameTests {
    private static final Vec3 SHIP_AT = new Vec3(2D, 4D, 2D);
    private static final Vec3 OWNER_AT = new Vec3(4D, 4D, 2D);

    private OwnerAccidentalDamageGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerMeleeDamageIsCappedAtOneHealth(GameTestHelper helper) {
        run(helper, scene -> scene.checkDamage(scene.level.damageSources().playerAttack(scene.owner), 10F, 1F));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerArrowDamageIsCappedEvenWhileHoldingAHammer(GameTestHelper helper) {
        run(helper, scene -> {
            scene.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KAITAI_HAMMER.get()));
            Arrow arrow = scene.entities.add(new Arrow(scene.level, scene.owner));
            DamageSource source = scene.level.damageSources().arrow(arrow, scene.owner);
            helper.assertTrue(source.getEntity() == scene.owner && source.getDirectEntity() == arrow,
                    "The arrow source must identify the owner as its cause");
            scene.checkDamage(source, 10F, 1F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerExplosionDamageIsCappedAtOneHealth(GameTestHelper helper) {
        run(helper, scene -> {
            PrimedTnt tnt = scene.entities.add(new PrimedTnt(scene.level,
                    scene.owner.getX(), scene.owner.getY(), scene.owner.getZ(), scene.owner));
            DamageSource source = scene.level.damageSources().explosion(tnt, scene.owner);
            helper.assertTrue(source.getEntity() == scene.owner && source.getDirectEntity() == tnt,
                    "The explosion source must identify the owner as its cause");
            scene.checkDamage(source, 10F, 1F);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerDamageBelowTheCapIsUnchanged(GameTestHelper helper) {
        run(helper, scene -> scene.checkDamage(scene.level.damageSources().playerAttack(scene.owner), 0.5F, 0.5F));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hammerStillKillsFullHealthShipsThroughTheirDamageOverrides(GameTestHelper helper) {
        run(helper, scene -> {
            scene.dismantle(scene.ship);
            BasicEntityShip atago = scene.ship(ModEntities.CA_ATAGO.get());
            scene.owner.removeAllEffects();
            scene.dismantle(atago);
            helper.assertTrue(scene.owner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "Dismantling must retain the ship-specific hurt override");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void otherPlayersDamageDoesNotReceiveTheOwnerCap(GameTestHelper helper) {
        run(helper, scene -> {
            helper.assertTrue(ConfigHandler.friendlyFire(), "The fixture requires default friendly fire");
            helper.assertTrue(!TeamHelper.checkSameOwner(scene.other, scene.ship),
                    "The control player must not own the ship");
            scene.ship.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
            scene.ship.getAttrs().setAttrsBuffed(ID.Attrs.DEF, 0F);
            helper.assertTrue(scene.ship.getAttrs().getDefense() == 0F
                            && scene.ship.getAttrs().getAttrsBuffed(ID.Attrs.DODGE) == 0F
                            && scene.ship.distanceToSqr(scene.other) < 36D,
                    "The control cannot dodge and must take at least 15 damage before vanilla armor");
            float before = scene.ship.getHealth();
            helper.assertTrue(scene.ship.hurt(scene.level.damageSources().playerAttack(scene.other), 20F),
                    "The control attack must be accepted");
            helper.assertTrue(before - scene.ship.getHealth() > 1F, "A non-owner received the accidental damage cap");
        });
    }

    private static void run(GameTestHelper helper, Consumer<Scene> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Scene scene = new Scene(helper)) {
                verification.accept(scene);
                helper.succeed();
            }
        }, SHIP_AT, OWNER_AT);
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final GameTestEntities entities;
        final FakePlayer owner;
        final FakePlayer other;
        final BasicEntityShip ship;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.entities = GameTestEntities.open(helper);
            this.owner = player("accidental_owner", 98101);
            this.other = player("accidental_other", 98102);
            GameTestEntities.onCompletion(helper, this::close, "accidental damage fixture cleanup");
            this.ship = ship(ModEntities.BB_KONGOU.get());
        }

        private FakePlayer player(String name, int uid) {
            FakePlayer player = FakePlayerFactory.get(this.level, new GameProfile(UUID.randomUUID(), name));
            player.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing admiral capability")).setPlayerUID(uid);
            player.moveTo(this.helper.absoluteVec(OWNER_AT));
            this.level.addNewPlayer(player);
            return player;
        }

        BasicEntityShip ship(EntityType<? extends BasicEntityShip> type) {
            BasicEntityShip result = this.entities.allowDamage(type.create(this.level));
            this.helper.assertTrue(result != null, "Could not create the damage fixture");
            result.moveTo(this.helper.absoluteVec(SHIP_AT));
            result.setNoAi(true);
            result.setNoGravity(true);
            result.setPlayerUID(98101);
            result.setOwnerUUID(this.owner.getUUID());
            result.setStateMinor(ID.M.ShipLevel, 1);
            result.setStateMinor(ID.M.NumGrudge, 100_000);
            result.setStateFlag(ID.F.NoFuel, false);
            result.setMorale(3000);
            result.removeAllEffects();
            result.calcShipAttributes(31, false);
            result.setHealth(result.getMaxHealth());
            result.setInvulnerable(false);
            this.helper.assertTrue(this.level.addFreshEntity(result), "Could not add the damage fixture");
            GameTestEntities.assertRegistered(this.helper, result);
            this.helper.assertTrue(result.getOwner() == this.owner && TeamHelper.checkSameOwner(this.owner, result)
                            && result.getHealth() == result.getMaxHealth() && result.getHealth() > 10F
                            && !result.isInvulnerable() && result.getStateMinor(ID.M.NumGrudge) > 0
                            && !result.getStateFlag(ID.F.NoFuel) && result.getActiveEffects().isEmpty(),
                    "The fixture must resolve its owner and have full, vulnerable health and fuel");
            return result;
        }

        void checkDamage(DamageSource source, float amount, float expected) {
            this.ship.setEntitySit(true);
            this.helper.assertTrue(this.ship.isOrderedToSit(), "The ship must start sitting");
            float before = this.ship.getHealth();
            this.helper.assertTrue(this.ship.hurt(source, amount), "The owner attack must be accepted");
            this.helper.assertTrue(Math.abs(before - this.ship.getHealth() - expected) < 1e-5F,
                    "Owner damage: " + (before - this.ship.getHealth()) + " expected " + expected);
            this.helper.assertTrue(!this.ship.isOrderedToSit()
                            && this.ship.getStateEmotion(ID.S.Emotion) == ID.Emotion.O_O,
                    "Owner damage must retain the stand-up and hurt expression");
        }

        void dismantle(BasicEntityShip target) {
            ItemStack hammer = new ItemStack(ModItems.KAITAI_HAMMER.get());
            this.owner.setItemInHand(InteractionHand.MAIN_HAND, hammer);
            this.helper.assertTrue(target.getHealth() == target.getMaxHealth(), "Hammer target must be full health");
            this.helper.assertTrue(hammer.getItem().onLeftClickEntity(hammer, this.owner, target),
                    "The hammer must consume its left click");
            this.helper.assertTrue(target.isDeadOrDying(), "The hammer must still kill the ship");
        }

        @Override
        public void close() {
            this.entities.close();
            this.level.removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
            this.level.removePlayerImmediately(this.other, Entity.RemovalReason.DISCARDED);
        }
    }
}
