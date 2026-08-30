package eu.purrtech.purrtechPVE.config;

/**
 * {@code combat.show-effectiveness-colors} - whether {@link eu.purrtech.purrtechPVE.combat.DamageFeedback}
 * colors each action-bar damage number by its effectiveness against the target (weak = yellow,
 * normal = white, resisted = gray) instead of a flat attacker/defender color. Off by default, so
 * an upgraded server's action-bar feedback looks exactly like before until an admin opts in.
 */
public record CombatFeedbackSettings(boolean effectivenessColors) {

    public static CombatFeedbackSettings defaults() {
        return new CombatFeedbackSettings(false);
    }
}
