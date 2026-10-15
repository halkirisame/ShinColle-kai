package com.lulan.shincolle.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticlePacketRouteTest {
    @Test
    void supportLineRequiresAnEntityTargetPayloadAndCannotBecomePositionFour() {
        assertEquals(ParticlePacketRoute.SUPPORT_LINE,
                ParticlePacketRoute.resolve(ParticlePacketRoute.SUPPORT_LINE_TYPE, 28));
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(4, 48));
        for (int length : new int[]{0, 4, 24, 27, 29, 48, 49}) {
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.SUPPORT_LINE_TYPE, length));
        }
    }

    @Test
    void redRejectionRejectsIncompleteAndUnrelatedPayloads() {
        for (int length : new int[]{0, 4, 23, 25, 28, 47, 49}) {
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.ORDER_REJECTED_TYPE, length));
        }
    }

    @Test
    void mountCannonSmokeUsesExactlyThreeEntityParametersAndCannotBecomeMissText() {
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(10, 24));
        assertEquals(ParticlePacketRoute.MOUNT_CANNON_SMOKE,
                ParticlePacketRoute.resolve(ParticlePacketRoute.MOUNT_CANNON_SMOKE_TYPE, 24));
        for (int length : new int[]{0, 4, 23, 25, 28, 48, 49}) {
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.MOUNT_CANNON_SMOKE_TYPE, length));
        }
    }

    @Test
    void repairGoddessAndYamatoEffectsHaveTheirOwnExactPayloads() {
        assertEquals(ParticlePacketRoute.REPAIR_GODDESS,
                ParticlePacketRoute.resolve(ParticlePacketRoute.REPAIR_GODDESS_TYPE, 0));
        assertEquals(ParticlePacketRoute.YAMATO_CHARGE,
                ParticlePacketRoute.resolve(ParticlePacketRoute.YAMATO_CHARGE_TYPE, 24));
        assertEquals(ParticlePacketRoute.YAMATO_BEAM,
                ParticlePacketRoute.resolve(ParticlePacketRoute.YAMATO_BEAM_TYPE, 28));
        assertEquals(ParticlePacketRoute.YAMATO_BEAM,
                ParticlePacketRoute.resolve(ParticlePacketRoute.YAMATO_BOSS_BEAM_TYPE, 28));
        for (int length : new int[]{4, 23, 24, 25, 48, 49}) {
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.REPAIR_GODDESS_TYPE, length));
        }
        for (int length : new int[]{0, 28, 48}) {
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.YAMATO_CHARGE_TYPE, length));
        }
        for (int length : new int[]{0, 24, 48}) {
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.YAMATO_BEAM_TYPE, length));
            assertEquals(ParticlePacketRoute.INVALID,
                    ParticlePacketRoute.resolve(ParticlePacketRoute.YAMATO_BOSS_BEAM_TYPE, length));
        }
    }

    @Test
    void fistAtPositionCannotBecomeHealing() {
        assertEquals(ParticlePacketRoute.HEAL, ParticlePacketRoute.resolve(23, 0));
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(23, 48));
        assertEquals(ParticlePacketRoute.INVALID, ParticlePacketRoute.resolve(23, 49));
    }

    @Test
    void fluidAndCraneUseEntitySpaceInsteadOfFistSpace() {
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(21, 48));
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(22, 48));
        assertEquals(ParticlePacketRoute.FLUID_FILL, ParticlePacketRoute.resolve(62, 24));
        assertEquals(ParticlePacketRoute.CRANE_TRANSFER, ParticlePacketRoute.resolve(63, 24));
        assertEquals(ParticlePacketRoute.INVALID, ParticlePacketRoute.resolve(62, 48));
    }

    @Test
    void redRejectionCannotBecomeSnowAndSkillsCannotBecomePositionEffects() {
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(30, 48));
        assertEquals(ParticlePacketRoute.ORDER_REJECTED, ParticlePacketRoute.resolve(61, 48));
        assertEquals(ParticlePacketRoute.SKILL, ParticlePacketRoute.resolve(60, 49));
        assertEquals(ParticlePacketRoute.INVALID, ParticlePacketRoute.resolve(60, 48));
        assertEquals(ParticlePacketRoute.EMOTION, ParticlePacketRoute.resolve(36, 4));
        assertEquals(ParticlePacketRoute.POSITION, ParticlePacketRoute.resolve(36, 48));
    }

    @Test
    void heavyAndAircraftAttacksOnlyAnimateWhileLightAndMeleeHaveSeparateHitEffects() {
        for (int type : new int[]{0, 2, 3, 4}) {
            assertEquals(0, ParticlePacketRoute.ordinaryMuzzleType(type, false));
        }
        for (int type : new int[]{2, 3, 4}) {
            assertEquals(0, ParticlePacketRoute.ordinaryHitType(type, false));
        }
        assertEquals(6, ParticlePacketRoute.ordinaryMuzzleType(1, false));
        assertEquals(9, ParticlePacketRoute.ordinaryHitType(1, false));
        assertEquals(1, ParticlePacketRoute.ordinaryHitType(0, false));
        assertEquals(31, ParticlePacketRoute.ordinaryMuzzleType(1, true));
        assertEquals(30, ParticlePacketRoute.ordinaryHitType(1, true));
        assertEquals(34, ParticlePacketRoute.attackTextType(4));
        assertEquals(0, ParticlePacketRoute.attackTextType(-1));
    }
}
