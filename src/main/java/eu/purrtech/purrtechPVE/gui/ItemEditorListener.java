package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes clicks in an open {@link ItemEditorHolder} inventory to {@link
 * ItemEditorMenu}, and runs a one-shot chat-based text-capture flow for the
 * numeric fields the GUI needs (amount/mode/context, percent) - simpler and
 * more robust than an anvil-GUI text prompt (no XP/item-consumption quirks,
 * no per-version anvil-result edge cases) for the multi-field inputs this
 * editor needs, at the cost of a chat round-trip instead of staying fully
 * in-menu. A deliberate deviation from PLAN.md's original "anvil/sign"
 * sketch.
 */
public final class ItemEditorListener implements Listener {

    private final PurrtechPVE plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ItemEditorListener(PurrtechPVE plugin) {
        this.plugin = plugin;
    }

    public void awaitInput(Player player, PendingInput prompt) {
        pending.put(player.getUniqueId(), prompt);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof ItemEditorHolder) && !(holder instanceof SetEditorHolder) && !(holder instanceof ItemListHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        if (holder instanceof ItemEditorHolder itemHolder) {
            ItemEditorMenu.handleClick(plugin, player, itemHolder, slot, event.isShiftClick());
        } else if (holder instanceof SetEditorHolder setHolder) {
            SetEditorMenu.handleClick(plugin, player, setHolder, slot);
        } else if (holder instanceof ItemListHolder listHolder) {
            ItemListMenu.handleClick(plugin, player, listHolder, slot, event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof ItemEditorHolder || holder instanceof SetEditorHolder || holder instanceof ItemListHolder) {
            // rebasing happens by clicking the preview slot while holding the item, not dragging -
            // keeps these GUIs free of cursor/partial-stack edge cases
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PendingInput prompt = pending.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String rawInput = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> prompt.handle(player, rawInput));
    }

    public interface PendingInput {
        void handle(Player player, String rawInput);
    }
}
