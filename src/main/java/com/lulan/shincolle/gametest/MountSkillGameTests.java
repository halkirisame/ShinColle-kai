package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Skill;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.C2SMountSkillPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountSkillGameTests {
    private MountSkillGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountLightUsesConfiguredCooldownAndChargesOnce(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture f = new Fixture(helper)) {
                int ammo = f.ship.getAmmoLight();
                int cost = f.ship.getAmmoConsumption();
                f.cast(Skill.LIGHT);
                f.cast(Skill.LIGHT);
                helper.assertTrue(f.ship.getAmmoLight() == ammo - cost, "Light skill must charge once");
                int expected = CombatHelper.getAttackDelay(f.ship.getAttrs().getAttackSpeed(), 1);
                helper.assertTrue(f.ship.getStateTimer(ID.T.MountSkillCD1) == expected,
                        "Expected configured CD " + expected + ", got " + f.ship.getStateTimer(ID.T.MountSkillCD1));
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountSkillRejectsHostOutsideCurrentMount(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture f = new Fixture(helper)) {
                f.ship.stopRiding();
                int ammo = f.ship.getAmmoLight();
                f.cast(Skill.LIGHT);
                helper.assertTrue(f.ship.getAmmoLight() == ammo && f.ship.getStateTimer(ID.T.MountSkillCD1) == 0,
                        "Detached host must not attack or start cooldown");
                helper.succeed();
            }
        }, new Vec3(2, 4, 2), new Vec3(5, 4, 2));
    }

    static final class Fixture implements AutoCloseable {
        private final GameTestEntities entities;
        final FakePlayer player;
        final BasicEntityShip ship;
        final BasicEntityMount mount;
        final Entity target;
        private final GameTestHelper helper;

        private Fixture(GameTestHelper helper) {
            this(helper, false);
        }

        Fixture(GameTestHelper helper, boolean carrier) {
            this.helper = helper;
            entities = GameTestEntities.open(helper);
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MountSkills"));
            player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(IllegalStateException::new).setPlayerUID(224001);
            player.setPos(helper.absoluteVec(new Vec3(2, 4, 2)));
            helper.getLevel().addNewPlayer(player);
            GameTestEntities.onCompletion(helper,
                    () -> helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED), "skill owner");
            ship = entities.add(carrier ? ModEntities.CV_WO.get().create(helper.getLevel())
                    : ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            ship.setPlayerUID(224001);
            ship.setPos(player.position());
            ship.setInvulnerable(true);
            ship.setStateMinor(ID.M.NumGrudge, 100000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.AtkType_Heavy, true);
            if (ship instanceof BasicEntityShipCV cv) {
                ship.setStateFlag(ID.F.AtkType_AirLight, true);
                ship.setStateFlag(ID.F.AtkType_AirHeavy, true);
                cv.setNumAircraftLight(3);
                cv.setNumAircraftHeavy(3);
            }
            ship.setAmmoLight(1000);
            ship.setAmmoHeavy(1000);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.SPD, 1F);
            helper.getLevel().addFreshEntity(ship);
            mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.setPos(player.position());
            helper.getLevel().addFreshEntity(mount);
            mount.setHost(ship);
            helper.assertTrue(ship.startRiding(mount, true), "Host must mount");
            helper.assertTrue(player.startRiding(mount, true), "Owner must mount");
            target = entities.add(EntityType.COW.create(helper.getLevel()));
            target.setPos(helper.absoluteVec(new Vec3(5, 4, 2)));
            target.setInvulnerable(true);
            helper.getLevel().addFreshEntity(target);
            GameTestEntities.assertRegistered(helper, target);
            helper.assertTrue(ship.getAttrs().getAttackRange() == 32F && ship.distanceToSqr(target) <= 32F * 32F
                    && ship.getStateFlag(ID.F.AtkType_Light) && ship.getAmmoLight() == 1000, "Invalid weapon fixture");
            helper.assertTrue(mount.getControllingPassenger() == player && TeamHelper.checkSameOwner(player, ship)
                    && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "Invalid mounted fixture");
        }

        void cast(Skill skill) {
            cast(new C2SMountSkillPacket(skill, new C2SMountSkillPacket.EntityTarget(target.getId())));
        }

        void cast(C2SMountSkillPacket request) {
            var before = new HashSet<>(helper.getLevel().getEntities(ship, ship.getBoundingBox().inflate(64)));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                request.encode(buf);
                C2SMountSkillPacket decoded = C2SMountSkillPacket.decode(buf);
                Connection connection = new Connection(PacketFlow.SERVERBOUND);
                connection.setListener(player.connection);
                var constructor = NetworkEvent.Context.class.getDeclaredConstructor(Connection.class, NetworkDirection.class, int.class);
                constructor.setAccessible(true);
                NetworkEvent.Context context = constructor.newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0);
                decoded.handle(() -> context);
                helper.assertTrue(context.getPacketHandled(), "Decoded mounted packet must dispatch through its handler");
            } catch (ReflectiveOperationException error) {
                throw new GameTestAssertException("Cannot construct packet context: " + error);
            } finally {
                buf.release();
                for (Entity entity : helper.getLevel().getEntities(ship, ship.getBoundingBox().inflate(64))) {
                    if (!before.contains(entity)) entities.add(entity);
                }
            }
        }

        @Override
        public void close() {
            player.stopRiding();
            entities.close();
        }
    }
}
