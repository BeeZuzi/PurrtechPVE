package eu.purrtech.purrtechPVE.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage that keeps the server thread off the disk entirely.
 *
 * <p>The whole database lives in two places. A real file ({@code purrtechpve.db}) is the durable
 * copy; at {@link #connect()} it is migrated by {@link Schema} and then copied into an in-memory
 * SQLite database. Every {@link #getConnection()} after that is a connection to the in-memory
 * copy, so a read is a microsecond-scale in-process query, never disk I/O - and a write takes
 * effect there immediately, so the very next read (an editor re-rendering after a click, the
 * next combat hit) sees it. No repository needed to change: they still run their own SQL.
 *
 * <p>Writes are made durable in the background. The connection handed out records every
 * data-changing statement with its bound parameters, and when the connection is closed the
 * statements it ran are queued as one group onto a single writer thread, which replays them
 * in order, each group in one transaction, against the file. One thread keeps SQLite's single
 * writer happy and keeps the file in the same order as memory; a group stays atomic (a
 * "delete all, insert these" save never half-lands on disk).
 *
 * <p>Consequences worth knowing: a write is acknowledged once it is in memory, so a hard kill
 * (the watchdog, a crash) can lose whatever was still queued - normally milliseconds, only more
 * if the disk is badly stalled; {@link #close()} drains the queue first. Memory use is the size
 * of the database. A statement that fails on disk after succeeding in memory is logged loudly
 * and memory then differs from disk until the next restart.
 */
public final class Database implements AutoCloseable {

    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    private final File databaseFile;
    private final Logger logger;
    private HikariDataSource diskSource;
    private Connection memory;
    private ExecutorService writer;
    private final AtomicInteger pendingGroups = new AtomicInteger();

    public Database(File dataFolder) {
        this(dataFolder, Logger.getLogger(Database.class.getName()));
    }

    public Database(File dataFolder, Logger logger) {
        this.databaseFile = new File(dataFolder, "purrtechpve.db");
        this.logger = logger;
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
        diskSource = new HikariDataSource(config);

        try (Connection connection = diskSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to configure SQLite connection", e);
        }

        // memory is still null here, so this migrates the real file.
        Schema.initialize(this);

        loadIntoMemory();
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PurrtechPVE-DB-Writer");
            thread.setDaemon(false);
            return thread;
        });
    }

    /** Copies the (already migrated) file into a fresh in-memory database, schema and rows. */
    private void loadIntoMemory() {
        try {
            // A raw connection, not a pooled one: an in-memory database disappears the moment its
            // connection closes, and a pool would eventually recycle it.
            memory = DriverManager.getConnection("jdbc:sqlite::memory:");
            try (Statement statement = memory.createStatement()) {
                statement.execute("PRAGMA foreign_keys = OFF");
            }
            try (PreparedStatement attach = memory.prepareStatement("ATTACH DATABASE ? AS disk")) {
                attach.setString(1, databaseFile.getAbsolutePath());
                attach.execute();
            }

            List<String> ddl = new ArrayList<>();
            List<String> tables = new ArrayList<>();
            try (Statement statement = memory.createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT type, name, sql FROM disk.sqlite_master
                         WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'
                         ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, rowid
                         """)) {
                while (rs.next()) {
                    ddl.add(rs.getString("sql"));
                    if ("table".equals(rs.getString("type"))) {
                        tables.add(rs.getString("name"));
                    }
                }
            }
            try (Statement statement = memory.createStatement()) {
                for (String sql : ddl) {
                    statement.execute(sql);
                }
                // The memory tables were created from the file's own CREATE statements, so
                // "SELECT *" lines up column for column (including columns added by migrations).
                for (String table : tables) {
                    String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
                    statement.execute("INSERT INTO main." + quoted + " SELECT * FROM disk." + quoted);
                }
                statement.execute("DETACH DATABASE disk");
                statement.execute("PRAGMA foreign_keys = ON");
            }
            logger.info("Loaded " + tables.size() + " table(s) into memory - the server thread no longer reads or writes the database file.");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load the database into memory", e);
        }
    }

    /**
     * Before {@link #connect()} finishes (the migration step) this is a real file connection; after
     * it, a connection to the in-memory copy whose data-changing statements are queued for the file
     * when it is closed. Always close it (try-with-resources, as every repository already does) -
     * closing is what hands the recorded writes to the writer thread.
     */
    public Connection getConnection() throws SQLException {
        if (memory == null) {
            return diskSource.getConnection();
        }
        RecordingConnection handler = new RecordingConnection();
        return (Connection) Proxy.newProxyInstance(Database.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    logger.severe("Database writer did not finish within " + SHUTDOWN_WAIT_SECONDS + "s - "
                            + pendingGroups.get() + " write group(s) were NOT saved to disk.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.severe("Interrupted while saving the database - " + pendingGroups.get() + " write group(s) may be unsaved.");
            }
        }
        if (memory != null) {
            try {
                memory.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close the in-memory database", e);
            }
        }
        if (diskSource != null) {
            diskSource.close();
        }
    }

    // ---- write-behind ----

    private record RecordedParam(Method method, Object[] args) {
    }

    private record RecordedWrite(String sql, List<RecordedParam> params) {
    }

    private void persist(List<RecordedWrite> group) {
        try (Connection connection = diskSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            connection.setAutoCommit(false);
            try {
                for (RecordedWrite write : group) {
                    try (PreparedStatement statement = connection.prepareStatement(write.sql())) {
                        for (RecordedParam param : write.params()) {
                            param.method().invoke(statement, param.args());
                        }
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to save a write group to the database file - memory and disk now differ until restart. First statement: "
                    + (group.isEmpty() ? "-" : group.get(0).sql()), unwrap(e));
        } finally {
            pendingGroups.decrementAndGet();
        }
    }

    private static boolean isRead(String sql) {
        String s = sql.stripLeading().toLowerCase(Locale.ROOT);
        return s.startsWith("select") || s.startsWith("pragma") || s.startsWith("with") || s.startsWith("explain");
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
    }

    private static Object invokeOn(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object[] copyArgs(Object[] args) {
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof byte[] bytes) {
                copy[i] = bytes.clone();
            }
        }
        return copy;
    }

    private final class RecordingConnection implements InvocationHandler {

        private final List<RecordedWrite> writes = new ArrayList<>();
        private boolean closed;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "close" -> {
                    if (!closed) {
                        closed = true;
                        flush();
                    }
                    return null;
                }
                case "isClosed" -> {
                    return closed;
                }
                case "prepareStatement" -> {
                    Object real = invokeOn(memory, method, args);
                    return wrap(PreparedStatement.class, new RecordingPreparedStatement((PreparedStatement) real, (String) args[0], writes));
                }
                case "createStatement" -> {
                    Object real = invokeOn(memory, method, args);
                    return wrap(Statement.class, new RecordingStatement((Statement) real, writes));
                }
                default -> {
                    return invokeOn(memory, method, args);
                }
            }
        }

        private void flush() {
            if (writes.isEmpty()) {
                return;
            }
            List<RecordedWrite> group = List.copyOf(writes);
            writes.clear();
            pendingGroups.incrementAndGet();
            try {
                writer.execute(() -> persist(group));
            } catch (RuntimeException e) {
                pendingGroups.decrementAndGet();
                logger.log(Level.SEVERE, "Database is shutting down - a write group was not saved to disk.", e);
            }
        }
    }

    private static Object wrap(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(Database.class.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class RecordingPreparedStatement implements InvocationHandler {

        private final PreparedStatement real;
        private final boolean write;
        private final String sql;
        private final List<RecordedWrite> sink;
        private final List<RecordedParam> params = new ArrayList<>();

        RecordingPreparedStatement(PreparedStatement real, String sql, List<RecordedWrite> sink) {
            this.real = real;
            this.sql = sql;
            this.write = !isRead(sql);
            this.sink = sink;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (write) {
                if (method.getDeclaringClass() == PreparedStatement.class && name.startsWith("set")
                        && args != null && args.length >= 1 && args[0] instanceof Integer) {
                    params.add(new RecordedParam(method, copyArgs(args)));
                } else if (name.equals("clearParameters")) {
                    params.clear();
                } else if (name.equals("addBatch") || name.equals("executeBatch") || name.equals("executeLargeBatch")) {
                    // Not used anywhere today; refusing is better than silently not saving a batch.
                    throw new SQLFeatureNotSupportedException("Batches are not supported by Database's write-behind layer");
                }
            }
            Object result = invokeOn(real, method, args);
            if (write && (args == null || args.length == 0)
                    && (name.equals("executeUpdate") || name.equals("execute") || name.equals("executeLargeUpdate"))) {
                sink.add(new RecordedWrite(sql, List.copyOf(params)));
            }
            return result;
        }
    }

    private static final class RecordingStatement implements InvocationHandler {

        private final Statement real;
        private final List<RecordedWrite> sink;

        RecordingStatement(Statement real, List<RecordedWrite> sink) {
            this.real = real;
            this.sink = sink;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            boolean sqlCall = args != null && args.length >= 1 && args[0] instanceof String;
            if (name.equals("addBatch") || name.equals("executeBatch") || name.equals("executeLargeBatch")) {
                throw new SQLFeatureNotSupportedException("Batches are not supported by Database's write-behind layer");
            }
            Object result = invokeOn(real, method, args);
            if (sqlCall && (name.equals("execute") || name.equals("executeUpdate") || name.equals("executeLargeUpdate"))
                    && !isRead((String) args[0])) {
                sink.add(new RecordedWrite((String) args[0], List.of()));
            }
            return result;
        }
    }
}
