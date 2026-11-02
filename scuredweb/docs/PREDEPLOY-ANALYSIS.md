# 배포 전 분석 및 변경 계획 (2026-09-28)

Servlet/JSP → service → JDBC DAO 구조, 전역 CSRF/CSP, AuthFilter 및 /admin/* AdminFilter, 외부 업로드 저장소와 Oracle JNDI 구조를 확인했다. 구조와 기존 URL을 유지한다.

|원인|수정 대상|
|---|---|
|일반 삭제에서 역할만으로 관리자 삭제 허용|BoardController, CommentService, 관리자 댓글 controller, detail.jsp|
|비공개 첨부에 관리자 역할만 검사|AuthorizationUtil, AdminFilter, FileDownloadController, FileImageController|
|공개 조회가 전체 UserDTO와 비활성 계정을 반환|SocialDAO/Service, social controllers, public profile DTO, profile/messages JSP|
|누적 용량·여유 공간 제한 없음, 업로드 중간 실패 cleanup 누락|BoardController/DAO, UserController, UploadBudget, AbuseFilter|
|HTTPS 공개 URL과 insecure cookie 조합 허용|AppConfig, AppContextListener|
|GET에서 읽음 상태 UPDATE|UserController, MessageController, common.js|
|사용되지 않는 JVM 사용자 캐시|AuthService 및 sync/remove/update 호출부|
|비밀번호 최대 길이 없음|인증/사용자 service, PasswordUtil 호출부, password JSP|
|평문 계정 사전 점검 없음|COUNT 전용 운영 SQL|
|목록 DAO의 무제한 조회와 정렬|인덱스 migration 및 schema; pagination 호환성 검토|
|제한된 테스트용 배포 설정|Nginx/Tomcat 설정, runtime pin, 영속성/백업 문서, 배포 package script|
|Maven 회귀 테스트 부재|pom.xml, src/test/java|

LOCAL_USERS 읽기는 getLocalUsers 자체에만 있고 실제 호출자는 없다. 상태 변경은 DB 기반 AuthFilter가 재확인한다. UserService 비밀번호 변경과 관리자 재인증이 세션 hash를 사용하므로 전체 SessionPrincipal 전환은 별도 개선 대상으로 남긴다.

BoardController는 전체 목록을 Java 정렬하며 현재 JSP에 페이지 이동 기능이 없다. DB pagination을 무조건 적용하면 기존 목록이 잘리고 UI 변경이 필요하므로 우선 호환성 영향과 정렬을 문서화하고 인덱스를 추가한다.

새 설치 schema는 운영 DB에서 실행하지 않는다. 운영에는 additive migration만 제공하며 실제 운영 DB를 변경하지 않는다. 현재 .git 디렉터리는 유효한 Git 저장소가 아니므로 파일 기반 변경 명세를 기록한다.
