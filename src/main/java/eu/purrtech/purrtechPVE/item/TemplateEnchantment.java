package eu.purrtech.purrtechPVE.item;

/**
 * An enchantment applied to a template, by key (e.g. {@code "minecraft:sharpness"} - whatever
 * {@link org.bukkit.enchantments.Enchantment#getKey()} returns) so this doesn't need to store a
 * live {@code Enchantment} reference. Rendered with {@code ignoreLevelRestriction=true} (see
 * {@code ItemRenderer}), so levels aren't capped at vanilla's max - these are admin-defined
 * custom items, not something obtained through vanilla enchanting.
 */
public record TemplateEnchantment(String enchantmentKey, int level) {
}
