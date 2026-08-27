package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.itemset.ItemSet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ItemSetRepository {

    private final Database database;

    public ItemSetRepository(Database database) {
        this.database = database;
    }

    public void insert(ItemSet set) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO item_sets (id, key, display_name, created_at, updated_at) VALUES (?,?,?,?,?)
                     """)) {
            statement.setString(1, set.id().toString());
            statement.setString(2, set.key());
            statement.setString(3, set.displayName());
            statement.setLong(4, set.createdAt());
            statement.setLong(5, set.updatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert item set " + set.key(), e);
        }
    }

    public Optional<ItemSet> findByKey(String key) {
        return findOne("SELECT * FROM item_sets WHERE key = ?", key);
    }

    public Optional<ItemSet> findById(UUID id) {
        return findOne("SELECT * FROM item_sets WHERE id = ?", id.toString());
    }

    private Optional<ItemSet> findOne(String sql, String param) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, param);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to look up item set", e);
        }
    }

    public List<ItemSet> findAll() {
        List<ItemSet> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM item_sets ORDER BY key");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list item sets", e);
        }
        return out;
    }

    /** Cascades to item_set_members/item_set_threshold_damage/item_set_threshold_modifier via FK ON DELETE CASCADE. */
    public boolean delete(String key) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM item_sets WHERE key = ?")) {
            statement.setString(1, key);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete item set " + key, e);
        }
    }

    private ItemSet map(ResultSet rs) throws SQLException {
        return new ItemSet(
                UUID.fromString(rs.getString("id")),
                rs.getString("key"),
                rs.getString("display_name"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
