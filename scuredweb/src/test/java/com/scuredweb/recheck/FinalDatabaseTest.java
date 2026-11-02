package com.scuredweb.recheck;
import com.scuredweb.common.util.DBUtil;
import com.scuredweb.common.security.*;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.auth.dao.AuthDAO;
import com.scuredweb.auth.dto.LoginRequestDTO;
import com.scuredweb.auth.service.AuthService;
import com.scuredweb.board.dao.BoardDAO;
import com.scuredweb.comment.dao.CommentDAO;
import com.scuredweb.social.dao.SocialDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FinalDatabaseTest {
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

    void sql(String sql) throws Exception { try(var s=fixture.createStatement()) { s.execute(sql); } }
    void password(long id,String hash) throws Exception { try(var s=fixture.prepareStatement("UPDATE RW_USER SET PASSWORD_HASH=? WHERE USER_ID=?")) { s.setString(1,hash);s.setLong(2,id);s.executeUpdate(); } }
    @Test void memberWithdrawalRequiresCurrentPassword() throws Exception {
        password(1,PasswordUtil.hash("existing password 123"));
        UserDAO dao=new UserDAO();
        assertFalse(dao.withdrawUser(1L,null)); assertFalse(dao.withdrawUser(1L,"wrong"));
        assertEquals("ACTIVE",value("SELECT STATUS FROM RW_USER WHERE USER_ID=1"));
        assertTrue(dao.withdrawUser(1L,"existing password 123"));
        assertEquals("WITHDRAWN",value("SELECT STATUS FROM RW_USER WHERE USER_ID=1"));
        assertEquals("B",value("SELECT CONTENT FROM RW_COMMENT WHERE COMMENT_ID=20"));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void administratorCannotWithdrawEvenWithCorrectPassword(String role) throws Exception {
        sql("UPDATE RW_USER SET ROLE_CODE='"+role+"' WHERE USER_ID=1");
        password(1,PasswordUtil.hash("existing password 123"));
        assertFalse(new UserDAO().withdrawUser(1L,"existing password 123"));
        assertFalse(new UserDAO().withdrawUser(1L));
        assertEquals("ACTIVE",value("SELECT STATUS FROM RW_USER WHERE USER_ID=1"));
    }
    @Test void securityRecordsSurviveWithdrawal() throws Exception {
        sql("INSERT INTO RW_REPORT (REPORT_ID,REPORTER_ID,TARGET_TYPE,TARGET_ID,REASON_CODE) VALUES (1,1,'USER',2,'TEST')");
        sql("INSERT INTO RW_REPORT (REPORT_ID,REPORTER_ID,TARGET_TYPE,TARGET_ID,REASON_CODE) VALUES (2,2,'POST',1,'TEST')");
        sql("INSERT INTO RW_ADMIN_ACTION_LOG (ACTION_LOG_ID,ADMIN_USER_ID,ACTION_TYPE,TARGET_TYPE) VALUES (1,1,'TEST','USER')");
        assertTrue(new UserDAO().withdrawUser(1L));
        assertEquals("2",value("SELECT COUNT(*) FROM RW_REPORT"));
        assertEquals("1",value("SELECT COUNT(*) FROM RW_ADMIN_ACTION_LOG"));
    }
    @ParameterizedTest @ValueSource(strings={"MEMBER","ADMIN","SUPER_ADMIN"})
    void reportsSuspendOnlyMembersAndAuditTogether(String role) throws Exception {
        sql("UPDATE RW_USER SET ROLE_CODE='"+role+"' WHERE USER_ID=1");
        sql("INSERT INTO RW_REPORT (REPORT_ID,REPORTER_ID,TARGET_TYPE,TARGET_ID,REASON_CODE) VALUES (1,2,'USER',1,'TEST')");
        assertTrue(new SocialDAO().resolveReportAndSuspend(1L,3L,1L,"test",java.time.LocalDateTime.now().plusDays(1)));
        assertEquals(role.equals("MEMBER")?"SUSPENDED":"ACTIVE",value("SELECT STATUS FROM RW_USER WHERE USER_ID=1"));
        assertEquals("RESOLVED",value("SELECT STATUS FROM RW_REPORT WHERE REPORT_ID=1"));
        assertEquals("1",value("SELECT COUNT(*) FROM RW_ADMIN_ACTION_LOG"));
    }
    @Test void reportAuditFailureRollsBackSuspension() throws Exception {
        sql("INSERT INTO RW_REPORT (REPORT_ID,REPORTER_ID,TARGET_TYPE,TARGET_ID,REASON_CODE) VALUES (1,2,'USER',1,'TEST')");
        sql("DROP TABLE RW_ADMIN_ACTION_LOG");
        assertThrows(SQLException.class,()->new SocialDAO().resolveReportAndSuspend(1L,3L,1L,"test",java.time.LocalDateTime.now().plusDays(1)));
        assertEquals("ACTIVE",value("SELECT STATUS FROM RW_USER WHERE USER_ID=1"));
        assertEquals("PENDING",value("SELECT STATUS FROM RW_REPORT WHERE REPORT_ID=1"));
    }
    @Test void passwordChangeAndResetIncrementVersionAndNewLoginWorks() throws Exception {
        String old=PasswordUtil.hash("original password 123");password(1,old);
        assertTrue(new UserDAO().updatePassword(1L,old,PasswordUtil.hash("changed password 123")));
        assertEquals("2",value("SELECT AUTH_VERSION FROM RW_USER WHERE USER_ID=1"));
        new AuthDAO().replacePasswordResetToken(1L,"test-token-digest",java.time.LocalDateTime.now().plusMinutes(5));
        assertTrue(new AuthDAO().resetPasswordWithToken("test-token-digest",PasswordUtil.hash("reset password 123")));
        assertEquals("3",value("SELECT AUTH_VERSION FROM RW_USER WHERE USER_ID=1"));
        assertFalse(new AuthDAO().resetPasswordWithToken("test-token-digest",PasswordUtil.hash("unused password 123")));
        LoginRequestDTO dto=new LoginRequestDTO();dto.setLoginId("private1");dto.setPassword("reset password 123");
        assertEquals(3,new AuthService().login(dto).getAuthVersion());
        dto.setPassword("changed password 123");assertNull(new AuthService().login(dto));
    }
    @Test void emailChangeRequiresPasswordAndClearsVerification() throws Exception {
        password(1,PasswordUtil.hash("original password 123"));sql("UPDATE RW_USER SET EMAIL_VERIFIED_YN='Y' WHERE USER_ID=1");
        UserDAO dao=new UserDAO();
        assertTrue(dao.updateProfile(1L,"nickname1","user1@example.invalid",null));
        assertEquals("Y",value("SELECT EMAIL_VERIFIED_YN FROM RW_USER WHERE USER_ID=1"));
        assertFalse(dao.updateProfile(1L,"nickname1","new@example.invalid",null,"wrong"));
        assertTrue(dao.updateProfile(1L,"nickname1","new@example.invalid",null,"original password 123"));
        assertEquals("N",value("SELECT EMAIL_VERIFIED_YN FROM RW_USER WHERE USER_ID=1"));
        assertNull(new AuthDAO().findActiveUserId("private1","new@example.invalid"));
    }
    @ParameterizedTest @ValueSource(strings={"HIDDEN","DELETED"})
    void nonpublicPostCannotMutate(String status) throws Exception {
        sql("UPDATE RW_POST SET STATUS='"+status+"' WHERE POST_ID=1");
        BoardDAO dao=new BoardDAO();dao.incrementViewCount(1L);
        assertEquals("0",value("SELECT VIEW_COUNT FROM RW_POST WHERE POST_ID=1"));
        assertNull(new CommentDAO().insertComment(1L,2L,null,"blocked"));
        assertFalse(dao.upsertReaction(1L,2L,"LIKE"));
        assertFalse(dao.updatePost(1L,1L,"changed","changed"));
        assertFalse(dao.deletePost(1L,1L));
        assertFalse(new CommentDAO().deleteComment(10L,1L,false));
        assertEquals("0",value("SELECT COUNT(*) FROM RW_POST_REACTION"));
        assertEquals("3",value("SELECT COUNT(*) FROM RW_COMMENT"));
    }
    @Test void missingPostCannotMutate() throws Exception {
        assertNull(new CommentDAO().insertComment(999L,2L,null,"blocked"));
        assertFalse(new BoardDAO().upsertReaction(999L,2L,"LIKE"));
    }
    @Test void publicPostCanCommentReactAndCount() throws Exception {
        BoardDAO dao=new BoardDAO();dao.incrementViewCount(1L);
        assertEquals("1",value("SELECT VIEW_COUNT FROM RW_POST WHERE POST_ID=1"));
        assertNotNull(new CommentDAO().insertComment(1L,2L,null,"allowed"));
        assertTrue(dao.upsertReaction(1L,2L,"LIKE"));
        assertEquals("1",value("SELECT COUNT(*) FROM RW_POST_REACTION"));
    }
    @Test void inactiveBoardDirectLookupListWriteBlocked() throws Exception {
        BoardDAO dao=new BoardDAO();assertNotNull(dao.findBoardByCode("free"));
        sql("UPDATE RW_BOARD SET ACTIVE_YN='N' WHERE BOARD_ID=1");
        assertNull(dao.findBoardByCode("free"));assertTrue(dao.findBoardPosts("free").isEmpty());
        assertNull(dao.insertPost("free","title","body",1L));
    }
    @Test void postAuditFailureRollsBackVisibility() throws Exception {
        sql("DROP TABLE RW_ADMIN_ACTION_LOG");
        assertThrows(SQLException.class,()->new BoardDAO().moderatePost(1L,3L,true));
        assertEquals("PUBLIC",value("SELECT STATUS FROM RW_POST WHERE POST_ID=1"));
    }
    @Test void administratorStatusGuardPreservesLastAdminAndSelf() throws Exception {
        sql("UPDATE RW_USER SET ROLE_CODE='SUPER_ADMIN' WHERE USER_ID=1");
        assertFalse(new UserDAO().updateUserStatusByAdmin(1L,"SUSPENDED",2L));
        assertFalse(new UserDAO().updateUserStatusByAdmin(1L,"SUSPENDED",1L));
        assertFalse(new UserDAO().updateUserStatus(1L,"SUSPENDED"));
    }
    @Test void successfulLoginUpgradesOldHashWithoutRevokingOtherSessions() throws Exception {
        String pass="oldshort";byte[] salt=new byte[16];
        var spec=new javax.crypto.spec.PBEKeySpec(pass.toCharArray(),salt,210000,256);
        byte[] hash=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String old="pbkdf2$210000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(hash);
        password(1,old);LoginRequestDTO dto=new LoginRequestDTO();dto.setLoginId("private1");dto.setPassword(pass);
        assertNotNull(new AuthService().login(dto));
        assertTrue(value("SELECT PASSWORD_HASH FROM RW_USER WHERE USER_ID=1").startsWith("pbkdf2$600000$"));
        assertEquals("1",value("SELECT AUTH_VERSION FROM RW_USER WHERE USER_ID=1"));
        assertFalse(new AuthDAO().upgradePasswordHash(1L,old,PasswordUtil.hash("stale password update")));
    }
    @Test void noticeAuditFailureRollsBackNewPost() throws Exception {
        sql("ALTER SEQUENCE SEQ_RW_POST RESTART WITH 100");sql("DROP TABLE RW_ADMIN_ACTION_LOG");
        assertThrows(SQLException.class,()->new BoardDAO().insertNoticePost("free","notice","body",3L));
        assertEquals("1",value("SELECT COUNT(*) FROM RW_POST"));
    }
    @Test void reportDismissAuditFailureRollsBack() throws Exception {
        sql("INSERT INTO RW_REPORT (REPORT_ID,REPORTER_ID,TARGET_TYPE,TARGET_ID,REASON_CODE) VALUES (1,2,'USER',1,'TEST')");sql("DROP TABLE RW_ADMIN_ACTION_LOG");
        assertThrows(SQLException.class,()->new SocialDAO().updateReportStatus(1L,3L,"DISMISSED","none"));
        assertEquals("PENDING",value("SELECT STATUS FROM RW_REPORT WHERE REPORT_ID=1"));
    }
}
