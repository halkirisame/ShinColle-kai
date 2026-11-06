package com.lulan.shincolle.playerskill;

import com.lulan.shincolle.ai.domain.combat.MountSkillDecision;
import com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Skill;
import com.lulan.shincolle.ai.domain.combat.MountSkillDecision.TargetKind;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.network.C2SMountSkillPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CMountSkillPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Server admission and execution for the four mounted manual weapons. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ShipSkillHandler {
    private static final Map<BasicEntityShip, EnumMap<Skill, Long>> ACCEPTED_TICKS = new WeakHashMap<>();
    private static final Map<ServerPlayer, S2CMountSkillPacket> SENT = new WeakHashMap<>();

    private ShipSkillHandler() { }

    /** Shared relation check for input, HUD and server authorization. */
    public static BasicEntityShip controlledHost(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !(player.getVehicle() instanceof BasicEntityMount mount) || !mount.isAlive()
                || mount.getControllingPassenger() != player
                || !(mount.getHostEntity() instanceof BasicEntityShip ship) || !ship.isAlive()
                || ship.getVehicle() != mount || mount.level() != player.level() || ship.level() != player.level()
                || !TeamHelper.checkSameOwner(player, ship)) return null;
        return ship;
    }

    public static boolean hasAbility(BasicEntityShip ship, Skill skill) {
        return ship.getStateFlag(switch (skill) {
            case LIGHT -> ID.F.AtkType_Light;
            case HEAVY -> ID.F.AtkType_Heavy;
            case AIR_LIGHT -> ID.F.AtkType_AirLight;
            case AIR_HEAVY -> ID.F.AtkType_AirHeavy;
        });
    }

    private static int timer(Skill skill) {
        return switch (skill) {
            case LIGHT -> ID.T.MountSkillCD1;
            case HEAVY -> ID.T.MountSkillCD2;
            case AIR_LIGHT -> ID.T.MountSkillCD3;
            case AIR_HEAVY -> ID.T.MountSkillCD4;
        };
    }

    public static int cooldown(BasicEntityShip ship, Skill skill) {
        return ship.getStateTimer(timer(skill));
    }

    /** Called on the server thread by the typed packet handler. */
    public static void handleMountSkill(ServerPlayer player, Skill skill, C2SMountSkillPacket.Target proposed) {
        BasicEntityShip ship = controlledHost(player);
        if (ship == null) return;
        Entity target = null;
        BlockPos pos = null;
        double range = ship.getAttrs().getAttackRange();
        if (proposed instanceof C2SMountSkillPacket.EntityTarget entity) {
            target = player.level().getEntity(entity.entityId());
            if (target == null || !target.isAlive() || target == player || target == player.getVehicle() || target == ship
                    || TeamHelper.checkSameOwner(ship, target) || ship.distanceToSqr(target) > range * range) return;
        } else {
            pos = ((C2SMountSkillPacket.BlockTarget) proposed).pos();
            if (player.level().isOutsideBuildHeight(pos) || !player.level().getWorldBorder().isWithinBounds(pos)
                    || !player.level().hasChunkAt(pos) || ship.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) return;
        }
        long tick = player.server.getTickCount();
        EnumMap<Skill, Long> accepted = ACCEPTED_TICKS.computeIfAbsent(ship, ignored -> new EnumMap<>(Skill.class));
        boolean sameTick = Long.valueOf(tick).equals(accepted.get(skill));
        if (!MountSkillDecision.reject(skill, target != null ? TargetKind.ENTITY : TargetKind.BLOCK,
                hasAbility(ship, skill), ship instanceof BasicEntityShipCV, cooldown(ship, skill), sameTick).isEmpty()) return;

        accepted.put(skill, tick);
        switch (skill) {
            case LIGHT -> ship.attackEntityWithAmmo(target);
            case HEAVY -> {
                if (target != null) ship.attackEntityWithHeavyAmmo(target);
                else ship.attackEntityWithHeavyAmmo(pos);
            }
            case AIR_LIGHT -> ((BasicEntityShipCV) ship).attackEntityWithAircraft(target);
            case AIR_HEAVY -> ((BasicEntityShipCV) ship).attackEntityWithHeavyAircraft(target);
        }
        int delay = CombatHelper.getAttackDelay(ship.getAttrs().getAttackSpeed(), skill.ordinal() + 1);
        ship.setStateTimer(timer(skill), delay);
        if (skill == Skill.AIR_LIGHT || skill == Skill.AIR_HEAVY) {
            ship.setStateTimer(ID.T.MountSkillCD3, delay);
            ship.setStateTimer(ID.T.MountSkillCD4, delay);
        }
    }

    @SubscribeEvent
    public static void syncMountedSkills(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        BasicEntityShip ship = controlledHost(player);
        if (ship == null) {
            SENT.remove(player);
            return;
        }
        S2CMountSkillPacket packet = S2CMountSkillPacket.of(ship);
        if (!packet.equals(SENT.put(player, packet))) ModNetworking.sendToPlayer(packet, player);
    }
}
