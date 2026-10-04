package eu.purrtech.purrtechPVE.listener;

import eu.purrtech.purrtechPVE.item.ItemRenderer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * A creative-mode middle-click clone of an item keeps its instance id, so the copy shares the
 * original's upgrades. That is allowed on purpose - the copy is left exactly as cloned - but it is
 * marked with the {@code duplicated} flag (see {@link ItemRenderer#duplicatedPdc()}) so it can be
 * recognised later. Only this clone path is detected; other ways of duplicating an item (a dupe
 * exploit, another plugin copying stacks) are not.
 */
public final class ItemDuplicationListener implements Listener {

    private final ItemRenderer renderer;

    public ItemDuplicationListener(ItemRenderer renderer) {
        this.renderer = renderer;
    }

    @EventHandler
    public void onCreativeClone(InventoryCreativeEvent event) {
        if (event.getAction() != InventoryAction.CLONE_STACK) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (renderer.readInstanceId(cursor).isEmpty()) {
            return;
        }
        ItemStack marked = cursor.clone();
        ItemMeta meta = marked.getItemMeta();
        meta.getPersistentDataContainer().set(renderer.duplicatedPdc(), PersistentDataType.BYTE, (byte) 1);
        marked.setItemMeta(meta);
        event.setCursor(marked);
    }
}
