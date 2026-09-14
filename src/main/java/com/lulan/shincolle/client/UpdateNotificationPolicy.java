package com.lulan.shincolle.client;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Pure policy for advancing update-notification state. */
final class UpdateNotificationPolicy {

    private UpdateNotificationPolicy() {
    }

    /**
     * Evaluates one completed version check.
     *
     * @param enabled whether the feature is enabled
     * @param configuredLaunches configured launch numbers
     * @param savedState previously saved state, or {@code null}
     * @param detectedVersion version reported by Forge
     * @param notificationTarget whether Forge's status is notification-worthy
     * @return the state to retain and whether to notify
     */
    static Decision evaluate(boolean enabled, List<? extends Integer> configuredLaunches,
                             UpdateNoticeState savedState, String detectedVersion,
                             boolean notificationTarget) {
        if (!enabled || !notificationTarget || detectedVersion == null) {
            return new Decision(savedState, false);
        }

        Set<Integer> launches = normalizedLaunches(configuredLaunches);
        int launchCount;
        int lastNotifiedLaunch;
        if (savedState == null || !Objects.equals(savedState.version(), detectedVersion)) {
            launchCount = 1;
            lastNotifiedLaunch = 0;
        } else {
            launchCount = savedState.launchCount() == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : savedState.launchCount() + 1;
            lastNotifiedLaunch = savedState.lastNotifiedLaunch();
        }

        boolean notify = launches.contains(launchCount);
        if (notify) {
            lastNotifiedLaunch = launchCount;
        }
        return new Decision(new UpdateNoticeState(detectedVersion, launchCount, lastNotifiedLaunch), notify);
    }

    private static Set<Integer> normalizedLaunches(List<? extends Integer> configuredLaunches) {
        Set<Integer> normalized = new TreeSet<>();
        if (configuredLaunches != null) {
            for (Integer launch : configuredLaunches) {
                if (launch != null && launch > 0) {
                    normalized.add(launch);
                }
            }
        }
        return normalized;
    }

    /** Result of one policy evaluation. */
    record Decision(UpdateNoticeState state, boolean shouldNotify) {
    }
}
