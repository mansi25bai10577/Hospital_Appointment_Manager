package com.hospital.util;

import java.io.IOException;
import java.util.logging.*;

/**
 * Utility wrapper around java.util.logging.Logger for standard application logging.
 * Logs key domain actions (bookings, cancellations, errors) to file and console.
 */
public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("HospitalSystem");

    static {
        try {
            LOGGER.setUseParentHandlers(false); // Clean custom formatting

            // File handler
            FileHandler fileHandler = new FileHandler(DatabaseConfig.getLogFile(), true);
            fileHandler.setFormatter(new SimpleFormatter() {
                private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format(format,
                            new java.util.Date(lr.getMillis()),
                            lr.getLevel().getLocalizedName(),
                            lr.getMessage()
                    );
                }
            });
            LOGGER.addHandler(fileHandler);

            // Console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.WARNING); // Show warning/severe in console if needed
            LOGGER.addHandler(consoleHandler);

            LOGGER.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Failed to initialize FileHandler for AppLogger: " + e.getMessage());
        }
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warning(String message) {
        LOGGER.warning(message);
    }

    public static void severe(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }
}
