package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.client.render.GenericShipRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.concurrent.atomic.AtomicInteger;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShipVisualStartupTest {
    @Test
    void resolveAndRendererWaitForFirstReloadThenUsePublishedVisual() throws Exception {
        PostureTestShips.create(false, false, false, false);
        var field = ShipVisuals.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, null);
            assertNull(ShipVisuals.resolve(id("kashima")));
            var ship = (PostureTestShip) PostureTestShips.create(false, false, false, false);
            ship.definitionState().receive("shincolle_kai:kashima", 1, false);
            var context = new EntityRendererProvider.Context(null, null, null, null, null, null, null);
            var renderer = new GenericShipRenderer<PostureTestShip>(context);
            assertNotNull(renderer.getTextureLocation(ship));
            assertDoesNotThrow(() -> renderer.render(ship, 0, 0, new PoseStack(), null, 0));
            reload(resources(files()));
            assertSame(ShipVisuals.snapshot().visuals().get(id("kashima")), ShipVisuals.resolve(id("kashima")));
            assertNotSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("kashima")));
        } finally {
            field.set(null, previous);
        }
    }

    @Test
    void ninetyThreeBoneVisualWarnsOnlyOnceAcrossReloadsAndRemainsUsable() throws Exception {
        var input = files();
        input.put(id("shincolle_kai/visuals/budget_probe.json"), input.get(id("shincolle_kai/visuals/kashima.json")));
        var count = new AtomicInteger();
        var appender = new AbstractAppender("boneBudget", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (event.getMessage().getFormattedMessage().equals(
                        "Ship visual shincolle_kai:budget_probe has 93 bones; recommended maximum is 64")) count.incrementAndGet();
            }
        };
        Logger logger = (Logger) LogManager.getLogger(ShipVisuals.class);
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 2; i++) {
                reload(resources(input));
                assertNotSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("budget_probe")));
            }
            assertEquals(1, count.get());
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }
}
