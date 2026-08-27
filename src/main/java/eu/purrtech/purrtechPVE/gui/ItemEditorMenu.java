package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code /pve item edit <key>} admin GUI - custom damages / resistances
 * / trinket slots / rebase-preview / publish, one chest inventory reused in
 * place across tab switches (never closed/reopened except around a chat
 * prompt). Every edit here calls straight into {@link
 * eu.purrtech.purrtechPVE.item.ItemTemplateService}, the exact same methods
 * the {@code /pve item ...} commands use - the GUI is a second entry point
 * onto the same service, not a parallel code path.
 */
public final class ItemEditorMenu {

    private static final int SIZE = 54;
    private static final int TAB_BASE = 0;
    private static final int TAB_DAMAGE = 1;
    private static final int TAB_RESIST = 2;
    private static final int TAB_TRINKET = 3;
    private static final int TAB_ARMOR_CLASS = 4;
    private static final int TAB_ARMOR_PENETRATION = 5;
    private static final int TAB_MOBS = 6;
    private static final int TAB_PUBLISH = 7;
    private static final int CLOSE_SLOT = 8;
    private static final int PREVIEW_SLOT = 13;
    private static final int PUBLISH_BUTTON_SLOT = 22;
    private static final int CONTENT_START = 18;

    private ItemEditorMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, String templateKey, ItemEditorTab tab) {
        if (plugin.getItemTemplateService().findByKey(templateKey).isEmpty()) {
            player.sendMessage(Component.text("Šablona " + templateKey + " neexistuje.", NamedTextColor.RED));
            return;
        }
        ItemEditorHolder holder = new ItemEditorHolder(templateKey, tab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Editor: " + templateKey));
        holder.setInventory(inventory);
        render(plugin, inventory, templateKey, tab);
        player.openInventory(inventory);
    }

    private static void reopen(PurrtechPVE plugin, Player player, ItemEditorHolder holder) {
        render(plugin, holder.getInventory(), holder.templateKey(), holder.tab());
        player.openInventory(holder.getInventory());
    }

    private static void switchTab(PurrtechPVE plugin, Player player, ItemEditorHolder holder, ItemEditorTab tab) {
        holder.setTab(tab);
        render(plugin, holder.getInventory(), holder.templateKey(), tab);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, String templateKey, ItemEditorTab tab) {
        inventory.clear();
        drawTabBar(inventory, tab);
        switch (tab) {
            case BASE -> renderBase(plugin, inventory, templateKey);
            case DAMAGE -> renderDamage(plugin, inventory, templateKey);
            case RESIST -> renderResist(plugin, inventory, templateKey);
            case TRINKET -> renderTrinket(plugin, inventory, templateKey);
            case ARMOR_CLASS -> renderArmorClass(plugin, inventory, templateKey);
            case ARMOR_PENETRATION -> renderArmorPenetration(plugin, inventory, templateKey);
            case MOBS -> renderMobs(plugin, inventory, templateKey);
            case PUBLISH -> renderPublish(plugin, inventory, templateKey);
        }
    }

    private static void drawTabBar(Inventory inventory, ItemEditorTab active) {
        inventory.setItem(TAB_BASE, tabIcon(Material.COMPASS, "Základ", active == ItemEditorTab.BASE));
        inventory.setItem(TAB_DAMAGE, tabIcon(Material.BLAZE_POWDER, "Custom Damages", active == ItemEditorTab.DAMAGE));
        inventory.setItem(TAB_RESIST, tabIcon(Material.SHIELD, "Odolnosti / Slabiny", active == ItemEditorTab.RESIST));
        inventory.setItem(TAB_TRINKET, tabIcon(Material.NAME_TAG, "Trinket sloty", active == ItemEditorTab.TRINKET));
        inventory.setItem(TAB_ARMOR_CLASS, tabIcon(Material.IRON_CHESTPLATE, "Typ armoru", active == ItemEditorTab.ARMOR_CLASS));
        inventory.setItem(TAB_ARMOR_PENETRATION, tabIcon(Material.NETHERITE_AXE, "Penetrace armoru", active == ItemEditorTab.ARMOR_PENETRATION));
        inventory.setItem(TAB_MOBS, tabIcon(Material.ZOMBIE_HEAD, "MythicMobs", active == ItemEditorTab.MOBS));
        inventory.setItem(TAB_PUBLISH, tabIcon(Material.EMERALD, "Uložit & Publikovat", active == ItemEditorTab.PUBLISH));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, Component.text("Zavřít", NamedTextColor.RED)));
    }

    private static ItemStack tabIcon(Material material, String label, boolean active) {
        Component name = Component.text((active ? "▶ " : "") + label, active ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        return named(material, name);
    }

    // ---- BASE ----

    private static void renderBase(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        ItemStack preview = plugin.getItemTemplateService().renderGiveable(templateKey);
        ItemMeta meta = preview.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Verze: " + template.version() + " (propsáno: " + template.syncedVersion() + ")", NamedTextColor.GRAY));
        lore.add(Component.text("Drž item v ruce a klikni sem", NamedTextColor.YELLOW));
        lore.add(Component.text("pro změnu základu (materiál).", NamedTextColor.YELLOW));
        meta.lore(lore);
        preview.setItemMeta(meta);
        inventory.setItem(PREVIEW_SLOT, preview);
    }

    private static void handleBaseClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot != PREVIEW_SLOT) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            player.sendMessage(Component.text("Drž v ruce item, který chceš použít jako nový základ.", NamedTextColor.RED));
            return;
        }
        Integer customModelData = held.hasItemMeta() && held.getItemMeta().hasCustomModelData()
                ? held.getItemMeta().getCustomModelData() : null;
        try {
            plugin.getItemTemplateService().rebase(holder.templateKey(), held.getType(), customModelData);
        } catch (TemplateNotFoundException e) {
            player.sendMessage(Component.text("Šablona už neexistuje.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Základ změněn na " + held.getType() + ".", NamedTextColor.GREEN));
        render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.BASE);
    }

    // ---- DAMAGE ----

    private static void renderDamage(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        List<DamageContribution> contributions = plugin.getItemTemplateService().damageContributions(templateKey);
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        for (int i = 0; i < types.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = types.get(i);
            Optional<DamageContribution> wielded = contributions.stream()
                    .filter(c -> c.damageTypeKey().equals(type.key()) && c.context() == ModifierContext.WIELDED).findFirst();
            Optional<DamageContribution> worn = contributions.stream()
                    .filter(c -> c.damageTypeKey().equals(type.key()) && c.context() == ModifierContext.WORN).findFirst();

            List<Component> lore = new ArrayList<>();
            wielded.ifPresent(c -> lore.add(Component.text("Při útoku: " + formatContribution(c), NamedTextColor.WHITE)));
            worn.ifPresent(c -> lore.add(Component.text("Pasivně nasazeno: " + formatContribution(c), NamedTextColor.WHITE)));
            if (lore.isEmpty()) {
                lore.add(Component.text("(nenastaveno)", NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Klik: nastavit (napíšeš částku, flat/percent", NamedTextColor.YELLOW));
            lore.add(Component.text("a wielded/worn do chatu)", NamedTextColor.YELLOW));
            lore.add(Component.text("Shift+klik: smazat obojí (wielded i worn)", NamedTextColor.RED));

            ItemStack icon = named(iconFor(type.key()), Component.text(type.icon() + " " + type.displayName(), NamedTextColor.AQUA));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handleDamageClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        Optional<DamageType> typeOpt = damageTypeAt(plugin, slot);
        if (typeOpt.isEmpty()) {
            return;
        }
        DamageType type = typeOpt.get();
        if (shift) {
            plugin.getItemTemplateService().removeDamageContribution(holder.templateKey(), type.key(), ModifierContext.WIELDED);
            plugin.getItemTemplateService().removeDamageContribution(holder.templateKey(), type.key(), ModifierContext.WORN);
            player.sendMessage(Component.text("Poškození " + type.displayName() + " smazáno.", NamedTextColor.GREEN));
            render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.DAMAGE);
            return;
        }
        promptDamageContribution(plugin, player, holder, type);
    }

    private static void promptDamageContribution(PurrtechPVE plugin, Player player, ItemEditorHolder holder, DamageType type) {
        player.closeInventory();
        player.sendMessage(Component.text("Napiš do chatu: <částka> <flat|percent> <wielded|worn>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Například: 4 flat wielded    (nebo napiš 'zrusit')", NamedTextColor.GRAY));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+");
            if (parts.length != 3) {
                p.sendMessage(Component.text("Neplatný vstup, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
                return;
            }
            Double amount = parseDouble(parts[0]);
            DamageMode mode = parseMode(parts[1]);
            ModifierContext parsedContext = parseContext(parts[2]);
            if (amount == null || mode == null || parsedContext == null) {
                p.sendMessage(Component.text("Neplatný vstup, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
                return;
            }
            try {
                plugin.getItemTemplateService().setDamageContribution(holder.templateKey(), type.key(), amount, mode, parsedContext);
                p.sendMessage(Component.text("Nastaveno.", NamedTextColor.GREEN));
            } catch (TemplateNotFoundException e) {
                p.sendMessage(Component.text("Šablona už neexistuje.", NamedTextColor.RED));
            }
            open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
        });
    }

    // ---- RESIST ----

    private static void renderResist(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        List<TypeModifier> modifiers = plugin.getItemTemplateService().typeModifiers(templateKey);
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        for (int i = 0; i < types.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = types.get(i);
            Optional<TypeModifier> modifier = modifiers.stream().filter(m -> m.damageTypeKey().equals(type.key())).findFirst();

            List<Component> lore = new ArrayList<>();
            if (modifier.isPresent()) {
                double percent = modifier.get().percent();
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

    private static void handleResistClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        Optional<DamageType> typeOpt = damageTypeAt(plugin, slot);
        if (typeOpt.isEmpty()) {
            return;
        }
        DamageType type = typeOpt.get();
        if (shift) {
            plugin.getItemTemplateService().removeTypeModifier(holder.templateKey(), type.key());
            player.sendMessage(Component.text("Odolnost/slabina " + type.displayName() + " smazána.", NamedTextColor.GREEN));
            render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.RESIST);
            return;
        }

        player.closeInventory();
        player.sendMessage(Component.text("Napiš do chatu procenta (kladné = odolnost, záporné = slabina), např. 50 nebo -25", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("(nebo napiš 'zrusit')", NamedTextColor.GRAY));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, holder.templateKey(), ItemEditorTab.RESIST);
                return;
            }
            Double percent = parseDouble(rawInput.trim());
            if (percent == null) {
                p.sendMessage(Component.text("Neplatné číslo, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, holder.templateKey(), ItemEditorTab.RESIST);
                return;
            }
            try {
                plugin.getItemTemplateService().setTypeModifier(holder.templateKey(), type.key(), percent);
                p.sendMessage(Component.text("Nastaveno.", NamedTextColor.GREEN));
            } catch (TemplateNotFoundException e) {
                p.sendMessage(Component.text("Šablona už neexistuje.", NamedTextColor.RED));
            }
            open(plugin, p, holder.templateKey(), ItemEditorTab.RESIST);
        });
    }

    // ---- TRINKET ----

    private static List<String> trinketSlotNames(PurrtechPVE plugin) {
        List<String> names = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            names.add(slot.name());
        }
        names.addAll(plugin.getAccessorySettings().slots());
        return names;
    }

    private static void renderTrinket(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        List<String> slotNames = trinketSlotNames(plugin);
        for (int i = 0; i < slotNames.size() && CONTENT_START + i < SIZE; i++) {
            String slotName = slotNames.get(i);
            boolean selected = template.allowedSlots().contains(slotName);
            NamedTextColor color = selected ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            String mark = selected ? "✔ " : "✘ ";
            ItemStack icon = named(trinketSlotIcon(slotName), Component.text(mark + slotName, color));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(Component.text("Klik: " + (selected ? "odebrat" : "povolit"), NamedTextColor.YELLOW)));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        ItemStack info = named(Material.PAPER, Component.text("Prázdný výběr = bez omezení", NamedTextColor.GRAY));
        inventory.setItem(PREVIEW_SLOT, info);
    }

    private static void handleTrinketClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        List<String> slotNames = trinketSlotNames(plugin);
        int index = slot - CONTENT_START;
        if (index < 0 || index >= slotNames.size()) {
            return;
        }
        String slotName = slotNames.get(index);
        ItemTemplate template = plugin.getItemTemplateService().findByKey(holder.templateKey()).orElseThrow();
        List<String> current = new ArrayList<>(template.allowedSlots());
        if (current.contains(slotName)) {
            current.remove(slotName);
        } else {
            current.add(slotName);
        }
        plugin.getItemTemplateService().setAllowedSlots(holder.templateKey(), current);
        render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.TRINKET);
    }

    private static Material trinketSlotIcon(String slotName) {
        return switch (slotName) {
            case "HAND" -> Material.IRON_SWORD;
            case "OFF_HAND" -> Material.SHIELD;
            case "HEAD" -> Material.LEATHER_HELMET;
            case "CHEST" -> Material.LEATHER_CHESTPLATE;
            case "LEGS" -> Material.LEATHER_LEGGINGS;
            case "FEET" -> Material.LEATHER_BOOTS;
            default -> Material.NAME_TAG;
        };
    }

    // ---- ARMOR_CLASS ----

    private static void renderArmorClass(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        ArmorClass current = template.armorClass();

        armorClassOption(inventory, CONTENT_START, null, "Žádný", Material.BARRIER, current);
        armorClassOption(inventory, CONTENT_START + 1, ArmorClass.LIGHT, "Lehký", Material.LEATHER_CHESTPLATE, current);
        armorClassOption(inventory, CONTENT_START + 2, ArmorClass.MEDIUM, "Střední", Material.IRON_CHESTPLATE, current);
        armorClassOption(inventory, CONTENT_START + 3, ArmorClass.HEAVY, "Těžký", Material.NETHERITE_CHESTPLATE, current);

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("Medium = vypadá jako vanilla armor.", NamedTextColor.GRAY));
        infoLore.add(Component.text("Light/Heavy = vlastní vzhled (base +", NamedTextColor.GRAY));
        infoLore.add(Component.text("custom model data v Základ tabu).", NamedTextColor.GRAY));
        infoLore.add(Component.empty());
        infoLore.add(Component.text("Klik: upravit benefity všech typů armoru", NamedTextColor.YELLOW));
        ItemStack info = named(Material.WRITABLE_BOOK, Component.text("Benefity typů armoru", NamedTextColor.AQUA));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);
    }

    private static void armorClassOption(Inventory inventory, int slot, ArmorClass value, String label, Material material, ArmorClass current) {
        boolean selected = value == current;
        NamedTextColor color = selected ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        String mark = selected ? "✔ " : "";
        ItemStack icon = named(material, Component.text(mark + label, color));
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(Component.text("Klik: nastavit", NamedTextColor.YELLOW)));
        icon.setItemMeta(meta);
        inventory.setItem(slot, icon);
    }

    private static void handleArmorClassClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot == PREVIEW_SLOT) {
            ArmorClass current = plugin.getItemTemplateService().findByKey(holder.templateKey()).orElseThrow().armorClass();
            ArmorClassMenu.open(plugin, player, current != null ? current : ArmorClass.LIGHT);
            return;
        }
        int index = slot - CONTENT_START;
        if (index < 0 || index > 3) {
            return;
        }
        ArmorClass newValue = switch (index) {
            case 1 -> ArmorClass.LIGHT;
            case 2 -> ArmorClass.MEDIUM;
            case 3 -> ArmorClass.HEAVY;
            default -> null;
        };
        plugin.getItemTemplateService().setArmorClass(holder.templateKey(), newValue);
        render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.ARMOR_CLASS);
    }

    // ---- ARMOR_PENETRATION ----

    private static void renderArmorPenetration(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        List<ArmorPenetration> penetration = plugin.getItemTemplateService().armorPenetration(templateKey);
        ArmorClass[] classes = ArmorClass.values();
        for (int i = 0; i < classes.length; i++) {
            ArmorClass armorClass = classes[i];
            Optional<ArmorPenetration> current = penetration.stream()
                    .filter(p -> p.armorClass() == armorClass).findFirst();

            List<Component> lore = new ArrayList<>();
            if (current.isPresent()) {
                lore.add(Component.text("Penetrace: " + formatAmount(current.get().amount()) + "%", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("(nenastaveno)", NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Klik: nastavit", NamedTextColor.YELLOW));
            lore.add(Component.text("Shift+klik: smazat", NamedTextColor.RED));

            ItemStack icon = named(armorClassIcon(armorClass), Component.text(armorClass.name(), NamedTextColor.AQUA));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }

        ItemStack info = named(Material.PAPER, Component.text("Před výpočtem poškození sníží", NamedTextColor.GRAY));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(
                Component.text("cílovu odolnost z benefitu daného", NamedTextColor.GRAY),
                Component.text("typu armoru o tolik procentních", NamedTextColor.GRAY),
                Component.text("bodů. Nic se přitom nemaže z invu.", NamedTextColor.GRAY)));
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);
    }

    private static void handleArmorPenetrationClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        ArmorClass[] classes = ArmorClass.values();
        int index = slot - CONTENT_START;
        if (index < 0 || index >= classes.length) {
            return;
        }
        ArmorClass armorClass = classes[index];

        if (shift) {
            plugin.getItemTemplateService().removeArmorPenetration(holder.templateKey(), armorClass);
            player.sendMessage(Component.text("Penetrace armoru " + armorClass.name() + " smazána.", NamedTextColor.GREEN));
            render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.ARMOR_PENETRATION);
            return;
        }

        player.closeInventory();
        player.sendMessage(Component.text("Napiš do chatu procenta penetrace, např. 10", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("(nebo napiš 'zrusit')", NamedTextColor.GRAY));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(Component.text("Zrušeno.", NamedTextColor.GRAY));
                open(plugin, p, holder.templateKey(), ItemEditorTab.ARMOR_PENETRATION);
                return;
            }
            Double amount = parseDouble(rawInput.trim());
            if (amount == null) {
                p.sendMessage(Component.text("Neplatné číslo, zkus to znovu z menu.", NamedTextColor.RED));
                open(plugin, p, holder.templateKey(), ItemEditorTab.ARMOR_PENETRATION);
                return;
            }
            try {
                plugin.getItemTemplateService().setArmorPenetration(holder.templateKey(), armorClass, amount);
                p.sendMessage(Component.text("Nastaveno.", NamedTextColor.GREEN));
            } catch (TemplateNotFoundException e) {
                p.sendMessage(Component.text("Šablona už neexistuje.", NamedTextColor.RED));
            }
            open(plugin, p, holder.templateKey(), ItemEditorTab.ARMOR_PENETRATION);
        });
    }

    private static Material armorClassIcon(ArmorClass armorClass) {
        return switch (armorClass) {
            case LIGHT -> Material.LEATHER_CHESTPLATE;
            case MEDIUM -> Material.IRON_CHESTPLATE;
            case HEAVY -> Material.NETHERITE_CHESTPLATE;
        };
    }

    // ---- MOBS ----

    private static void renderMobs(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        if (plugin.getMythicMobsBridge() == null) {
            ItemStack info = named(Material.BARRIER, Component.text("MythicMobs není dostupný", NamedTextColor.RED));
            ItemMeta infoMeta = info.getItemMeta();
            infoMeta.lore(List.of(
                    Component.text("Buď není na serveru nainstalovaný,", NamedTextColor.GRAY),
                    Component.text("nebo jeho verze neodpovídá tomu,", NamedTextColor.GRAY),
                    Component.text("co tenhle plugin očekává.", NamedTextColor.GRAY)));
            info.setItemMeta(infoMeta);
            inventory.setItem(PREVIEW_SLOT, info);
            return;
        }

        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        List<String> mobTypes = listMobTypesSafely(plugin);
        for (int i = 0; i < mobTypes.size() && CONTENT_START + i < SIZE; i++) {
            String mobType = mobTypes.get(i);
            Map<String, UUID> equipment = plugin.getMobEquipmentRepository().findByMob(mobType);
            Optional<String> assignedSlot = equipment.entrySet().stream()
                    .filter(e -> e.getValue().equals(template.id())).map(Map.Entry::getKey).findFirst();

            List<Component> lore = new ArrayList<>();
            if (assignedSlot.isPresent()) {
                lore.add(Component.text("Nasazeno v: " + assignedSlot.get(), NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("(nenasazeno)", NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Klik: dát tenhle item mobovi", NamedTextColor.YELLOW));
            lore.add(Component.text("(slot se určí podle materiálu)", NamedTextColor.YELLOW));
            lore.add(Component.text("Shift+klik: odebrat", NamedTextColor.RED));

            ItemStack icon = named(Material.ZOMBIE_HEAD, Component.text(mobType, NamedTextColor.AQUA));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handleMobsClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        if (plugin.getMythicMobsBridge() == null) {
            return;
        }
        List<String> mobTypes = listMobTypesSafely(plugin);
        int index = slot - CONTENT_START;
        if (index < 0 || index >= mobTypes.size()) {
            return;
        }
        String mobType = mobTypes.get(index);
        ItemTemplate template = plugin.getItemTemplateService().findByKey(holder.templateKey()).orElseThrow();

        if (shift) {
            Map<String, UUID> equipment = plugin.getMobEquipmentRepository().findByMob(mobType);
            Optional<String> assignedSlot = equipment.entrySet().stream()
                    .filter(e -> e.getValue().equals(template.id())).map(Map.Entry::getKey).findFirst();
            if (assignedSlot.isPresent()) {
                plugin.getMobEquipmentRepository().remove(mobType, assignedSlot.get());
                player.sendMessage(Component.text("Odebráno z moba " + mobType + ".", NamedTextColor.GREEN));
            }
        } else {
            EquipmentSlot equipSlot = autoSlotFor(template.baseMaterial());
            plugin.getMobEquipmentRepository().set(mobType, equipSlot.name(), template.id());
            player.sendMessage(Component.text("Item nasazen mobovi " + mobType + " do slotu "
                    + equipSlot.name() + ".", NamedTextColor.GREEN));
        }
        render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.MOBS);
    }

    /** Guesses the equipment slot from the base material's vanilla naming convention (helmet/chestplate/... suffix, shield). */
    private static EquipmentSlot autoSlotFor(Material baseMaterial) {
        String name = baseMaterial.name();
        if (name.endsWith("_HELMET") || name.equals("CARVED_PUMPKIN") || name.equals("TURTLE_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        if (name.equals("SHIELD")) {
            return EquipmentSlot.OFF_HAND;
        }
        return EquipmentSlot.HAND;
    }

    private static List<String> listMobTypesSafely(PurrtechPVE plugin) {
        try {
            return plugin.getMythicMobsBridge().listMobTypeInternalNames();
        } catch (Throwable t) {
            return List.of();
        }
    }

    // ---- PUBLISH ----

    private static void renderPublish(PurrtechPVE plugin, Inventory inventory, String templateKey) {
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("Aktuální verze: " + template.version(), NamedTextColor.WHITE));
        infoLore.add(Component.text("Propsáno do oběhu: " + template.syncedVersion(), NamedTextColor.WHITE));
        infoLore.add(Component.empty());
        infoLore.add(template.isFullySynced()
                ? Component.text("Vše propsáno.", NamedTextColor.GREEN)
                : Component.text("Máš neuložené změny čekající na propsání.", NamedTextColor.YELLOW));
        ItemStack info = named(Material.WRITABLE_BOOK, Component.text("Stav šablony", NamedTextColor.AQUA));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);

        List<Component> buttonLore = List.of(
                Component.text("Přerenderuje všechny itemy této šablony", NamedTextColor.GRAY),
                Component.text("u online hráčů. Offline hráči doženou", NamedTextColor.GRAY),
                Component.text("verzi při příštím přihlášení.", NamedTextColor.GRAY));
        ItemStack button = named(Material.EMERALD_BLOCK, Component.text("Propsat do oběhu teď", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD));
        ItemMeta buttonMeta = button.getItemMeta();
        buttonMeta.lore(buttonLore);
        button.setItemMeta(buttonMeta);
        inventory.setItem(PUBLISH_BUTTON_SLOT, button);
    }

    private static void handlePublishClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot != PUBLISH_BUTTON_SLOT) {
            return;
        }
        plugin.getItemTemplateService().propagate(holder.templateKey());
        int touched = plugin.getItemSyncService().resyncAllOnlinePlayers();
        player.sendMessage(Component.text("Propsáno do oběhu. Aktualizováno " + touched + " itemů u online hráčů.", NamedTextColor.GREEN));
        render(plugin, holder.getInventory(), holder.templateKey(), ItemEditorTab.PUBLISH);
    }

    // ---- shared click routing ----

    public static void handleClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        if (plugin.getItemTemplateService().findByKey(holder.templateKey()).isEmpty()) {
            player.sendMessage(Component.text("Šablona byla mezitím smazána.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        switch (slot) {
            case TAB_BASE -> switchTab(plugin, player, holder, ItemEditorTab.BASE);
            case TAB_DAMAGE -> switchTab(plugin, player, holder, ItemEditorTab.DAMAGE);
            case TAB_RESIST -> switchTab(plugin, player, holder, ItemEditorTab.RESIST);
            case TAB_TRINKET -> switchTab(plugin, player, holder, ItemEditorTab.TRINKET);
            case TAB_ARMOR_CLASS -> switchTab(plugin, player, holder, ItemEditorTab.ARMOR_CLASS);
            case TAB_ARMOR_PENETRATION -> switchTab(plugin, player, holder, ItemEditorTab.ARMOR_PENETRATION);
            case TAB_MOBS -> switchTab(plugin, player, holder, ItemEditorTab.MOBS);
            case TAB_PUBLISH -> switchTab(plugin, player, holder, ItemEditorTab.PUBLISH);
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
                switch (holder.tab()) {
                    case BASE -> handleBaseClick(plugin, player, holder, slot);
                    case DAMAGE -> handleDamageClick(plugin, player, holder, slot, shift);
                    case RESIST -> handleResistClick(plugin, player, holder, slot, shift);
                    case TRINKET -> handleTrinketClick(plugin, player, holder, slot);
                    case ARMOR_CLASS -> handleArmorClassClick(plugin, player, holder, slot);
                    case ARMOR_PENETRATION -> handleArmorPenetrationClick(plugin, player, holder, slot, shift);
                    case MOBS -> handleMobsClick(plugin, player, holder, slot, shift);
                    case PUBLISH -> handlePublishClick(plugin, player, holder, slot);
                }
            }
        }
    }

    // ---- helpers ----

    private static Optional<DamageType> damageTypeAt(PurrtechPVE plugin, int slot) {
        int index = slot - CONTENT_START;
        List<DamageType> types = plugin.getDamageTypeRegistry().all().values().stream().toList();
        if (index < 0 || index >= types.size()) {
            return Optional.empty();
        }
        return Optional.of(types.get(index));
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static String formatContribution(DamageContribution c) {
        return c.mode() == DamageMode.PERCENT_OF_TOTAL
                ? "+" + formatAmount(c.amount()) + "%"
                : "+" + formatAmount(c.amount());
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

    private static DamageMode parseMode(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "flat" -> DamageMode.FLAT;
            case "percent", "percent_of_total" -> DamageMode.PERCENT_OF_TOTAL;
            default -> null;
        };
    }

    private static ModifierContext parseContext(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "wielded" -> ModifierContext.WIELDED;
            case "worn" -> ModifierContext.WORN;
            default -> null;
        };
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
