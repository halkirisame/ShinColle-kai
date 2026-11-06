package com.lulan.shincolle.ai.path;

import com.lulan.shincolle.entity.IShipNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;

/** Keeps ordinary amphibious edges and adds collision-safe surface-water exits. */
final class ShipAmphibiousNodeEvaluator extends AmphibiousNodeEvaluator {
    private boolean shoreWalkableAllowed;

    ShipAmphibiousNodeEvaluator() {
        super(false);
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        // The parent temporarily replaces WALKABLE malus with its swimming preference.
        this.shoreWalkableAllowed = mob.getPathfindingMalus(BlockPathTypes.WALKABLE) >= 0;
        super.prepare(region, mob);
    }

    @Override
    public void done() {
        super.done();
        this.shoreWalkableAllowed = false;
    }

    @Override
    public int getNeighbors(Node[] output, Node from) {
        int count = super.getNeighbors(output, from);
        BlockPos water = from.asBlockPos();
        if (!this.shoreWalkableAllowed || this.mob instanceof IShipNavigator ship && ship.canFly()
                || (from.type != BlockPathTypes.WATER && from.type != BlockPathTypes.WATER_BORDER)
                || !this.level.getFluidState(water).is(FluidTags.WATER)
                || this.level.getFluidState(water.above()).is(FluidTags.WATER)) return count;

        int waterTop = from.y + 1;
        int highest = Math.min(this.level.getMaxBuildHeight() - 1,
                Mth.floor(waterTop + this.mob.maxUpStep() + 0.01));
        double centerOffset = (int) (this.mob.getBbWidth() + 1.0F) / 2.0;
        double halfWidth = this.mob.getBbWidth() / 2.0;
        double centerX = from.x + centerOffset;
        double centerZ = from.z + centerOffset;
        AABB source = new AABB(centerX - halfWidth, from.y + 0.001, centerZ - halfWidth,
                centerX + halfWidth, from.y + 0.001 + this.mob.getBbHeight(), centerZ + halfWidth);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (count >= output.length) break;
            int x = from.x + direction.getStepX();
            int z = from.z + direction.getStepZ();
            for (int y = waterTop + 1; y <= highest; y++) {
                BlockPathTypes type = this.getCachedBlockType(this.mob, x, y, z);
                float malus = this.mob.getPathfindingMalus(type);
                // Do not promote fences, hazards, doors, unsupported air or forbidden cells.
                if (type != BlockPathTypes.WALKABLE || malus < 0) continue;
                AABB raised = source.move(0, y - from.y, 0);
                AABB landing = raised.move(direction.getStepX(), 0, direction.getStepZ());
                if (!this.hasFullSupport(landing, y)
                        || !this.level.noCollision(this.mob, source.minmax(raised))
                        || !this.level.noCollision(this.mob, raised.minmax(landing))) continue;

                Node candidate = this.getNode(x, y, z);
                if (candidate.closed || candidate.costMalus < 0) continue;
                boolean duplicate = false;
                for (int i = 0; i < count; i++) {
                    if (output[i].equals(candidate)) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) continue;
                candidate.type = type;
                candidate.costMalus = Math.max(candidate.costMalus, malus);
                output[count++] = candidate;
                break;
            }
        }
        return count;
    }

    /** Only whole-block, fully supported landings are added; other shapes retain the parent's rules. */
    private boolean hasFullSupport(AABB box, int feetY) {
        for (int x = Mth.floor(box.minX + 0.001); x <= Mth.floor(box.maxX - 0.001); x++) {
            for (int z = Mth.floor(box.minZ + 0.001); z <= Mth.floor(box.maxZ - 0.001); z++) {
                BlockPos below = new BlockPos(x, feetY - 1, z);
                if (!this.level.getBlockState(below).isCollisionShapeFullBlock(this.level, below)) return false;
            }
        }
        return true;
    }
}
