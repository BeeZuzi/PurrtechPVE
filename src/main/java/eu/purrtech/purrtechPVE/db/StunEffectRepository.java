package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.StunEffect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** A template's {@link StunEffect}, at most one row per template (a weapon either has stun configured or doesn't). */
public final class StunEffectRepository {

    private final Database database;

    public StunEffectRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, StunEffect effect) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_stun_effect (template_id, chance_percent, duration_seconds, visible)
                     VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setDouble(2, effect.chancePercent());
            statement.setDouble(3, effect.durationSeconds());
            statement.setBoolean(4, effect.visible());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save stun effect for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM item_stun_effect WHERE template_id = ?")) {
            statement.setString(1, templateId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove stun effect for template " + templateId, e);
        }
    }

    public Optional<StunEffect> findByTemplate(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT chance_percent, duration_seconds, visible FROM item_stun_effect WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StunEffect(rs.getDouble("chance_percent"), rs.getDouble("duration_seconds"), rs.getBoolean("visible")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load stun effect for template " + templateId, e);
        }
    }
}
