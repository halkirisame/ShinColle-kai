package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile.Channel;
import com.lulan.shincolle.ai.domain.combat.ShipClassAttackProfile.MissileValues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipClassAttackProfileTest {
    private static final MissileValues DEFAULT = new MissileValues(0, -1, 0.5F, 1.04F, 1.04F);

    @Test
    void approvedProfilesMatchUpstreamValues() {
        for (ShipClassAttackKind kind : ShipClassAttackKind.values()) {
            ShipClassAttackProfile profile = new ShipClassAttackProfile(kind);
            float melee = switch (kind) {
                case KIRISHIMA -> 1.5F;
                case BATTLESHIP_HIME -> 3F;
                case CARRIER_HIME, RU -> 2F;
                case ATAGO, SUBMARINE_YO, SUBMARINE_RO500, SUBMARINE_U511, SUBMARINE_HIME,
                        HOSTILE_SUBMARINE_U511, HOSTILE_SUBMARINE_RO500 -> 0.125F;
                default -> 1F;
            };
            assertEquals(melee, profile.multiplier(Channel.MELEE), kind.name());
            assertEquals(kind == ShipClassAttackKind.RU ? .2F
                            : kind == ShipClassAttackKind.HARBOUR_HIME || kind == ShipClassAttackKind.NORTHERN_HIME ? 0.75F : 1F,
                    profile.multiplier(Channel.HEAVY), kind.name());
            for (Channel channel : new Channel[]{Channel.LIGHT, Channel.LIGHT_AIRCRAFT, Channel.HEAVY_AIRCRAFT}) {
                assertEquals(1F, profile.multiplier(channel), kind + " " + channel);
            }
            assertEquals(kind != ShipClassAttackKind.CARRIER_WATER_DEMON, profile.heavyCannonAllowed());
        }
    }

    @Test
    void equipmentFinalizationRespectsChannelAndCondition() {
        for (ShipClassAttackKind kind : ShipClassAttackKind.values()) {
            ShipClassAttackProfile profile = new ShipClassAttackProfile(kind);
            for (Channel channel : Channel.values()) {
                for (boolean married : new boolean[]{false, true}) {
                    for (boolean ring : new boolean[]{false, true}) {
                        MissileValues result = profile.finalizeMissile(channel, DEFAULT, married, ring);
                        MissileValues expected = DEFAULT;
                        if (channel == Channel.HEAVY) {
                            expected = switch (kind) {
                                case BATTLESHIP_HIME -> new MissileValues(3, 1, 0.5F, 1.04F, 1.04F);
                                case HARBOUR_HIME -> new MissileValues(2, 0, 3F, 0F, 0F);
                                case ISOLATED_HIME -> new MissileValues(5, -1, 0.5F, 1.04F, 1.04F);
                                case ATAGO -> married && ring ? new MissileValues(5, -1, 0.5F, 1.04F, 1.04F) : DEFAULT;
                                default -> DEFAULT;
                            };
                        }
                        if (kind == ShipClassAttackKind.CARRIER_HIME && channel == Channel.MELEE) {
                            expected = new MissileValues(0, -1, 0.8F, 1.1F, 1.1F);
                        }
                        boolean additive = channel == Channel.LIGHT
                                && profile.lightAttack() == ShipClassAttackProfile.LightAttack.EQUIPMENT_MISSILE;
                        if (additive) {
                            float speed = kind == ShipClassAttackKind.SUBMARINE_RO500 ? .5F
                                    : kind == ShipClassAttackKind.SUBMARINE_U511 ? .4F : .3F;
                            float acceleration = kind == ShipClassAttackKind.SUBMARINE_RO500 ? .08F
                                    : kind == ShipClassAttackKind.SUBMARINE_U511 ? .07F : .06F;
                            expected = new MissileValues(0, -1, .5F + speed, 1.04F + acceleration, 1.04F + acceleration);
                        }
                        assertEquals(expected, result, kind + " " + channel + " " + married + " " + ring);
                        // Additive hull finalization is applied once after each equipment reset, not to its own output.
                        if (!additive) assertEquals(result, profile.finalizeMissile(channel, result, married, ring));
                        MissileValues specified = new MissileValues(4, 3, 2F, 1.4F, 1.6F);
                        MissileValues finalSpecified = profile.finalizeMissile(channel, specified, married, ring);
                        if (additive) {
                            assertEquals(4, finalSpecified.type());
                            assertEquals(3, finalSpecified.moveType());
                            assertEquals(2F + result.velocity() - DEFAULT.velocity(), finalSpecified.velocity(), .00001F);
                            assertEquals(1.4F + result.acceleration1() - DEFAULT.acceleration1(), finalSpecified.acceleration1(), .00001F);
                            assertEquals(1.6F + result.acceleration2() - DEFAULT.acceleration2(), finalSpecified.acceleration2(), .00001F);
                        } else assertEquals(specified, finalSpecified);
                    }
                }
            }
        }
        assertEquals(new MissileValues(0, -1, 0.5F, 1.04F, 1.04F), DEFAULT, "Input must remain immutable");
    }

    @Test
    void carrierMinimaPreserveHigherEquipmentValuesIndependently() {
        ShipClassAttackProfile carrier = new ShipClassAttackProfile(ShipClassAttackKind.CARRIER_HIME);
        MissileValues source = new MissileValues(5, 3, 2F, 0.7F, 1.6F);
        assertEquals(new MissileValues(5, 3, 2F, 1.1F, 1.6F), carrier.finalizeMissile(Channel.MELEE, source, false, false));
        assertSame(source, carrier.finalizeMissile(Channel.LIGHT, source, false, false));
        assertTrue(carrier.heavyCannonAllowed());
        assertFalse(new ShipClassAttackProfile(ShipClassAttackKind.CARRIER_WATER_DEMON).heavyCannonAllowed());
    }

    @Test
    void submarineLaunchProfilesKeepLightAndHeavyContractsSeparate() {
        for (ShipClassAttackKind kind : ShipClassAttackKind.values()) {
            ShipClassAttackProfile profile = new ShipClassAttackProfile(kind);
            boolean dual = kind == ShipClassAttackKind.SUBMARINE_HIME || kind == ShipClassAttackKind.SSNH;
            boolean hostile = kind == ShipClassAttackKind.HOSTILE_SUBMARINE_U511 || kind == ShipClassAttackKind.HOSTILE_SUBMARINE_RO500;
            boolean light = dual || hostile || kind == ShipClassAttackKind.SUBMARINE_YO
                    || kind == ShipClassAttackKind.SUBMARINE_U511 || kind == ShipClassAttackKind.SUBMARINE_RO500;
            assertEquals(light, profile.missileLaunch(Channel.LIGHT).isPresent());
            assertEquals(dual, profile.missileLaunch(Channel.HEAVY).isPresent());
            boolean carrier = kind == ShipClassAttackKind.CARRIER_HIME;
            assertEquals(carrier, profile.missileLaunch(Channel.MELEE).isPresent());
            if (carrier) {
                var melee = profile.missileLaunch(Channel.MELEE).orElseThrow();
                assertEquals(1, melee.count());
                assertEquals(100, melee.lifetime());
                assertEquals(0F, melee.knockback());
                assertEquals(0F, melee.addHeight());
                assertEquals(0, melee.trailDelay().remainingTicks());
            }
            assertTrue(profile.missileLaunch(Channel.LIGHT_AIRCRAFT).isEmpty());
            assertTrue(profile.missileLaunch(Channel.HEAVY_AIRCRAFT).isEmpty());
            if (light) {
                var launch = profile.missileLaunch(Channel.LIGHT).orElseThrow();
                assertEquals(dual ? 2 : 1, launch.count());
                assertEquals(dual || hostile ? 160 : 140, launch.lifetime());
                assertEquals(.15F, launch.knockback());
                assertEquals(.25F, launch.addHeight());
                assertEquals(dual ? 4 : 0, launch.trailDelay().remainingTicks());
            }
            if (hostile) assertEquals(new MissileValues(1, -1, .8F, 1.1F, 1.1F), profile.hostileLightMissile());
        }
    }
}
