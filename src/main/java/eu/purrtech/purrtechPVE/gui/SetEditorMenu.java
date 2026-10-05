package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.combat.DamageFeedback;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.itemset.SetThresholdDamage;
import eu.purrtech.purrtechPVE.itemset.SetThresholdModifier;
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
import java.util.Set;
import java.util.TreeSet;

/**
 * The set editor GUI ({@code /pve set}, then a set in {@link SetListMenu}) - which item templates
 * belong to a set and its tiered piece-count bonuses, all configured by clicking:
 * <ul>
 *   <li><b>Members</b> - the templates in the set (with the equipment slot each one goes in) and a picker to add more.</li>
 *   <li><b>Thresholds</b> - one icon per piece count; click one to edit it, shift+click to delete it, or
 *       add a new one (a small screen where the piece count is raised/lowered with buttons).</li>
 *   <li><b>One threshold</b> - its damage and resistance bonuses as rows: left/right click +1/-1, shift
 *       +-10, F switches a damage bonus between flat and percent, Q removes it; two buttons add a new
 *       damage/resistance bonus, which opens a picker of damage types.</li>
 * </ul>
 * Every change goes straight into {@code ItemSetService} and the screen is redrawn in place. Nothing
 * here needs typing except what the list menu asks for when a set is created (its key and name).
 */
public final class SetEditorMenu {

    private static final int SIZE = 54;
    private static final int TAB_MEMBERS = 0;
    private static final int TAB_THRESHOLDS = 1;
    private static final int BACK_SLOT = 0;
    private static final int INFO_SLOT = 4;
    private static final int TO_LIST_SLOT = 7;
    private static final int CLOSE_SLOT = 8;
    private static final int ACTION_BUTTON_SLOT = 9;
    private static final int SECOND_ACTION_SLOT = 10;
    private static final int CONTENT_START = 18;

    // new-threshold screen
    private static final int COUNT_MINUS_SLOT = 20;
    private static final int COUNT_SLOT = 22;
    private static final int COUNT_PLUS_SLOT = 24;
    private static final int COUNT_CONFIRM_SLOT = 40;
    /** Plenty for armor (4) + trinkets (4) + hands (2); only here so the screen has an end. */
    private static final int MAX_PIECES = 20;

    private static final double DEFAULT_DAMAGE = 1;
    private static final double DEFAULT_RESIST_PERCENT = 5;

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
        render(plugin, holder, locale);
        player.openInventory(inventory);
    }

    private static void switchTab(PurrtechPVE plugin, SetEditorHolder holder, SetEditorTab tab, Locale locale) {
        holder.setTab(tab);
        render(plugin, holder, locale);
    }

    private static void render(PurrtechPVE plugin, SetEditorHolder holder, Locale locale) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        Messages messages = plugin.getMessages();
        String setKey = holder.setKey();
        switch (holder.tab()) {
            case MEMBERS -> {
                drawTabBar(plugin, inventory, holder.tab(), locale);
                inventory.setItem(ACTION_BUTTON_SLOT, named(Material.LIME_DYE, messages.render(locale, "gui.set-editor.add-member")));
                renderMembers(plugin, inventory, setKey, locale);
            }
            case THRESHOLDS -> {
                drawTabBar(plugin, inventory, holder.tab(), locale);
                inventory.setItem(ACTION_BUTTON_SLOT, named(Material.LIME_DYE, messages.render(locale, "gui.set-editor.add-threshold")));
                renderThresholds(plugin, inventory, setKey, locale);
            }
            case ADD_MEMBER -> {
                drawBackAndClose(messages, inventory, locale);
                renderAddMemberPicker(plugin, inventory, setKey, locale);
            }
            case NEW_TIER -> {
                drawBackAndClose(messages, inventory, locale);
                renderNewTier(plugin, inventory, holder, locale);
            }
            case TIER -> {
                drawBackAndClose(messages, inventory, locale);
                renderTier(plugin, inventory, holder, locale);
            }
            case PICK_TYPE -> {
                drawBackAndClose(messages, inventory, locale);
                renderTypePicker(plugin, inventory, holder, locale);
            }
        }
    }

    private static void drawBackAndClose(Messages messages, Inventory inventory, Locale locale) {
        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
    }

    private static void drawTabBar(PurrtechPVE plugin, Inventory inventory, SetEditorTab active, Locale locale) {
        Messages messages = plugin.getMessages();
        inventory.setItem(TAB_MEMBERS, tabIcon(messages, locale, Material.CHEST, "gui.set-editor.tab.members", active == SetEditorTab.MEMBERS));
        inventory.setItem(TAB_THRESHOLDS, tabIcon(messages, locale, Material.BEACON, "gui.set-editor.tab.thresholds", active == SetEditorTab.THRESHOLDS));
        inventory.setItem(TO_LIST_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
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
            // The real rendered stack, not a bare material+name icon, so the display name's
            // MiniMessage markup shows properly instead of literally.
            ItemStack icon = plugin.getItemTemplateService().renderGiveable(template.key());
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.item-list.key", Placeholder.unparsed("key", template.key())));
            slotLabel(template).ifPresent(slot -> lore.add(messages.render(locale, "gui.set-editor.member-slot", Placeholder.unparsed("slot", slot))));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.set-editor.hint-remove-member"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    /** Where a piece goes: its allowed slots if the template restricts them, else the armor slot of its base material (empty for a plain held item). */
    private static java.util.Optional<String> slotLabel(ItemTemplate template) {
        if (!template.allowedSlots().isEmpty()) {
            return java.util.Optional.of(String.join(", ", template.allowedSlots()).toLowerCase(Locale.ROOT));
        }
        EquipmentSlot slot = template.baseMaterial().getEquipmentSlot();
        return slot == EquipmentSlot.HAND ? java.util.Optional.empty() : java.util.Optional.of(slot.name().toLowerCase(Locale.ROOT));
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
        render(plugin, holder, locale);
    }

    // ---- ADD_MEMBER picker ----

    private static List<ItemTemplate> memberCandidates(PurrtechPVE plugin, String setKey) {
        Set<String> memberKeys = plugin.getItemSetService().members(setKey).stream().map(ItemTemplate::key)
                .collect(java.util.stream.Collectors.toSet());
        return plugin.getItemTemplateService().listAll().stream().filter(t -> !memberKeys.contains(t.key())).toList();
    }

    private static void renderAddMemberPicker(PurrtechPVE plugin, Inventory inventory, String setKey, Locale locale) {
        Messages messages = plugin.getMessages();
        List<ItemTemplate> candidates = memberCandidates(plugin, setKey);
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
        if (slot == BACK_SLOT) {
            switchTab(plugin, holder, SetEditorTab.MEMBERS, locale);
            return;
        }
        List<ItemTemplate> candidates = memberCandidates(plugin, holder.setKey());
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

    private static List<Integer> distinctPieceCounts(PurrtechPVE plugin, String setKey) {
        Set<Integer> counts = new TreeSet<>();
        plugin.getItemSetService().damageThresholds(setKey).forEach(d -> counts.add(d.pieceCount()));
        plugin.getItemSetService().modifierThresholds(setKey).forEach(m -> counts.add(m.pieceCount()));
        return List.copyOf(counts);
    }

    private static void renderThresholds(PurrtechPVE plugin, Inventory inventory, String setKey, Locale locale) {
        Messages messages = plugin.getMessages();
        List<Integer> pieceCounts = distinctPieceCounts(plugin, setKey);
        for (int i = 0; i < pieceCounts.size() && CONTENT_START + i < SIZE; i++) {
            int count = pieceCounts.get(i);
            List<Component> lore = new ArrayList<>();
            for (TierEntry entry : tierEntries(plugin, setKey, count)) {
                lore.add(entryLine(plugin, entry, locale));
            }
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.set-editor.threshold-hint-edit"));
            lore.add(messages.render(locale, "gui.set-editor.threshold-hint-delete"));

            ItemStack icon = named(Material.BEACON, messages.render(locale, "gui.set-editor.threshold-icon", Placeholder.unparsed("count", String.valueOf(count))));
            icon.setAmount(Math.max(1, Math.min(count, 64)));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    /** One bonus as a short lore line, same wording as before this was a menu. */
    private static Component entryLine(PurrtechPVE plugin, TierEntry entry, Locale locale) {
        Messages messages = plugin.getMessages();
        if (!entry.resist()) {
            String amount = "+" + DamageFeedback.formatAmount(entry.value()) + (entry.mode() == DamageMode.PERCENT_OF_TOTAL ? "%" : "");
            return messages.render(locale, "gui.set-editor.threshold-damage-line",
                    Placeholder.unparsed("amount", amount), Placeholder.unparsed("type", entry.type()));
        }
        String key = entry.value() >= 0 ? "gui.set-editor.threshold-resist-line" : "gui.set-editor.threshold-weakness-line";
        return messages.render(locale, key, Placeholder.unparsed("amount", DamageFeedback.formatAmount(Math.abs(entry.value()))),
                Placeholder.unparsed("type", entry.type()));
    }

    private static void handleThresholdsClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot, ClickType click) {
        Locale locale = player.locale();
        if (slot == ACTION_BUTTON_SLOT) {
            List<Integer> counts = distinctPieceCounts(plugin, holder.setKey());
            holder.setPieceCount(counts.isEmpty() ? 1 : Math.min(MAX_PIECES, counts.get(counts.size() - 1) + 1));
            switchTab(plugin, holder, SetEditorTab.NEW_TIER, locale);
            return;
        }
        List<Integer> pieceCounts = distinctPieceCounts(plugin, holder.setKey());
        int index = slot - CONTENT_START;
        if (index < 0 || index >= pieceCounts.size()) {
            return;
        }
        int count = pieceCounts.get(index);
        if (click.isShiftClick()) {
            deleteTier(plugin, holder.setKey(), count);
            player.sendMessage(plugin.getMessages().render(locale, "gui.set-editor.tier-deleted", Placeholder.unparsed("count", String.valueOf(count))));
            render(plugin, holder, locale);
            return;
        }
        holder.setPieceCount(count);
        switchTab(plugin, holder, SetEditorTab.TIER, locale);
    }

    private static void deleteTier(PurrtechPVE plugin, String setKey, int count) {
        for (TierEntry entry : tierEntries(plugin, setKey, count)) {
            if (entry.resist()) {
                plugin.getItemSetService().removeModifierThreshold(setKey, count, entry.type());
            } else {
                plugin.getItemSetService().removeDamageThreshold(setKey, count, entry.type());
            }
        }
    }

    // ---- NEW_TIER: choose the piece count ----

    private static void renderNewTier(PurrtechPVE plugin, Inventory inventory, SetEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        int count = holder.pieceCount();
        ItemStack minus = named(Material.RED_DYE, messages.render(locale, "gui.set-editor.new-tier-minus"));
        ItemMeta minusMeta = minus.getItemMeta();
        minusMeta.lore(List.of(messages.render(locale, "gui.set-editor.new-tier-hint")));
        minus.setItemMeta(minusMeta);
        inventory.setItem(COUNT_MINUS_SLOT, minus);

        ItemStack shown = named(Material.BEACON, messages.render(locale, "gui.set-editor.new-tier-count", Placeholder.unparsed("count", String.valueOf(count))));
        shown.setAmount(Math.max(1, Math.min(count, 64)));
        inventory.setItem(COUNT_SLOT, shown);

        ItemStack plus = named(Material.LIME_DYE, messages.render(locale, "gui.set-editor.new-tier-plus"));
        ItemMeta plusMeta = plus.getItemMeta();
        plusMeta.lore(List.of(messages.render(locale, "gui.set-editor.new-tier-hint")));
        plus.setItemMeta(plusMeta);
        inventory.setItem(COUNT_PLUS_SLOT, plus);

        ItemStack confirm = named(Material.EMERALD, messages.render(locale, "gui.set-editor.new-tier-confirm"));
        if (distinctPieceCounts(plugin, holder.setKey()).contains(count)) {
            ItemMeta meta = confirm.getItemMeta();
            meta.lore(List.of(messages.render(locale, "gui.set-editor.new-tier-exists")));
            confirm.setItemMeta(meta);
        }
        inventory.setItem(COUNT_CONFIRM_SLOT, confirm);
    }

    private static void handleNewTierClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot, ClickType click) {
        Locale locale = player.locale();
        int step = click.isShiftClick() ? 5 : 1;
        switch (slot) {
            case BACK_SLOT -> switchTab(plugin, holder, SetEditorTab.THRESHOLDS, locale);
            case COUNT_MINUS_SLOT -> {
                holder.setPieceCount(Math.max(1, holder.pieceCount() - step));
                render(plugin, holder, locale);
            }
            case COUNT_PLUS_SLOT -> {
                holder.setPieceCount(Math.min(MAX_PIECES, holder.pieceCount() + step));
                render(plugin, holder, locale);
            }
            case COUNT_CONFIRM_SLOT -> switchTab(plugin, holder, SetEditorTab.TIER, locale);
            default -> {
            }
        }
    }

    // ---- TIER: the bonuses of one threshold ----

    /** One bonus of a threshold: a damage bonus (value + mode) or a resistance/weakness (value only). */
    private record TierEntry(boolean resist, String type, double value, DamageMode mode) {
    }

    /** Damage bonuses first, then resistances, each sorted by type - the order the rows are drawn and clicked in. */
    private static List<TierEntry> tierEntries(PurrtechPVE plugin, String setKey, int count) {
        List<TierEntry> out = new ArrayList<>();
        plugin.getItemSetService().damageThresholds(setKey).stream()
                .filter(d -> d.pieceCount() == count)
                .sorted(Comparator.comparing(SetThresholdDamage::damageTypeKey))
                .forEach(d -> out.add(new TierEntry(false, d.damageTypeKey(), d.amount(), d.mode())));
        plugin.getItemSetService().modifierThresholds(setKey).stream()
                .filter(m -> m.pieceCount() == count)
                .sorted(Comparator.comparing(SetThresholdModifier::damageTypeKey))
                .forEach(m -> out.add(new TierEntry(true, m.damageTypeKey(), m.percent(), DamageMode.FLAT)));
        return out;
    }

    private static void renderTier(PurrtechPVE plugin, Inventory inventory, SetEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        ItemStack info = named(Material.BEACON, messages.render(locale, "gui.set-editor.tier-info", Placeholder.unparsed("count", String.valueOf(holder.pieceCount()))));
        info.setAmount(Math.max(1, Math.min(holder.pieceCount(), 64)));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(messages.render(locale, "gui.set-editor.tier-info-hint-1"), messages.render(locale, "gui.set-editor.tier-info-hint-2")));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        inventory.setItem(ACTION_BUTTON_SLOT, addButton(messages, locale, Material.LIME_DYE, "gui.set-editor.tier-add-damage"));
        inventory.setItem(SECOND_ACTION_SLOT, addButton(messages, locale, Material.CYAN_DYE, "gui.set-editor.tier-add-resist"));

        List<TierEntry> entries = tierEntries(plugin, holder.setKey(), holder.pieceCount());
        if (entries.isEmpty()) {
            inventory.setItem(CONTENT_START, named(Material.PAPER, messages.render(locale, "gui.set-editor.tier-empty")));
            return;
        }
        for (int i = 0; i < entries.size() && CONTENT_START + i < SIZE; i++) {
            inventory.setItem(CONTENT_START + i, entryIcon(plugin, entries.get(i), locale));
        }
    }

    private static ItemStack addButton(Messages messages, Locale locale, Material material, String nameKey) {
        ItemStack button = named(material, messages.render(locale, nameKey));
        ItemMeta meta = button.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.set-editor.tier-add-hint")));
        button.setItemMeta(meta);
        return button;
    }

    private static ItemStack entryIcon(PurrtechPVE plugin, TierEntry entry, Locale locale) {
        Messages messages = plugin.getMessages();
        String glyph = plugin.getDamageTypeRegistry().find(entry.type()).map(DamageType::icon).orElse("");
        List<Component> lore = new ArrayList<>();
        ItemStack icon;
        if (!entry.resist()) {
            icon = named(Material.RED_DYE, messages.render(locale, "gui.set-editor.tier-damage-name",
                    Placeholder.unparsed("icon", glyph), Placeholder.component("type", messages.damageTypeName(locale, entry.type(), true))));
            String mode = messages.plain(locale, entry.mode() == DamageMode.PERCENT_OF_TOTAL ? "gui.set-editor.mode-percent" : "gui.set-editor.mode-flat");
            lore.add(messages.render(locale, "gui.set-editor.tier-damage-amount",
                    Placeholder.unparsed("amount", DamageFeedback.formatAmount(entry.value())), Placeholder.unparsed("mode", mode)));
        } else {
            boolean weakness = entry.value() < 0;
            icon = named(Material.CYAN_DYE, messages.render(locale, "gui.set-editor.tier-resist-name",
                    Placeholder.unparsed("icon", glyph), Placeholder.component("type", messages.resistTypeName(locale, entry.type(), weakness, true))));
            lore.add(messages.render(locale, weakness ? "gui.set-editor.tier-weakness-amount" : "gui.set-editor.tier-resist-amount",
                    Placeholder.unparsed("amount", DamageFeedback.formatAmount(Math.abs(entry.value())))));
        }
        lore.add(Component.empty());
        lore.add(messages.render(locale, "gui.set-editor.tier-entry-hint-1"));
        if (!entry.resist()) {
            lore.add(messages.render(locale, "gui.set-editor.tier-entry-hint-mode"));
        }
        lore.add(messages.render(locale, "gui.set-editor.tier-entry-hint-remove"));
        ItemMeta meta = icon.getItemMeta();
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static void handleTierClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot, ClickType click) {
        Locale locale = player.locale();
        if (slot == BACK_SLOT) {
            switchTab(plugin, holder, SetEditorTab.THRESHOLDS, locale);
            return;
        }
        if (slot == ACTION_BUTTON_SLOT || slot == SECOND_ACTION_SLOT) {
            holder.setPickResist(slot == SECOND_ACTION_SLOT);
            switchTab(plugin, holder, SetEditorTab.PICK_TYPE, locale);
            return;
        }
        List<TierEntry> entries = tierEntries(plugin, holder.setKey(), holder.pieceCount());
        int index = slot - CONTENT_START;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        TierEntry entry = entries.get(index);
        String setKey = holder.setKey();
        int count = holder.pieceCount();

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            if (entry.resist()) {
                plugin.getItemSetService().removeModifierThreshold(setKey, count, entry.type());
            } else {
                plugin.getItemSetService().removeDamageThreshold(setKey, count, entry.type());
            }
            render(plugin, holder, locale);
            return;
        }
        if (click == ClickType.SWAP_OFFHAND && !entry.resist()) {
            DamageMode toggled = entry.mode() == DamageMode.FLAT ? DamageMode.PERCENT_OF_TOTAL : DamageMode.FLAT;
            plugin.getItemSetService().setDamageThreshold(setKey, count, entry.type(), entry.value(), toggled);
            render(plugin, holder, locale);
            return;
        }
        double delta = switch (click) {
            case LEFT -> 1;
            case RIGHT -> -1;
            case SHIFT_LEFT -> 10;
            case SHIFT_RIGHT -> -10;
            default -> 0;
        };
        if (delta == 0) {
            return;
        }
        double next = entry.value() + delta;
        if (entry.resist()) {
            plugin.getItemSetService().setModifierThreshold(setKey, count, entry.type(), next);
        } else {
            plugin.getItemSetService().setDamageThreshold(setKey, count, entry.type(), next, entry.mode());
        }
        render(plugin, holder, locale);
    }

    // ---- PICK_TYPE: add a damage/resistance bonus to the threshold ----

    /**
     * Types that can still be added to this threshold. The "physical" alias is skipped (its three
     * subtypes are listed on their own); bleed is a damage-over-time effect, so it is a perfectly good
     * resistance but not a plain damage bonus.
     */
    private static List<DamageType> pickableTypes(PurrtechPVE plugin, SetEditorHolder holder) {
        Set<String> taken = new java.util.HashSet<>();
        for (TierEntry entry : tierEntries(plugin, holder.setKey(), holder.pieceCount())) {
            if (entry.resist() == holder.pickResist()) {
                taken.add(entry.type());
            }
        }
        return plugin.getDamageTypeRegistry().all().values().stream()
                .filter(type -> !DamageTypeRegistry.FALLBACK_PHYSICAL.equals(type.key()))
                .filter(type -> holder.pickResist() || !"bleed".equals(type.key()))
                .filter(type -> !taken.contains(type.key()))
                .sorted(Comparator.comparing(DamageType::key))
                .toList();
    }

    private static void renderTypePicker(PurrtechPVE plugin, Inventory inventory, SetEditorHolder holder, Locale locale) {
        Messages messages = plugin.getMessages();
        String titleKey = holder.pickResist() ? "gui.set-editor.pick-title-resist" : "gui.set-editor.pick-title-damage";
        ItemStack info = named(holder.pickResist() ? Material.CYAN_DYE : Material.RED_DYE,
                messages.render(locale, titleKey, Placeholder.unparsed("count", String.valueOf(holder.pieceCount()))));
        inventory.setItem(INFO_SLOT, info);

        List<DamageType> types = pickableTypes(plugin, holder);
        if (types.isEmpty()) {
            inventory.setItem(CONTENT_START, named(Material.PAPER, messages.render(locale, "gui.set-editor.pick-none")));
            return;
        }
        for (int i = 0; i < types.size() && CONTENT_START + i < SIZE; i++) {
            DamageType type = types.get(i);
            Component typeName = holder.pickResist()
                    ? messages.resistTypeName(locale, type.key(), false, true)
                    : messages.damageTypeName(locale, type.key(), true);
            ItemStack icon = named(Material.PAPER, messages.render(locale,
                    holder.pickResist() ? "gui.set-editor.tier-resist-name" : "gui.set-editor.tier-damage-name",
                    Placeholder.unparsed("icon", type.icon()), Placeholder.component("type", typeName)));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(messages.render(locale, "gui.set-editor.pick-hint")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void handlePickTypeClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot) {
        Locale locale = player.locale();
        if (slot == BACK_SLOT) {
            switchTab(plugin, holder, SetEditorTab.TIER, locale);
            return;
        }
        List<DamageType> types = pickableTypes(plugin, holder);
        int index = slot - CONTENT_START;
        if (index < 0 || index >= types.size()) {
            return;
        }
        String type = types.get(index).key();
        if (holder.pickResist()) {
            plugin.getItemSetService().setModifierThreshold(holder.setKey(), holder.pieceCount(), type, DEFAULT_RESIST_PERCENT);
        } else {
            plugin.getItemSetService().setDamageThreshold(holder.setKey(), holder.pieceCount(), type, DEFAULT_DAMAGE, DamageMode.FLAT);
        }
        switchTab(plugin, holder, SetEditorTab.TIER, locale);
    }

    // ---- shared click routing ----

    public static void handleClick(PurrtechPVE plugin, Player player, SetEditorHolder holder, int slot, ClickType click) {
        Locale locale = player.locale();
        if (plugin.getItemSetService().findByKey(holder.setKey()).isEmpty()) {
            player.sendMessage(plugin.getMessages().render(locale, "gui.set-editor.set-gone-meanwhile"));
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        switch (holder.tab()) {
            case ADD_MEMBER -> handleAddMemberClick(plugin, player, holder, slot);
            case NEW_TIER -> handleNewTierClick(plugin, player, holder, slot, click);
            case TIER -> handleTierClick(plugin, player, holder, slot, click);
            case PICK_TYPE -> handlePickTypeClick(plugin, player, holder, slot);
            case MEMBERS, THRESHOLDS -> {
                if (slot == TO_LIST_SLOT) {
                    SetListMenu.open(plugin, player, 0);
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
                if (holder.tab() == SetEditorTab.MEMBERS) {
                    handleMembersClick(plugin, player, holder, slot);
                } else {
                    handleThresholdsClick(plugin, player, holder, slot, click);
                }
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
}
