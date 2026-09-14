package com.lulan.shincolle.client;

/** Persisted notification progress for one detected version. */
record UpdateNoticeState(String version, int launchCount, int lastNotifiedLaunch) {
}
