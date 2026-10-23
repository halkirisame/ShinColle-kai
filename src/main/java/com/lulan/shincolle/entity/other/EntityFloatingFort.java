package com.lulan.shincolle.entity.other;

import com.lulan.shincolle.ai.domain.combat.FortAttributeProfile;
import com.lulan.shincolle.ai.domain.combat.FortFlightDecision;
import com.lulan.shincolle.ai.domain.combat.FortFlightDecision.Completion;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.ai.path.ShipMoveControl;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.reference.unitclass.MissileData;
import com.lulan.shincolle.equip.ShipOnHitEffects;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Objects;

/**
 * Floating fortress: the heavy bombardment of the northern princess. It flies to its target and
 * explodes there with the damage of its launching ship's heavy cannon.
 * Ported from 1.10.2 EntityFloatingFort.
 */
public class EntityFloatingFort extends BasicEntityAirplane {
    private Completion completion = Completion.ACTIVE;

    public EntityFloatingFort(EntityType<? extends EntityFloatingFort> type, Level level) {
        super(type, level);
    }

    @Override
    public void initAttrs(IShipAttackBase host, Entity target, int scaleLevel, float... par2) {
        if (!(host instanceof BasicEntityShip ship)) {
            return;
        }
        float launchY = par2 != null && par2.length > 0 ? par2[0] : (float) ship.getY();
        this.host = ship;
        this.setEntityTarget(target);
        this.setScaleLevel(scaleLevel);
        this.numAmmoLight = 0;
        this.numAmmoHeavy = 1;
        this.setPos(ship.getX(), launchY, ship.getZ());

        // The fortress is a detached cannon shot: its own values, not the airplane's.
        Attrs hostAttrs = ship.getAttrs();
        FortAttributeProfile profile = FortAttributeProfile.of(new FortAttributeProfile.Host(
                ship.getMaxHealth(), ship.getAttackBaseDamage(1, target), ship.getAttackBaseDamage(2, target),
                hostAttrs.getDefense(), hostAttrs.getAttackSpeed(), hostAttrs.getMoveSpeed(),
                hostAttrs.getAttrsBuffed(ID.Attrs.DODGE)));
        this.shipAttrs = Attrs.copyAttrs(hostAttrs);
        this.shipAttrs.setAttrsBuffed(ID.Attrs.HP, profile.health());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.ATK_L, profile.lightDamage());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.ATK_H, profile.heavyDamage());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.ATK_AL, profile.lightAirDamage());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.ATK_AH, profile.heavyAirDamage());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.DEF, profile.defense());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.SPD, profile.attackSpeed());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.MOV, profile.moveSpeed());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.HIT, profile.hit());
        this.shipAttrs.setAttrsBuffed(ID.Attrs.DODGE, profile.dodge());

        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(profile.health());
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(profile.moveSpeed());
        Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE)).setBaseValue(profile.followRange());
        Objects.requireNonNull(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE))
                .setBaseValue(profile.knockbackResistance());
        this.setHealth(this.getMaxHealth());

        this.navigation = this.createNavigation(this.level());
        this.moveControl = new ShipMoveControl(this, 45F, 1.5F);
        this.setAIList();
    }

    /** The fortress does not circle or search like an airplane; its flight is decided in {@link #flightStep}. */
    @Override
    protected void setAIList() {
        Entity initialTarget = this.getEntityTarget();
        this.clearAITasks();
        this.clearAITargetTasks();
        this.setEntityTarget(initialTarget);
    }

    @Override
    public void tick() {
        if (this.level().isClientSide() && this.isAlive() && (this.tickCount & 1) == 0) {
            ParticleHelper.spawnAttackParticleAt(this.level(), this.getX(), this.getY() + 0.2D, this.getZ(),
                    -this.getDeltaMovement().x * 0.5D, 0.07D, -this.getDeltaMovement().z * 0.5D, 29);
        }

        if (!this.level().isClientSide() && this.isAlive() && !this.flightStep()) {
            return;
        }

        super.tick();
    }

    /** @return true while the fortress is still flying */
    private boolean flightStep() {
        Entity target = this.getEntityTarget();
        FortFlightDecision.Result result = FortFlightDecision.decide(this.facts(target));
        switch (result.action()) {
            case PATH -> {
                this.getNavigation().moveTo(target, 1D);
                return true;
            }
            case DETONATE -> {
                this.impactExplosion();
                return false;
            }
            case DISCARD -> {
                this.completion = Completion.FINISHED;
                this.discard();
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    private FortFlightDecision.Facts facts(Entity target) {
        boolean targetValid = target != null && target.isAlive();
        double distance = 0D;
        if (target != null) {
            double x = target.getX() - this.getX();
            double y = target.getY() + 1D - this.getY();
            double z = target.getZ() - this.getZ();
            distance = x * x + y * y + z * z;
        }
        return new FortFlightDecision.Facts(this.tickCount, this.backHome, this.hostIsValid(), targetValid,
                distance, this.completion);
    }

    /** Only the launching ship keeps the fortress flying; its admiral may be dead or offline. */
    @Override
    protected boolean isOwnerLost() {
        return false;
    }

    private boolean hostIsValid() {
        return this.host instanceof Entity ship && ship.isAlive() && ship.level() == this.level();
    }

    /** Heavy entry: explodes when the shot is due, as the flight would have decided on its own. */
    @Override
    public boolean attackEntityWithHeavyAmmo(Entity target) {
        if (this.level().isClientSide()) {
            return false;
        }
        switch (FortFlightDecision.decide(this.facts(target)).action()) {
            case DETONATE -> {
                this.impactExplosion();
                return true;
            }
            case DISCARD -> {
                this.completion = Completion.FINISHED;
                this.discard();
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** Ammunition and the aircraft slot were spent with the shot; nothing comes back, however it ends. */
    @Override
    protected void returnSummonResource() {
    }

    private void impactExplosion() {
        if (this.level().isClientSide() || !this.isAlive() || this.completion == Completion.FINISHED
                || !(this.host instanceof Entity ship)) {
            return;
        }
        this.completion = Completion.FINISHED;
        this.getNavigation().stop();

        // The ship, not this copy of its values, decides the special effect, the bonuses and the rolls.
        IShipAttackBase attacker = this.host;
        MissileData md = this.getMissileData(2);
        int specialType = md != null ? md.type : 0;
        CombatHelper.specialAttackEffect(attacker, specialType,
                new float[]{(float) this.getX(), (float) this.getY(), (float) this.getZ()});

        Entity owner = this.getHostEntity();
        AABB hitBox = this.getBoundingBox().inflate(4.5D, 4.5D, 4.5D);
        List<Entity> hitList = this.level().getEntities(this, hitBox);

        for (Entity ent : hitList) {
            if (!ent.isPickable() || ent == this || ent == ship || ent == owner) {
                continue;
            }
            if (TargetHelper.isEntityInvulnerable(ent)) {
                continue;
            }
            if (TeamHelper.checkSameOwner(this, ent)) {
                continue;
            }

            float atk = this.shipAttrs.getAttackDamageHeavy();
            atk = CombatHelper.modDamageByAdditionAttrs(attacker, ent, atk, 0);
            atk = CombatHelper.applyCombatRateToDamage(attacker, ent, false, 1F, atk);
            atk = CombatHelper.applyDamageReduceOnPlayer(ent, atk);

            if (CombatHelper.isFriendlyFire(ship, ent) || atk <= 0F) {
                continue;
            }

            if (ent.hurt(this.damageSources().explosion(this, ship), atk) && ship instanceof LivingEntity livingHost) {
                ShipOnHitEffects.dispatch(livingHost, ent, atk);
            }
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            double x = this.getX();
            double y = this.getY();
            double z = this.getZ();
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0D, 0D, 0D, 0D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.8D, 0.8D, 0.8D, 0.03D);
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 10, 0.6D, 0.6D, 0.6D, 0.06D);
        }

        this.discard();
    }

    @Override
    public boolean useAmmoLight() {
        return false;
    }

    @Override
    public boolean useAmmoHeavy() {
        return true;
    }
}
