package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open item editor GUI and which template/tab it's showing, so the listener can route clicks and re-render in place. */
public final class ItemEditorHolder implements InventoryHolder {

    private final String templateKey;
    private ItemEditorTab tab;
    private Inventory inventory;
    // Sub-screen shared by every tab that lists "things you can add" (DAMAGE, RESIST, MOBS):
    // instead of always listing every possible option, those tabs show only the already-configured
    // ones plus an "Add" button - clicking it flips this flag and the same tab re-renders as a
    // picker of the not-yet-configured options instead. One flag is enough since only one tab is
    // ever visible at a time and switchTab() always resets it.
    private boolean pickerOpen;

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

    public boolean isPickerOpen() {
        return pickerOpen;
    }

    public void setPickerOpen(boolean pickerOpen) {
        this.pickerOpen = pickerOpen;
    }
}
