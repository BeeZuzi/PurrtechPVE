package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies an open {@link ValueEditorMenu} - which template, which kind of stat, and (for
 * every kind but the BLEED/CRIT singletons) which specific entry within it - so the listener can
 * route +/-/visibility clicks and re-render in place.
 */
public final class ValueEditorHolder implements InventoryHolder {

    private final String templateKey;
    private final ValueEditorKind kind;
    private final String entryId;
    // ItemListMenu page the originating ItemEditorMenu was opened from - carried through so
    // BACK_SLOT's return to ItemEditorMenu can hand it straight back, keeping BACK_TO_LIST_SLOT
    // over there accurate too.
    private final int listPage;
    private Inventory inventory;

    public ValueEditorHolder(String templateKey, ValueEditorKind kind, String entryId, int listPage) {
        this.templateKey = templateKey;
        this.kind = kind;
        this.entryId = entryId;
        this.listPage = listPage;
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

    public ValueEditorKind kind() {
        return kind;
    }

    public String entryId() {
        return entryId;
    }

    public int listPage() {
        return listPage;
    }
}
