package eu.purrtech.purrtechPVE.config;

import eu.purrtech.purrtechPVE.item.ArmorClass;

import java.util.EnumMap;
import java.util.Map;

/**
 * {@code armor-penetration-conversion} - how much of a weapon's {@code ArmorPenetration} against
 * one {@link ArmorClass} also counts against a defender wearing a differently-classed armor
 * (see {@code EquipmentResolver.applyArmorPenetration}/{@code resolveArmorPoints}). Without this,
 * penetration only ever reduced the exact same class it was configured for, so e.g. a light-only
 * weapon did nothing at all against a heavy-armored defender. Defaults to fully effective (1.0)
 * across every pair, matching the pre-existing same-class behavior until an admin tunes it down.
 */
public record ArmorPenetrationConversionSettings(Map<ArmorClass, Map<ArmorClass, Double>> factors) {

    public static ArmorPenetrationConversionSettings defaults() {
        Map<ArmorClass, Map<ArmorClass, Double>> map = new EnumMap<>(ArmorClass.class);
        for (ArmorClass from : ArmorClass.values()) {
            Map<ArmorClass, Double> row = new EnumMap<>(ArmorClass.class);
            for (ArmorClass to : ArmorClass.values()) {
                if (from != to) {
                    row.put(to, 1.0);
                }
            }
            map.put(from, row);
        }
        return new ArmorPenetrationConversionSettings(map);
    }

    public double factor(ArmorClass from, ArmorClass to) {
        if (from == to) {
            return 1.0;
        }
        return factors.getOrDefault(from, Map.of()).getOrDefault(to, 1.0);
    }
}
