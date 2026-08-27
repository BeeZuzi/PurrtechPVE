package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class Schema {

    private Schema() {
    }

    static void initialize(Database database) {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS damage_type_definitions (
                        key TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        icon_material TEXT NOT NULL,
                        color TEXT,
                        is_dot INTEGER NOT NULL DEFAULT 0,
                        dot_period_ticks INTEGER,
                        dot_tick_percent REAL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        description TEXT
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_templates (
                        id TEXT PRIMARY KEY,
                        key TEXT NOT NULL UNIQUE,
                        display_name TEXT NOT NULL,
                        base_material TEXT NOT NULL,
                        base_item_snapshot BLOB,
                        custom_model_data INTEGER,
                        is_trinket INTEGER NOT NULL DEFAULT 0,
                        allowed_slots TEXT,
                        armor_class TEXT,
                        version INTEGER NOT NULL DEFAULT 1,
                        synced_version INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        created_by TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_templates_key ON item_templates(key)");
            // item_templates already has production data on servers that ran this plugin before
            // armor_class existed - see the enchantments migration below for why CREATE TABLE IF
            // NOT EXISTS alone isn't enough there.
            addColumnIfMissing(connection, "item_templates", "armor_class", "TEXT");

            // Full computed state at every version a template has ever had - not just the
            // current one. Needed so a stack that was deliberately NOT pushed to circulation
            // (item.version bumped but item.synced_version left behind) can still be caught
            // up to exactly the last version that WAS pushed, rather than jumping straight to
            // whatever the live item_damage_contribution/item_type_modifier rows say now.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_template_snapshot (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        version INTEGER NOT NULL,
                        template_key TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        base_material TEXT NOT NULL,
                        custom_model_data INTEGER,
                        damage_contributions TEXT NOT NULL,
                        type_modifiers TEXT NOT NULL,
                        enchantments TEXT NOT NULL DEFAULT '',
                        armor_penetration TEXT NOT NULL DEFAULT '',
                        bleed_effect TEXT,
                        critical_effect TEXT,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (template_id, version)
                    )
                    """);
            // CREATE TABLE IF NOT EXISTS above is a no-op on a database that already has this
            // table from before `enchantments`/`armor_penetration`/`bleed_effect`/`critical_effect`
            // existed (every server that's already run this plugin) - ALTER TABLE is the only way
            // an already-shipped table picks up a new column. SQLite allows adding a NOT NULL
            // column as long as it has a DEFAULT, which backfills every existing row.
            addColumnIfMissing(connection, "item_template_snapshot", "enchantments", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "item_template_snapshot", "armor_penetration", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "item_template_snapshot", "bleed_effect", "TEXT");
            addColumnIfMissing(connection, "item_template_snapshot", "critical_effect", "TEXT");

            // damage_type_key is NOT a FK to damage_type_definitions: DamageTypeRegistry is
            // still in-memory-only (Fáze 1), that table stays unpopulated until a later
            // phase makes the registry DB-backed. Validity is enforced at the service layer
            // (ItemTemplateService.requireDamageType) against the in-memory registry instead.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_damage_contribution (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        damage_type_key TEXT NOT NULL,
                        amount REAL NOT NULL,
                        mode TEXT NOT NULL,
                        context TEXT NOT NULL,
                        PRIMARY KEY (template_id, damage_type_key, context)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_damage_contribution_template "
                    + "ON item_damage_contribution(template_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_type_modifier (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        damage_type_key TEXT NOT NULL,
                        percent REAL NOT NULL,
                        PRIMARY KEY (template_id, damage_type_key)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_type_modifier_template "
                    + "ON item_type_modifier(template_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_attribute_modifier (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        attribute_key TEXT NOT NULL,
                        amount REAL NOT NULL,
                        operation TEXT NOT NULL,
                        context TEXT NOT NULL,
                        PRIMARY KEY (template_id, attribute_key, context)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_attribute_modifier_template "
                    + "ON item_attribute_modifier(template_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mob_damage_profile (
                        mythic_mob_internal_name TEXT NOT NULL,
                        damage_type_key TEXT NOT NULL,
                        percent REAL NOT NULL,
                        PRIMARY KEY (mythic_mob_internal_name, damage_type_key)
                    )
                    """);

            // Resistance/weakness granted by an armor_class (LIGHT/MEDIUM/HEAVY - see the
            // ArmorClass enum) itself, applied to every equipped piece tagged with that class on
            // top of that piece's own item_type_modifier rows (see EquipmentResolver). Live/
            // global config, not versioned/snapshotted - same treatment as mob_damage_profile
            // above, for the same reason: it's a rule about a whole category, not a stat baked
            // into one specific item.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS armor_class_profile (
                        armor_class TEXT NOT NULL,
                        damage_type_key TEXT NOT NULL,
                        percent REAL NOT NULL,
                        PRIMARY KEY (armor_class, damage_type_key)
                    )
                    """);

            // item_data is a full serialized ItemStack (ItemStack#serializeAsBytes), same
            // approach as vanilla playerdata - a virtual per-player accessory slot isn't tied
            // to any one of our own templates, so it needs to store an arbitrary item, not
            // just a template reference.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_accessory_slots (
                        player_uuid TEXT NOT NULL,
                        slot_name TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        PRIMARY KEY (player_uuid, slot_name)
                    )
                    """);

            // Set definitions are treated as live/global config, like mob_damage_profile and
            // item_templates.allowed_slots - not versioned/snapshotted per item stack, since a
            // set bonus is a rule about "how many pieces are currently worn", not a stat baked
            // into any one item.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_sets (
                        id TEXT PRIMARY KEY,
                        key TEXT NOT NULL UNIQUE,
                        display_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_sets_key ON item_sets(key)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_set_members (
                        set_id TEXT NOT NULL REFERENCES item_sets(id) ON DELETE CASCADE,
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        PRIMARY KEY (set_id, template_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_set_members_template ON item_set_members(template_id)");

            // piece_count is NOT predetermined - the admin adds whichever thresholds they want
            // (1 piece, 2 pieces, 5 pieces, ...), each with its own independent bonus. Thresholds
            // are cumulative at combat time: wearing enough pieces for a higher threshold keeps
            // every lower threshold's bonus active too (see EquipmentResolver).
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_set_threshold_damage (
                        set_id TEXT NOT NULL REFERENCES item_sets(id) ON DELETE CASCADE,
                        piece_count INTEGER NOT NULL,
                        damage_type_key TEXT NOT NULL,
                        amount REAL NOT NULL,
                        mode TEXT NOT NULL,
                        PRIMARY KEY (set_id, piece_count, damage_type_key)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_set_threshold_damage_set ON item_set_threshold_damage(set_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_set_threshold_modifier (
                        set_id TEXT NOT NULL REFERENCES item_sets(id) ON DELETE CASCADE,
                        piece_count INTEGER NOT NULL,
                        damage_type_key TEXT NOT NULL,
                        percent REAL NOT NULL,
                        PRIMARY KEY (set_id, piece_count, damage_type_key)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_set_threshold_modifier_set ON item_set_threshold_modifier(set_id)");

            // Which of our item templates a MythicMobs mob type spawns wearing/holding, per
            // vanilla equipment slot. Applied fresh (live template data) on every spawn by
            // MythicMobEquipmentListener - not tied to any specific mob instance, so nothing
            // here needs versioning/snapshotting either.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mob_equipment (
                        mythic_mob_internal_name TEXT NOT NULL,
                        slot TEXT NOT NULL,
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        PRIMARY KEY (mythic_mob_internal_name, slot)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mob_equipment_template ON mob_equipment(template_id)");

            // A template's applied enchantments (vanilla or otherwise registered in the server's
            // Enchantment registry) - a stat like damage contributions/type modifiers, so it's
            // versioned/snapshotted the same way (see item_template_snapshot.enchantments above).
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_template_enchantment (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        enchantment_key TEXT NOT NULL,
                        level INTEGER NOT NULL,
                        PRIMARY KEY (template_id, enchantment_key)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_template_enchantment_template "
                    + "ON item_template_enchantment(template_id)");

            // A weapon's ability to punch through one of the 3 armor classes - see the
            // ArmorPenetration record's javadoc for exactly what it reduces (the defender's
            // armor_class_profile-sourced resistance only, not their own per-item stats, and
            // never anything persisted - purely a per-hit combat calculation in
            // EquipmentResolver). A stat like damage contributions, so versioned/snapshotted the
            // same way (see item_template_snapshot.armor_penetration above).
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_armor_penetration (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        armor_class TEXT NOT NULL,
                        amount REAL NOT NULL,
                        PRIMARY KEY (template_id, armor_class)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_armor_penetration_template "
                    + "ON item_armor_penetration(template_id)");

            // A weapon's chance to inflict bleeding on a hit + how long it lasts - see the
            // BleedEffect record's javadoc. At most one row per template.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_bleed_effect (
                        template_id TEXT PRIMARY KEY REFERENCES item_templates(id) ON DELETE CASCADE,
                        chance_percent REAL NOT NULL,
                        duration_seconds REAL NOT NULL
                    )
                    """);

            // A weapon's chance to land a critical hit + how much extra damage it deals - see
            // the CriticalEffect record's javadoc. At most one row per template.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_critical_effect (
                        template_id TEXT PRIMARY KEY REFERENCES item_templates(id) ON DELETE CASCADE,
                        chance_percent REAL NOT NULL,
                        bonus_damage_percent REAL NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database schema", e);
        }
    }

    /** Adds {@code column} to {@code table} if it isn't already there - see the item_template_snapshot.enchantments migration above. */
    private static void addColumnIfMissing(Connection connection, String table, String column, String columnDefinition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDefinition);
        }
    }
}
