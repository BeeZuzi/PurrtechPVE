package eu.purrtech.purrtechPVE.item;

/**
 * Which of the 3 fixed armor weight classes a template belongs to - a live/global
 * classification (like {@code allowedSlots}/trinket, see {@code ItemTemplateService.
 * setArmorClass}), not a per-version pinned stat: the actual resistance/weakness each
 * class grants lives separately in {@code armor_class_profile} (see {@code
 * ArmorClassProfileRepository}, admin-editable via {@code /pve armorclass} and {@code
 * gui/ArmorClassMenu}) and applies live to every piece tagged with that class, the same
 * way {@code mob_damage_profile} applies live to every mob of a given MythicMobs type.
 *
 * <p>{@code MEDIUM} is meant for pieces that still use plain vanilla armor materials
 * (so they keep vanilla's own armor/toughness values and look like normal Minecraft
 * armor when worn); {@code LIGHT}/{@code HEAVY} are meant for custom-looking pieces
 * (a non-armor base material + custom model data, resource-pack rendered) - this
 * plugin doesn't enforce that distinction, it's just how the 3 classes are intended to
 * be used when building items.
 */
public enum ArmorClass {
    LIGHT,
    MEDIUM,
    HEAVY
}
