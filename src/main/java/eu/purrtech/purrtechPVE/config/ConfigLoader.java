package eu.purrtech.purrtechPVE.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads config.yml into the settings records the rest of the plugin
 * expects. Falls back to each record's own .defaults() when a section is
 * missing, so a partially-edited or pre-upgrade config.yml doesn't break
 * startup.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static WorldToggleSettings loadWorldToggles(FileConfiguration config) {
        WorldToggleSettings defaults = WorldToggleSettings.defaults();

        Set<String> disabledWorlds = toSet(config.getStringList("worlds.disabled"));

        ConfigurationSection pvp = config.getConfigurationSection("pvp");
        boolean pvpEnabled = pvp != null ? pvp.getBoolean("enabled", defaults.pvpEnabled()) : defaults.pvpEnabled();
        Set<String> pvpDisabledWorlds = toSet(config.getStringList("pvp.disabled-worlds"));

        ConfigurationSection pve = config.getConfigurationSection("pve");
        boolean pveEnabled = pve != null ? pve.getBoolean("enabled", defaults.pveEnabled()) : defaults.pveEnabled();
        Set<String> pveDisabledWorlds = toSet(config.getStringList("pve.disabled-worlds"));

        return new WorldToggleSettings(disabledWorlds, pvpEnabled, pvpDisabledWorlds, pveEnabled, pveDisabledWorlds);
    }

    public static AccessorySettings loadAccessorySettings(FileConfiguration config) {
        List<String> slots = config.getStringList("accessory-slots");
        return slots.isEmpty() ? AccessorySettings.defaults() : new AccessorySettings(List.copyOf(slots));
    }

    public static CombatFeedbackSettings loadCombatFeedbackSettings(FileConfiguration config) {
        CombatFeedbackSettings defaults = CombatFeedbackSettings.defaults();
        ConfigurationSection combat = config.getConfigurationSection("combat");
        boolean effectivenessColors = combat != null
                ? combat.getBoolean("show-effectiveness-colors", defaults.effectivenessColors())
                : defaults.effectivenessColors();
        return new CombatFeedbackSettings(effectivenessColors);
    }

    public static String loadLocale(FileConfiguration config) {
        return config.getString("locale", "cs");
    }

    private static Set<String> toSet(List<String> values) {
        return new LinkedHashSet<>(values);
    }
}
