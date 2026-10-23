package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.advancements.critereon.DistancePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Uses a non-FakePlayer advancement manager; it is not a networked-client acceptance test. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipKillAdvancementGameTests {
    private ShipKillAdvancementGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_ship_kill_advancement")
    public static void nativeShipKillAwardsActualAdvancement(GameTestHelper helper) {
        verify(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_summon_kill_advancement")
    public static void summonKillAwardsAdvancementWithoutForgingSourceOrDistance(GameTestHelper helper) {
        verify(helper, true);
    }

    private static void verify(GameTestHelper helper, boolean summonAttack) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            ServerPlayer owner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "advancement_owner"));
            owner.connection = new SilentPackets(owner);
            owner.moveTo(helper.absoluteVec(new Vec3(8D, 2D, 1D)));
            helper.getLevel().addNewPlayer(owner);
            GameTestEntities.onCompletion(helper, () -> helper.getLevel().removePlayerImmediately(
                    owner, Entity.RemovalReason.DISCARDED), "advancement player cleanup");
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Fixture: ship creation");
            ship.tame(owner);
            ship.setNoAi(true);
            ship.setStateMinor(ID.M.NumGrudge, 1000);
            ship.moveTo(helper.absoluteVec(new Vec3(2D, 2D, 1D)));
            helper.getLevel().addFreshEntity(ship);
            helper.assertTrue(ship.isTame() && ship.getOwner() == owner, "Fixture: normal tame owner resolution");
            LivingEntity attacker = ship;
            if (summonAttack) {
                EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
                helper.assertTrue(summon != null, "Fixture: summon creation");
                summon.setHost(ship);
                summon.setNoAi(true);
                summon.moveTo(ship.position());
                helper.getLevel().addFreshEntity(summon);
                attacker = summon;
            }
            Zombie victim = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            helper.assertTrue(victim != null, "Fixture: zombie creation");
            victim.setNoAi(true);
            victim.moveTo(helper.absoluteVec(new Vec3(3D, 2D, 1D)));
            helper.getLevel().addFreshEntity(victim);

            Advancement normal = listen(helper, owner,
                    KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity().of(EntityType.ZOMBIE)));
            Advancement playerSource = listen(helper, owner, KilledTrigger.TriggerInstance.playerKilledEntity(
                    EntityPredicate.Builder.entity().of(EntityType.ZOMBIE),
                    DamageSourcePredicate.Builder.damageType().source(EntityPredicate.Builder.entity().of(EntityType.PLAYER))));
            Advancement tooNear = listen(helper, owner, KilledTrigger.TriggerInstance.playerKilledEntity(
                    EntityPredicate.Builder.entity().of(EntityType.ZOMBIE)
                            .distance(DistancePredicate.absolute(MinMaxBounds.Doubles.atMost(1D)))));
            helper.assertTrue(!done(owner, normal) && !done(owner, playerSource) && !done(owner, tooNear),
                    "Fixture: all three advancements must start incomplete");
            victim.hurt(attacker.damageSources().mobAttack(attacker), 80F);
            helper.startSequence().thenIdle(2).thenExecute(() -> {
                helper.assertTrue(done(owner, normal), "The owner must actually earn the zombie advancement");
                helper.assertTrue(!done(owner, playerSource), "A ship/summon source must not satisfy a Player-source criterion");
                helper.assertTrue(!done(owner, tooNear), "A distant owner must not satisfy a near-distance criterion");
                helper.succeed();
            });
        }, new Vec3(2D, 2D, 1D), new Vec3(8D, 2D, 1D));
    }

    private static Advancement listen(GameTestHelper helper, ServerPlayer owner, KilledTrigger.TriggerInstance condition) {
        Advancement advancement = Advancement.Builder.advancement().addCriterion("kill", condition)
                .build(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "kill_credit_test/" + UUID.randomUUID()));
        var listener = new CriterionTrigger.Listener<>(condition, advancement, "kill");
        owner.getAdvancements().getOrStartProgress(advancement);
        CriteriaTriggers.PLAYER_KILLED_ENTITY.addPlayerListener(owner.getAdvancements(), listener);
        GameTestEntities.onCompletion(helper,
                () -> CriteriaTriggers.PLAYER_KILLED_ENTITY.removePlayerListener(owner.getAdvancements(), listener),
                "advancement listener cleanup");
        return advancement;
    }

    private static boolean done(ServerPlayer owner, Advancement advancement) {
        return owner.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static final class SilentPackets extends ServerGamePacketListenerImpl {
        private SilentPackets(ServerPlayer player) {
            super(player.getServer(), new Connection(PacketFlow.CLIENTBOUND), player);
        }

        @Override
        public void send(Packet<?> packet) { }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) { }
    }
}
