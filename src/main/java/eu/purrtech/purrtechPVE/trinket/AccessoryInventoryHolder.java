package eu.purrtech.purrtechPVE.trinket;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Identifies an open accessory menu so {@link AccessoryMenuListener} can
 * tell it apart from any other inventory a player might have open, and
 * knows which of its slots are "real" (index < {@code slotNames.size()})
 * vs. locked filler.
 */
public final class AccessoryInventoryHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final List<String> slotNames;
    private Inventory inventory;

    public AccessoryInventoryHolder(UUID playerUuid, List<String> slotNames) {
        this.playerUuid = playerUuid;
        this.slotNames = slotNames;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public List<String> slotNames() {
        return slotNames;
    }
}
