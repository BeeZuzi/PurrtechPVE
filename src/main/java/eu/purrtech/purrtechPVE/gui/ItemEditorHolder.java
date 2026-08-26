package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open item editor GUI and which template/tab it's showing, so the listener can route clicks and re-render in place. */
public final class ItemEditorHolder implements InventoryHolder {

    private final String templateKey;
    private ItemEditorTab tab;
    private Inventory inventory;

    public ItemEditorHolder(String templateKey, ItemEditorTab tab) {
        this.templateKey = templateKey;
        this.tab = tab;
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

    public ItemEditorTab tab() {
        return tab;
    }

    public void setTab(ItemEditorTab tab) {
        this.tab = tab;
    }
}
