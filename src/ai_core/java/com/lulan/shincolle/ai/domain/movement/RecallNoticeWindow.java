package com.lulan.shincolle.ai.domain.movement;

/** One non-extending notification window for a departure's newly ended entries. */
public record RecallNoticeWindow(long started, int absent, int failures, int limits) {
    public static RecallNoticeWindow empty(long now) { return new RecallNoticeWindow(now, 0, 0, 0); }

    public RecallNoticeWindow add(RecallLoadQueue.Result result) {
        return switch (result) {
            case ABSENT_AFTER_LOAD -> new RecallNoticeWindow(started, absent + 1, failures, limits);
            case LOAD_BUDGET_EXCEEDED, QUEUE_BUDGET_EXCEEDED, ROSTER_EXPIRED ->
                    new RecallNoticeWindow(started, absent, failures, limits + 1);
            default -> this;
        };
    }

    public boolean due(long now) { return now - started >= 20L && absent + failures + limits > 0; }
}
