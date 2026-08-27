package eu.purrtech.purrtechPVE.damage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of known damage types. Fáze 1 seeds it with a hardcoded
 * default list - it becomes DB-backed (admin-editable via GUI/commands) in a
 * later phase, at which point this class turns into a cache in front of
 * {@code damage_type_definitions} instead of the source of truth.
 */
public final class DamageTypeRegistry {

    /**
     * Fallback bucket used when a weapon has no configured damage-type split
     * (e.g. a vanilla item with no item template attached) - keeps the resist
     * pipeline usable even without any custom item involved.
     */
    public static final String FALLBACK_PHYSICAL = "physical";

    private final Map<String, DamageType> byKey = new LinkedHashMap<>();

    public DamageTypeRegistry() {
        seedDefaults();
    }

    private void seedDefaults() {
        // Icons are plain Unicode glyphs from blocks Minecraft's default font has rendered since
        // early versions (Misc Symbols U+2600-26FF, Dingbats, Greek letters, arrows) - no resource
        // pack required, unlike full-color emoji which don't reliably render client-side.
        register(DamageType.instant(FALLBACK_PHYSICAL, "Fyzické", "⚔"));

        // fyzické podtypy
        register(DamageType.instant("blunt", "Tupé", "⚒"));
        register(DamageType.instant("piercing", "Bodné", "†"));
        register(DamageType.instant("slashing", "Sečné", "‡"));

        // živlové
        register(DamageType.instant("fire", "Ohnivé", "♨"));
        register(DamageType.instant("frozen", "Mrazivé", "❄"));
        register(DamageType.instant("lightning", "Bleskové", "⚡"));
        register(DamageType.instant("acid", "Kyselinové", "☣"));

        // temné/světlé
        register(DamageType.instant("shadow", "Temné", "☾"));
        register(DamageType.instant("spirit", "Duchovní", "☯"));
        register(DamageType.instant("radiant", "Zářivé", "☀"));
        register(DamageType.instant("holy", "Svaté", "✝"));
        register(DamageType.instant("magic", "Magické", "✦"));

        // DoT/status
        register(DamageType.dot("bleed", "Krvácení", "⚕", 20, 0.1));
        register(DamageType.dot("poison", "Jed", "☠", 20, 0.08));
        register(DamageType.instant("explosive", "Výbušné", "☄"));

        // ostatní
        register(DamageType.instant("psychic", "Psychické", "Ψ"));
        register(DamageType.instant("sonic", "Zvukové", "♪"));
        register(DamageType.instant("gravity", "Gravitační", "↓"));
        register(DamageType.instant("necrotic", "Nekrotické", "⚰"));
    }

    public void register(DamageType damageType) {
        byKey.put(damageType.key(), damageType);
    }

    public Optional<DamageType> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public Map<String, DamageType> all() {
        return Map.copyOf(byKey);
    }
}
