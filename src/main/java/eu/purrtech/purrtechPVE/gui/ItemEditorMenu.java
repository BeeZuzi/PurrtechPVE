package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.AttributeModifierEntry;
import eu.purrtech.purrtechPVE.item.AttributeSlots;
import eu.purrtech.purrtechPVE.item.BaseItemSnapshots;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.LoreHeader;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final int TAB_SPECIAL_EFFECTS = 6;
    private static final int TAB_MOBS = 7;
    private static final int TAB_PUBLISH = 8;
    // Row 0 (slots 0-8) is fully packed with tabs above, so Close lives at the end of row 1
    // instead - free in every tab (PREVIEW_SLOT is the only other row-1 slot used, and it's
    // distinct from this one).
    private static final int CLOSE_SLOT = 17;
    private static final int PREVIEW_SLOT = 13;
    // The section-header show/hide toggle (DAMAGE/RESIST/ARMOR_PENETRATION, and BASE for its
    // attributes header) - slot 9 is free in row 1 on every tab that uses it, distinct from
    // PREVIEW_SLOT/CLOSE_SLOT above. DAMAGE alone needs a second one (slot 10): it has two
    // separate headers, "Damage on hit" (wielded) and "Passive bonus" (worn).
    private static final int HEADER_TOGGLE_SLOT = 9;
    private static final int HEADER_TOGGLE_SLOT_2 = 10;
    private static final int PUBLISH_BUTTON_SLOT = 22;
    private static final int CONTENT_START = 18;

    private ItemEditorMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, String templateKey, ItemEditorTab tab) {
        Locale locale = player.locale();
        if (plugin.getItemTemplateService().findByKey(templateKey).isEmpty()) {
            player.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", templateKey)));
            return;
        }
        ItemEditorHolder holder = new ItemEditorHolder(templateKey, tab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                plugin.getMessages().render(locale, "gui.item-editor.title", Placeholder.unparsed("key", templateKey)));
        holder.setInventory(inventory);
        render(plugin, inventory, holder, locale);
        player.openInventory(inventory);
    }

    private static void reopen(PurrtechPVE plugin, Player player, ItemEditorHolder holder) {
        render(plugin, holder.getInventory(), holder, player.locale());
        player.openInventory(holder.getInventory());
    }

    private static void switchTab(PurrtechPVE plugin, Player player, ItemEditorHolder holder, ItemEditorTab tab) {
        holder.setTab(tab);
        holder.setPickerOpen(false);
        render(plugin, holder.getInventory(), holder, player.locale());
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, ItemEditorHolder holder, Locale locale) {
        String templateKey = holder.templateKey();
        ItemEditorTab tab = holder.tab();
        inventory.clear();
        drawTabBar(plugin, inventory, tab, locale);
        switch (tab) {
            case BASE -> renderBase(plugin, inventory, holder, locale);
            case DAMAGE -> renderDamage(plugin, inventory, holder, locale);
            case RESIST -> renderResist(plugin, inventory, holder, locale);
            case TRINKET -> renderTrinket(plugin, inventory, templateKey, locale);
            case ARMOR_CLASS -> renderArmorClass(plugin, inventory, templateKey, locale);
            case ARMOR_PENETRATION -> renderArmorPenetration(plugin, inventory, templateKey, locale);
            case SPECIAL_EFFECTS -> renderSpecialEffects(plugin, inventory, templateKey, locale);
            case MOBS -> renderMobs(plugin, inventory, holder, locale);
            case PUBLISH -> renderPublish(plugin, inventory, templateKey, locale);
        }
    }

    private static void drawTabBar(PurrtechPVE plugin, Inventory inventory, ItemEditorTab active, Locale locale) {
        Messages messages = plugin.getMessages();
        inventory.setItem(TAB_BASE, tabIcon(messages, locale, Material.COMPASS, "gui.item-editor.tab.base", active == ItemEditorTab.BASE));
        inventory.setItem(TAB_DAMAGE, tabIcon(messages, locale, Material.BLAZE_POWDER, "gui.item-editor.tab.damage", active == ItemEditorTab.DAMAGE));
        inventory.setItem(TAB_RESIST, tabIcon(messages, locale, Material.SHIELD, "gui.item-editor.tab.resist", active == ItemEditorTab.RESIST));
        inventory.setItem(TAB_TRINKET, tabIcon(messages, locale, Material.NAME_TAG, "gui.item-editor.tab.trinket", active == ItemEditorTab.TRINKET));
        inventory.setItem(TAB_ARMOR_CLASS, tabIcon(messages, locale, Material.IRON_CHESTPLATE, "gui.item-editor.tab.armor-class", active == ItemEditorTab.ARMOR_CLASS));
        inventory.setItem(TAB_ARMOR_PENETRATION, tabIcon(messages, locale, Material.NETHERITE_AXE, "gui.item-editor.tab.armor-penetration", active == ItemEditorTab.ARMOR_PENETRATION));
        inventory.setItem(TAB_SPECIAL_EFFECTS, tabIcon(messages, locale, Material.REDSTONE, "gui.item-editor.tab.special-effects", active == ItemEditorTab.SPECIAL_EFFECTS));
        inventory.setItem(TAB_MOBS, tabIcon(messages, locale, Material.ZOMBIE_HEAD, "gui.item-editor.tab.mobs", active == ItemEditorTab.MOBS));
        inventory.setItem(TAB_PUBLISH, tabIcon(messages, locale, Material.EMERALD, "gui.item-editor.tab.publish", active == ItemEditorTab.PUBLISH));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
    }

    private static ItemStack tabIcon(Messages messages, Locale locale, Material material, String labelKey, boolean active) {
        String prefixKey = active ? "gui.tab-active" : "gui.tab-inactive";
        Component name = messages.render(locale, prefixKey, Placeholder.unparsed("label", messages.plain(locale, labelKey)));
        return named(material, name);
    }

    // ---- BASE ----
    // Preview/rebase stays at PREVIEW_SLOT as before; the content area below it now also lists
    // this template's real vanilla Attribute bonuses (max health, attack damage, movement speed,
    // ...) - same "only configured + Add button" shape as DAMAGE/RESIST/MOBS, sharing the same
    // ItemEditorHolder.pickerOpen flag (BASE's own picker temporarily takes over PREVIEW_SLOT for
    // its "Zpět" button, same trade-off DAMAGE/RESIST already make). Unlike those, the picker here
    // lists ALL attributes every time rather than excluding already-added ones: a single attribute
    // can legitimately have more than one entry (a ring on AMULET and boots on FEET both granting
    // SAFE_FALL_DISTANCE, say), since the real identity is (attribute, slot), not attribute alone.

    private static void renderBase(PurrtechPVE plugin, Inventory inventory, ItemEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        String templateKey = holder.templateKey();
        if (holder.isPickerOpen()) {
            renderAttributePicker(messages, locale, inventory);
            return;
        }
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        ItemStack preview = plugin.getItemTemplateService().renderGiveable(templateKey);
        ItemMeta meta = preview.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(messages.render(locale, "gui.item-editor.base.version",
                Placeholder.unparsed("version", String.valueOf(template.version())), Placeholder.unparsed("synced", String.valueOf(template.syncedVersion()))));
        lore.add(messages.render(locale, "gui.item-editor.base.rebase-hint-1"));
        lore.add(messages.render(locale, "gui.item-editor.base.rebase-hint-2"));
        meta.lore(lore);
        preview.setItemMeta(meta);
        inventory.setItem(PREVIEW_SLOT, preview);
        inventory.setItem(HEADER_TOGGLE_SLOT, headerToggleIcon(plugin, locale, templateKey, LoreHeader.ATTRIBUTES));

        List<AttributeModifierEntry> entries = plugin.getItemTemplateService().attributeModifiers(templateKey);
        for (int i = 0; i < entries.size() && CONTENT_START + i < SIZE; i++) {
            AttributeModifierEntry entry = entries.get(i);
            List<Component> entryLore = new ArrayList<>();
            entryLore.add(messages.render(locale, "gui.item-editor.base.attribute-amount",
                    Placeholder.unparsed("amount", formatAttributeAmount(entry)), Placeholder.unparsed("attribute", entry.attribute().name())));
            entryLore.add(messages.render(locale, "gui.item-editor.base.attribute-slot", Placeholder.unparsed("slot", entry.slot())));
            entryLore.add(Component.empty());
            entryLore.add(messages.render(locale, "gui.item-editor.base.attribute-hint-edit-1"));
            entryLore.add(messages.render(locale, "gui.item-editor.base.attribute-hint-edit-2"));
            entryLore.add(messages.render(locale, "gui.item-editor.hint-shift-delete"));
            ItemStack icon = named(Material.NETHER_STAR, messages.render(locale, "gui.item-editor.base.attribute-icon",
                    Placeholder.unparsed("attribute", entry.attribute().name())));
            ItemMeta entryMeta = icon.getItemMeta();
            entryMeta.lore(entryLore);
            icon.setItemMeta(entryMeta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        int addSlot = CONTENT_START + entries.size();
        if (addSlot < SIZE) {
            inventory.setItem(addSlot, addButton(messages, locale, "gui.item-editor.base.add-attribute"));
        }
    }

    private static void renderAttributePicker(Messages messages, Locale locale, Inventory inventory) {
        Attribute[] attributes = Attribute.values();
        for (int i = 0; i < attributes.length && CONTENT_START + i < SIZE; i++) {
            Attribute attribute = attributes[i];
            ItemStack icon = named(Material.NETHER_STAR, messages.render(locale, "gui.item-editor.base.attribute-icon",
                    Placeholder.unparsed("attribute", attribute.name())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(messages.render(locale, "gui.item-editor.base.picker-hint-add")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        ItemStack back = named(Material.ARROW, messages.render(locale, "gui.item-editor.back-red"));
        ItemMeta backMeta = back.getItemMeta();
        backMeta.lore(List.of(messages.render(locale, "gui.item-editor.base.picker-hint-back")));
        back.setItemMeta(backMeta);
        inventory.setItem(PREVIEW_SLOT, back);
    }

    private static String formatAttributeAmount(AttributeModifierEntry entry) {
        return (entry.amount() >= 0 ? "+" : "") + formatAmount(entry.amount())
                + (entry.operation() == AttributeModifier.Operation.ADD_NUMBER ? "" : "%");
    }

    private static void handleBaseClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        Locale locale = player.locale();
        if (holder.isPickerOpen()) {
            handleAttributePickerClick(plugin, player, holder, slot);
            return;
        }
        if (slot == PREVIEW_SLOT) {
            handleRebaseClick(plugin, player, holder);
            return;
        }
        if (slot == HEADER_TOGGLE_SLOT) {
            handleHeaderToggleClick(plugin, player, holder, LoreHeader.ATTRIBUTES);
            return;
        }
        List<AttributeModifierEntry> entries = plugin.getItemTemplateService().attributeModifiers(holder.templateKey());
        int index = slot - CONTENT_START;
        if (index == entries.size()) {
            holder.setPickerOpen(true);
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }
        if (index < 0 || index >= entries.size()) {
            return;
        }
        AttributeModifierEntry entry = entries.get(index);
        if (shift) {
            plugin.getItemTemplateService().removeAttributeModifier(holder.templateKey(), entry.attribute(), entry.slot());
            player.sendMessage(plugin.getMessages().render(locale, "gui.item-editor.base.attribute-removed",
                    Placeholder.unparsed("attribute", entry.attribute().name()), Placeholder.unparsed("slot", entry.slot())));
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }
        // Already has a slot/operation from when it was created - nothing non-numeric left to
        // pick, so straight into the +/- editor instead of re-running the whole chat prompt.
        ValueEditorMenu.open(plugin, player, holder.templateKey(), ValueEditorKind.ATTRIBUTE, entry.attribute().name() + "|" + entry.slot());
    }

    private static void handleRebaseClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            player.sendMessage(messages.render(locale, "gui.item-editor.base.rebase-empty-hand"));
            return;
        }
        Integer customModelData = held.hasItemMeta() && held.getItemMeta().hasCustomModelData()
                ? held.getItemMeta().getCustomModelData() : null;
        byte[] baseItemSnapshot = BaseItemSnapshots.capture(held);
        try {
            plugin.getItemTemplateService().rebase(holder.templateKey(), held.getType(), customModelData, baseItemSnapshot);
        } catch (TemplateNotFoundException e) {
            player.sendMessage(messages.render(locale, "gui.item-editor.template-gone"));
            return;
        }
        player.sendMessage(messages.render(locale, "gui.item-editor.base.rebase-done", Placeholder.unparsed("material", held.getType().name())));
        render(plugin, holder.getInventory(), holder, locale);
    }

    private static void handleAttributePickerClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot == PREVIEW_SLOT) {
            holder.setPickerOpen(false);
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        Attribute[] attributes = Attribute.values();
        int index = slot - CONTENT_START;
        if (index < 0 || index >= attributes.length) {
            return;
        }
        promptAttributeModifier(plugin, player, holder, attributes[index]);
    }

    private static void promptAttributeModifier(PurrtechPVE plugin, Player player, ItemEditorHolder holder, Attribute attribute) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.item-editor.base.prompt-attribute-1"));
        player.sendMessage(messages.render(locale, "gui.item-editor.base.prompt-attribute-2"));
        player.sendMessage(messages.render(locale, "gui.item-editor.base.prompt-attribute-3",
                Placeholder.unparsed("slots", String.join("/", plugin.getAccessorySettings().slots()))));
        player.sendMessage(messages.render(locale, "gui.item-editor.base.prompt-attribute-4"));
        player.sendMessage(messages.render(locale, "gui.item-editor.base.prompt-attribute-example"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, holder.templateKey(), ItemEditorTab.BASE);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+");
            if (parts.length != 3) {
                p.sendMessage(messages.render(locale, "gui.item-editor.invalid-input"));
                open(plugin, p, holder.templateKey(), ItemEditorTab.BASE);
                return;
            }
            String slotName = AttributeSlots.parse(parts[0], plugin.getAccessorySettings().slots());
            Double amount = parseDouble(parts[1]);
            AttributeModifier.Operation operation = parseOperation(parts[2]);
            if (slotName == null || amount == null || operation == null) {
                p.sendMessage(messages.render(locale, "gui.item-editor.invalid-input"));
                open(plugin, p, holder.templateKey(), ItemEditorTab.BASE);
                return;
            }
            try {
                plugin.getItemTemplateService().setAttributeModifier(holder.templateKey(), attribute, amount, operation, slotName);
                p.sendMessage(messages.render(locale, "gui.prompt.done"));
            } catch (TemplateNotFoundException e) {
                p.sendMessage(messages.render(locale, "gui.item-editor.template-gone"));
            }
            open(plugin, p, holder.templateKey(), ItemEditorTab.BASE);
        });
    }

    // ---- DAMAGE ----
    // Only damage types the item already has a contribution for are listed, followed by an
    // "Add" button - clicking it flips ItemEditorHolder.pickerOpen and the same tab
    // re-renders as a picker of the remaining (not yet configured) types instead. Picking one
    // there (or clicking an already-listed type) both funnel into the same chat prompt, which is
    // where flat-vs-percent is actually chosen (see promptDamageContribution) - the picker only
    // decides *which* damage type you're about to configure, not flat/percent.

    private static void renderDamage(PurrtechPVE plugin, Inventory inventory, ItemEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        String templateKey = holder.templateKey();
        if (holder.isPickerOpen()) {
            renderDamageTypePicker(plugin, inventory, templateKey, locale);
            return;
        }
        inventory.setItem(HEADER_TOGGLE_SLOT, headerToggleIcon(plugin, locale, templateKey, LoreHeader.DAMAGE));
        inventory.setItem(HEADER_TOGGLE_SLOT_2, headerToggleIcon(plugin, locale, templateKey, LoreHeader.PASSIVE));
        List<DamageContribution> contributions = plugin.getItemTemplateService().damageContributions(templateKey);
        List<DamageType> configured = configuredDamageTypes(plugin, contributions);
        for (int i = 0; i < configured.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = configured.get(i);
            Optional<DamageContribution> wielded = contributions.stream()
                    .filter(c -> c.damageTypeKey().equals(type.key()) && c.context() == ModifierContext.WIELDED).findFirst();
            Optional<DamageContribution> worn = contributions.stream()
                    .filter(c -> c.damageTypeKey().equals(type.key()) && c.context() == ModifierContext.WORN).findFirst();

            List<Component> lore = new ArrayList<>();
            wielded.ifPresent(c -> lore.add(messages.render(locale, "gui.item-editor.damage.wielded-line", Placeholder.unparsed("amount", formatContribution(c)))));
            worn.ifPresent(c -> lore.add(messages.render(locale, "gui.item-editor.damage.worn-line", Placeholder.unparsed("amount", formatContribution(c)))));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-editor.damage.hint-edit-1"));
            lore.add(messages.render(locale, "gui.item-editor.damage.hint-edit-2"));
            lore.add(messages.render(locale, "gui.item-editor.damage.hint-shift-delete"));

            ItemStack icon = named(iconFor(type.key()), messages.render(locale, "gui.type-icon",
                    Placeholder.unparsed("icon", type.icon()), Placeholder.unparsed("type", type.displayName())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        int addSlot = CONTENT_START + configured.size();
        if (addSlot < SIZE) {
            inventory.setItem(addSlot, addButton(messages, locale, "gui.item-editor.damage.add"));
        }
    }

    private static void renderDamageTypePicker(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        List<DamageContribution> contributions = plugin.getItemTemplateService().damageContributions(templateKey);
        renderTypePicker(plugin.getMessages(), locale, inventory, unconfiguredDamageTypes(plugin, contributions));
    }

    private static List<DamageType> configuredDamageTypes(PurrtechPVE plugin, List<DamageContribution> contributions) {
        Set<String> keys = contributions.stream().map(DamageContribution::damageTypeKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return plugin.getDamageTypeRegistry().all().values().stream().filter(t -> keys.contains(t.key())).toList();
    }

    private static List<DamageType> unconfiguredDamageTypes(PurrtechPVE plugin, List<DamageContribution> contributions) {
        Set<String> keys = contributions.stream().map(DamageContribution::damageTypeKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return plugin.getDamageTypeRegistry().all().values().stream().filter(t -> !keys.contains(t.key())).toList();
    }

    private static void handleDamageClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        if (holder.isPickerOpen()) {
            handleDamageTypePickerClick(plugin, player, holder, slot);
            return;
        }
        if (slot == HEADER_TOGGLE_SLOT) {
            handleHeaderToggleClick(plugin, player, holder, LoreHeader.DAMAGE);
            return;
        }
        if (slot == HEADER_TOGGLE_SLOT_2) {
            handleHeaderToggleClick(plugin, player, holder, LoreHeader.PASSIVE);
            return;
        }
        List<DamageContribution> contributions = plugin.getItemTemplateService().damageContributions(holder.templateKey());
        List<DamageType> configured = configuredDamageTypes(plugin, contributions);
        int index = slot - CONTENT_START;
        if (index == configured.size()) {
            holder.setPickerOpen(true);
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        if (index < 0 || index >= configured.size()) {
            return;
        }
        DamageType type = configured.get(index);
        if (shift) {
            plugin.getItemTemplateService().removeDamageContribution(holder.templateKey(), type.key(), ModifierContext.WIELDED);
            plugin.getItemTemplateService().removeDamageContribution(holder.templateKey(), type.key(), ModifierContext.WORN);
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.item-editor.damage.removed", Placeholder.unparsed("type", type.displayName())));
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        promptDamageContribution(plugin, player, holder, type);
    }

    private static void handleDamageTypePickerClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot == PREVIEW_SLOT) {
            holder.setPickerOpen(false);
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        List<DamageContribution> contributions = plugin.getItemTemplateService().damageContributions(holder.templateKey());
        List<DamageType> available = unconfiguredDamageTypes(plugin, contributions);
        int index = slot - CONTENT_START;
        if (index < 0 || index >= available.size()) {
            return;
        }
        promptDamageContribution(plugin, player, holder, available.get(index));
    }

    private static void promptDamageContribution(PurrtechPVE plugin, Player player, ItemEditorHolder holder, DamageType type) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.item-editor.damage.prompt-1"));
        player.sendMessage(messages.render(locale, "gui.item-editor.damage.prompt-2"));
        player.sendMessage(messages.render(locale, "gui.item-editor.damage.prompt-example"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
                return;
            }
            String[] parts = rawInput.trim().split("\\s+");
            // 4th word is optional - "show"/"hide" whether this line appears in the rendered
            // lore (see DamageContribution.visible()); defaults to shown, same as every other
            // stat that predates this toggle.
            if (parts.length != 3 && parts.length != 4) {
                p.sendMessage(messages.render(locale, "gui.item-editor.invalid-input"));
                open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
                return;
            }
            Double amount = parseDouble(parts[0]);
            DamageMode mode = parseMode(parts[1]);
            ModifierContext parsedContext = parseContext(parts[2]);
            Boolean visible = parts.length == 4 ? parseVisible(parts[3]) : Boolean.TRUE;
            if (amount == null || mode == null || parsedContext == null || visible == null) {
                p.sendMessage(messages.render(locale, "gui.item-editor.invalid-input"));
                open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
                return;
            }
            try {
                plugin.getItemTemplateService().setDamageContribution(holder.templateKey(), type.key(), amount, mode, parsedContext, visible);
                p.sendMessage(messages.render(locale, "gui.prompt.done"));
            } catch (TemplateNotFoundException e) {
                p.sendMessage(messages.render(locale, "gui.item-editor.template-gone"));
            }
            open(plugin, p, holder.templateKey(), ItemEditorTab.DAMAGE);
        });
    }

    /** {@code null} on anything but "show"/"hide" - same lenient/explicit shape as {@link #parseMode}/{@link #parseContext}. */
    private static Boolean parseVisible(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "show" -> Boolean.TRUE;
            case "hide" -> Boolean.FALSE;
            default -> null;
        };
    }

    // ---- RESIST ----
    // Same "only configured + Add button" shape as DAMAGE above, just for TypeModifier instead of
    // DamageContribution - see that section's comment for the shared picker mechanics.

    private static void renderResist(PurrtechPVE plugin, Inventory inventory, ItemEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        String templateKey = holder.templateKey();
        if (holder.isPickerOpen()) {
            renderResistTypePicker(plugin, inventory, templateKey, locale);
            return;
        }
        inventory.setItem(HEADER_TOGGLE_SLOT, headerToggleIcon(plugin, locale, templateKey, LoreHeader.RESIST));
        List<TypeModifier> modifiers = plugin.getItemTemplateService().typeModifiers(templateKey);
        List<DamageType> configured = configuredResistTypes(plugin, modifiers);
        for (int i = 0; i < configured.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = configured.get(i);
            TypeModifier modifier = modifiers.stream().filter(m -> m.damageTypeKey().equals(type.key())).findFirst().orElseThrow();
            double percent = modifier.percent();
            String key = percent >= 0 ? "gui.item-editor.resist.resist-line" : "gui.item-editor.resist.weakness-line";

            List<Component> lore = new ArrayList<>();
            lore.add(messages.render(locale, key, Placeholder.unparsed("amount", formatAmount(Math.abs(percent)))));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-editor.resist.hint-edit"));
            lore.add(messages.render(locale, "gui.item-editor.hint-shift-delete"));

            ItemStack icon = named(iconFor(type.key()), messages.render(locale, "gui.type-icon",
                    Placeholder.unparsed("icon", type.icon()), Placeholder.unparsed("type", type.displayName())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        int addSlot = CONTENT_START + configured.size();
        if (addSlot < SIZE) {
            inventory.setItem(addSlot, addButton(messages, locale, "gui.item-editor.resist.add"));
        }
    }

    private static void renderResistTypePicker(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        List<TypeModifier> modifiers = plugin.getItemTemplateService().typeModifiers(templateKey);
        List<DamageType> available = unconfiguredResistTypes(plugin, modifiers);
        renderTypePicker(plugin.getMessages(), locale, inventory, available);
    }

    private static List<DamageType> configuredResistTypes(PurrtechPVE plugin, List<TypeModifier> modifiers) {
        Set<String> keys = modifiers.stream().map(TypeModifier::damageTypeKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return plugin.getDamageTypeRegistry().all().values().stream().filter(t -> keys.contains(t.key())).toList();
    }

    private static List<DamageType> unconfiguredResistTypes(PurrtechPVE plugin, List<TypeModifier> modifiers) {
        Set<String> keys = modifiers.stream().map(TypeModifier::damageTypeKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return plugin.getDamageTypeRegistry().all().values().stream().filter(t -> !keys.contains(t.key())).toList();
    }

    private static void handleResistClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        if (holder.isPickerOpen()) {
            handleResistTypePickerClick(plugin, player, holder, slot);
            return;
        }
        if (slot == HEADER_TOGGLE_SLOT) {
            handleHeaderToggleClick(plugin, player, holder, LoreHeader.RESIST);
            return;
        }
        List<TypeModifier> modifiers = plugin.getItemTemplateService().typeModifiers(holder.templateKey());
        List<DamageType> configured = configuredResistTypes(plugin, modifiers);
        int index = slot - CONTENT_START;
        if (index == configured.size()) {
            holder.setPickerOpen(true);
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        if (index < 0 || index >= configured.size()) {
            return;
        }
        DamageType type = configured.get(index);
        if (shift) {
            plugin.getItemTemplateService().removeTypeModifier(holder.templateKey(), type.key());
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.item-editor.resist.removed", Placeholder.unparsed("type", type.displayName())));
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        ValueEditorMenu.open(plugin, player, holder.templateKey(), ValueEditorKind.RESIST, type.key());
    }

    private static void handleResistTypePickerClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot == PREVIEW_SLOT) {
            holder.setPickerOpen(false);
            render(plugin, holder.getInventory(), holder, player.locale());
            return;
        }
        List<TypeModifier> modifiers = plugin.getItemTemplateService().typeModifiers(holder.templateKey());
        List<DamageType> available = unconfiguredResistTypes(plugin, modifiers);
        int index = slot - CONTENT_START;
        if (index < 0 || index >= available.size()) {
            return;
        }
        // A brand-new resist entry starts at 0% - RESIST is purely numeric (no mode/context to
        // pick like DAMAGE has), so there's nothing left needing chat, straight into the same
        // +/- editor an existing entry's icon opens.
        ValueEditorMenu.open(plugin, player, holder.templateKey(), ValueEditorKind.RESIST, available.get(index).key());
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

    private static void renderTrinket(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        Messages messages = plugin.getMessages();
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        List<String> slotNames = trinketSlotNames(plugin);
        for (int i = 0; i < slotNames.size() && CONTENT_START + i < SIZE; i++) {
            String slotName = slotNames.get(i);
            boolean selected = template.allowedSlots().contains(slotName);
            String key = selected ? "gui.item-editor.trinket.slot-selected" : "gui.item-editor.trinket.slot-unselected";
            ItemStack icon = named(trinketSlotIcon(slotName), messages.render(locale, key, Placeholder.unparsed("slot", slotName)));
            ItemMeta meta = icon.getItemMeta();
            String hintKey = selected ? "gui.item-editor.trinket.hint-remove" : "gui.item-editor.trinket.hint-allow";
            meta.lore(List.of(messages.render(locale, hintKey)));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        ItemStack info = named(Material.PAPER, messages.render(locale, "gui.item-editor.trinket.info"));
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
        render(plugin, holder.getInventory(), holder, player.locale());
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

    private static void renderArmorClass(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        Messages messages = plugin.getMessages();
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        ArmorClass current = template.armorClass();

        armorClassOption(messages, locale, inventory, CONTENT_START, null, "gui.item-editor.armor-class.none", Material.BARRIER, current);
        armorClassOption(messages, locale, inventory, CONTENT_START + 1, ArmorClass.LIGHT, "gui.armor-class.tab.light", Material.LEATHER_CHESTPLATE, current);
        armorClassOption(messages, locale, inventory, CONTENT_START + 2, ArmorClass.MEDIUM, "gui.armor-class.tab.medium", Material.IRON_CHESTPLATE, current);
        armorClassOption(messages, locale, inventory, CONTENT_START + 3, ArmorClass.HEAVY, "gui.armor-class.tab.heavy", Material.NETHERITE_CHESTPLATE, current);

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(messages.render(locale, "gui.item-editor.armor-class.info-1"));
        infoLore.add(messages.render(locale, "gui.item-editor.armor-class.info-2"));
        infoLore.add(messages.render(locale, "gui.item-editor.armor-class.info-3"));
        infoLore.add(Component.empty());
        infoLore.add(messages.render(locale, "gui.item-editor.armor-class.hint-edit"));
        ItemStack info = named(Material.WRITABLE_BOOK, messages.render(locale, "gui.item-editor.armor-class.title"));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);
    }

    private static void armorClassOption(Messages messages, Locale locale, Inventory inventory, int slot, ArmorClass value,
                                          String labelKey, Material material, ArmorClass current) {
        boolean selected = value == current;
        String key = selected ? "gui.item-editor.armor-class.option-selected" : "gui.item-editor.armor-class.option-unselected";
        ItemStack icon = named(material, messages.render(locale, key, Placeholder.unparsed("label", messages.plain(locale, labelKey))));
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.item-editor.armor-class.hint-set")));
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
        render(plugin, holder.getInventory(), holder, player.locale());
    }

    // ---- ARMOR_PENETRATION ----

    private static void renderArmorPenetration(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        Messages messages = plugin.getMessages();
        inventory.setItem(HEADER_TOGGLE_SLOT, headerToggleIcon(plugin, locale, templateKey, LoreHeader.PENETRATION));
        List<ArmorPenetration> penetration = plugin.getItemTemplateService().armorPenetration(templateKey);
        ArmorClass[] classes = ArmorClass.values();
        for (int i = 0; i < classes.length; i++) {
            ArmorClass armorClass = classes[i];
            Optional<ArmorPenetration> current = penetration.stream()
                    .filter(p -> p.armorClass() == armorClass).findFirst();

            List<Component> lore = new ArrayList<>();
            if (current.isPresent()) {
                lore.add(messages.render(locale, "gui.item-editor.penetration.value", Placeholder.unparsed("amount", formatAmount(current.get().amount()))));
            } else {
                lore.add(messages.render(locale, "gui.armor-class.lore.not-set"));
            }
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-editor.armor-class.hint-set"));
            lore.add(messages.render(locale, "gui.item-editor.hint-shift-delete"));

            ItemStack icon = named(armorClassIcon(armorClass), messages.render(locale, "gui.item-editor.penetration.class-icon",
                    Placeholder.unparsed("class", armorClass.name())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }

        ItemStack info = named(Material.PAPER, messages.render(locale, "gui.item-editor.penetration.info-title"));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(
                messages.render(locale, "gui.item-editor.penetration.info-1"),
                messages.render(locale, "gui.item-editor.penetration.info-2"),
                messages.render(locale, "gui.item-editor.penetration.info-3")));
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);
    }

    private static void handleArmorPenetrationClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        if (slot == HEADER_TOGGLE_SLOT) {
            handleHeaderToggleClick(plugin, player, holder, LoreHeader.PENETRATION);
            return;
        }
        ArmorClass[] classes = ArmorClass.values();
        int index = slot - CONTENT_START;
        if (index < 0 || index >= classes.length) {
            return;
        }
        ArmorClass armorClass = classes[index];

        if (shift) {
            plugin.getItemTemplateService().removeArmorPenetration(holder.templateKey(), armorClass);
            player.sendMessage(messages.render(locale, "gui.item-editor.penetration.removed", Placeholder.unparsed("class", armorClass.name())));
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }

        ValueEditorMenu.open(plugin, player, holder.templateKey(), ValueEditorKind.ARMOR_PENETRATION, armorClass.name());
    }

    private static Material armorClassIcon(ArmorClass armorClass) {
        return switch (armorClass) {
            case LIGHT -> Material.LEATHER_CHESTPLATE;
            case MEDIUM -> Material.IRON_CHESTPLATE;
            case HEAVY -> Material.NETHERITE_CHESTPLATE;
        };
    }

    // ---- SPECIAL_EFFECTS (bleed chance/duration, crit chance/bonus damage) ----

    private static final int SLOT_BLEED_CHANCE = CONTENT_START;
    private static final int SLOT_BLEED_DURATION = CONTENT_START + 1;
    private static final int SLOT_BLEED_DAMAGE = CONTENT_START + 2;
    private static final int SLOT_CRIT_CHANCE = CONTENT_START + 3;
    private static final int SLOT_CRIT_BONUS = CONTENT_START + 4;

    private static void renderSpecialEffects(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        Messages messages = plugin.getMessages();
        Optional<BleedEffect> bleed = plugin.getItemTemplateService().bleedEffect(templateKey);
        Optional<CriticalEffect> critical = plugin.getItemTemplateService().criticalEffect(templateKey);

        inventory.setItem(SLOT_BLEED_CHANCE, effectStatIcon(messages, locale, Material.REDSTONE, "gui.item-editor.effects.bleed-chance",
                bleed.map(b -> formatAmount(b.chancePercent()) + "%").orElse(null)));
        inventory.setItem(SLOT_BLEED_DURATION, effectStatIcon(messages, locale, Material.CLOCK, "gui.item-editor.effects.bleed-duration",
                bleed.map(b -> formatAmount(b.durationSeconds()) + "s").orElse(null)));
        inventory.setItem(SLOT_BLEED_DAMAGE, effectStatIcon(messages, locale, Material.IRON_HOE, "gui.item-editor.effects.bleed-damage",
                bleed.map(ItemEditorMenu::formatBleedDamage).orElse(null)));
        inventory.setItem(SLOT_CRIT_CHANCE, effectStatIcon(messages, locale, Material.IRON_SWORD, "gui.item-editor.effects.crit-chance",
                critical.map(c -> formatAmount(c.chancePercent()) + "%").orElse(null)));
        inventory.setItem(SLOT_CRIT_BONUS, effectStatIcon(messages, locale, Material.GOLDEN_SWORD, "gui.item-editor.effects.crit-bonus",
                critical.map(c -> "+" + formatAmount(c.bonusDamagePercent()) + "%").orElse(null)));

        ItemStack info = named(Material.PAPER, messages.render(locale, "gui.item-editor.effects.info-title"));
        ItemMeta infoMeta = info.getItemMeta();
        List<Component> infoLore = new ArrayList<>(List.of(
                messages.render(locale, "gui.item-editor.effects.info-1"),
                messages.render(locale, "gui.item-editor.effects.info-2"),
                messages.render(locale, "gui.item-editor.effects.info-3"),
                messages.render(locale, "gui.item-editor.effects.info-4"),
                Component.empty()));
        // All 3 bleed fields (chance/duration/damage) - or both crit fields - have to actually be
        // set for the effect to roll at all (see BleedEffect/CriticalEffect.isComplete()), so an
        // admin building one up one field at a time (this screen's own +/- editor works that way)
        // can see at a glance whether it's live yet or still missing something.
        infoLore.add(messages.render(locale, bleed.map(BleedEffect::isComplete).orElse(false)
                ? "gui.item-editor.effects.bleed-complete" : "gui.item-editor.effects.bleed-incomplete"));
        infoLore.add(messages.render(locale, critical.map(CriticalEffect::isComplete).orElse(false)
                ? "gui.item-editor.effects.crit-complete" : "gui.item-editor.effects.crit-incomplete"));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);
    }

    private static String formatBleedDamage(BleedEffect bleed) {
        return "+" + formatAmount(bleed.damageAmount()) + (bleed.mode() == DamageMode.PERCENT_OF_TOTAL ? "%" : "");
    }

    private static ItemStack effectStatIcon(Messages messages, Locale locale, Material material, String labelKey, String currentValue) {
        ItemStack icon = named(material, messages.render(locale, labelKey));
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(currentValue != null
                ? messages.render(locale, "gui.item-editor.effects.value", Placeholder.unparsed("value", currentValue))
                : messages.render(locale, "gui.armor-class.lore.not-set"));
        lore.add(Component.empty());
        lore.add(messages.render(locale, "gui.item-editor.armor-class.hint-set"));
        lore.add(messages.render(locale, "gui.item-editor.effects.hint-shift-delete"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static void handleSpecialEffectsClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        switch (slot) {
            case SLOT_BLEED_CHANCE, SLOT_BLEED_DURATION, SLOT_BLEED_DAMAGE -> {
                if (shift) {
                    plugin.getItemTemplateService().removeBleedEffect(holder.templateKey());
                    player.sendMessage(messages.render(locale, "gui.item-editor.effects.bleed-removed"));
                    render(plugin, holder.getInventory(), holder, locale);
                    return;
                }
                ValueEditorKind kind = switch (slot) {
                    case SLOT_BLEED_CHANCE -> ValueEditorKind.BLEED_CHANCE;
                    case SLOT_BLEED_DURATION -> ValueEditorKind.BLEED_DURATION;
                    default -> ValueEditorKind.BLEED_DAMAGE;
                };
                ValueEditorMenu.open(plugin, player, holder.templateKey(), kind, null);
            }
            case SLOT_CRIT_CHANCE, SLOT_CRIT_BONUS -> {
                if (shift) {
                    plugin.getItemTemplateService().removeCriticalEffect(holder.templateKey());
                    player.sendMessage(messages.render(locale, "gui.item-editor.effects.crit-removed"));
                    render(plugin, holder.getInventory(), holder, locale);
                    return;
                }
                ValueEditorKind kind = slot == SLOT_CRIT_CHANCE ? ValueEditorKind.CRIT_CHANCE : ValueEditorKind.CRIT_BONUS;
                ValueEditorMenu.open(plugin, player, holder.templateKey(), kind, null);
            }
            default -> {
            }
        }
    }

    // ---- MOBS ----
    // Same "only configured + Add button" shape as DAMAGE/RESIST above, just keyed by mob type
    // name (a plain String from MythicMobs, not a DamageType) instead of a damage type - mob type
    // lists can get a lot longer than the ~19 damage types, so this is where it matters most.

    private static void renderMobs(PurrtechPVE plugin, Inventory inventory, ItemEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        if (plugin.getMythicMobsBridge() == null) {
            ItemStack info = named(Material.BARRIER, messages.render(locale, "gui.item-editor.mobs.unavailable"));
            ItemMeta infoMeta = info.getItemMeta();
            infoMeta.lore(List.of(
                    messages.render(locale, "gui.item-editor.mobs.unavailable-1"),
                    messages.render(locale, "gui.item-editor.mobs.unavailable-2"),
                    messages.render(locale, "gui.item-editor.mobs.unavailable-3")));
            info.setItemMeta(infoMeta);
            inventory.setItem(PREVIEW_SLOT, info);
            return;
        }

        String templateKey = holder.templateKey();
        if (holder.isPickerOpen()) {
            renderMobPicker(plugin, inventory, templateKey, locale);
            return;
        }
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        List<String> assigned = assignedMobTypes(plugin, template.id());
        for (int i = 0; i < assigned.size() && CONTENT_START + i < SIZE; i++) {
            String mobType = assigned.get(i);
            String assignedSlot = mobEquipmentSlot(plugin, mobType, template.id()).orElseThrow();

            List<Component> lore = new ArrayList<>();
            lore.add(messages.render(locale, "gui.item-editor.mobs.equipped-in", Placeholder.unparsed("slot", assignedSlot)));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-editor.mobs.hint-reequip"));
            lore.add(messages.render(locale, "gui.item-editor.mobs.hint-unequip"));

            ItemStack icon = named(Material.ZOMBIE_HEAD, messages.render(locale, "gui.item-editor.mobs.icon", Placeholder.unparsed("mob", mobType)));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        int addSlot = CONTENT_START + assigned.size();
        if (addSlot < SIZE) {
            inventory.setItem(addSlot, addButton(messages, locale, "gui.item-editor.mobs.add"));
        }
    }

    private static void renderMobPicker(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        Messages messages = plugin.getMessages();
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        List<String> available = unassignedMobTypes(plugin, template.id());
        for (int i = 0; i < available.size() && CONTENT_START + i < SIZE; i++) {
            String mobType = available.get(i);
            ItemStack icon = named(Material.ZOMBIE_HEAD, messages.render(locale, "gui.item-editor.mobs.icon", Placeholder.unparsed("mob", mobType)));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(
                    messages.render(locale, "gui.item-editor.mobs.hint-equip-1"),
                    messages.render(locale, "gui.item-editor.mobs.hint-equip-2")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        if (available.isEmpty()) {
            inventory.setItem(CONTENT_START, named(Material.PAPER, messages.render(locale, "gui.item-editor.mobs.all-equipped")));
        }
        ItemStack back = named(Material.ARROW, messages.render(locale, "gui.item-editor.back-red"));
        ItemMeta backMeta = back.getItemMeta();
        backMeta.lore(List.of(messages.render(locale, "gui.item-editor.mobs.hint-back")));
        back.setItemMeta(backMeta);
        inventory.setItem(PREVIEW_SLOT, back);
    }

    private static Optional<String> mobEquipmentSlot(PurrtechPVE plugin, String mobType, UUID templateId) {
        Map<String, UUID> equipment = plugin.getMobEquipmentRepository().findByMob(mobType);
        return equipment.entrySet().stream().filter(e -> e.getValue().equals(templateId)).map(Map.Entry::getKey).findFirst();
    }

    private static List<String> assignedMobTypes(PurrtechPVE plugin, UUID templateId) {
        return listMobTypesSafely(plugin).stream().filter(m -> mobEquipmentSlot(plugin, m, templateId).isPresent()).toList();
    }

    private static List<String> unassignedMobTypes(PurrtechPVE plugin, UUID templateId) {
        return listMobTypesSafely(plugin).stream().filter(m -> mobEquipmentSlot(plugin, m, templateId).isEmpty()).toList();
    }

    private static void handleMobsClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        Locale locale = player.locale();
        if (plugin.getMythicMobsBridge() == null) {
            return;
        }
        if (holder.isPickerOpen()) {
            handleMobPickerClick(plugin, player, holder, slot);
            return;
        }
        ItemTemplate template = plugin.getItemTemplateService().findByKey(holder.templateKey()).orElseThrow();
        List<String> assigned = assignedMobTypes(plugin, template.id());
        int index = slot - CONTENT_START;
        if (index == assigned.size()) {
            holder.setPickerOpen(true);
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }
        if (index < 0 || index >= assigned.size()) {
            return;
        }
        String mobType = assigned.get(index);
        if (shift) {
            String assignedSlot = mobEquipmentSlot(plugin, mobType, template.id()).orElseThrow();
            plugin.getMobEquipmentRepository().remove(mobType, assignedSlot);
            player.sendMessage(plugin.getMessages().render(locale, "gui.item-editor.mobs.unequipped", Placeholder.unparsed("mob", mobType)));
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }
        equipToMob(plugin, player, template, mobType);
        render(plugin, holder.getInventory(), holder, locale);
    }

    private static void handleMobPickerClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        Locale locale = player.locale();
        if (slot == PREVIEW_SLOT) {
            holder.setPickerOpen(false);
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }
        ItemTemplate template = plugin.getItemTemplateService().findByKey(holder.templateKey()).orElseThrow();
        List<String> available = unassignedMobTypes(plugin, template.id());
        int index = slot - CONTENT_START;
        if (index < 0 || index >= available.size()) {
            return;
        }
        equipToMob(plugin, player, template, available.get(index));
        holder.setPickerOpen(false);
        render(plugin, holder.getInventory(), holder, locale);
    }

    private static void equipToMob(PurrtechPVE plugin, Player player, ItemTemplate template, String mobType) {
        EquipmentSlot equipSlot = autoSlotFor(template.baseMaterial());
        plugin.getMobEquipmentRepository().set(mobType, equipSlot.name(), template.id());
        player.sendMessage(plugin.getMessages().render(player.locale(), "gui.item-editor.mobs.equipped",
                Placeholder.unparsed("mob", mobType), Placeholder.unparsed("slot", equipSlot.name())));
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

    private static void renderPublish(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        Messages messages = plugin.getMessages();
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(messages.render(locale, "gui.item-editor.publish.current-version", Placeholder.unparsed("version", String.valueOf(template.version()))));
        infoLore.add(messages.render(locale, "gui.item-editor.publish.synced-version", Placeholder.unparsed("version", String.valueOf(template.syncedVersion()))));
        infoLore.add(Component.empty());
        infoLore.add(messages.render(locale, template.isFullySynced() ? "gui.item-editor.publish.all-synced" : "gui.item-editor.publish.pending-changes"));
        ItemStack info = named(Material.WRITABLE_BOOK, messages.render(locale, "gui.item-editor.publish.status-title"));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(PREVIEW_SLOT, info);

        List<Component> buttonLore = List.of(
                messages.render(locale, "gui.item-editor.publish.button-info-1"),
                messages.render(locale, "gui.item-editor.publish.button-info-2"),
                messages.render(locale, "gui.item-editor.publish.button-info-3"));
        ItemStack button = named(Material.EMERALD_BLOCK, messages.render(locale, "gui.item-editor.publish.button").decorate(TextDecoration.BOLD));
        ItemMeta buttonMeta = button.getItemMeta();
        buttonMeta.lore(buttonLore);
        button.setItemMeta(buttonMeta);
        inventory.setItem(PUBLISH_BUTTON_SLOT, button);
    }

    private static void handlePublishClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot) {
        if (slot != PUBLISH_BUTTON_SLOT) {
            return;
        }
        Locale locale = player.locale();
        plugin.getItemTemplateService().propagate(holder.templateKey());
        int touched = plugin.getItemSyncService().resyncAllOnlinePlayers();
        player.sendMessage(plugin.getMessages().render(locale, "gui.item-editor.publish.done", Placeholder.unparsed("count", String.valueOf(touched))));
        render(plugin, holder.getInventory(), holder, locale);
    }

    // ---- shared click routing ----

    public static void handleClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, int slot, boolean shift) {
        if (plugin.getItemTemplateService().findByKey(holder.templateKey()).isEmpty()) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.item-editor.template-gone-meanwhile"));
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
            case TAB_SPECIAL_EFFECTS -> switchTab(plugin, player, holder, ItemEditorTab.SPECIAL_EFFECTS);
            case TAB_MOBS -> switchTab(plugin, player, holder, ItemEditorTab.MOBS);
            case TAB_PUBLISH -> switchTab(plugin, player, holder, ItemEditorTab.PUBLISH);
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
                switch (holder.tab()) {
                    case BASE -> handleBaseClick(plugin, player, holder, slot, shift);
                    case DAMAGE -> handleDamageClick(plugin, player, holder, slot, shift);
                    case RESIST -> handleResistClick(plugin, player, holder, slot, shift);
                    case TRINKET -> handleTrinketClick(plugin, player, holder, slot);
                    case ARMOR_CLASS -> handleArmorClassClick(plugin, player, holder, slot);
                    case ARMOR_PENETRATION -> handleArmorPenetrationClick(plugin, player, holder, slot, shift);
                    case SPECIAL_EFFECTS -> handleSpecialEffectsClick(plugin, player, holder, slot, shift);
                    case MOBS -> handleMobsClick(plugin, player, holder, slot, shift);
                    case PUBLISH -> handlePublishClick(plugin, player, holder, slot);
                }
            }
        }
    }

    // ---- helpers ----

    /** Shared section-header show/hide toggle icon (DAMAGE x2, RESIST, ARMOR_PENETRATION, BASE for its attributes header). */
    private static ItemStack headerToggleIcon(PurrtechPVE plugin, Locale locale, String templateKey, LoreHeader header) {
        Messages messages = plugin.getMessages();
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        boolean hidden = template.hiddenHeaders().contains(header.key());
        Material material = hidden ? Material.GRAY_DYE : Material.LIME_DYE;
        String key = hidden ? "gui.item-editor.header-toggle.hidden" : "gui.item-editor.header-toggle.shown";
        ItemStack icon = named(material, messages.render(locale, key));
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.item-editor.header-toggle.hint")));
        icon.setItemMeta(meta);
        return icon;
    }

    private static void handleHeaderToggleClick(PurrtechPVE plugin, Player player, ItemEditorHolder holder, LoreHeader header) {
        plugin.getItemTemplateService().toggleHeader(holder.templateKey(), header);
        render(plugin, holder.getInventory(), holder, player.locale());
    }

    /** Shared "+ Add" button icon used by every tab's configured-list + picker pattern (DAMAGE, RESIST, MOBS). */
    private static ItemStack addButton(Messages messages, Locale locale, String labelKey) {
        ItemStack icon = named(Material.LIME_DYE, messages.render(locale, labelKey));
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.item-editor.add-hint")));
        icon.setItemMeta(meta);
        return icon;
    }

    /** Shared damage-type picker screen (DAMAGE/RESIST tabs) - a "Zpět" arrow in PREVIEW_SLOT, one icon per not-yet-configured type. */
    private static void renderTypePicker(Messages messages, Locale locale, Inventory inventory, List<DamageType> available) {
        for (int i = 0; i < available.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = available.get(i);
            ItemStack icon = named(iconFor(type.key()), messages.render(locale, "gui.type-icon",
                    Placeholder.unparsed("icon", type.icon()), Placeholder.unparsed("type", type.displayName())));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(messages.render(locale, "gui.item-editor.picker-hint-add")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
        if (available.isEmpty()) {
            inventory.setItem(CONTENT_START, named(Material.PAPER, messages.render(locale, "gui.item-editor.picker-all-added")));
        }
        ItemStack back = named(Material.ARROW, messages.render(locale, "gui.item-editor.back-red"));
        ItemMeta backMeta = back.getItemMeta();
        backMeta.lore(List.of(messages.render(locale, "gui.item-editor.picker-hint-back")));
        back.setItemMeta(backMeta);
        inventory.setItem(PREVIEW_SLOT, back);
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

    private static AttributeModifier.Operation parseOperation(String raw) {
        try {
            return AttributeModifier.Operation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
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
