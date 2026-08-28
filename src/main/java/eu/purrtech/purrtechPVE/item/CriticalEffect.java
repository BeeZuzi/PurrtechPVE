package eu.purrtech.purrtechPVE.item;

/**
 * A weapon's chance to land a critical hit, and how much extra damage that
 * hit deals - {@code bonusDamagePercent} on top of what a normal hit would
 * have dealt (e.g. {@code 50} = 50% more damage, final total multiplied by
 * {@code 1 + bonusDamagePercent/100}). Rolled and applied in {@code
 * CombatDamageListener} after the normal damage-type/resistance pipeline,
 * against the fully-resolved total - same convention as vanilla Minecraft's
 * own sword crit.
 *
 * <p>A stat like a damage contribution, so it's versioned/snapshotted (see
 * {@code TemplateSnapshot}), unlike {@code ArmorClass} which is a live
 * classification.
 *
 * @param visible whether the combined "X% crit chance, +Y% damage" line shows in the rendered
 *                lore - purely cosmetic, the crit always rolls at combat time regardless.
 */
public record CriticalEffect(double chancePercent, double bonusDamagePercent, boolean visible) {
}
