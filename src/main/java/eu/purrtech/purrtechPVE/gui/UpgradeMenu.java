package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.combat.DamageFeedback;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemUpgradeService;
import eu.purrtech.purrtechPVE.item.ItemUpgrades;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.UpgradeCategory;
import eu.purrtech.purrtechPVE.item.UpgradeEffect;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code /pve upgrade} GUI - upgrades the item held in the main hand: its damage per type, its
 * resistances and its special-effect numbers. Two screens: pick a category, then click entries to
 * raise/lower that entry's upgrade (left/right click +1/-1, shift +10/-10, Q resets to 0). Each
 * click writes the new amount through {@link ItemUpgradeService} and swaps the re-rendered item back
 * into the slot it came from, so the lore updates live. Only ever adds on top of the template (an
 * upgrade can't go below 0), and has no cost of its own - gate it with the permission, or call
 * {@link ItemUpgradeService} from whatever shop/economy flow should charge for it.
 */
public final class UpgradeMenu {

    private static final int CATEGORY_SIZE = 27;
    private static final int ENTRIES_SIZE = 54;
    private static final int BACK_SLOT = 0;
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 8;
    private static final int CONTENT_START = 9;

    private static final int DAMAGE_SLOT = 11;
    private static final int RESIST_SLOT = 13;
    private static final int EFFECT_SLOT = 15;

    private UpgradeMenu() {
    }

    // ---- open ----

    public static void open(PurrtechPVE plugin, Player player) {
        int slot = player.getInventory().getHeldItemSlot();
        Optional<ItemRenderer.StampedTemplate> stamp = plugin.getItemRenderer().readStamp(player.getInventory().getItem(slot));
        if (stamp.isEmpty()) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.upgrade.no-item"));
            return;
        }
        openCategories(plugin, player, stamp.get().templateKey(), slot);
    }

    private static void openCategories(PurrtechPVE plugin, Player player, String templateKey, int itemSlot) {
        Locale locale = player.locale();
        UpgradeMenuHolder holder = new UpgradeMenuHolder(UpgradeMenuHolder.View.CATEGORIES, templateKey, itemSlot, null);
        Inventory inventory = Bukkit.createInventory(holder, CATEGORY_SIZE,
                plugin.getMessages().render(locale, "gui.upgrade.title-categories", Placeholder.unparsed("key", templateKey)));
        holder.setInventory(inventory);
        Messages messages = plugin.getMessages();
        inventory.setItem(DAMAGE_SLOT, category(messages, locale, Material.IRON_SWORD, "damage"));
        inventory.setItem(RESIST_SLOT, category(messages, locale, Material.SHIELD, "resist"));
        inventory.setItem(EFFECT_SLOT, category(messages, locale, Material.NETHER_STAR, "effect"));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
        player.openInventory(inventory);
    }

    private static void openEntries(PurrtechPVE plugin, Player player, String templateKey, int itemSlot, UpgradeCategory category) {
        Locale locale = player.locale();
        UpgradeMenuHolder holder = new UpgradeMenuHolder(UpgradeMenuHolder.View.ENTRIES, templateKey, itemSlot, category);
        Inventory inventory = Bukkit.createInventory(holder, ENTRIES_SIZE, plugin.getMessages().render(locale, "gui.upgrade.title-entries",
                Placeholder.unparsed("category", plugin.getMessages().plain(locale, "gui.upgrade.category." + categoryKey(category)))));
        holder.setInventory(inventory);
        renderEntries(plugin, inventory, player, holder);
        player.openInventory(inventory);
    }

    // ---- render ----

    private static ItemStack category(Messages messages, Locale locale, Material icon, String key) {
        ItemStack stack = named(icon, messages.render(locale, "gui.upgrade.category." + key));
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.upgrade.category-hint")));
        stack.setItemMeta(meta);
        return stack;
    }

    private static void renderEntries(PurrtechPVE plugin, Inventory inventory, Player player, UpgradeMenuHolder holder) {
        Messages messages = plugin.getMessages();
        Locale locale = player.locale();
        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));

        ItemStack held = player.getInventory().getItem(holder.itemSlot());
        ItemUpgrades upgrades = plugin.getItemUpgradeService().upgradesOf(held);
        Map<String, Double> current = upgrades.of(holder.category());

        ItemStack info = named(Material.BOOK, messages.render(locale, "gui.upgrade.info", Placeholder.unparsed("key", holder.templateKey())));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(messages.render(locale, "gui.upgrade.info-hint-1"), messages.render(locale, "gui.upgrade.info-hint-2")));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        List<Entry> entries = entries(plugin, locale, holder.category(), contextOf(held));
        for (int i = 0; i < entries.size() && CONTENT_START + i < ENTRIES_SIZE; i++) {
            Entry entry = entries.get(i);
            double amount = current.getOrDefault(entry.key(), 0.0);
            ItemStack icon = named(amount > 0 ? Material.LIME_DYE : Material.GRAY_DYE, Component.text(entry.label()));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(
                    messages.render(locale, "gui.upgrade.entry-amount", Placeholder.unparsed("amount", DamageFeedback.formatAmount(amount))),
                    Component.empty(),
                    messages.render(locale, "gui.upgrade.entry-hint-1"),
                    messages.render(locale, "gui.upgrade.entry-hint-2")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    /** One clickable row of the entry screen: the key stored in the DB and the text shown for it. */
    private record Entry(String key, String label) {
    }

    private static List<Entry> entries(PurrtechPVE plugin, Locale locale, UpgradeCategory category, ModifierContext context) {
        List<Entry> out = new ArrayList<>();
        switch (category) {
            case DAMAGE -> types(plugin, false).forEach(type ->
                    out.add(new Entry(ItemUpgrades.damageKey(type.key(), context), type.icon() + " " + type.displayName())));
            case RESIST -> types(plugin, true).forEach(type ->
                    out.add(new Entry(type.key(), type.icon() + " " + type.displayName())));
            case EFFECT -> {
                for (UpgradeEffect effect : UpgradeEffect.values()) {
                    out.add(new Entry(effect.name(), plugin.getMessages().plain(locale,
                            "gui.upgrade.effect." + effect.name().toLowerCase(Locale.ROOT).replace('_', '-'))));
                }
            }
        }
        return out;
    }

    /**
     * Damage types an item can be upgraded on. The "physical" alias is skipped (its three subtypes are
     * listed on their own); bleed is a damage-over-time effect configured under the effects category, so
     * it is not a plain damage contribution - but it is a perfectly good resistance.
     */
    private static List<DamageType> types(PurrtechPVE plugin, boolean forResistance) {
        return plugin.getDamageTypeRegistry().all().values().stream()
                .filter(type -> !DamageTypeRegistry.FALLBACK_PHYSICAL.equals(type.key()))
                .filter(type -> forResistance || !"bleed".equals(type.key()))
                .sorted(Comparator.comparing(DamageType::key))
                .toList();
    }

    /** Armor and trinkets add damage while worn, anything held (weapons, tools) while wielded. */
    private static ModifierContext contextOf(ItemStack stack) {
        EquipmentSlot slot = stack == null ? EquipmentSlot.HAND : stack.getType().getEquipmentSlot();
        return slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND ? ModifierContext.WIELDED : ModifierContext.WORN;
    }

    private static String categoryKey(UpgradeCategory category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    // ---- clicks ----

    public static void handleClick(PurrtechPVE plugin, Player player, UpgradeMenuHolder holder, int slot, ClickType click) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        // Whatever happened to the inventory since the menu opened, never touch a different item than the one it was opened for.
        ItemStack held = player.getInventory().getItem(holder.itemSlot());
        Optional<ItemRenderer.StampedTemplate> stamp = plugin.getItemRenderer().readStamp(held);
        if (stamp.isEmpty() || !stamp.get().templateKey().equals(holder.templateKey())) {
            player.closeInventory();
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.upgrade.item-changed"));
            return;
        }
        switch (holder.view()) {
            case CATEGORIES -> {
                UpgradeCategory chosen = switch (slot) {
                    case DAMAGE_SLOT -> UpgradeCategory.DAMAGE;
                    case RESIST_SLOT -> UpgradeCategory.RESIST;
                    case EFFECT_SLOT -> UpgradeCategory.EFFECT;
                    default -> null;
                };
                if (chosen != null) {
                    openEntries(plugin, player, holder.templateKey(), holder.itemSlot(), chosen);
                }
            }
            case ENTRIES -> handleEntryClick(plugin, player, holder, held, slot, click);
        }
    }

    private static void handleEntryClick(PurrtechPVE plugin, Player player, UpgradeMenuHolder holder, ItemStack held, int slot, ClickType click) {
        if (slot == BACK_SLOT) {
            openCategories(plugin, player, holder.templateKey(), holder.itemSlot());
            return;
        }
        List<Entry> entries = entries(plugin, player.locale(), holder.category(), contextOf(held));
        int index = slot - CONTENT_START;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry entry = entries.get(index);
        ItemUpgradeService service = plugin.getItemUpgradeService();
        double current = service.upgradesOf(held).of(holder.category()).getOrDefault(entry.key(), 0.0);
        double next = switch (click) {
            case LEFT -> current + 1;
            case RIGHT -> current - 1;
            case SHIFT_LEFT -> current + 10;
            case SHIFT_RIGHT -> current - 10;
            case DROP, CONTROL_DROP -> 0;
            default -> current;
        };
        if (next == current) {
            return;
        }
        service.setUpgrade(held, holder.category(), entry.key(), next).ifPresent(updated -> {
            player.getInventory().setItem(holder.itemSlot(), updated);
            Inventory inventory = holder.getInventory();
            inventory.clear();
            renderEntries(plugin, inventory, player, holder);
        });
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
