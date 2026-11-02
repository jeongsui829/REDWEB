package com.scuredweb.recheck;

import com.scuredweb.comment.dao.CommentDAO;
import com.scuredweb.common.util.DBUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminReplyModerationTest {
    @ParameterizedTest @ValueSource(ints={2,0})
    void adminCanRemoveWholeTreeIncludingDeletedAnchorsWithoutDoubleCounting(int activeRows) throws Exception {
        Connection connection=mock(Connection.class);
        PreparedStatement find=mock(PreparedStatement.class),count=mock(PreparedStatement.class),
                delete=mock(PreparedStatement.class),update=mock(PreparedStatement.class);
        ResultSet found=mock(ResultSet.class),counted=mock(ResultSet.class);
        when(connection.prepareStatement(startsWith("SELECT POST_ID"))).thenReturn(find);
        when(connection.prepareStatement(contains("COUNT(CASE WHEN STATUS = 'ACTIVE'"))).thenReturn(count);
        when(connection.prepareStatement(startsWith("DELETE FROM RW_COMMENT"))).thenReturn(delete);
        when(connection.prepareStatement(startsWith("UPDATE RW_POST"))).thenReturn(update);
        when(find.executeQuery()).thenReturn(found);when(found.next()).thenReturn(true);
        when(found.getLong("POST_ID")).thenReturn(1L);
        when(count.executeQuery()).thenReturn(counted);when(counted.next()).thenReturn(true);
        when(counted.getInt(1)).thenReturn(activeRows);when(delete.executeUpdate()).thenReturn(3);
        try(var db=mockStatic(DBUtil.class)) {
            db.when(DBUtil::getConnection).thenReturn(connection);
            assertTrue(new CommentDAO().deleteComment(10L,99L,true));
            verify(connection).prepareStatement(argThat(sql->sql.startsWith("DELETE FROM RW_COMMENT") && sql.contains("CONNECT BY PRIOR COMMENT_ID = PARENT_COMMENT_ID")));
            verify(update).setInt(1,activeRows); // Already-deleted anchors cannot decrement the count twice.
            verify(connection).commit();verify(connection,never()).rollback();
        }
    }
}
