package eu.purrtech.purrtechPVE.item;

/**
 * A weapon's ability to punch through one of the 3 armor classes: subtracted from whatever
 * resistance the defender's {@code ArmorClass}-wide {@code armor_class_profile} bonus contributes
 * (not their own per-item {@link TypeModifier}s - see {@code EquipmentResolver.resolveResistance})
 * before damage is computed for that hit. Nothing is ever removed from the defender's inventory or
 * their item's own stats - this only affects the one hit's math.
 *
 * <p>{@code mode} picks how {@code amount} is applied - {@link DamageMode#FLAT}: subtract
 * {@code amount} points straight off the resist value (e.g. 20% resist with 10 flat penetration
 * becomes 10%, regardless of how big the resist was to start with - "punches through 10 units of
 * armor"). {@link DamageMode#PERCENT_OF_TOTAL}: subtract {@code amount} percent OF the resist's
 * own current value instead (e.g. 20% resist with 50% penetration becomes 10%, since it cuts the
 * resist itself in half) - scales with however much resist the defender actually has, unlike flat.
 *
 * <p>WIELDED-only by nature (only makes sense on the attacking weapon), so
 * unlike {@link DamageContribution}/{@link TypeModifier} there's no {@code
 * ModifierContext} here. A stat like any other on the item, so it's
 * versioned/snapshotted the same way (see {@code TemplateSnapshot}).
 *
 * @param visible whether this entry gets its own line in the rendered lore - purely cosmetic,
 *                the penetration always applies at combat time regardless.
 */
public record ArmorPenetration(ArmorClass armorClass, double amount, DamageMode mode, boolean visible) {
}
