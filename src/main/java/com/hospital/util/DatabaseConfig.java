package com.hospital.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration loader for database URL, schema path, and application settings.
 */
public class DatabaseConfig {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load config.properties, using fallback defaults. Error: " + e.getMessage());
        }
    }

    public static String getDbUrl() {
        return properties.getProperty("db.url", "jdbc:sqlite:hospital.db");
    }

    public static String getSchemaPath() {
        return properties.getProperty("db.schema.path", "schema.sql");
    }

    public static String getLogFile() {
        return properties.getProperty("app.log.file", "hospital.log");
    }
}
