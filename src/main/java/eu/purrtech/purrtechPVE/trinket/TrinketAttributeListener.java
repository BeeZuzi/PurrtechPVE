package eu.purrtech.purrtechPVE.trinket;

import eu.purrtech.purrtechPVE.config.AccessorySettings;
import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.item.AttributeModifierEntry;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TemplateSnapshot;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Grants/revokes real vanilla {@link Attribute} modifiers for whatever this
 * server's accessory (trinket) slots hold - the one part of {@link
 * AttributeModifierEntry} that {@link ItemRenderer} can't just bake into
 * {@code ItemMeta}, since a trinket slot isn't a real Bukkit {@code
 * EquipmentSlot} vanilla's own equip/unequip detection ever looks at.
 *
 * <p>Modifiers are granted via {@link AttributeInstance#addTransientModifier}
 * (never {@code addModifier}) specifically so nothing here ever gets saved
 * into vanilla player NBT - every grant is entirely derived from {@code
 * player_accessory_slots} + the template's live data, so a fresh, from-
 * scratch recompute on every {@link #reapply} call is always correct and
 * there's no persisted state of our own to reconcile against or go stale.
 *
 * <p>Runs a full remove-then-reapply over every (configured trinket slot ×
 * every known {@link Attribute}) combination each time, using a
 * deterministic {@link NamespacedKey} per (slot, attribute) pair - cheap
 * (a few hundred {@code removeModifier} calls at most, all no-ops except the
 * ones that actually changed) and avoids needing to track "what did we
 * apply last time" ourselves.
 *
 * <p>Reapplied on two triggers: closing the accessory GUI (after its own
 * listener has saved the new contents - see the {@link EventPriority#MONITOR}
 * below) and on player join (covers a server restart, or simply the first
 * time this session sees the player). A template's attribute modifiers being
 * edited while already equipped in a trinket slot does NOT retroactively
 * refresh an online player's grant - unlike damage/resist stats, which
 * {@code EquipmentResolver} recomputes fresh on every hit, these are only
 * recomputed on the two triggers above; re-opening/closing the accessory
 * menu (or relogging) picks up the change.
 */
public final class TrinketAttributeListener implements Listener {

    private final Plugin plugin;
    private final AccessoryRepository accessoryRepository;
    private final AccessorySettings accessorySettings;
    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateSnapshotRepository snapshotRepository;
    private final ItemRenderer renderer;

    public TrinketAttributeListener(Plugin plugin, AccessoryRepository accessoryRepository, AccessorySettings accessorySettings,
                                     ItemTemplateRepository templateRepository, ItemTemplateSnapshotRepository snapshotRepository,
                                     ItemRenderer renderer) {
        this.plugin = plugin;
        this.accessoryRepository = accessoryRepository;
        this.accessorySettings = accessorySettings;
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.renderer = renderer;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reapply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAccessoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AccessoryInventoryHolder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        reapply(player);
    }

    /** Recomputes and grants exactly the attribute modifiers this player's current trinket contents justify - see the class javadoc. */
    public void reapply(Player player) {
        Map<String, ItemStack> accessories = accessoryRepository.findAll(player.getUniqueId());
        for (String slotName : accessorySettings.slots()) {
            List<AttributeModifierEntry> desired = resolvedItemOf(accessories.get(slotName))
                    .map(item -> item.snapshot().attributeModifiers().stream()
                            .filter(a -> a.slot().equals(slotName)).toList())
                    .orElse(List.of());
            for (Attribute attribute : Attribute.values()) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                instance.removeModifier(keyFor(slotName, attribute));
            }
            for (AttributeModifierEntry entry : desired) {
                AttributeInstance instance = player.getAttribute(entry.attribute());
                if (instance == null) {
                    continue;
                }
                instance.addTransientModifier(new AttributeModifier(keyFor(slotName, entry.attribute()), entry.amount(), entry.operation()));
            }
        }
    }

    private NamespacedKey keyFor(String slotName, Attribute attribute) {
        return new NamespacedKey(plugin, "trinket_" + slotName.toLowerCase(Locale.ROOT) + "_" + attribute.name().toLowerCase(Locale.ROOT));
    }

    /** Same PDC-stamp -> template -> pinned-snapshot resolution as {@code EquipmentResolver.resolvedItemOf}, kept local rather than shared since it's this small. */
    private Optional<ResolvedItem> resolvedItemOf(ItemStack stack) {
        return renderer.readStamp(stack).flatMap(stamp ->
                templateRepository.findByKey(stamp.templateKey()).flatMap(template ->
                        snapshotRepository.find(template.id(), stamp.templateVersion())
                                .map(snapshot -> new ResolvedItem(template, snapshot))));
    }

    private record ResolvedItem(ItemTemplate template, TemplateSnapshot snapshot) {
    }
}
