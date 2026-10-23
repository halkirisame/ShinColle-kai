package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.battleship.EntityBBHaruna;
import com.lulan.shincolle.entity.battleship.EntityBBHarunaMob;
import com.lulan.shincolle.entity.battleship.EntityBBHiei;
import com.lulan.shincolle.entity.battleship.EntityBBHieiMob;
import com.lulan.shincolle.entity.battleship.EntityBBKirishima;
import com.lulan.shincolle.entity.battleship.EntityBBKirishimaMob;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.entity.battleship.EntityBBKongouMob;
import com.lulan.shincolle.entity.battleship.EntityBattleshipNagato;
import com.lulan.shincolle.entity.battleship.EntityBattleshipNagatoMob;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRu;
import com.lulan.shincolle.entity.battleship.EntityBattleshipYamato;
import com.lulan.shincolle.entity.battleship.EntityBattleshipYamatoMob;
import com.lulan.shincolle.entity.carrier.EntityCarrierWo;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerAkatsuki;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerAkatsukiMob;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerHibiki;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerHibikiMob;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerIkazuchi;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerIkazuchiMob;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerInazuma;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerInazumaMob;
import com.lulan.shincolle.entity.mounts.EntityMountBaH;
import com.lulan.shincolle.entity.mounts.EntityMountAfH;
import com.lulan.shincolle.entity.mounts.EntityMountIsH;
import com.lulan.shincolle.entity.mounts.EntityMountMiH;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.entity.other.EntityAirplaneTakoyaki;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.entity.other.EntityRensouhouMob;
import com.lulan.shincolle.entity.submarine.EntitySubmYo;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.ChatFormatting;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.EmotionHelper;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Client-local equipment exhaust, liquid wakes and mount drips. */
public final class ShipAmbientVisuals {
    private ShipAmbientVisuals() {
    }

    public static void tick(BasicEntityShip ship) {
        if (!ship.level().isClientSide()) {
            return;
        }
        if ((ship.tickCount & 31) == 0) {
            nameTag(ship);
        }
        wake(ship, EntityHelper.checkEntityIsInLiquid(ship) && !ship.isPassenger(), 0.4D, true);
        damage(ship, ship.getStateEmotion(ID.S.HPState), ship.getBbWidth());
        int state = ship.getStateEmotion(ID.S.State);
        boolean fueled = !ship.getStateFlag(ID.F.NoFuel);
        if ((ship.tickCount & 3) == 0 && fueled && !ship.getIsSitting()) {
            if (ship instanceof EntityBattleshipNagato && EmotionHelper.checkModelState(1, state)) {
                exhaust(ship, -0.56F, 1.5D, 1D, 43);
            } else if (ship instanceof EntityBattleshipYamato && EmotionHelper.checkModelState(0, state)) {
                exhaust(ship, -0.63F, 1.65D, 0D, 20);
            } else if (EmotionHelper.checkModelState(0, state)) {
                if (ship instanceof EntityBBKongou) {
                    exhaust(ship, -0.7F, 1.17D, 1D, 43);
                    exhaust(ship, -0.45F, 1.32D, 1D, 43);
                } else if (ship instanceof EntityBBHiei || ship instanceof EntityBBHaruna
                        || ship instanceof EntityBBKirishima) {
                    exhaust(ship, -0.6F, 1.17D, 1D, 43);
                } else if (ship instanceof EntityDestroyerAkatsuki akatsuki && akatsuki.getRiderType() < 1) {
                    exhaust(ship, ship.isPassenger() ? -0.62F : -0.42F, 1.4D, 0D, 20);
                } else if (ship instanceof EntityDestroyerHibiki hibiki && hibiki.getRiderType() < 2
                        || ship instanceof EntityDestroyerInazuma inazuma && inazuma.getRiderType() < 4
                        || ship instanceof EntityDestroyerIkazuchi) {
                    exhaust(ship, -0.42F, 1.4D, 0D, 20);
                }
            }
        }
        if (ship instanceof EntityCarrierWo) {
            carrierEyes(ship, state, fueled);
        } else if (ship instanceof EntitySubmYo) {
            submarineEyes(ship, state, fueled);
        }
        if ((ship.tickCount & 15) == 0 && !ship.getIsSitting() && !ship.isPassenger()) {
            if (ship instanceof EntityBattleshipRu && EmotionHelper.checkModelState(3, state)
                    && (ship.tickCount & 511) < 400) {
                ParticleHelper.spawnEquipmentSparkParticle(ship, 1.34D, 0.12D, 0.17D);
            } else if (ship instanceof EntityCarrierWo && EmotionHelper.checkModelState(4, state)) {
                ParticleHelper.spawnEquipmentSparkParticle(ship, 1.28D, 0.12D, 0.17D);
            }
        }
    }

    private static void nameTag(BasicEntityShip ship) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || ship.unitNames == null) {
            return;
        }
        int distance = ConfigHandler.nameTagDistance();
        if (!ConfigHandler.showNameTag() && !minecraft.options.keySprint.isDown()
                && ship.distanceToSqr(minecraft.player) >= (double) distance * distance) {
            return;
        }
        StringBuilder text = new StringBuilder();
        ChatFormatting color = ship.getPlayerUID() == TeamHelper.getPlayerUID(minecraft.player)
                ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
        int lines = 0;
        for (String name : ship.unitNames) {
            if (name != null && name.length() > 1) {
                if (lines++ > 0) text.append("\n");
                text.append(color).append(name);
            }
        }
        if (ConfigHandler.debugMode()) {
            if (lines++ > 0) text.append("\n");
            text.append(ChatFormatting.GREEN).append("UID ").append(ship.getShipUID());
        }
        if (lines > 0) {
            CustomTextVisuals.atEntity(ship, 0D, ship.getBbHeight() + 0.8D, 0D, text.toString(), lines);
        }
    }

    public static void tick(BasicEntityShipHostile ship) {
        if (!ship.level().isClientSide()) {
            return;
        }
        wake(ship, EntityHelper.checkEntityIsInLiquid(ship), 0.4D, false);
        damage(ship, ship.getStateEmotion(ID.S.HPState), ship.getBbWidth() * 1.5D);
        if ((ship.tickCount & 3) != 0) {
            return;
        }
        int state = ship.getStateEmotion(ID.S.State);
        int scale = ship.getScaleLevel();
        float factor = scale + 1F;
        if (ship instanceof EntityBattleshipYamatoMob) {
            exhaust(ship, -0.63F * factor, 1.65D * factor, factor, 43);
        } else if (ship instanceof EntityBattleshipNagatoMob && EmotionHelper.checkModelState(1, state)) {
            exhaust(ship, -0.56F * factor, 1.5D * factor, factor, 43);
        } else if (EmotionHelper.checkModelState(0, state)) {
            if (ship instanceof EntityBBKongouMob) {
                float[] xs = {-0.7F, -0.82F, -1.25F, -1.64F};
                double[] ys = {1.17D, 2.47D, 3.57D, 4.74D};
                float[] xs2 = {-0.45F, -1.33F, -1.98F, -2.61F};
                double[] ys2 = {1.32D, 2.21D, 3.18D, 4.27D};
                exhaust(ship, xs[scale], ys[scale], factor, 43);
                exhaust(ship, xs2[scale], ys2[scale], factor, 43);
            } else if (ship instanceof EntityBBHieiMob) {
                exhaust(ship, -0.6F, 1.17D, 1D, 43);
            } else if (ship instanceof EntityBBHarunaMob || ship instanceof EntityBBKirishimaMob) {
                float[] xs = {-0.6F, -1.16F, -1.76F, -2.36F};
                double[] ys = {1.17D, 2.3D, 3.4D, 4.6D};
                exhaust(ship, xs[scale], ys[scale], factor, 43);
            } else if (ship instanceof EntityDestroyerAkatsukiMob || ship instanceof EntityDestroyerHibikiMob
                    || ship instanceof EntityDestroyerIkazuchiMob || ship instanceof EntityDestroyerInazumaMob) {
                float[] xs = {-0.42F, -0.8F, -1.2F, -1.65F};
                double[] ys = {1.4D, 2.7D, 4.1D, 5.3D};
                exhaust(ship, xs[scale], ys[scale], 1D + scale * 0.8D, 43);
            }
        }
    }

    public static void tick(BasicEntityMount mount) {
        if (!mount.level().isClientSide()) {
            return;
        }
        wake(mount, EntityHelper.checkEntityIsInLiquid(mount), 0.6D, true);
        if (mount instanceof EntityMountBaH && (mount.tickCount & 3) == 0) {
            at(mount, 0D, 3D, 0D, 20);
        } else if (mount instanceof EntityMountAfH && (mount.tickCount & 7) == 0) {
            mouthSmoke(mount, -1F);
            mouthSmoke(mount, -1.8F);
        }
        if (mount instanceof com.lulan.shincolle.entity.mounts.EntityMountHbH && (mount.tickCount & 15) == 0) {
            ParticleHelper.spawnLightningParticle(mount, 0F, 0);
            for (int i = 0; i < 3; i++) {
                if (mount.getRandom().nextInt(3) == 0) {
                    ParticleHelper.spawnLightningParticle(mount, 0F, 0);
                }
            }
        }
        if ((mount.tickCount & 7) == 0) {
            if (mount instanceof EntityMountIsH) {
                drip(mount, 0.65F, -0.15F, 0.7D, 28);
            } else if (mount instanceof EntityMountMiH) {
                drip(mount, 1.2F, -0.25F, 0.85D, 49);
            }
        }
    }

    public static void death(BasicEntityShip ship) {
        deathSmoke(ship, 0.1D, 1.5D);
    }

    public static void death(BasicEntityShipHostile ship) {
        deathSmoke(ship, 0.3D, 1D + ship.getScaleLevel());
    }

    private static void damage(LivingEntity entity, int hpState, double width) {
        if ((entity.tickCount & 15) != 0) {
            return;
        }
        int type = switch (hpState) {
            case ID.HPState.MINOR -> 4;
            case ID.HPState.MODERATE -> 5;
            case ID.HPState.HEAVY -> 7;
            default -> 0;
        };
        if (type != 0) {
            ParticleHelper.spawnAttackParticleAt(entity.level(), entity.getX(), entity.getY() + 0.7D,
                    entity.getZ(), width, 0.05D, 0D, type);
        }
    }

    private static void deathSmoke(LivingEntity entity, double height, double scale) {
        if (!entity.level().isClientSide() || (entity.tickCount & 3) != 0) {
            return;
        }
        int count = (int) ((3 - Minecraft.getInstance().options.particles().get().getId()) * 1.8F);
        double range = entity.getBbWidth() * 1.2D;
        for (int i = 0; i < count; i++) {
            ParticleHelper.spawnAttackParticleAt(entity.level(),
                    entity.getX() - range + entity.getRandom().nextDouble() * range * 2D,
                    entity.getY() + height + entity.getRandom().nextDouble() * 0.3D,
                    entity.getZ() - range + entity.getRandom().nextDouble() * range * 2D,
                    scale, 0D, 0D, 43);
        }
    }

    private static void mouthSmoke(BasicEntityMount mount, float x) {
        float[] pos = CalcHelper.rotateXZByAxis(0F, x, mount.yBodyRot * Mth.DEG_TO_RAD, 1F);
        ParticleHelper.spawnAttackParticleAt(mount.level(), mount.getX() + pos[1], mount.getY() + 0.9D,
                mount.getZ() + pos[0], 0D, 0.1D, 0D, 18);
    }

    public static void tick(BasicEntityAirplane airplane) {
        if (!airplane.level().isClientSide()) {
            return;
        }
        Vec3 motion = airplane.getDeltaMovement();
        if (airplane instanceof EntityAirplaneTakoyaki && (airplane.tickCount & 1) == 0) {
            ParticleHelper.spawnAttackParticleAt(airplane.level(), airplane.getX(), airplane.getY() + 0.1D,
                    airplane.getZ(), -motion.x * 0.5D, 0.07D, -motion.z * 0.5D, 18);
        } else if (airplane instanceof EntityAirplane) {
            ParticleHelper.spawnAttackParticleAt(airplane.level(), airplane.getX() - motion.x * 1.5D,
                    airplane.getY() + 0.5D - motion.y * 1.5D, airplane.getZ() - motion.z * 1.5D,
                    -motion.x * 0.5D, -motion.y * 0.5D, -motion.z * 0.5D, 17);
        }
    }

    public static void tick(EntityAbyssMissile missile) {
        if (!missile.getTrailDelay().allowsEmission()) return;
        if (missile.level().isClientSide() && !missile.isInvisible() && missile.getMissileType() == 2) {
            // railgun: two short lightning strips on the head every tick
            for (int i = 0; i < 2; i++) {
                Minecraft.getInstance().particleEngine.add(new ParticleStickyLightning(
                        (net.minecraft.client.multiplayer.ClientLevel) missile.level(), missile, 0F, 10, 4));
            }
            return;
        }
        if (!missile.level().isClientSide() || missile.isInvisible() || missile.tickCount <= 2
                || missile.getMissileType() == 2) {
            return;
        }
        if (missile.vel0 < 0.45D || missile.vel0 > 0.55D) {
            if (missile.getMovementType() != 2) {
                int trailType = missile.vel0 > 0.55D ? 1 : 2;
                for (int j = 0; j < 4; j++) {
                    Minecraft.getInstance().particleEngine.add(
                            new ParticleSpray(missile, trailType, new double[]{missile.vel0, j}));
                }
            }
            return;
        }
        int type = missile.getMissileType() == 3 || missile.getMissileType() == 4 ? 18 : 15;
        Vec3 motion = missile.getDeltaMovement();
        if (motion.lengthSqr() == 0D) {
            return;
        }
        for (int j = 0; j < 4; j++) {
            ParticleHelper.spawnAttackParticleAt(missile.level(), missile.getX() + motion.x * (2D - 1.5D * j),
                    missile.getY() + 0.5D + motion.y * (2D - 1.5D * j),
                    missile.getZ() + motion.z * (2D - 1.5D * j),
                    -motion.x * 0.1D, -motion.y * 0.1D, -motion.z * 0.1D, type);
        }
    }

    public static void tick(EntityRensouhou turret) {
        turretTrail(turret);
    }

    public static void tick(EntityRensouhouMob turret) {
        turretTrail(turret);
    }

    private static void turretTrail(LivingEntity turret) {
        if (!turret.level().isClientSide()) {
            return;
        }
        double dx = turret.getX() - turret.xo;
        double dz = turret.getZ() - turret.zo;
        if (dx != 0D || dz != 0D) {
            ParticleHelper.spawnAttackParticleAt(turret.level(), turret.getX() + dx * 1.5D,
                    turret.getY(), turret.getZ() + dz * 1.5D, -dx * 0.5D, 0D, -dz * 0.5D, 15);
        }
    }

    private static void wake(LivingEntity entity, boolean inWater, double height, boolean clamp) {
        if (!inWater) {
            return;
        }
        // Remote entities are interpolated; their velocity need not describe that movement.
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        if (clamp) {
            dx = Mth.clamp(dx, -0.25D, 0.25D);
            dz = Mth.clamp(dz, -0.25D, 0.25D);
        }
        if (dx != 0D || dz != 0D) {
            ParticleHelper.spawnAttackParticleAt(entity.level(), entity.getX() + dx * 3D,
                    entity.getY() + height, entity.getZ() + dz * 3D, -dx, entity.getBbWidth(), -dz, 47);
        }
    }

    private static void exhaust(LivingEntity entity, float z, double height, double scale, int type) {
        float[] pos = CalcHelper.rotateXZByAxis(z, 0F, entity.yBodyRot * Mth.DEG_TO_RAD, 1F);
        ParticleHelper.spawnAttackParticleAt(entity.level(), entity.getX() + pos[1], entity.getY() + height,
                entity.getZ() + pos[0], scale, 0D, 0D, type);
    }

    private static void drip(LivingEntity entity, float z, float x, double height, int type) {
        float[] pos = CalcHelper.rotateXZByAxis(z, x, entity.yBodyRot * Mth.DEG_TO_RAD, 1F);
        at(entity, pos[1], height, pos[0], type);
    }

    private static void at(LivingEntity entity, double x, double y, double z, int type) {
        ParticleHelper.spawnAttackParticleAt(entity.level(), entity.getX() + x, entity.getY() + y,
                entity.getZ() + z, 0D, 0D, 0D, type);
    }

    private static void carrierEyes(BasicEntityShip ship, int state, boolean fueled) {
        if ((ship.tickCount & 3) != 0 || !fueled || !EmotionHelper.checkModelState(0, state)
                || ship.getIsSitting() && ship.getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED) {
            return;
        }
        float[][] eyes = ship.getIsSitting()
                ? new float[][] {{-0.3F, 1F, -0.4F}, {-0.7F, 0.8F, 0.6F}}
                : new float[][] {{0.55F, 1F, 0.2F}, {-0.55F, 1F, 0.2F}};
        for (float[] eye : eyes) {
            if (!ship.getIsSitting() && ship.getStateEmotion(ID.S.Emotion2) == 1) {
                float[] tilt = CalcHelper.rotateXZByAxis(eye[0], eye[1], -0.24F, 1F);
                eye[0] = tilt[0];
                eye[1] = tilt[1];
            }
            float[] pos = CalcHelper.rotateXYZByYawPitch(eye[0], eye[1], eye[2],
                    ship.getYHeadRot() * Mth.DEG_TO_RAD, ship.getXRot() * Mth.DEG_TO_RAD, 1F);
            ParticleHelper.spawnAttackParticleAt(ship.level(), ship.getX() + pos[0], ship.getY() + 1.5D + pos[1],
                    ship.getZ() + pos[2], 0D, 0.05D, 1D, 16);
        }
    }

    private static void submarineEyes(BasicEntityShip ship, int state, boolean fueled) {
        if ((ship.tickCount & 3) != 0 || !fueled || !EmotionHelper.checkModelState(0, state)
                || ship.getIsSitting() && ship.getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED) {
            return;
        }
        float y = ship.getIsSitting() ? 1.5F : 1.8F;
        float z = ship.getIsSitting() ? -0.5F : -0.35F;
        for (float x : new float[] {0.35F, -0.35F}) {
            float[] pos = CalcHelper.rotateXYZByYawPitch(x, y, z, ship.yBodyRot * Mth.DEG_TO_RAD, 0F, 1F);
            ParticleHelper.spawnAttackParticleAt(ship.level(), ship.getX() + pos[0], ship.getY() + pos[1],
                    ship.getZ() + pos[2], 0D, 0.05D, 0.5D, 16);
        }
    }
}
