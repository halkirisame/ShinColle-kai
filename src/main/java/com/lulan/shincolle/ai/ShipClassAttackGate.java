package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.ShipClassAttackKind;
import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile;
import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile.Channel;
import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile.MissileValues;
import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile.LightAttack;
import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile.MissileLaunch;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.battleship.EntityBBKirishima;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRu;
import com.lulan.shincolle.entity.cruiser.EntityCAAtago;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerIkazuchi;
import com.lulan.shincolle.entity.hime.EntityAirfieldHime;
import com.lulan.shincolle.entity.hime.EntityBattleshipHime;
import com.lulan.shincolle.entity.hime.EntityCarrierHime;
import com.lulan.shincolle.entity.hime.EntityCarrierWD;
import com.lulan.shincolle.entity.hime.EntityDestroyerHime;
import com.lulan.shincolle.entity.hime.EntityHarbourHime;
import com.lulan.shincolle.entity.hime.EntityIsolatedHime;
import com.lulan.shincolle.entity.hime.EntityMidwayHime;
import com.lulan.shincolle.entity.hime.EntityNorthernHime;
import com.lulan.shincolle.entity.hime.EntitySubmHime;
import com.lulan.shincolle.entity.hime.EntitySSNH;
import com.lulan.shincolle.entity.submarine.EntitySubmYo;
import com.lulan.shincolle.entity.submarine.EntitySubmRo500;
import com.lulan.shincolle.entity.submarine.EntitySubmU511;
import com.lulan.shincolle.entity.submarine.EntitySubmRo500Mob;
import com.lulan.shincolle.entity.submarine.EntitySubmU511Mob;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.MissileData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Maps built-in hulls to pure rules and applies only their derived values. */
public final class ShipClassAttackGate {
    private static final Map<ShipClassAttackKind, ShipClassAttackProfile> PROFILES = profiles();

    private ShipClassAttackGate() { }

    private static Map<ShipClassAttackKind, ShipClassAttackProfile> profiles() {
        Map<ShipClassAttackKind, ShipClassAttackProfile> profiles = new EnumMap<>(ShipClassAttackKind.class);
        for (ShipClassAttackKind kind : ShipClassAttackKind.values()) profiles.put(kind, new ShipClassAttackProfile(kind));
        return Map.copyOf(profiles);
    }

    private static Optional<ShipClassAttackProfile> profile(Entity host) {
        if (host instanceof BasicEntityMount mount) host = mount.getHost();
        ShipClassAttackKind kind;
        if (host instanceof EntityBBKirishima) kind = ShipClassAttackKind.KIRISHIMA;
        else if (host instanceof EntityBattleshipRu) kind = ShipClassAttackKind.RU;
        else if (host instanceof EntityDestroyerIkazuchi) kind = ShipClassAttackKind.IKAZUCHI;
        else if (host instanceof EntityBattleshipHime) kind = ShipClassAttackKind.BATTLESHIP_HIME;
        else if (host instanceof EntityCarrierHime) kind = ShipClassAttackKind.CARRIER_HIME;
        else if (host instanceof EntityCarrierWD) kind = ShipClassAttackKind.CARRIER_WATER_DEMON;
        else if (host instanceof EntityNorthernHime) kind = ShipClassAttackKind.NORTHERN_HIME;
        else if (host instanceof EntityHarbourHime) kind = ShipClassAttackKind.HARBOUR_HIME;
        else if (host instanceof EntityIsolatedHime) kind = ShipClassAttackKind.ISOLATED_HIME;
        else if (host instanceof EntityAirfieldHime) kind = ShipClassAttackKind.AIRFIELD_HIME;
        else if (host instanceof EntityDestroyerHime) kind = ShipClassAttackKind.DESTROYER_HIME;
        else if (host instanceof EntityMidwayHime) kind = ShipClassAttackKind.MIDWAY_HIME;
        else if (host instanceof EntityCAAtago) kind = ShipClassAttackKind.ATAGO;
        else if (host instanceof EntitySubmYo) kind = ShipClassAttackKind.SUBMARINE_YO;
        else if (host instanceof EntitySubmRo500) kind = ShipClassAttackKind.SUBMARINE_RO500;
        else if (host instanceof EntitySubmU511) kind = ShipClassAttackKind.SUBMARINE_U511;
        else if (host instanceof EntitySubmHime) kind = ShipClassAttackKind.SUBMARINE_HIME;
        else if (host instanceof EntitySSNH) kind = ShipClassAttackKind.SSNH;
        else if (host instanceof EntitySubmU511Mob) kind = ShipClassAttackKind.HOSTILE_SUBMARINE_U511;
        else if (host instanceof EntitySubmRo500Mob) kind = ShipClassAttackKind.HOSTILE_SUBMARINE_RO500;
        else return Optional.empty();
        return Optional.of(PROFILES.get(kind));
    }

    private static Optional<Channel> channel(int attackType) {
        return switch (attackType) {
            case 0 -> Optional.of(Channel.MELEE);
            case 1 -> Optional.of(Channel.LIGHT);
            case 2 -> Optional.of(Channel.HEAVY);
            case 3 -> Optional.of(Channel.LIGHT_AIRCRAFT);
            case 4 -> Optional.of(Channel.HEAVY_AIRCRAFT);
            default -> Optional.empty();
        };
    }

    /** Called after the existing skill profile; only restored submarine light attacks bypass AA/ASM here. */
    public static Optional<Float> baseDamage(Mob host, int attackType) {
        if (attackType == 1 && profile(host).map(p -> p.lightAttack() == LightAttack.EQUIPMENT_MISSILE).orElse(false)) {
            return Optional.of(((IShipAttackBase) host).getAttrs().getAttackDamage());
        }
        return channel(attackType).filter(c -> c == Channel.MELEE || c == Channel.HEAVY).flatMap(c ->
                profile(host).map(p -> {
                    IShipAttackBase ship = (IShipAttackBase) host;
                    float damage = c == Channel.MELEE ? ship.getAttrs().getAttackDamage() : ship.getAttrs().getAttackDamageHeavy();
                    return damage * p.multiplier(c);
                }));
    }

    public static boolean heavyCannonAllowed(Entity host) {
        return profile(host).map(ShipClassAttackProfile::heavyCannonAllowed).orElse(true);
    }

    /** Already-accounted light attacks select a projectile before the direct-damage rate pipeline. */
    public static boolean lightMissile(Mob host, Entity target) {
        Optional<ShipClassAttackProfile> selected = profile(host).filter(p -> p.lightAttack() != LightAttack.DIRECT);
        if (selected.isEmpty()) return false;
        ShipClassAttackProfile profile = selected.get();
        IShipAttackBase ship = (IShipAttackBase) host;
        if (host instanceof BasicEntityShip friendly) {
            friendly.applySoundAtAttacker(2, target);
            friendly.applyParticleAtAttacker(2, target, target);
        } else if (host instanceof BasicEntityShipHostile hostile) {
            hostile.applySoundAtAttacker(2, target);
            hostile.applyParticleAtAttacker(2, target, target);
        }
        double tarX = target.getX();
        float tarY = (float) target.getY();
        double tarZ = target.getZ();
        // Hostile auto movement is selected from the original target height, before miss displacement.
        int hostileMove = CombatHelper.calcMissileMoveType(ship, target.getY(), 1);
        float distance = (float) Math.sqrt(host.distanceToSqr(target));
        if (host.getRandom().nextFloat() <= CombatHelper.calcMissRate(ship, distance)) {
            double[] aim = CombatHelper.calcMissAimPoint(tarX, tarY, tarZ, host.getX(), host.getZ(),
                    target.getBbWidth(), host.getRandom().nextFloat(), host.getRandom().nextFloat(), host.getRandom().nextFloat());
            tarX = aim[0];
            tarY = (float) aim[1];
            tarZ = aim[2];
            ParticleHelper.spawnAttackTextParticle(host, 0);
        }
        float attack = ship.getAttrs().getAttackDamage();
        if (host instanceof BasicEntityShip friendly) {
            friendly.summonMissile(1, attack, tarX, tarY, tarZ, 1F);
        } else {
            MissileValues fixed = profile.hostileLightMissile();
            MissileLaunch launch = profile.missileLaunch(Channel.LIGHT).orElseThrow();
            float launchY = (float) host.getY() + host.getBbHeight() * (hostileMove == 0 ? .3F : .5F);
            EntityAbyssMissile missile = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), host.level());
            missile.initMissile(ship, fixed.type(), hostileMove, attack, launch.knockback(), launchY,
                    tarX, tarY + target.getBbHeight() * .1F, tarZ, launch.lifetime(), launch.addHeight(),
                    fixed.velocity(), fixed.acceleration1(), fixed.acceleration2());
            host.level().addFreshEntity(missile);
        }
        return true;
    }

    /** Only the two built-in dual launchers override the ordinary shared missile constructor. */
    public static boolean dualMissiles(BasicEntityShip ship, int attackType, float attack,
                                        double tarX, float tarY, double tarZ, float targetHeight) {
        Optional<MissileLaunch> launch = channel(attackType).flatMap(c -> profile(ship).flatMap(p -> p.missileLaunch(c)))
                .filter(p -> p.count() == 2);
        if (launch.isEmpty()) return false;
        MissileLaunch pattern = launch.get();
        MissileData data = ship.getMissileData(attackType);
        int move = CombatHelper.calcMissileMoveType(ship, tarY, attackType);
        float launchY = (float) ship.getY() + ship.getBbHeight() * .6F;
        for (float side : new float[]{1F, -1F}) {
            float[] offset = CalcHelper.rotateXZByAxis(0F, side, (ship.yBodyRot % 360F) * ((float) Math.PI / 180F), 1F);
            Vec3 origin = new Vec3(ship.getX() + offset[1], launchY, ship.getZ() + offset[0]);
            EntityAbyssMissile missile = EntityAbyssMissile.createBuiltInLaunch(ship.level(), ship, data.type, move,
                    attack, pattern.knockback(), origin, tarX, tarY + targetHeight * .1F, tarZ,
                    pattern.lifetime(), pattern.addHeight(), data.vel0, data.accY1, data.accY2, pattern.trailDelay());
            ship.level().addFreshEntity(missile);
        }
        return true;
    }

    /** A natural or direct melee entry launches the channel-zero missile instead of touching the target. */
    public static boolean carrierMelee(BasicEntityShip ship, Entity target, float attack) {
        Optional<MissileLaunch> launch = profile(ship).flatMap(p -> p.missileLaunch(Channel.MELEE));
        if (launch.isEmpty() || ship.level().isClientSide()) return false;
        MissileLaunch pattern = launch.get();
        MissileData data = ship.getMissileData(0);
        ship.applySoundAtAttacker(0, target);
        ship.applyParticleAtAttacker(0, target, target);
        Vec3 origin = new Vec3(ship.getX(), ship.getY() + 1.3D, ship.getZ());
        EntityAbyssMissile missile = EntityAbyssMissile.createBuiltInLaunch(ship.level(), ship, data.type, 0,
                attack,
                pattern.knockback(), origin, target.getX(), (float) target.getY() + target.getBbHeight() * .2F,
                target.getZ(), pattern.lifetime(), pattern.addHeight(), data.vel0, data.accY1, data.accY2,
                pattern.trailDelay());
        ship.level().addFreshEntity(missile);
        return true;
    }

    /** Shared end of both saved-data formats, including direct death-egg restoration. */
    public static void restoreCapabilities(BasicEntityShip ship) {
        if (!heavyCannonAllowed(ship)) {
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
        }
    }

    /** Thin hull overrides call this once, after their entire super equipment calculation. */
    public static void finalizeEquipment(BasicEntityShip ship) {
        profile(ship).ifPresent(p -> {
            for (int index = 0; index < 5; index++) {
                MissileData data = ship.getMissileData(index);
                MissileValues source = new MissileValues(data.type, data.movetype, data.vel0, data.accY1, data.accY2);
                MissileValues result = p.finalizeMissile(channel(index).orElseThrow(), source,
                        ship.getStateFlag(ID.F.IsMarried), ship.getStateFlag(ID.F.UseRingEffect));
                data.type = result.type();
                data.movetype = result.moveType();
                data.vel0 = result.velocity();
                data.accY1 = result.acceleration1();
                data.accY2 = result.acceleration2();
            }
        });
    }
}
