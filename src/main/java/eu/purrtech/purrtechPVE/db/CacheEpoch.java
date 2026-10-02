package eu.purrtech.purrtechPVE.db;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One shared "data changed" counter for every {@link ReadCache}. Any repository write that could
 * affect something a cache holds calls {@link #bump()}; every cache notices the new value on its
 * next read and empties itself. Coarse on purpose: writes here are rare admin edits, reads are
 * every single hit in combat, and one counter also covers writes that reach a cached table by
 * cascade (deleting an item template or set removes rows from tables other repositories cache)
 * without each of those repositories having to know about the others.
 */
final class CacheEpoch {

    private static final AtomicLong EPOCH = new AtomicLong();

    private CacheEpoch() {
    }

    static long current() {
        return EPOCH.get();
    }

    static void bump() {
        EPOCH.incrementAndGet();
    }
}
