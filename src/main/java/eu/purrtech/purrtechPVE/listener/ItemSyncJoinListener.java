package eu.purrtech.purrtechPVE.listener;

import eu.purrtech.purrtechPVE.item.ItemSyncService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Catches an offline player's items up to their templates' last explicitly
 * pushed ({@code syncedVersion}) state on login - the offline half of item
 * sync, since {@link ItemSyncService#resyncAllOnlinePlayers()} can only
 * reach players who are already connected.
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
}
