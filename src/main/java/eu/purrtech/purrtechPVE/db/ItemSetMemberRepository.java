package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemSetMemberRepository {

    private final Database database;

    public ItemSetMemberRepository(Database database) {
        this.database = database;
    }

    public void add(UUID setId, UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO item_set_members (set_id, template_id) VALUES (?,?)
                     """)) {
            statement.setString(1, setId.toString());
            statement.setString(2, templateId.toString());
            statement.executeUpdate();
            CacheEpoch.bump();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add member to set " + setId, e);
        }
    }

    public boolean remove(UUID setId, UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_set_members WHERE set_id = ? AND template_id = ?
                     """)) {
            statement.setString(1, setId.toString());
            statement.setString(2, templateId.toString());
            boolean removed = statement.executeUpdate() > 0;
            CacheEpoch.bump();
            return removed;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove member from set " + setId, e);
        }
    }

    public List<UUID> findTemplateIdsOfSet(UUID setId) {
        return findIds("SELECT template_id AS id FROM item_set_members WHERE set_id = ?", setId);
    }

    // Asked for every equipped piece on every combat event - see ReadCache. Returned lists are read-only.
    private final ReadCache<UUID, List<UUID>> setIdsByTemplate = new ReadCache<>(2048);

    /** Every set a given template is a member of - used by EquipmentResolver to count worn pieces per set. Read-only. */
    public List<UUID> findSetIdsContainingTemplate(UUID templateId) {
        return setIdsByTemplate.get(templateId,
                () -> List.copyOf(findIds("SELECT set_id AS id FROM item_set_members WHERE template_id = ?", templateId)));
    }

    private List<UUID> findIds(String sql, UUID param) {
        List<UUID> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, param.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(UUID.fromString(rs.getString("id")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query set membership", e);
        }
        return out;
    }
}
