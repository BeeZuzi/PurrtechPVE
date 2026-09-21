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
    // MOBS tab pagination - separate pages for the assigned-mobs list and the picker, since they're
    // different-length lists and switching between them (or tabs) shouldn't carry a stale page
    // number over onto the other list. Reset to 0 by switchTab()/entering the picker.
    private int mobsPage;
    private int mobsPickerPage;
    // MOBS tab's extra sub-screens, both null/unset outside MOBS: mobsPendingMobType is set right
    // after picking a not-yet-assigned mob type in the picker, showing an "Equipment or Drop?"
    // choice before anything's actually written to either mob_equipment or mob_drop.
    // mobsDropConfigMobType is set when viewing/editing an existing drop assignment's amount/
    // chance% (a mob already routed to mob_drop). Only one of the two, or neither, is ever set at
    // once - see ItemEditorMenu's MOBS tab render/click handling.
    private String mobsPendingMobType;
    private String mobsDropConfigMobType;

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

    public int mobsPage() {
        return mobsPage;
    }

    public void setMobsPage(int mobsPage) {
        this.mobsPage = mobsPage;
    }

    public int mobsPickerPage() {
        return mobsPickerPage;
    }

    public void setMobsPickerPage(int mobsPickerPage) {
        this.mobsPickerPage = mobsPickerPage;
    }

    public String mobsPendingMobType() {
        return mobsPendingMobType;
    }

    public void setMobsPendingMobType(String mobsPendingMobType) {
        this.mobsPendingMobType = mobsPendingMobType;
    }

    public String mobsDropConfigMobType() {
        return mobsDropConfigMobType;
    }

    public void setMobsDropConfigMobType(String mobsDropConfigMobType) {
        this.mobsDropConfigMobType = mobsDropConfigMobType;
    }
}
