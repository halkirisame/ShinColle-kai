package com.lulan.shincolle.gametest;

import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Guards the GameTest world contract: flat terrain and a fixed seed.
 *
 * <p>On natural terrain, the structure clearing that precedes each test hollows out the ground,
 * and gravel or sand left above the hollow falls into the test area as falling-block entities.
 * Spatial queries then count those entities as candidates (TASK-105). build.gradle writes the
 * world settings into server.properties and recreates the world before each run.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameTestWorldGameTests {

    private static final String SEED_PROPERTY = "shincolle.gametestLevelSeed";

    private GameTestWorldGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void gameTestWorldIsFlatWithFixedSeed(GameTestHelper helper) {
        ChunkGenerator generator = helper.getLevel().getChunkSource().getGenerator();
        if (!(generator instanceof FlatLevelSource)) {
            throw new AssertionError("GameTest world is not flat (generator " + generator.getClass().getName()
                    + "). Natural terrain lets gravel and sand fall into test areas.");
        }

        String expectedSeed = System.getProperty(SEED_PROPERTY);
        if (expectedSeed == null) {
            throw new AssertionError(SEED_PROPERTY + " is not set. Run GameTests through the Gradle run configuration.");
        }
        long actualSeed = helper.getLevel().getSeed();
        if (actualSeed != Long.parseLong(expectedSeed)) {
            throw new AssertionError("GameTest world seed is " + actualSeed + ", expected " + expectedSeed
                    + ". The world was not recreated with the configured seed.");
        }
        helper.succeed();
    }
}
