package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies the open list of item sets (see {@link SetListMenu}) and which page it shows, so the listener can route clicks. */
public final class SetListHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    public SetListHolder(int page) {
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
}
