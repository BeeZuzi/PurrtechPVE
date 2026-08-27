package eu.purrtech.purrtechPVE.itemset;

/** Resistance (positive) or weakness (negative) to {@code damageTypeKey}, granted once a wearer has at least {@code pieceCount} pieces of a set equipped. */
public record SetThresholdModifier(
        int pieceCount,
        String damageTypeKey,
        double percent
) {
}
