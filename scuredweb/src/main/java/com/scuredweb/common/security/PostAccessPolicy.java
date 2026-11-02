package com.scuredweb.common.security;
import java.sql.*;
public final class PostAccessPolicy {
    private PostAccessPolicy() {}
    public static boolean lockPublic(Connection c, Long id) throws SQLException {
        if (id == null) return false;
        try (PreparedStatement s = c.prepareStatement("SELECT POST_ID FROM RW_POST WHERE POST_ID = ? AND STATUS = 'PUBLIC' AND BOARD_ID IN (SELECT BOARD_ID FROM RW_BOARD WHERE ACTIVE_YN = 'Y') FOR UPDATE")) {
            s.setLong(1,id); try (ResultSet r=s.executeQuery()) { return r.next(); }
        }
    }
}
