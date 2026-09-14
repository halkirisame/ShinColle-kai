package com.lulan.shincolle.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateNotificationLinksTest {

    @Test
    void acceptsOnlyValidHttpAndHttpsUrls() {
        assertTrue(UpdateNotificationLinks.isValidDownloadUrl("https://www.curseforge.com/projects/1683061"));
        assertTrue(UpdateNotificationLinks.isValidDownloadUrl("http://example.com/download"));
        assertFalse(UpdateNotificationLinks.isValidDownloadUrl(null));
        assertFalse(UpdateNotificationLinks.isValidDownloadUrl(""));
        assertFalse(UpdateNotificationLinks.isValidDownloadUrl("ftp://example.com/download"));
        assertFalse(UpdateNotificationLinks.isValidDownloadUrl("https://"));
        assertFalse(UpdateNotificationLinks.isValidDownloadUrl("https://example.com/bad path"));
    }
}
