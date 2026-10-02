package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-MythicMobs-type resistance/weakness (percent, positive resists,
 * negative weakens) - mirrors {@code item_type_modifier} but keyed by the
 * mob's internal MythicMobs name instead of an item template, so a mob's
 * innate resistance doesn't depend on it wearing one of our items.
 */
public final class MobDamageProfileRepository {

    private final Database database;

    public MobDamageProfileRepository(Database database) {
        this.database = database;
    }

    public void upsert(String mythicMobInternalName, String damageTypeKey, double percent) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO mob_damage_profile (mythic_mob_internal_name, damage_type_key, percent)
                     VALUES (?,?,?)
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, damageTypeKey);
            statement.setDouble(3, percent);
            statement.executeUpdate();
            CacheEpoch.bump();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save mob damage profile for " + mythicMobInternalName, e);
        }
    }

    public boolean remove(String mythicMobInternalName, String damageTypeKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM mob_damage_profile WHERE mythic_mob_internal_name = ? AND damage_type_key = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, damageTypeKey);
            boolean removed = statement.executeUpdate() > 0;
            CacheEpoch.bump();
            return removed;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove mob damage profile for " + mythicMobInternalName, e);
        }
    }

    // Read for every MythicMobs defender on every combat event - see ReadCache. Returned maps are read-only.
    private final ReadCache<String, Map<String, Double>> cache = new ReadCache<>(1024);

    /** damageTypeKey -> percent, ready to merge straight into a resistance map. Read-only. */
    public Map<String, Double> findByMob(String mythicMobInternalName) {
        return cache.get(mythicMobInternalName, () -> Collections.unmodifiableMap(loadByMob(mythicMobInternalName)));
    }

    private Map<String, Double> loadByMob(String mythicMobInternalName) {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT damage_type_key, percent FROM mob_damage_profile WHERE mythic_mob_internal_name = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("damage_type_key"), rs.getDouble("percent"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mob damage profile for " + mythicMobInternalName, e);
        }
        return out;
    }
}
