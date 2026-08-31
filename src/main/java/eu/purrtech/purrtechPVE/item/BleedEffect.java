package eu.purrtech.purrtechPVE.item;

/**
 * A weapon's chance to inflict bleeding on a hit, how long that bleed
 * lasts, and how much total damage it deals over that duration -
 * {@code damageAmount}/{@code mode} work exactly like a normal {@link
 * DamageContribution}'s {@code amount}/{@code mode} (a flat number, or a
 * percent of the raw hit that triggered it), spread evenly across however
 * many ticks fit the "bleed" {@code DamageType}'s own {@code
 * dotPeriodTicks} - see {@code CombatDamageListener} for exactly where that
 * split happens. Before this field existed, every weapon's bleed dealt the
 * same fraction of the raw hit ({@code dotTickPercent}, a single global
 * number on the "bleed" {@code DamageType} itself) - now each weapon sets
 * its own damage, same as any other stat.
 *
 * <p>Because of this, {@code "bleed"} is no longer a valid {@link
 * DamageContribution} damage type key (see {@code
 * ItemTemplateService.setDamageContribution}) - a weapon's bleed damage is
 * configured here instead, not as a normal wielded/worn contribution.
 * {@code "bleed"} remains a perfectly normal {@link TypeModifier} key
 * though - resistance/weakness to it still works exactly as before, read at
 * each {@code BleedManager} tick.
 *
 * <p>A stat like a damage contribution, so it's versioned/snapshotted (see
 * {@code TemplateSnapshot}), unlike {@code ArmorClass} which is a live
 * classification.
 *
 * @param visible whether the combined "X% chance to bleed for Ys" line shows in the rendered
 *                lore - purely cosmetic, the bleed always rolls at combat time regardless.
 */
public record BleedEffect(double chancePercent, double durationSeconds, double damageAmount, DamageMode mode, boolean visible) {

    /**
     * All 3 of chance/duration/damage have to be actually set (not just a DB row existing) for
     * this to actually roll in combat - see {@code CombatDamageListener}. Lets an admin build
     * this up one field at a time (e.g. via {@code ValueEditorMenu}'s +/- buttons) without a
     * half-finished bleed accidentally firing with a 0 duration or 0 damage.
     */
    public boolean isComplete() {
        return chancePercent > 0 && durationSeconds > 0 && damageAmount > 0;
    }
}
