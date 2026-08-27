package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Which item template (by id) a MythicMobs mob type spawns wearing/holding, keyed by vanilla equipment slot name. */
public final class MobEquipmentRepository {

    private final Database database;

    public MobEquipmentRepository(Database database) {
        this.database = database;
    }

    public void set(String mythicMobInternalName, String slot, UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO mob_equipment (mythic_mob_internal_name, slot, template_id) VALUES (?,?,?)
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, slot);
            statement.setString(3, templateId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save mob equipment for " + mythicMobInternalName, e);
        }
    }

    public boolean remove(String mythicMobInternalName, String slot) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM mob_equipment WHERE mythic_mob_internal_name = ? AND slot = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, slot);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove mob equipment for " + mythicMobInternalName, e);
        }
    }

    /** slot name -> template id. */
    public Map<String, UUID> findByMob(String mythicMobInternalName) {
        Map<String, UUID> out = new LinkedHashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT slot, template_id FROM mob_equipment WHERE mythic_mob_internal_name = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("slot"), UUID.fromString(rs.getString("template_id")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mob equipment for " + mythicMobInternalName, e);
        }
        return out;
    }
}
