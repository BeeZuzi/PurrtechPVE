package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.ArmorPenetrationRepository;
import eu.purrtech.purrtechPVE.db.AttributeModifierRepository;
import eu.purrtech.purrtechPVE.db.BleedEffectRepository;
import eu.purrtech.purrtechPVE.db.CriticalEffectRepository;
import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates template CRUD + its damage-contribution/type-modifier rows.
 * Every mutation bumps the template's {@code version} and snapshots the full
 * resulting state - but does NOT touch {@code syncedVersion} or any
 * already-issued item, i.e. edits are invisible to circulation by default.
 * {@link #propagate} is the explicit separate action that catches {@code
 * syncedVersion} up to {@code version}; the actual in-place re-rendering of
 * online players' stacks is {@link ItemSyncService}'s job, called by the
 * command layer right after {@link #propagate} commits.
 */
public final class ItemTemplateService {

    private final ItemTemplateRepository templateRepository;
    private final DamageContributionRepository damageContributionRepository;
    private final TypeModifierRepository typeModifierRepository;
    private final TemplateEnchantmentRepository enchantmentRepository;
    private final ArmorPenetrationRepository armorPenetrationRepository;
    private final BleedEffectRepository bleedEffectRepository;
    private final CriticalEffectRepository criticalEffectRepository;
    private final AttributeModifierRepository attributeModifierRepository;
    private final ItemTemplateSnapshotRepository snapshotRepository;
    private final DamageTypeRegistry damageTypeRegistry;
    private final ItemRenderer renderer;

    public ItemTemplateService(ItemTemplateRepository templateRepository,
                                DamageContributionRepository damageContributionRepository,
                                TypeModifierRepository typeModifierRepository,
                                TemplateEnchantmentRepository enchantmentRepository,
                                ArmorPenetrationRepository armorPenetrationRepository,
                                BleedEffectRepository bleedEffectRepository,
                                CriticalEffectRepository criticalEffectRepository,
                                AttributeModifierRepository attributeModifierRepository,
                                ItemTemplateSnapshotRepository snapshotRepository,
                                DamageTypeRegistry damageTypeRegistry,
                                ItemRenderer renderer) {
        this.templateRepository = templateRepository;
        this.damageContributionRepository = damageContributionRepository;
        this.typeModifierRepository = typeModifierRepository;
        this.enchantmentRepository = enchantmentRepository;
        this.armorPenetrationRepository = armorPenetrationRepository;
        this.bleedEffectRepository = bleedEffectRepository;
        this.criticalEffectRepository = criticalEffectRepository;
        this.attributeModifierRepository = attributeModifierRepository;
        this.snapshotRepository = snapshotRepository;
        this.damageTypeRegistry = damageTypeRegistry;
        this.renderer = renderer;
    }

    public ItemTemplate create(String key, Material baseMaterial, String displayName, String createdBy) {
        return create(key, baseMaterial, null, null, List.of(), displayName, createdBy);
    }

    /** Same as {@link #create(String, Material, String, String)}, but also seeds the base's custom model data (e.g. imported from a real item's ItemMeta). */
    public ItemTemplate create(String key, Material baseMaterial, Integer customModelData, String displayName, String createdBy) {
        return create(key, baseMaterial, customModelData, null, List.of(), displayName, createdBy);
    }

    /**
     * Same as {@link #create(String, Material, Integer, String, String)}, but also seeds {@code
     * baseItemSnapshot} - the imported item's own custom_data (see {@link BaseItemSnapshots}),
     * carried forward onto every future render so third-party plugins (ItemsAdder, etc.) that key
     * their own rendering off it keep working - and {@code customLore}, seeded from the imported
     * item's own lore (see {@code ItemRenderer}/{@code PveCommand.importFromValhalla}) so its
     * original flavor text isn't just silently dropped in favor of this plugin's own stat lore.
     */
    public ItemTemplate create(String key, Material baseMaterial, Integer customModelData, byte[] baseItemSnapshot,
                                List<String> customLore, String displayName, String createdBy) {
        if (templateRepository.findByKey(key).isPresent()) {
            throw new DuplicateTemplateKeyException(key);
        }
        long now = System.currentTimeMillis();
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), key, displayName, customLore, List.of(), baseMaterial, baseItemSnapshot,
                customModelData, false, List.of(), null, 1, 1, now, now, createdBy);
        templateRepository.insert(template);
        snapshotRepository.insert(snapshotOf(template, List.of(), List.of(), List.of(), List.of(), null, null, List.of()));
        return template;
    }

    public boolean delete(String key) {
        return templateRepository.delete(key);
    }

    public Optional<ItemTemplate> findByKey(String key) {
        return templateRepository.findByKey(key);
    }

    public List<ItemTemplate> listAll() {
        return templateRepository.findAll();
    }

    public ItemTemplate setDamageContribution(String key, String damageTypeKey, double amount, DamageMode mode,
                                               ModifierContext context) {
        return setDamageContribution(key, damageTypeKey, amount, mode, context, true);
    }

    /**
     * Same as the 5-arg overload, but also sets whether this contribution shows its own lore line
     * - see {@link DamageContribution#visible()}.
     *
     * @throws NonContributableDamageTypeException for {@code "bleed"} - a weapon's bleed damage
     *         is configured via {@link #setBleedEffect} instead, not as a normal contribution
     *         (see {@link BleedEffect}'s javadoc for why). {@code "bleed"} remains a perfectly
     *         valid {@link #setTypeModifier} key though - only contributions reject it.
     */
    public ItemTemplate setDamageContribution(String key, String damageTypeKey, double amount, DamageMode mode,
                                               ModifierContext context, boolean visible) {
        ItemTemplate template = requireTemplate(key);
        requireDamageType(damageTypeKey);
        requireContributable(damageTypeKey);
        damageContributionRepository.upsert(template.id(), new DamageContribution(damageTypeKey, amount, mode, context, visible));
        return bumpVersion(template);
    }

    /** Flips an existing contribution's lore visibility without touching its amount/mode - the value itself is untouched. */
    public ItemTemplate toggleDamageContributionVisibility(String key, String damageTypeKey, ModifierContext context) {
        ItemTemplate template = requireTemplate(key);
        DamageContribution current = damageContributionRepository.findByTemplate(template.id()).stream()
                .filter(c -> c.damageTypeKey().equals(damageTypeKey) && c.context() == context)
                .findFirst().orElseThrow(() -> new IllegalStateException("No damage contribution " + damageTypeKey + "/" + context + " on " + key));
        damageContributionRepository.upsert(template.id(),
                new DamageContribution(damageTypeKey, current.amount(), current.mode(), context, !current.visible()));
        return bumpVersion(template);
    }

    public ItemTemplate removeDamageContribution(String key, String damageTypeKey, ModifierContext context) {
        ItemTemplate template = requireTemplate(key);
        damageContributionRepository.remove(template.id(), damageTypeKey, context);
        return bumpVersion(template);
    }

    public ItemTemplate setTypeModifier(String key, String damageTypeKey, double percent) {
        return setTypeModifier(key, damageTypeKey, percent, true);
    }

    /** Same as the 3-arg overload, but also sets whether this modifier shows its own lore line - see {@link TypeModifier#visible()}. */
    public ItemTemplate setTypeModifier(String key, String damageTypeKey, double percent, boolean visible) {
        ItemTemplate template = requireTemplate(key);
        requireDamageType(damageTypeKey);
        typeModifierRepository.upsert(template.id(), new TypeModifier(damageTypeKey, percent, visible));
        return bumpVersion(template);
    }

    /** Flips an existing modifier's lore visibility without touching its percent. */
    public ItemTemplate toggleTypeModifierVisibility(String key, String damageTypeKey) {
        ItemTemplate template = requireTemplate(key);
        TypeModifier current = typeModifierRepository.findByTemplate(template.id()).stream()
                .filter(m -> m.damageTypeKey().equals(damageTypeKey))
                .findFirst().orElseThrow(() -> new IllegalStateException("No type modifier " + damageTypeKey + " on " + key));
        typeModifierRepository.upsert(template.id(), new TypeModifier(damageTypeKey, current.percent(), !current.visible()));
        return bumpVersion(template);
    }

    public ItemTemplate removeTypeModifier(String key, String damageTypeKey) {
        ItemTemplate template = requireTemplate(key);
        typeModifierRepository.remove(template.id(), damageTypeKey);
        return bumpVersion(template);
    }

    public List<DamageContribution> damageContributions(String key) {
        return damageContributionRepository.findByTemplate(requireTemplate(key).id());
    }

    public List<TypeModifier> typeModifiers(String key) {
        return typeModifierRepository.findByTemplate(requireTemplate(key).id());
    }

    public ItemTemplate setEnchantment(String key, String enchantmentKey, int level) {
        ItemTemplate template = requireTemplate(key);
        enchantmentRepository.upsert(template.id(), new TemplateEnchantment(enchantmentKey, level));
        return bumpVersion(template);
    }

    public ItemTemplate removeEnchantment(String key, String enchantmentKey) {
        ItemTemplate template = requireTemplate(key);
        enchantmentRepository.remove(template.id(), enchantmentKey);
        return bumpVersion(template);
    }

    public List<TemplateEnchantment> enchantments(String key) {
        return enchantmentRepository.findByTemplate(requireTemplate(key).id());
    }

    /**
     * How much of the given armor class this (presumably a weapon) template's WIELDED hits punch
     * through - a stat like damage contributions, so it bumps version. See {@link ArmorPenetration}'s
     * javadoc for exactly what it does at combat time (only ever reduces the defender's {@code
     * armor_class_profile}-sourced resistance for that one hit, nothing persisted).
     */
    public ItemTemplate setArmorPenetration(String key, ArmorClass armorClass, double amount) {
        return setArmorPenetration(key, armorClass, amount, true);
    }

    /** Same as the 3-arg overload, but also sets whether this entry shows its own lore line - see {@link ArmorPenetration#visible()}. */
    public ItemTemplate setArmorPenetration(String key, ArmorClass armorClass, double amount, boolean visible) {
        ItemTemplate template = requireTemplate(key);
        armorPenetrationRepository.upsert(template.id(), new ArmorPenetration(armorClass, amount, visible));
        return bumpVersion(template);
    }

    /** Flips an existing entry's lore visibility without touching its amount. */
    public ItemTemplate toggleArmorPenetrationVisibility(String key, ArmorClass armorClass) {
        ItemTemplate template = requireTemplate(key);
        ArmorPenetration current = armorPenetrationRepository.findByTemplate(template.id()).stream()
                .filter(p -> p.armorClass() == armorClass)
                .findFirst().orElseThrow(() -> new IllegalStateException("No armor penetration " + armorClass + " on " + key));
        armorPenetrationRepository.upsert(template.id(), new ArmorPenetration(armorClass, current.amount(), !current.visible()));
        return bumpVersion(template);
    }

    public ItemTemplate removeArmorPenetration(String key, ArmorClass armorClass) {
        ItemTemplate template = requireTemplate(key);
        armorPenetrationRepository.remove(template.id(), armorClass);
        return bumpVersion(template);
    }

    public List<ArmorPenetration> armorPenetration(String key) {
        return armorPenetrationRepository.findByTemplate(requireTemplate(key).id());
    }

    /**
     * This weapon's chance to inflict bleeding on a hit, how long it lasts, and how much total
     * damage it deals over that duration ({@code damageAmount}/{@code mode}, exactly like a
     * normal {@link DamageContribution}'s {@code amount}/{@code mode}) - a stat, so it bumps
     * version. See {@link BleedEffect}'s javadoc; all 3 have to be set (see {@link
     * BleedEffect#isComplete()}) before it actually rolls in combat.
     */
    public ItemTemplate setBleedEffect(String key, double chancePercent, double durationSeconds, double damageAmount, DamageMode mode) {
        return setBleedEffect(key, chancePercent, durationSeconds, damageAmount, mode, currentBleedVisible(key));
    }

    /** Same as the 5-arg overload, but also sets whether the combined bleed line shows in lore - see {@link BleedEffect#visible()}. */
    public ItemTemplate setBleedEffect(String key, double chancePercent, double durationSeconds, double damageAmount,
                                        DamageMode mode, boolean visible) {
        ItemTemplate template = requireTemplate(key);
        bleedEffectRepository.upsert(template.id(), new BleedEffect(chancePercent, durationSeconds, damageAmount, mode, visible));
        return bumpVersion(template);
    }

    /** Flips the bleed effect's lore visibility without touching its other fields. */
    public ItemTemplate toggleBleedEffectVisibility(String key) {
        BleedEffect current = bleedEffect(key).orElse(new BleedEffect(0, 0, 0, DamageMode.FLAT, true));
        return setBleedEffect(key, current.chancePercent(), current.durationSeconds(), current.damageAmount(), current.mode(), !current.visible());
    }

    private boolean currentBleedVisible(String key) {
        return bleedEffect(key).map(BleedEffect::visible).orElse(true);
    }

    public ItemTemplate removeBleedEffect(String key) {
        ItemTemplate template = requireTemplate(key);
        bleedEffectRepository.remove(template.id());
        return bumpVersion(template);
    }

    public Optional<BleedEffect> bleedEffect(String key) {
        return bleedEffectRepository.findByTemplate(requireTemplate(key).id());
    }

    /** This weapon's chance to land a critical hit + how much extra damage it deals - a stat, so it bumps version. See {@link CriticalEffect}'s javadoc. */
    public ItemTemplate setCriticalEffect(String key, double chancePercent, double bonusDamagePercent) {
        return setCriticalEffect(key, chancePercent, bonusDamagePercent, currentCriticalVisible(key));
    }

    /** Same as the 3-arg overload, but also sets whether the combined crit line shows in lore - see {@link CriticalEffect#visible()}. */
    public ItemTemplate setCriticalEffect(String key, double chancePercent, double bonusDamagePercent, boolean visible) {
        ItemTemplate template = requireTemplate(key);
        criticalEffectRepository.upsert(template.id(), new CriticalEffect(chancePercent, bonusDamagePercent, visible));
        return bumpVersion(template);
    }

    /** Flips the critical effect's lore visibility without touching its chance/bonus. */
    public ItemTemplate toggleCriticalEffectVisibility(String key) {
        CriticalEffect current = criticalEffect(key).orElse(new CriticalEffect(0, 0, true));
        return setCriticalEffect(key, current.chancePercent(), current.bonusDamagePercent(), !current.visible());
    }

    private boolean currentCriticalVisible(String key) {
        return criticalEffect(key).map(CriticalEffect::visible).orElse(true);
    }

    public ItemTemplate removeCriticalEffect(String key) {
        ItemTemplate template = requireTemplate(key);
        criticalEffectRepository.remove(template.id());
        return bumpVersion(template);
    }

    public Optional<CriticalEffect> criticalEffect(String key) {
        return criticalEffectRepository.findByTemplate(requireTemplate(key).id());
    }

    /**
     * A real vanilla {@link Attribute} bonus this template grants in one specific {@code slot} -
     * a stat like damage contributions, so it bumps version. See {@link AttributeModifierEntry}'s
     * javadoc for exactly how {@code slot} determines whether it's baked into the rendered item
     * (a vanilla equipment slot group) or applied by {@code TrinketAttributeListener} instead (one
     * of this server's configured accessory slot names) - this method doesn't validate {@code
     * slot} against either, same as {@link #setAllowedSlots} doesn't validate its slot names
     * either; that's left to the command/GUI layer, which has {@code AccessorySettings} to check against.
     */
    public ItemTemplate setAttributeModifier(String key, Attribute attribute, double amount, AttributeModifier.Operation operation, String slot) {
        return setAttributeModifier(key, attribute, amount, operation, slot, true);
    }

    /** Same as the 5-arg overload, but also sets whether this entry shows its own lore line - see {@link AttributeModifierEntry#visible()}. */
    public ItemTemplate setAttributeModifier(String key, Attribute attribute, double amount, AttributeModifier.Operation operation,
                                              String slot, boolean visible) {
        ItemTemplate template = requireTemplate(key);
        attributeModifierRepository.upsert(template.id(), new AttributeModifierEntry(attribute, amount, operation, slot, visible));
        return bumpVersion(template);
    }

    /** Flips an existing entry's lore visibility without touching its amount/operation. */
    public ItemTemplate toggleAttributeModifierVisibility(String key, Attribute attribute, String slot) {
        ItemTemplate template = requireTemplate(key);
        AttributeModifierEntry current = attributeModifierRepository.findByTemplate(template.id()).stream()
                .filter(a -> a.attribute() == attribute && a.slot().equals(slot))
                .findFirst().orElseThrow(() -> new IllegalStateException("No attribute modifier " + attribute + "/" + slot + " on " + key));
        attributeModifierRepository.upsert(template.id(),
                new AttributeModifierEntry(attribute, current.amount(), current.operation(), slot, !current.visible()));
        return bumpVersion(template);
    }

    public ItemTemplate removeAttributeModifier(String key, Attribute attribute, String slot) {
        ItemTemplate template = requireTemplate(key);
        attributeModifierRepository.remove(template.id(), attribute, slot);
        return bumpVersion(template);
    }

    public List<AttributeModifierEntry> attributeModifiers(String key) {
        return attributeModifierRepository.findByTemplate(requireTemplate(key).id());
    }

    /**
     * Extra, admin-authored lore lines (each a raw MiniMessage string) shown above whatever stat
     * lines get auto-generated - a stat like any other, so it bumps version. See {@link
     * ItemTemplate}'s javadoc for why this exists (imported items would otherwise lose their
     * original flavor text entirely) and {@code ItemRenderer} for where it's rendered.
     */
    public ItemTemplate setCustomLore(String key, List<String> lines) {
        ItemTemplate template = requireTemplate(key);
        ItemTemplate updated = new ItemTemplate(template.id(), template.key(), template.displayName(), List.copyOf(lines),
                template.hiddenHeaders(), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), template.trinket(),
                template.allowedSlots(), template.armorClass(), template.version(), template.syncedVersion(),
                template.createdAt(), template.updatedAt(), template.createdBy());
        return bumpVersion(updated);
    }

    /**
     * Flips whether one of the 5 auto-generated lore section headers (see {@link LoreHeader}) is
     * suppressed for this template - a stat like {@link #setCustomLore}, so it bumps version.
     * Independent of any individual entry's own {@code visible} flag underneath it.
     */
    public ItemTemplate toggleHeader(String key, LoreHeader header) {
        ItemTemplate template = requireTemplate(key);
        List<String> hidden = new ArrayList<>(template.hiddenHeaders());
        if (!hidden.remove(header.key())) {
            hidden.add(header.key());
        }
        ItemTemplate updated = new ItemTemplate(template.id(), template.key(), template.displayName(), template.customLore(),
                List.copyOf(hidden), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), template.trinket(),
                template.allowedSlots(), template.armorClass(), template.version(), template.syncedVersion(),
                template.createdAt(), template.updatedAt(), template.createdBy());
        return bumpVersion(updated);
    }

    /** Marks the template's current version as pushed to circulation - the caller still has to actually walk online players (see ItemSyncService). */
    public ItemTemplate propagate(String key) {
        ItemTemplate template = requireTemplate(key);
        ItemTemplate synced = template.withSyncedVersion(template.version(), System.currentTimeMillis());
        templateRepository.update(synced);
        return synced;
    }

    /**
     * Restricts where this template's WORN contributions/resistance apply - a placement rule, not a stat, so
     * unlike everything above this does NOT bump version/write a snapshot; it applies to circulating items
     * immediately (see EquipmentResolver.isAllowedInSlot). An empty list means unrestricted (default) and also
     * clears the trinket flag; a non-empty list sets it.
     */
    public ItemTemplate setAllowedSlots(String key, List<String> slotNames) {
        ItemTemplate template = requireTemplate(key);
        ItemTemplate updated = new ItemTemplate(template.id(), template.key(), template.displayName(), template.customLore(),
                template.hiddenHeaders(), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), !slotNames.isEmpty(),
                List.copyOf(slotNames), template.armorClass(), template.version(), template.syncedVersion(), template.createdAt(),
                System.currentTimeMillis(), template.createdBy());
        templateRepository.update(updated);
        return updated;
    }

    /**
     * Which of the 3 fixed armor weight classes this template belongs to (or {@code null} for
     * non-armor). Same live-classification treatment as {@link #setAllowedSlots} - the class
     * itself doesn't bump version; what resistance/weakness that class actually grants lives in
     * {@code armor_class_profile} (see {@code ArmorClassProfileRepository}) and applies
     * immediately, already-issued items included, exactly like {@code mob_damage_profile} does
     * for MythicMobs mob types.
     */
    public ItemTemplate setArmorClass(String key, ArmorClass armorClass) {
        ItemTemplate template = requireTemplate(key);
        ItemTemplate updated = new ItemTemplate(template.id(), template.key(), template.displayName(), template.customLore(),
                template.hiddenHeaders(), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), template.trinket(),
                template.allowedSlots(), armorClass, template.version(), template.syncedVersion(), template.createdAt(),
                System.currentTimeMillis(), template.createdBy());
        templateRepository.update(updated);
        return updated;
    }

    /**
     * Swaps the template's base material/custom model data/{@code baseItemSnapshot} - a stat like any other, so it
     * bumps version and writes a snapshot same as damage/resist changes (an un-synced rebase has zero effect on
     * circulating items). {@code newBaseItemSnapshot} replaces whatever was captured before outright (not merged) -
     * see {@link BaseItemSnapshots#capture}, the caller is expected to have captured it from whatever real item
     * this rebase is sourced from, {@code null} if there isn't one (e.g. a rebase not sourced from any held item).
     */
    public ItemTemplate rebase(String key, Material newBaseMaterial, Integer newCustomModelData, byte[] newBaseItemSnapshot) {
        ItemTemplate template = requireTemplate(key);
        ItemTemplate withNewBase = new ItemTemplate(template.id(), template.key(), template.displayName(), template.customLore(),
                template.hiddenHeaders(), newBaseMaterial, newBaseItemSnapshot, newCustomModelData, template.trinket(), template.allowedSlots(),
                template.armorClass(), template.version(), template.syncedVersion(), template.createdAt(), template.updatedAt(),
                template.createdBy());
        return bumpVersion(withNewBase);
    }

    public ItemStack renderGiveable(String key) {
        ItemTemplate template = requireTemplate(key);
        List<DamageContribution> contributions = damageContributionRepository.findByTemplate(template.id());
        List<TypeModifier> modifiers = typeModifierRepository.findByTemplate(template.id());
        List<TemplateEnchantment> enchantments = enchantmentRepository.findByTemplate(template.id());
        List<ArmorPenetration> armorPenetration = armorPenetrationRepository.findByTemplate(template.id());
        BleedEffect bleed = bleedEffectRepository.findByTemplate(template.id()).orElse(null);
        CriticalEffect critical = criticalEffectRepository.findByTemplate(template.id()).orElse(null);
        List<AttributeModifierEntry> attributeModifiers = attributeModifierRepository.findByTemplate(template.id());
        return renderer.render(template, contributions, modifiers, enchantments, armorPenetration, bleed, critical, attributeModifiers);
    }

    private ItemTemplate requireTemplate(String key) {
        return templateRepository.findByKey(key).orElseThrow(() -> new TemplateNotFoundException(key));
    }

    private void requireDamageType(String damageTypeKey) {
        if (damageTypeRegistry.find(damageTypeKey).isEmpty()) {
            throw new UnknownDamageTypeException(damageTypeKey);
        }
    }

    /** {@code "bleed"} is the only key this rejects right now - see {@link #setDamageContribution}'s javadoc. */
    private void requireContributable(String damageTypeKey) {
        if ("bleed".equals(damageTypeKey)) {
            throw new NonContributableDamageTypeException(damageTypeKey);
        }
    }

    private ItemTemplate bumpVersion(ItemTemplate template) {
        ItemTemplate bumped = template.withBumpedVersion(System.currentTimeMillis());
        templateRepository.update(bumped);
        snapshotRepository.insert(snapshotOf(bumped,
                damageContributionRepository.findByTemplate(bumped.id()),
                typeModifierRepository.findByTemplate(bumped.id()),
                enchantmentRepository.findByTemplate(bumped.id()),
                armorPenetrationRepository.findByTemplate(bumped.id()),
                bleedEffectRepository.findByTemplate(bumped.id()).orElse(null),
                criticalEffectRepository.findByTemplate(bumped.id()).orElse(null),
                attributeModifierRepository.findByTemplate(bumped.id())));
        return bumped;
    }

    private TemplateSnapshot snapshotOf(ItemTemplate template, List<DamageContribution> contributions, List<TypeModifier> modifiers,
                                         List<TemplateEnchantment> enchantments, List<ArmorPenetration> armorPenetration,
                                         BleedEffect bleedEffect, CriticalEffect criticalEffect, List<AttributeModifierEntry> attributeModifiers) {
        return new TemplateSnapshot(template.id(), template.key(), template.version(), template.displayName(), template.customLore(),
                template.hiddenHeaders(), template.baseMaterial(), template.baseItemSnapshot(), template.customModelData(), contributions,
                modifiers, enchantments, armorPenetration, bleedEffect, criticalEffect, attributeModifiers, template.updatedAt());
    }
}
