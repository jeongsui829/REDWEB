package com.scuredweb.common.util;

import com.scuredweb.common.config.DBConfig;

import java.sql.Connection;
import java.sql.SQLException;

public final class DBUtil {
    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DBConfig.getConnection();
    }
}


