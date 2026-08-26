package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.config.WorldToggleSettings;

/**
 * Whether the custom damage/resistance/trinket system should run at all for a
 * given hit - a world on the global disabled list always wins, then PvP/PvE
 * has its own on/off switch plus its own per-world override list.
 */
public final class WorldToggleEvaluator {

    private WorldToggleEvaluator() {
    }

    public static boolean isActive(WorldToggleSettings settings, String worldName, CombatKind kind) {
        if (settings.disabledWorlds().contains(worldName)) {
            return false;
        }
        return switch (kind) {
            case PVP -> settings.pvpEnabled() && !settings.pvpDisabledWorlds().contains(worldName);
            case PVE -> settings.pveEnabled() && !settings.pveDisabledWorlds().contains(worldName);
        };
    }
}
