package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.DuplicateTemplateKeyException;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        List<ItemTemplate> templates = sortedTemplates(plugin);
        int clampedPage = Math.max(0, Math.min(page, lastPage(templates)));
        ItemListHolder holder = new ItemListHolder(clampedPage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Itemy (šablony)"));
        holder.setInventory(inventory);
        render(plugin, inventory, clampedPage);
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

    private static void render(PurrtechPVE plugin, Inventory inventory, int page) {
        inventory.clear();
        List<ItemTemplate> templates = sortedTemplates(plugin);
        int totalPages = lastPage(templates) + 1;

        inventory.setItem(ADD_SLOT, named(Material.LIME_DYE, Component.text("+ Vytvořit item", NamedTextColor.GREEN)));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, Component.text("Zavřít", NamedTextColor.RED)));

        ItemStack info = named(Material.BOOK, Component.text("Strana " + (page + 1) + "/" + totalPages, NamedTextColor.AQUA));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(Component.text(templates.size() + " itemů celkem", NamedTextColor.GRAY)));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        if (page > 0) {
            inventory.setItem(PREV_SLOT, named(Material.ARROW, Component.text("← Předchozí strana", NamedTextColor.YELLOW)));
        }
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, Component.text("Další strana →", NamedTextColor.YELLOW)));
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
            lore.add(Component.text(template.key(), NamedTextColor.GRAY));
            lore.add(Component.text("v" + template.version()
                    + (template.isFullySynced() ? "" : " (nepropsáno)"), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Klik: upravit", NamedTextColor.YELLOW));
            lore.add(Component.text("Shift+levý klik: dát kopii do inventáře", NamedTextColor.GREEN));
            lore.add(Component.text("Shift+pravý klik: smazat", NamedTextColor.RED));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    public static void handleClick(PurrtechPVE plugin, Player player, ItemListHolder holder, int slot, ClickType click) {
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
            player.sendMessage(Component.text("Šablona " + template.key() + " smazána.", NamedTextColor.GREEN));
            reopen(plugin, player, holder.page());
        } else if (click == ClickType.SHIFT_LEFT) {
            try {
                ItemStack copy = plugin.getItemTemplateService().renderGiveable(template.key());
                player.getInventory().addItem(copy);
                player.sendMessage(Component.text("Dostal jsi kopii itemu " + template.key() + ".", NamedTextColor.GREEN));
            } catch (TemplateNotFoundException e) {
                player.sendMessage(Component.text("Šablona mezitím zmizela.", NamedTextColor.RED));
                reopen(plugin, player, holder.page());
            }
        } else {
            ItemEditorMenu.open(plugin, player, template.key(), ItemEditorTab.BASE);
        }
    }

    private static void promptCreate(PurrtechPVE plugin, Player player, int page) {
        player.closeInventory();
        player.sendMessage(Component.text("Napiš do chatu: <klíč> <materiál> <zobrazovaný název>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Například: fire-sword IRON_SWORD Plamenný meč   (nebo napiš 'zrusit')", NamedTextColor.GRAY));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, page);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+", 3);
            if (parts.length != 3) {
                p.sendMessage(Component.text("Neplatný vstup, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, page);
                return;
            }
            String key = parts[0];
            Material material = Material.matchMaterial(parts[1]);
            String displayName = parts[2];
            if (material == null) {
                p.sendMessage(Component.text("Neznámý item: " + parts[1], NamedTextColor.RED));
                open(plugin, p, page);
                return;
            }
            try {
                plugin.getItemTemplateService().create(key, material, displayName, p.getUniqueId().toString());
                p.sendMessage(Component.text("Šablona " + key + " vytvořena.", NamedTextColor.GREEN));
            } catch (DuplicateTemplateKeyException e) {
                p.sendMessage(Component.text("Šablona s klíčem " + key + " už existuje.", NamedTextColor.RED));
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
