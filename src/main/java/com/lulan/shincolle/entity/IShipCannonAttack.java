package com.lulan.shincolle.entity;

import net.minecraft.world.entity.Entity;

public interface IShipCannonAttack extends IShipAttackBase {
    boolean attackEntityWithAmmo(Entity target);

    boolean attackEntityWithHeavyAmmo(Entity target);

    /** Scale of the wait after a heavy attack call; read after the call returns. */
    default float heavyDelayScale() {
        return 1F;
    }

    /** True while the light cannons must wait, e.g. a charged beam that has not yet been fired. */
    default boolean holdsLightFire() {
        return false;
    }

    boolean useAmmoLight();

    boolean useAmmoHeavy();
}
