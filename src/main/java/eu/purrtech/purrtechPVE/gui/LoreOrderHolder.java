package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open {@link LoreOrderMenu} - which template, and which {@link ItemEditorTab} "Back" returns to. */
public final class LoreOrderHolder implements InventoryHolder {

    private final String templateKey;
    private final ItemEditorTab returnTab;
    private Inventory inventory;

    public LoreOrderHolder(String templateKey, ItemEditorTab returnTab) {
        this.templateKey = templateKey;
        this.returnTab = returnTab;
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

    public ItemEditorTab returnTab() {
        return returnTab;
    }
}
