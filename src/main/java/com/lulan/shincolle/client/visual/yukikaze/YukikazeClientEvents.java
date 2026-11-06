package com.lulan.shincolle.client.visual.yukikaze;

import com.lulan.shincolle.client.visual.ShipVisualGeoModel;
import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YukikazeClientEvents {
    private YukikazeClientEvents() { }

    @SubscribeEvent
    public static void compileLayers(GeoRenderEvent.ReplacedEntity.CompileRenderLayers event) {
        var renderer = (GeoReplacedEntityRenderer<?, ?>) event.getRenderer();
        // Other mods' models may need a non-null animatable to resolve resources.
        if (!(renderer.getGeoModel() instanceof ShipVisualGeoModel model)) return;
        var resource = model.getModelResource(null);
        if (!resource.toString().equals("shincolle_kai:geo/yukikaze.geo.json")) return;
        event.addLayer(new YukikazeExpressionLayer(renderer));
    }
}
