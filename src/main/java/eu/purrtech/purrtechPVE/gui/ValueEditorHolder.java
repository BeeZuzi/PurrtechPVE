package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies an open {@link ValueEditorMenu} - which template, which kind of stat, and (for
 * every kind but the BLEED/CRIT singletons) which specific entry within it - so the listener can
 * route +/-/visibility clicks and re-render in place.
 */
public final class ValueEditorHolder implements InventoryHolder {

    private final String templateKey;
    private final ValueEditorKind kind;
    private final String entryId;
    private Inventory inventory;

    public ValueEditorHolder(String templateKey, ValueEditorKind kind, String entryId) {
        this.templateKey = templateKey;
        this.kind = kind;
        this.entryId = entryId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public String templateKey() {
        return templateKey;
    }

    public ValueEditorKind kind() {
        return kind;
    }

    public String entryId() {
        return entryId;
    }
}
