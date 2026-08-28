package eu.purrtech.purrtechPVE.trinket;

import eu.purrtech.purrtechPVE.config.AccessorySettings;
import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Opens a chest GUI with one slot per configured virtual accessory slot, backed by {@link AccessoryRepository}. */
public final class AccessoryMenu {

    private AccessoryMenu() {
    }

    public static void open(Player player, Messages messages, AccessorySettings settings, AccessoryRepository repository) {
        List<String> slotNames = settings.slots();
        int size = Math.max(9, ((slotNames.size() + 8) / 9) * 9);

        AccessoryInventoryHolder holder = new AccessoryInventoryHolder(player.getUniqueId(), slotNames);
        Inventory inventory = Bukkit.createInventory(holder, size, messages.render(player.locale(), "gui.accessory.title"));
        holder.setInventory(inventory);

        Map<String, ItemStack> saved = repository.findAll(player.getUniqueId());
        for (int i = 0; i < slotNames.size(); i++) {
            ItemStack stack = saved.get(slotNames.get(i));
            if (stack != null) {
                inventory.setItem(i, stack);
            }
        }
        for (int i = slotNames.size(); i < size; i++) {
            inventory.setItem(i, lockedFiller());
        }

        player.openInventory(inventory);
    }

    private static ItemStack lockedFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        return filler;
    }
}
