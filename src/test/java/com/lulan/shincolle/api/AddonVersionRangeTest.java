package com.lulan.shincolle.api;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dependency range documented for addons must accept every 1.x release and reject 0.x and 2.x. */
class AddonVersionRangeTest {

    static final String DOCUMENTED_RANGE = "[1.20.1-1.0.0,1.20.1-2)";

    private static boolean accepts(String version) throws InvalidVersionSpecificationException {
        return VersionRange.createFromVersionSpec(DOCUMENTED_RANGE)
                .containsVersion(new DefaultArtifactVersion(version));
    }

    @Test
    void acceptsEveryOneDotXRelease() throws InvalidVersionSpecificationException {
        assertTrue(accepts("1.20.1-1.0.0"));
        assertTrue(accepts("1.20.1-1.0.1"));
        assertTrue(accepts("1.20.1-1.9.9"));
        assertTrue(accepts("1.20.1-1.99.99"));
    }

    @Test
    void rejectsPreOneAndTwoDotX() throws InvalidVersionSpecificationException {
        assertFalse(accepts("1.20.1-0.12.1"));
        assertFalse(accepts("1.20.1-2.0.0"));
        assertFalse(accepts("1.20.1-2.3.4"));
        assertFalse(accepts("1.20.1-3.0.0"));
    }
}
