package eu.purrtech.purrtechPVE.trinket;

import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the accessory GUI to simple direct single-slot placement: shift-
 * clicks and any interaction with the locked filler slots are rejected, only
 * an item that actually declares an {@link eu.purrtech.purrtechPVE.item.AttributeModifierEntry}
 * for that exact slot name may be placed into it (anything else - vanilla
 * gear, blocks, a trinket meant for a different slot - is rejected rather
 * than silently doing nothing the way {@link TrinketAttributeListener} would
 * treat it), and the real slots' contents are persisted on close. No quick-
 * move support in this v1 - safer against dupe/placement edge cases than
 * trying to handle every InventoryAction case for a first pass.
 */
public final class AccessoryMenuListener implements Listener {

    private final AccessoryRepository repository;
    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateSnapshotRepository snapshotRepository;
    private final ItemRenderer renderer;

    public AccessoryMenuListener(AccessoryRepository repository, ItemTemplateRepository templateRepository,
                                  ItemTemplateSnapshotRepository snapshotRepository, ItemRenderer renderer) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.renderer = renderer;
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
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            // Click landed in the player's own inventory (e.g. picking the item to hotbar-swap in
            // with), not the accessory GUI itself - nothing here to restrict.
            return;
        }
        if (rawSlot >= holder.slotNames().size()) {
            event.setCancelled(true);
            return;
        }
        ItemStack incoming = incomingItem(event);
        if (incoming == null || incoming.getType().isAir()) {
            // Taking an item out (or a no-op click) never introduces a new stack - nothing to check.
            return;
        }
        if (!hasTrinketSlot(incoming, holder.slotNames().get(rawSlot))) {
            event.setCancelled(true);
        }
    }

    /** The stack that would end up in the clicked slot if this click is allowed to proceed. */
    private ItemStack incomingItem(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.HOTBAR_SWAP) {
            int hotbarButton = event.getHotbarButton();
            return hotbarButton >= 0 ? event.getWhoClicked().getInventory().getItem(hotbarButton) : null;
        }
        return event.getCursor();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                continue;
            }
            if (rawSlot >= holder.slotNames().size() || !hasTrinketSlot(event.getOldCursor(), holder.slotNames().get(rawSlot))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Whether {@code stack} is one of our own templates with an attribute modifier assigned to exactly this trinket slot name. */
    private boolean hasTrinketSlot(ItemStack stack, String slotName) {
        return renderer.readStamp(stack)
                .flatMap(stamp -> templateRepository.findByKey(stamp.templateKey())
                        .flatMap(template -> snapshotRepository.find(template.id(), stamp.templateVersion())))
                .map(snapshot -> snapshot.attributeModifiers().stream().anyMatch(a -> a.slot().equalsIgnoreCase(slotName)))
                .orElse(false);
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
