package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.itemset.SetThresholdModifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemSetModifierThresholdRepository {

    private final Database database;

    public ItemSetModifierThresholdRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID setId, SetThresholdModifier threshold) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_set_threshold_modifier
                         (set_id, piece_count, damage_type_key, percent)
                     VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, setId.toString());
            statement.setInt(2, threshold.pieceCount());
            statement.setString(3, threshold.damageTypeKey());
            statement.setDouble(4, threshold.percent());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save modifier threshold for set " + setId, e);
        }
    }

    public boolean remove(UUID setId, int pieceCount, String damageTypeKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_set_threshold_modifier WHERE set_id = ? AND piece_count = ? AND damage_type_key = ?
                     """)) {
            statement.setString(1, setId.toString());
            statement.setInt(2, pieceCount);
            statement.setString(3, damageTypeKey);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove modifier threshold for set " + setId, e);
        }
    }

    public List<SetThresholdModifier> findBySet(UUID setId) {
        List<SetThresholdModifier> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT piece_count, damage_type_key, percent FROM item_set_threshold_modifier
                     WHERE set_id = ? ORDER BY piece_count
                     """)) {
            statement.setString(1, setId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new SetThresholdModifier(
                            rs.getInt("piece_count"),
                            rs.getString("damage_type_key"),
                            rs.getDouble("percent")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load modifier thresholds for set " + setId, e);
        }
        return out;
    }
}
