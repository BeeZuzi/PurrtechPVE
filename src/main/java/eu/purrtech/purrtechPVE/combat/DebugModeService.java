package eu.purrtech.purrtechPVE.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player opt-in toggle for the {@code /pve debug} equip/hold readout (see {@code
 * EquipmentDebugListener}) - same in-memory, self-toggle pattern as {@link DpsTracker#toggle},
 * for the same reason: a momentary testing preference, not something worth persisting past a
 * restart.
 */
public final class DebugModeService {

    private final Set<UUID> enabled = new HashSet<>();

    /** Flips whether {@code playerId} sees equip/hold debug messages - returns the new state. */
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
}
