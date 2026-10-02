package eu.purrtech.purrtechPVE.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseWriteBehindTest {

    /** Opens a second Database on the same folder, i.e. reads what is actually on disk. */
    private static Map<String, Double> readFromDisk(File folder, String armorClass) {
        Database reopened = new Database(folder);
        reopened.connect();
        try {
            return new ArmorClassProfileRepository(reopened).findByArmorClass(armorClass);
        } finally {
            reopened.close();
        }
    }

    @Test
    void aWriteIsVisibleToTheNextReadImmediately(@TempDir File folder) {
        Database database = new Database(folder);
        database.connect();
        try {
            ArmorClassProfileRepository repository = new ArmorClassProfileRepository(database);
            repository.upsert("HEAVY", "fire", -40.0);

            // no waiting, no flush - the in-memory copy is what reads see
            assertEquals(-40.0, repository.findByArmorClass("HEAVY").get("fire"));
        } finally {
            database.close();
        }
    }

    @Test
    void writesReachTheFileAfterCloseAndSurviveAReopen(@TempDir File folder) {
        Database database = new Database(folder);
        database.connect();
        ArmorClassProfileRepository repository = new ArmorClassProfileRepository(database);
        repository.upsert("HEAVY", "fire", -40.0);
        repository.upsert("HEAVY", "physical", 20.0);
        repository.upsert("LIGHT", "fire", 5.0);
        repository.remove("HEAVY", "physical");
        database.close();

        Map<String, Double> heavy = readFromDisk(folder, "HEAVY");
        assertEquals(1, heavy.size());
        assertEquals(-40.0, heavy.get("fire"));
        assertEquals(5.0, readFromDisk(folder, "LIGHT").get("fire"));
    }

    @Test
    void aConnectionsWritesAreReplayedInOrderAsOneGroup(@TempDir File folder) throws Exception {
        Database database = new Database(folder);
        database.connect();
        new ArmorClassProfileRepository(database).upsert("MEDIUM", "old", 1.0);

        // same shape as AccessoryRepository.saveAll: delete everything, then insert replacements
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM armor_class_profile WHERE armor_class = ?")) {
                delete.setString(1, "MEDIUM");
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO armor_class_profile (armor_class, damage_type_key, percent) VALUES (?,?,?)")) {
                insert.setString(1, "MEDIUM");
                insert.setString(2, "new");
                insert.setDouble(3, 2.0);
                insert.executeUpdate();
            }
        }
        database.close();

        Map<String, Double> medium = readFromDisk(folder, "MEDIUM");
        assertEquals(1, medium.size());
        assertEquals(2.0, medium.get("new"));
    }

    @Test
    void readsDoNotCreateWriteGroups(@TempDir File folder) {
        Database database = new Database(folder);
        database.connect();
        ArmorClassProfileRepository repository = new ArmorClassProfileRepository(database);
        for (int i = 0; i < 20; i++) {
            assertTrue(repository.findByArmorClass("LIGHT").isEmpty());
        }
        database.close();
        assertTrue(readFromDisk(folder, "LIGHT").isEmpty());
    }
}
