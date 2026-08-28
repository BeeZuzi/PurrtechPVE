package eu.purrtech.purrtechPVE.item;

import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.List;
import java.util.Locale;

/**
 * Parses/validates the free-text {@code slot} an admin types (in a command or the item editor's
 * chat prompt) into the canonical form {@link AttributeModifierEntry#slot()} stores - either a
 * vanilla {@link EquipmentSlotGroup} name (mainhand/offhand/hand/feet/legs/chest/head/armor/body/
 * any/saddle, case-insensitive input, canonical lowercase) or one of this server's configured
 * accessory/trinket slot names (case-insensitive input, canonical uppercase - matching how {@code
 * AccessorySettings}/{@code ItemTemplate.allowedSlots} already store those). Shared between {@code
 * PveCommand} and {@code ItemEditorMenu} so both accept/display the exact same set of valid slots.
 */
public final class AttributeSlots {

    private AttributeSlots() {
    }

    /** {@code null} on anything that's neither a real EquipmentSlotGroup name nor one of {@code trinketSlots}. */
    public static String parse(String raw, List<String> trinketSlots) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw.trim().toLowerCase(Locale.ROOT));
        if (group != null) {
            return group.toString();
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return trinketSlots.contains(upper) ? upper : null;
    }
}
