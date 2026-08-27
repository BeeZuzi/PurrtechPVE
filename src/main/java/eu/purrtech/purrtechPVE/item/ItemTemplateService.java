package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.ArmorPenetrationRepository;
import eu.purrtech.purrtechPVE.db.BleedEffectRepository;
import eu.purrtech.purrtechPVE.db.CriticalEffectRepository;
import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
        this.snapshotRepository = snapshotRepository;
        this.damageTypeRegistry = damageTypeRegistry;
        this.renderer = renderer;
    }

    public ItemTemplate create(String key, Material baseMaterial, String displayName, String createdBy) {
        return create(key, baseMaterial, null, displayName, createdBy);
    }

    /** Same as {@link #create(String, Material, String, String)}, but also seeds the base's custom model data (e.g. imported from a real item's ItemMeta). */
    public ItemTemplate create(String key, Material baseMaterial, Integer customModelData, String displayName, String createdBy) {
        if (templateRepository.findByKey(key).isPresent()) {
            throw new DuplicateTemplateKeyException(key);
        }
        long now = System.currentTimeMillis();
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), key, displayName, baseMaterial, customModelData,
                false, List.of(), null, 1, 1, now, now, createdBy);
        templateRepository.insert(template);
        snapshotRepository.insert(snapshotOf(template, List.of(), List.of(), List.of(), List.of(), null, null));
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
        ItemTemplate template = requireTemplate(key);
        requireDamageType(damageTypeKey);
        damageContributionRepository.upsert(template.id(), new DamageContribution(damageTypeKey, amount, mode, context));
        return bumpVersion(template);
    }

    public ItemTemplate removeDamageContribution(String key, String damageTypeKey, ModifierContext context) {
        ItemTemplate template = requireTemplate(key);
        damageContributionRepository.remove(template.id(), damageTypeKey, context);
        return bumpVersion(template);
    }

    public ItemTemplate setTypeModifier(String key, String damageTypeKey, double percent) {
        ItemTemplate template = requireTemplate(key);
        requireDamageType(damageTypeKey);
        typeModifierRepository.upsert(template.id(), new TypeModifier(damageTypeKey, percent));
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
        ItemTemplate template = requireTemplate(key);
        armorPenetrationRepository.upsert(template.id(), new ArmorPenetration(armorClass, amount));
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

    /** This weapon's chance to inflict bleeding on a hit + how long it lasts - a stat, so it bumps version. See {@link BleedEffect}'s javadoc. */
    public ItemTemplate setBleedEffect(String key, double chancePercent, double durationSeconds) {
        ItemTemplate template = requireTemplate(key);
        bleedEffectRepository.upsert(template.id(), new BleedEffect(chancePercent, durationSeconds));
        return bumpVersion(template);
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
        ItemTemplate template = requireTemplate(key);
        criticalEffectRepository.upsert(template.id(), new CriticalEffect(chancePercent, bonusDamagePercent));
        return bumpVersion(template);
    }

    public ItemTemplate removeCriticalEffect(String key) {
        ItemTemplate template = requireTemplate(key);
        criticalEffectRepository.remove(template.id());
        return bumpVersion(template);
    }

    public Optional<CriticalEffect> criticalEffect(String key) {
        return criticalEffectRepository.findByTemplate(requireTemplate(key).id());
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
        ItemTemplate updated = new ItemTemplate(template.id(), template.key(), template.displayName(), template.baseMaterial(),
                template.customModelData(), !slotNames.isEmpty(), List.copyOf(slotNames), template.armorClass(), template.version(),
                template.syncedVersion(), template.createdAt(), System.currentTimeMillis(), template.createdBy());
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
        ItemTemplate updated = new ItemTemplate(template.id(), template.key(), template.displayName(), template.baseMaterial(),
                template.customModelData(), template.trinket(), template.allowedSlots(), armorClass, template.version(),
                template.syncedVersion(), template.createdAt(), System.currentTimeMillis(), template.createdBy());
        templateRepository.update(updated);
        return updated;
    }

    /**
     * Swaps the template's base material/custom model data - a stat like any other, so it bumps version and
     * writes a snapshot same as damage/resist changes (an un-synced rebase has zero effect on circulating items).
     */
    public ItemTemplate rebase(String key, Material newBaseMaterial, Integer newCustomModelData) {
        ItemTemplate template = requireTemplate(key);
        ItemTemplate withNewBase = new ItemTemplate(template.id(), template.key(), template.displayName(), newBaseMaterial,
                newCustomModelData, template.trinket(), template.allowedSlots(), template.armorClass(), template.version(),
                template.syncedVersion(), template.createdAt(), template.updatedAt(), template.createdBy());
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
        return renderer.render(template, contributions, modifiers, enchantments, armorPenetration, bleed, critical);
    }

    private ItemTemplate requireTemplate(String key) {
        return templateRepository.findByKey(key).orElseThrow(() -> new TemplateNotFoundException(key));
    }

    private void requireDamageType(String damageTypeKey) {
        if (damageTypeRegistry.find(damageTypeKey).isEmpty()) {
            throw new UnknownDamageTypeException(damageTypeKey);
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
                criticalEffectRepository.findByTemplate(bumped.id()).orElse(null)));
        return bumped;
    }

    private TemplateSnapshot snapshotOf(ItemTemplate template, List<DamageContribution> contributions, List<TypeModifier> modifiers,
                                         List<TemplateEnchantment> enchantments, List<ArmorPenetration> armorPenetration,
                                         BleedEffect bleedEffect, CriticalEffect criticalEffect) {
        return new TemplateSnapshot(template.id(), template.key(), template.version(), template.displayName(),
                template.baseMaterial(), template.customModelData(), contributions, modifiers, enchantments,
                armorPenetration, bleedEffect, criticalEffect, template.updatedAt());
    }
}
