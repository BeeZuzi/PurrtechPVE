package eu.purrtech.purrtechPVE.item;

/**
 * A weapon's chance to stun the defender on a hit, and how long the stun
 * lasts. While stunned, the defender cannot attack, is slowed, and is
 * blinded - see {@code CombatDamageListener} for exactly how each of those
 * three is applied and {@code EquipmentResolver#resolveStunResistPercent}
 * for how a defender's worn armor can reduce the effective chance below.
 *
 * <p>A stat like a damage contribution, so it's versioned/snapshotted (see
 * {@code TemplateSnapshot}), unlike the armor-side stun resistance which is
 * a live, unversioned classification (same treatment as {@code
 * ItemTemplate#armorAmount}).
 *
 * @param visible whether the combined "X% stun chance, Y s duration" line shows in the rendered
 *                lore - purely cosmetic, the stun always rolls at combat time regardless.
 */
public record StunEffect(double chancePercent, double durationSeconds, boolean visible) {

    /**
     * Both chance and duration have to be actually set (not just a DB row existing) for this to
     * actually roll in combat - see {@code CombatDamageListener}. Same reasoning as {@link
     * CriticalEffect#isComplete()}.
     */
    public boolean isComplete() {
        return chancePercent > 0 && durationSeconds > 0;
    }
}
