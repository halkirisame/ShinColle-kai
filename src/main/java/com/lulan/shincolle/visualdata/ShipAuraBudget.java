package com.lulan.shincolle.visualdata;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Rolling client-tick emission history; denied emissions are never queued. */
public final class ShipAuraBudget {
    private record Emission(long tick, int id) { }
    private final ArrayDeque<Emission> history = new ArrayDeque<>();
    private final Map<Integer, Long> last = new HashMap<>();

    public void advance(long tick) {
        while (!history.isEmpty() && tick - history.peekFirst().tick() >= 20) history.removeFirst();
        last.entrySet().removeIf(entry -> tick - entry.getValue() >= 20);
    }

    public boolean emit(long tick, int id, int liveCount) {
        advance(tick);
        Long previous = last.get(id);
        if (liveCount >= 64 || history.size() >= 64 || previous != null && tick - previous < 5) return false;
        history.addLast(new Emission(tick, id));
        last.put(id, tick);
        return true;
    }

    public void reset() {
        history.clear();
        last.clear();
    }
}
