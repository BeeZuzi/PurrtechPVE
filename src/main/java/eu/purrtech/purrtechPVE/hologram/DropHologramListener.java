package eu.purrtech.purrtechPVE.hologram;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Spawns a floating {@link TextDisplay} passenger showing an item's rendered name above every
 * world-dropped {@link Item} entity stamped by {@link ItemRenderer} (mob drops, {@code /pve item
 * give}, ...) - gated by the server-wide {@code drop-hologram.enabled} switch ({@link
 * #refresh(boolean)}) and each template's own override in {@code
 * eu.purrtech.purrtechPVE.db.ItemHologramRepository}. Riding as a passenger means the display
 * moves/despawns together with the item entity with zero extra bookkeeping in the common case;
 * the pickup/merge handlers below only exist for the cases where the item entity is removed but
 * Bukkit doesn't automatically clean up its passengers first.
 */
public final class DropHologramListener implements Listener {

    private final PurrtechPVE plugin;
    private boolean globallyEnabled;

    public DropHologramListener(PurrtechPVE plugin, boolean globallyEnabled) {
        this.plugin = plugin;
        this.globallyEnabled = globallyEnabled;
    }

    /** See {@code PurrtechPVE.reload()} - lets {@code drop-hologram.enabled} take effect without a restart. */
    public void refresh(boolean globallyEnabled) {
        this.globallyEnabled = globallyEnabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!globallyEnabled) {
            return;
        }
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        stampedTemplateOf(stack).ifPresent(template -> {
            if (plugin.getItemHologramRepository().isDisabled(template.id())) {
                return;
            }
            TextDisplay display = item.getWorld().spawn(item.getLocation(), TextDisplay.class, d -> {
                d.text(stack.getItemMeta().displayName());
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setPersistent(false);
            });
            item.addPassenger(display);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        removeHologram(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        removeHologram(event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        // Only the merged-away source entity needs cleanup - the surviving target's own hologram
        // (if any) is still valid and untouched by this event.
        removeHologram(event.getEntity());
    }

    private void removeHologram(Item item) {
        for (Entity passenger : List.copyOf(item.getPassengers())) {
            if (passenger instanceof TextDisplay) {
                item.removePassenger(passenger);
                passenger.remove();
            }
        }
    }

    private Optional<ItemTemplate> stampedTemplateOf(ItemStack stack) {
        return plugin.getItemRenderer().readStamp(stack)
                .flatMap(stamp -> plugin.getItemTemplateService().findByKey(stamp.templateKey()));
    }
}
