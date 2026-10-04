package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.item.UpgradeCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies one screen of {@link UpgradeMenu} so {@link ItemEditorListener} can route its clicks.
 * Remembers which hotbar/inventory slot the upgraded item was in and which template it was, so every
 * click can re-check that slot still holds the same item before changing anything.
 */
public final class UpgradeMenuHolder implements InventoryHolder {

    public enum View {
        CATEGORIES, ENTRIES
    }

    private final View view;
    private final String templateKey;
    private final int itemSlot;
    private final UpgradeCategory category;
    private Inventory inventory;

    /** @param category the category being shown; {@code null} on the category screen */
    public UpgradeMenuHolder(View view, String templateKey, int itemSlot, UpgradeCategory category) {
        this.view = view;
        this.templateKey = templateKey;
        this.itemSlot = itemSlot;
        this.category = category;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public View view() {
        return view;
    }

    public String templateKey() {
        return templateKey;
    }

    public int itemSlot() {
        return itemSlot;
    }

    public UpgradeCategory category() {
        return category;
    }
}
