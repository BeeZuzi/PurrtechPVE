package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.MobEquipmentRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    private final MobEquipmentRepository mobEquipmentRepository;
    private final ItemTemplateRepository templateRepository;
    private final DamageContributionRepository damageContributionRepository;
    private final TypeModifierRepository typeModifierRepository;
    private final TemplateEnchantmentRepository enchantmentRepository;
    private final ItemRenderer renderer;

    public MythicMobEquipmentListener(MobEquipmentRepository mobEquipmentRepository,
                                       ItemTemplateRepository templateRepository,
                                       DamageContributionRepository damageContributionRepository,
                                       TypeModifierRepository typeModifierRepository,
                                       TemplateEnchantmentRepository enchantmentRepository,
                                       ItemRenderer renderer) {
        this.mobEquipmentRepository = mobEquipmentRepository;
        this.templateRepository = templateRepository;
        this.damageContributionRepository = damageContributionRepository;
        this.typeModifierRepository = typeModifierRepository;
        this.enchantmentRepository = enchantmentRepository;
        this.renderer = renderer;
    }

    @EventHandler
    public void onSpawn(MythicMobSpawnEvent event) {
        try {
            Map<String, UUID> equipment = mobEquipmentRepository.findByMob(event.getMobType().getInternalName());
            if (equipment.isEmpty()) {
                return;
            }
            LivingEntity entity = event.getLivingEntity();
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
                        enchantmentRepository.findByTemplate(template.get().id()));
                entityEquipment.setItem(slot, rendered);
            }
        } catch (Throwable t) {
            // an incompatible MythicMobs build, or any other surprise here, shouldn't break mob spawning
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
