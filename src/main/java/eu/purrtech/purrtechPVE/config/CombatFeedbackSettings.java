package eu.purrtech.purrtechPVE.config;

/**
 * {@code combat.show-effectiveness-colors} - whether {@link eu.purrtech.purrtechPVE.combat.DamageFeedback}
 * colors each action-bar damage number by its effectiveness against the target (weak = yellow,
 * normal = white, resisted = gray) instead of a flat attacker/defender color. On by default per
 * the 2026-09-20 request; an already-installed server's on-disk {@code config.yml} keeps whatever
 * value it was extracted with, so this default only takes effect on a fresh install (or once an
 * admin edits the on-disk key themselves and runs {@code /pve reload}).
 */
public record CombatFeedbackSettings(boolean effectivenessColors) {

    public static CombatFeedbackSettings defaults() {
        return new CombatFeedbackSettings(true);
    }
}
