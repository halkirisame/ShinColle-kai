package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.visualdata.ShipAuraBudget;
import com.lulan.shincolle.visualdata.ShipAuraSelection;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Client event boundary for frame selection, tick-only emissions and lifecycle disposal. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ShipAuraClient {
    private static final ShipAuraSelection SELECTION = new ShipAuraSelection();
    private static final ShipAuraBudget BUDGET = new ShipAuraBudget();
    private static final List<ShipAuraParticle> PARTICLES = new ArrayList<>();
    private static final ResourceLocation PARTICLE_SPRITE = new ResourceLocation("minecraft", "block/white_concrete");
    private static ClientLevel world;
    private static Entity cameraEntity;
    private static Object cameraType;
    private static long frame;
    private static long tick;

    private ShipAuraClient() { }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
            context();
        }
    }

    private static boolean context() {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        Object type = mc.options.getCameraType();
        if (world != mc.level || cameraEntity != camera || cameraType != type) {
            reset();
            ShipVisuals.clearTextureCache();
            world = mc.level;
            cameraEntity = camera;
            cameraType = type;
        }
        return world != null && cameraEntity != null;
    }

    @SubscribeEvent
    public static void renderStage(RenderLevelStageEvent event) {
        if (!context()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = event.getCamera();
            Vec3 position = camera.getPosition();
            Frustum frustum = event.getFrustum();
            List<ShipAuraSelection.Candidate> candidates = new ArrayList<>();
            for (Entity entity : world.entitiesForRendering()) {
                if (!(entity instanceof GenericHostileShipEntity ship)) continue;
                if (!eligible(ship, position)) continue;
                var box = ship.getBoundingBoxForCulling().inflate(0.5);
                if (box.hasNaN() || box.getSize() == 0) {
                    box = new AABB(ship.getX() - 2, ship.getY() - 2, ship.getZ() - 2,
                            ship.getX() + 2, ship.getY() + 2, ship.getZ() + 2);
                }
                boolean visible = frustum.isVisible(box)
                        && (mc.getEntityRenderDispatcher().shouldRender(ship, frustum, position.x, position.y, position.z)
                        || ship.hasIndirectPassenger(mc.player))
                        && (world.isOutsideBuildHeight(ship.blockPosition().getY())
                        || event.getLevelRenderer().isChunkCompiled(ship.blockPosition()))
                        && (ship != camera.getEntity() || camera.isDetached()
                        || camera.getEntity() instanceof LivingEntity living && living.isSleeping());
                candidates.add(new ShipAuraSelection.Candidate(ship.getId(), position.distanceToSqr(ship.position()),
                        true, true, false, false, true, visible));
            }
            SELECTION.begin(frame, candidates);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            SELECTION.complete(frame);
        }
    }

    private static boolean eligible(GenericHostileShipEntity ship, Vec3 camera) {
        return new ShipAuraSelection.Candidate(ship.getId(), camera.distanceToSqr(ship.position()), true,
                ship.isAlive() && !ship.isRemoved() && !ship.isSpectator(), ship.definitionState().dormant(),
                ship.isInvisible(), ship.individualPresentation().redAura(), true).eligible();
    }

    public static boolean selected(Entity entity) {
        return entity instanceof GenericHostileShipEntity ship && context() && SELECTION.contains(ship.getId())
                && eligible(ship, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !context() || Minecraft.getInstance().isPaused()) return;
        tick++;
        BUDGET.advance(tick);
        PARTICLES.removeIf(particle -> particle.expire(tick));
        Minecraft mc = Minecraft.getInstance();
        ParticleStatus setting = mc.options.particles().get();
        if (setting == ParticleStatus.MINIMAL || setting == ParticleStatus.DECREASED && tick % 10 != 0) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (int id : SELECTION.completed()) {
            if (!(world.getEntity(id) instanceof GenericHostileShipEntity ship) || !eligible(ship, camera)) continue;
            if (!BUDGET.emit(tick, id, PARTICLES.size())) continue;
            double width = ship.getBbWidth();
            ShipAuraParticle particle = new ShipAuraParticle(world,
                    ship.getX() + (world.random.nextDouble() - 0.5) * width,
                    ship.getY() + world.random.nextDouble() * ship.getBbHeight(),
                    ship.getZ() + (world.random.nextDouble() - 0.5) * width,
                    mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(PARTICLE_SPRITE), tick);
            PARTICLES.add(particle);
            mc.particleEngine.add(particle);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ShipVisuals.clearTextureCache();
        world = null;
        cameraEntity = null;
        cameraType = null;
    }

    static void reset() {
        SELECTION.reset();
        BUDGET.reset();
        PARTICLES.forEach(ShipAuraParticle::remove);
        PARTICLES.clear();
    }
}
