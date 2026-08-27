package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open set editor GUI and which set/tab it's showing, so the listener can route clicks and re-render in place. */
public final class SetEditorHolder implements InventoryHolder {

    private final String setKey;
    private SetEditorTab tab;
    private Inventory inventory;

    public SetEditorHolder(String setKey, SetEditorTab tab) {
        this.setKey = setKey;
        this.tab = tab;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public String setKey() {
        return setKey;
    }

    public SetEditorTab tab() {
        return tab;
    }

    public void setTab(SetEditorTab tab) {
        this.tab = tab;
    }
}
