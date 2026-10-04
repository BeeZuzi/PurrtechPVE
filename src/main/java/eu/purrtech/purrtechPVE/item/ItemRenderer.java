package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Bumped whenever how a stack LOOKS changes without its template or the lang text changing (so
     * nothing else would mark circulating stacks stale) - it is folded into {@link #currentLangHash},
     * which makes every older stack re-render once. 2: lore italic is set explicitly (custom lore
     * normal, generated lines italic).
     */
    private static final int RENDER_REVISION = 2;

    private final Plugin plugin;
    // Not final - see refresh(), called by PurrtechPVE.reload() so an admin editing lang.yml or
    // the locale in config.yml and running /pve reload doesn't need a server restart to see it in
    // rendered item lore. Every other consumer of Messages/Locale (GUI menus, commands) already
    // reads them fresh from PurrtechPVE's own getters each call, so this is the one place that
    // needed to stop capturing a permanent copy - everything reachable from an ItemRenderer
    // (ItemTemplateService, ItemSyncService, EquipmentResolver, ...) shares this same instance.
    private Messages messages;
    private Locale locale;
    private final NamespacedKey templateKeyPdc;
    private final NamespacedKey templateVersionPdc;
    private final NamespacedKey langHashPdc;
    private final NamespacedKey renderedNameHashPdc;
    private final NamespacedKey renderedLoreHashesPdc;
    private final NamespacedKey instanceIdPdc;
    private final NamespacedKey duplicatedPdc;

    public ItemRenderer(Plugin plugin, Messages messages, Locale locale) {
        this.plugin = plugin;
        this.messages = messages;
        this.locale = locale;
        this.templateKeyPdc = new NamespacedKey(plugin, "template_key");
        this.templateVersionPdc = new NamespacedKey(plugin, "template_version");
        this.langHashPdc = new NamespacedKey(plugin, "lang_hash");
        this.renderedNameHashPdc = new NamespacedKey(plugin, "rendered_name_hash");
        this.renderedLoreHashesPdc = new NamespacedKey(plugin, "rendered_lore_hashes");
        this.instanceIdPdc = new NamespacedKey(plugin, "instance_id");
        this.duplicatedPdc = new NamespacedKey(plugin, "duplicated");
    }

    /** Identifies the lang text + locale a stack was rendered with, so a lang edit marks every older stack stale. */
    public int currentLangHash() {
        return Objects.hash(messages.fingerprint(), locale.toLanguageTag(), RENDER_REVISION);
    }

    /** See the {@code messages}/{@code locale} field comment - called by {@code PurrtechPVE.reload()}. */
    public void refresh(Messages messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
    }

    public NamespacedKey templateKeyPdc() {
        return templateKeyPdc;
    }

    public NamespacedKey templateVersionPdc() {
        return templateVersionPdc;
    }

    /** PDC key of the per-item id that per-item DB data (upgrades) hangs off - absent until an item first needs one. */
    public NamespacedKey instanceIdPdc() {
        return instanceIdPdc;
    }

    /** PDC flag set (see {@code ItemDuplicationListener}) on an item that was cloned from one carrying an instance id. */
    public NamespacedKey duplicatedPdc() {
        return duplicatedPdc;
    }

    public Optional<UUID> readInstanceId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(instanceIdPdc, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public NamespacedKey renderedNameHashPdc() {
        return renderedNameHashPdc;
    }

    public NamespacedKey renderedLoreHashesPdc() {
        return renderedLoreHashesPdc;
    }

    /** Hash of a component's plain text - formatting is ignored, so it survives the item's NBT round trip. */
    public static int textHash(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).hashCode();
    }

    /** Renders from the template's current live data - always the newest version, used for freshly given items. */
    public ItemStack render(ItemTemplate template, List<DamageContribution> contributions, List<TypeModifier> modifiers,
                             List<TemplateEnchantment> enchantments, List<ArmorPenetration> armorPenetration,
                             BleedEffect bleedEffect, CriticalEffect criticalEffect, StunEffect stunEffect, ReflectEffect reflectEffect,
                             List<AttributeModifierEntry> attributeModifiers) {
        return render(template.key(), template.version(), template.displayName(), template.customLore(), template.hiddenHeaders(),
                template.loreOrder(), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), contributions,
                modifiers, enchantments, armorPenetration, bleedEffect, criticalEffect, stunEffect, reflectEffect, attributeModifiers,
                template.armorClass(), template.armorAmount());
    }

    /**
     * Renders exactly as a given historical version looked - used to catch up a stack pinned behind the live version.
     * {@code armorClass} is the template's live value, not part of the snapshot (see {@code ItemTemplate}'s javadoc).
     */
    public ItemStack renderSnapshot(TemplateSnapshot snapshot, ArmorClass armorClass, double armorAmount) {
        return render(snapshot.templateKey(), snapshot.version(), snapshot.displayName(), snapshot.customLore(), snapshot.hiddenHeaders(),
                snapshot.loreOrder(), snapshot.baseMaterial(), snapshot.baseItemSnapshot(), snapshot.customModelData(),
                snapshot.damageContributions(), snapshot.typeModifiers(), snapshot.enchantments(), snapshot.armorPenetration(),
                snapshot.bleedEffect(), snapshot.criticalEffect(), snapshot.stunEffect(), snapshot.reflectEffect(), snapshot.attributeModifiers(),
                armorClass, armorAmount);
    }

    private ItemStack render(String key, int version, String displayName, List<String> customLore, List<String> hiddenHeaders,
                              List<String> loreOrder, Material baseMaterial, byte[] baseItemSnapshot, Integer customModelData,
                              List<DamageContribution> contributions, List<TypeModifier> modifiers, List<TemplateEnchantment> enchantments,
                              List<ArmorPenetration> armorPenetration, BleedEffect bleedEffect, CriticalEffect criticalEffect,
                              StunEffect stunEffect, ReflectEffect reflectEffect, List<AttributeModifierEntry> attributeModifiers,
                              ArmorClass armorClass, double armorAmount) {
        // Starts from a full clone of whatever real item this template was created/rebased from
        // (raw NBT, not just Bukkit's PersistentDataContainer view of it - see BaseItemSnapshots for
        // exactly why that distinction is the whole fix) instead of a bare new ItemStack, so
        // anything a third-party plugin needs to recognize/render its own custom item (ItemsAdder,
        // Nexo, Oraxen, ...) rides along automatically. Everything below then overwrites/adds onto
        // that base exactly as it always has, so this plugin's own name/lore/enchants/attributes/
        // stamp still always win.
        ItemStack stack = BaseItemSnapshots.restore(baseItemSnapshot, baseMaterial);
        ItemMeta meta = stack.getItemMeta();

        Component renderedName = parseMiniMessage(displayName).decoration(TextDecoration.ITALIC, false);
        meta.displayName(renderedName);
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        List<Component> renderedLore = buildLore(customLore, hiddenHeaders, loreOrder, contributions, modifiers, armorPenetration,
                bleedEffect, criticalEffect, stunEffect, reflectEffect, attributeModifiers, armorClass, armorAmount);
        meta.lore(renderedLore);

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
        pdc.set(langHashPdc, PersistentDataType.INTEGER, currentLangHash());
        // Fingerprints of the name/lore lines WE wrote, so a later re-render can tell them apart from
        // anything a player or another plugin added afterwards (an anvil rename, a "soulbound" line, ...)
        // and carry those over instead of wiping them - see StackStateCarrier.
        pdc.set(renderedNameHashPdc, PersistentDataType.INTEGER, textHash(renderedName));
        pdc.set(renderedLoreHashesPdc, PersistentDataType.INTEGER_ARRAY,
                renderedLore.stream().mapToInt(ItemRenderer::textHash).toArray());

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
        return Optional.of(new StampedTemplate(key, version, pdc.get(langHashPdc, PersistentDataType.INTEGER)));
    }

    /** {@code langHash} is null on stacks rendered before lang stamping existed - treated as stale. */
    public record StampedTemplate(String templateKey, int templateVersion, Integer langHash) {
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
                                       StunEffect stunEffect, ReflectEffect reflectEffect, List<AttributeModifierEntry> attributeModifiers,
                                       ArmorClass armorClass, double armorAmount) {
        List<LoreLine> candidates = lineCandidates(customLore, hiddenHeaders, contributions, modifiers,
                armorPenetration, bleedEffect, criticalEffect, stunEffect, reflectEffect, attributeModifiers, armorClass, armorAmount);
        return LoreLine.canonicalize(loreOrder, candidates).stream().map(ItemRenderer::withDefaultItalic).toList();
    }

    /**
     * The client draws every lore line italic unless told otherwise, so the italic of each line is set
     * explicitly: lines this plugin generates (headers, damage, resistances, effects, ...) are italic,
     * admin-written custom lore is not. Only a DEFAULT - {@code decorationIfAbsent} leaves alone anything
     * the text itself already decided, such as {@code <i>...</i>} or {@code &o} in a custom line, which
     * therefore stays italic exactly where the author asked for it.
     */
    static Component withDefaultItalic(LoreLine line) {
        boolean custom = line.key().startsWith("custom#");
        return line.component().decorationIfAbsent(TextDecoration.ITALIC,
                custom ? TextDecoration.State.FALSE : TextDecoration.State.TRUE);
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
                                          StunEffect stunEffect, ReflectEffect reflectEffect, List<AttributeModifierEntry> attributeModifiers,
                                          ArmorClass armorClass, double armorAmount) {
        List<LoreLine> lines = new ArrayList<>();

        // Admin-authored (or import-seeded) lore - see ItemTemplate's javadoc for why this exists.
        // Identified by index (see LoreLine's javadoc for why that's the best identity available).
        for (int i = 0; i < customLore.size(); i++) {
            lines.add(new LoreLine("custom#" + i, parseMiniMessage(customLore.get(i))));
        }

        // "Light/medium/heavy armor" label - only for pieces that actually have a class (null = not armor).
        if (armorClass != null) {
            Component className = messages.armorClassName(locale, armorClass.name());
            lines.add(new LoreLine("armor-class", armorAmount > 0
                    ? messages.render(locale, "item.line.armor-class-amount",
                            Placeholder.unparsed("amount", formatAmount(armorAmount)),
                            Placeholder.component("class", className))
                    : messages.render(locale, "item.line.armor-class", Placeholder.component("class", className))));
        }

        // Each category filters to its own visible-only entries before deciding whether to show
        // at all - an entry's own `visible` flag hides just that one line, while hiddenHeaders
        // (see LoreHeader) suppresses the header even if visible lines remain under it. A header
        // never shows with nothing under it: if every entry in a category is individually hidden,
        // the header is skipped too, same as having no entries there at all. Hiding the header
        // itself (hiddenHeaders) only ever suppresses that one header line - the stat lines below
        // it keep rendering regardless (see ItemTemplate's javadoc on hiddenHeaders). DAMAGE/
        // PASSIVE/RESIST additionally swap each line's damage-type name to its "no header" variant
        // (see Messages.damageTypeName/resistTypeName) so a bare "Blunt" doesn't lose its context
        // once the section header above it is gone. PENETRATION instead shows a bare number under
        // its header (the header itself already says "penetration") and only adds the armor
        // class's full phrase (see Messages.armorClassPenetrationName) once that header is hidden -
        // BLEED/CRITICAL/ATTRIBUTES have no such variant since their lines carry no type name.
        List<DamageContribution> wielded = contributions.stream()
                .filter(c -> c.context() == ModifierContext.WIELDED && c.visible()).toList();
        if (!wielded.isEmpty()) {
            boolean headerHidden = hiddenHeaders.contains(LoreHeader.DAMAGE.key());
            if (!headerHidden) {
                lines.add(new LoreLine("header#damage", messages.render(locale, "item.header.damage")));
            }
            for (DamageContribution c : wielded) {
                lines.add(new LoreLine("damage#" + c.damageTypeKey(), damageLine(c, headerHidden)));
            }
        }

        List<DamageContribution> worn = contributions.stream()
                .filter(c -> c.context() == ModifierContext.WORN && c.visible()).toList();
        if (!worn.isEmpty()) {
            boolean headerHidden = hiddenHeaders.contains(LoreHeader.PASSIVE.key());
            if (!headerHidden) {
                lines.add(new LoreLine("header#passive", messages.render(locale, "item.header.passive")));
            }
            for (DamageContribution c : worn) {
                lines.add(new LoreLine("passive#" + c.damageTypeKey(), damageLine(c, headerHidden)));
            }
        }

        List<TypeModifier> visibleModifiers = modifiers.stream().filter(TypeModifier::visible).toList();
        if (!visibleModifiers.isEmpty()) {
            boolean headerHidden = hiddenHeaders.contains(LoreHeader.RESIST.key());
            if (!headerHidden) {
                lines.add(new LoreLine("header#resist", messages.render(locale, "item.header.resist")));
            }
            for (TypeModifier m : visibleModifiers) {
                lines.add(new LoreLine("resist#" + m.damageTypeKey(), resistLine(m, headerHidden)));
            }
        }

        List<ArmorPenetration> visiblePenetration = armorPenetration.stream().filter(ArmorPenetration::visible).toList();
        if (!visiblePenetration.isEmpty()) {
            boolean headerHidden = hiddenHeaders.contains(LoreHeader.PENETRATION.key());
            if (!headerHidden) {
                lines.add(new LoreLine("header#penetration", messages.render(locale, "item.header.penetration")));
            }
            for (ArmorPenetration p : visiblePenetration) {
                lines.add(new LoreLine("penetration#" + p.armorClass().name(), penetrationLine(p, headerHidden)));
            }
        }

        if (bleedEffect != null && bleedEffect.visible()) {
            lines.add(new LoreLine("bleed", bleedLine(bleedEffect)));
        }

        if (criticalEffect != null && criticalEffect.visible()) {
            lines.add(new LoreLine("critical", messages.render(locale, "item.line.critical",
                    Placeholder.unparsed("chance", formatAmount(criticalEffect.chancePercent())),
                    Placeholder.unparsed("bonus", formatAmount(criticalEffect.bonusDamagePercent())))));
        }

        if (stunEffect != null && stunEffect.visible()) {
            lines.add(new LoreLine("stun", messages.render(locale, "item.line.stun",
                    Placeholder.unparsed("chance", formatAmount(stunEffect.chancePercent())),
                    Placeholder.unparsed("duration", formatAmount(stunEffect.durationSeconds())))));
        }

        if (reflectEffect != null && reflectEffect.visible()) {
            lines.add(new LoreLine("reflect", messages.render(locale, "item.line.reflect",
                    Placeholder.unparsed("chance", formatAmount(reflectEffect.chancePercent())),
                    Placeholder.unparsed("percent", formatAmount(reflectEffect.reflectPercent())))));
        }

        List<AttributeModifierEntry> visibleAttributes = attributeModifiers.stream().filter(AttributeModifierEntry::visible).toList();
        if (!visibleAttributes.isEmpty()) {
            if (!hiddenHeaders.contains(LoreHeader.ATTRIBUTES.key())) {
                lines.add(new LoreLine("header#attributes", messages.render(locale, "item.header.attributes")));
            }
            for (AttributeModifierEntry a : visibleAttributes) {
                lines.add(new LoreLine("attribute#" + a.attribute().name() + "|" + a.slot(), attributeLine(a)));
            }
        }

        return lines;
    }

    private Component damageLine(DamageContribution c, boolean headerHidden) {
        String key = c.mode() == DamageMode.PERCENT_OF_TOTAL ? "item.line.damage-percent" : "item.line.damage-flat";
        String sign = c.amount() < 0 ? "-" : "+";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", sign + formatAmount(Math.abs(c.amount()))),
                Placeholder.component("type", messages.damageTypeName(locale, c.damageTypeKey(), headerHidden)));
    }

    private Component resistLine(TypeModifier m, boolean headerHidden) {
        boolean weakness = m.percent() < 0;
        String key = weakness ? "item.line.weakness" : "item.line.resist";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", formatAmount(Math.abs(m.percent()))),
                Placeholder.component("type", messages.resistTypeName(locale, m.damageTypeKey(), weakness, headerHidden)));
    }

    private Component penetrationLine(ArmorPenetration p, boolean headerHidden) {
        boolean percent = p.mode() == DamageMode.PERCENT_OF_TOTAL;
        if (!headerHidden) {
            String key = percent ? "item.line.penetration-percent" : "item.line.penetration-flat";
            return messages.render(locale, key, Placeholder.unparsed("amount", formatAmount(p.amount())));
        }
        String key = percent ? "item.line.penetration-percent-full" : "item.line.penetration-flat-full";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", formatAmount(p.amount())),
                Placeholder.component("class", messages.armorClassPenetrationName(locale, p.armorClass().name())));
    }

    /** {@code damageAmount}/{@code mode} work exactly like a {@link DamageContribution}'s own amount/mode - see {@link BleedEffect}'s javadoc - hence the same flat-vs-percent lang key split as {@link #damageLine}/{@link #penetrationLine}. */
    private Component bleedLine(BleedEffect bleedEffect) {
        String key = bleedEffect.mode() == DamageMode.PERCENT_OF_TOTAL ? "item.line.bleed-percent" : "item.line.bleed-flat";
        return messages.render(locale, key,
                Placeholder.unparsed("chance", formatAmount(bleedEffect.chancePercent())),
                Placeholder.unparsed("duration", formatAmount(bleedEffect.durationSeconds())),
                Placeholder.unparsed("damage", formatAmount(bleedEffect.damageAmount())));
    }

    /** ADD_NUMBER is a flat amount; ADD_SCALAR/MULTIPLY_SCALAR_1 are both percentage-of-base operations - shown with a trailing "%" either way, same simplicity as flat-vs-percent damage contributions. */
    private Component attributeLine(AttributeModifierEntry a) {
        String amount = (a.amount() >= 0 ? "+" : "") + formatAmount(a.amount())
                + (a.operation() == AttributeModifier.Operation.ADD_NUMBER ? "" : "%");
        return messages.render(locale, "item.line.attribute",
                Placeholder.unparsed("amount", amount),
                Placeholder.component("attribute", messages.attributeName(locale, a.attribute().getKey().value())),
                Placeholder.unparsed("slot", a.slot()));
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
            return MINI_MESSAGE.deserialize(LegacyColorTranslator.toMiniMessage(raw));
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
