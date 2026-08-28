package eu.purrtech.purrtechPVE.item;

/**
 * A weapon's ability to punch through one of the 3 armor classes: {@code
 * amount} percentage points are subtracted from whatever resistance the
 * defender's {@code ArmorClass}-wide {@code armor_class_profile} bonus
 * contributes (not their own per-item {@link TypeModifier}s - see {@code
 * EquipmentResolver.resolveResistance}) before damage is computed for that
 * hit. Nothing is ever removed from the defender's inventory or their
 * item's own stats - this only affects the one hit's math.
 *
 * <p>WIELDED-only by nature (only makes sense on the attacking weapon), so
 * unlike {@link DamageContribution}/{@link TypeModifier} there's no {@code
 * ModifierContext} here. A stat like any other on the item, so it's
 * versioned/snapshotted the same way (see {@code TemplateSnapshot}).
 *
 * @param visible whether this entry gets its own line in the rendered lore - purely cosmetic,
 *                the penetration always applies at combat time regardless.
 */
public record ArmorPenetration(ArmorClass armorClass, double amount, boolean visible) {
}
