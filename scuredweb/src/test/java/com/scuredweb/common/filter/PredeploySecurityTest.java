package com.scuredweb.common.filter;

import com.scuredweb.auth.controller.AuthController;
import com.scuredweb.auth.dao.AuthDAO;
import com.scuredweb.auth.dto.LoginRequestDTO;
import com.scuredweb.auth.service.AuthService;
import com.scuredweb.board.controller.BoardController;
import com.scuredweb.board.service.BoardService;
import com.scuredweb.comment.dao.CommentDAO;
import com.scuredweb.comment.service.CommentService;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.security.*;
import com.scuredweb.common.util.*;
import com.scuredweb.file.controller.*;
import com.scuredweb.file.dto.AttachmentDTO;
import com.scuredweb.social.controller.MessageController;
import com.scuredweb.social.dao.SocialDAO;
import com.scuredweb.social.dto.PublicProfileDTO;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.controller.UserController;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.user.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PredeploySecurityTest {
    HttpServletRequest request; HttpServletResponse response; HttpSession session;
    @BeforeEach void exchange() {
        request=mock(HttpServletRequest.class); response=mock(HttpServletResponse.class); session=mock(HttpSession.class);
        when(request.getSession()).thenReturn(session); when(request.getSession(false)).thenReturn(session);
        when(request.getSession(true)).thenReturn(session); when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn(UUID.randomUUID().toString());
    }
    static UserDTO user(String role, String status) {
        UserDTO u=new UserDTO(); u.setUserId(1L); u.setLoginId("test"); u.setNickname("test");
        u.setRoleCode(role); u.setStatus(status); u.setPasswordHash("password123"); return u;
    }
    static Object call(Object target,String method,Class<?>[] types,Object... args) throws Exception {
        Method m=target.getClass().getDeclaredMethod(method,types); m.setAccessible(true);
        try { return m.invoke(target,args); } catch(InvocationTargetException e) {
            if(e.getCause() instanceof Exception ex) throw ex; throw e;
        }
    }
    void servlet(Object target,String method) throws Exception {
        call(target,method,new Class<?>[]{HttpServletRequest.class,HttpServletResponse.class},request,response);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void stepUpRequiredAndFreshRoleAllowed(String role) throws Exception {
        UserDTO u=user(role,"ACTIVE"); UserDAO dao=mock(UserDAO.class); when(dao.findById(1L)).thenReturn(u);
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(u); when(request.getServletPath()).thenReturn("/admin/posts");
        FilterChain chain=mock(FilterChain.class);
        new AdminFilter(dao).doFilter(request,response,chain); verifyNoInteractions(chain);
        verify(response).sendRedirect(startsWith("/admin/verify?"));
        when(session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED)).thenReturn(true);
        when(session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT)).thenReturn(System.currentTimeMillis());
        new AdminFilter(dao).doFilter(request,response,chain); verify(chain).doFilter(request,response);
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDED","WITHDRAWN"})
    void inactiveAdminDenied(String status) throws Exception {
        UserDAO dao=mock(UserDAO.class); when(dao.findById(1L)).thenReturn(user("ADMIN",status));
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("ADMIN","ACTIVE"));
        FilterChain chain=mock(FilterChain.class); new AdminFilter(dao).doFilter(request,response,chain);
        verify(session).invalidate(); verifyNoInteractions(chain);
    }
    @Test void memberCannotBecomeAdmin() throws Exception {
        UserDAO dao=mock(UserDAO.class); when(dao.findById(1L)).thenReturn(user("USER","ACTIVE"));
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("ADMIN","ACTIVE"));
        FilterChain chain=mock(FilterChain.class); new AdminFilter(dao).doFilter(request,response,chain);
        verify(response).sendError(403); verifyNoInteractions(chain);
    }
    @Test void ttlRejectsExpiredAndFutureValues() {
        when(session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED)).thenReturn(true);
        for(long value:new long[]{System.currentTimeMillis()-600_001L,System.currentTimeMillis()+60_000L}) {
            when(session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT)).thenReturn(value);
            assertFalse(AuthorizationUtil.hasFreshVerification(session));
        }
    }
    @ParameterizedTest @ValueSource(strings={"/messages/read","/notifications/read","/admin/comments/delete","/board/delete"})
    void csrfMissingRejectedAndValidAccepted(String path) throws Exception {
        when(request.getServletPath()).thenReturn(path); when(request.getMethod()).thenReturn("POST");
        FilterChain chain=mock(FilterChain.class); new CsrfFilter().doFilter(request,response,chain);
        verify(response).sendError(403); verifyNoInteractions(chain);
        when(session.getAttribute(CsrfUtil.SESSION_ATTRIBUTE)).thenReturn("token"); when(request.getParameter("_csrf")).thenReturn("token");
        new CsrfFilter().doFilter(request,response,chain); verify(chain).doFilter(request,response);
    }
    @Test void normalCommentDeleteNeverUsesAdminBranch() throws Exception {
        try(var dao=mockConstruction(CommentDAO.class)) {
            assertFalse(new CommentService().deleteComment(8L,user("ADMIN","ACTIVE")));
            verify(dao.constructed().getFirst()).deleteComment(8L,1L,false);
        }
    }
    @Test void normalBoardDeleteNeverUsesAdminBranch() throws Exception {
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("SUPER_ADMIN","ACTIVE"));
        when(request.getParameter("id")).thenReturn("8"); when(request.getServletPath()).thenReturn("/board/delete");
        try(var services=mockConstruction(BoardService.class)) {
            servlet(new BoardController(),"doPost"); BoardService service=services.constructed().getFirst();
            verify(service).deletePost(8L,1L); verify(service,never()).deletePostAsAdmin(anyLong());
        }
    }
    @Test void boardEditRejectsNonOwnerBeforeFileWrite() throws Exception {
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("ADMIN","ACTIVE"));
        when(request.getServletPath()).thenReturn("/board/edit"); when(request.getParameter("id")).thenReturn("8");
        when(request.getParameter("title")).thenReturn("title"); when(request.getParameter("content")).thenReturn("content");
        when(request.getParts()).thenReturn(List.of());
        try(var services=mockConstruction(BoardService.class,(mock,context)-> {
            var post=new com.scuredweb.board.dto.PostDTO(); post.setUserId(2L); when(mock.getPostDetail(8L)).thenReturn(post);
        })) {
            servlet(new BoardController(),"doPost"); verify(response).sendError(403);
            verify(services.constructed().getFirst(),never()).updatePost(any(),any(),any(),any());
        }
    }
    @Test void privateAttachmentRequiresOwnerOrFreshAdmin() throws Exception {
        AttachmentDTO file=new AttachmentDTO(); file.setPostStatus("HIDDEN"); file.setUploaderId(2L);
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("ADMIN","ACTIVE"));
        for(Object controller:List.of(new FileDownloadController(),new FileImageController())) {
            String method=controller instanceof FileDownloadController ? "canDownload":"canAccessAttachment";
            assertEquals(false,call(controller,method,new Class<?>[]{HttpServletRequest.class,AttachmentDTO.class},request,file));
            file.setUploaderId(1L);
            assertEquals(true,call(controller,method,new Class<?>[]{HttpServletRequest.class,AttachmentDTO.class},request,file));
            file.setUploaderId(2L);
        }
    }
    @Test void successfulLoginRotatesSessionAndResetsStepUp() throws Exception {
        when(request.getServletPath()).thenReturn("/login"); when(request.getParameter("loginId")).thenReturn("test");
        when(request.getParameter("password")).thenReturn("password123");
        try(var auth=mockConstruction(AuthService.class,(mock,ctx)->when(mock.login(any(),any(),any())).thenReturn(user("USER","ACTIVE")));
            var users=mockConstruction(UserDAO.class,(mock,ctx)->when(mock.findById(1L)).thenReturn(user("USER","ACTIVE")))) {
            servlet(new AuthController(),"doPost"); verify(request).changeSessionId();
            verify(session).removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED);
        }
    }
    @Test void failedLoginDoesNotRotateOrInstallUser() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        try(var auth=mockConstruction(AuthService.class)) {
            servlet(new AuthController(),"doPost"); verify(request,never()).changeSessionId();
            verify(session,never()).setAttribute(eq(SessionKey.LOGIN_USER),any());
        }
    }
    @Test void legacyLoginMigratesHash() throws Exception {
        LoginRequestDTO dto=new LoginRequestDTO(); dto.setLoginId("test"); dto.setPassword("password123");
        try(var daos=mockConstruction(AuthDAO.class,(mock,ctx)->when(mock.findUserForLogin(any())).thenReturn(user("USER","ACTIVE")))) {
            assertNotNull(new AuthService().login(dto)); verify(daos.constructed().getFirst()).updatePasswordHash(eq(1L),startsWith("pbkdf2$"));
        }
    }
    @Test void messageGetDoesNotMarkRead() throws Exception {
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("USER","ACTIVE"));
        when(request.getServletPath()).thenReturn("/messages"); when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
        try(var social=mockStatic(SocialService.class)) {
            social.when(()->SocialService.getMessages(1L)).thenReturn(List.of());
            servlet(new MessageController(),"doGet"); social.verify(()->SocialService.markMessagesRead(anyLong()),never());
        }
    }
    @Test void notificationsGetDoesNotMarkRead() throws Exception {
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("USER","ACTIVE"));
        when(request.getServletPath()).thenReturn("/mypage/notifications"); when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
        try(var users=mockConstruction(UserService.class); var social=mockStatic(SocialService.class)) {
            servlet(new UserController(),"doGet"); social.verify(()->SocialService.markNotificationsRead(anyLong()),never());
        }
    }
    @Test void publicProfileHasNoPrivateFieldsAndActivePredicate() throws Exception {
        assertEquals(Set.of("userId","nickname","bio","profileImagePath"),new HashSet<>(Arrays.stream(PublicProfileDTO.class.getDeclaredFields()).map(Field::getName).toList()));
        Connection connection=mock(Connection.class); PreparedStatement statement=mock(PreparedStatement.class); ResultSet rows=mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement); when(statement.executeQuery()).thenReturn(rows);
        try(var db=mockStatic(DBUtil.class)) {
            db.when(DBUtil::getConnection).thenReturn(connection); assertNull(new SocialDAO().findUser("hidden-user"));
            verify(connection).prepareStatement(argThat(sql->sql.contains("STATUS = 'ACTIVE'") && !sql.contains("PASSWORD_HASH") && !sql.contains("EMAIL") && !sql.contains("PHONE")));
        }
        String jsp=Files.readString(Path.of("src/main/webapp/WEB-INF/views/user/profile.jsp")); assertFalse(jsp.contains("getEmail"));
    }
    @Test void passwordBoundaryAndLegacyCompatibility() {
        assertTrue(PasswordUtil.verifyPassword("legacy", "legacy"));
        String max="x".repeat(128); assertTrue(PasswordUtil.verifyPassword(max,PasswordUtil.hash(max)));
        assertFalse(PasswordUtil.verifyPassword(max+"x",max+"x"));
        assertFalse(PasswordUtil.verifyPassword("wrong",PasswordUtil.hash("correct")));
        assertFalse(PasswordUtil.verifyPassword(null,"legacy"));
    }
    @Test void uploadQuotaBoundaryAndOverflow() throws Exception {
        UploadBudget.checkCapacity(90,10,100);
        assertThrows(IOException.class,()->UploadBudget.checkCapacity(90,11,100));
        assertThrows(IOException.class,()->UploadBudget.checkCapacity(Long.MAX_VALUE,1,Long.MAX_VALUE));
    }
    @Test void lowDiskSpaceRejected(@TempDir Path root) {
        System.setProperty("redweb.upload.minFreeBytes",Long.toString(Long.MAX_VALUE));
        try { assertThrows(IOException.class,()->UploadBudget.checkSpace(root,1)); }
        finally { System.clearProperty("redweb.upload.minFreeBytes"); }
    }
    @Test void fileValidationAndTraversal(@TempDir Path root) throws Exception {
        Part p=mock(Part.class); when(p.getSize()).thenReturn(8L); when(p.getSubmittedFileName()).thenReturn("a.png");
        when(p.getContentType()).thenReturn("image/png"); when(p.getInputStream()).thenAnswer(i->new ByteArrayInputStream(new byte[]{(byte)0x89,0x50,0x4e,0x47,13,10,26,10}));
        assertTrue(FileValidationUtil.isAllowedAttachment(p));
        when(p.getSubmittedFileName()).thenReturn("a.jsp"); assertFalse(FileValidationUtil.isAllowedAttachment(p));
        when(p.getSubmittedFileName()).thenReturn("a.png"); when(p.getContentType()).thenReturn("text/html"); assertFalse(FileValidationUtil.isAllowedAttachment(p));
        assertThrows(IOException.class,()->FileValidationUtil.safeResolve(root,"../escape"));
    }
    @Test void requestRateLimitDoesNotResetLoginLimiter() {
        String ip=UUID.randomUUID().toString();
        for(int i=0;i<5;i++) assertTrue(RateLimitService.tryConsume("register",ip,5));
        assertFalse(RateLimitService.tryConsume("register",ip,5));
        assertFalse(RateLimitService.isBlocked("login",ip,"test"));
    }

    @Test void httpsCookieConfigurationFailsClosed() {
        assertThrows(IllegalStateException.class,()->com.scuredweb.common.config.AppConfig.validateSecureCookie("https://example.com",false));
        assertDoesNotThrow(()->com.scuredweb.common.config.AppConfig.validateSecureCookie("https://example.com",true));
        assertDoesNotThrow(()->com.scuredweb.common.config.AppConfig.validateSecureCookie("http://localhost",false));
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDED","WITHDRAWN"})
    void authFilterRejectsInactiveAccounts(String status) throws Exception {
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("USER","ACTIVE"));
        try(var dao=mockConstruction(UserDAO.class,(mock,ctx)->when(mock.findById(1L)).thenReturn(user("USER",status)))) {
            FilterChain chain=mock(FilterChain.class); new AuthFilter().doFilter(request,response,chain);
            verify(session).invalidate(); verifyNoInteractions(chain);
        }
    }
    @Test void wrongPasswordAndInactiveLoginFail() throws Exception {
        LoginRequestDTO dto=new LoginRequestDTO(); dto.setLoginId("test"); dto.setPassword("wrong");
        try(var daos=mockConstruction(AuthDAO.class,(mock,ctx)->when(mock.findUserForLogin(any())).thenReturn(user("USER","ACTIVE")))) {
            assertNull(new AuthService().login(dto)); verify(daos.constructed().getFirst(),never()).recordSuccessfulLogin(anyLong(),any(),any());
        }
        dto.setPassword("password123");
        try(var daos=mockConstruction(AuthDAO.class,(mock,ctx)->when(mock.findUserForLogin(any())).thenReturn(user("USER","SUSPENDED")))) {
            assertNull(new AuthService().login(dto));
        }
    }
    @Test void adminStepUpRotatesSession() throws Exception {
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("ADMIN","ACTIVE"));
        when(request.getParameter("password")).thenReturn("password123");
        servlet(new com.scuredweb.admin.controller.AdminVerificationController(),"doPost");
        verify(request).changeSessionId(); verify(session).setAttribute(SessionKey.ADMIN_ACCESS_VERIFIED,true);
        verify(session).setAttribute(eq(SessionKey.ADMIN_ACCESS_VERIFIED_AT),anyLong());
    }
    @Test void secondUploadFailureCleansFirstFile(@TempDir Path root) throws Exception {
        System.setProperty("redweb.upload.root",root.toString());
        System.setProperty("redweb.upload.minFreeBytes","0");
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("USER","ACTIVE"));
        when(request.getServletPath()).thenReturn("/board/write"); when(request.getParameter("boardCode")).thenReturn("free");
        when(request.getParameter("title")).thenReturn("title"); when(request.getParameter("content")).thenReturn("content");
        Part good=mock(Part.class); when(good.getName()).thenReturn("attachments"); when(good.getSubmittedFileName()).thenReturn("good.txt");
        when(good.getContentType()).thenReturn("text/plain"); when(good.getSize()).thenReturn(4L);
        when(good.getInputStream()).thenAnswer(i->new ByteArrayInputStream("test".getBytes()));
        Part bad=mock(Part.class); when(bad.getName()).thenReturn("contentImages"); when(bad.getSubmittedFileName()).thenReturn("bad.jsp"); when(bad.getSize()).thenReturn(4L);
        when(request.getParts()).thenReturn(List.of(good,bad));
        Connection conn=mock(Connection.class); PreparedStatement statement=mock(PreparedStatement.class); ResultSet rows=mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement); when(statement.executeQuery()).thenReturn(rows); when(rows.next()).thenReturn(true);
        try(var db=mockStatic(DBUtil.class);var board=mockConstruction(BoardService.class)) {
            db.when(DBUtil::getConnection).thenReturn(conn);
            assertThrows(ServletException.class,()->servlet(new BoardController(),"doPost"));
            try(var files=Files.list(root.resolve("attachments"))) { assertEquals(0,files.count()); }
            // Once the DB has committed, a broken HTTP response must not delete its files.
            when(request.getParts()).thenReturn(List.of(good));
            BoardController controller=new BoardController();
            BoardService service=board.constructed().getLast();
            when(service.createPost("free","title","content",1L)).thenReturn(8L);
            when(service.saveAttachments(eq(8L),eq(1L),anyList())).thenReturn(true);
            doThrow(new IOException("Disconnected client")).when(response).sendRedirect(anyString());
            assertThrows(IOException.class,()->servlet(controller,"doPost"));
            verify(service).createPost("free","title","content",1L);
            try(var files=Files.list(root.resolve("attachments"))) { assertEquals(1,files.count()); }
        } finally { System.clearProperty("redweb.upload.root"); System.clearProperty("redweb.upload.minFreeBytes"); }
    }
    @Test void commitTimeQuotaUsesLockAndRollsBack() throws Exception {
        Connection conn=mock(Connection.class); PreparedStatement statement=mock(PreparedStatement.class); ResultSet rows=mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement); when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true); when(rows.getLong(1)).thenReturn(UploadBudget.quotaBytes());
        AttachmentDTO file=new AttachmentDTO(); file.setFileSize(1L);
        try(var db=mockStatic(DBUtil.class)) {
            db.when(DBUtil::getConnection).thenReturn(conn);
            assertThrows(SQLException.class,()->new com.scuredweb.board.dao.BoardDAO().insertAttachments(8L,1L,List.of(file)));
            verify(conn).prepareStatement(contains("FOR UPDATE")); verify(conn).rollback(); verify(conn,never()).commit();
        }
    }
    @Test void additiveMigrationMatchesSchemaAndDoesNotDestroyData() throws Exception {
        String migration=Files.readString(Path.of("src/main/resources/sql/predeploy-indexes.sql"));
        assertFalse(migration.matches("(?s).*\\b(?:DROP|TRUNCATE|DELETE)\\s.*"));
        assertTrue(migration.contains("USER_IND_COLUMNS")); assertTrue(migration.contains("WHENEVER SQLERROR EXIT"));
        String schema=Files.readString(Path.of("src/main/resources/sql/scuredweb-schema.sql"));
        var matcher=java.util.regex.Pattern.compile("ensure_index\\('([^']+)', '([^']+)', '([^']+)'\\)").matcher(migration);
        int count=0;
        while(matcher.find()) { assertTrue(schema.contains("CREATE INDEX "+matcher.group(1)+" ON "+matcher.group(2)+" ("+matcher.group(3)+");"));count++; }
        assertEquals(11,count); // Static consistency only; actual Oracle execution remains a deployment gate.
    }
}
