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
 */
public final class MythicMobsBridge {

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
