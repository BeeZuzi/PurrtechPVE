package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.item.ArmorClass;
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
import java.util.Map;

/**
 * The {@code /pve armorclass menu} admin GUI - what resistance/weakness each
 * of the 3 fixed armor weight classes (LIGHT/MEDIUM/HEAVY) grants, live to
 * every equipped piece tagged with that class (see {@code
 * ArmorClassProfileRepository}/{@code EquipmentResolver}). Same conventions
 * as {@link ItemEditorMenu}'s RESIST tab - one reused chest inventory, chat
 * prompt for the percent value, click to set/shift-click to remove - just
 * scoped to a whole class instead of one item template, and with no
 * versioning at all (this is live/global config, like {@code mob_damage_
 * profile}, not a per-item stat).
 */
public final class ArmorClassMenu {

    private static final int SIZE = 54;
    private static final int TAB_LIGHT = 0;
    private static final int TAB_MEDIUM = 1;
    private static final int TAB_HEAVY = 2;
    private static final int CLOSE_SLOT = 8;
    private static final int CONTENT_START = 9;

    private ArmorClassMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, ArmorClass armorClass) {
        Locale locale = player.locale();
        ArmorClassHolder holder = new ArmorClassHolder(armorClass);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().render(locale, "gui.armor-class.title"));
        holder.setInventory(inventory);
        render(plugin, inventory, armorClass, locale);
        player.openInventory(inventory);
    }

    private static void switchTab(PurrtechPVE plugin, ArmorClassHolder holder, ArmorClass armorClass, Locale locale) {
        holder.setArmorClass(armorClass);
        render(plugin, holder.getInventory(), armorClass, locale);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, ArmorClass armorClass, Locale locale) {
        inventory.clear();
        Messages messages = plugin.getMessages();
        inventory.setItem(TAB_LIGHT, tabIcon(messages, locale, Material.LEATHER_CHESTPLATE, "gui.armor-class.tab.light", armorClass == ArmorClass.LIGHT));
        inventory.setItem(TAB_MEDIUM, tabIcon(messages, locale, Material.IRON_CHESTPLATE, "gui.armor-class.tab.medium", armorClass == ArmorClass.MEDIUM));
        inventory.setItem(TAB_HEAVY, tabIcon(messages, locale, Material.NETHERITE_CHESTPLATE, "gui.armor-class.tab.heavy", armorClass == ArmorClass.HEAVY));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));

        Map<String, Double> profile = plugin.getArmorClassProfileRepository().findByArmorClass(armorClass.name());
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        for (int i = 0; i < types.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = types.get(i);
            Double percent = profile.get(type.key());

            List<Component> lore = new ArrayList<>();
            if (percent != null) {
                String key = percent >= 0 ? "gui.armor-class.lore.resist" : "gui.armor-class.lore.weakness";
                lore.add(messages.render(locale, key, Placeholder.unparsed("amount", formatAmount(Math.abs(percent)))));
            } else {
                lore.add(messages.render(locale, "gui.armor-class.lore.not-set"));
            }
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.armor-class.lore.hint-set"));
            lore.add(messages.render(locale, "gui.armor-class.lore.hint-remove"));

            ItemStack icon = named(iconFor(type.key()), messages.render(locale, "gui.armor-class.type-icon",
                    Placeholder.unparsed("icon", type.icon()), Placeholder.unparsed("type", type.displayName())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    public static void handleClick(PurrtechPVE plugin, Player player, ArmorClassHolder holder, int slot, boolean shift) {
        Locale locale = player.locale();
        switch (slot) {
            case TAB_LIGHT -> switchTab(plugin, holder, ArmorClass.LIGHT, locale);
            case TAB_MEDIUM -> switchTab(plugin, holder, ArmorClass.MEDIUM, locale);
            case TAB_HEAVY -> switchTab(plugin, holder, ArmorClass.HEAVY, locale);
            case CLOSE_SLOT -> player.closeInventory();
            default -> handleContentClick(plugin, player, holder, slot, shift);
        }
    }

    private static void handleContentClick(PurrtechPVE plugin, Player player, ArmorClassHolder holder, int slot, boolean shift) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        int index = slot - CONTENT_START;
        if (index < 0 || index >= types.size()) {
            return;
        }
        DamageType type = types.get(index);
        ArmorClass armorClass = holder.armorClass();

        if (shift) {
            plugin.getArmorClassProfileRepository().remove(armorClass.name(), type.key());
            player.sendMessage(messages.render(locale, "gui.armor-class.removed",
                    Placeholder.unparsed("type", type.displayName()), Placeholder.unparsed("class", armorClass.name())));
            render(plugin, holder.getInventory(), armorClass, locale);
            return;
        }

        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.armor-class.prompt-set"));
        player.sendMessage(messages.render(locale, "gui.prompt.cancel-hint"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, armorClass);
                return;
            }
            Double percent = parseDouble(rawInput.trim());
            if (percent == null) {
                p.sendMessage(messages.render(locale, "gui.prompt.invalid-number"));
                open(plugin, p, armorClass);
                return;
            }
            plugin.getArmorClassProfileRepository().upsert(armorClass.name(), type.key(), percent);
            p.sendMessage(messages.render(locale, "gui.prompt.done"));
            open(plugin, p, armorClass);
        });
    }

    private static ItemStack tabIcon(Messages messages, Locale locale, Material material, String labelKey, boolean active) {
        String prefixKey = active ? "gui.tab-active" : "gui.tab-inactive";
        Component name = messages.render(locale, prefixKey, Placeholder.unparsed("label", messages.plain(locale, labelKey)));
        return named(material, name);
    }

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

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Material iconFor(String damageTypeKey) {
        return switch (damageTypeKey) {
            case "fire" -> Material.BLAZE_POWDER;
            case "frozen" -> Material.PACKED_ICE;
            case "lightning" -> Material.GLOWSTONE_DUST;
            case "bleed" -> Material.REDSTONE;
            case "spirit" -> Material.GHAST_TEAR;
            case "radiant" -> Material.GLOWSTONE;
            case "holy" -> Material.GOLDEN_APPLE;
            case "shadow" -> Material.COAL;
            case "magic" -> Material.NETHER_STAR;
            case "poison" -> Material.SPIDER_EYE;
            case "explosive" -> Material.TNT;
            case "psychic" -> Material.ENDER_PEARL;
            case "sonic" -> Material.NOTE_BLOCK;
            case "gravity" -> Material.ANVIL;
            case "necrotic" -> Material.BONE;
            case "acid" -> Material.SLIME_BALL;
            case "blunt" -> Material.STICK;
            case "piercing" -> Material.ARROW;
            case "slashing" -> Material.IRON_SWORD;
            case "physical" -> Material.IRON_INGOT;
            default -> Material.PAPER;
        };
    }
}
