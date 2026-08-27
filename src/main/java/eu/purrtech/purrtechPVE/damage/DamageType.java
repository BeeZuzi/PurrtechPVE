package eu.purrtech.purrtechPVE.damage;

/**
 * @param icon a single Unicode glyph from a block Minecraft's default font has rendered reliably since early
 *             versions (Miscellaneous Symbols U+2600-26FF, Dingbats, or a plain Greek letter/arrow) - used as a
 *             lightweight in-game "icon" for this type (action bar combat feedback, GUI lore) without requiring
 *             a resource pack.
 */
public record DamageType(
        String key,
        String displayName,
        String icon,
        boolean dot,
        int dotPeriodTicks,
        double dotTickPercent
) {

    public static DamageType instant(String key, String displayName, String icon) {
        return new DamageType(key, displayName, icon, false, 0, 0.0);
    }

    public static DamageType dot(String key, String displayName, String icon, int periodTicks, double tickPercent) {
        return new DamageType(key, displayName, icon, true, periodTicks, tickPercent);
    }
}
