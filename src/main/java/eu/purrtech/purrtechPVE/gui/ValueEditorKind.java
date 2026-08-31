package eu.purrtech.purrtechPVE.gui;

/**
 * Which single numeric stat field {@link ValueEditorMenu} is currently adjusting, and which tab
 * to return to on "Back" - the editor itself is generic (same +/- buttons + visibility toggle
 * layout for all of them), this just says how to read/write the one value each kind represents.
 * {@link ValueEditorHolder#entryId()} carries the rest (which damage type/armor class/attribute+
 * slot within that kind) - {@code null} for the two singleton-per-template kinds (BLEED/CRIT).
 */
public enum ValueEditorKind {
    RESIST(ItemEditorTab.RESIST, false),
    ARMOR_PENETRATION(ItemEditorTab.ARMOR_PENETRATION, false),
    ATTRIBUTE(ItemEditorTab.BASE, false),
    BLEED_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false),
    BLEED_DURATION(ItemEditorTab.SPECIAL_EFFECTS, false),
    /** The only kind with a flat/percent mode toggle - see {@code BleedEffect}'s javadoc for why bleed damage works like a normal {@code DamageContribution} now. */
    BLEED_DAMAGE(ItemEditorTab.SPECIAL_EFFECTS, true),
    CRIT_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false),
    CRIT_BONUS(ItemEditorTab.SPECIAL_EFFECTS, false);

    private final ItemEditorTab returnTab;
    private final boolean hasMode;

    ValueEditorKind(ItemEditorTab returnTab, boolean hasMode) {
        this.returnTab = returnTab;
        this.hasMode = hasMode;
    }

    public ItemEditorTab returnTab() {
        return returnTab;
    }

    public boolean hasMode() {
        return hasMode;
    }
}
