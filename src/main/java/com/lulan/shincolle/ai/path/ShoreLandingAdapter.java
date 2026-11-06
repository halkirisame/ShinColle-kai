package com.lulan.shincolle.ai.path;

import com.lulan.shincolle.ai.domain.movement.ShoreFacts;
import com.lulan.shincolle.ai.domain.movement.ShoreLandingRules;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipNavigator;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.OptionalDouble;

/** Converts loaded terrain and the physical body's movement into one observation. */
final class ShoreLandingAdapter {
    private ShoreLandingAdapter() {
    }

    static ShoreFacts observe(Mob body, IShipNavigator navigator, boolean moving,
                              double wantedX, double wantedY, double wantedZ) {
        long key = BlockPos.containing(wantedX, wantedY, wantedZ).asLong();
        boolean controlled = body instanceof BasicEntityMount mount && mount.getControllingPassenger() instanceof Player;
        boolean flying = navigator.canFly();
        boolean liquid = EntityHelper.checkEntityIsInLiquid(body);
        double feet = body.getBoundingBox().minY;
        double step = body.maxUpStep();
        OptionalDouble waterTop = OptionalDouble.empty();
        if (moving && !flying && !controlled) {
            BlockPos.MutableBlockPos water = BlockPos.containing(body.getX(), feet, body.getZ()).mutable();
            int bottom = Math.max(body.level().getMinBuildHeight(), Mth.floor(feet - step - 1.01));
            while (water.getY() >= bottom && body.level().hasChunkAt(water)) {
                if (body.level().getFluidState(water).is(FluidTags.WATER)) {
                    while (water.getY() + 1 < body.level().getMaxBuildHeight()
                            && body.level().hasChunkAt(water.above())
                            && body.level().getFluidState(water.above()).is(FluidTags.WATER)) {
                        water.move(Direction.UP);
                    }
                    waterTop = OptionalDouble.of(water.getY() + 1.0);
                    break;
                }
                if (!body.level().getBlockState(water).getCollisionShape(body.level(), water).isEmpty()) break;
                water.move(Direction.DOWN);
            }
        }
        boolean afloat = ShoreLandingRules.isAfloat(liquid, body.onGround(), waterTop.isPresent(), feet,
                waterTop.orElse(0), step);
        OptionalDouble ledge = OptionalDouble.empty();
        boolean headroom = false;
        double dx = wantedX - body.getX();
        double dz = wantedZ - body.getZ();
        double distanceSq = dx * dx + dz * dz;
        if (waterTop.isPresent() && distanceSq >= 1.0E-4) {
            double offset = (body.getBbWidth() / 2.0 + 0.5) / Math.sqrt(distanceSq);
            dx *= offset;
            dz *= offset;
            int waterY = Mth.floor(waterTop.getAsDouble()) - 1;
            BlockPos.MutableBlockPos column = BlockPos.containing(body.getX() + dx, waterY, body.getZ() + dz).mutable();
            int searchTop = Math.min(body.level().getMaxBuildHeight() - 1, waterY + Mth.ceil(step + 1));
            boolean found = false;
            while (column.getY() < body.level().getMaxBuildHeight() && body.level().hasChunkAt(column)) {
                var shape = body.level().getBlockState(column).getCollisionShape(body.level(), column);
                if (shape.isEmpty()) {
                    if (found || column.getY() >= searchTop) break;
                } else {
                    found = true;
                    ledge = OptionalDouble.of(column.getY() + shape.max(Direction.Axis.Y));
                }
                column.move(Direction.UP);
                if (!found && column.getY() > searchTop) break;
            }
            if (ledge.isPresent()) {
                AABB landingBox = body.getBoundingBox().move(dx, ledge.getAsDouble() + 0.01 - feet, dz);
                headroom = body.level().noCollision(body, landingBox);
            }
        }
        return new ShoreFacts(moving, flying, controlled, body.onGround(), afloat, waterTop.orElse(0),
                body.horizontalCollision, ledge, headroom, wantedY, step, body.tickCount, key);
    }
}
