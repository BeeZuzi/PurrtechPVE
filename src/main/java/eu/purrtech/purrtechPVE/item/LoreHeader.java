package eu.purrtech.purrtechPVE.item;

/**
 * The 5 auto-generated section headers {@link ItemRenderer#buildLore} can print above a
 * category's stat lines ("Damage on hit:", "Resistances / weaknesses:", ...) - individually
 * hideable per template via {@link ItemTemplate#hiddenHeaders()}, same idea as a single stat
 * entry's {@code visible} flag but for the header line itself rather than one entry under it.
 * {@link #key} is what's actually persisted (comma-joined, see {@code ItemTemplateRepository}),
 * so it must stay stable even if a header's lang key or in-game wording changes.
 */
public enum LoreHeader {
    DAMAGE("damage"),
    PASSIVE("passive"),
    RESIST("resist"),
    PENETRATION("penetration"),
    ATTRIBUTES("attributes");

    private final String key;

    LoreHeader(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static LoreHeader fromKey(String key) {
        for (LoreHeader header : values()) {
            if (header.key.equals(key)) {
                return header;
            }
        }
        throw new IllegalArgumentException("Unknown lore header key: " + key);
    }
}
