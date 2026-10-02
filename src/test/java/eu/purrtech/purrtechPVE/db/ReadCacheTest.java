package eu.purrtech.purrtechPVE.db;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadCacheTest {

    @Test
    void repeatedReadsLoadOnlyOnce() {
        ReadCache<String, String> cache = new ReadCache<>(10);
        AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            assertEquals("v", cache.get("k", () -> {
                loads.incrementAndGet();
                return "v";
            }));
        }

        assertEquals(1, loads.get());
    }

    @Test
    void aWriteInvalidatesEveryCachedEntry() {
        ReadCache<String, Integer> cache = new ReadCache<>(10);
        AtomicInteger source = new AtomicInteger(1);

        assertEquals(1, cache.get("k", source::get));
        source.set(2);
        assertEquals(1, cache.get("k", source::get), "still cached until something writes");

        CacheEpoch.bump();

        assertEquals(2, cache.get("k", source::get));
    }

    @Test
    void aWriteDuringALoadStopsTheStaleValueBeingStored() {
        ReadCache<String, Integer> cache = new ReadCache<>(10);
        AtomicInteger source = new AtomicInteger(1);

        // the load reads the old value, then a write lands before it returns
        int first = cache.get("k", () -> {
            int old = source.get();
            source.set(2);
            CacheEpoch.bump();
            return old;
        });

        assertEquals(1, first);
        assertEquals(2, cache.get("k", source::get), "the stale 1 must not have been cached");
    }

    @Test
    void exceedingTheSizeCapEmptiesTheCacheInsteadOfGrowingForever() {
        ReadCache<Integer, Integer> cache = new ReadCache<>(2);
        AtomicInteger loads = new AtomicInteger();

        cache.get(1, () -> loads.incrementAndGet());
        cache.get(2, () -> loads.incrementAndGet());
        cache.get(3, () -> loads.incrementAndGet());
        assertEquals(3, loads.get());

        cache.get(3, () -> loads.incrementAndGet());
        assertEquals(3, loads.get(), "entry 3 was stored after the reset, so it is a hit");
        cache.get(1, () -> loads.incrementAndGet());
        assertEquals(4, loads.get(), "entry 1 was dropped by the reset, so it loads again");
    }
}
