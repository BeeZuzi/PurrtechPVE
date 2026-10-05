package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.MobDropEntry;
import eu.purrtech.purrtechPVE.db.MobDropRepository;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds our own item templates to a MythicMobs mob's vanilla death drops (see {@code mob_drop},
 * set from the item editor's "MythicMobs" tab), rolling each configured drop's chance % and
 * amount independently. Appending to {@link MythicMobDeathEvent#getDrops()} rather than manually
 * spawning item entities lets MythicMobs' own drop-location/looting-related handling apply to
 * these drops exactly like any of its native ones.
 *
 * <p>That alone is not enough to be sure the drop survives: a mob with {@code PreventOtherDrops},
 * or another plugin that rewrites the drops afterwards, can wipe what the Mythic event added. So
 * what was rolled is remembered per dying entity and, once the vanilla {@link EntityDeathEvent}
 * reaches its last stage ({@code MONITOR}), any rolled stack that is no longer among the drops is
 * put back - never added twice, because a stack that is still present is left alone. Everything is
 * written to the console (what was rolled, what was re-added, any failure) so a missing drop can be
 * traced instead of silently disappearing.
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

    /** A rolled drop is only ever waiting for the very same death, which follows within the same tick. */
    private static final long PENDING_TTL_MILLIS = 10_000;

    private final MobDropRepository mobDropRepository;
    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateService itemTemplateService;
    private final Logger logger;
    // Only ever touched from the main thread (both events are fired there).
    private final Map<UUID, Pending> pending = new HashMap<>();

    public MythicMobDropListener(MobDropRepository mobDropRepository, ItemTemplateRepository templateRepository,
                                  ItemTemplateService itemTemplateService, Logger logger) {
        this.mobDropRepository = mobDropRepository;
        this.templateRepository = templateRepository;
        this.itemTemplateService = itemTemplateService;
        this.logger = logger;
    }

    @EventHandler
    public void onDeath(MythicMobDeathEvent event) {
        String mob = "?";
        try {
            mob = event.getMobType().getInternalName();
            Map<UUID, MobDropEntry> drops = mobDropRepository.findByMob(mob);
            if (drops.isEmpty()) {
                return;
            }
            List<ItemStack> rolled = new ArrayList<>();
            for (Map.Entry<UUID, MobDropEntry> entry : drops.entrySet()) {
                MobDropEntry drop = entry.getValue();
                if (ThreadLocalRandom.current().nextDouble(100.0) >= drop.chancePercent()) {
                    continue;
                }
                Optional<ItemTemplate> template = templateRepository.findById(entry.getKey());
                if (template.isEmpty()) {
                    logger.warning("Drop of mob " + mob + " points at a template that no longer exists (" + entry.getKey() + ") - skipped.");
                    continue;
                }
                ItemStack rendered = itemTemplateService.renderGiveable(template.get().key());
                rendered.setAmount(drop.amount());
                rolled.add(rendered);
            }
            logger.info("Mob " + mob + " died: " + rolled.size() + " of " + drops.size() + " configured custom drop(s) rolled.");
            if (rolled.isEmpty()) {
                return;
            }
            List<ItemStack> updated = new ArrayList<>(event.getDrops());
            updated.addAll(rolled);
            event.setDrops(updated);
            remember(event.getEntity().getUniqueId(), rolled);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Could not add the custom drops of mob " + mob + " - it drops only what MythicMobs gives it.", t);
        }
    }

    /**
     * Last look at the drops of a mob {@link #onDeath} rolled for: puts back any rolled stack that
     * something removed in between (a {@code PreventOtherDrops} mob, another plugin), and leaves
     * alone every one that is still there.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Pending waiting = pending.remove(event.getEntity().getUniqueId());
        if (waiting == null) {
            return;
        }
        try {
            List<ItemStack> drops = event.getDrops();
            int restored = 0;
            for (ItemStack stack : waiting.stacks()) {
                boolean present = drops.stream().anyMatch(existing -> existing != null && existing.isSimilar(stack));
                if (!present) {
                    drops.add(stack.clone());
                    restored++;
                }
            }
            if (restored > 0) {
                logger.info("Put back " + restored + " custom drop(s) that were removed after MythicMobs' death event "
                        + "(PreventOtherDrops or another plugin).");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Could not verify the custom drops of a dying mob.", t);
        }
    }

    private void remember(UUID entityId, List<ItemStack> rolled) {
        long now = System.currentTimeMillis();
        Iterator<Pending> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().createdMillis() > PENDING_TTL_MILLIS) {
                iterator.remove();
            }
        }
        pending.put(entityId, new Pending(List.copyOf(rolled), now));
    }

    private record Pending(List<ItemStack> stacks, long createdMillis) {
    }
}
