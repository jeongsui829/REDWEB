package com.scuredweb.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

public final class AppConfig {
    private static final String PROPERTY_PATH = "/properties/app.properties";

    private AppConfig() {
    }

    public static boolean secureCookie() {
        boolean secure = Boolean.parseBoolean(System.getProperty("scuredweb.secureCookie",
                System.getProperty("redweb.secureCookie", "false")));
        validateSecureCookie(publicBaseUrl(), secure);
        return secure;
    }

    public static void validateSecureCookie(String publicUrl, boolean secure) {
        if (publicUrl != null && publicUrl.toLowerCase(java.util.Locale.ROOT).startsWith("https://") && !secure) {
            throw new IllegalStateException("HTTPS public URL requires redweb.secureCookie=true (check scuredweb.secureCookie override)");
        }
    }

    public static String publicBaseUrl() {
        return loadPublicBaseUrl();
    }

    /*
     * Full public application base URL, including the context path when the app
     * is deployed under one. Example: https://www.example.com/redweb
     */
    public static boolean publicRegistrationEnabled() {
        String value = System.getProperty("redweb.publicRegistrationEnabled");
        if (value == null) value = System.getenv("REDWEB_PUBLIC_REGISTRATION_ENABLED");
        if (value == null) {
            if (production()) throw new IllegalStateException("HTTPS requires explicit public registration configuration");
            return true;
        }
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalStateException("Invalid public registration configuration");
    }

    public static boolean production() {
        String url = publicBaseUrl();
        return url != null && url.toLowerCase(java.util.Locale.ROOT).startsWith("https://");
    }
    public static boolean booleanSetting(String property, String env, boolean fallback) {
        String value = System.getProperty(property, System.getenv(env));
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalStateException("Invalid boolean setting: " + property);
    }
    public static int positiveSetting(String name, int fallback, int max) {
        String value = System.getProperty("redweb." + name, System.getenv("REDWEB_" + name.replace('.', '_').replace('-', '_').toUpperCase(java.util.Locale.ROOT)));
        try {
            int result = value == null ? fallback : Integer.parseInt(value);
            if (result < 1 || result > max) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) { throw new IllegalStateException("Invalid numeric setting: " + name); }
    }

    private static String loadPublicBaseUrl() {
        String configured = System.getProperty("redweb.publicBaseUrl");
        if (configured == null) configured = System.getenv("REDWEB_PUBLIC_BASE_URL");
        if (configured != null) return sanitizePublicBaseUrl(configured);

        Properties properties = new Properties();
        try (InputStream inputStream = AppConfig.class.getResourceAsStream(PROPERTY_PATH)) {
            if (inputStream == null) {
                return null;
            }
            properties.load(inputStream);
            return sanitizePublicBaseUrl(properties.getProperty("app.publicBaseUrl"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read public URL configuration", exception);
        }
    }

    private static String sanitizePublicBaseUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value;
        if (trimmed.isEmpty() || !trimmed.equals(trimmed.trim()) || !isAllowedPublicBaseUrl(trimmed)) {
            throw new IllegalStateException("Invalid REDWEB_PUBLIC_BASE_URL / redweb.publicBaseUrl / app.publicBaseUrl");
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isAllowedPublicBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) return false;
            if ("https".equalsIgnoreCase(scheme)) {
                return uri.getHost() != null;
            }
            if (!"http".equalsIgnoreCase(scheme)) {
                return false;
            }
            String host = uri.getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host) || "[::1]".equals(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

