package eu.purrtech.purrtechPVE.config;

import java.util.Set;

public record WorldToggleSettings(
        Set<String> disabledWorlds,
        boolean pvpEnabled,
        Set<String> pvpDisabledWorlds,
        boolean pveEnabled,
        Set<String> pveDisabledWorlds
) {

    public static WorldToggleSettings defaults() {
        return new WorldToggleSettings(Set.of(), true, Set.of(), true, Set.of());
    }
}
