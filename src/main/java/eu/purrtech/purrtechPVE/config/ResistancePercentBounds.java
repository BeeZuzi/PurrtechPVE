package eu.purrtech.purrtechPVE.config;

/**
 * {@code combat.resistance-percent-min}/{@code -max} - the hard floor/ceiling every fully-resolved
 * damage-type resistance percent ({@code EquipmentResolver.resolveResistance}'s output, after
 * summing armor class profile + item modifiers + set bonuses + mob profile) is clamped into,
 * before it's used anywhere - weapon hits, bleed ticks, and the poison/wither/fire/frozen vanilla-
 * effect linkage alike, since they all read the same resolved map. Positive = resistance, negative
 * = weakness, so {@code maxPercent} caps how resistant and {@code minPercent} caps how weak an
 * entity can ever be to any one type, no matter how many stacking sources pushed it past that
 * line. Defaults match the pre-existing hardcoded weapon-hit-only safety net ({@code
 * DamagePipeline.MIN_RESIST_PERCENT}/{@code MAX_RESIST_PERCENT}) so behavior is unchanged until an
 * admin edits config.yml - {@code DamagePipeline}'s own -200/95 clamp still applies afterward as
 * an independent, non-configurable safety net specifically for weapon-hit resolution, so setting
 * {@code maxPercent} above 95 there won't actually reach full effect on a weapon hit (bleed/vanilla-
 * effect linkage have no such second clamp, so those follow this config's value exactly).
 */
public record ResistancePercentBounds(double minPercent, double maxPercent) {

    public static ResistancePercentBounds defaults() {
        return new ResistancePercentBounds(-200.0, 95.0);
    }
}
