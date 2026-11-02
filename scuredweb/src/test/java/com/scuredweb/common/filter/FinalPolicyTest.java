package com.scuredweb.common.filter;

import com.scuredweb.common.config.*;
import com.scuredweb.common.security.*;
import com.scuredweb.common.util.UploadPathUtil;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.user.service.UserService;
import com.scuredweb.auth.service.AuthService;
import com.scuredweb.auth.dto.RegisterRequestDTO;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinalPolicyTest {
    Properties before;
    @BeforeEach void config() { before=(Properties)System.getProperties().clone();System.setProperty("redweb.publicBaseUrl","http://localhost:8080/scuredweb"); }
    @AfterEach void restore() { System.setProperties(before); }
    UserDTO user(String role,long version) { UserDTO u=new UserDTO();u.setUserId(1L);u.setRoleCode(role);u.setStatus("ACTIVE");u.setAuthVersion(version);return u; }
    @ParameterizedTest @ValueSource(strings={"MEMBER","ADMIN","SUPER_ADMIN"})
    void oldSessionsInvalidatedAfterCredentialVersionChanges(String role) throws Exception {
        UserDTO current=user(role,2);
        try(var daos=mockConstruction(UserDAO.class,(d,c)->when(d.findById(1L)).thenReturn(current))) {
            for(int i=0;i<2;i++) {
                HttpServletRequest r=mock(HttpServletRequest.class);HttpServletResponse p=mock(HttpServletResponse.class);HttpSession session=mock(HttpSession.class);FilterChain chain=mock(FilterChain.class);
                when(r.getSession(false)).thenReturn(session);when(session.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user(role,1));when(r.getContextPath()).thenReturn("");
                when(r.getServletPath()).thenReturn("/admin/verify");when(session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED)).thenReturn(true);when(session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT)).thenReturn(System.currentTimeMillis());
                if(role.equals("MEMBER")) new AuthFilter().doFilter(r,p,chain);else new AdminFilter().doFilter(r,p,chain);
                verify(session).invalidate();verify(chain,never()).doFilter(any(),any());
            }
        }
    }
    @Test void currentVersionAllowsNewSession() throws Exception {
        try(var daos=mockConstruction(UserDAO.class,(d,c)->when(d.findById(1L)).thenReturn(user("MEMBER",3)))) {
            var r=mock(HttpServletRequest.class);var p=mock(HttpServletResponse.class);var s=mock(HttpSession.class);var chain=mock(FilterChain.class);
            when(r.getSession(false)).thenReturn(s);when(s.getAttribute(SessionKey.LOGIN_USER)).thenReturn(user("MEMBER",3));
            new AuthFilter().doFilter(r,p,chain);verify(chain).doFilter(r,p);verify(s,never()).invalidate();
        }
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void withdrawalServiceRejectsAdminsBeforeDAO(String role) throws Exception {
        try(var daos=mockConstruction(UserDAO.class)) {assertFalse(new UserService().withdraw(user(role,1),"correct password 123"));verifyNoInteractions(daos.constructed().getFirst());}
    }
    @Test void newPasswordsHave15To128CharactersOldPasswordsStillVerify() {
        assertFalse(PasswordUtil.isNewPasswordAllowed("x".repeat(14)));assertTrue(PasswordUtil.isNewPasswordAllowed("x".repeat(15)));
        assertTrue(PasswordUtil.isNewPasswordAllowed("x".repeat(128)));assertFalse(PasswordUtil.isNewPasswordAllowed("x".repeat(129)));
        assertTrue(PasswordUtil.verifyPassword("oldshort",PasswordUtil.hash("oldshort")));
        var u=user("MEMBER",1);u.setPasswordHash(PasswordUtil.hash("oldshort"));
        assertNotNull(new UserService().changePassword(u,"oldshort","x".repeat(14),"x".repeat(14)));
        assertFalse(new AuthService().resetPasswordWithToken("dummy","x".repeat(14),"x".repeat(14)).isSuccess());
    }
    @Test void newHashTargetAndOldFormatRemainCompatible() throws Exception {
        String password="old password 123";byte[] salt=new byte[16];
        var spec=new javax.crypto.spec.PBEKeySpec(password.toCharArray(),salt,210000,256);
        byte[] hash=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String old="pbkdf2$210000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(hash);
        assertTrue(PasswordUtil.verifyPassword(password,old));assertTrue(PasswordUtil.needsRehash(old));
        String fresh=PasswordUtil.hash(password);assertTrue(fresh.startsWith("pbkdf2$600000$"));assertFalse(PasswordUtil.needsRehash(fresh));
    }
    @Test void plaintextDisabledAndProductionDefaultDisabled() {
        System.setProperty("redweb.allowLegacyPlaintextPasswords","false");assertFalse(PasswordUtil.verifyPassword("legacy","legacy"));
        System.clearProperty("redweb.allowLegacyPlaintextPasswords");System.setProperty("redweb.publicBaseUrl","https://example.invalid");assertFalse(PasswordUtil.verifyPassword("legacy","legacy"));
    }
    @Test void productionRegistrationRequiresExplicitChoice() {
        System.setProperty("redweb.publicBaseUrl","https://example.invalid");System.clearProperty("redweb.publicRegistrationEnabled");
        assertThrows(IllegalStateException.class,AppConfig::publicRegistrationEnabled);
        System.setProperty("redweb.publicRegistrationEnabled","false");assertFalse(AppConfig.publicRegistrationEnabled());
    }
    @ParameterizedTest @ValueSource(strings={"", " ","-1","nonnumeric"})
    void malformedQuotaFailsBeforeUpload(String bad) {
        System.setProperty("redweb.upload.quotaBytes",bad);assertThrows(RuntimeException.class,UploadBudget::validateConfiguration);
    }
    @Test void productionRootRequiredAndRelativeRootRejected() {
        System.setProperty("redweb.publicBaseUrl","https://example.invalid");System.clearProperty("redweb.upload.root");
        assertThrows(IllegalStateException.class,UploadPathUtil::validateConfiguration);
        System.setProperty("redweb.upload.root","relative/uploads");assertThrows(IllegalStateException.class,UploadPathUtil::validateConfiguration);
    }
    @Test void requireJndiDoesNotUseDirectFallback() throws Exception {
        System.setProperty("redweb.requireJndi","true");System.setProperty("redweb.db.url","jdbc:unused");System.setProperty("redweb.db.username","test");System.setProperty("redweb.db.password","fixture-only");
        var field=DBConfig.class.getDeclaredField("dataSource");field.setAccessible(true);Object old=field.get(null);field.set(null,null);
        try(var drivers=mockStatic(java.sql.DriverManager.class)) {
            assertThrows(java.sql.SQLException.class,DBConfig::getConnection);drivers.verify(()->java.sql.DriverManager.getConnection(anyString(),anyString(),anyString()),never());
            javax.sql.DataSource ds=mock(javax.sql.DataSource.class);java.sql.SQLException failure=new java.sql.SQLException("fixture");when(ds.getConnection()).thenThrow(failure);field.set(null,ds);
            assertThrows(java.sql.SQLException.class,DBConfig::getConnection);drivers.verify(()->java.sql.DriverManager.getConnection(anyString(),anyString(),anyString()),never());
        } finally {field.set(null,old);}
    }
    @ParameterizedTest @ValueSource(strings={"ip","account","pair"})
    void independentLoginBucketsCannotBeResetBySuccessfulLogin(String bucket) {
        String scope="login";String id=UUID.randomUUID().toString();
        System.setProperty("redweb.rate.login.ip","100");System.setProperty("redweb.rate.login.account","100");System.setProperty("redweb.rate.login.pair","100");System.setProperty("redweb.rate.login."+bucket,"2");
        String ip=id, account=id;
        assertTrue(RateLimitService.consumeAuthentication(scope,ip,account));
        assertTrue(RateLimitService.consumeAuthentication(scope,bucket.equals("account")?id+"2":ip,bucket.equals("ip")?id+"2":account));
        RateLimitService.recordSuccess(scope,ip,account);
        assertFalse(RateLimitService.consumeAuthentication(scope,ip,account));
    }
    @ParameterizedTest @ValueSource(strings={"/comment/add","/messages/send","/report/user"})
    void abuseLimitsUserAcrossIPs(String path) throws Exception {
        String scope=path.contains("comment")?"comment":path.contains("messages")?"message":"report";System.setProperty("redweb.rate."+scope,"1");
        var r=mock(HttpServletRequest.class);var p=mock(HttpServletResponse.class);var s=mock(HttpSession.class);var chain=mock(FilterChain.class);
        when(r.getMethod()).thenReturn("POST");when(r.getServletPath()).thenReturn(path);when(r.getSession(false)).thenReturn(s);
        UserDTO u=user("MEMBER",1);u.setUserId(System.nanoTime());when(s.getAttribute(SessionKey.LOGIN_USER)).thenReturn(u);
        when(r.getRemoteAddr()).thenReturn(UUID.randomUUID().toString(),UUID.randomUUID().toString());
        AbuseFilter filter=new AbuseFilter();filter.doFilter(r,p,chain);filter.doFilter(r,p,chain);
        verify(chain,times(1)).doFilter(r,p);verify(p).sendError(429);verify(r,never()).getParameter(anyString());
    }
    @ParameterizedTest @ValueSource(strings={"TRACE","CONNECT"})
    void unsafeMethodsRejected(String method) throws Exception {
        var r=mock(HttpServletRequest.class);var p=mock(HttpServletResponse.class);var c=mock(FilterChain.class);when(r.getMethod()).thenReturn(method);
        new SecurityHeadersFilter().doFilter(r,p,c);verify(p).sendError(405);verifyNoInteractions(c);
    }
    @Test void contextPermissionAndBootOrderingAreExplicit() throws Exception {
        String readme=Files.readString(Path.of("deploy/gcp-test/README.md"));assertTrue(readme.contains("-m 0640 /tmp/scuredweb.xml"));assertFalse(readme.contains("chmod 0600 /opt/tomcat/conf/Catalina/localhost/scuredweb.xml"));
        String oracle=Files.readString(Path.of("deploy/oci/conf/oracle-free.service"));assertTrue(oracle.contains("RequiresMountsFor=/srv/redweb/oracle"));assertTrue(oracle.contains("mountpoint -q /srv/redweb/oracle"));
        String tomcat=Files.readString(Path.of("deploy/oci/conf/tomcat.service"));assertTrue(tomcat.contains("ExecStartPre=/usr/bin/test -r /opt/tomcat/conf/Catalina/localhost/scuredweb.xml"));
    }
    @Test void uploadConcurrencyAndMinimumSpaceValidateAtStartup() {
        System.setProperty("redweb.upload.concurrency","0");assertThrows(RuntimeException.class,UploadBudget::validateConfiguration);
        System.setProperty("redweb.upload.concurrency","17");assertThrows(RuntimeException.class,UploadBudget::validateConfiguration);
        System.setProperty("redweb.upload.concurrency","2");System.setProperty("redweb.upload.minFreeBytes","-1");assertThrows(RuntimeException.class,UploadBudget::validateConfiguration);
    }
    @Test void productionLegacyWithdrawalHelperIsDisabled() {
        System.setProperty("redweb.publicBaseUrl","https://example.invalid");
        assertFalse(new UserService().withdraw(user("MEMBER",1)));
        assertDoesNotThrow(()->assertFalse(new UserDAO().withdrawUser(1L)));
    }
    @Test void anonymousRegistrationRetainsExistingFivePerIPBudget() throws Exception {
        var r=mock(HttpServletRequest.class);var p=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
        when(r.getMethod()).thenReturn("POST");when(r.getServletPath()).thenReturn("/register");when(r.getRemoteAddr()).thenReturn(UUID.randomUUID().toString());
        AbuseFilter filter=new AbuseFilter();for(int i=0;i<6;i++) filter.doFilter(r,p,chain);
        verify(chain,times(5)).doFilter(r,p);verify(p).sendError(429);
    }
}
