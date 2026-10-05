package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.itemset.DuplicateSetKeyException;
import eu.purrtech.purrtechPVE.itemset.ItemSet;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The {@code /pve set} GUI - every item set in one paginated list: a button to create a new one
 * (only the set's key is typed in chat, everything else is configured in {@link
 * SetEditorMenu}), a plain click to open the editor on one, shift+right-click to delete it. Same
 * layout and conventions as {@link ItemListMenu}.
 */
public final class SetListMenu {

    private static final int SIZE = 54;
    private static final int ADD_SLOT = 0;
    private static final int PREV_SLOT = 3;
    private static final int INFO_SLOT = 4;
    private static final int NEXT_SLOT = 5;
    private static final int CLOSE_SLOT = 8;
    private static final int CONTENT_START = 9;
    private static final int PAGE_SIZE = SIZE - CONTENT_START;

    private SetListMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, int page) {
        Locale locale = player.locale();
        List<ItemSet> sets = sortedSets(plugin);
        int clamped = Math.max(0, Math.min(page, lastPage(sets.size())));
        SetListHolder holder = new SetListHolder(clamped);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().render(locale, "gui.set-list.title"));
        holder.setInventory(inventory);
        render(plugin, inventory, clamped, sets, locale);
        player.openInventory(inventory);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, int page, List<ItemSet> sets, Locale locale) {
        Messages messages = plugin.getMessages();
        int totalPages = lastPage(sets.size()) + 1;

        ItemStack add = named(Material.LIME_DYE, messages.render(locale, "gui.set-list.add"));
        ItemMeta addMeta = add.getItemMeta();
        addMeta.lore(List.of(messages.render(locale, "gui.set-list.add-hint-1"), messages.render(locale, "gui.set-list.add-hint-2")));
        add.setItemMeta(addMeta);
        inventory.setItem(ADD_SLOT, add);
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));

        ItemStack info = named(Material.BOOK, messages.render(locale, "gui.set-list.page",
                Placeholder.unparsed("page", String.valueOf(page + 1)), Placeholder.unparsed("total", String.valueOf(totalPages))));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(messages.render(locale, "gui.set-list.count", Placeholder.unparsed("count", String.valueOf(sets.size())))));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);
        if (page > 0) {
            inventory.setItem(PREV_SLOT, named(Material.ARROW, messages.render(locale, "gui.set-list.prev-page")));
        }
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, messages.render(locale, "gui.set-list.next-page")));
        }

        if (sets.isEmpty()) {
            inventory.setItem(CONTENT_START, named(Material.PAPER, messages.render(locale, "gui.set-list.none")));
            return;
        }
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < sets.size(); i++) {
            ItemSet set = sets.get(start + i);
            ItemStack icon = named(Material.ENCHANTED_BOOK, messages.render(locale, "gui.set-list.icon",
                    Placeholder.unparsed("name", set.displayName())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(
                    messages.render(locale, "gui.set-list.key", Placeholder.unparsed("key", set.key())),
                    messages.render(locale, "gui.set-list.members",
                            Placeholder.unparsed("count", String.valueOf(plugin.getItemSetService().members(set.key()).size()))),
                    messages.render(locale, "gui.set-list.tiers", Placeholder.unparsed("count", String.valueOf(tierCount(plugin, set.key())))),
                    Component.empty(),
                    messages.render(locale, "gui.set-list.hint-open"),
                    messages.render(locale, "gui.set-list.hint-delete")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    public static void handleClick(PurrtechPVE plugin, Player player, SetListHolder holder, int slot, ClickType click) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == ADD_SLOT) {
            promptCreate(plugin, player, holder.page());
            return;
        }
        List<ItemSet> sets = sortedSets(plugin);
        if (slot == PREV_SLOT) {
            if (holder.page() > 0) {
                open(plugin, player, holder.page() - 1);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page() < lastPage(sets.size())) {
                open(plugin, player, holder.page() + 1);
            }
            return;
        }
        int index = holder.page() * PAGE_SIZE + (slot - CONTENT_START);
        if (slot < CONTENT_START || index < 0 || index >= sets.size()) {
            return;
        }
        ItemSet set = sets.get(index);
        if (click == ClickType.SHIFT_RIGHT) {
            plugin.getItemSetService().delete(set.key());
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.set-list.deleted", Placeholder.unparsed("key", set.key())));
            open(plugin, player, holder.page());
        } else {
            SetEditorMenu.open(plugin, player, set.key(), SetEditorTab.MEMBERS);
        }
    }

    /** One chat prompt - the key - because that is the only value that is genuinely free text. */
    private static void promptCreate(PurrtechPVE plugin, Player player, int page) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.set-list.prompt-key"));
        player.sendMessage(messages.render(locale, "gui.set-list.prompt-key-example"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawKey) -> {
            if (isCancel(rawKey)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, page);
                return;
            }
            String key = rawKey.trim();
            if (key.isEmpty() || key.contains(" ")) {
                p.sendMessage(messages.render(locale, "gui.set-list.invalid-key"));
                open(plugin, p, page);
                return;
            }
            if (plugin.getItemSetService().findByKey(key).isPresent()) {
                p.sendMessage(messages.render(locale, "gui.set-list.duplicate", Placeholder.unparsed("key", key)));
                open(plugin, p, page);
                return;
            }
            // A set's display name is stored but shown nowhere a player looks, so it is not asked for -
            // the key stands in for it.
            try {
                plugin.getItemSetService().create(key, key);
                p.sendMessage(messages.render(locale, "gui.set-list.created", Placeholder.unparsed("key", key)));
                SetEditorMenu.open(plugin, p, key, SetEditorTab.MEMBERS);
            } catch (DuplicateSetKeyException e) {
                p.sendMessage(messages.render(locale, "gui.set-list.duplicate", Placeholder.unparsed("key", key)));
                open(plugin, p, page);
            }
        });
    }

    private static int tierCount(PurrtechPVE plugin, String setKey) {
        Set<Integer> counts = new HashSet<>();
        plugin.getItemSetService().damageThresholds(setKey).forEach(d -> counts.add(d.pieceCount()));
        plugin.getItemSetService().modifierThresholds(setKey).forEach(m -> counts.add(m.pieceCount()));
        return counts.size();
    }

    private static List<ItemSet> sortedSets(PurrtechPVE plugin) {
        List<ItemSet> sets = new ArrayList<>(plugin.getItemSetService().listAll());
        sets.sort(Comparator.comparing(ItemSet::key));
        return sets;
    }

    private static int lastPage(int count) {
        return Math.max(0, (count - 1) / PAGE_SIZE);
    }

    private static boolean isCancel(String rawInput) {
        String normalized = rawInput.trim().toLowerCase(Locale.ROOT);
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
