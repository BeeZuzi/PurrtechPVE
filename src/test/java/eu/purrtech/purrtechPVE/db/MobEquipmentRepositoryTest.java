package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ItemTemplate;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobEquipmentRepositoryTest {

    private Database database;
    private MobEquipmentRepository repository;
    private ItemTemplateRepository templateRepository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new MobEquipmentRepository(database);
        templateRepository = new ItemTemplateRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private UUID insertTemplate(String key) {
        long now = 1_700_000_000_000L;
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), key, "Zbroj kostlivce", Material.IRON_HELMET, null,
                false, List.of("HEAD"), null, 1, 1, now, now, "console");
        templateRepository.insert(template);
        return template.id();
    }

    @Test
    void unknownMobHasNoEquipment() {
        assertTrue(repository.findByMob("SkeletalKnight").isEmpty());
    }

    @Test
    void setThenFindRoundTrips() {
        UUID helmetId = insertTemplate("frozen-helmet");
        repository.set("SkeletalKnight", "HEAD", helmetId);

        Map<String, UUID> equipment = repository.findByMob("SkeletalKnight");
        assertEquals(1, equipment.size());
        assertEquals(helmetId, equipment.get("HEAD"));
    }

    @Test
    void differentSlotsOnSameMobCoexist() {
        UUID helmetId = insertTemplate("frozen-helmet");
        UUID swordId = insertTemplate("frost-sword");
        repository.set("SkeletalKnight", "HEAD", helmetId);
        repository.set("SkeletalKnight", "HAND", swordId);

        Map<String, UUID> equipment = repository.findByMob("SkeletalKnight");
        assertEquals(2, equipment.size());
        assertEquals(helmetId, equipment.get("HEAD"));
        assertEquals(swordId, equipment.get("HAND"));
    }

    @Test
    void differentMobTypesAreIndependent() {
        UUID helmetId = insertTemplate("frozen-helmet");
        UUID otherHelmetId = insertTemplate("fire-helmet");
        repository.set("SkeletalKnight", "HEAD", helmetId);
        repository.set("FireImp", "HEAD", otherHelmetId);

        assertEquals(helmetId, repository.findByMob("SkeletalKnight").get("HEAD"));
        assertEquals(otherHelmetId, repository.findByMob("FireImp").get("HEAD"));
    }

    @Test
    void setOnSameMobAndSlotReplaces() {
        UUID helmetId = insertTemplate("frozen-helmet");
        UUID otherHelmetId = insertTemplate("fire-helmet");
        repository.set("SkeletalKnight", "HEAD", helmetId);
        repository.set("SkeletalKnight", "HEAD", otherHelmetId);

        Map<String, UUID> equipment = repository.findByMob("SkeletalKnight");
        assertEquals(1, equipment.size());
        assertEquals(otherHelmetId, equipment.get("HEAD"));
    }

    @Test
    void removeDeletesJustThatEntry() {
        UUID helmetId = insertTemplate("frozen-helmet");
        UUID swordId = insertTemplate("frost-sword");
        repository.set("SkeletalKnight", "HEAD", helmetId);
        repository.set("SkeletalKnight", "HAND", swordId);

        assertTrue(repository.remove("SkeletalKnight", "HEAD"));
        assertFalse(repository.remove("SkeletalKnight", "HEAD"));

        Map<String, UUID> equipment = repository.findByMob("SkeletalKnight");
        assertEquals(1, equipment.size());
        assertEquals(swordId, equipment.get("HAND"));
    }

    @Test
    void deletingTemplateCascadesToEquipment() {
        UUID helmetId = insertTemplate("frozen-helmet");
        repository.set("SkeletalKnight", "HEAD", helmetId);

        templateRepository.delete("frozen-helmet");

        assertTrue(repository.findByMob("SkeletalKnight").isEmpty());
    }
}
