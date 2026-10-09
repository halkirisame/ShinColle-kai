package com.lulan.shincolle.ai.domain.combat;

/**
 * The host's weapon flags and stores, as the attack goals read them.
 *
 * @param cannonsEnabled   light (type, use flag, ammo) or heavy (the same) may fire; the range goal's start condition
 * @param useLight         the light cannon's use flag, checked again when it fires
 * @param hasAmmoLight     light ammunition left
 * @param useHeavy         the heavy cannon's use flag
 * @param hasAmmoHeavy     heavy ammunition left
 * @param aircraftEnabled  light (type, use flag, ammo, aircraft) or heavy (the same) may launch; the carrier goal's start condition
 * @param useAirLight      light aircraft are in use
 * @param useAirHeavy      heavy aircraft are in use
 * @param airLightStocked  light ammunition and a light aircraft are left
 * @param airHeavyStocked  heavy ammunition and a heavy aircraft are left
 * @param crane            the crane is loading
 * @param meleeEnabled     the melee flag
 * @param passenger        the host rides anything
 */
public record WeaponReadiness(boolean cannonsEnabled, boolean useLight, boolean hasAmmoLight,
                              boolean useHeavy, boolean hasAmmoHeavy,
                              boolean aircraftEnabled, boolean useAirLight, boolean useAirHeavy,
                              boolean airLightStocked, boolean airHeavyStocked,
                              boolean crane, boolean meleeEnabled, boolean passenger) {
}
