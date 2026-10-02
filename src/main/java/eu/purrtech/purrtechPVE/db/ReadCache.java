package eu.purrtech.purrtechPVE.db;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Read-through cache for the repositories hit on every combat event. {@code CombatDamageListener}
 * resolves a hit through a dozen {@code EquipmentResolver} methods, each of which used to re-read
 * the template, snapshot and accessory rows for every equipped slot straight from SQLite on the
 * server thread - a few hundred queries per hit, so a skill that damages a player every tick (a
 * MythicMobs flamethrower) starved the main thread until the watchdog killed the server.
 *
 * <p>Invalidated wholesale through {@link CacheEpoch}, never entry by entry. A value is only
 * stored if no write happened while it was being loaded, so a read racing a write can't leave a
 * stale entry behind. {@code null} is never a cacheable value (use {@code Optional}/empty
 * collections); the size cap just empties the cache rather than evicting individual entries,
 * which keeps this free of any ordering bookkeeping.
 */
final class ReadCache<K, V> {

    private final ConcurrentHashMap<K, V> entries = new ConcurrentHashMap<>();
    private final int maxEntries;
    private volatile long epoch = CacheEpoch.current();

    ReadCache(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    V get(K key, Supplier<V> loader) {
        long startEpoch = CacheEpoch.current();
        syncEpoch(startEpoch);
        V cached = entries.get(key);
        if (cached != null) {
            return cached;
        }
        V loaded = loader.get();
        if (loaded != null && CacheEpoch.current() == startEpoch) {
            if (entries.size() >= maxEntries) {
                entries.clear();
            }
            entries.put(key, loaded);
        }
        return loaded;
    }

    private void syncEpoch(long now) {
        if (now != epoch) {
            synchronized (this) {
                if (now != epoch) {
                    entries.clear();
                    epoch = now;
                }
            }
        }
    }
}
