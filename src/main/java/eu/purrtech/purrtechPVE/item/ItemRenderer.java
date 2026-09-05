package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a stored {@link ItemTemplate} (+ its damage contributions/type
 * modifiers) into an actual {@link ItemStack}: display name, lore, and a
 * PersistentDataContainer tag recording which template + version this stack
 * was rendered from. That tag is the only thing carried in the item itself -
 * everything else is recomputed from the DB, which is what later phases'
 * live-sync (propagating an edited template to items already in circulation)
 * relies on.
 */
public final class ItemRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Messages messages;
    private final Locale locale;
    private final DamageTypeRegistry damageTypeRegistry;
    private final NamespacedKey templateKeyPdc;
    private final NamespacedKey templateVersionPdc;

    public ItemRenderer(Plugin plugin, Messages messages, Locale locale, DamageTypeRegistry damageTypeRegistry) {
        this.plugin = plugin;
        this.messages = messages;
        this.locale = locale;
        this.damageTypeRegistry = damageTypeRegistry;
        this.templateKeyPdc = new NamespacedKey(plugin, "template_key");
        this.templateVersionPdc = new NamespacedKey(plugin, "template_version");
    }

    public NamespacedKey templateKeyPdc() {
        return templateKeyPdc;
    }

    public NamespacedKey templateVersionPdc() {
        return templateVersionPdc;
    }

    /** Renders from the template's current live data - always the newest version, used for freshly given items. */
    public ItemStack render(ItemTemplate template, List<DamageContribution> contributions, List<TypeModifier> modifiers,
                             List<TemplateEnchantment> enchantments, List<ArmorPenetration> armorPenetration,
                             BleedEffect bleedEffect, CriticalEffect criticalEffect, List<AttributeModifierEntry> attributeModifiers) {
        return render(template.key(), template.version(), template.displayName(), template.customLore(), template.hiddenHeaders(),
                template.loreOrder(), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), contributions,
                modifiers, enchantments, armorPenetration, bleedEffect, criticalEffect, attributeModifiers);
    }

    /** Renders exactly as a given historical version looked - used to catch up a stack pinned behind the live version. */
    public ItemStack renderSnapshot(TemplateSnapshot snapshot) {
        return render(snapshot.templateKey(), snapshot.version(), snapshot.displayName(), snapshot.customLore(), snapshot.hiddenHeaders(),
                snapshot.loreOrder(), snapshot.baseMaterial(), snapshot.baseItemSnapshot(), snapshot.customModelData(),
                snapshot.damageContributions(), snapshot.typeModifiers(), snapshot.enchantments(), snapshot.armorPenetration(),
                snapshot.bleedEffect(), snapshot.criticalEffect(), snapshot.attributeModifiers());
    }

    private ItemStack render(String key, int version, String displayName, List<String> customLore, List<String> hiddenHeaders,
                              List<String> loreOrder, Material baseMaterial, byte[] baseItemSnapshot, Integer customModelData,
                              List<DamageContribution> contributions, List<TypeModifier> modifiers, List<TemplateEnchantment> enchantments,
                              List<ArmorPenetration> armorPenetration, BleedEffect bleedEffect, CriticalEffect criticalEffect,
                              List<AttributeModifierEntry> attributeModifiers) {
        // Starts from a full clone of whatever real item this template was created/rebased from
        // (raw NBT, not just Bukkit's PersistentDataContainer view of it - see BaseItemSnapshots for
        // exactly why that distinction is the whole fix) instead of a bare new ItemStack, so
        // anything a third-party plugin needs to recognize/render its own custom item (ItemsAdder,
        // Nexo, Oraxen, ...) rides along automatically. Everything below then overwrites/adds onto
        // that base exactly as it always has, so this plugin's own name/lore/enchants/attributes/
        // stamp still always win.
        ItemStack stack = BaseItemSnapshots.restore(baseItemSnapshot, baseMaterial);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(parseMiniMessage(displayName).decoration(TextDecoration.ITALIC, false));
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        meta.lore(buildLore(customLore, hiddenHeaders, loreOrder, contributions, modifiers, armorPenetration, bleedEffect, criticalEffect,
                attributeModifiers));

        for (TemplateEnchantment enchantment : enchantments) {
            resolveEnchantment(enchantment.enchantmentKey())
                    // ignoreLevelRestriction=true: these are admin-defined custom items, levels
                    // aren't capped at whatever vanilla considers the enchantment's usual max.
                    .ifPresent(e -> meta.addEnchant(e, enchantment.level(), true));
        }

        // Only entries whose slot is a real vanilla EquipmentSlotGroup (mainhand/offhand/head/...)
        // get baked in here - vanilla itself applies/removes these the moment the item is
        // equipped/unequipped in that slot, exactly like any vanilla attribute-modifier item,
        // zero custom combat code needed. A trinket-slot entry (this server's own accessory slot
        // names, which aren't real equipment slots vanilla can watch) is deliberately NOT baked in
        // here - see AttributeModifierEntry's javadoc - but IS still shown in the lore below so
        // it's not invisible to whoever's looking at the item.
        for (AttributeModifierEntry entry : attributeModifiers) {
            EquipmentSlotGroup group = EquipmentSlotGroup.getByName(entry.slot().toLowerCase(Locale.ROOT));
            if (group == null) {
                continue;
            }
            NamespacedKey modifierKey = new NamespacedKey(plugin, "attr_" + entry.slot().toLowerCase(Locale.ROOT)
                    + "_" + entry.attribute().name().toLowerCase(Locale.ROOT));
            meta.addAttributeModifier(entry.attribute(), new AttributeModifier(modifierKey, entry.amount(), entry.operation(), group));
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(templateKeyPdc, PersistentDataType.STRING, key);
        pdc.set(templateVersionPdc, PersistentDataType.INTEGER, version);

        stack.setItemMeta(meta);
        return stack;
    }

    /** {@code null}/empty on a key this server's Enchantment registry doesn't recognize - skipped rather than failing the whole render. */
    private Optional<Enchantment> resolveEnchantment(String enchantmentKey) {
        NamespacedKey key = NamespacedKey.fromString(enchantmentKey);
        return key == null ? Optional.empty() : Optional.ofNullable(Registry.ENCHANTMENT.get(key));
    }

    /** {@code templateKey}/{@code templateVersion} as stamped by {@link #render}, if the stack carries our PDC tags at all. */
    public Optional<StampedTemplate> readStamp(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String key = pdc.get(templateKeyPdc, PersistentDataType.STRING);
        Integer version = pdc.get(templateVersionPdc, PersistentDataType.INTEGER);
        if (key == null || version == null) {
            return Optional.empty();
        }
        return Optional.of(new StampedTemplate(key, version));
    }

    public record StampedTemplate(String templateKey, int templateVersion) {
    }

    /**
     * Builds the natural candidate {@link LoreLine}s (see {@link #lineCandidates}), then
     * concatenates them in whatever order {@code loreOrder} says (see {@link
     * LoreLine#canonicalize}) rather than a fixed sequence - {@code LoreOrderMenu} is what lets an
     * admin change that order, line by line rather than whole-category.
     */
    private List<Component> buildLore(List<String> customLore, List<String> hiddenHeaders, List<String> loreOrder,
                                       List<DamageContribution> contributions, List<TypeModifier> modifiers,
                                       List<ArmorPenetration> armorPenetration, BleedEffect bleedEffect, CriticalEffect criticalEffect,
                                       List<AttributeModifierEntry> attributeModifiers) {
        List<LoreLine> candidates = lineCandidates(customLore, hiddenHeaders, contributions, modifiers,
                armorPenetration, bleedEffect, criticalEffect, attributeModifiers);
        return LoreLine.canonicalize(loreOrder, candidates).stream().map(LoreLine::component).toList();
    }

    /**
     * The individual lore lines this template's data currently produces, in a fixed "natural"
     * default sequence (customLore, then damage/passive/resist/penetration/bleed/critical/
     * attributes) - exposed on its own so {@code LoreOrderMenu} can preview each line's real,
     * fully-colored {@link Component} (rather than a generic category label) on the icon that
     * reorders it. {@code hiddenHeaders} (see {@link LoreHeader}) still only controls a header
     * line's own presence, not position; a header is never a candidate with nothing under it -
     * same as before this existed, just one line at a time instead of a whole block.
     */
    public List<LoreLine> lineCandidates(List<String> customLore, List<String> hiddenHeaders,
                                          List<DamageContribution> contributions, List<TypeModifier> modifiers,
                                          List<ArmorPenetration> armorPenetration, BleedEffect bleedEffect, CriticalEffect criticalEffect,
                                          List<AttributeModifierEntry> attributeModifiers) {
        List<LoreLine> lines = new ArrayList<>();

        // Admin-authored (or import-seeded) lore - see ItemTemplate's javadoc for why this exists.
        // Identified by index (see LoreLine's javadoc for why that's the best identity available).
        for (int i = 0; i < customLore.size(); i++) {
            lines.add(new LoreLine("custom#" + i, parseMiniMessage(customLore.get(i))));
        }

        // Each category filters to its own visible-only entries before deciding whether to show
        // at all - an entry's own `visible` flag hides just that one line, while hiddenHeaders
        // (see LoreHeader) suppresses the header even if visible lines remain under it. A header
        // never shows with nothing under it: if every entry in a category is individually hidden,
        // the header is skipped too, same as having no entries there at all.
        List<DamageContribution> wielded = contributions.stream()
                .filter(c -> c.context() == ModifierContext.WIELDED && c.visible()).toList();
        if (!wielded.isEmpty() && !hiddenHeaders.contains(LoreHeader.DAMAGE.key())) {
            lines.add(new LoreLine("header#damage", messages.render(locale, "item.header.damage")));
            for (DamageContribution c : wielded) {
                lines.add(new LoreLine("damage#" + c.damageTypeKey(), damageLine(c)));
            }
        }

        List<DamageContribution> worn = contributions.stream()
                .filter(c -> c.context() == ModifierContext.WORN && c.visible()).toList();
        if (!worn.isEmpty() && !hiddenHeaders.contains(LoreHeader.PASSIVE.key())) {
            lines.add(new LoreLine("header#passive", messages.render(locale, "item.header.passive")));
            for (DamageContribution c : worn) {
                lines.add(new LoreLine("passive#" + c.damageTypeKey(), damageLine(c)));
            }
        }

        List<TypeModifier> visibleModifiers = modifiers.stream().filter(TypeModifier::visible).toList();
        if (!visibleModifiers.isEmpty() && !hiddenHeaders.contains(LoreHeader.RESIST.key())) {
            lines.add(new LoreLine("header#resist", messages.render(locale, "item.header.resist")));
            for (TypeModifier m : visibleModifiers) {
                lines.add(new LoreLine("resist#" + m.damageTypeKey(), resistLine(m)));
            }
        }

        List<ArmorPenetration> visiblePenetration = armorPenetration.stream().filter(ArmorPenetration::visible).toList();
        if (!visiblePenetration.isEmpty() && !hiddenHeaders.contains(LoreHeader.PENETRATION.key())) {
            lines.add(new LoreLine("header#penetration", messages.render(locale, "item.header.penetration")));
            for (ArmorPenetration p : visiblePenetration) {
                lines.add(new LoreLine("penetration#" + p.armorClass().name(), penetrationLine(p)));
            }
        }

        if (bleedEffect != null && bleedEffect.visible()) {
            lines.add(new LoreLine("bleed", messages.render(locale, "item.line.bleed",
                    Placeholder.unparsed("chance", formatAmount(bleedEffect.chancePercent())),
                    Placeholder.unparsed("duration", formatAmount(bleedEffect.durationSeconds())))));
        }

        if (criticalEffect != null && criticalEffect.visible()) {
            lines.add(new LoreLine("critical", messages.render(locale, "item.line.critical",
                    Placeholder.unparsed("chance", formatAmount(criticalEffect.chancePercent())),
                    Placeholder.unparsed("bonus", formatAmount(criticalEffect.bonusDamagePercent())))));
        }

        List<AttributeModifierEntry> visibleAttributes = attributeModifiers.stream().filter(AttributeModifierEntry::visible).toList();
        if (!visibleAttributes.isEmpty() && !hiddenHeaders.contains(LoreHeader.ATTRIBUTES.key())) {
            lines.add(new LoreLine("header#attributes", messages.render(locale, "item.header.attributes")));
            for (AttributeModifierEntry a : visibleAttributes) {
                lines.add(new LoreLine("attribute#" + a.attribute().name() + "|" + a.slot(), attributeLine(a)));
            }
        }

        return lines;
    }

    private Component damageLine(DamageContribution c) {
        String key = c.mode() == DamageMode.PERCENT_OF_TOTAL ? "item.line.damage-percent" : "item.line.damage-flat";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", formatAmount(c.amount())),
                Placeholder.unparsed("type", displayName(c.damageTypeKey())));
    }

    private Component resistLine(TypeModifier m) {
        String key = m.percent() >= 0 ? "item.line.resist" : "item.line.weakness";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", formatAmount(Math.abs(m.percent()))),
                Placeholder.unparsed("type", displayName(m.damageTypeKey())));
    }

    private Component penetrationLine(ArmorPenetration p) {
        return messages.render(locale, "item.line.penetration",
                Placeholder.unparsed("amount", formatAmount(p.amount())),
                Placeholder.unparsed("class", p.armorClass().name()));
    }

    /** ADD_NUMBER is a flat amount; ADD_SCALAR/MULTIPLY_SCALAR_1 are both percentage-of-base operations - shown with a trailing "%" either way, same simplicity as flat-vs-percent damage contributions. */
    private Component attributeLine(AttributeModifierEntry a) {
        String amount = (a.amount() >= 0 ? "+" : "") + formatAmount(a.amount())
                + (a.operation() == AttributeModifier.Operation.ADD_NUMBER ? "" : "%");
        return messages.render(locale, "item.line.attribute",
                Placeholder.unparsed("amount", amount),
                Placeholder.unparsed("attribute", a.attribute().name()),
                Placeholder.unparsed("slot", a.slot()));
    }

    private String displayName(String damageTypeKey) {
        return damageTypeRegistry.find(damageTypeKey)
                .map(DamageType::displayName)
                .orElse(damageTypeKey);
    }

    /**
     * Deserializes admin-authored text (display name, custom lore lines) as MiniMessage - both
     * imported and freshly-created items go through this, so a name/lore line can use {@code
     * <red>}/hex colors/{@code <bold>}/etc. instead of always showing as flat, unstyled text.
     * Falls back to showing the text literally on a parse failure (unbalanced/invalid tags) rather
     * than breaking the whole render - this is typed by an admin, not validated up front.
     */
    private static Component parseMiniMessage(String raw) {
        try {
            return MINI_MESSAGE.deserialize(raw);
        } catch (RuntimeException e) {
            return Component.text(raw);
        }
    }

    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }
}
