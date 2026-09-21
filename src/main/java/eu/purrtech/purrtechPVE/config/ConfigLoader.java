package eu.purrtech.purrtechPVE.config;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public static DropHologramSettings loadDropHologramSettings(FileConfiguration config) {
        DropHologramSettings defaults = DropHologramSettings.defaults();
        ConfigurationSection dropHologram = config.getConfigurationSection("drop-hologram");
        boolean enabled = dropHologram != null ? dropHologram.getBoolean("enabled", defaults.enabled()) : defaults.enabled();
        return new DropHologramSettings(enabled);
    }

    public static ArmorPenetrationConversionSettings loadArmorPenetrationConversion(FileConfiguration config) {
        Map<ArmorClass, Map<ArmorClass, Double>> factors = new EnumMap<>(ArmorClass.class);
        ConfigurationSection root = config.getConfigurationSection("armor-penetration-conversion");
        for (ArmorClass from : ArmorClass.values()) {
            Map<ArmorClass, Double> row = new EnumMap<>(ArmorClass.class);
            ConfigurationSection fromSection = root != null ? root.getConfigurationSection(from.name().toLowerCase(Locale.ROOT)) : null;
            for (ArmorClass to : ArmorClass.values()) {
                if (from == to) {
                    continue;
                }
                double value = fromSection != null ? fromSection.getDouble(to.name().toLowerCase(Locale.ROOT), 1.0) : 1.0;
                row.put(to, value);
            }
            factors.put(from, row);
        }
        return new ArmorPenetrationConversionSettings(factors);
    }

    private static Set<String> toSet(List<String> values) {
        return new LinkedHashSet<>(values);
    }
}
