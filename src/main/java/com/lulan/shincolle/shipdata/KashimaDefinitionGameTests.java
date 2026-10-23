package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import com.lulan.shincolle.visualdata.ShipVisualRenderMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KashimaDefinitionGameTests {
    private static final ShipDefinitionId KASHIMA = ShipDefinitionId.parse("shincolle_kai:kashima");

    private KashimaDefinitionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void officialKashimaSpawnsAlliedWithTenryuuStatsOnDedicatedServer(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity ship = (GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, false, null);
            helper.assertTrue(ship != null, "official allied definition not spawnable");
            entities.add(ship);
            place(helper, ship);
            checkDefinition(helper, ship);
            helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "missing fuel");
            BasicEntityShip tenryuu = entities.add(ModEntities.CL_TENRYUU.get().create(helper.getLevel()));
            place(helper, tenryuu);
            for (int level : new int[]{1, 25, 100}) {
                ship.setShipLevel(level, false);
                tenryuu.setShipLevel(level, false);
                helper.assertTrue(ship.getLevel() == level && tenryuu.getLevel() == level, "level fixture ignored");
                ship.calcShipAttributes(31, false);
                tenryuu.calcShipAttributes(31, false);
                helper.assertTrue(Arrays.equals(ship.getAttrs().getAttrsRaw(), tenryuu.getAttrs().getAttrsRaw()),
                        "provisional base/growth differs from Tenryuu at " + level);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void officialKashimaSpawnsHostileWithServerDefinitionAndNoClientAdapter(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, true, null);
            helper.assertTrue(ship != null, "official hostile definition not spawnable");
            entities.add(ship);
            place(helper, ship);
            checkDefinition(helper, ship);
            helper.assertTrue(!ship.definitionState().dormant(), "hostile definition missing");
            helper.assertTrue(ship.getDropEgg().isEmpty(), "unimplemented hostile acquisition enabled");
        });
    }

    private static void checkDefinition(GameTestHelper helper, Mob entity) {
        DefinitionBackedShip ship = (DefinitionBackedShip) entity;
        ShipDefinition definition = ship.definitionState().definition();
        helper.assertTrue(definition != null && definition.id().equals(KASHIMA), "wrong definition identity");
        helper.assertTrue(definition.visual().equals(new ResourceLocation("shincolle_kai", "kashima")), "wrong visual ID");
        helper.assertTrue(!definition.construction().enabled() && !definition.spawn().enabled(), "natural acquisition enabled");
        helper.assertTrue(ship.getShipId().equals(KASHIMA.toString()) && !ship.definitionState().dormant(), "entity not initialized");
        helper.assertTrue(!entity.level().isClientSide(), "test is not running on dedicated server");
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void kashimaPresentationReadsExistingServerStateWithoutMovingTheEntity(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity ship = (GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, false, null);
            helper.assertTrue(ship != null, "official allied definition not spawnable");
            entities.add(ship);
            place(helper, ship);
            Vec3 position = ship.position();
            var bounds = ship.getBoundingBox();
            helper.assertTrue(ship.isAppearanceBitToggleable(0), "equipment toggle not exposed by existing GUI contract");
            for (int state : new int[]{0, 1, 2, 3}) {
                ship.setStateEmotion(ID.S.State, state, true);
                helper.assertTrue(ship.getStateEmotion(ID.S.State) == state, "model-state fixture ignored");
                helper.assertTrue(ShipVisualPresentation.equipmentVisible(ship.getStateEmotion(ID.S.State)) == ((state & 1) != 0),
                        "equipment visibility differs from cannon bit");
            }
            for (int emotion = 0; emotion < 10; emotion++) {
                ship.setStateEmotion(ID.S.Emotion, emotion, true);
                helper.assertTrue(ship.getStateEmotion(ID.S.Emotion) == emotion, "emotion fixture ignored");
                var view = ShipVisualPresentation.emotion(ship.getStateEmotion(ID.S.Emotion), false);
                helper.assertTrue(view.face() >= 0 && view.face() < 10 && view.mouth() >= 0 && view.mouth() < 6,
                        "invalid static expression");
            }
            helper.assertTrue(ship.position().equals(position) && ship.getBoundingBox().equals(bounds),
                    "presentation state moved entity or changed collision");
        });
    }

    private static void fixture(GameTestHelper helper, java.util.function.Consumer<GameTestEntities> test) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                test.accept(entities);
                helper.succeed();
            }
        }, new Vec3(1.5, 2, 1.5));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void kashimaDrawingScaleLeavesAlliedAndHostileCollisionDimensionsUnchanged(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity allied = (GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, false, null);
            GenericHostileShipEntity hostile = (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, true, null);
            helper.assertTrue(allied != null && hostile != null, "scale fixtures not spawnable");
            entities.add(allied);
            entities.add(hostile);
            place(helper, allied);
            place(helper, hostile);
            helper.assertTrue(allied.getStateMinor(ID.M.NumGrudge) > 0 && !allied.getStateFlag(ID.F.NoFuel), "missing fuel");
            for (int level = 0; level <= 3; level++) {
                allied.setScaleLevel(level);
                hostile.setScaleLevel(level);
                helper.assertTrue(allied.getScaleLevel() == level && hostile.getScaleLevel() == level, "scale fixture ignored");
                float width = 0.5F * (1 + level * 0.5F);
                float height = 1.5F * (1 + level * 0.5F);
                var alliedSize = allied.getDimensions(allied.getPose());
                var hostileSize = hostile.getDimensions(hostile.getPose());
                var bounds = allied.getBoundingBox();
                helper.assertTrue(alliedSize.width == width && hostileSize.width == width
                        && alliedSize.height == height && hostileSize.height == height, "collision formula changed");
                helper.assertTrue(ShipVisualRenderMath.scale(0.46F, level) == 0.46F * (1 + level), "drawing scale ratio changed");
                helper.assertTrue(ShipVisualRenderMath.shadow(0.35F, level) == 0.35F + level * 0.4F, "shadow formula changed");
                helper.assertTrue(allied.getBoundingBox().equals(bounds), "drawing calculation changed collision");
            }
        });
    }

    private static void place(GameTestHelper helper, Mob entity) {
        entity.setNoAi(true);
        entity.setInvulnerable(true);
        entity.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "entity spawn rejected");
        GameTestEntities.assertRegistered(helper, entity);
    }
}
