package eu.purrtech.purrtechPVE.item;

/**
 * Whether a damage/attribute contribution applies while the item is the
 * held weapon ({@code WIELDED}, used in an attack) or while it's worn as
 * armor/trinket ({@code WORN}, a passive bonus).
 */
public enum ModifierContext {
    WIELDED,
    WORN
}
