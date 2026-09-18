package com.hospital.util;

import com.hospital.exception.DatabaseException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * DatabaseManager is the single gateway between the application and the SQLite file.
 *
 * <p>It handles two responsibilities:</p>
 * <ol>
 *   <li><strong>Connection vending</strong>: Every DAO calls {@link #getConnection()} to
 *       get a fresh JDBC connection. Each connection has foreign-key enforcement turned on
 *       ({@code PRAGMA foreign_keys = ON}) because SQLite disables it by default.</li>
 *
 *   <li><strong>Schema bootstrapping</strong>: On the first call to {@link #initializeDatabase()},
 *       the class reads {@code schema.sql} and executes every CREATE TABLE statement.
 *       Because the schema uses {@code IF NOT EXISTS}, this is safe to run on a database
 *       that already has the tables — it simply becomes a no-op.</li>
 * </ol>
 *
 * <p><strong>Note on connection pooling</strong>: This class opens and closes a new
 * connection per operation (the try-with-resources pattern in each DAO handles closing).
 * For a single-user desktop application this is perfectly acceptable and keeps
 * the code simple. A web server version would want a connection pool instead.</p>
 */
public class DatabaseManager {

    /** The JDBC URL built from the configured file path, e.g. "jdbc:sqlite:hospital.db". */
    private static final String DB_URL = DatabaseConfig.getDbUrl();

    /**
     * Tracks whether the schema has already been applied in this JVM session.
     * Prevents needlessly re-executing schema SQL on every call.
     */
    private static boolean isInitialized = false;

    // Register the SQLite JDBC driver as soon as this class is loaded.
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseManager] SQLite JDBC driver not found on classpath: " + e.getMessage());
        }
    }

    // =========================================================================
    // CONNECTION VENDING
    // =========================================================================

    /**
     * Opens and returns a new SQLite JDBC connection.
     *
     * <p>Every connection has {@code PRAGMA foreign_keys = ON} applied immediately.
     * This is necessary because SQLite silently ignores FK constraints by default —
     * without this, a booking could reference a non-existent doctor ID without error.</p>
     *
     * <p>Callers are expected to close the connection themselves, typically via
     * try-with-resources: {@code try (Connection conn = DatabaseManager.getConnection()) { ... }}</p>
     *
     * @return an open, FK-enabled JDBC connection
     * @throws DatabaseException if the driver fails or the file cannot be opened
     */
    public static Connection getConnection() throws DatabaseException {
        try {
            Connection connection = DriverManager.getConnection(DB_URL);

            // Without this pragma, SQLite ignores all FOREIGN KEY constraints.
            // We enable it on every connection to ensure referential integrity is always enforced.
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON;");
            }

            return connection;

        } catch (SQLException e) {
            AppLogger.severe("Failed to open a database connection to: " + DB_URL, e);
            throw new DatabaseException("Unable to connect to the database. " +
                    "Check that 'hospital.db' is accessible. Details: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // SCHEMA BOOTSTRAP
    // =========================================================================

    /**
     * Creates all database tables if they do not already exist.
     *
     * <p>This method reads {@code schema.sql} (first from the file system, then from
     * the classpath as a fallback) and executes each SQL statement within it.
     * All statements use {@code CREATE TABLE IF NOT EXISTS}, so calling this method
     * on an already-initialized database is completely harmless.</p>
     *
     * <p>A simple flag ({@link #isInitialized}) prevents re-running the schema on
     * every application action once it has successfully completed.</p>
     *
     * @throws DatabaseException if the schema file cannot be read or any SQL fails
     */
    public static synchronized void initializeDatabase() throws DatabaseException {
        String schemaFilePath = DatabaseConfig.getSchemaPath();
        String sqlScript      = loadSchemaScript(schemaFilePath);

        if (sqlScript == null || sqlScript.trim().isEmpty()) {
            // Not a fatal error — the tables might have been created by a previous run.
            AppLogger.warning("Schema script not found at '" + schemaFilePath +
                    "'. Skipping schema initialization (tables may already exist).");
            isInitialized = true;
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt  = conn.createStatement()) {

            // Strip comment lines before parsing — a semicolon inside a comment
            // would confuse the simple split-on-semicolon approach.
            StringBuilder cleanScript = new StringBuilder();
            for (String line : sqlScript.split("\n")) {
                if (!line.trim().startsWith("--")) {        // skip SQL comment lines
                    cleanScript.append(line).append("\n");
                }
            }

            // Split on semicolons to get individual SQL statements and execute each one.
            for (String statement : cleanScript.toString().split(";")) {
                String sql = statement.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }

            isInitialized = true;
            AppLogger.info("Database schema initialized successfully from '" + schemaFilePath + "'.");

        } catch (SQLException e) {
            AppLogger.severe("Failed to execute database schema SQL", e);
            throw new DatabaseException(
                    "Error running schema SQL initialization: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // PRIVATE — SCHEMA FILE LOADER
    // =========================================================================

    /**
     * Attempts to read the SQL schema file from two locations in order:
     * <ol>
     *   <li>The file system at the given path (useful when running from the project root).</li>
     *   <li>The Java classpath (useful when running from a JAR where the schema is bundled).</li>
     * </ol>
     *
     * @param schemaPath relative or absolute path to the SQL schema file
     * @return the file contents as a single string, or {@code null} if not found
     */
    private static String loadSchemaScript(String schemaPath) {
        // Option 1 — try to read from the file system (works when run via mvn exec:java).
        File schemaFile = new File(schemaPath);
        if (schemaFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(schemaFile))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                AppLogger.warning("Could not read schema file from disk: " + e.getMessage());
            }
        }

        // Option 2 — fall back to the classpath (works when the schema is inside a JAR).
        try (InputStream resourceStream = DatabaseManager.class
                .getClassLoader()
                .getResourceAsStream(schemaPath)) {

            if (resourceStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            }
        } catch (Exception e) {
            AppLogger.warning("Could not read schema file from classpath: " + e.getMessage());
        }

        return null; // Schema not found in either location.
    }
}
