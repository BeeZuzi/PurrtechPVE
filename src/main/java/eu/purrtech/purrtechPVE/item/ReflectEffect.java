package eu.purrtech.purrtechPVE.item;

/**
 * An item's chance to reflect part of the damage its bearer takes back onto
 * the attacker - {@code reflectPercent} of the fully-resolved incoming
 * damage. Unlike {@link BleedEffect}/{@link CriticalEffect}/{@link
 * StunEffect}, which only ever roll off the attacker's wielded weapon, this
 * rolls off the DEFENDER's side and applies whether the item carrying it is
 * held in hand or worn as armor (including trinkets) - see {@code
 * EquipmentResolver#resolveReflectEffects} and {@code CombatDamageListener}
 * for exactly how it's resolved/applied.
 *
 * <p>A stat like a damage contribution, so it's versioned/snapshotted (see
 * {@code TemplateSnapshot}), same treatment as {@code StunEffect}.
 *
 * @param visible whether the combined "X% chance, Y% reflected" line shows in the rendered lore -
 *                purely cosmetic, the reflect always rolls at combat time regardless.
 */
public record ReflectEffect(double chancePercent, double reflectPercent, boolean visible) {

    /**
     * Both chance and reflect percent have to be actually set (not just a DB row existing) for
     * this to actually roll in combat - see {@code CombatDamageListener}. Same reasoning as
     * {@link StunEffect#isComplete()}.
     */
    public boolean isComplete() {
        return chancePercent > 0 && reflectPercent > 0;
    }
}
