package com.lulan.shincolle.entity.other;

import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipCustomTexture;
import com.lulan.shincolle.entity.IShipOwner;
import com.lulan.shincolle.entity.IShipProjectile;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Beam projectile entity (laser, energy beam, etc.).
 * Implements beam physics: the beam extends from the host entity toward a
 * target
 * direction, damaging entities along its path. The beam persists for a set
 * duration.
 */
public class EntityProjectileBeam extends Entity implements IShipOwner, IShipCustomTexture, IShipProjectile {

    private static final EntityDataAccessor<Float> BEAM_DIR_X = SynchedEntityData.defineId(EntityProjectileBeam.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_DIR_Y = SynchedEntityData.defineId(EntityProjectileBeam.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_DIR_Z = SynchedEntityData.defineId(EntityProjectileBeam.class,
            EntityDataSerializers.FLOAT);
    private static final int LIFE_LENGTH = 31;
    private static final double SPEED = 4.0D;
    private int playerUID;
    private int textureID;
    private int projectileType;
    private int beamType;
    /**
     * Host entity that fired this beam
     */
    private LivingEntity hostEntity;
    private IShipAttackBase hostShip;
    private final List<Entity> damagedTargets = new ArrayList<>();
    /**
     * Beam direction (normalized)
     */
    private double dirX, dirY, dirZ;
    /**
     * Beam damage per tick
     */
    private float beamDamage;

    public EntityProjectileBeam(EntityType<? extends EntityProjectileBeam> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /**
     * Initialize the beam with its host, normalized travel direction, and damage.
     *
     * @param host     the entity firing the beam
     * @param dirX     beam direction X (normalized)
     * @param dirY     beam direction Y (normalized)
     * @param dirZ     beam direction Z (normalized)
     * @param damage   damage per hit
     */
    public void initBeam(IShipAttackBase host, double dirX, double dirY, double dirZ, float damage) {
        if (host instanceof LivingEntity le) {
            this.hostEntity = le;
        }
        this.hostShip = host;
        this.setPlayerUID(host.getPlayerUID());

        double mag = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (mag > 0.001D) {
            this.dirX = dirX / mag;
            this.dirY = dirY / mag;
            this.dirZ = dirZ / mag;
        } else {
            this.dirX = 0;
            this.dirY = 0;
            this.dirZ = 1;
        }

        if (this.hostEntity != null) {
            this.setPos(this.hostEntity.getX() + this.dirX,
                    this.hostEntity.getY() + this.hostEntity.getBbHeight() * 0.5D,
                    this.hostEntity.getZ() + this.dirZ);
        }

        this.beamDamage = damage;
        this.entityData.set(BEAM_DIR_X, (float) this.dirX);
        this.entityData.set(BEAM_DIR_Y, (float) this.dirY);
        this.entityData.set(BEAM_DIR_Z, (float) this.dirZ);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(1.0F, 1.0F);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BEAM_DIR_X, 0.0F);
        this.entityData.define(BEAM_DIR_Y, 0.0F);
        this.entityData.define(BEAM_DIR_Z, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("BeamType", this.beamType);
        compound.putFloat("BeamDamage", this.beamDamage);
        compound.putDouble("DirX", this.dirX);
        compound.putDouble("DirY", this.dirY);
        compound.putDouble("DirZ", this.dirZ);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.beamType = compound.getInt("BeamType");
        this.beamDamage = compound.getFloat("BeamDamage");
        this.dirX = compound.getDouble("DirX");
        this.dirY = compound.getDouble("DirY");
        this.dirZ = compound.getDouble("DirZ");
        this.entityData.set(BEAM_DIR_X, (float) this.dirX);
        this.entityData.set(BEAM_DIR_Y, (float) this.dirY);
        this.entityData.set(BEAM_DIR_Z, (float) this.dirZ);
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 direction = this.level().isClientSide()
                ? getBeamDirection()
                : new Vec3(this.dirX, this.dirY, this.dirZ);
        Vec3 velocity = direction.scale(SPEED);
        this.setDeltaMovement(velocity);
        this.setPos(this.position().add(velocity));

        if (!this.level().isClientSide()) {
            if (this.hostEntity == null || this.tickCount > LIFE_LENGTH) {
                this.discard();
                return;
            }
            applyBeamDamage();
        } else {
            int particleLife = Math.max(1, 32 - this.tickCount);
            ParticleHelper.spawnStickyLightningParticle(this, 0.0F, particleLife, 0);
        }
    }

    /**
     * Apply damage once to every eligible entity near the moving beam head.
     */
    private void applyBeamDamage() {
        List<Entity> entities = this.level().getEntities(this, this.getBoundingBox().inflate(1.5D));

        for (Entity ent : entities) {
            if (!ent.isPickable() || ent == this.hostEntity || this.damagedTargets.contains(ent))
                continue;
            this.damagedTargets.add(ent);

            if (ent instanceof IShipOwner owner) {
                if (this.playerUID > 0 && owner.getPlayerUID() == this.playerUID)
                    continue;
            }
            if (!(ent instanceof LivingEntity livingTarget)
                    || CombatHelper.isFriendlyFire(this.hostEntity, ent)) {
                continue;
            }
            float damage = CombatHelper.applyDamageReduceByDEF(this.beamDamage, ent);
            boolean hurt = livingTarget.hurt(this.damageSources().mobAttack(this.hostEntity), damage);
            if (hurt) {
                LogHelper.info("DIAG: beam hit beam=" + this + " target=" + ent + " damage=" + damage);
            }
            if (hurt && ModList.get().isLoaded("curios")) {
                ShipCuriosIntegration.runOnHitHooks(this.hostEntity, ent, damage);
            }
        }
    }

    /**
     * Disables the legacy debug line renderer. The moving particle trail is
     * the beam's visual representation.
     */
    public float getBeamLength() {
        return 0.0F;
    }

    /**
     * Get the beam direction vector
     */
    public Vec3 getBeamDirection() {
        return new Vec3(this.entityData.get(BEAM_DIR_X), this.entityData.get(BEAM_DIR_Y),
                this.entityData.get(BEAM_DIR_Z));
    }

    // ========== IShipOwner ==========

    @Override
    public int getPlayerUID() {
        return this.playerUID;
    }

    @Override
    public void setPlayerUID(int uid) {
        this.playerUID = uid;
    }

    @Override
    public Entity getHostEntity() {
        return this.hostEntity;
    }

    // ========== IShipCustomTexture ==========

    @Override
    public int getTextureID() {
        return this.textureID;
    }

    @Override
    public void setTextureID(int id) {
        this.textureID = id;
    }

    // ========== IShipProjectile ==========

    @Override
    public int getProjectileType() {
        return this.projectileType;
    }

    @Override
    public void setProjectileType(int type) {
        this.projectileType = type;
    }

    // ========== Beam-specific getters/setters ==========

    public int getBeamType() {
        return this.beamType;
    }

    public void setBeamType(int beamType) {
        this.beamType = beamType;
    }
}
