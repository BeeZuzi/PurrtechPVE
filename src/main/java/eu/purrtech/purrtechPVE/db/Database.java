package eu.purrtech.purrtechPVE.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {

    private final File databaseFile;
    private HikariDataSource dataSource;

    public Database(File dataFolder) {
        this.databaseFile = new File(dataFolder, "purrtechpve.db");
    }

    public void connect() {
        File parent = databaseFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create plugin data folder at " + parent);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite has no real concurrent-writer support; a single pooled connection
        // avoids SQLITE_BUSY errors instead of fighting them with retries.
        config.setMaximumPoolSize(1);
        config.setPoolName("purrtechpve-sqlite");
        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to configure SQLite connection", e);
        }

        Schema.initialize(this);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
