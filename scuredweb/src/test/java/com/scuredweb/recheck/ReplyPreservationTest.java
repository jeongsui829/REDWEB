package com.scuredweb.recheck;

import com.scuredweb.comment.dao.CommentDAO;
import com.scuredweb.common.util.DBUtil;
import com.scuredweb.social.dao.SocialDAO;
import com.scuredweb.user.dao.UserDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real JDBC transactions against an isolated H2 Oracle-mode fixture, never the user's Oracle DB. */
class ReplyPreservationTest {
    String url;
    Connection fixture;
    MockedStatic<DBUtil> db;
    @BeforeEach void database() throws Exception {
        url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=Oracle;DB_CLOSE_DELAY=-1";
        fixture=DriverManager.getConnection(url);
        try(var statement=fixture.createStatement()) {
            for(String sql:Files.readString(Path.of("src/main/resources/sql/scuredweb-schema.sql")).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
            // Oracle catalog compatibility for the DAO's FK allowlist and optional-column check.
            statement.execute("""
                    CREATE VIEW USER_CONSTRAINTS AS
                    SELECT T.TABLE_NAME, T.CONSTRAINT_NAME,
                           R.UNIQUE_CONSTRAINT_NAME R_CONSTRAINT_NAME,
                           CASE WHEN T.CONSTRAINT_TYPE = 'FOREIGN KEY' THEN 'R' ELSE 'P' END CONSTRAINT_TYPE
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS T
                    LEFT JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS R ON T.CONSTRAINT_NAME = R.CONSTRAINT_NAME
                    """);
            statement.execute("CREATE VIEW USER_CONS_COLUMNS AS SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE");
            statement.execute("CREATE VIEW USER_TAB_COLUMNS AS SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS");
            for(int i=1;i<=3;i++) statement.executeUpdate("INSERT INTO RW_USER (USER_ID,LOGIN_ID,PASSWORD_HASH,USER_NAME,NICKNAME,EMAIL) VALUES ("+i+",'private"+i+"','fixture','name"+i+"','public"+i+"','user"+i+"@example.invalid')");
            statement.executeUpdate("INSERT INTO RW_BOARD (BOARD_ID,BOARD_CODE,BOARD_NAME) VALUES (1,'free','Free')");
            statement.executeUpdate("INSERT INTO RW_POST (POST_ID,BOARD_ID,USER_ID,TITLE,CONTENT,COMMENT_COUNT) VALUES (1,1,1,'original title','original body',3)");
            statement.executeUpdate("INSERT INTO RW_COMMENT (COMMENT_ID,POST_ID,USER_ID,PARENT_COMMENT_ID,CONTENT) VALUES (10,1,1,NULL,'A')");
            statement.executeUpdate("INSERT INTO RW_COMMENT (COMMENT_ID,POST_ID,USER_ID,PARENT_COMMENT_ID,CONTENT) VALUES (20,1,2,10,'B')");
            statement.executeUpdate("INSERT INTO RW_COMMENT (COMMENT_ID,POST_ID,USER_ID,PARENT_COMMENT_ID,CONTENT) VALUES (30,1,3,20,'C')");
        }
        db=mockStatic(DBUtil.class);
        db.when(DBUtil::getConnection).thenAnswer(i->DriverManager.getConnection(url));
    }
    @AfterEach void close() throws Exception {
        if(db!=null) db.close();
        if(fixture!=null) { try(var s=fixture.createStatement()) { s.execute("SHUTDOWN"); } fixture.close(); }
    }
    String value(String sql) throws Exception {
        try(var s=fixture.createStatement();var rows=s.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getObject(1) instanceof java.math.BigDecimal n ? n.toPlainString() : rows.getString(1);
        }
    }
    void assertTree(long deletedUser) throws Exception {
        assertEquals("3",value("SELECT COUNT(*) FROM RW_COMMENT"));
        for(int i=1;i<=3;i++) {
            assertEquals(String.valueOf(i),value("SELECT USER_ID FROM RW_COMMENT WHERE COMMENT_ID="+(i*10)));
            assertEquals(i==deletedUser?"DELETED":"ACTIVE",value("SELECT STATUS FROM RW_COMMENT WHERE COMMENT_ID="+(i*10)));
            assertEquals(i==deletedUser?"[삭제된 댓글입니다.]":String.valueOf((char)('A'+i-1)),value("SELECT CONTENT FROM RW_COMMENT WHERE COMMENT_ID="+(i*10)));
        }
        assertEquals("10",value("SELECT PARENT_COMMENT_ID FROM RW_COMMENT WHERE COMMENT_ID=20"));
        assertEquals("20",value("SELECT PARENT_COMMENT_ID FROM RW_COMMENT WHERE COMMENT_ID=30"));
        assertEquals("2",value("SELECT COMMENT_COUNT FROM RW_POST WHERE POST_ID=1"));
        var roots=new CommentDAO().findCommentsByPostId(1L);
        assertEquals(1,roots.size());
        assertEquals(20L,roots.getFirst().getChildren().getFirst().getCommentId());
        assertEquals(30L,roots.getFirst().getChildren().getFirst().getChildren().getFirst().getCommentId());
        assertEquals(List.of(20L,30L),roots.getFirst().getDescendants().stream().map(c->c.getCommentId()).toList());
    }
    @ParameterizedTest @ValueSource(longs={1,2})
    void deletingAOrBPreservesForeignReplies(long owner) throws Exception {
        CommentDAO dao=new CommentDAO(); assertTrue(dao.deleteComment(owner*10,owner,false));
        assertTree(owner);
        assertFalse(dao.deleteComment(owner*10,owner,false)); // Counter must not decrease twice.
        assertTree(owner);
    }
    @Test void nonOwnerCannotDeleteComment() throws Exception {
        assertFalse(new CommentDAO().deleteComment(10L,2L,false));
        assertEquals("A",value("SELECT CONTENT FROM RW_COMMENT WHERE COMMENT_ID=10"));
        assertEquals("3",value("SELECT COMMENT_COUNT FROM RW_POST WHERE POST_ID=1"));
    }
    @ParameterizedTest @ValueSource(longs={1,2})
    void withdrawalPreservesRepliesEvenOnWithdrawnAuthorsPost(long owner) throws Exception {
        assertTrue(new UserDAO().withdrawUser(owner));
        assertTree(owner);
        assertEquals("WITHDRAWN",value("SELECT STATUS FROM RW_USER WHERE USER_ID="+owner));
        assertNotEquals("private"+owner,value("SELECT LOGIN_ID FROM RW_USER WHERE USER_ID="+owner));
        assertTrue(value("SELECT PASSWORD_HASH FROM RW_USER WHERE USER_ID="+owner).startsWith("pbkdf2$"));
        if(owner==1) assertEquals("[삭제된 게시글입니다.]",value("SELECT CONTENT FROM RW_POST WHERE POST_ID=1"));
        else assertEquals("original body",value("SELECT CONTENT FROM RW_POST WHERE POST_ID=1"));
        assertFalse(new UserDAO().withdrawUser(owner));
        assertNull(new SocialDAO().findPublicProfileByNickname("public"+owner));
    }
    @Test void publicLookupDoesNotAcceptLoginIdButInternalLookupStillDoes() throws Exception {
        SocialDAO dao=new SocialDAO();
        assertNotNull(dao.findPublicProfileByNickname("public1"));
        assertNotNull(dao.findPublicProfileByNickname("PUBLIC1"));
        assertNull(dao.findPublicProfileByNickname("private1"));
        assertNotNull(dao.findUser("private1"));
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDED","WITHDRAWN","INACTIVE"})
    void publicLookupRejectsInactiveAccounts(String status) throws Exception {
        try(var s=fixture.prepareStatement("UPDATE RW_USER SET STATUS=? WHERE USER_ID=1")) {s.setString(1,status);s.executeUpdate();}
        assertNull(new SocialDAO().findPublicProfileByNickname("public1"));
    }
}
