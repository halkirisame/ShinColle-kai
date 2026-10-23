package com.lulan.shincolle.ai.domain.combat;

import java.util.Objects;
import java.util.Optional;

/** Immutable class-specific damage and post-equipment missile rules, with no world dependencies. */
public record ShipClassAttackProfile(ShipClassAttackKind kind) {
    public ShipClassAttackProfile {
        Objects.requireNonNull(kind, "kind");
    }

    /** The existing attack indices are decoded only by the Minecraft adapter. */
    public enum Channel {
        MELEE, LIGHT, HEAVY, LIGHT_AIRCRAFT, HEAVY_AIRCRAFT
    }

    /** A detached copy of the equipment calculation's missile values. */
    public record MissileValues(int type, int moveType, float velocity, float acceleration1, float acceleration2) { }

    public enum LightAttack { DIRECT, EQUIPMENT_MISSILE, FIXED_TORPEDO }

    public record MissileLaunch(int count, int lifetime, float knockback, float addHeight, ProjectileTrailDelay trailDelay) { }

    public LightAttack lightAttack() {
        return switch (this.kind) {
            case SUBMARINE_YO, SUBMARINE_RO500, SUBMARINE_U511, SUBMARINE_HIME, SSNH -> LightAttack.EQUIPMENT_MISSILE;
            case HOSTILE_SUBMARINE_U511, HOSTILE_SUBMARINE_RO500 -> LightAttack.FIXED_TORPEDO;
            default -> LightAttack.DIRECT;
        };
    }

    public Optional<MissileLaunch> missileLaunch(Channel channel) {
        if (channel == Channel.MELEE) {
            return this.kind == ShipClassAttackKind.CARRIER_HIME
                    ? Optional.of(new MissileLaunch(1, 100, 0F, 0F, ProjectileTrailDelay.NONE)) : Optional.empty();
        }
        if (channel != Channel.LIGHT && channel != Channel.HEAVY) return Optional.empty();
        if (this.kind == ShipClassAttackKind.SUBMARINE_HIME || this.kind == ShipClassAttackKind.SSNH) {
            return Optional.of(new MissileLaunch(2, 160, .15F, .25F, ProjectileTrailDelay.FOUR_TICKS));
        }
        if (channel != Channel.LIGHT || lightAttack() == LightAttack.DIRECT) return Optional.empty();
        return Optional.of(new MissileLaunch(1, lightAttack() == LightAttack.FIXED_TORPEDO ? 160 : 140,
                .15F, .25F, ProjectileTrailDelay.NONE));
    }

    public MissileValues hostileLightMissile() {
        return new MissileValues(1, -1, .8F, 1.1F, 1.1F);
    }

    public float multiplier(Channel channel) {
        return switch (channel) {
            case MELEE -> switch (this.kind) {
                case KIRISHIMA -> 1.5F;
                case BATTLESHIP_HIME -> 3F;
                case CARRIER_HIME, RU -> 2F;
                case ATAGO, SUBMARINE_YO, SUBMARINE_RO500, SUBMARINE_U511, SUBMARINE_HIME,
                        HOSTILE_SUBMARINE_U511, HOSTILE_SUBMARINE_RO500 -> 0.125F;
                default -> 1F;
            };
            case HEAVY -> this.kind == ShipClassAttackKind.RU ? .2F : this.kind == ShipClassAttackKind.NORTHERN_HIME
                    || this.kind == ShipClassAttackKind.HARBOUR_HIME ? 0.75F : 1F;
            default -> 1F;
        };
    }

    public boolean heavyCannonAllowed() {
        return this.kind != ShipClassAttackKind.CARRIER_WATER_DEMON;
    }

    /** Equipment-specified types win; carrier melee minima apply only to channel zero. */
    public MissileValues finalizeMissile(Channel channel, MissileValues source, boolean married, boolean ringEffect) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(source, "source");
        if (channel == Channel.LIGHT && lightAttack() == LightAttack.EQUIPMENT_MISSILE) {
            float velocity = this.kind == ShipClassAttackKind.SUBMARINE_RO500 ? .5F
                    : this.kind == ShipClassAttackKind.SUBMARINE_U511 ? .4F : .3F;
            float acceleration = this.kind == ShipClassAttackKind.SUBMARINE_RO500 ? .08F
                    : this.kind == ShipClassAttackKind.SUBMARINE_U511 ? .07F : .06F;
            return new MissileValues(source.type(), source.moveType(), source.velocity() + velocity,
                    source.acceleration1() + acceleration, source.acceleration2() + acceleration);
        }
        if (this.kind == ShipClassAttackKind.CARRIER_HIME && channel == Channel.MELEE) {
            return new MissileValues(source.type(), source.moveType(), Math.max(0.8F, source.velocity()),
                    Math.max(1.1F, source.acceleration1()), Math.max(1.1F, source.acceleration2()));
        }
        if (channel != Channel.HEAVY || source.type() != 0) return source;
        return switch (this.kind) {
            case BATTLESHIP_HIME -> new MissileValues(3, 1, source.velocity(), source.acceleration1(), source.acceleration2());
            case HARBOUR_HIME -> new MissileValues(2, 0, 3F, 0F, 0F);
            case ISOLATED_HIME -> gravity(source);
            case ATAGO -> married && ringEffect ? gravity(source) : source;
            default -> source;
        };
    }

    private static MissileValues gravity(MissileValues source) {
        return new MissileValues(5, source.moveType(), source.velocity(), source.acceleration1(), source.acceleration2());
    }
}
