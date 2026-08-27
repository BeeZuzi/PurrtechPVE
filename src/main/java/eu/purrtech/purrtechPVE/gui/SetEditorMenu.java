package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.UnknownDamageTypeException;
import eu.purrtech.purrtechPVE.itemset.ItemSetNotFoundException;
import eu.purrtech.purrtechPVE.itemset.SetThresholdDamage;
import eu.purrtech.purrtechPVE.itemset.SetThresholdModifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        if (plugin.getItemSetService().findByKey(setKey).isEmpty()) {
            player.sendMessage(Component.text("Set " + setKey + " neexistuje.", NamedTextColor.RED));
            return;
        }
        SetEditorHolder holder = new SetEditorHolder(setKey, tab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Set: " + setKey));
        holder.setInventory(inventory);
        render(plugin, inventory, setKey, tab);
        player.openInventory(inventory);
    }

    private static void switchTab(PurrtechPVE plugin, SetEditorHolder holder, SetEditorTab tab) {
        holder.setTab(tab);
        render(plugin, holder.getInventory(), holder.setKey(), tab);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, String setKey, SetEditorTab tab) {
        inventory.clear();
        switch (tab) {
            case MEMBERS -> {
                drawTabBar(inventory, tab);
                inventory.setItem(ACTION_BUTTON_SLOT, named(Material.LIME_DYE, Component.text("+ Přidat item", NamedTextColor.GREEN)));
                renderMembers(plugin, inventory, setKey);
            }
            case THRESHOLDS -> {
                drawTabBar(inventory, tab);
                inventory.setItem(ACTION_BUTTON_SLOT, named(Material.LIME_DYE, Component.text("+ Přidat práh", NamedTextColor.GREEN)));
                renderThresholds(plugin, inventory, setKey);
            }
            case ADD_MEMBER -> {
                inventory.setItem(TAB_MEMBERS, named(Material.ARROW, Component.text("← Zpět", NamedTextColor.YELLOW)));
                inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, Component.text("Zavřít", NamedTextColor.RED)));
                renderAddMemberPicker(plugin, inventory, setKey);
            }
        }
    }

    private static void drawTabBar(Inventory inventory, SetEditorTab active) {
        inventory.setItem(TAB_MEMBERS, tabIcon(Material.CHEST, "Itemy v setu", active == SetEditorTab.MEMBERS));
        inventory.setItem(TAB_THRESHOLDS, tabIcon(Material.BEACON, "Prahy bonusů", active == SetEditorTab.THRESHOLDS));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, Component.text("Zavřít", NamedTextColor.RED)));
    }

    private static ItemStack tabIcon(Material material, String label, boolean active) {
        Component name = Component.text((active ? "▶ " : "") + label, active ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        return named(material, name);
    }

    // ---- MEMBERS ----

    private static void renderMembers(PurrtechPVE plugin, Inventory inventory, String setKey) {
        List<ItemTemplate> members = plugin.getItemSetService().members(setKey);
        for (int i = 0; i < members.size() && CONTENT_START + i < SIZE; i++) {
            ItemTemplate template = members.get(i);
            ItemStack icon = named(template.baseMaterial(), Component.text(template.displayName(), NamedTextColor.AQUA));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(
                    Component.text(template.key(), NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Klik: odebrat ze setu", NamedTextColor.YELLOW)));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handleMembersClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        if (slot == ACTION_BUTTON_SLOT) {
            switchTab(plugin, holder, SetEditorTab.ADD_MEMBER);
            return;
        }
        List<ItemTemplate> members = plugin.getItemSetService().members(holder.setKey());
        int index = slot - CONTENT_START;
        if (index < 0 || index >= members.size()) {
            return;
        }
        ItemTemplate template = members.get(index);
        plugin.getItemSetService().removeMember(holder.setKey(), template.key());
        player.sendMessage(Component.text("Item " + template.key() + " odebrán ze setu.", NamedTextColor.GREEN));
        render(plugin, holder.getInventory(), holder.setKey(), SetEditorTab.MEMBERS);
    }

    // ---- ADD_MEMBER picker ----

    private static void renderAddMemberPicker(PurrtechPVE plugin, Inventory inventory, String setKey) {
        Set<String> memberKeys = plugin.getItemSetService().members(setKey).stream().map(ItemTemplate::key)
                .collect(java.util.stream.Collectors.toSet());
        List<ItemTemplate> candidates = plugin.getItemTemplateService().listAll().stream()
                .filter(t -> !memberKeys.contains(t.key())).toList();
        for (int i = 0; i < candidates.size() && CONTENT_START + i < SIZE; i++) {
            ItemTemplate template = candidates.get(i);
            ItemStack icon = named(template.baseMaterial(), Component.text(template.displayName(), NamedTextColor.AQUA));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(
                    Component.text(template.key(), NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Klik: přidat do setu", NamedTextColor.YELLOW)));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handleAddMemberClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        if (slot == TAB_MEMBERS) {
            switchTab(plugin, holder, SetEditorTab.MEMBERS);
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
        player.sendMessage(Component.text("Item " + template.key() + " přidán do setu.", NamedTextColor.GREEN));
        switchTab(plugin, holder, SetEditorTab.MEMBERS);
    }

    // ---- THRESHOLDS ----

    private static void renderThresholds(PurrtechPVE plugin, Inventory inventory, String setKey) {
        List<Integer> pieceCounts = distinctPieceCounts(plugin, setKey);
        for (int i = 0; i < pieceCounts.size() && CONTENT_START + i < SIZE; i++) {
            int count = pieceCounts.get(i);
            List<Component> lore = new ArrayList<>();
            for (SetThresholdDamage d : plugin.getItemSetService().damageThresholds(setKey)) {
                if (d.pieceCount() == count) {
                    lore.add(Component.text("Poškození: +" + formatAmount(d.amount())
                            + (d.mode() == DamageMode.PERCENT_OF_TOTAL ? "%" : "") + " " + d.damageTypeKey(), NamedTextColor.WHITE));
                }
            }
            for (SetThresholdModifier m : plugin.getItemSetService().modifierThresholds(setKey)) {
                if (m.pieceCount() == count) {
                    NamedTextColor color = m.percent() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                    lore.add(Component.text((m.percent() >= 0 ? "Odolnost: " : "Slabina: ") + formatAmount(Math.abs(m.percent()))
                            + "% " + m.damageTypeKey(), color));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("Klik: upravit bonusy tohoto prahu", NamedTextColor.YELLOW));

            ItemStack icon = named(Material.BEACON, Component.text(count + " ks", NamedTextColor.AQUA));
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
        player.closeInventory();
        player.sendMessage(Component.text("Napiš do chatu: <počet kusů> damage <typ> <částka> <flat|percent>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("nebo: <počet kusů> resist <typ> <procenta>    (nebo 'zrusit')", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Například: 2 damage fire 4 flat   nebo   4 resist frozen 25", NamedTextColor.GRAY));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, holder.setKey(), SetEditorTab.THRESHOLDS);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+");
            Integer pieceCount = parts.length > 0 ? parseInt(parts[0]) : null;
            if (pieceCount == null || pieceCount < 1) {
                p.sendMessage(Component.text("Neplatný počet kusů, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, holder.setKey(), SetEditorTab.THRESHOLDS);
                return;
            }
            String[] rest = java.util.Arrays.copyOfRange(parts, 1, parts.length);
            applyThresholdCommand(plugin, p, holder, pieceCount, rest);
        });
    }

    private static void promptEditThreshold(PurrtechPVE plugin, Player player, SetEditorHolder holder, int pieceCount) {
        player.closeInventory();
        player.sendMessage(Component.text("Práh " + pieceCount + " kusů - napiš do chatu:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("damage <typ> <částka> <flat|percent>   |   resist <typ> <procenta>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("remove damage <typ>   |   remove resist <typ>   (nebo 'zrusit')", NamedTextColor.YELLOW));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, holder.setKey(), SetEditorTab.THRESHOLDS);
                return;
            }
            applyThresholdCommand(plugin, p, holder, pieceCount, rawInput.trim().split("\\s+"));
        });
    }

    private static void applyThresholdCommand(PurrtechPVE plugin, Player player, SetEditorHolder holder, int pieceCount, String[] args) {
        try {
            if (args.length >= 4 && "damage".equalsIgnoreCase(args[0])) {
                String type = args[1];
                Double amount = parseDouble(args[2]);
                DamageMode mode = parseMode(args[3]);
                if (amount == null || mode == null) {
                    player.sendMessage(Component.text("Neplatný vstup.", NamedTextColor.RED));
                } else {
                    plugin.getItemSetService().setDamageThreshold(holder.setKey(), pieceCount, type, amount, mode);
                    player.sendMessage(Component.text("Nastaveno.", NamedTextColor.GREEN));
                }
            } else if (args.length >= 3 && "resist".equalsIgnoreCase(args[0])) {
                String type = args[1];
                Double percent = parseDouble(args[2]);
                if (percent == null) {
                    player.sendMessage(Component.text("Neplatné číslo.", NamedTextColor.RED));
                } else {
                    plugin.getItemSetService().setModifierThreshold(holder.setKey(), pieceCount, type, percent);
                    player.sendMessage(Component.text("Nastaveno.", NamedTextColor.GREEN));
                }
            } else if (args.length >= 3 && "remove".equalsIgnoreCase(args[0]) && "damage".equalsIgnoreCase(args[1])) {
                plugin.getItemSetService().removeDamageThreshold(holder.setKey(), pieceCount, args[2]);
                player.sendMessage(Component.text("Odebráno.", NamedTextColor.GREEN));
            } else if (args.length >= 3 && "remove".equalsIgnoreCase(args[0]) && "resist".equalsIgnoreCase(args[1])) {
                plugin.getItemSetService().removeModifierThreshold(holder.setKey(), pieceCount, args[2]);
                player.sendMessage(Component.text("Odebráno.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Nerozpoznaný příkaz, zkus to znovu z menu.", NamedTextColor.RED));
            }
        } catch (ItemSetNotFoundException e) {
            player.sendMessage(Component.text("Set už neexistuje.", NamedTextColor.RED));
        } catch (UnknownDamageTypeException e) {
            player.sendMessage(Component.text("Neznámý typ poškození.", NamedTextColor.RED));
        }
        open(plugin, player, holder.setKey(), SetEditorTab.THRESHOLDS);
    }

    // ---- shared click routing ----

    public static void handleClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        if (plugin.getItemSetService().findByKey(holder.setKey()).isEmpty()) {
            player.sendMessage(Component.text("Set byl mezitím smazán.", NamedTextColor.RED));
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
            switchTab(plugin, holder, SetEditorTab.MEMBERS);
            return;
        }
        if (slot == TAB_THRESHOLDS) {
            switchTab(plugin, holder, SetEditorTab.THRESHOLDS);
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
