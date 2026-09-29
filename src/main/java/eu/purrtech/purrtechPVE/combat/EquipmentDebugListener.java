package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;

/**
 * The {@code /pve debug} equip/hold readout: reports every armor/hand/off-hand change on a player
 * who has {@link DebugModeService#isEnabled} turned on - one line per changed slot, old item to
 * new item. Rides {@link EntityEquipmentChangedEvent} rather than separate {@code
 * PlayerItemHeldEvent}/armor-swap detection since Paper already fires this one event for both
 * "player changes their currently held item" and "player changing their equipped armor" (see its
 * javadoc) - but that same event also fires on pure durability wear (its javadoc lists "durability
 * of an equipment item changing" as a trigger too), so {@link #sameIgnoringDurability} strips
 * {@link Damageable} damage before comparing to avoid spamming a message on every hit landed while
 * holding the same weapon.
 */
public final class EquipmentDebugListener implements Listener {

    private final PurrtechPVE plugin;
    private final DebugModeService debugModeService;
    private final DebugStatsReporter statsReporter;

    public EquipmentDebugListener(PurrtechPVE plugin, DebugModeService debugModeService, DebugStatsReporter statsReporter) {
        this.plugin = plugin;
        this.debugModeService = debugModeService;
        this.statsReporter = statsReporter;
    }

    @EventHandler
    public void onEquipmentChanged(EntityEquipmentChangedEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player) || !debugModeService.isEnabled(player.getUniqueId())) {
            return;
        }
        boolean anyChange = false;
        for (Map.Entry<EquipmentSlot, EntityEquipmentChangedEvent.EquipmentChange> entry : event.getEquipmentChanges().entrySet()) {
            ItemStack oldItem = entry.getValue().oldItem();
            ItemStack newItem = entry.getValue().newItem();
            if (sameIgnoringDurability(oldItem, newItem)) {
                continue;
            }
            anyChange = true;
            player.sendMessage(plugin.getMessages().render(player.locale(), "debug.equipment-change",
                    Placeholder.unparsed("slot", entry.getKey().name().toLowerCase(Locale.ROOT)),
                    Placeholder.unparsed("old", describe(oldItem)),
                    Placeholder.unparsed("new", describe(newItem))));
        }
        if (anyChange) {
            // One tick later so the resolver reads the inventory as it is after this change.
            Bukkit.getScheduler().runTask(plugin, () -> statsReporter.send(player));
        }
    }

    private boolean sameIgnoringDurability(ItemStack a, ItemStack b) {
        boolean aEmpty = a == null || a.getType().isAir();
        boolean bEmpty = b == null || b.getType().isAir();
        if (aEmpty || bEmpty) {
            return aEmpty && bEmpty;
        }
        if (a.getType() != b.getType()) {
            return false;
        }
        return stripDurability(a).isSimilar(stripDurability(b));
    }

    private ItemStack stripDurability(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasDamage()) {
            return stack;
        }
        ItemStack clone = stack.clone();
        damageable.setDamage(0);
        clone.setItemMeta(meta);
        return clone;
    }

    private String describe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "-";
        }
        String name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName())
                : stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return stack.getAmount() > 1 ? name + " x" + stack.getAmount() : name;
    }
}
