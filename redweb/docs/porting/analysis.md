# Phase 1: comparison and merge decisions

TARGET: redweb / com.redweb. REFERENCE: scuredweb / com.scuredweb.
Baseline: `.porting/baseline.zip`. Unified comparisons: `.porting/diffs/`.

| File | Change | Take from reference | Preserve from target | Vulnerability impact |
|---|---|---|---|---|
| `src/main/java/com/redweb/admin/controller/AdminDashboardController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/controller/AdminManagementController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/controller/AdminVerificationController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/dao/AdminDashboardDAO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/dto/DashboardMetricDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/service/AdminDashboardService.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/service/AdminManagementService.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/admin/service/AdminOpsService.java` | target only | none | entire original | unchanged |
| `src/main/java/com/redweb/auth/controller/AuthController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/auth/dao/AuthDAO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/auth/dto/LoginRequestDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/auth/dto/RegisterRequestDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/auth/service/AuthResult.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/auth/service/AuthService.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/controller/BoardController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/dao/BoardDAO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/dto/BoardDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/dto/CategoryDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/dto/PostDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/dto/TagDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/board/service/BoardService.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/comment/controller/CommentController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/comment/dao/CommentDAO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/comment/dto/CommentDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/comment/service/CommentService.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/config/DBConfig.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/constant/MessageKey.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/constant/RoleCode.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/constant/SessionKey.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/constant/ViewPath.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/controller/AssetController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/exception/AppException.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/filter/AdminFilter.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/filter/AuthFilter.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/filter/EncodingFilter.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/listener/AppContextListener.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/util/DBUtil.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/util/RequestUtil.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/util/SampleDataFactory.java` | target only | none | entire original | unchanged |
| `src/main/java/com/redweb/common/util/UploadPathUtil.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/common/util/ViewUtil.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/error/controller/ErrorController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/file/controller/FileDownloadController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/file/dto/AttachmentDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/home/controller/HomeController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/notification/dto/NotificationDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/search/controller/SearchController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/controller/MessageController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/controller/NotificationController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/controller/ProfileController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/controller/ReportController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/dao/SocialDAO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/dto/GuestbookEntryDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/dto/MessageDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/dto/ReportDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/social/service/SocialService.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/user/controller/UserController.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/user/dao/UserDAO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/user/dto/LoginHistoryDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/user/dto/UserDTO.java` | different | none | entire original | unchanged |
| `src/main/java/com/redweb/user/service/UserService.java` | different | none | entire original | unchanged |
| `src/main/resources/properties/jdbc.properties` | different | none | entire original | unchanged |
| `src/main/resources/sql/create-users-3-6.sql` | different | none | entire original | unchanged |
| `src/main/resources/sql/redweb-db-upgrade.sql` | target only | none | entire original | unchanged |
| `src/main/resources/sql/redweb-schema.sql` | target only | none | entire original | unchanged |
| `src/main/resources/sql/redweb-seed.sql` | target only | none | entire original | unchanged |
| `src/main/webapp/assets/css/admin/admin.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/common.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/components.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/layout.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/pages/auth.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/pages/board.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/pages/home.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/css/pages/user.css` | different | exact stylesheet | n/a | none |
| `src/main/webapp/assets/js/common.js` | different | search/menu/notification UI, online display | existing fetch and GET report link | exclude CSRF injection and POST report |
| `src/main/webapp/assets/js/home.js` | identical | none | entire original | unchanged |
| `src/main/webapp/WEB-INF/views/admin/dashboard.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/admin/notices.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/admin/posts.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/admin/reports.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/admin/users.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/admin/verify.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/auth/find-id.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/auth/find-password.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/auth/login.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/auth/register.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/auth/reset-password.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/auth/verify-email.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/board/detail.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/board/edit.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/board/list.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/board/write.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/common/admin-header.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/common/admin-sidebar.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/common/footer.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/common/header.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/common/sidebar.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/error/404.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/error/500.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/home/index.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/search/results.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/user/messages.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/user/mypage.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/views/user/profile.jsp` | different | classes, wrappers, layout | bindings, raw outputs, form/URL contract | exclude CSRF, escaping and changed auth flow |
| `src/main/webapp/WEB-INF/web.xml` | different | none | entire original | unchanged |

## Reference-only Java inventory

| File | Category | Decision |
|---|---|---|
| `admin/dao/AdminAuditDAO.java` | normal feature / operational infrastructure | exclude: configuration, audit, health and exception model unnecessary for UI |
| `auth/service/AccountRecoveryNotifier.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `auth/service/AccountRecoveryNotifierFactory.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `auth/service/NoOpAccountRecoveryNotifier.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/config/AppConfig.java` | normal feature / operational infrastructure | exclude: configuration, audit, health and exception model unnecessary for UI |
| `common/controller/HealthController.java` | normal feature / operational infrastructure | exclude: configuration, audit, health and exception model unnecessary for UI |
| `common/exception/DataAccessException.java` | normal feature / operational infrastructure | exclude: configuration, audit, health and exception model unnecessary for UI |
| `common/filter/CsrfFilter.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/filter/SecurityHeadersFilter.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/listener/OnlineUserListener.java` | normal feature | port: independent session observer/API; no DAO/auth mutation |
| `common/security/AuthorizationUtil.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/security/CsrfUtil.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/security/PasswordUtil.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/security/RateLimitService.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/util/FileValidationUtil.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/util/HtmlUtil.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/util/InputValidator.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/util/OnlineUserTracker.java` | normal feature | port: independent session observer/API; no DAO/auth mutation |
| `common/util/RedirectUtil.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `common/util/StoredFileCleanup.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `file/controller/FileImageController.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `file/dao/FileDAO.java` | security hardening / coupled feature | exclude: changes security, file, or account recovery behavior |
| `social/controller/OnlineUserController.java` | normal feature | port: independent session observer/API; no DAO/auth mutation |

## Page-specific decisions
- reset-password: retain target security question/answer and resetQuestion session flow.
- verify-email: retain original form.
- header: original notification lookup and GET logout.
- detail: raw post/comments, savedName + originalName download parameters, per-field ID defaults and permission conditions.
- write/edit: reference editor appearance with target multipart fields and raw HTML editing output.
- mypage/profile: exclude image path allowlist; retain direct image src and all existing data output fields.
- board/list: read popular posts through existing BoardService.getTrendingPosts() from the JSP; no BoardController change.
- favicon: serve same SVG under assets because existing HomeController owns the default / mapping.
- Preserve all existing Java, pom.xml, web.xml and resources/SQL byte for byte.