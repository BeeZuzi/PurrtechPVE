package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies an open set editor GUI and which set/tab it's showing, so the listener can route clicks
 * and re-render in place. The tier screens also remember which threshold (piece count) they are on and,
 * on the type picker, whether it is adding damage or a resistance.
 */
public final class SetEditorHolder implements InventoryHolder {

    private final String setKey;
    private SetEditorTab tab;
    private int pieceCount;
    private boolean pickResist;
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

    /** The threshold the {@link SetEditorTab#NEW_TIER}/{@link SetEditorTab#TIER}/{@link SetEditorTab#PICK_TYPE} screens are on. */
    public int pieceCount() {
        return pieceCount;
    }

    public void setPieceCount(int pieceCount) {
        this.pieceCount = pieceCount;
    }

    /** On {@link SetEditorTab#PICK_TYPE}: {@code true} adds a resistance, {@code false} adds damage. */
    public boolean pickResist() {
        return pickResist;
    }

    public void setPickResist(boolean pickResist) {
        this.pickResist = pickResist;
    }
}
