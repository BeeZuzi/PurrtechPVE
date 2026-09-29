package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.db.ArmorPenetrationRepository;
import eu.purrtech.purrtechPVE.db.AttributeModifierRepository;
import eu.purrtech.purrtechPVE.db.BleedEffectRepository;
import eu.purrtech.purrtechPVE.db.CriticalEffectRepository;
import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.MobEquipmentRepository;
import eu.purrtech.purrtechPVE.db.ReflectEffectRepository;
import eu.purrtech.purrtechPVE.db.StunEffectRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Equips a MythicMobs mob with whichever of our item templates are
 * configured for its type (see {@code mob_equipment}, set from the item
 * editor's "MythicMobs" tab) the moment it spawns. Rendered fresh from the
 * template's current live data every spawn - these are ephemeral mob-held
 * items, not player-owned persistent stacks, so none of {@code
 * ItemTemplate}'s version-pinning machinery applies here.
 *
 * <p>Only ever registered after a successful {@link MythicMobsBridge#probe()}
 * (see {@code PurrtechPVE.onEnable}), and the registration itself is also
 * wrapped in {@code catch (Throwable)} there, since merely referencing
 * {@link MythicMobSpawnEvent} in this class's method signature requires that
 * class to resolve - same class-mismatch risk as the rest of this package.
 */
public final class MythicMobEquipmentListener implements Listener {

    // Mob types already reported to the console by scheduleEquip - a spawner would otherwise spam it.
    private static final java.util.Set<String> LOGGED_TYPES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final MobEquipmentRepository mobEquipmentRepository;
    private final ItemTemplateRepository templateRepository;
    private final DamageContributionRepository damageContributionRepository;
    private final TypeModifierRepository typeModifierRepository;
    private final TemplateEnchantmentRepository enchantmentRepository;
    private final ArmorPenetrationRepository armorPenetrationRepository;
    private final BleedEffectRepository bleedEffectRepository;
    private final CriticalEffectRepository criticalEffectRepository;
    private final StunEffectRepository stunEffectRepository;
    private final ReflectEffectRepository reflectEffectRepository;
    private final AttributeModifierRepository attributeModifierRepository;
    private final ItemRenderer renderer;

    public MythicMobEquipmentListener(MobEquipmentRepository mobEquipmentRepository,
                                       ItemTemplateRepository templateRepository,
                                       DamageContributionRepository damageContributionRepository,
                                       TypeModifierRepository typeModifierRepository,
                                       TemplateEnchantmentRepository enchantmentRepository,
                                       ArmorPenetrationRepository armorPenetrationRepository,
                                       BleedEffectRepository bleedEffectRepository,
                                       CriticalEffectRepository criticalEffectRepository,
                                       StunEffectRepository stunEffectRepository,
                                       ReflectEffectRepository reflectEffectRepository,
                                       AttributeModifierRepository attributeModifierRepository,
                                       ItemRenderer renderer) {
        this.mobEquipmentRepository = mobEquipmentRepository;
        this.templateRepository = templateRepository;
        this.damageContributionRepository = damageContributionRepository;
        this.typeModifierRepository = typeModifierRepository;
        this.enchantmentRepository = enchantmentRepository;
        this.armorPenetrationRepository = armorPenetrationRepository;
        this.bleedEffectRepository = bleedEffectRepository;
        this.criticalEffectRepository = criticalEffectRepository;
        this.stunEffectRepository = stunEffectRepository;
        this.reflectEffectRepository = reflectEffectRepository;
        this.attributeModifierRepository = attributeModifierRepository;
        this.renderer = renderer;
    }

    @EventHandler
    public void onSpawn(MythicMobSpawnEvent event) {
        // getEntity() rather than the deprecated getLivingEntity(): same object, but the non-
        // deprecated accessor is the one MythicMobs keeps stable across 5.x releases.
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        scheduleEquip(entity, event.getMobType().getInternalName(), "MythicMobSpawnEvent");
    }

    /**
     * Equips right now, then again 1 and 10 ticks later. MythicMobs applies the mob's own configured
     * equipment as part of spawning, in an order relative to {@link MythicMobSpawnEvent} that has
     * differed between releases - a single set at event time can be overwritten straight afterwards,
     * which looked exactly like "the assigned armor never shows up". Setting it again a moment later
     * makes ours win either way; it is idempotent, so the extra passes are harmless when unneeded.
     */
    public void scheduleEquip(LivingEntity entity, String mobType, String source) {
        equip(entity, mobType);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(MythicMobEquipmentListener.class);
        for (long delay : new long[]{1L, 10L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (entity.isValid()) {
                    equip(entity, mobType);
                }
            }, delay);
        }
        if (LOGGED_TYPES.add(mobType)) {
            plugin.getLogger().info("Mob type " + mobType + " spawned (via " + source + ") - applying its assigned equipment ("
                    + mobEquipmentRepository.findByMob(mobType).size() + " slot(s) configured). Logged once per type.");
        }
    }

    /** Sets {@code entity}'s equipment to whatever is currently configured for {@code mobType} - also used by the mob menu to re-equip mobs that are already alive. */
    public void equip(LivingEntity entity, String mobType) {
        try {
            Map<String, UUID> equipment = mobEquipmentRepository.findByMob(mobType);
            if (equipment.isEmpty()) {
                return;
            }
            EntityEquipment entityEquipment = entity.getEquipment();
            if (entityEquipment == null) {
                return;
            }
            for (Map.Entry<String, UUID> entry : equipment.entrySet()) {
                EquipmentSlot slot = parseSlot(entry.getKey());
                if (slot == null) {
                    continue;
                }
                Optional<ItemTemplate> template = templateRepository.findById(entry.getValue());
                if (template.isEmpty()) {
                    continue;
                }
                ItemStack rendered = renderer.render(template.get(),
                        damageContributionRepository.findByTemplate(template.get().id()),
                        typeModifierRepository.findByTemplate(template.get().id()),
                        enchantmentRepository.findByTemplate(template.get().id()),
                        armorPenetrationRepository.findByTemplate(template.get().id()),
                        bleedEffectRepository.findByTemplate(template.get().id()).orElse(null),
                        criticalEffectRepository.findByTemplate(template.get().id()).orElse(null),
                        stunEffectRepository.findByTemplate(template.get().id()).orElse(null),
                        reflectEffectRepository.findByTemplate(template.get().id()).orElse(null),
                        attributeModifierRepository.findByTemplate(template.get().id()));
                entityEquipment.setItem(slot, rendered);
            }
        } catch (Throwable t) {
            // Never lets a failure break mob spawning, but no longer swallows it silently either -
            // a mob spawning without its assigned gear was otherwise impossible to diagnose.
            JavaPlugin.getProvidingPlugin(MythicMobEquipmentListener.class).getLogger().log(Level.WARNING,
                    "Failed to equip MythicMobs mob type " + mobType + " - it keeps whatever equipment it had.", t);
        }
    }

    private EquipmentSlot parseSlot(String name) {
        try {
            return EquipmentSlot.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
