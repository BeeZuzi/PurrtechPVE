package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.UnknownDamageTypeException;
import eu.purrtech.purrtechPVE.itemset.ItemSetNotFoundException;
import eu.purrtech.purrtechPVE.itemset.SetThresholdDamage;
import eu.purrtech.purrtechPVE.itemset.SetThresholdModifier;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The {@code /pve set edit <key>} admin GUI - manage which item templates
 * belong to a set, and its tiered piece-count bonuses. Same conventions as
 * {@link ItemEditorMenu}: one reused inventory, every edit calls straight
 * into {@code ItemSetService}, chat-based prompts for multi-field input
 * (reuses {@link ItemEditorListener}'s prompt infrastructure).
 */
public final class SetEditorMenu {

    private static final int SIZE = 54;
    private static final int TAB_MEMBERS = 0;
    private static final int TAB_THRESHOLDS = 1;
    private static final int CLOSE_SLOT = 8;
    private static final int ACTION_BUTTON_SLOT = 9;
    private static final int CONTENT_START = 18;

    private SetEditorMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, String setKey, SetEditorTab tab) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        if (plugin.getItemSetService().findByKey(setKey).isEmpty()) {
            player.sendMessage(messages.render(locale, "set.not-found", Placeholder.unparsed("key", setKey)));
            return;
        }
        SetEditorHolder holder = new SetEditorHolder(setKey, tab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                messages.render(locale, "gui.set-editor.title", Placeholder.unparsed("key", setKey)));
        holder.setInventory(inventory);
        render(plugin, inventory, setKey, tab, locale);
        player.openInventory(inventory);
    }

    private static void switchTab(PurrtechPVE plugin, SetEditorHolder holder, SetEditorTab tab, Locale locale) {
        holder.setTab(tab);
        render(plugin, holder.getInventory(), holder.setKey(), tab, locale);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, String setKey, SetEditorTab tab, Locale locale) {
        inventory.clear();
        Messages messages = plugin.getMessages();
        switch (tab) {
            case MEMBERS -> {
                drawTabBar(plugin, inventory, tab, locale);
                inventory.setItem(ACTION_BUTTON_SLOT, named(Material.LIME_DYE, messages.render(locale, "gui.set-editor.add-member")));
                renderMembers(plugin, inventory, setKey, locale);
            }
            case THRESHOLDS -> {
                drawTabBar(plugin, inventory, tab, locale);
                inventory.setItem(ACTION_BUTTON_SLOT, named(Material.LIME_DYE, messages.render(locale, "gui.set-editor.add-threshold")));
                renderThresholds(plugin, inventory, setKey, locale);
            }
            case ADD_MEMBER -> {
                inventory.setItem(TAB_MEMBERS, named(Material.ARROW, messages.render(locale, "gui.back")));
                inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
                renderAddMemberPicker(plugin, inventory, setKey, locale);
            }
        }
    }

    private static void drawTabBar(PurrtechPVE plugin, Inventory inventory, SetEditorTab active, Locale locale) {
        Messages messages = plugin.getMessages();
        inventory.setItem(TAB_MEMBERS, tabIcon(messages, locale, Material.CHEST, "gui.set-editor.tab.members", active == SetEditorTab.MEMBERS));
        inventory.setItem(TAB_THRESHOLDS, tabIcon(messages, locale, Material.BEACON, "gui.set-editor.tab.thresholds", active == SetEditorTab.THRESHOLDS));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
    }

    private static ItemStack tabIcon(Messages messages, Locale locale, Material material, String labelKey, boolean active) {
        String prefixKey = active ? "gui.tab-active" : "gui.tab-inactive";
        Component name = messages.render(locale, prefixKey, Placeholder.unparsed("label", messages.plain(locale, labelKey)));
        return named(material, name);
    }

    // ---- MEMBERS ----

    private static void renderMembers(PurrtechPVE plugin, Inventory inventory, String setKey, Locale locale) {
        Messages messages = plugin.getMessages();
        List<ItemTemplate> members = plugin.getItemSetService().members(setKey);
        for (int i = 0; i < members.size() && CONTENT_START + i < SIZE; i++) {
            ItemTemplate template = members.get(i);
            // Same reasoning as ItemListMenu: the real rendered stack, not a bare material+name
            // icon, so the display name's MiniMessage markup shows properly instead of literally.
            ItemStack icon = plugin.getItemTemplateService().renderGiveable(template.key());
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-list.key", Placeholder.unparsed("key", template.key())));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.set-editor.hint-remove-member"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handleMembersClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        if (slot == ACTION_BUTTON_SLOT) {
            switchTab(plugin, holder, SetEditorTab.ADD_MEMBER, locale);
            return;
        }
        List<ItemTemplate> members = plugin.getItemSetService().members(holder.setKey());
        int index = slot - CONTENT_START;
        if (index < 0 || index >= members.size()) {
            return;
        }
        ItemTemplate template = members.get(index);
        plugin.getItemSetService().removeMember(holder.setKey(), template.key());
        player.sendMessage(messages.render(locale, "gui.set-editor.member-removed", Placeholder.unparsed("key", template.key())));
        render(plugin, holder.getInventory(), holder.setKey(), SetEditorTab.MEMBERS, locale);
    }

    // ---- ADD_MEMBER picker ----

    private static void renderAddMemberPicker(PurrtechPVE plugin, Inventory inventory, String setKey, Locale locale) {
        Messages messages = plugin.getMessages();
        Set<String> memberKeys = plugin.getItemSetService().members(setKey).stream().map(ItemTemplate::key)
                .collect(java.util.stream.Collectors.toSet());
        List<ItemTemplate> candidates = plugin.getItemTemplateService().listAll().stream()
                .filter(t -> !memberKeys.contains(t.key())).toList();
        for (int i = 0; i < candidates.size() && CONTENT_START + i < SIZE; i++) {
            ItemTemplate template = candidates.get(i);
            ItemStack icon = plugin.getItemTemplateService().renderGiveable(template.key());
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-list.key", Placeholder.unparsed("key", template.key())));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.set-editor.hint-add-member"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handleAddMemberClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        if (slot == TAB_MEMBERS) {
            switchTab(plugin, holder, SetEditorTab.MEMBERS, locale);
            return;
        }
        Set<String> memberKeys = plugin.getItemSetService().members(holder.setKey()).stream().map(ItemTemplate::key)
                .collect(java.util.stream.Collectors.toSet());
        List<ItemTemplate> candidates = plugin.getItemTemplateService().listAll().stream()
                .filter(t -> !memberKeys.contains(t.key())).toList();
        int index = slot - CONTENT_START;
        if (index < 0 || index >= candidates.size()) {
            return;
        }
        ItemTemplate template = candidates.get(index);
        plugin.getItemSetService().addMember(holder.setKey(), template.key());
        player.sendMessage(messages.render(locale, "gui.set-editor.member-added", Placeholder.unparsed("key", template.key())));
        switchTab(plugin, holder, SetEditorTab.MEMBERS, locale);
    }

    // ---- THRESHOLDS ----

    private static void renderThresholds(PurrtechPVE plugin, Inventory inventory, String setKey, Locale locale) {
        Messages messages = plugin.getMessages();
        List<Integer> pieceCounts = distinctPieceCounts(plugin, setKey);
        for (int i = 0; i < pieceCounts.size() && CONTENT_START + i < SIZE; i++) {
            int count = pieceCounts.get(i);
            List<Component> lore = new ArrayList<>();
            for (SetThresholdDamage d : plugin.getItemSetService().damageThresholds(setKey)) {
                if (d.pieceCount() == count) {
                    String amount = "+" + formatAmount(d.amount()) + (d.mode() == DamageMode.PERCENT_OF_TOTAL ? "%" : "");
                    lore.add(messages.render(locale, "gui.set-editor.threshold-damage-line",
                            Placeholder.unparsed("amount", amount), Placeholder.unparsed("type", d.damageTypeKey())));
                }
            }
            for (SetThresholdModifier m : plugin.getItemSetService().modifierThresholds(setKey)) {
                if (m.pieceCount() == count) {
                    String key = m.percent() >= 0 ? "gui.set-editor.threshold-resist-line" : "gui.set-editor.threshold-weakness-line";
                    lore.add(messages.render(locale, key,
                            Placeholder.unparsed("amount", formatAmount(Math.abs(m.percent()))), Placeholder.unparsed("type", m.damageTypeKey())));
                }
            }
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.set-editor.threshold-hint-edit"));

            ItemStack icon = named(Material.BEACON, messages.render(locale, "gui.set-editor.threshold-icon", Placeholder.unparsed("count", String.valueOf(count))));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static List<Integer> distinctPieceCounts(PurrtechPVE plugin, String setKey) {
        Set<Integer> counts = new TreeSet<>();
        plugin.getItemSetService().damageThresholds(setKey).forEach(d -> counts.add(d.pieceCount()));
        plugin.getItemSetService().modifierThresholds(setKey).forEach(m -> counts.add(m.pieceCount()));
        return List.copyOf(counts);
    }

    private static void handleThresholdsClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        if (slot == ACTION_BUTTON_SLOT) {
            promptNewThreshold(plugin, player, holder);
            return;
        }
        List<Integer> pieceCounts = distinctPieceCounts(plugin, holder.setKey());
        int index = slot - CONTENT_START;
        if (index < 0 || index >= pieceCounts.size()) {
            return;
        }
        promptEditThreshold(plugin, player, holder, pieceCounts.get(index));
    }

    private static void promptNewThreshold(PurrtechPVE plugin, Player player, SetEditorHolder holder) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.set-editor.prompt-new-threshold-1"));
        player.sendMessage(messages.render(locale, "gui.set-editor.prompt-new-threshold-2"));
        player.sendMessage(messages.render(locale, "gui.set-editor.prompt-threshold-example"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, holder.setKey(), SetEditorTab.THRESHOLDS);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+");
            Integer pieceCount = parts.length > 0 ? parseInt(parts[0]) : null;
            if (pieceCount == null || pieceCount < 1) {
                p.sendMessage(messages.render(locale, "gui.set-editor.invalid-piece-count"));
                open(plugin, p, holder.setKey(), SetEditorTab.THRESHOLDS);
                return;
            }
            String[] rest = java.util.Arrays.copyOfRange(parts, 1, parts.length);
            applyThresholdCommand(plugin, p, holder, pieceCount, rest);
        });
    }

    private static void promptEditThreshold(PurrtechPVE plugin, Player player, SetEditorHolder holder, int pieceCount) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.set-editor.prompt-edit-threshold-1", Placeholder.unparsed("count", String.valueOf(pieceCount))));
        player.sendMessage(messages.render(locale, "gui.set-editor.prompt-edit-threshold-2"));
        player.sendMessage(messages.render(locale, "gui.set-editor.prompt-edit-threshold-3"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, holder.setKey(), SetEditorTab.THRESHOLDS);
                return;
            }
            applyThresholdCommand(plugin, p, holder, pieceCount, rawInput.trim().split("\\s+"));
        });
    }

    private static void applyThresholdCommand(PurrtechPVE plugin, Player player, SetEditorHolder holder, int pieceCount, String[] args) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        try {
            if (args.length >= 4 && "damage".equalsIgnoreCase(args[0])) {
                String type = args[1];
                Double amount = parseDouble(args[2]);
                DamageMode mode = parseMode(args[3]);
                if (amount == null || mode == null) {
                    player.sendMessage(messages.render(locale, "gui.set-editor.invalid-input"));
                } else {
                    plugin.getItemSetService().setDamageThreshold(holder.setKey(), pieceCount, type, amount, mode);
                    player.sendMessage(messages.render(locale, "gui.prompt.done"));
                }
            } else if (args.length >= 3 && "resist".equalsIgnoreCase(args[0])) {
                String type = args[1];
                Double percent = parseDouble(args[2]);
                if (percent == null) {
                    player.sendMessage(messages.render(locale, "gui.set-editor.invalid-number"));
                } else {
                    plugin.getItemSetService().setModifierThreshold(holder.setKey(), pieceCount, type, percent);
                    player.sendMessage(messages.render(locale, "gui.prompt.done"));
                }
            } else if (args.length >= 3 && "remove".equalsIgnoreCase(args[0]) && "damage".equalsIgnoreCase(args[1])) {
                plugin.getItemSetService().removeDamageThreshold(holder.setKey(), pieceCount, args[2]);
                player.sendMessage(messages.render(locale, "gui.set-editor.threshold-removed"));
            } else if (args.length >= 3 && "remove".equalsIgnoreCase(args[0]) && "resist".equalsIgnoreCase(args[1])) {
                plugin.getItemSetService().removeModifierThreshold(holder.setKey(), pieceCount, args[2]);
                player.sendMessage(messages.render(locale, "gui.set-editor.threshold-removed"));
            } else {
                player.sendMessage(messages.render(locale, "gui.set-editor.unrecognized-command"));
            }
        } catch (ItemSetNotFoundException e) {
            player.sendMessage(messages.render(locale, "set.not-found", Placeholder.unparsed("key", holder.setKey())));
        } catch (UnknownDamageTypeException e) {
            player.sendMessage(messages.render(locale, "gui.set-editor.unknown-damage-type"));
        }
        open(plugin, player, holder.setKey(), SetEditorTab.THRESHOLDS);
    }

    // ---- shared click routing ----

    public static void handleClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        Locale locale = player.locale();
        if (plugin.getItemSetService().findByKey(holder.setKey()).isEmpty()) {
            player.sendMessage(plugin.getMessages().render(locale, "gui.set-editor.set-gone-meanwhile"));
            player.closeInventory();
            return;
        }
        if (holder.tab() == SetEditorTab.ADD_MEMBER) {
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            handleAddMemberClick(plugin, player, holder, slot);
            return;
        }
        if (slot == TAB_MEMBERS) {
            switchTab(plugin, holder, SetEditorTab.MEMBERS, locale);
            return;
        }
        if (slot == TAB_THRESHOLDS) {
            switchTab(plugin, holder, SetEditorTab.THRESHOLDS, locale);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        switch (holder.tab()) {
            case MEMBERS -> handleMembersClick(plugin, player, holder, slot);
            case THRESHOLDS -> handleThresholdsClick(plugin, player, holder, slot);
            case ADD_MEMBER -> {
                // handled above
            }
        }
    }

    // ---- helpers ----

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }

    private static boolean isCancel(String rawInput) {
        String normalized = rawInput.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("zrusit") || normalized.equals("zrušit") || normalized.equals("cancel");
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DamageMode parseMode(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "flat" -> DamageMode.FLAT;
            case "percent", "percent_of_total" -> DamageMode.PERCENT_OF_TOTAL;
            default -> null;
        };
    }
}
