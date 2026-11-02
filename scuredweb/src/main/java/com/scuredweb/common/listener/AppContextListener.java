package com.scuredweb.common.listener;

import com.scuredweb.common.config.DBConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

import java.time.LocalDate;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class AppContextListener implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(AppContextListener.class.getName());
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        boolean secureCookie = com.scuredweb.common.config.AppConfig.secureCookie();
        sce.getServletContext().setAttribute("publicRegistrationEnabled", com.scuredweb.common.config.AppConfig.publicRegistrationEnabled());
        com.scuredweb.common.security.UploadBudget.validateConfiguration();
        com.scuredweb.common.util.UploadPathUtil.validateConfiguration();
        com.scuredweb.common.config.AppConfig.booleanSetting("redweb.allowLegacyPlaintextPasswords", "REDWEB_ALLOW_LEGACY_PLAINTEXT_PASSWORDS", !com.scuredweb.common.config.AppConfig.production());
        DBConfig.validateConfiguration();
        sce.getServletContext().setAttribute("appName", "REDWEB");
        sce.getServletContext().setAttribute("buildStage", "Isolated Validation Environment");
        sce.getServletContext().setAttribute("currentDate", LocalDate.now());
        sce.getServletContext().setSessionTimeout(30);
        SessionCookieConfig cookieConfig = sce.getServletContext().getSessionCookieConfig();
        cookieConfig.setHttpOnly(true);
        cookieConfig.setSecure(secureCookie);
        cookieConfig.setAttribute("SameSite", "Lax");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ClassLoader applicationClassLoader = getClass().getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() != applicationClassLoader) {
                continue;
            }
            try {
                DriverManager.deregisterDriver(driver);
            } catch (SQLException exception) {
                LOGGER.log(Level.WARNING, "Failed to deregister JDBC driver " + driver.getClass().getName(), exception);
            }
        }
    }
}

