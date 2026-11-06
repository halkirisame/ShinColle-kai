package com.lulan.shincolle.client;

import com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Skill;
import com.lulan.shincolle.client.particle.ParticleTeam;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SMountSkillPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CMountSkillPacket;
import com.lulan.shincolle.playerskill.ShipSkillHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Locale;

/** Mounted hotbar input, aiming and the four manual weapon icons. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MountSkillClient {
    private static final ResourceLocation HUD = new ResourceLocation(Reference.MOD_ID, "textures/gui/guihud.png");
    private static final boolean[] SCAN_DOWN = new boolean[4];
    private static final boolean[] CLICKED = new boolean[4];
    private static int sampleCooldown;
    private static BasicEntityShip sampledHost;

    private MountSkillClient() { }

    private static BasicEntityShip activeHost(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null || !mc.isWindowActive()) return null;
        return ShipSkillHandler.controlledHost(mc.player);
    }

    private static boolean teamSelection(Minecraft mc) {
        return mc.options.keySprint.isDown() && (mc.player.getMainHandItem().is(ModItems.POINTER.get())
                || mc.player.getOffhandItem().is(ModItems.POINTER.get()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        BasicEntityShip host = activeHost(mc);
        Arrays.fill(CLICKED, false);
        if (host == null) {
            Arrays.fill(SCAN_DOWN, false);
            sampleCooldown = 0;
            sampledHost = null;
            return;
        }
        if (teamSelection(mc) || Arrays.stream(Skill.values()).noneMatch(skill -> ShipSkillHandler.hasAbility(host, skill))) return;
        // Drain clicks before vanilla changes the selected slot, including duplicate bindings.
        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            for (int skill = 0; skill < 4; skill++) {
                if (!mc.options.keyHotbarSlots[i].getKey().equals(mc.options.keyHotbarSlots[skill].getKey())) continue;
                while (mc.options.keyHotbarSlots[i].consumeClick()) CLICKED[skill] = true;
            }
        }
    }

    @SubscribeEvent
    public static void keyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 4; i++) {
            if (mc.options.keyHotbarSlots[i].matches(event.getKey(), event.getScanCode())) {
                SCAN_DOWN[i] = event.getAction() != GLFW.GLFW_RELEASE;
            }
        }
    }

    private static boolean held(Minecraft mc, int index) {
        var mapping = mc.options.keyHotbarSlots[index];
        InputConstants.Key key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        return mapping.isDown() || switch (key.getType()) {
            case KEYSYM -> key.getValue() != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> SCAN_DOWN[index];
        };
    }

    /** Called after mount GUI arbitration, independently of mount movement throttling. */
    static void sample(boolean guiRequested) {
        Minecraft mc = Minecraft.getInstance();
        BasicEntityShip host = activeHost(mc);
        if (guiRequested || host == null || teamSelection(mc)) {
            sampleCooldown = 0;
            sampledHost = null;
            Arrays.fill(CLICKED, false);
            return;
        }
        if (Arrays.stream(Skill.values()).noneMatch(skill -> ShipSkillHandler.hasAbility(host, skill))) return;
        if (sampledHost != host) sampleCooldown = 0;
        sampledHost = host;
        int selected = -1;
        for (int i = 0; i < 4; i++) {
            if (held(mc, i) || CLICKED[i]) {
                selected = i;
                break;
            }
        }
        Arrays.fill(CLICKED, false);
        if (selected < 0) {
            sampleCooldown = 0;
            return;
        }
        if (sampleCooldown > 0 && --sampleCooldown > 0) return;
        sampleCooldown = 2;
        Skill skill = Skill.values()[selected];
        BasicEntityMount mount = (BasicEntityMount) mc.player.getVehicle();
        Entity viewer = mc.getCameraEntity();
        if (viewer == null) viewer = mc.player;
        if (viewer.getVehicle() instanceof BasicEntityMount viewMount && viewMount.getHostEntity() != null) {
            viewer = viewMount.getHostEntity();
        }
        double range = host.getAttrs().getAttackRange();
        Vec3 start = viewer.getEyePosition(1F);
        Vec3 end = start.add(viewer.getViewVector(1F).scale(range));
        BlockHitResult solid = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        double nearest = start.distanceToSqr(solid.getLocation());
        Entity target = null;
        for (Entity candidate : mc.level.getEntities(viewer, new AABB(start, end).inflate(1), entity ->
                entity != mc.player && entity != mount && entity != host && entity.isPickable()
                        && !entity.isInvisible() && !TargetHelper.isEntityInvulnerable(entity))) {
            AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            Vec3 hit = box.contains(start) ? start : box.clip(start, end).orElse(null);
            if (hit != null && start.distanceToSqr(hit) < nearest) {
                target = candidate;
                nearest = start.distanceToSqr(hit);
            }
        }
        if (target != null) {
            mc.particleEngine.add(new ParticleTeam(mc.level, target, 0.3F, 5));
            ModNetworking.sendToServer(new C2SMountSkillPacket(skill, new C2SMountSkillPacket.EntityTarget(target.getId())));
        } else if (skill == Skill.HEAVY) {
            // Observe only the bounded feet column; never change the mount's movement depth.
            BlockPos feet = mount.blockPosition();
            double depth = 0;
            for (int i = 0; i < 4; i++) {
                BlockPos pos = feet.above(i);
                if (mc.level.isOutsideBuildHeight(pos) || !mc.level.hasChunkAt(pos)) break;
                var fluid = mc.level.getFluidState(pos);
                if (fluid.isEmpty()) break;
                depth = pos.getY() + fluid.getHeight(mc.level, pos) - mount.getY();
                if (depth >= 3) break;
            }
            BlockHitResult block = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    depth >= 3 ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY, viewer));
            if (block.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = block.getBlockPos();
                mc.particleEngine.add(new ParticleTeam(mc.level, 0.3F, 5, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
                ModNetworking.sendToServer(new C2SMountSkillPacket(skill, new C2SMountSkillPacket.BlockTarget(pos)));
            }
        }
    }

    public static void receive(S2CMountSkillPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.hostId()) instanceof BasicEntityShip ship)) return;
        ship.setStateTimer(ID.T.MountSkillCD1, packet.light());
        ship.setStateTimer(ID.T.MountSkillCD2, packet.heavy());
        ship.setStateTimer(ID.T.MountSkillCD3, packet.airLight());
        ship.setStateTimer(ID.T.MountSkillCD4, packet.airHeavy());
        if (ship instanceof BasicEntityShipCV cv) {
            cv.setNumAircraftLight(packet.planesLight());
            cv.setNumAircraftHeavy(packet.planesHeavy());
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BasicEntityShip ship = activeHost(mc);
        if (ship == null || mc.options.hideGui) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = (int) (mc.getWindow().getGuiScaledWidth() * ConfigHandler.posHUD[0]);
        int y = (int) (mc.getWindow().getGuiScaledHeight() * ConfigHandler.posHUD[1]);
        for (Skill skill : Skill.values()) {
            if (!ShipSkillHandler.hasAbility(ship, skill)) continue;
            int i = skill.ordinal();
            int px = x - 40 + i * 21;
            int cd = Math.max(0, ShipSkillHandler.cooldown(ship, skill));
            int max = CombatHelper.getAttackDelay(ship.getAttrs().getAttackSpeed(), i + 1);
            int mask = max <= 0 ? 0 : (int) (Math.min(1D, (double) cd / max) * 18);
            graphics.blit(HUD, px, y, i * 18, 0, 18, 18);
            if (mask > 0) graphics.blit(HUD, px, y + 18 - mask, 0, 36 - mask, 18, mask);
            if (cd > 0) graphics.drawString(mc.font, String.format(Locale.ROOT, "%.1f", cd * 0.05F), px + 5, y + 18, 0xFFFF00);
            if (held(mc, i)) graphics.fill(px, y, px + 18, y + 18, 0x3FFFFFFF);
            graphics.drawString(mc.font, Integer.toString(i + 1), px, y + 9, 0xFFFFFF);
            if (ship instanceof BasicEntityShipCV cv && i >= 2) {
                graphics.drawString(mc.font, Integer.toString(i == 2 ? cv.getNumAircraftLight() : cv.getNumAircraftHeavy()),
                        px + 7, y - 8, i == 2 ? 0x00FF00 : 0x00FFFF);
            }
        }
    }
}
