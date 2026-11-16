package com.blackout.game.systems;

import java.util.ArrayList;
import java.util.List;

/** Picks the single best {@link Interactable} for the player's position and routes the interact key to it. */
public final class InteractionSystem {

    /** Each priority point counts as this many world units of extra closeness. */
    private static final double PRIORITY_BONUS = 20;

    private final List<Interactable> targets = new ArrayList<>();
    private Interactable current;

    public void register(Interactable i) {
        targets.add(i);
    }

    public void clear() {
        targets.clear();
        current = null;
    }

    public int size() {
        return targets.size();
    }

    /** Re-evaluates the best target for this position. Returns it (or null when nothing is in range). */
    public Interactable update(double px, double pz) {
        Interactable best = null;
        double bestScore = Double.MAX_VALUE;
        for (Interactable t : targets) {
            if (!t.available()) {
                continue;
            }
            double d = Math.hypot(px - t.x(), pz - t.z());
            if (d > t.range()) {
                continue;
            }
            double score = d - t.priority() * PRIORITY_BONUS;
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        current = best;
        return best;
    }

    public Interactable current() {
        return current;
    }

    /** Uses the current target. Returns true if something was actually triggered. */
    public boolean use() {
        Interactable c = current;
        if (c == null || !c.available() || !c.enabled()) {
            return false;
        }
        c.interact();
        return true;
    }
}
