package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.itemset.SetThresholdDamage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemSetDamageThresholdRepository {

    private final Database database;

    public ItemSetDamageThresholdRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID setId, SetThresholdDamage threshold) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_set_threshold_damage
                         (set_id, piece_count, damage_type_key, amount, mode)
                     VALUES (?,?,?,?,?)
                     """)) {
            statement.setString(1, setId.toString());
            statement.setInt(2, threshold.pieceCount());
            statement.setString(3, threshold.damageTypeKey());
            statement.setDouble(4, threshold.amount());
            statement.setString(5, threshold.mode().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save damage threshold for set " + setId, e);
        }
    }

    public boolean remove(UUID setId, int pieceCount, String damageTypeKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_set_threshold_damage WHERE set_id = ? AND piece_count = ? AND damage_type_key = ?
                     """)) {
            statement.setString(1, setId.toString());
            statement.setInt(2, pieceCount);
            statement.setString(3, damageTypeKey);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove damage threshold for set " + setId, e);
        }
    }

    public List<SetThresholdDamage> findBySet(UUID setId) {
        List<SetThresholdDamage> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT piece_count, damage_type_key, amount, mode FROM item_set_threshold_damage
                     WHERE set_id = ? ORDER BY piece_count
                     """)) {
            statement.setString(1, setId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new SetThresholdDamage(
                            rs.getInt("piece_count"),
                            rs.getString("damage_type_key"),
                            rs.getDouble("amount"),
                            DamageMode.valueOf(rs.getString("mode"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load damage thresholds for set " + setId, e);
        }
        return out;
    }
}
