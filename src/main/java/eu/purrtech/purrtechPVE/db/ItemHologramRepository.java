package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Per-item override for the world-drop hologram - a row present means disabled, absent means enabled (the default). */
public final class ItemHologramRepository {

    private final Database database;

    public ItemHologramRepository(Database database) {
        this.database = database;
    }

    public void setDisabled(UUID templateId, boolean disabled) {
        if (disabled) {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT OR IGNORE INTO item_hologram_disabled (template_id) VALUES (?)")) {
                statement.setString(1, templateId.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to disable hologram for template " + templateId, e);
            }
        } else {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM item_hologram_disabled WHERE template_id = ?")) {
                statement.setString(1, templateId.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to enable hologram for template " + templateId, e);
            }
        }
    }

    public boolean isDisabled(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM item_hologram_disabled WHERE template_id = ?")) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check hologram setting for template " + templateId, e);
        }
    }
}
