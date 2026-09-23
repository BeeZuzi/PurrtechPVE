package eu.purrtech.purrtechPVE.listener;

import eu.purrtech.purrtechPVE.item.ItemSyncService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The lazy half of item sync - {@link ItemSyncService#resyncAllOnlinePlayers()} only reaches
 * connected players' own inventories, so everything else is caught up the moment it's next
 * touched: an offline player's items on login, a chest/shulker/barrel when opened, and a dropped
 * stack when picked up.
 */
public final class ItemSyncJoinListener implements Listener {

    private final ItemSyncService itemSyncService;

    public ItemSyncJoinListener(ItemSyncService itemSyncService) {
        this.itemSyncService = itemSyncService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        itemSyncService.resyncPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        itemSyncService.resyncInventory(event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        itemSyncService.resyncStack(event.getItem().getItemStack())
                .ifPresent(updated -> event.getItem().setItemStack(updated));
    }
}
