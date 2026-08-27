package eu.purrtech.purrtechPVE.itemset;

import java.util.UUID;

/**
 * A named collection of item templates whose combined wear grants tiered
 * bonuses (see {@link SetThresholdDamage}/{@link SetThresholdModifier}).
 * Treated as live/global config, not versioned per item stack - see
 * {@code Schema}'s comment on {@code item_sets}.
 */
public record ItemSet(
        UUID id,
        String key,
        String displayName,
        long createdAt,
        long updatedAt
) {
}
