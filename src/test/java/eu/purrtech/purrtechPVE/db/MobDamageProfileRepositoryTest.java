package eu.purrtech.purrtechPVE.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDamageProfileRepositoryTest {

    private Database database;
    private MobDamageProfileRepository repository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new MobDamageProfileRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownMobHasEmptyProfile() {
        assertTrue(repository.findByMob("SkeletalKnight").isEmpty());
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert("SkeletalKnight", "frozen", 50.0);
        repository.upsert("SkeletalKnight", "fire", -25.0);

        Map<String, Double> profile = repository.findByMob("SkeletalKnight");
        assertEquals(2, profile.size());
        assertEquals(50.0, profile.get("frozen"));
        assertEquals(-25.0, profile.get("fire"));
    }

    @Test
    void differentMobTypesAreIndependent() {
        repository.upsert("SkeletalKnight", "frozen", 50.0);
        repository.upsert("FireImp", "frozen", -50.0);

        assertEquals(50.0, repository.findByMob("SkeletalKnight").get("frozen"));
        assertEquals(-50.0, repository.findByMob("FireImp").get("frozen"));
    }

    @Test
    void upsertOnSameMobAndTypeReplaces() {
        repository.upsert("SkeletalKnight", "frozen", 50.0);
        repository.upsert("SkeletalKnight", "frozen", 80.0);

        assertEquals(1, repository.findByMob("SkeletalKnight").size());
        assertEquals(80.0, repository.findByMob("SkeletalKnight").get("frozen"));
    }

    @Test
    void removeDeletesJustThatEntry() {
        repository.upsert("SkeletalKnight", "frozen", 50.0);
        repository.upsert("SkeletalKnight", "fire", -25.0);

        assertTrue(repository.remove("SkeletalKnight", "frozen"));
        assertFalse(repository.remove("SkeletalKnight", "frozen"));

        Map<String, Double> profile = repository.findByMob("SkeletalKnight");
        assertEquals(1, profile.size());
        assertEquals(-25.0, profile.get("fire"));
    }
}
