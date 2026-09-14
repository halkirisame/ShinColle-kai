package com.lulan.shincolle.client;

import net.minecraftforge.fml.VersionChecker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateStatusAdapterTest {

    @ParameterizedTest
    @EnumSource(VersionChecker.Status.class)
    void onlyOutdatedStatesAreNotificationTargets(VersionChecker.Status status) {
        boolean expected = status == VersionChecker.Status.OUTDATED
                || status == VersionChecker.Status.BETA_OUTDATED;

        assertEquals(expected, UpdateStatusAdapter.isNotificationTarget(status));
    }
}
