package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.DuplicateTemplateKeyException;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /pve item menu} admin GUI - every created item template in one
 * paginated grid, replacing {@code /pve item list}'s plain chat dump for
 * day-to-day management: a button to create a new one, plain click to open
 * {@link ItemEditorMenu} on it, shift+right-click to delete it outright, and
 * shift+left-click to get a copy of it into your own inventory. Same
 * conventions as {@link ItemEditorMenu}/{@link SetEditorMenu} - one reused
 * chest inventory, chat-based prompt for the multi-field "create" input,
 * every action calls straight into {@code ItemTemplateService}.
 */
public final class ItemListMenu {

    private static final int SIZE = 54;
    private static final int ADD_SLOT = 0;
    private static final int PREV_SLOT = 3;
    private static final int INFO_SLOT = 4;
    private static final int NEXT_SLOT = 5;
    private static final int CLOSE_SLOT = 8;
    private static final int CONTENT_START = 9;
    private static final int PAGE_SIZE = SIZE - CONTENT_START;

    private ItemListMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, int page) {
        Locale locale = player.locale();
        List<ItemTemplate> templates = sortedTemplates(plugin);
        int clampedPage = Math.max(0, Math.min(page, lastPage(templates)));
        ItemListHolder holder = new ItemListHolder(clampedPage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().render(locale, "gui.item-list.title"));
        holder.setInventory(inventory);
        render(plugin, inventory, clampedPage, locale);
        player.openInventory(inventory);
    }

    private static void reopen(PurrtechPVE plugin, Player player, int page) {
        open(plugin, player, page);
    }

    private static int lastPage(List<ItemTemplate> templates) {
        return Math.max(0, (templates.size() - 1) / PAGE_SIZE);
    }

    private static List<ItemTemplate> sortedTemplates(PurrtechPVE plugin) {
        return plugin.getItemTemplateService().listAll().stream()
                .sorted(Comparator.comparing(ItemTemplate::key))
                .toList();
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, int page, Locale locale) {
        inventory.clear();
        Messages messages = plugin.getMessages();
        List<ItemTemplate> templates = sortedTemplates(plugin);
        int totalPages = lastPage(templates) + 1;

        inventory.setItem(ADD_SLOT, named(Material.LIME_DYE, messages.render(locale, "gui.item-list.add")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));

        ItemStack info = named(Material.BOOK, messages.render(locale, "gui.item-list.page",
                Placeholder.unparsed("page", String.valueOf(page + 1)), Placeholder.unparsed("total", String.valueOf(totalPages))));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(messages.render(locale, "gui.item-list.count", Placeholder.unparsed("count", String.valueOf(templates.size())))));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        if (page > 0) {
            inventory.setItem(PREV_SLOT, named(Material.ARROW, messages.render(locale, "gui.item-list.prev-page")));
        }
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, messages.render(locale, "gui.item-list.next-page")));
        }

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < templates.size(); i++) {
            ItemTemplate template = templates.get(start + i);
            // The exact same stack renderGiveable() would hand you via shift+left-click below -
            // base material, custom model data, damage/resist/enchant lore, all of it - so the
            // preview in this grid IS what you'd actually get, not just a generic named icon.
            ItemStack icon = plugin.getItemTemplateService().renderGiveable(template.key());
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-list.key", Placeholder.unparsed("key", template.key())));
            String syncSuffixKey = template.isFullySynced() ? "gui.item-list.version" : "gui.item-list.version-unsynced";
            lore.add(messages.render(locale, syncSuffixKey, Placeholder.unparsed("version", String.valueOf(template.version()))));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-list.hint-edit"));
            lore.add(messages.render(locale, "gui.item-list.hint-copy"));
            lore.add(messages.render(locale, "gui.item-list.hint-delete"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    public static void handleClick(PurrtechPVE plugin, Player player, ItemListHolder holder, int slot, ClickType click) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        if (slot == ADD_SLOT) {
            promptCreate(plugin, player, holder.page());
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        List<ItemTemplate> templates = sortedTemplates(plugin);
        if (slot == PREV_SLOT) {
            if (holder.page() > 0) {
                reopen(plugin, player, holder.page() - 1);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page() < lastPage(templates)) {
                reopen(plugin, player, holder.page() + 1);
            }
            return;
        }

        int index = holder.page() * PAGE_SIZE + (slot - CONTENT_START);
        if (slot < CONTENT_START || index < 0 || index >= templates.size()) {
            return;
        }
        ItemTemplate template = templates.get(index);

        if (click == ClickType.SHIFT_RIGHT) {
            plugin.getItemTemplateService().delete(template.key());
            player.sendMessage(messages.render(locale, "gui.item-list.deleted", Placeholder.unparsed("key", template.key())));
            reopen(plugin, player, holder.page());
        } else if (click == ClickType.SHIFT_LEFT) {
            try {
                ItemStack copy = plugin.getItemTemplateService().renderGiveable(template.key());
                player.getInventory().addItem(copy);
                player.sendMessage(messages.render(locale, "gui.item-list.got-copy", Placeholder.unparsed("key", template.key())));
            } catch (TemplateNotFoundException e) {
                player.sendMessage(messages.render(locale, "gui.item-list.vanished"));
                reopen(plugin, player, holder.page());
            }
        } else {
            ItemEditorMenu.open(plugin, player, template.key(), ItemEditorTab.BASE);
        }
    }

    private static void promptCreate(PurrtechPVE plugin, Player player, int page) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.item-list.create-prompt"));
        player.sendMessage(messages.render(locale, "gui.item-list.create-prompt-example"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, page);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+", 3);
            if (parts.length != 3) {
                p.sendMessage(messages.render(locale, "gui.item-list.create-invalid"));
                open(plugin, p, page);
                return;
            }
            String key = parts[0];
            Material material = Material.matchMaterial(parts[1]);
            String displayName = parts[2];
            if (material == null) {
                p.sendMessage(messages.render(locale, "error.invalid-material", Placeholder.unparsed("material", parts[1])));
                open(plugin, p, page);
                return;
            }
            try {
                plugin.getItemTemplateService().create(key, material, displayName, p.getUniqueId().toString());
                p.sendMessage(messages.render(locale, "item.created", Placeholder.unparsed("key", key)));
            } catch (DuplicateTemplateKeyException e) {
                p.sendMessage(messages.render(locale, "item.duplicate-key", Placeholder.unparsed("key", key)));
            }
            open(plugin, p, page);
        });
    }

    private static boolean isCancel(String rawInput) {
        String normalized = rawInput.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("zrusit") || normalized.equals("zrušit") || normalized.equals("cancel");
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
