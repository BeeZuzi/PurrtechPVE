package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Which item templates a MythicMobs mob type drops on death, keyed by template id. */
public final class MobDropRepository {

    private final Database database;

    public MobDropRepository(Database database) {
        this.database = database;
    }

    public void set(String mythicMobInternalName, UUID templateId, int amount, double chancePercent) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO mob_drop (mythic_mob_internal_name, template_id, amount, chance_percent) VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, templateId.toString());
            statement.setInt(3, amount);
            statement.setDouble(4, chancePercent);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save mob drop for " + mythicMobInternalName, e);
        }
    }

    public boolean remove(String mythicMobInternalName, UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM mob_drop WHERE mythic_mob_internal_name = ? AND template_id = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, templateId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove mob drop for " + mythicMobInternalName, e);
        }
    }

    public Optional<MobDropEntry> find(String mythicMobInternalName, UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT amount, chance_percent FROM mob_drop WHERE mythic_mob_internal_name = ? AND template_id = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MobDropEntry(rs.getInt("amount"), rs.getDouble("chance_percent")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mob drop for " + mythicMobInternalName, e);
        }
    }

    /** template id -> drop entry, for a given mob (used by the drop-on-death listener). */
    public Map<UUID, MobDropEntry> findByMob(String mythicMobInternalName) {
        Map<UUID, MobDropEntry> out = new LinkedHashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT template_id, amount, chance_percent FROM mob_drop WHERE mythic_mob_internal_name = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(UUID.fromString(rs.getString("template_id")),
                            new MobDropEntry(rs.getInt("amount"), rs.getDouble("chance_percent")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mob drops for " + mythicMobInternalName, e);
        }
        return out;
    }
}
