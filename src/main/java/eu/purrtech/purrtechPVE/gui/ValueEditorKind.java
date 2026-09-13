package eu.purrtech.purrtechPVE.gui;

/**
 * Which single numeric stat field {@link ValueEditorMenu} is currently adjusting, and which tab
 * to return to on "Back" - the editor itself is generic (same +/- buttons + visibility toggle
 * layout for all of them), this just says how to read/write the one value each kind represents.
 * {@link ValueEditorHolder#entryId()} carries the rest (which damage type/armor class/attribute+
 * slot within that kind) - {@code null} for the two singleton-per-template kinds (BLEED/CRIT).
 */
public enum ValueEditorKind {
    RESIST(ItemEditorTab.RESIST, false, false),
    ARMOR_PENETRATION(ItemEditorTab.ARMOR_PENETRATION, false, false),
    ATTRIBUTE(ItemEditorTab.BASE, false, false),
    /**
     * The only kind with a context toggle (wielded/worn) - {@link ValueEditorHolder#entryId()} is
     * {@code "<damageTypeKey>|<ModifierContext>"}, same shape as {@link #ATTRIBUTE}'s
     * {@code "<attribute>|<slot>"}. A single damage type can have up to two independent
     * contributions (one wielded, one worn) - see {@code ItemEditorMenu}'s DAMAGE tab for how
     * both get their own entry instead of being merged into one.
     */
    DAMAGE(ItemEditorTab.DAMAGE, true, true),
    BLEED_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false, false),
    BLEED_DURATION(ItemEditorTab.SPECIAL_EFFECTS, false, false),
    /** See {@code BleedEffect}'s javadoc for why bleed damage works like a normal {@code DamageContribution} now. */
    BLEED_DAMAGE(ItemEditorTab.SPECIAL_EFFECTS, true, false),
    CRIT_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false, false),
    CRIT_BONUS(ItemEditorTab.SPECIAL_EFFECTS, false, false);

    private final ItemEditorTab returnTab;
    private final boolean hasMode;
    private final boolean hasContext;

    ValueEditorKind(ItemEditorTab returnTab, boolean hasMode, boolean hasContext) {
        this.returnTab = returnTab;
        this.hasMode = hasMode;
        this.hasContext = hasContext;
    }

    public ItemEditorTab returnTab() {
        return returnTab;
    }

    public boolean hasMode() {
        return hasMode;
    }

    public boolean hasContext() {
        return hasContext;
    }
}
