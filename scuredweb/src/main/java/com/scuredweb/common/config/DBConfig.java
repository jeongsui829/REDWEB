package com.scuredweb.common.config;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConfig {
    public static final String JNDI_NAME = "java:comp/env/jdbc/RedWebDB";
    private static final String DRIVER_CLASS = "oracle.jdbc.OracleDriver";
    private static volatile DataSource dataSource;
    private static volatile boolean preferDirectJdbc;

    private DBConfig() {
    }

    public static Connection getConnection() throws SQLException {
        if (AppConfig.booleanSetting("redweb.requireJndi", "REDWEB_REQUIRE_JNDI", false)) {
            DataSource source = lookupDataSource();
            if (source == null) throw new SQLException("Required JNDI data source unavailable");
            try { return source.getConnection(); }
            catch (SQLException e) { throw new SQLException("Required JNDI connection unavailable"); }
        }
        DirectJdbcSettings settings = directJdbcSettings();
        if (!preferDirectJdbc) {
            DataSource resolvedDataSource = lookupDataSource();
            if (resolvedDataSource != null) {
                try {
                    Connection connection = resolvedDataSource.getConnection();
                    dataSource = resolvedDataSource;
                    return connection;
                } catch (SQLException exception) {
                    if (settings == null) {
                        throw new SQLException("Configured REDWEB JNDI data source is not usable.", exception);
                    }
                    preferDirectJdbc = true;
                }
            }
        }

        if (settings == null) {
            throw new SQLException("REDWEB database is not configured. Configure "
                    + JNDI_NAME + " or the REDWEB_DB_URL, REDWEB_DB_USERNAME and REDWEB_DB_PASSWORD values.");
        }
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Oracle JDBC driver is not available.", exception);
        }
        return DriverManager.getConnection(settings.url(), settings.username(), settings.password());
    }

    public static void validateConfiguration() {
        try (Connection ignored = getConnection()) {
            // Fail deployment early when the selected external database configuration is unusable.
        } catch (SQLException exception) {
            throw new IllegalStateException("REDWEB database configuration is missing or unusable. Configure "
                    + JNDI_NAME + " or external REDWEB_DB_* values.", exception);
        }
    }

    private static DataSource lookupDataSource() {
        DataSource current = dataSource;
        if (current != null) {
            return current;
        }
        try {
            Object candidate = new InitialContext().lookup(JNDI_NAME);
            if (candidate instanceof DataSource resolved) {
                return resolved;
            }
        } catch (NamingException ignored) {
            // Direct JDBC is an explicit development fallback only.
        }
        return null;
    }

    private static DirectJdbcSettings directJdbcSettings() {
        String url = externalValue("REDWEB_DB_URL", "redweb.db.url");
        String username = externalValue("REDWEB_DB_USERNAME", "redweb.db.username");
        String password = externalValue("REDWEB_DB_PASSWORD", "redweb.db.password");
        if (isBlank(url) || isBlank(username) || isBlank(password)) {
            return null;
        }
        return new DirectJdbcSettings(url.trim(), username.trim(), password);
    }

    private static String externalValue(String environmentName, String propertyName) {
        String propertyValue = System.getProperty(propertyName);
        return isBlank(propertyValue) ? System.getenv(environmentName) : propertyValue;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DirectJdbcSettings(String url, String username, String password) {
    }
}


