package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.db.ArmorClassProfileRepository;
import eu.purrtech.purrtechPVE.db.ItemSetDamageThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetMemberRepository;
import eu.purrtech.purrtechPVE.db.ItemSetModifierThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.MobDamageProfileRepository;
import eu.purrtech.purrtechPVE.config.ArmorPenetrationConversionSettings;
import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.ReflectEffect;
import eu.purrtech.purrtechPVE.item.StunEffect;
import eu.purrtech.purrtechPVE.item.TemplateSnapshot;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import eu.purrtech.purrtechPVE.itemset.SetThresholdDamage;
import eu.purrtech.purrtechPVE.itemset.SetThresholdModifier;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobsBridge;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@link ItemTemplate} data off a {@link LivingEntity}'s actual
 * equipped items (via {@link EntityEquipment}, which both players and
 * vanilla/MythicMobs mobs expose the same way - this class doesn't care
 * which) and turns it into the two inputs {@code DamagePipeline.apply}
 * needs: the attacker's outgoing typed damage and the defender's resistance
 * map.
 *
 * <p>Every lookup resolves a stack's contributions/modifiers from the
 * {@link TemplateSnapshot} pinned at that stack's own stamped {@code
 * template_version} - NOT the template's live current data - so an edit an
 * admin chose not to {@code /pve item sync} has zero combat effect on
 * already-issued items, matching {@code ItemTemplateService}'s versioning
 * contract exactly (only the {@code allowedSlots}/trinket restriction is
 * treated as live template config rather than a pinned stat, since it's a
 * placement rule, not a balance number - set thresholds are the same way,
 * see {@code ItemSetService}).
 *
 * <p>Player attackers/defenders also fold in their virtual accessory slots
 * (see {@code trinket}) alongside vanilla equipment; other entities (mobs)
 * don't have those. Defender resistance also folds in the entity's
 * MythicMobs-type {@code mob_damage_profile}, if {@code mythicMobsBridge} is
 * non-null (only constructed when MythicMobs is actually installed) and the
 * entity is one of its mobs.
 *
 * <p>Set bonuses: every equipped piece's template is checked against {@code
 * item_set_members} to count how many pieces of each set are currently worn.
 * Thresholds are cumulative - a wearer with 4 set pieces gets every
 * threshold's bonus whose {@code pieceCount} is 4 or fewer, not just the
 * highest one, matching how tiered set bonuses conventionally work.
 *
 * <p>Armor class bonuses: a piece tagged with one of the 3 fixed {@code
 * ArmorClass} values (LIGHT/MEDIUM/HEAVY) additionally gets whatever
 * resistance/weakness {@code armor_class_profile} defines for that class,
 * on top of its own {@code item_type_modifier} rows - live/global, like
 * {@code mob_damage_profile}, not versioned per item.
 *
 * <p>Armor penetration: the attacker's wielded weapon's {@link
 * ArmorPenetration} stats (pinned to its snapshot, like any other weapon
 * stat) reduce whatever the defender's gear got from {@code
 * armor_class_profile} for the matching class, for that one hit's
 * resistance calculation only - see {@link ArmorPenetration}'s javadoc for
 * why it's scoped to just the class-wide profile and not an item's own
 * individually-set resistance, and why nothing is ever touched in anyone's
 * inventory.
 */
public final class EquipmentResolver {

    private static final EquipmentSlot[] VANILLA_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateSnapshotRepository snapshotRepository;
    private final MobDamageProfileRepository mobDamageProfileRepository;
    private final ArmorClassProfileRepository armorClassProfileRepository;
    private final AccessoryRepository accessoryRepository;
    private final ItemSetMemberRepository setMemberRepository;
    private final ItemSetDamageThresholdRepository setDamageThresholdRepository;
    private final ItemSetModifierThresholdRepository setModifierThresholdRepository;
    private final ItemRenderer renderer;
    private final MythicMobsBridge mythicMobsBridge;
    private ArmorPenetrationConversionSettings conversionSettings;

    public EquipmentResolver(ItemTemplateRepository templateRepository,
                              ItemTemplateSnapshotRepository snapshotRepository,
                              MobDamageProfileRepository mobDamageProfileRepository,
                              ArmorClassProfileRepository armorClassProfileRepository,
                              AccessoryRepository accessoryRepository,
                              ItemSetMemberRepository setMemberRepository,
                              ItemSetDamageThresholdRepository setDamageThresholdRepository,
                              ItemSetModifierThresholdRepository setModifierThresholdRepository,
                              ItemRenderer renderer,
                              MythicMobsBridge mythicMobsBridge,
                              ArmorPenetrationConversionSettings conversionSettings) {
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.mobDamageProfileRepository = mobDamageProfileRepository;
        this.armorClassProfileRepository = armorClassProfileRepository;
        this.accessoryRepository = accessoryRepository;
        this.setMemberRepository = setMemberRepository;
        this.setDamageThresholdRepository = setDamageThresholdRepository;
        this.setModifierThresholdRepository = setModifierThresholdRepository;
        this.renderer = renderer;
        this.mythicMobsBridge = mythicMobsBridge;
        this.conversionSettings = conversionSettings;
    }

    /** Re-reads {@code armor-penetration-conversion} on {@code /pve reload} - see {@code PurrtechPVE.reload}. */
    public void refresh(ArmorPenetrationConversionSettings conversionSettings) {
        this.conversionSettings = conversionSettings;
    }

    /**
     * The held weapon's WIELDED contributions split rawDamage into typed buckets (100% {@link
     * DamageTypeRegistry#FALLBACK_PHYSICAL} if the held item has none/isn't one of our templates); every equipped
     * piece's WORN contributions (respecting each template's allowedSlots restriction, if any) are added as bonus
     * damage on top of that split, merged into the same buckets, and so are any active set-threshold bonuses.
     */
    public Map<String, Double> resolveOutgoingTypedDamage(LivingEntity attacker, double rawDamage) {
        EntityEquipment equipment = attacker.getEquipment();
        if (equipment == null) {
            return Map.of(DamageTypeRegistry.FALLBACK_PHYSICAL, rawDamage);
        }

        Map<String, Double> typed = new HashMap<>();
        List<DamageContribution> wielded = resolvedItemOf(equipment.getItemInMainHand())
                .map(item -> item.snapshot().damageContributions())
                .orElse(List.of())
                .stream().filter(c -> c.context() == ModifierContext.WIELDED).toList();
        if (wielded.isEmpty()) {
            typed.put(DamageTypeRegistry.FALLBACK_PHYSICAL, rawDamage);
        } else {
            for (DamageContribution c : wielded) {
                typed.merge(c.damageTypeKey(), resolveAmount(c, rawDamage), Double::sum);
            }
        }

        Map<String, ItemStack> pieces = allEquippedPieces(attacker, equipment);
        for (Map.Entry<String, ItemStack> entry : pieces.entrySet()) {
            for (DamageContribution c : contributionsAllowedIn(entry.getValue(), entry.getKey())) {
                if (c.context() == ModifierContext.WORN) {
                    typed.merge(c.damageTypeKey(), resolveAmount(c, rawDamage), Double::sum);
                }
            }
        }

        for (Map.Entry<UUID, Integer> setCount : countEquippedSetPieces(pieces).entrySet()) {
            for (SetThresholdDamage t : setDamageThresholdRepository.findBySet(setCount.getKey())) {
                if (t.pieceCount() <= setCount.getValue()) {
                    double amount = t.mode() == DamageMode.PERCENT_OF_TOTAL ? rawDamage * t.amount() / 100.0 : t.amount();
                    typed.merge(t.damageTypeKey(), amount, Double::sum);
                }
            }
        }
        return typed;
    }

    /**
     * The attacker's {@link CriticalEffect}, pooled additively across their whole equipped set
     * (weapon in hand, worn armor, trinkets alike, respecting each piece's allowedSlots) - so a
     * single piece with just chance (or just bonus) set still contributes, whether it's held or
     * worn, exactly like {@link #resolveReflectEffects} already works. Each contributing piece's
     * {@code chancePercent}/{@code bonusDamagePercent} are summed; empty if no equipped piece has
     * one configured at all.
     */
    public Optional<CriticalEffect> resolveCriticalEffect(LivingEntity attacker) {
        EntityEquipment equipment = attacker.getEquipment();
        if (equipment == null) {
            return Optional.empty();
        }
        double chance = 0;
        double bonus = 0;
        boolean any = false;
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(attacker, equipment).entrySet()) {
            Optional<CriticalEffect> effect = resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.snapshot().criticalEffect());
            if (effect.isPresent()) {
                chance += effect.get().chancePercent();
                bonus += effect.get().bonusDamagePercent();
                any = true;
            }
        }
        return any ? Optional.of(new CriticalEffect(chance, bonus, true)) : Optional.empty();
    }

    /**
     * The attacker's {@link BleedEffect}, pooled additively across their whole equipped set - same
     * whole-equipped-set treatment as {@link #resolveCriticalEffect}. Each contributing piece's
     * {@code chancePercent}/{@code durationSeconds}/{@code damageAmount} are summed; {@code mode}
     * isn't summable, so it's taken from whichever contributing piece was seen last (only matters
     * if more than one piece has bleed configured at once).
     */
    public Optional<BleedEffect> resolveBleedEffect(LivingEntity attacker) {
        EntityEquipment equipment = attacker.getEquipment();
        if (equipment == null) {
            return Optional.empty();
        }
        double chance = 0;
        double duration = 0;
        double damage = 0;
        DamageMode mode = DamageMode.FLAT;
        boolean any = false;
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(attacker, equipment).entrySet()) {
            Optional<BleedEffect> effect = resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.snapshot().bleedEffect());
            if (effect.isPresent()) {
                chance += effect.get().chancePercent();
                duration += effect.get().durationSeconds();
                damage += effect.get().damageAmount();
                mode = effect.get().mode();
                any = true;
            }
        }
        return any ? Optional.of(new BleedEffect(chance, duration, damage, mode, true)) : Optional.empty();
    }

    /**
     * The attacker's {@link StunEffect}, pooled additively across their whole equipped set - same
     * whole-equipped-set treatment as {@link #resolveCriticalEffect}. Each contributing piece's
     * {@code chancePercent}/{@code durationSeconds} are summed.
     */
    public Optional<StunEffect> resolveStunEffect(LivingEntity attacker) {
        EntityEquipment equipment = attacker.getEquipment();
        if (equipment == null) {
            return Optional.empty();
        }
        double chance = 0;
        double duration = 0;
        boolean any = false;
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(attacker, equipment).entrySet()) {
            Optional<StunEffect> effect = resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.snapshot().stunEffect());
            if (effect.isPresent()) {
                chance += effect.get().chancePercent();
                duration += effect.get().durationSeconds();
                any = true;
            }
        }
        return any ? Optional.of(new StunEffect(chance, duration, true)) : Optional.empty();
    }

    /**
     * Sum of {@link ItemTemplate#stunResistPercent} across the defender's whole equipped set, respecting each
     * piece's {@code allowedSlots} - live/unversioned, unlike {@link #resolveArmorPoints}, and pooled flatly with
     * no per-{@link ArmorClass} grouping or armor-penetration reduction, since it's a piece's own classification
     * magnitude rather than a class-wide profile bonus. See {@link StunEffect}'s javadoc for how this reduces the
     * attacker's effective stun chance.
     */
    public double resolveStunResistPercent(LivingEntity defender) {
        EntityEquipment equipment = defender.getEquipment();
        if (equipment == null) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(defender, equipment).entrySet()) {
            total += resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.template().stunResistPercent())
                    .orElse(0.0);
        }
        return total;
    }

    /**
     * Sum of {@link ItemTemplate#critResistPercent} across the defender's whole equipped set, respecting each
     * piece's {@code allowedSlots} - same live/unversioned, flatly-pooled treatment as {@link
     * #resolveStunResistPercent}. Positive scales the attacker's {@link CriticalEffect} chance down, negative
     * (weakness) scales it up.
     */
    public double resolveCritResistPercent(LivingEntity defender) {
        EntityEquipment equipment = defender.getEquipment();
        if (equipment == null) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(defender, equipment).entrySet()) {
            total += resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.template().critResistPercent())
                    .orElse(0.0);
        }
        return total;
    }

    /**
     * Every equipped piece's (weapon in hand, worn armor, trinkets alike) {@link ReflectEffect}, if
     * complete (see {@link ReflectEffect#isComplete()}) - this is resolved off the DEFENDER's whole
     * equipped set, respecting each piece's allowedSlots, since it has to trigger whether the item
     * carrying it is held or worn (including trinkets), same as bleed/critical/stun above and
     * {@link #resolvePassiveReflectPercent} below. See {@code CombatDamageListener} for how
     * each entry is rolled independently and the reflected damage applied back to the attacker.
     */
    public List<ReflectEffect> resolveReflectEffects(LivingEntity defender) {
        EntityEquipment equipment = defender.getEquipment();
        if (equipment == null) {
            return List.of();
        }
        List<ReflectEffect> effects = new ArrayList<>();
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(defender, equipment).entrySet()) {
            resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.snapshot().reflectEffect())
                    .filter(effect -> effect != null && effect.isComplete())
                    .ifPresent(effects::add);
        }
        return effects;
    }

    /**
     * Sum of {@link ItemTemplate#passiveReflectPercent} across the defender's whole equipped set, respecting each
     * piece's {@code allowedSlots} - same live/unversioned, flatly-pooled treatment as {@link
     * #resolveCritResistPercent}/{@link #resolveStunResistPercent}. Unlike {@link #resolveReflectEffects}, this
     * always applies (no chance roll) - see {@code CombatDamageListener} for how it's folded into the same
     * reflected-damage total.
     */
    public double resolvePassiveReflectPercent(LivingEntity defender) {
        EntityEquipment equipment = defender.getEquipment();
        if (equipment == null) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(defender, equipment).entrySet()) {
            total += resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .map(item -> item.template().passiveReflectPercent())
                    .orElse(0.0);
        }
        return total;
    }

    /**
     * Sum of item_type_modifier percent across every equipped piece respecting allowedSlots (positive resists,
     * negative weakens), plus any active set-threshold resistance bonuses, plus the entity's MythicMobs
     * mob_damage_profile if it is one of its mobs, minus whatever the attacker's weapon's armor penetration
     * eats into the defender's armor-class-profile resistance.
     *
     * @param attacker {@code null} when there's no specific attacking weapon to consider (e.g. a
     *                 {@code BleedManager} DOT tick, which isn't any one hit) - armor penetration
     *                 is simply skipped in that case.
     */
    public Map<String, Double> resolveResistance(LivingEntity attacker, LivingEntity defender) {
        Map<String, Double> resist = new HashMap<>();
        // Tracked separately so armor penetration only eats into THIS (the shared, per-class
        // bonus), never an item's own individually-set resistance - see ArmorPenetration's javadoc.
        Map<ArmorClass, Map<String, Double>> classProfileContribution = new HashMap<>();

        EntityEquipment equipment = defender.getEquipment();
        if (equipment != null) {
            Map<String, ItemStack> pieces = allEquippedPieces(defender, equipment);
            for (Map.Entry<String, ItemStack> entry : pieces.entrySet()) {
                for (TypeModifier modifier : modifiersAllowedIn(entry.getValue(), entry.getKey())) {
                    resist.merge(modifier.damageTypeKey(), modifier.percent(), Double::sum);
                }
                classProfileModifiersAllowedIn(entry.getValue(), entry.getKey()).forEach((armorClass, byType) ->
                        byType.forEach((type, percent) -> classProfileContribution
                                .computeIfAbsent(armorClass, k -> new HashMap<>())
                                .merge(type, percent, Double::sum)));
            }

            for (Map.Entry<UUID, Integer> setCount : countEquippedSetPieces(pieces).entrySet()) {
                for (SetThresholdModifier t : setModifierThresholdRepository.findBySet(setCount.getKey())) {
                    if (t.pieceCount() <= setCount.getValue()) {
                        resist.merge(t.damageTypeKey(), t.percent(), Double::sum);
                    }
                }
            }
        }

        if (mythicMobsBridge != null) {
            try {
                mythicMobsBridge.mythicMobInternalName(defender).ifPresent(internalName ->
                        mobDamageProfileRepository.findByMob(internalName)
                                .forEach((type, percent) -> resist.merge(type, percent, Double::sum)));
            } catch (Throwable t) {
                // an incompatible MythicMobs build shouldn't break vanilla combat resolution - see MythicMobsBridge's javadoc
            }
        }

        applyArmorPenetration(attacker, classProfileContribution, resist);
        return resist;
    }

    /**
     * Reduces {@code resist} by the attacker's wielded weapon's {@link ArmorPenetration}, per damage type any
     * armor class's profile touched - purely this hit's math, nothing persisted. A penetration's own class
     * always applies at full strength against a defender's SAME class; against a DIFFERENT class it's scaled by
     * {@code conversionSettings.factor(p.armorClass(), defenderClass)} (1.0 default - see {@code
     * ArmorPenetrationConversionSettings}), so e.g. a light-only weapon still has some (or, at default settings,
     * full) effect against medium/heavy armor instead of doing nothing at all. {@link DamageMode#FLAT} subtracts
     * {@code amount * factor} points straight off {@code resist} regardless of its size (a weapon that "punches
     * through 10 units of armor" always removes exactly 10 against its own class, whether the target has 20 or
     * 200); {@link DamageMode#PERCENT_OF_TOTAL} instead subtracts {@code amount} percent OF {@code resist}'s own
     * current value, then scaled by {@code factor}. Neither is clamped to the class's own contribution size (so
     * over-penetrating can push a type into net weakness), matching how "penetration exceeding total armor deals
     * bonus damage" conventionally works.
     */
    private void applyArmorPenetration(LivingEntity attacker, Map<ArmorClass, Map<String, Double>> classProfileContribution,
                                        Map<String, Double> resist) {
        if (attacker == null) {
            return;
        }
        EntityEquipment attackerEquipment = attacker.getEquipment();
        if (attackerEquipment == null) {
            return;
        }
        List<ArmorPenetration> penetration = resolvedItemOf(attackerEquipment.getItemInMainHand())
                .map(item -> item.snapshot().armorPenetration())
                .orElse(List.of());
        for (ArmorPenetration p : penetration) {
            for (Map.Entry<ArmorClass, Map<String, Double>> classEntry : classProfileContribution.entrySet()) {
                double factor = conversionSettings.factor(p.armorClass(), classEntry.getKey());
                if (factor == 0) {
                    continue;
                }
                for (String type : classEntry.getValue().keySet()) {
                    double reduction = p.mode() == DamageMode.PERCENT_OF_TOTAL
                            ? resist.getOrDefault(type, 0.0) * (p.amount() / 100.0) * factor
                            : p.amount() * factor;
                    resist.merge(type, -reduction, Double::sum);
                }
            }
        }
    }

    /**
     * Sum of flat, vanilla-style armor points ({@link ItemTemplate#armorAmount}) across the defender's whole
     * equipped set, pooled per {@link ArmorClass} - fed through {@code DamagePipeline.armorMultiplier} as a
     * separate multiplicative layer on top of the percent-based {@link #resolveResistance}, mirroring vanilla's
     * own armor {@code DamageModifier}. The attacker's wielded {@link ArmorPenetration} reduces each class's
     * pooled points the same way it reduces {@code armor_class_profile} percent above (same-class at full
     * strength, other classes scaled by {@code conversionSettings}), before the classes are summed.
     *
     * @param attacker {@code null} when there's no specific attacking weapon to consider (e.g. a
     *                 {@code BleedManager} DOT tick) - armor penetration is simply skipped in that case.
     */
    public double resolveArmorPoints(LivingEntity attacker, LivingEntity defender) {
        EntityEquipment equipment = defender.getEquipment();
        if (equipment == null) {
            return 0;
        }
        Map<ArmorClass, Double> pointsByClass = new EnumMap<>(ArmorClass.class);
        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(defender, equipment).entrySet()) {
            resolvedItemOf(entry.getValue())
                    .filter(item -> isAllowedInSlot(item.template(), entry.getKey()))
                    .ifPresent(item -> {
                        ArmorClass armorClass = item.template().armorClass();
                        if (armorClass != null && item.template().armorAmount() != 0) {
                            pointsByClass.merge(armorClass, item.template().armorAmount(), Double::sum);
                        }
                    });
        }
        if (pointsByClass.isEmpty()) {
            return 0;
        }

        if (attacker != null) {
            EntityEquipment attackerEquipment = attacker.getEquipment();
            List<ArmorPenetration> penetration = attackerEquipment == null ? List.of()
                    : resolvedItemOf(attackerEquipment.getItemInMainHand())
                            .map(item -> item.snapshot().armorPenetration())
                            .orElse(List.of());
            for (ArmorPenetration p : penetration) {
                for (Map.Entry<ArmorClass, Double> classEntry : pointsByClass.entrySet()) {
                    double factor = conversionSettings.factor(p.armorClass(), classEntry.getKey());
                    if (factor == 0) {
                        continue;
                    }
                    double reduction = p.mode() == DamageMode.PERCENT_OF_TOTAL
                            ? classEntry.getValue() * (p.amount() / 100.0) * factor
                            : p.amount() * factor;
                    classEntry.setValue(Math.max(0, classEntry.getValue() - reduction));
                }
            }
        }
        return pointsByClass.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Just the armor-class-profile share of a piece's resistance (see {@link #modifiersAllowedIn}), grouped by class, for {@link #applyArmorPenetration}. */
    private Map<ArmorClass, Map<String, Double>> classProfileModifiersAllowedIn(ItemStack stack, String slotName) {
        return resolvedItemOf(stack)
                .filter(item -> isAllowedInSlot(item.template(), slotName))
                .map(item -> {
                    ArmorClass armorClass = item.template().armorClass();
                    if (armorClass == null) {
                        return Map.<ArmorClass, Map<String, Double>>of();
                    }
                    return Map.of(armorClass, armorClassProfileRepository.findByArmorClass(armorClass.name()));
                })
                .orElse(Map.of());
    }

    /** How many equipped pieces belong to each set - counts physical pieces, so two rings of the same set template count as 2. */
    private Map<UUID, Integer> countEquippedSetPieces(Map<String, ItemStack> equippedPieces) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (ItemStack stack : equippedPieces.values()) {
            resolvedItemOf(stack).ifPresent(item ->
                    setMemberRepository.findSetIdsContainingTemplate(item.template().id())
                            .forEach(setId -> counts.merge(setId, 1, Integer::sum)));
        }
        return counts;
    }

    /** Vanilla equipment slots + (for players) virtual accessory slots, keyed by slot name for allowedSlots checks. */
    private Map<String, ItemStack> allEquippedPieces(LivingEntity entity, EntityEquipment equipment) {
        Map<String, ItemStack> pieces = new HashMap<>();
        for (EquipmentSlot slot : VANILLA_SLOTS) {
            pieces.put(slot.name(), equipment.getItem(slot));
        }
        if (entity instanceof Player player) {
            pieces.putAll(accessoryRepository.findAll(player.getUniqueId()));
        }
        return pieces;
    }

    private double resolveAmount(DamageContribution contribution, double rawDamage) {
        return contribution.mode() == DamageMode.PERCENT_OF_TOTAL
                ? rawDamage * contribution.amount() / 100.0
                : contribution.amount();
    }

    private List<DamageContribution> contributionsAllowedIn(ItemStack stack, String slotName) {
        return resolvedItemOf(stack)
                .filter(item -> isAllowedInSlot(item.template(), slotName))
                .map(item -> item.snapshot().damageContributions())
                .orElse(List.of());
    }

    /** The item's own type modifiers, plus its armor class's profile (if it has one) - see ArmorClass's javadoc. */
    private List<TypeModifier> modifiersAllowedIn(ItemStack stack, String slotName) {
        return resolvedItemOf(stack)
                .filter(item -> isAllowedInSlot(item.template(), slotName))
                .map(item -> {
                    ArmorClass armorClass = item.template().armorClass();
                    if (armorClass == null) {
                        return item.snapshot().typeModifiers();
                    }
                    List<TypeModifier> combined = new ArrayList<>(item.snapshot().typeModifiers());
                    armorClassProfileRepository.findByArmorClass(armorClass.name())
                            .forEach((type, percent) -> combined.add(new TypeModifier(type, percent, true)));
                    return combined;
                })
                .orElse(List.of());
    }

    /** An empty allowedSlots list means unrestricted - applies no matter where it's equipped. */
    private boolean isAllowedInSlot(ItemTemplate template, String slotName) {
        return template.allowedSlots().isEmpty() || template.allowedSlots().contains(slotName);
    }

    private Optional<ResolvedItem> resolvedItemOf(ItemStack stack) {
        return renderer.readStamp(stack).flatMap(stamp ->
                templateRepository.findByKey(stamp.templateKey()).flatMap(template ->
                        snapshotRepository.find(template.id(), stamp.templateVersion())
                                .map(snapshot -> new ResolvedItem(template, snapshot))));
    }

    private record ResolvedItem(ItemTemplate template, TemplateSnapshot snapshot) {
    }
}
