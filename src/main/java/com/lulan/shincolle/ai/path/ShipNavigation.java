package com.lulan.shincolle.ai.path;

import com.lulan.shincolle.entity.IShipNavigator;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * Navigation factory for ship entities.
 *
 * <p>Which navigation an entity needs depends on how it actually moves:
 * aircraft path freely through the air, while ship girls move across the water
 * surface and onto land. Handing a surface mover a flying navigation produces
 * paths through open air that it can never follow, which looks in game exactly
 * like the AI doing nothing at all.
 */
public final class ShipNavigation {

    /** Paths visited per node, raised so ships can route around large obstacles. */
    private static final float VISITED_NODES_MULTIPLIER = 4.0F;

    private ShipNavigation() {
    }

    /**
     * Builds the navigation matching how this entity moves.
     *
     * <p>Safe to call from a Mob constructor: every {@code canFly()}
     * implementation returns a constant, so no uninitialised state is read.
     */
    public static PathNavigation create(Mob mob, Level level) {
        boolean canFly = mob instanceof IShipNavigator navigator && navigator.canFly();

        PathNavigation navigation = canFly
                ? new FlyingPathNavigation(mob, level)
                // Amphibious pathing covers both the water surface and land,
                // which is how ship girls and abyssals actually travel.
                : new AmphibiousPathNavigation(mob, level);

        navigation.setMaxVisitedNodesMultiplier(VISITED_NODES_MULTIPLIER);
        return navigation;
    }
}
