package com.redweb.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class DBConfig {
    private static final String PROPERTY_PATH = "/properties/jdbc.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream inputStream = DBConfig.class.getResourceAsStream(PROPERTY_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("JDBC properties file not found: " + PROPERTY_PATH);
            }
            PROPERTIES.load(inputStream);
            Class.forName(PROPERTIES.getProperty("db.driver"));
        } catch (IOException | ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private DBConfig() {
    }

    public static String getUrl() {
        return PROPERTIES.getProperty("db.url");
    }

    public static String getUsername() {
        return PROPERTIES.getProperty("db.username");
    }

    public static String getPassword() {
        return PROPERTIES.getProperty("db.password");
    }
}

