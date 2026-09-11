package com.lulan.shincolle.ai.path;

import com.lulan.shincolle.entity.IShipNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Amphibious path generation with the original ships' waypoint lookahead. */
public final class ShipPathNavigation extends AmphibiousPathNavigation {

    public ShipPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected void followThePath() {
        Vec3 mobPos = this.getTempMobPos();
        this.maxDistanceToWaypoint = Math.max(this.mob.getBbWidth() * 0.75F, 0.75F);

        int sameHeightEnd = this.path.getNodeCount();
        // Upstream compares against floor(feet Y + 0.5), not the amphibious body midpoint.
        double pathHeight = Math.floor(this.mob.getY() + 0.5D);
        for (int index = this.path.getNextNodeIndex(); index < this.path.getNodeCount(); index++) {
            if (this.path.getNode(index).y != pathHeight) {
                sameHeightEnd = index;
                break;
            }
        }

        BlockPos nextPos = this.path.getNextNodePos();
        if (Math.abs(this.mob.getX() - nextPos.getX() - 0.5D) < this.maxDistanceToWaypoint
                && Math.abs(this.mob.getZ() - nextPos.getZ() - 0.5D) < this.maxDistanceToWaypoint) {
            this.path.advance();
        }

        for (int index = sameHeightEnd - 1; index >= this.path.getNextNodeIndex(); index--) {
            // canMoveDirectly rejects dry land; use the shared collision check in both media.
            Vec3 target = this.path.getEntityPosAtNode(this.mob, index);
            if (isClearForMovementBetween(this.mob, mobPos, target, false)
                    && this.hasSupportBetween(target, (int) pathHeight)) {
                this.path.setNextNodeIndex(index);
                break;
            }
        }

        this.doStuckDetection(mobPos);
    }

    /** Visit every horizontal grid cell of the shortcut at waypoint feet height. */
    private boolean hasSupportBetween(Vec3 target, int feetY) {
        if (this.mob instanceof IShipNavigator ship && ship.canFly()) {
            return true;
        }
        // Preserve the non-air predicate, including water, at waypoint feet height.
        return HorizontalSupportScan.hasSupportBetween(this.mob.getX(), this.mob.getZ(), target.x, target.z,
                (x, z) -> this.isStableDestination(new BlockPos(x, feetY, z)));
    }
}
