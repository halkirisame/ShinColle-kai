package com.lulan.shincolle.network;

/** Disjoint payload contracts for position, entity and custom particle events. */
public enum ParticlePacketRoute {
    POSITION, HEAL, EMOTION, SKILL, ORDER_REJECTED, FLUID_FILL, CRANE_TRANSFER,
    EQUIPMENT_SMOKE, PURPLE_BEAM, DUAL_BEAM, MOUNT_CANNON_SMOKE, REPAIR_GODDESS,
    YAMATO_CHARGE, YAMATO_BEAM, SUPPORT_LINE, INVALID;

    public static final byte ORDER_REJECTED_TYPE = 61;
    public static final byte FLUID_FILL_TYPE = 62;
    public static final byte CRANE_TRANSFER_TYPE = 63;
    public static final byte EQUIPMENT_SMOKE_TYPE = 64;
    public static final byte PURPLE_BEAM_TYPE = 65;
    public static final byte DUAL_BEAM_TYPE = 66;
    public static final byte MOUNT_CANNON_SMOKE_TYPE = 67;
    public static final byte REPAIR_GODDESS_TYPE = 68;
    public static final byte YAMATO_CHARGE_TYPE = 69;
    public static final byte YAMATO_BEAM_TYPE = 70;
    public static final byte YAMATO_BOSS_BEAM_TYPE = 71;
    public static final byte SUPPORT_LINE_TYPE = 72;

    public static ParticlePacketRoute resolve(int type, int payloadLength) {
        if (type == 23 && payloadLength == 0) return HEAL;
        if (type == 36 && payloadLength == 4) return EMOTION;
        if (type == 60) return payloadLength == 49 ? SKILL : INVALID;
        if (type == ORDER_REJECTED_TYPE) {
            return payloadLength == 24 || payloadLength == 48 ? ORDER_REJECTED : INVALID;
        }
        if (type == FLUID_FILL_TYPE) return payloadLength == 24 ? FLUID_FILL : INVALID;
        if (type == CRANE_TRANSFER_TYPE) return payloadLength == 24 ? CRANE_TRANSFER : INVALID;
        if (type == EQUIPMENT_SMOKE_TYPE) return payloadLength == 24 ? EQUIPMENT_SMOKE : INVALID;
        if (type == PURPLE_BEAM_TYPE) return payloadLength == 28 ? PURPLE_BEAM : INVALID;
        if (type == DUAL_BEAM_TYPE) return payloadLength == 28 ? DUAL_BEAM : INVALID;
        if (type == MOUNT_CANNON_SMOKE_TYPE) return payloadLength == 24 ? MOUNT_CANNON_SMOKE : INVALID;
        if (type == REPAIR_GODDESS_TYPE) return payloadLength == 0 ? REPAIR_GODDESS : INVALID;
        if (type == YAMATO_CHARGE_TYPE) return payloadLength == 24 ? YAMATO_CHARGE : INVALID;
        if (type == YAMATO_BEAM_TYPE || type == YAMATO_BOSS_BEAM_TYPE) {
            return payloadLength == 28 ? YAMATO_BEAM : INVALID;
        }
        if (type == SUPPORT_LINE_TYPE) return payloadLength == 28 ? SUPPORT_LINE : INVALID;
        if (type >= 1 && type <= 49 && (payloadLength == 0 || payloadLength == 24 || payloadLength == 48)) {
            return POSITION;
        }
        return INVALID;
    }

    /** Combat result codes and position-effect codes have different meanings. */
    public static int attackTextType(int result) {
        return switch (result) {
            case 0, 1, 2, 3 -> 10 + result;
            case 4 -> 34;
            default -> 0;
        };
    }

    public static int ordinaryMuzzleType(int attackType, boolean snow) {
        return attackType == 1 ? (snow ? 31 : 6) : 0;
    }

    public static int ordinaryHitType(int attackType, boolean snow) {
        return switch (attackType) {
            case 0 -> 1;
            case 1 -> snow ? 30 : 9;
            default -> 0;
        };
    }
}
