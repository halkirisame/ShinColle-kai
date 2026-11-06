package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.client.model.ShipModelBaseAdv;
import com.lulan.shincolle.utility.EntityHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountWaterBobbingTest {
    @ParameterizedTest
    @CsvSource({"AfH,64.75", "BaH,64.75", "CaH,64.75", "CaWD,64.75", "HbH,64.75", "IsH,64.75", "MiH,64.75", "SuH,64.75",
            "AfH,-54.11", "BaH,-54.11", "CaH,-54.11", "CaWD,-54.11", "HbH,-54.11", "IsH,-54.11", "MiH,-54.11", "SuH,-54.11"})
    void clientFeetStateReachesAnimationAndRenderedVertices(String name, double y) throws Exception {
        var entity = (PostureTestShip) PostureTestShips.create(false, false, false, false);
        entity.tickCount = 1;
        Blocks.WATER.defaultBlockState().initCache();
        var world = allocate(FeetLevel.class);
        entity.world = world;
        Field client = net.minecraft.world.level.Level.class.getDeclaredField("isClientSide");
        client.setAccessible(true);
        client.setBoolean(world, true);
        assertTrue(entity.level().isClientSide());
        Field position = Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(entity, new Vec3(-0.25, y, -1.25));
        entity.setBoundingBox(new AABB(-0.75, y, -1.75, 0.25, y + 1.25, -0.75));
        world.expectedFeet = BlockPos.containing(-0.25, y, -1.25);
        assertEquals(0, entity.getShipDepth(0)); // No server depth has been supplied.
        assertFalse(entity.getStateFlag(com.lulan.shincolle.reference.ID.F.NoFuel));
        assertFalse(Blocks.WATER.defaultBlockState().getFluidState().isEmpty());

        Class<?> type = Class.forName("com.lulan.shincolle.client.model.ModelMount" + name);
        var root = ((LayerDefinition) type.getMethod("createBodyLayer").invoke(null)).bakeRoot();
        @SuppressWarnings("unchecked")
        var model = (ShipModelBaseAdv<Entity>) type.getConstructor(ModelPart.class).newInstance(root);
        model.prepareMobModel(entity, 0, 0, 0);
        float base = model.getOffsetY();
        float bob = name.equals("MiH") ? 0.04F : 0.05F;
        List<Double> dryVertices = null;
        for (BlockState state : new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.STONE.defaultBlockState()}) {
            world.feet = state;
            boolean wet = state.is(Blocks.WATER);
            assertEquals(wet, EntityHelper.checkEntityIsInLiquid(entity));
            world.reads = 0;
            model.prepareMobModel(entity, 0, 0, 0);
            model.setupAnim(entity, 0, 0, 0, 0, 0);
            assertEquals(base + (wet ? bob : 0), model.getOffsetY(), 0.000001F, name);
            assertTrue(world.reads <= 1, "Only the feet block is needed, no depth-column scan");
            List<Double> vertices = new ArrayList<>();
            VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("vertex")) vertices.add(((Number) args[1]).doubleValue());
                        return method.getReturnType() == VertexConsumer.class ? proxy : null;
                    });
            model.renderToBuffer(new PoseStack(), sink, 0, 0, 1, 1, 1, 1);
            assertFalse(vertices.isEmpty());
            if (dryVertices == null) dryVertices = vertices;
            assertEquals(dryVertices.size(), vertices.size());
            for (int i = 0; i < vertices.size(); i++) {
                assertEquals(dryVertices.get(i) + (wet ? bob * model.getScale() : 0), vertices.get(i), 0.000001);
            }
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class FeetLevel extends ClientLevel {
        BlockState feet;
        BlockPos expectedFeet;
        int reads;

        private FeetLevel() {
            super(null, null, null, null, 0, 0, null, null, false, 0);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            assertEquals(expectedFeet, pos);
            reads++;
            return feet;
        }
    }
}
