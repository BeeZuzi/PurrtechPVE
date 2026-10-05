package eu.purrtech.purrtechPVE.gui;

public enum SetEditorTab {
    MEMBERS,
    ADD_MEMBER,
    THRESHOLDS,
    /** Pick the piece count of a threshold that doesn't exist yet. */
    NEW_TIER,
    /** The bonuses (damage + resistances) of one threshold, edited in place. */
    TIER,
    /** Pick a damage type to add to a threshold; {@link SetEditorHolder#pickResist()} says damage or resistance. */
    PICK_TYPE
}
