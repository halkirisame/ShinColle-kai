package com.lulan.shincolle.utility;

import com.lulan.shincolle.entity.*;
import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.entity.other.EntityProjectileStatic;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.reference.unitclass.Attrs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Helper for combat damage calculations.
 * <p>
 * Handles miss/crit/multi-hit, damage type modifiers, defense reduction,
 * dodge chance, and ship-vs-ship damage scaling.
 * <p>
 * Ported from 1.10.2 CombatHelper.
 */
public class CombatHelper {

    /**
     * Apply combat rate to damage. Rolls for miss, critical, double hit, triple
     * hit.
     * Priority: miss > cri > dhit > thit > normal
     * Uses cumulative probability on a single random roll.
     *
     * @param host        attacker (IShipAttackBase)
     * @param target      target entity
     * @param canMultiHit whether multi-hit is allowed for this attack
     * @param distance    distance to target
     * @param rawAtk      raw attack damage
     * @return modified damage (0 = miss)
     */
    public static float applyCombatRateToDamage(IShipAttackBase host, Entity target,
                                                boolean canMultiHit, float distance, float rawAtk) {
        if (host == null)
            return rawAtk;

        // if host is a minion, get host's host
        if ((host instanceof BasicEntitySummon || host instanceof BasicEntityMount) &&
                host.getHostEntity() instanceof IShipAttackBase) {
            host = (IShipAttackBase) host.getHostEntity();
        }

        // cumulative probability: miss > cri > dhit > thit
        float miss = calcMissRate(host, distance);
        float cri = host.getAttrs().getAttrsBuffed(ID.Attrs.CRI);
        float dhit = host.getAttrs().getAttrsBuffed(ID.Attrs.DHIT);
        float thit = host.getAttrs().getAttrsBuffed(ID.Attrs.THIT);

        // cumulative ranges
        cri += miss;
        if (cri < miss)
            cri = miss;

        dhit += cri;
        if (dhit < cri)
            dhit = cri;

        thit += dhit;
        if (thit < dhit)
            thit = dhit;

        // roll
        float roll = host.getRand().nextFloat();

        // miss
        if (roll <= miss) {
            if (host instanceof Entity e)
                ParticleHelper.spawnAttackTextParticle(e, 0);
            LogHelper.debug("DEBUG: combat rate: " + host + " -> " + target
                    + " roll=" + roll + " miss=" + miss + " cri=" + cri + " dhit=" + dhit + " thit=" + thit
                    + " result=MISS rawAtk=" + rawAtk + " finalAtk=0");
            return 0F;
        }
        // critical
        else if (roll <= cri) {
            if (host instanceof Entity e)
                ParticleHelper.spawnAttackTextParticle(e, 1);
            LogHelper.debug("DEBUG: combat rate: " + host + " -> " + target
                    + " roll=" + roll + " miss=" + miss + " cri=" + cri + " dhit=" + dhit + " thit=" + thit
                    + " result=CRITICAL rawAtk=" + rawAtk + " finalAtk=" + (rawAtk * 1.5F));
            return rawAtk * 1.5F;
        }
        // double hit
        else if (canMultiHit && roll <= dhit) {
            if (host instanceof Entity e)
                ParticleHelper.spawnAttackTextParticle(e, 2);
            LogHelper.debug("DEBUG: combat rate: " + host + " -> " + target
                    + " roll=" + roll + " miss=" + miss + " cri=" + cri + " dhit=" + dhit + " thit=" + thit
                    + " result=DOUBLE_HIT rawAtk=" + rawAtk + " finalAtk=" + (rawAtk * 2F));
            return rawAtk * 2F;
        }
        // triple hit
        else if (canMultiHit && roll <= thit) {
            if (host instanceof Entity e)
                ParticleHelper.spawnAttackTextParticle(e, 3);
            LogHelper.debug("DEBUG: combat rate: " + host + " -> " + target
                    + " roll=" + roll + " miss=" + miss + " cri=" + cri + " dhit=" + dhit + " thit=" + thit
                    + " result=TRIPLE_HIT rawAtk=" + rawAtk + " finalAtk=" + (rawAtk * 3F));
            return rawAtk * 3F;
        }

        // normal hit
        LogHelper.debug("DEBUG: combat rate: " + host + " -> " + target
                + " roll=" + roll + " miss=" + miss + " cri=" + cri + " dhit=" + dhit + " thit=" + thit
                + " result=NORMAL rawAtk=" + rawAtk + " finalAtk=" + rawAtk);
        return rawAtk;
    }

    /**
     * The widest target the beside-the-target miss point is built for. A miss point at 6 blocks to the
     * side and at most 1 along the line of fire is at least 6 / sqrt(2) = 4.24 blocks from the target
     * along at least one axis, and a splash box reaches 3.75 + width / 2, which is at most that for a
     * width up to 2 * (6 / sqrt(2) - 3.75) = 0.985; 0.98 is used.
     */
    static final double MISS_AIM_MAX_WIDTH = 0.98D;

    /**
     * Where a heavy shot that missed aims. A target no wider than {@link #MISS_AIM_MAX_WIDTH}: beside
     * it on the ground, not behind or above it. The point is 6 to 7 blocks to one side of the target,
     * seen from the shooter, and at most one block towards or away from the shooter; its height is the
     * target's. That is outside the splash box of a shot at such a target, so a miss stays a miss, and
     * the missile lands on the ground instead of flying on above the target until it times out.
     * A wider target keeps the earlier point, up to five blocks to either side and up to five above.
     * <p>
     * The horizontal coordinates are doubles, and so is the result: the margin of a miss is a few
     * thousandths of a block (4.2426 against a reach of at most 4.24), so the point must keep the
     * precision of the target's position. Rounding it to a float loses that margin from about a million
     * blocks out, where a float is a quarter of a block apart or more; in doubles the spacing at the
     * world's edge is under four billionths of a block, so the margin holds wherever the target stands.
     * The height is a float and stays one: the beside-the-target point returns it unchanged, and the
     * earlier point adds its offset in float arithmetic, from the target's x and z as floats, so it is
     * the same as before. The height element holds the float's value, so {@code (float)} of it is exact.
     * <p>
     * The three random values are {@code nextFloat()} results, taken in this order: for the
     * beside-the-target point the side, the distance to the side and the position along the line of
     * fire; for the earlier point the x offset, the height and the z offset.
     *
     * @param targetWidth the target's horizontal width, 0 when the target is a block
     * @return the aim point as {x, y, z}
     */
    public static double[] calcMissAimPoint(double tarX, float tarY, double tarZ, double shooterX, double shooterZ,
                                            double targetWidth, float sideRoll, float lateralRoll,
                                            float alongRoll) {
        if (targetWidth > MISS_AIM_MAX_WIDTH) {
            float x = (float) tarX;
            float z = (float) tarZ;
            return new double[]{x - 5F + sideRoll * 10F, tarY + lateralRoll * 5F, z - 5F + alongRoll * 10F};
        }
        // from the target to the shooter; the shooter standing on the target has no side, so take east
        double ux = shooterX - tarX;
        double uz = shooterZ - tarZ;
        double length = Math.sqrt(ux * ux + uz * uz);
        if (length < 0.01D) {
            ux = 1D;
            uz = 0D;
        } else {
            ux /= length;
            uz /= length;
        }
        double side = sideRoll < 0.5F ? -1D : 1D;
        double lateral = 6D + lateralRoll;
        double along = (alongRoll - 0.5D) * 2D;
        // the side is perpendicular to the line of fire: (-uz, ux)
        return new double[]{
                tarX + -uz * side * lateral + ux * along,
                tarY,
                tarZ + ux * side * lateral + uz * along};
    }

    /**
     * Calculate miss rate based on range tiers and ship level.
     * <p>
     * Range < 3: base 25% - level bonus
     * Range < 6: base 25% + 15% distance ratio - level bonus
     * Range > 6: base 25% + 25% distance ratio - level bonus
     * <p>
     * Caps at 50% before nausea effect.
     */
    public static float calcMissRate(IShipAttackBase host, float distance) {
        float miss;
        float attackRange = host.getAttrs().getAttackRange();
        int level = host.getLevel();

        if (attackRange <= 3F) {
            miss = 0.25F - 0.001F * level;
        } else if (attackRange <= 6F) {
            miss = 0.25F + 0.15F * (distance / attackRange) - 0.001F * level;
        } else {
            miss = 0.25F + 0.25F * (distance / attackRange) - 0.001F * level;
        }

        // miss reduction from equips
        miss -= host.getAttrs().getAttrsBuffed(ID.Attrs.MISS);

        // cap at 50%
        if (miss > 0.5F)
            miss = 0.5F;
        if (miss < 0F)
            miss = 0F;

        // apply nausea potion effect (after limit)
        if (host instanceof LivingEntity living) {
            if (living.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION)) {
                miss += 0.4F;
            }
        }

        return miss;
    }

    /**
     * Tweak damage by attacker/defender type and light level.
     * Uses interpolation between day/night tables based on lightCoef.
     * NOTE: original code deliberately swaps table names for interpolation.
     *
     * @param dmg       raw damage
     * @param typeAtk   attacker damage type (1-based)
     * @param typeDef   defender damage type (1-based)
     * @param lightCoef 0=night, 1=day, 0.X=night vision
     * @return modified damage
     */
    public static float modDamageByLight(float dmg, int typeAtk, int typeDef, float lightCoef) {
        if (typeAtk <= 0 || typeDef <= 0)
            return dmg;

        if (lightCoef < 0F)
            lightCoef = 0F;
        else if (lightCoef > 1F)
            lightCoef = 1F;

        // NOTE: deliberately swapped as in original
        float modDay = Values.ModDmgNight[typeAtk - 1][typeDef - 1];
        float modNight = Values.ModDmgDay[typeAtk - 1][typeDef - 1];
        float mod = modNight + (modDay - modNight) * lightCoef;

        return dmg * mod;
    }

    /**
     * Apply damage type modifier based on attacker and target ship types.
     * Uses modDamageByLight with calculated light coefficient.
     */
    public static float applyCombatRateToDamageByType(float damage, Entity attacker, Entity target) {
        int atkType = 0;
        int tgtType = 0;

        if (attacker instanceof IShipAttackBase ship) {
            atkType = ship.getDamageType();
        }
        if (target instanceof IShipAttackBase ship) {
            tgtType = ship.getDamageType();
        }

        if (atkType < 1 || atkType > 7 || tgtType < 1 || tgtType > 7) {
            return damage;
        }

        // calculate light coefficient
        float lightCoef = 1F;
        if (attacker instanceof LivingEntity living) {
            long time = living.level().getDayTime() % 24000;
            // smooth transition: 0 = night, 1 = day
            if (time >= 12500 && time <= 23500) {
                lightCoef = 0F; // night
            }
        }

        return modDamageByLight(damage, atkType, tgtType, lightCoef);
    }

    /**
     * Reduce incoming damage by defender's DEF stat.
     * <p>
     * Formula: finalDmg = rawDmg * (1 - DEF + random(-0.25, +0.25))
     * Original formula includes random variance for less deterministic combat.
     *
     * @param rand   random source
     * @param attrs  defender's attributes
     * @param rawAtk raw incoming damage
     * @return reduced damage
     */
    public static float applyDamageReduceByDEF(RandomSource rand, Attrs attrs, float rawAtk) {
        return rawAtk * (1F - attrs.getDefense() + (rand.nextFloat() * 0.5F - 0.25F));
    }

    /**
     * Overload: reduce incoming damage by entity target's DEF.
     */
    public static float applyDamageReduceByDEF(float damage, Entity target) {
        if (target instanceof IShipAttrs shipTarget) {
            Attrs attrs = shipTarget.getAttrs();
            if (attrs != null && target instanceof LivingEntity living) {
                return applyDamageReduceByDEF(living.getRandom(), attrs, damage);
            }
        }
        return damage;
    }

    /**
     * Calculate whether an attack is dodged.
     * Ported from original canDodge: uses host's DODGE stat, config limit,
     * submarine invisible bonus at distance.
     *
     * @param host   defender (IShipAttrs)
     * @param distSq squared distance from attacker
     * @return true if dodged
     */
    public static boolean canDodge(IShipAttrs host, float distSq) {
        if (!(host instanceof LivingEntity living))
            return false;
        if (living.level().isClientSide())
            return false;

        Attrs attrs = host.getAttrs();
        if (attrs == null)
            return false;

        float dodge = host.shipAttribute(CoreShipAttributes.DODGE);

        // apply config limit
        double limit = ConfigHandler.shipAttributeMaximum(CoreShipAttributes.DODGE);
        if (limit > 0 && dodge > limit) {
            dodge = (float) limit;
        }

        // submarine invisible bonus: add invisible level when distance > 6
        if (host instanceof IShipInvisible invis) {
            if (distSq > 36F) { // 6*6
                dodge += invis.getInvisibleLevel();
            }
            // uncapped bonus at distance > 16
            if (distSq > 256F) { // 16*16
                dodge += 0.5F;
            }
        }

        if (dodge <= 0F)
            return false;

        boolean dodged = living.getRandom().nextFloat() < dodge;

        // spawn dodge particle
        if (dodged) {
            ParticleHelper.spawnAttackTextParticle(living, 4);
        }

        return dodged;
    }

    /**
     * Legacy overload for calcDodge using Entity parameters.
     */
    public static boolean calcDodge(Entity attacker, Entity target) {
        if (target instanceof IShipAttrs shipTarget) {
            float distSq = (float) attacker.distanceToSqr(target);
            return canDodge(shipTarget, distSq);
        }
        return false;
    }

    /**
     * Apply ship-vs-ship damage scaling from config.
     * Only applies when the attacker is a friendly ship/summon/mount (playerUID >
     * 0).
     * The target is always the ship being hurt (BasicEntityShip).
     */
    public static float applyShipVsShipDamage(float damage, Entity attacker, Entity target) {
        // original: attacker must be IShipOwner with playerUID > 0
        // and must be a BasicEntityShip, BasicEntitySummon, or BasicEntityMount
        if (attacker instanceof IShipOwner shipOwner && shipOwner.getPlayerUID() > 0 &&
                (attacker instanceof BasicEntityShip ||
                        attacker instanceof BasicEntitySummon ||
                        attacker instanceof BasicEntityMount)) {
            return damage * ConfigHandler.COMMON.dmgTakenSvS.get() / 100F;
        }

        return damage;
    }

    /**
     * Check if attacker and target are friendly (same team / same owner / ally).
     * Returns true if friendly fire should be blocked.
     */
    public static boolean isFriendlyFire(Entity attacker, Entity target) {
        if (ConfigHandler.friendlyFire())
            return false;

        // friendly ship vs friendly ship - same owner
        if (attacker instanceof BasicEntityShip atkShip && target instanceof BasicEntityShip tgtShip) {
            int atkOwner = atkShip.getPlayerUID();
            int tgtOwner = tgtShip.getPlayerUID();

            if (atkOwner > 0 && atkOwner == tgtOwner) {
                return true;
            }

            // ally team check
            if (TeamHelper.checkIsAlly(atkShip, tgtShip)) {
                return true;
            }
        }

        // hostile vs hostile - same faction
        if (attacker instanceof BasicEntityShipHostile && target instanceof BasicEntityShipHostile) {
            return true;
        }

        // friendly ship should not hurt its owner
        if (attacker instanceof BasicEntityShip atkShip
                && target instanceof net.minecraft.world.entity.player.Player player) {
            return atkShip.getOwner() != null && atkShip.getOwner().equals(player);
        }

        return false;
    }

    /**
     * Cap damage dealt to players. Max 25% of raw damage, absolute max 59 (TNT
     * damage level).
     */
    public static float applyDamageReduceOnPlayer(Entity target, float damage) {
        if (target instanceof net.minecraft.world.entity.player.Player) {
            damage *= 0.25F;
            if (damage > 59F)
                damage = 59F;
        }
        return damage;
    }

    /**
     * Get attack delay based on attack speed and type.
     * type: 0=melee, 1=light, 2=heavy, 3=air-light, 4=air-heavy
     */
    public static int getAttackDelay(float aspd, int type) {
        if (aspd < 0.01F)
            aspd = 0.01F;
        switch (type) {
            case 0:
                return (int) (ConfigHandler.baseAttackSpeed[0] / aspd) + ConfigHandler.fixedAttackDelay[0];
            case 1:
                return (int) (ConfigHandler.baseAttackSpeed[1] / aspd) + ConfigHandler.fixedAttackDelay[1];
            case 2:
                return (int) (ConfigHandler.baseAttackSpeed[2] / aspd) + ConfigHandler.fixedAttackDelay[2];
            case 3:
                return (int) (ConfigHandler.baseAttackSpeed[3] / aspd) + ConfigHandler.fixedAttackDelay[3];
            case 4:
                return (int) (ConfigHandler.baseAttackSpeed[4] / aspd) + ConfigHandler.fixedAttackDelay[4];
        }
        return 40;
    }

    /**
     * Modify damage by addition attributes (AA/ASM) based on target type.
     * Also applies special attack effect multiplier.
     *
     * @param host   attacker (IShipAttrs)
     * @param target target entity
     * @param dmg    base damage
     * @param type   0=normal, 2=nagato special (4x), 3=yamato special (1.5x)
     * @return modified damage
     */
    public static float modDamageByAdditionAttrs(IShipAttrs host, Entity target, float dmg, int type) {
        float newDmg = dmg;
        float modEffect;

        switch (type) {
            case 2:
                modEffect = 4F;
                break; // nagato heavy
            case 3:
                modEffect = 1.5F;
                break; // yamato heavy
            default:
                modEffect = 1F;
                break;
        }

        int targetType = EntityHelper.checkEntityMovingType(target);

        if (targetType == 1) { // air mob
            newDmg = (newDmg + host.getAttrs().getAttrsBuffed(ID.Attrs.AA)) * modEffect;
        } else if (targetType == 2) { // water mob
            newDmg = (newDmg + host.getAttrs().getAttrsBuffed(ID.Attrs.ASM)) * modEffect;
        } else {
            newDmg *= modEffect;
        }

        return newDmg;
    }

    /**
     * Calculate missile move type based on host water depth and target Y.
     * type: 0=melee, 1=light, 2=heavy, 3=air-light, 4=air-heavy
     *
     * @return moveType: 0=underwater, 1=arc, 2=surface
     */
    public static int calcMissileMoveType(IShipAttackBase host, double tarY, int type) {
        int moveType = host.getMissileData(type).movetype;

        // moveType = -1: auto-detect from depth
        if (moveType < 0) {
            double depth = host.getShipDepth(0);

            if (depth > 2D) {
                // in water
                moveType = 0;
            } else if (depth > 0D) {
                // on water surface
                if (tarY <= ((Entity) host).getY() || tarY - ((Entity) host).getY() < depth) {
                    moveType = 2; // target is lower
                } else {
                    moveType = 1; // target is higher
                }
            } else {
                // on solid block
                moveType = 1;
            }
        }

        return moveType;
    }

    /**
     * Calculate missile move type for airplane attacks.
     */
    public static int calcMissileMoveTypeForAirplane(IShipAttackBase host, Entity target, int type) {
        int moveType = host.getMissileData(type).movetype;

        if (moveType < 0) {
            boolean targetLiq = EntityHelper.checkEntityIsInLiquid(target);
            boolean hostLiq = EntityHelper.checkEntityIsInLiquid((Entity) host);
            BlockState blockAtTargetY = target.level().getBlockState(
                    new BlockPos(Mth.floor(((Entity) host).getX()),
                            (int) target.getY(),
                            Mth.floor(((Entity) host).getZ())));
            boolean hostUnderLiq = BlockHelper.checkBlockIsLiquid(blockAtTargetY);

            if (hostLiq) {
                // host in water
                moveType = 0;
            } else if (targetLiq && hostUnderLiq) {
                // on water
                if (target.getY() <= ((Entity) host).getY()) {
                    moveType = 2; // target is lower
                } else {
                    moveType = 0; // target is higher
                }
            } else {
                // on solid block
                moveType = 0;
            }
        }

        return moveType;
    }

    /**
     * Trigger special attack effects from missile type.
     * <p>
     * type:
     * 5: black hole field
     */
    public static void specialAttackEffect(IShipAttackBase host, int type, float[] data) {
        if (host == null || data == null || data.length < 3) {
            return;
        }

        if (type == 5) {
            if (!(host instanceof Entity hostEntity) || hostEntity.level().isClientSide()) {
                return;
            }

            EntityProjectileStatic effect = new EntityProjectileStatic(ModEntities.PROJECTILE_STATIC.get(),
                    hostEntity.level());
            effect.setPos(data[0], data[1], data[2]);

            int life = Mth.floor((float) (20D + host.getLevel() * 0.125D));
            float pullForce = (float) (0.12D + host.getLevel() * 0.00075D);
            float range = (float) (4D + host.getLevel() * 0.035D);
            effect.initEffect(host, 5, pullForce, range, life);

            // 2026/04/07：GitHub Copilotによって確認済み
            hostEntity.level().addFreshEntity(effect);
        }
    }
}
