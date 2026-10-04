package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ItemUpgrades;
import eu.purrtech.purrtechPVE.item.UpgradeCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-item upgrades (see {@link ItemUpgrades}), keyed by the instance id stamped on the item.
 * {@link #find} sits on the combat path (every equipped piece of an upgraded item is resolved on
 * every hit), so results are cached per instance - including "no upgrades" - and a write only has
 * to drop its own instance's entry.
 */
public final class ItemUpgradeRepository {

    private static final int CACHE_LIMIT = 10_000;

    private final Database database;
    private final Map<UUID, ItemUpgrades> cache = new ConcurrentHashMap<>();

    public ItemUpgradeRepository(Database database) {
        this.database = database;
    }

    /** Sets one entry's upgrade; an amount of 0 or less removes the row instead of storing a useless zero. */
    public void set(UUID instanceId, UpgradeCategory category, String entryKey, double amount) {
        try (Connection connection = database.getConnection()) {
            if (amount <= 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM item_instance_upgrade WHERE instance_id = ? AND category = ? AND entry_key = ?
                        """)) {
                    statement.setString(1, instanceId.toString());
                    statement.setString(2, category.name());
                    statement.setString(3, entryKey);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT OR REPLACE INTO item_instance_upgrade (instance_id, category, entry_key, amount) VALUES (?,?,?,?)
                        """)) {
                    statement.setString(1, instanceId.toString());
                    statement.setString(2, category.name());
                    statement.setString(3, entryKey);
                    statement.setDouble(4, amount);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save item upgrade for " + instanceId, e);
        } finally {
            cache.remove(instanceId);
        }
    }

    public ItemUpgrades find(UUID instanceId) {
        ItemUpgrades cached = cache.get(instanceId);
        if (cached != null) {
            return cached;
        }
        Map<String, Double> damage = new HashMap<>();
        Map<String, Double> resist = new HashMap<>();
        Map<String, Double> effects = new HashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT category, entry_key, amount FROM item_instance_upgrade WHERE instance_id = ?
                     """)) {
            statement.setString(1, instanceId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Double> target = switch (UpgradeCategory.valueOf(rs.getString("category"))) {
                        case DAMAGE -> damage;
                        case RESIST -> resist;
                        case EFFECT -> effects;
                    };
                    target.put(rs.getString("entry_key"), rs.getDouble("amount"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load item upgrades for " + instanceId, e);
        }
        ItemUpgrades loaded = new ItemUpgrades(Map.copyOf(damage), Map.copyOf(resist), Map.copyOf(effects));
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(instanceId, loaded);
        return loaded;
    }
}
