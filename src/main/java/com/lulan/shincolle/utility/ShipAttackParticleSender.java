package com.lulan.shincolle.utility;

import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.battleship.EntityBBHaruna;
import com.lulan.shincolle.entity.battleship.EntityBBHarunaMob;
import com.lulan.shincolle.entity.battleship.EntityBBHiei;
import com.lulan.shincolle.entity.battleship.EntityBBHieiMob;
import com.lulan.shincolle.entity.battleship.EntityBBKirishima;
import com.lulan.shincolle.entity.battleship.EntityBBKirishimaMob;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.entity.battleship.EntityBBKongouMob;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRe;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRu;
import com.lulan.shincolle.entity.battleship.EntityBattleshipYamato;
import com.lulan.shincolle.entity.battleship.EntityBattleshipYamatoMob;
import com.lulan.shincolle.entity.hime.EntityBattleshipHime;
import com.lulan.shincolle.entity.hime.EntityCarrierWD;
import com.lulan.shincolle.entity.hime.EntityNorthernHime;
import com.lulan.shincolle.entity.mounts.EntityMountBaH;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.ParticlePacketRoute;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.network.S2CAttackAnimationPacket;
import com.lulan.shincolle.reference.unitclass.Dist4d;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Maps an already-executed attack to its visual event without combat decisions. */
public final class ShipAttackParticleSender {
    private ShipAttackParticleSender() { }

    public static void muzzle(Mob source, Entity target, int attackType) {
        if (source.level().isClientSide() || target == null) return;
        if (source.getVehicle() instanceof BasicEntityMount mount) {
            ModNetworking.sendToAllTracking(new S2CAttackAnimationPacket(mount.getId(),
                    S2CAttackAnimationPacket.STANDARD_DURATION_TICKS), mount);
            if (attackType == 1) {
                if (mount instanceof EntityMountBaH) {
                    send(mount, S2CSpawnParticlePacket.entityParameters(ParticlePacketRoute.MOUNT_CANNON_SMOKE_TYPE,
                            mount.getId(), 1.2D, 1.8D, 0.5D));
                } else {
                    Dist4d direction = direction(mount, target);
                    position(mount, mount, 19, mount.getX(), mount.getY() + 1.5D, mount.getZ(),
                            direction.x, 1.2D, direction.z);
                }
            }
            return;
        }
        if (attackType != 1) return;
        Dist4d direction = direction(source, target);
        double dx = direction.x;
        double dy = direction.y;
        double dz = direction.z;
        if (source instanceof EntityBattleshipRe) {
            send(source, S2CSpawnParticlePacket.targetParameters(ParticlePacketRoute.PURPLE_BEAM_TYPE,
                    source.getId(), target.getId(), source.isPassenger() ? 1.45D : 1.7D, 0.08D, 0.02D));
        } else if (source instanceof EntityCarrierWD) {
            send(source, S2CSpawnParticlePacket.targetParameters(ParticlePacketRoute.DUAL_BEAM_TYPE,
                    source.getId(), target.getId(), source.isPassenger() ? 0.8D : 1.85D, 0D, 0D));
        } else if (source instanceof EntityBattleshipYamatoMob hostile) {
            double scale = hostile.getScaleLevel() + 1D;
            smoke(source, scale, scale, 1.6D * scale);
            smoke(source, 0.9D * scale, 1.2D * scale, scale);
            smoke(source, 1.1D * scale, 1.1D * scale, 0.5D * scale);
        } else if (source instanceof EntityBattleshipYamato) {
            smoke(source, 0.9D, 1.3D, 1.2D);
        } else if (source instanceof EntityBBHarunaMob || source instanceof EntityBBHieiMob
                || source instanceof EntityBBKirishimaMob || source instanceof EntityBBKongouMob) {
            double scale = ((BasicEntityShipHostile) source).getScaleLevel() + 1D;
            smoke(source, 0.9D * scale, scale, 1.1D * scale);
        } else if (source instanceof EntityBBHaruna || source instanceof EntityBBHiei
                || source instanceof EntityBBKirishima || source instanceof EntityBBKongou
                || source instanceof EntityBattleshipHime) {
            position(source, source, 19, source.getX(), source.getY() + 0.3D, source.getZ(), dx, 1D, dz);
        } else if (source instanceof EntityBattleshipRu) {
            position(source, source, 19, source.getX(), source.getY(), source.getZ(), dx, 0.7D, dz);
        } else {
            int type = ParticlePacketRoute.ordinaryMuzzleType(attackType, source instanceof EntityNorthernHime);
            position(source, source, type, source.getX(), source.getY(), source.getZ(), dx, dy, dz);
        }
    }

    public static void hit(Mob source, Entity target, int attackType) {
        if (source.level().isClientSide() || target == null) return;
        int type = ParticlePacketRoute.ordinaryHitType(attackType, source instanceof EntityNorthernHime);
        if (type != 0) position(source, target, type, target.getX(), target.getY(), target.getZ(), 0D, 0D, 0D);
    }

    private static void smoke(Mob source, double width, double forward, double height) {
        send(source, S2CSpawnParticlePacket.entityParameters(ParticlePacketRoute.EQUIPMENT_SMOKE_TYPE,
                source.getId(), width, forward, height));
    }

    private static Dist4d direction(Entity source, Entity target) {
        Dist4d direction = CalcHelper.getDistanceFromA2B(source, target);
        direction.normalize();
        return direction;
    }

    private static void position(Mob observer, Entity source, int type, double x, double y, double z,
                                  double vx, double vy, double vz) {
        send(observer, S2CSpawnParticlePacket.position(type, source.getId(), x, y, z, vx, vy, vz));
    }

    private static void send(Entity observer, S2CSpawnParticlePacket packet) {
        ModNetworking.sendToAllTracking(packet, observer);
    }
}
