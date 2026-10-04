package eu.purrtech.purrtechPVE.combat;

import java.util.Map;

/**
 * The typed damage a MythicMobs {@code pvedamage} skill hands to {@link
 * eu.purrtech.purrtechPVE.listener.CombatDamageListener} for the one synchronous {@code damage()}
 * call it makes - the listener then uses it instead of deriving a split from the attacker's held
 * weapon, and runs it through the defender's resistances/armor like any other hit. Only ever
 * touched on the main thread, so a plain field is enough; the previous value is restored so nested
 * calls (a reflect, a skill triggered from the hit) can't leak into each other.
 */
public final class SkillDamageContext {

    private static Map<String, Double> current;

    private SkillDamageContext() {
    }

    public static void run(Map<String, Double> typedDamage, Runnable damageCall) {
        Map<String, Double> previous = current;
        current = typedDamage;
        try {
            damageCall.run();
        } finally {
            current = previous;
        }
    }

    /** The typed damage of the skill hit currently being dealt, or {@code null} for an ordinary hit. */
    public static Map<String, Double> current() {
        return current;
    }
}
