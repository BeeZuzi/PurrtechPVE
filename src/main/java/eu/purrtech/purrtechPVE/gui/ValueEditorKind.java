package eu.purrtech.purrtechPVE.gui;

/**
 * Which single numeric stat field {@link ValueEditorMenu} is currently adjusting, and which tab
 * to return to on "Back" - the editor itself is generic (same +/- buttons + visibility toggle
 * layout for all of them), this just says how to read/write the one value each kind represents.
 * {@link ValueEditorHolder#entryId()} carries the rest (which damage type/armor class/attribute+
 * slot within that kind) - {@code null} for the two singleton-per-template kinds (BLEED/CRIT).
 */
public enum ValueEditorKind {
    RESIST(ItemEditorTab.RESIST),
    ARMOR_PENETRATION(ItemEditorTab.ARMOR_PENETRATION),
    ATTRIBUTE(ItemEditorTab.BASE),
    BLEED_CHANCE(ItemEditorTab.SPECIAL_EFFECTS),
    BLEED_DURATION(ItemEditorTab.SPECIAL_EFFECTS),
    CRIT_CHANCE(ItemEditorTab.SPECIAL_EFFECTS),
    CRIT_BONUS(ItemEditorTab.SPECIAL_EFFECTS);

    private final ItemEditorTab returnTab;

    ValueEditorKind(ItemEditorTab returnTab) {
        this.returnTab = returnTab;
    }

    public ItemEditorTab returnTab() {
        return returnTab;
    }
}
