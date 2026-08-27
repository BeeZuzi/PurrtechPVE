package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.item.ArmorClass;
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
        ArmorClassHolder holder = new ArmorClassHolder(armorClass);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Typy armoru: benefity"));
        holder.setInventory(inventory);
        render(plugin, inventory, armorClass);
        player.openInventory(inventory);
    }

    private static void switchTab(PurrtechPVE plugin, ArmorClassHolder holder, ArmorClass armorClass) {
        holder.setArmorClass(armorClass);
        render(plugin, holder.getInventory(), armorClass);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, ArmorClass armorClass) {
        inventory.clear();
        inventory.setItem(TAB_LIGHT, tabIcon(Material.LEATHER_CHESTPLATE, "Lehký", armorClass == ArmorClass.LIGHT));
        inventory.setItem(TAB_MEDIUM, tabIcon(Material.IRON_CHESTPLATE, "Střední", armorClass == ArmorClass.MEDIUM));
        inventory.setItem(TAB_HEAVY, tabIcon(Material.NETHERITE_CHESTPLATE, "Těžký", armorClass == ArmorClass.HEAVY));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, Component.text("Zavřít", NamedTextColor.RED)));

        Map<String, Double> profile = plugin.getArmorClassProfileRepository().findByArmorClass(armorClass.name());
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        for (int i = 0; i < types.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = types.get(i);
            Double percent = profile.get(type.key());

            List<Component> lore = new ArrayList<>();
            if (percent != null) {
                String label = percent >= 0 ? "Odolnost" : "Slabina";
                NamedTextColor color = percent >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                lore.add(Component.text(label + ": " + formatAmount(Math.abs(percent)) + "%", color));
            } else {
                lore.add(Component.text("(nenastaveno)", NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Klik: nastavit (kladné = odolnost, záporné = slabina)", NamedTextColor.YELLOW));
            lore.add(Component.text("Shift+klik: smazat", NamedTextColor.RED));

            ItemStack icon = named(iconFor(type.key()), Component.text(type.icon() + " " + type.displayName(), NamedTextColor.AQUA));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    public static void handleClick(PurrtechPVE plugin, Player player, ArmorClassHolder holder, int slot, boolean shift) {
        switch (slot) {
            case TAB_LIGHT -> switchTab(plugin, holder, ArmorClass.LIGHT);
            case TAB_MEDIUM -> switchTab(plugin, holder, ArmorClass.MEDIUM);
            case TAB_HEAVY -> switchTab(plugin, holder, ArmorClass.HEAVY);
            case CLOSE_SLOT -> player.closeInventory();
            default -> handleContentClick(plugin, player, holder, slot, shift);
        }
    }

    private static void handleContentClick(PurrtechPVE plugin, Player player, ArmorClassHolder holder, int slot, boolean shift) {
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        int index = slot - CONTENT_START;
        if (index < 0 || index >= types.size()) {
            return;
        }
        DamageType type = types.get(index);
        ArmorClass armorClass = holder.armorClass();

        if (shift) {
            plugin.getArmorClassProfileRepository().remove(armorClass.name(), type.key());
            player.sendMessage(Component.text("Odolnost/slabina " + type.displayName() + " smazána pro typ armoru "
                    + armorClass.name() + ".", NamedTextColor.GREEN));
            render(plugin, holder.getInventory(), armorClass);
            return;
        }

        player.closeInventory();
        player.sendMessage(Component.text("Napiš do chatu procenta (kladné = odolnost, záporné = slabina), např. 50 nebo -25", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("(nebo napiš 'zrusit')", NamedTextColor.GRAY));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, armorClass);
                return;
            }
            Double percent = parseDouble(rawInput.trim());
            if (percent == null) {
                p.sendMessage(Component.text("Neplatné číslo, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, armorClass);
                return;
            }
            plugin.getArmorClassProfileRepository().upsert(armorClass.name(), type.key(), percent);
            p.sendMessage(Component.text("Nastaveno.", NamedTextColor.GREEN));
            open(plugin, p, armorClass);
        });
    }

    private static ItemStack tabIcon(Material material, String label, boolean active) {
        Component name = Component.text((active ? "▶ " : "") + label, active ? NamedTextColor.GREEN : NamedTextColor.WHITE);
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
