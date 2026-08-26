package eu.purrtech.purrtechPVE.item;

/**
 * Resistance (positive percent) or weakness (negative percent) to a damage
 * type, granted while this template is worn as armor/trinket. Applies
 * regardless of {@link ModifierContext} - there's no "wielded resistance".
 */
public record TypeModifier(
        String damageTypeKey,
        double percent
) {
}
