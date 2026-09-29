package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Safety net for {@link MythicMobEquipmentListener}: hooks Bukkit's own {@link CreatureSpawnEvent}
 * (every spawner, command, egg and MythicMobs spawn goes through it) instead of MythicMobs'
 * event, so assigned equipment still gets applied even if {@code MythicMobSpawnEvent} never
 * reaches us - a MythicMobs release that changed when/whether it fires, or a handler-registration
 * problem on that class. The mob's MythicMobs identity is only looked up two ticks later, since
 * MythicMobs registers its ActiveMob for the entity just after Bukkit fires this event.
 *
 * <p>Deliberately mentions no MythicMobs type in its own signatures, and only ever registered
 * once MythicMobs has been detected (see {@code PurrtechPVE.trySetupMythicMobs}). Equipping twice
 * (here and from the MythicMobs event) is harmless - {@code equip} is idempotent.
 */
public final class MobSpawnEquipFallbackListener implements Listener {

    private final PurrtechPVE plugin;

    public MobSpawnEquipFallbackListener(PurrtechPVE plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            MythicMobsBridge bridge = plugin.getMythicMobsBridge();
            MythicMobEquipmentListener listener = plugin.getMobEquipmentListener();
            if (!entity.isValid() || bridge == null || listener == null) {
                return;
            }
            try {
                bridge.mythicMobInternalName(entity)
                        .ifPresent(mobType -> listener.scheduleEquip(entity, mobType, "CreatureSpawnEvent"));
            } catch (Throwable t) {
                // an incompatible MythicMobs build shouldn't break spawning - the main listener logs its own failures
            }
        }, 2L);
    }
}
