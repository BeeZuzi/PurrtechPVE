package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open armor-class benefits GUI and which of the 3 classes it's showing, so the listener can route clicks and re-render in place. */
public final class ArmorClassHolder implements InventoryHolder {

    private ArmorClass armorClass;
    private Inventory inventory;
    // Null when opened as a root screen (/pve armorclass menu) - no back button is shown in that
    // case, only close. Set when opened from ItemEditorMenu's ARMOR_CLASS tab, so a back button can
    // return to that exact template/tab/list-page instead of leaving close as the only way out.
    private final String returnTemplateKey;
    private final ItemEditorTab returnTab;
    private final int returnListPage;

    public ArmorClassHolder(ArmorClass armorClass, String returnTemplateKey, ItemEditorTab returnTab, int returnListPage) {
        this.armorClass = armorClass;
        this.returnTemplateKey = returnTemplateKey;
        this.returnTab = returnTab;
        this.returnListPage = returnListPage;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public ArmorClass armorClass() {
        return armorClass;
    }

    public void setArmorClass(ArmorClass armorClass) {
        this.armorClass = armorClass;
    }

    public String returnTemplateKey() {
        return returnTemplateKey;
    }

    public ItemEditorTab returnTab() {
        return returnTab;
    }

    public int returnListPage() {
        return returnListPage;
    }
}
