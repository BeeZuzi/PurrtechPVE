package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.itemset.ItemSet;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSetMemberRepositoryTest {

    private Database database;
    private ItemSetMemberRepository repository;
    private UUID setId;
    private UUID templateAId;
    private UUID templateBId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemSetMemberRepository(database);

        ItemSet set = new ItemSet(UUID.randomUUID(), "dragon-set", "Sada draka", 0L, 0L);
        new ItemSetRepository(database).insert(set);
        setId = set.id();

        ItemTemplateRepository templateRepository = new ItemTemplateRepository(database);
        ItemTemplate templateA = new ItemTemplate(UUID.randomUUID(), "helm", "Helma", List.of(), Material.IRON_HELMET,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        ItemTemplate templateB = new ItemTemplate(UUID.randomUUID(), "chest", "Plát", List.of(), Material.IRON_CHESTPLATE,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        templateRepository.insert(templateA);
        templateRepository.insert(templateB);
        templateAId = templateA.id();
        templateBId = templateB.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void addThenFindTemplateIdsOfSet() {
        repository.add(setId, templateAId);
        repository.add(setId, templateBId);

        List<UUID> members = repository.findTemplateIdsOfSet(setId);
        assertEquals(2, members.size());
        assertTrue(members.contains(templateAId));
        assertTrue(members.contains(templateBId));
    }

    @Test
    void addTwiceIsIdempotent() {
        repository.add(setId, templateAId);
        repository.add(setId, templateAId);
        assertEquals(1, repository.findTemplateIdsOfSet(setId).size());
    }

    @Test
    void findSetIdsContainingTemplate() {
        repository.add(setId, templateAId);
        List<UUID> sets = repository.findSetIdsContainingTemplate(templateAId);
        assertEquals(1, sets.size());
        assertEquals(setId, sets.get(0));
        assertTrue(repository.findSetIdsContainingTemplate(templateBId).isEmpty());
    }

    @Test
    void removeDeletesJustThatMembership() {
        repository.add(setId, templateAId);
        repository.add(setId, templateBId);

        assertTrue(repository.remove(setId, templateAId));
        assertFalse(repository.remove(setId, templateAId));

        List<UUID> members = repository.findTemplateIdsOfSet(setId);
        assertEquals(1, members.size());
        assertEquals(templateBId, members.get(0));
    }
}
