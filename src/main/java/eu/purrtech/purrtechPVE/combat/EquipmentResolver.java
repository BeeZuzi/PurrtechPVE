package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.MobDamageProfileRepository;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TemplateSnapshot;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobsBridge;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * placement rule, not a balance number).
 *
 * <p>Player attackers/defenders also fold in their virtual accessory slots
 * (see {@code trinket}) alongside vanilla equipment; other entities (mobs)
 * don't have those. Defender resistance also folds in the entity's
 * MythicMobs-type {@code mob_damage_profile}, if {@code mythicMobsBridge} is
 * non-null (only constructed when MythicMobs is actually installed) and the
 * entity is one of its mobs.
 */
public final class EquipmentResolver {

    private static final EquipmentSlot[] VANILLA_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateSnapshotRepository snapshotRepository;
    private final MobDamageProfileRepository mobDamageProfileRepository;
    private final AccessoryRepository accessoryRepository;
    private final ItemRenderer renderer;
    private final MythicMobsBridge mythicMobsBridge;

    public EquipmentResolver(ItemTemplateRepository templateRepository,
                              ItemTemplateSnapshotRepository snapshotRepository,
                              MobDamageProfileRepository mobDamageProfileRepository,
                              AccessoryRepository accessoryRepository,
                              ItemRenderer renderer,
                              MythicMobsBridge mythicMobsBridge) {
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.mobDamageProfileRepository = mobDamageProfileRepository;
        this.accessoryRepository = accessoryRepository;
        this.renderer = renderer;
        this.mythicMobsBridge = mythicMobsBridge;
    }

    /**
     * The held weapon's WIELDED contributions split rawDamage into typed buckets (100% {@link
     * DamageTypeRegistry#FALLBACK_PHYSICAL} if the held item has none/isn't one of our templates); every equipped
     * piece's WORN contributions (respecting each template's allowedSlots restriction, if any) are added as bonus
     * damage on top of that split, merged into the same buckets.
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

        for (Map.Entry<String, ItemStack> entry : allEquippedPieces(attacker, equipment).entrySet()) {
            for (DamageContribution c : contributionsAllowedIn(entry.getValue(), entry.getKey())) {
                if (c.context() == ModifierContext.WORN) {
                    typed.merge(c.damageTypeKey(), resolveAmount(c, rawDamage), Double::sum);
                }
            }
        }
        return typed;
    }

    /**
     * Sum of item_type_modifier percent across every equipped piece respecting allowedSlots (positive resists,
     * negative weakens), plus the entity's MythicMobs mob_damage_profile if it is one of its mobs.
     */
    public Map<String, Double> resolveResistance(LivingEntity defender) {
        Map<String, Double> resist = new HashMap<>();

        EntityEquipment equipment = defender.getEquipment();
        if (equipment != null) {
            for (Map.Entry<String, ItemStack> entry : allEquippedPieces(defender, equipment).entrySet()) {
                for (TypeModifier modifier : modifiersAllowedIn(entry.getValue(), entry.getKey())) {
                    resist.merge(modifier.damageTypeKey(), modifier.percent(), Double::sum);
                }
            }
        }

        if (mythicMobsBridge != null) {
            mythicMobsBridge.mythicMobInternalName(defender).ifPresent(internalName ->
                    mobDamageProfileRepository.findByMob(internalName)
                            .forEach((type, percent) -> resist.merge(type, percent, Double::sum)));
        }

        return resist;
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

    private List<TypeModifier> modifiersAllowedIn(ItemStack stack, String slotName) {
        return resolvedItemOf(stack)
                .filter(item -> isAllowedInSlot(item.template(), slotName))
                .map(item -> item.snapshot().typeModifiers())
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
