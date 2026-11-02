package com.scuredweb.common.security;
import java.sql.*;
public final class AccountPolicy {
    private AccountPolicy() {}
    public static boolean reportMaySuspend(String role) { return "MEMBER".equals(role); }
    /** Serialize administrator removals before reading target/counts; Oracle staging concurrency gate required. */
    public static void lockAdministrators(Connection c) throws SQLException {
        try (PreparedStatement s=c.prepareStatement("SELECT USER_ID FROM RW_USER WHERE ROLE_CODE IN ('ADMIN','SUPER_ADMIN') ORDER BY USER_ID FOR UPDATE"); ResultSet r=s.executeQuery()) { while(r.next()) { r.getLong(1); } }
    }
    public static boolean maySuspend(Connection c, Long target, Long actor, String role) throws SQLException {
        if (target.equals(actor)) return false;
        if (reportMaySuspend(role)) return true;
        try (PreparedStatement s=c.prepareStatement("SELECT ROLE_CODE FROM RW_USER WHERE STATUS = 'ACTIVE' AND ROLE_CODE IN ('ADMIN','SUPER_ADMIN')"); ResultSet r=s.executeQuery()) {
            int all=0, supers=0;
            while(r.next()) { all++; if("SUPER_ADMIN".equals(r.getString(1))) supers++; }
            return all>1 && (!"SUPER_ADMIN".equals(role) || supers>1);
        }
    }
}
