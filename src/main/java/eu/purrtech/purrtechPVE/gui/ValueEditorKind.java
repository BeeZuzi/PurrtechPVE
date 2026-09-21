package eu.purrtech.purrtechPVE.gui;

/**
 * Which single numeric stat field {@link ValueEditorMenu} is currently adjusting, and which tab
 * to return to on "Back" - the editor itself is generic (same +/- buttons + visibility toggle
 * layout for all of them), this just says how to read/write the one value each kind represents.
 * {@link ValueEditorHolder#entryId()} carries the rest (which damage type/armor class/attribute+
 * slot within that kind) - {@code null} for the two singleton-per-template kinds (BLEED/CRIT).
 */
public enum ValueEditorKind {
    RESIST(ItemEditorTab.RESIST, false, false, true),
    /** See {@code ArmorPenetration}'s javadoc for what FLAT vs PERCENT_OF_TOTAL actually do differently here. */
    ARMOR_PENETRATION(ItemEditorTab.ARMOR_PENETRATION, true, false, true),
    ATTRIBUTE(ItemEditorTab.BASE, false, false, true),
    /**
     * The only kind with a context toggle (wielded/worn) - {@link ValueEditorHolder#entryId()} is
     * {@code "<damageTypeKey>|<ModifierContext>"}, same shape as {@link #ATTRIBUTE}'s
     * {@code "<attribute>|<slot>"}. A single damage type can have up to two independent
     * contributions (one wielded, one worn) - see {@code ItemEditorMenu}'s DAMAGE tab for how
     * both get their own entry instead of being merged into one.
     */
    DAMAGE(ItemEditorTab.DAMAGE, true, true, true),
    BLEED_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    BLEED_DURATION(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    /** See {@code BleedEffect}'s javadoc for why bleed damage works like a normal {@code DamageContribution} now. */
    BLEED_DAMAGE(ItemEditorTab.SPECIAL_EFFECTS, true, false, true),
    CRIT_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    CRIT_BONUS(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    STUN_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    STUN_DURATION(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    /** See {@code ReflectEffect}'s javadoc - resolved off the wearer's/holder's whole equipped set, unlike bleed/crit/stun above. */
    REFLECT_CHANCE(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    REFLECT_PERCENT(ItemEditorTab.SPECIAL_EFFECTS, false, false, true),
    /**
     * How many flat, vanilla-style armor points ({@code ItemTemplate.armorAmount}) the currently
     * selected {@code ArmorClass} grants - unlike every other kind, there's no {@code visible}
     * flag backing it (it's never shown in this item's own lore, same as {@code
     * armor_class_profile}'s own bonus - see {@code ItemTemplate}'s javadoc), so {@code
     * hasVisibility} is {@code false} here.
     */
    ARMOR_CLASS_AMOUNT(ItemEditorTab.ARMOR_CLASS, false, false, false),
    /**
     * {@code ItemTemplate.stunResistPercent} - same live/unversioned, never-in-lore treatment as
     * {@link #ARMOR_CLASS_AMOUNT}, and likewise independent of which/whether an {@code ArmorClass}
     * is currently selected.
     */
    STUN_RESIST_PERCENT(ItemEditorTab.ARMOR_CLASS, false, false, false),
    /**
     * How many of this template a MythicMobs mob type drops on death - {@link
     * ValueEditorHolder#entryId()} is the mob's internal name. Not shown in this item's own lore
     * (it describes a mob's loot table, not the item itself), so {@code hasVisibility} is {@code
     * false}, same reasoning as {@link #ARMOR_CLASS_AMOUNT}.
     */
    MOB_DROP_AMOUNT(ItemEditorTab.MOBS, false, false, false),
    /** The % chance {@link #MOB_DROP_AMOUNT}'s drop rolls at all on that death - same entryId shape. */
    MOB_DROP_CHANCE(ItemEditorTab.MOBS, false, false, false);

    private final ItemEditorTab returnTab;
    private final boolean hasMode;
    private final boolean hasContext;
    private final boolean hasVisibility;

    ValueEditorKind(ItemEditorTab returnTab, boolean hasMode, boolean hasContext, boolean hasVisibility) {
        this.returnTab = returnTab;
        this.hasMode = hasMode;
        this.hasContext = hasContext;
        this.hasVisibility = hasVisibility;
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

    public boolean hasVisibility() {
        return hasVisibility;
    }
}
