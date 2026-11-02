package com.scuredweb.recheck;

import com.scuredweb.auth.controller.AuthController;
import com.scuredweb.auth.service.AuthService;
import com.scuredweb.common.config.*;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.listener.AppContextListener;
import com.scuredweb.common.util.OnlineUserTracker;
import com.scuredweb.social.controller.OnlineUserController;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecheckHttpConfigTest {
    HttpServletRequest request; HttpServletResponse response;
    Map<String,String> saved;
    @BeforeEach void exchange() {
        saved=new HashMap<>();
        for(String name:List.of("redweb.publicBaseUrl","redweb.publicRegistrationEnabled","redweb.secureCookie","scuredweb.secureCookie")) {
            saved.put(name,System.getProperty(name));System.clearProperty(name);
        }
        // Test configuration must not inherit an operator's production settings.
        System.setProperty("redweb.publicBaseUrl","http://localhost");
        System.setProperty("redweb.publicRegistrationEnabled","true");
        request=mock(HttpServletRequest.class);response=mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("");
    }
    @AfterEach void restore() { saved.forEach((k,v)-> { if(v==null) System.clearProperty(k); else System.setProperty(k,v); }); }
    void call(Object controller,String name) throws Exception {
        Method method=controller.getClass().getDeclaredMethod(name,HttpServletRequest.class,HttpServletResponse.class);
        method.setAccessible(true);
        try { method.invoke(controller,request,response); }
        catch(InvocationTargetException e) { if(e.getCause() instanceof Exception ex) throw ex;throw e; }
    }
    @Test void anonymousHeartbeatNeverCreatesSession() throws Exception {
        StringWriter json=new StringWriter();when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(request.getSession(false)).thenReturn(null);
        OnlineUserController controller=new OnlineUserController();
        try(var tracker=mockStatic(OnlineUserTracker.class)) {
            tracker.when(OnlineUserTracker::status).thenReturn(new OnlineUserTracker.OnlineStatus(0,0,0,List.of()));
            for(int i=0;i<100;i++) call(controller,"doGet");
            verify(request,never()).getSession(true);verify(request,never()).getSession();
            tracker.verify(()->OnlineUserTracker.userUpdated(any(),any()),never());
        }
        assertTrue(json.toString().contains("\"userListVisible\":false"));
        assertTrue(json.toString().contains("\"users\":[]"));
    }
    @Test void loggedInHeartbeatAndNamePolicyRemain() throws Exception {
        HttpSession session=mock(HttpSession.class);UserDTO user=new UserDTO();user.setUserId(1L);user.setNickname("Public Nickname");
        when(request.getSession(false)).thenReturn(session);when(session.getId()).thenReturn("known-session");
        when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user);
        StringWriter json=new StringWriter();when(response.getWriter()).thenReturn(new PrintWriter(json));
        try(var tracker=mockStatic(OnlineUserTracker.class)) {
            tracker.when(OnlineUserTracker::status).thenReturn(new OnlineUserTracker.OnlineStatus(1,1,0,
                    List.of(new OnlineUserTracker.OnlineUser("known-session",1L,"Public Nickname",true,System.currentTimeMillis()))));
            call(new OnlineUserController(),"doGet");tracker.verify(()->OnlineUserTracker.userUpdated("known-session",user));
        }
        assertTrue(json.toString().contains("\"name\":\"Public Nickname\""));
        verify(request,never()).getSession(true);
    }
    @ParameterizedTest @ValueSource(strings={"doGet","doPost"})
    void disabledRegistrationRejectsBothMethodsWithoutServiceCall(String method) throws Exception {
        System.setProperty("redweb.publicRegistrationEnabled","false");when(request.getServletPath()).thenReturn("/register");
        try(var auth=mockConstruction(AuthService.class)) {
            call(new AuthController(),method);verify(response).sendError(eq(403),anyString());
            verifyNoInteractions(auth.constructed().getFirst());
        }
    }
    @Test void disabledRegistrationAlsoRejectsDirectServiceEntry() {
        System.setProperty("redweb.publicRegistrationEnabled","false");assertFalse(new AuthService().register(null).isSuccess());
    }
    @Test void registrationDefaultsOnAndDoesNotBlockLoginPage() throws Exception {
        assertTrue(AppConfig.publicRegistrationEnabled());
        System.setProperty("redweb.publicRegistrationEnabled","false");when(request.getServletPath()).thenReturn("/login");
        RequestDispatcher dispatcher=mock(RequestDispatcher.class);when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        call(new AuthController(),"doGet");verify(dispatcher).forward(request,response);
        verify(response,never()).sendError(anyInt(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "https://", "not-a-url", "/relative", "ftp://example.com", " https://example.com", "https://user@example.com", "https://example.com?x=1", "https://example.com/#fragment", "https://example.com:99999", "https://example.com\n"})
    void explicitMalformedUrlFailsStartupBeforeDatabase(String value) {
        System.setProperty("redweb.publicBaseUrl",value);
        try(var db=mockStatic(DBConfig.class)) {
            assertThrows(IllegalStateException.class,()->new AppContextListener().contextInitialized(mock(ServletContextEvent.class)));
            db.verifyNoInteractions();
        }
    }
    @Test void developmentHttpStillAllowedButHttpsNeedsSecure() {
        assertEquals("http://localhost",AppConfig.publicBaseUrl());assertFalse(AppConfig.secureCookie());
        System.setProperty("redweb.publicBaseUrl","https://example.com/scuredweb");
        assertThrows(IllegalStateException.class,AppConfig::secureCookie);
        System.setProperty("redweb.secureCookie","true");assertTrue(AppConfig.secureCookie());
    }
    @Test void environmentUrlIsValidatedInFreshJvm() throws Exception {
        for(String url:List.of("https://", "", "javascript:alert(1)")) {
            ProcessBuilder process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-cp",System.getProperty("java.class.path"),ConfigProbe.class.getName());
            process.environment().put("REDWEB_PUBLIC_BASE_URL",url);
            process.redirectErrorStream(true);
            Process child=process.start();child.getInputStream().transferTo(OutputStream.nullOutputStream());
            assertEquals(3,child.waitFor());
        }
    }
    public static class ConfigProbe {
        public static void main(String[] args) {
            try {
                if(args.length>0 && "defaults".equals(args[0])) {
                    System.exit(AppConfig.publicBaseUrl()==null && AppConfig.publicRegistrationEnabled() && !AppConfig.secureCookie() ? 0 : 4);
                }
                AppConfig.secureCookie();System.exit(0);
            }
            catch(IllegalStateException e) { System.exit(3); }
        }
    }
    @Test void unspecifiedDevelopmentConfigurationKeepsDefaults() throws Exception {
        ProcessBuilder process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-cp",System.getProperty("java.class.path"),ConfigProbe.class.getName(),"defaults");
        process.environment().remove("REDWEB_PUBLIC_BASE_URL");process.environment().remove("REDWEB_PUBLIC_REGISTRATION_ENABLED");
        Process child=process.start();child.getInputStream().transferTo(OutputStream.nullOutputStream());
        assertEquals(0,child.waitFor());
    }
    @Test void deletedCommentAnchorsDoNotIncreaseDisplayedCount() throws Exception {
        when(request.getParameter("id")).thenReturn("1");
        when(request.getServletContext()).thenReturn(mock(ServletContext.class));
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
        var post=new com.scuredweb.board.dto.PostDTO();post.setPostId(1L);post.setCommentCount(0);
        var deleted=new com.scuredweb.comment.dto.CommentDTO();deleted.setCommentId(10L);deleted.setDeleted(true);
        try(var boards=mockConstruction(com.scuredweb.board.service.BoardService.class,(service,ctx)->when(service.getPostDetail(1L)).thenReturn(post));
            var comments=mockConstruction(com.scuredweb.comment.service.CommentService.class,(service,ctx)->when(service.getComments(1L)).thenReturn(List.of(deleted)))) {
            when(request.getServletPath()).thenReturn("/board/detail");
            call(new com.scuredweb.board.controller.BoardController(),"doGet");
            assertEquals(0,post.getCommentCount());
        }
    }
    @Test void deploymentPinsProvidedOjdbc17AndHostRedirectIsCanonical() throws Exception {
        String pom=Files.readString(Path.of("pom.xml"));
        assertTrue(pom.matches("(?s).*<artifactId>ojdbc17</artifactId>\\s*<version>\\$\\{ojdbc.version}</version>\\s*<scope>provided</scope>.*"));
        assertTrue(pom.contains("<ojdbc.version>23.26.3.0.0</ojdbc.version>"));assertFalse(pom.contains("<artifactId>ojdbc11</artifactId>"));
        String nginx=Files.readString(Path.of("deploy/gcp-test/conf/nginx-redweb.conf"));
        assertFalse(nginx.matches("(?s).*return\\s+30[1278]\\s+https?://\\$host.*"));
        assertTrue(nginx.contains("return 301 https://REDWEB_TEST_HOST$request_uri;"));
        assertTrue(nginx.contains("listen 80 default_server;"));assertTrue(nginx.contains("ssl_reject_handshake on;"));
        assertTrue(nginx.contains("if ($host != REDWEB_TEST_HOST) { return 444; }"));
    }
    @Test void withdrawalServiceCannotUseHardDelete() throws Exception {
        UserDTO user=new UserDTO();user.setUserId(1L);
        try(var daos=mockConstruction(com.scuredweb.user.dao.UserDAO.class,(dao,context)->when(dao.withdrawUser(1L)).thenReturn(true))) {
            assertTrue(new com.scuredweb.user.service.UserService().withdraw(user));
            verify(daos.constructed().getFirst()).withdrawUser(1L);
            verify(daos.constructed().getFirst(),never()).deleteUserHard(anyLong());
        }
    }
}
