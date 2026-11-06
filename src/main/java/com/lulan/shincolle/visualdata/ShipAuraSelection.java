package com.lulan.shincolle.visualdata;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Value-only eligibility and stable, frame-owned selection. */
public final class ShipAuraSelection {
    public record Candidate(int id, double distanceSquared, boolean hostile, boolean alive, boolean dormant,
                            boolean invisible, boolean redAura, boolean visible) {
        public boolean eligible() {
            return hostile && alive && !dormant && !invisible && redAura && visible
                    && distanceSquared >= 0 && distanceSquared < 1024;
        }
    }

    private long frame;
    private boolean started;
    private List<Integer> selected = List.of();
    private List<Integer> completed = List.of();

    public void begin(long frame, Collection<Candidate> candidates) {
        if (started && this.frame == frame) return;
        started = true;
        this.frame = frame;
        selected = candidates.stream().filter(Candidate::eligible)
                .sorted(Comparator.comparingDouble(Candidate::distanceSquared).thenComparingInt(Candidate::id))
                .map(Candidate::id).distinct().limit(16).toList();
    }

    public void complete(long frame) {
        if (started && this.frame == frame) completed = selected;
    }

    public boolean contains(int id) { return selected.contains(id); }
    public List<Integer> completed() { return completed; }

    public void reset() {
        started = false;
        selected = List.of();
        completed = List.of();
    }
}
