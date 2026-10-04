package eu.purrtech.purrtechPVE.item;

import java.util.Map;

/**
 * What one circulating item (identified by the instance id stamped on it) has been upgraded by, as
 * non-negative DELTAS on top of whatever its template version gives - so editing or syncing the
 * template never touches them. Maps are keyed by:
 * <ul>
 *   <li>{@code damage}: {@link #damageKey} ({@code "<damageTypeKey>|<ModifierContext>"});</li>
 *   <li>{@code resist}: the damage type key;</li>
 *   <li>{@code effects}: a {@link UpgradeEffect} name.</li>
 * </ul>
 */
public record ItemUpgrades(Map<String, Double> damage, Map<String, Double> resist, Map<String, Double> effects) {

    public static final ItemUpgrades NONE = new ItemUpgrades(Map.of(), Map.of(), Map.of());

    public boolean isEmpty() {
        return damage.isEmpty() && resist.isEmpty() && effects.isEmpty();
    }

    public static String damageKey(String damageTypeKey, ModifierContext context) {
        return damageTypeKey + "|" + context.name();
    }

    public Map<String, Double> of(UpgradeCategory category) {
        return switch (category) {
            case DAMAGE -> damage;
            case RESIST -> resist;
            case EFFECT -> effects;
        };
    }
}
