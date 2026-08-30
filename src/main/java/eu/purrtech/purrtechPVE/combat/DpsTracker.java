package eu.purrtech.purrtechPVE.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks each player's own damage dealt over a rolling window, for the opt-in {@code /pve dps}
 * action-bar readout - a per-player toggle (see {@link #toggle}), off by default so nobody sees
 * it unless they ask for it. {@link #currentDps} is a plain sum-of-last-{@value #WINDOW_MILLIS}
 * ms-over-window-length average, refreshed by {@link CombatDamageListener} appending it onto the
 * same action-bar message it already sends on every hit (see that class) rather than a separate
 * scheduled ticker - so it only updates when the player is actually landing hits, same as the
 * per-hit damage-type breakdown it rides alongside.
 *
 * <p>Purely in-memory, one instance shared by the whole plugin - toggle state and hit history
 * both reset on a server restart, same as any other live-only combat state (e.g. {@code
 * BleedManager}'s active bleeds). Not persisted per player because "do I currently want to see my
 * DPS" is a momentary UI preference, not a stat worth surviving a relog.
 */
public final class DpsTracker {

    private static final long WINDOW_MILLIS = 5_000L;

    private final Set<UUID> enabled = new HashSet<>();
    private final Map<UUID, Deque<Hit>> hits = new HashMap<>();

    private record Hit(long timestampMillis, double damage) {
    }

    /** Flips whether {@code playerId} sees their DPS readout on their next hit - returns the new state. */
    public boolean toggle(UUID playerId) {
        if (enabled.remove(playerId)) {
            return false;
        }
        enabled.add(playerId);
        return true;
    }

    public boolean isEnabled(UUID playerId) {
        return enabled.contains(playerId);
    }

    /** Records one hit's final dealt damage - call regardless of {@link #isEnabled}, so the window is already warm if a player toggles mid-fight. */
    public void record(UUID playerId, double damage) {
        if (damage <= 0) {
            return;
        }
        hits.computeIfAbsent(playerId, k -> new ArrayDeque<>()).addLast(new Hit(System.currentTimeMillis(), damage));
    }

    /** Sum of damage dealt in the last {@value #WINDOW_MILLIS}ms, divided by the window length in seconds - 0 with no recent hits. */
    public double currentDps(UUID playerId) {
        Deque<Hit> queue = hits.get(playerId);
        if (queue == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        while (!queue.isEmpty() && queue.peekFirst().timestampMillis() < cutoff) {
            queue.pollFirst();
        }
        double sum = 0;
        for (Hit hit : queue) {
            sum += hit.damage();
        }
        return sum / (WINDOW_MILLIS / 1000.0);
    }
}
