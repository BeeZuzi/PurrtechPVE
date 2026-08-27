package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-armor-class resistance/weakness (percent, positive resists, negative
 * weakens) - mirrors {@code mob_damage_profile}, but keyed by one of the 3
 * fixed {@code ArmorClass} values instead of a MythicMobs mob type: applies
 * to every equipped piece tagged with that class, on top of that piece's own
 * {@code item_type_modifier} rows (see {@code EquipmentResolver}).
 */
public final class ArmorClassProfileRepository {

    private final Database database;

    public ArmorClassProfileRepository(Database database) {
        this.database = database;
    }

    public void upsert(String armorClass, String damageTypeKey, double percent) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO armor_class_profile (armor_class, damage_type_key, percent)
                     VALUES (?,?,?)
                     """)) {
            statement.setString(1, armorClass);
            statement.setString(2, damageTypeKey);
            statement.setDouble(3, percent);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save armor class profile for " + armorClass, e);
        }
    }

    public boolean remove(String armorClass, String damageTypeKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM armor_class_profile WHERE armor_class = ? AND damage_type_key = ?
                     """)) {
            statement.setString(1, armorClass);
            statement.setString(2, damageTypeKey);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove armor class profile for " + armorClass, e);
        }
    }

    /** damageTypeKey -> percent, ready to merge straight into a resistance map. */
    public Map<String, Double> findByArmorClass(String armorClass) {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT damage_type_key, percent FROM armor_class_profile WHERE armor_class = ?
                     """)) {
            statement.setString(1, armorClass);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("damage_type_key"), rs.getDouble("percent"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load armor class profile for " + armorClass, e);
        }
        return out;
    }
}
