package eu.purrtech.purrtechPVE.item;

/**
 * How much of a given damage type a template contributes - either as a
 * weapon dealing it ({@link ModifierContext#WIELDED}) or as a passive bonus
 * while worn ({@link ModifierContext#WORN}, e.g. a trinket adding fire
 * damage to every hit).
 *
 * @param visible whether this contribution gets its own line in the rendered lore - purely
 *                cosmetic, the combat math in {@code EquipmentResolver} always applies the full
 *                amount regardless. Lets an admin hide a stat line without disabling the stat.
 */
public record DamageContribution(
        String damageTypeKey,
        double amount,
        DamageMode mode,
        ModifierContext context,
        boolean visible
) {
}
