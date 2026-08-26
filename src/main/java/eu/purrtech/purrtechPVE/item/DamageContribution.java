package eu.purrtech.purrtechPVE.item;

/**
 * How much of a given damage type a template contributes - either as a
 * weapon dealing it ({@link ModifierContext#WIELDED}) or as a passive bonus
 * while worn ({@link ModifierContext#WORN}, e.g. a trinket adding fire
 * damage to every hit).
 */
public record DamageContribution(
        String damageTypeKey,
        double amount,
        DamageMode mode,
        ModifierContext context
) {
}
