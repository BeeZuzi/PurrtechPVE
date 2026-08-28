package eu.purrtech.purrtechPVE.item;

/**
 * A weapon's chance to inflict bleeding on a hit, and how long that bleed
 * lasts. On a successful roll (see {@code EquipmentResolver.resolveBleedEffect}/
 * {@code CombatDamageListener}), the defender starts ticking "bleed"-type
 * damage (see {@code combat.BleedManager}) for {@code durationSeconds},
 * at whatever cadence/per-tick fraction the "bleed" {@code DamageType}
 * itself declares ({@code dotPeriodTicks}/{@code dotTickPercent}) - this
 * record only carries the two things that vary per weapon, not the DOT
 * mechanics themselves, which are a property of the damage type.
 *
 * <p>A stat like a damage contribution, so it's versioned/snapshotted (see
 * {@code TemplateSnapshot}), unlike {@code ArmorClass} which is a live
 * classification.
 *
 * @param visible whether the combined "X% chance to bleed for Ys" line shows in the rendered
 *                lore - purely cosmetic, the bleed always rolls at combat time regardless.
 */
public record BleedEffect(double chancePercent, double durationSeconds, boolean visible) {
}
