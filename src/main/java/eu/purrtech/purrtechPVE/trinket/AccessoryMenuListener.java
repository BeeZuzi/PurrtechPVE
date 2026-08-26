package eu.purrtech.purrtechPVE.trinket;

import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the accessory GUI to simple direct single-slot placement: shift-
 * clicks and any interaction with the locked filler slots are rejected, and
 * the real slots' contents are persisted on close. No quick-move support in
 * this v1 - safer against dupe/placement edge cases than trying to handle
 * every InventoryAction case for a first pass.
 */
public final class AccessoryMenuListener implements Listener {

    private final AccessoryRepository repository;

    public AccessoryMenuListener(AccessoryRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < event.getInventory().getSize() && rawSlot >= holder.slotNames().size()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot >= holder.slotNames().size()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }
        Map<String, ItemStack> slots = new HashMap<>();
        List<String> names = holder.slotNames();
        for (int i = 0; i < names.size(); i++) {
            slots.put(names.get(i), event.getInventory().getItem(i));
        }
        repository.saveAll(holder.playerUuid(), slots);
    }
}
