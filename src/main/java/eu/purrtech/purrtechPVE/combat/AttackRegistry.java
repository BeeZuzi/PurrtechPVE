package eu.purrtech.purrtechPVE.combat;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every {@code pvedamage{id=...}} attack id seen in a loaded MythicMobs skill, so the {@code /pve
 * mobs} menu can list attacks that have no damage configured yet. Filled when MythicMobs parses a
 * skill line (which can happen off the main thread), hence the concurrent set.
 */
public final class AttackRegistry {

    private final Set<String> ids = ConcurrentHashMap.newKeySet();

    public void register(String attackId) {
        ids.add(attackId);
    }

    public Set<String> all() {
        return new TreeSet<>(ids);
    }
}
