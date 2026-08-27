package eu.purrtech.purrtechPVE.mythicmobs;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * The only class in this plugin allowed to import the MythicMobs API.
 * Everything else must go through here, and every caller must first check
 * {@code Bukkit.getPluginManager().isPluginEnabled("MythicMobs")} (see
 * {@code PurrtechPVE.onEnable} - only constructed at all when that check
 * passes) before touching an instance of this class, so the plugin still
 * loads and runs fine on a server that doesn't have MythicMobs installed
 * (declared as {@code softdepend} in paper-plugin.yml, {@code compileOnly}
 * in the build).
 *
 * <p>A plugin literally named "MythicMobs" being enabled does NOT guarantee
 * its classes match this API - an older/forked/incompatible build can still
 * satisfy {@code isPluginEnabled} while missing the exact classes compiled
 * against here, which throws {@link NoClassDefFoundError} the first time
 * they're touched (seen in production - see {@code probe()}). Both {@code
 * PurrtechPVE.onEnable} (via {@link #probe()}) and every call site in {@code
 * EquipmentResolver} additionally guard with a {@code catch (Throwable)} so
 * a mismatch degrades to "no MythicMobs integration" instead of breaking
 * every single damage event.
 */
public final class MythicMobsBridge {

    /** Forces the MythicMobs API classes to resolve right now, at a controlled point, instead of lazily mid-combat. */
    public void probe() {
        MythicBukkit.inst().getMobManager();
    }

    public boolean isMythicMob(Entity entity) {
        return MythicBukkit.inst().getMobManager().isActiveMob(entity.getUniqueId());
    }

    /** The mob's internal MythicMobs config name (e.g. "SkeletalKnight"), used as the mob_damage_profile lookup key. */
    public Optional<String> mythicMobInternalName(Entity entity) {
        return MythicBukkit.inst().getMobManager().getSkillCaster(entity.getUniqueId())
                .filter(caster -> caster instanceof ActiveMob)
                .map(caster -> ((ActiveMob) caster).getType().getInternalName());
    }
}
