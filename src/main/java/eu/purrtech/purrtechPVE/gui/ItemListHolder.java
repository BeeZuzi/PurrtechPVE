package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open item-management list GUI and which page it's showing, so the listener can route clicks and re-render in place. */
public final class ItemListHolder implements InventoryHolder {

    private int page;
    private Inventory inventory;

    public ItemListHolder(int page) {
        this.page = page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
