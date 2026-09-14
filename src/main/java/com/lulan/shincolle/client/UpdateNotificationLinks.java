package com.lulan.shincolle.client;

import java.net.URI;
import java.net.URISyntaxException;

/** URL validation kept independent from chat component construction. */
final class UpdateNotificationLinks {

    private UpdateNotificationLinks() {
    }

    /** Returns true for an absolute HTTP(S) URL with a host. */
    static boolean isValidDownloadUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
