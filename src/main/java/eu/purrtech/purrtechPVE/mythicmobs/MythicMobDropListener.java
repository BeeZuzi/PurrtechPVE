package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.MobDropEntry;
import eu.purrtech.purrtechPVE.db.MobDropRepository;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds our own item templates to a MythicMobs mob's vanilla death drops (see {@code mob_drop},
 * set from the item editor's "MythicMobs" tab), rolling each configured drop's chance % and
 * amount independently. Appending to {@link MythicMobDeathEvent#getDrops()} rather than manually
 * spawning item entities lets MythicMobs' own drop-location/looting-related handling apply to
 * these drops exactly like any of its native ones.
 *
 * <p>Rendered via {@link ItemTemplateService#renderGiveable}, same as {@code /pve item give} - a
 * dropped item is a fresh, ordinary giveable stack, not a mob-held ephemeral render like {@link
 * MythicMobEquipmentListener} uses, so it gets that method's render cache benefit too.
 *
 * <p>Only ever registered after a successful {@link MythicMobsBridge#probe()} (see {@code
 * PurrtechPVE.onEnable}), and wrapped in {@code catch (Throwable)} for the same class-mismatch
 * risk as the rest of this package - see {@link MythicMobEquipmentListener}'s javadoc.
 */
public final class MythicMobDropListener implements Listener {

    private final MobDropRepository mobDropRepository;
    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateService itemTemplateService;

    public MythicMobDropListener(MobDropRepository mobDropRepository, ItemTemplateRepository templateRepository,
                                  ItemTemplateService itemTemplateService) {
        this.mobDropRepository = mobDropRepository;
        this.templateRepository = templateRepository;
        this.itemTemplateService = itemTemplateService;
    }

    @EventHandler
    public void onDeath(MythicMobDeathEvent event) {
        try {
            Map<UUID, MobDropEntry> drops = mobDropRepository.findByMob(event.getMobType().getInternalName());
            if (drops.isEmpty()) {
                return;
            }
            List<ItemStack> updated = new ArrayList<>(event.getDrops());
            for (Map.Entry<UUID, MobDropEntry> entry : drops.entrySet()) {
                MobDropEntry drop = entry.getValue();
                if (ThreadLocalRandom.current().nextDouble(100.0) >= drop.chancePercent()) {
                    continue;
                }
                Optional<ItemTemplate> template = templateRepository.findById(entry.getKey());
                if (template.isEmpty()) {
                    continue;
                }
                ItemStack rendered = itemTemplateService.renderGiveable(template.get().key());
                rendered.setAmount(drop.amount());
                updated.add(rendered);
            }
            event.setDrops(updated);
        } catch (Throwable t) {
            // an incompatible MythicMobs build, or any other surprise here, shouldn't break mob death handling
        }
    }
}
