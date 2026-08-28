package eu.purrtech.purrtechPVE.item;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/**
 * A real vanilla Minecraft {@link Attribute} bonus (attack damage, max
 * health, movement speed, ...) this template grants while equipped in one
 * specific {@code slot} - unlike {@link DamageContribution}/{@link
 * TypeModifier}, which are this plugin's own virtual combat-math system
 * resolved fresh every hit, this is baked straight into the real Bukkit
 * attribute system, so it works exactly like any vanilla enchanted/
 * attribute-modifier item and needs zero custom combat code.
 *
 * @param slot either a name {@link org.bukkit.inventory.EquipmentSlotGroup#getByName(String)}
 *             resolves (mainhand/offhand/hand/feet/legs/chest/head/armor/body/any/saddle) - baked
 *             directly into the rendered item's {@code ItemMeta} at render time, exactly like a
 *             vanilla attribute-modifier item, so it applies/removes automatically the moment the
 *             item is equipped/unequipped in a REAL equipment slot - or one of this server's
 *             configured accessory/trinket slot names (see {@code AccessorySettings}), which
 *             aren't real equipment slots vanilla can watch: those are applied/removed by {@code
 *             TrinketAttributeListener} instead, driven by the accessory GUI closing and by
 *             player join.
 */
public record AttributeModifierEntry(
        Attribute attribute,
        double amount,
        AttributeModifier.Operation operation,
        String slot
) {
}
