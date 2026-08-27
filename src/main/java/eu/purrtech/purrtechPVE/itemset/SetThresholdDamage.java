package eu.purrtech.purrtechPVE.itemset;

import eu.purrtech.purrtechPVE.item.DamageMode;

/** Bonus damage of {@code damageTypeKey} granted once a wearer has at least {@code pieceCount} pieces of a set equipped. */
public record SetThresholdDamage(
        int pieceCount,
        String damageTypeKey,
        double amount,
        DamageMode mode
) {
}
