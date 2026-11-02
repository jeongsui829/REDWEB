package com.redweb.common.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.time.LocalDate;

@WebListener
public class AppContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setAttribute("appName", "RedWeb");
        sce.getServletContext().setAttribute("buildStage", "Isolated Validation Environment");
        sce.getServletContext().setAttribute("currentDate", LocalDate.now());
    }
}

