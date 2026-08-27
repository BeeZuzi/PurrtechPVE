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

class ArmorClassProfileRepositoryTest {

    private Database database;
    private ArmorClassProfileRepository repository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ArmorClassProfileRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownArmorClassHasEmptyProfile() {
        assertTrue(repository.findByArmorClass("LIGHT").isEmpty());
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert("HEAVY", "physical", 20.0);
        repository.upsert("HEAVY", "fire", -10.0);

        Map<String, Double> profile = repository.findByArmorClass("HEAVY");
        assertEquals(2, profile.size());
        assertEquals(20.0, profile.get("physical"));
        assertEquals(-10.0, profile.get("fire"));
    }

    @Test
    void differentArmorClassesAreIndependent() {
        repository.upsert("LIGHT", "physical", -10.0);
        repository.upsert("HEAVY", "physical", 20.0);

        assertEquals(-10.0, repository.findByArmorClass("LIGHT").get("physical"));
        assertEquals(20.0, repository.findByArmorClass("HEAVY").get("physical"));
    }

    @Test
    void upsertOnSameClassAndTypeReplaces() {
        repository.upsert("MEDIUM", "physical", 10.0);
        repository.upsert("MEDIUM", "physical", 15.0);

        assertEquals(1, repository.findByArmorClass("MEDIUM").size());
        assertEquals(15.0, repository.findByArmorClass("MEDIUM").get("physical"));
    }

    @Test
    void removeDeletesJustThatEntry() {
        repository.upsert("LIGHT", "physical", -10.0);
        repository.upsert("LIGHT", "piercing", 5.0);

        assertTrue(repository.remove("LIGHT", "physical"));
        assertFalse(repository.remove("LIGHT", "physical"));

        Map<String, Double> profile = repository.findByArmorClass("LIGHT");
        assertEquals(1, profile.size());
        assertEquals(5.0, profile.get("piercing"));
    }
}
