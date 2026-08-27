package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies an open armor-class benefits GUI and which of the 3 classes it's showing, so the listener can route clicks and re-render in place. */
public final class ArmorClassHolder implements InventoryHolder {

    private ArmorClass armorClass;
    private Inventory inventory;

    public ArmorClassHolder(ArmorClass armorClass) {
        this.armorClass = armorClass;
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
}
