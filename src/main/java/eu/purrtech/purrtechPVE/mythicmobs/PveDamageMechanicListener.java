package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.combat.AttackRegistry;
import eu.purrtech.purrtechPVE.db.MobAttackDamageRepository;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Hooks the {@code pvedamage} mechanic into MythicMobs' skill parser and records every attack id it
 * sees in {@link AttackRegistry}. Same registration caveat as {@link MythicMobEquipmentListener}:
 * the handler's signature names a MythicMobs class, so registering this is wrapped in a
 * {@code catch (Throwable)} by the caller.
 */
public final class PveDamageMechanicListener implements Listener {

    private static final String MECHANIC_NAME = "pvedamage";

    private final Plugin plugin;
    private final MythicMobsBridge bridge;
    private final MobAttackDamageRepository repository;
    private final AttackRegistry attackRegistry;

    public PveDamageMechanicListener(Plugin plugin, MythicMobsBridge bridge, MobAttackDamageRepository repository,
                                     AttackRegistry attackRegistry) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.repository = repository;
        this.attackRegistry = attackRegistry;
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        if (!MECHANIC_NAME.equalsIgnoreCase(event.getMechanicName())) {
            return;
        }
        String attackId = event.getConfig().getString(new String[]{"id", "attack", "name"}, "");
        if (attackId.isBlank()) {
            plugin.getLogger().warning("pvedamage mechanic without an id - use pvedamage{id=<attack-name>}");
            return;
        }
        double fallback = event.getConfig().getDouble(new String[]{"amount", "a"}, 0.0);
        attackRegistry.register(attackId);
        event.register(new PveDamageMechanic(plugin, bridge, repository, attackId, fallback));
    }
}
