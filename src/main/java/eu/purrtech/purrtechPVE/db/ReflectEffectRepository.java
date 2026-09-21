package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ReflectEffect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** A template's {@link ReflectEffect}, at most one row per template (an item either has reflect configured or doesn't). */
public final class ReflectEffectRepository {

    private final Database database;

    public ReflectEffectRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, ReflectEffect effect) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_reflect_effect (template_id, chance_percent, reflect_percent, visible)
                     VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setDouble(2, effect.chancePercent());
            statement.setDouble(3, effect.reflectPercent());
            statement.setBoolean(4, effect.visible());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save reflect effect for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM item_reflect_effect WHERE template_id = ?")) {
            statement.setString(1, templateId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove reflect effect for template " + templateId, e);
        }
    }

    public Optional<ReflectEffect> findByTemplate(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT chance_percent, reflect_percent, visible FROM item_reflect_effect WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ReflectEffect(rs.getDouble("chance_percent"), rs.getDouble("reflect_percent"), rs.getBoolean("visible")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load reflect effect for template " + templateId, e);
        }
    }
}
