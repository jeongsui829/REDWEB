# 배포 전 재점검 결과 (2026-09-28)

## 1. 실제 확인한 문제

수정 전 코드를 기준으로 다음 경로를 확인했다. 실제 OCI 운영 장애나 침해를 재현했다는 의미는 아니다.

- Java 25 target에 ojdbc11 provided 의존성이 설정되어 있었다. JDBC 호출은 표준 java.sql/JNDI를 사용해 드라이버별 코드 변경은 필요하지 않았다.
- 일반 댓글 삭제가 CONNECT BY 하위 트리 삭제를 사용했고, 탈퇴도 물리 삭제 경로를 사용해 다른 작성자의 답글을 함께 없앨 수 있었다.
- 공개 회원가입을 운영 설정으로 차단할 수 없어 여러 계정으로 사용자별 업로드 한도를 분산할 수 있었다. 실제 이메일 verification provider 부재 때문에 계정 수 제한으로 볼 수 없었다.
- OnlineUserController가 익명 조회에도 세션을 생성했다.
- HTTP redirect 목적지가 입력 Host에 의존했고 명시적인 미등록 Host 기본 거부 구성이 없었다.
- 공개 프로필 검색이 nickname 외 loginId도 허용했다.
- 잘못된 PUBLIC_BASE_URL을 null로 처리해 미설정 상태와 구분하지 못했다.

## 2. 수정한 파일

기존 파일 25개 수정, 회귀 테스트 3개 및 이 보고서 추가. 경로는 프로젝트 루트 기준이다.

| 파일 | 변경 |
| --- | --- |
| pom.xml | ojdbc17 provided, 테스트 전용 H2 |
| src/main/java/com/scuredweb/auth/controller/AuthController.java | 회원가입 GET/POST 차단 |
| src/main/java/com/scuredweb/auth/service/AuthService.java | 서비스에서도 가입 설정 확인 |
| src/main/java/com/scuredweb/board/controller/BoardController.java | 삭제 표시 댓글을 활성 댓글 수에서 제외 |
| src/main/java/com/scuredweb/comment/dao/CommentDAO.java | 일반 삭제 soft delete, 답글 연결 보존, 관리자 활성 댓글 수 처리 |
| src/main/java/com/scuredweb/comment/dto/CommentDTO.java | 삭제 상태와 하위 답글 순회 |
| src/main/java/com/scuredweb/common/config/AppConfig.java | URL 엄격 검증, 가입 설정 |
| src/main/java/com/scuredweb/common/listener/AppContextListener.java | DB 초기화 전 설정 검증 |
| src/main/java/com/scuredweb/social/controller/OnlineUserController.java | 기존 세션만 조회 |
| src/main/java/com/scuredweb/social/controller/ProfileController.java | 공개 nickname 전용 조회 |
| src/main/java/com/scuredweb/social/dao/SocialDAO.java | 공개 조회와 내부 조회 분리 |
| src/main/java/com/scuredweb/social/service/SocialService.java | 공개 조회 서비스 |
| src/main/java/com/scuredweb/user/dao/UserDAO.java | 탈퇴 시 타인 답글 보존 및 계정 익명화 |
| src/main/java/com/scuredweb/user/service/UserService.java | 탈퇴 전용 DAO 경로 사용 |
| src/main/resources/properties/app.properties | 설정 설명 |
| src/main/webapp/WEB-INF/views/board/detail.jsp | 삭제 표시 및 전체 하위 답글 표시 |
| deploy/gcp-test/conf/nginx-redweb.conf | canonical redirect, 기본 Host/SNI 거부 |
| deploy/gcp-test/conf/redweb-https.env.example | 운영 회원가입 false |
| deploy/gcp-test/PRODUCTION.md | JDBC/설정/인증서/볼륨 한도 운영 절차 |
| deploy/gcp-test/README.md | JDBC 설치 및 인증서 발급 순서 |
| scripts/verify-admin-ui.js | JDBC 경로 |
| scripts/verify-attachment-compensation.js | JDBC 경로 |
| scripts/verify-functional-flow.js | JDBC 경로 |
| scripts/verify-online-users.js | JDBC 경로 |
| scripts/verify-restart-persistence.js | JDBC 경로 |
| src/test/java/com/scuredweb/recheck/ReplyPreservationTest.java | 신규 SQL 회귀 테스트 9개 |
| src/test/java/com/scuredweb/recheck/RecheckHttpConfigTest.java | 신규 HTTP/설정 회귀 테스트 24개 |
| src/test/java/com/scuredweb/recheck/AdminReplyModerationTest.java | 신규 관리자 댓글 수 회귀 테스트 2개 |
| docs/RECHECK-REPORT.md | 이 보고서 |

## 3. 각 수정 이유

- ojdbc17 23.26.3.0.0을 provided로 사용하고 Tomcat lib에 단일 드라이버를 설치하도록 정리했다. [Oracle JDBC 공식 지원표](https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html)를 기준으로 runtime matrix를 갱신했다.
- 일반 댓글은 자식 유무와 관계없이 기존 STATUS로 삭제 표시한다. 동시 답글 작성과 물리 삭제의 경쟁을 피하고, 본문을 placeholder로 치환하며 활성 댓글 수만 감소시킨다. 작성자 소유권과 부모 ID는 유지한다.
- 탈퇴 시 본인 댓글만 삭제 표시하며 타인 답글을 보존한다. 필수 FK를 유지하기 위해 계정은 WITHDRAWN 익명화 행으로 남기고, 본인 게시글도 내용이 지워진 연결 행으로 남긴다. 종전 deleteUserHard는 오프라인 검증 데이터 정리 용도로만 남아 있으며 애플리케이션 탈퇴는 호출하지 않는다.
- REDWEB_PUBLIC_REGISTRATION_ENABLED 기본값은 true, 운영 예시는 false다. GET/POST와 서비스 진입점에서 차단한다. DB 동시성 보장 없는 전역 SUM quota는 추가하지 않았다. 별도 유한 용량 볼륨 또는 파일시스템 project quota와 용량/inode 감시를 운영 절차에 명시했다.
- 온라인 API는 getSession(false)만 사용한다. 세션 없는 조회도 JSON을 반환하며 로그인 추적은 유지한다.
- redirect 호스트를 REDWEB_TEST_HOST로 고정하고 미등록 HTTP Host 및 HTTPS SNI/Host를 거부한다. 인증서 선발급, nginx -t, 기존 default_server 충돌 확인 절차를 문서화했다.
- 공개 프로필은 ACTIVE + nickname으로만 조회한다. 인증된 내부 수신자 검색의 loginId 기능과 최소 PublicProfileDTO는 유지한다.
- 명시적인 빈 값·공백·잘못된 URL은 시작 실패로 처리한다. 미설정 개발환경과 구분하고, HTTPS + Secure=false 검증도 유지한다.

## 4. 테스트 결과

- mvn -B clean test: 68개, 실패 0 / 오류 0 / 생략 0.
- scripts/package-production.ps1 내부 mvn -B clean package: 68개, 실패 0 / 오류 0 / 생략 0, BUILD SUCCESS.
- 기존 보안 테스트 33개는 수정 없이 유지했고 신규 35개를 추가했다.
- A→B→C에서 A/B의 댓글 삭제 및 탈퇴 후 타인 내용·소유권·부모 ID 보존, 중복 삭제/탈퇴, 비소유자 거부, 댓글 수를 검증했다.
- 관리자 subtree 삭제와 이미 삭제된 연결 댓글의 활성 댓글 수 처리를 mock JDBC로 검증했다. 기존 fresh step-up 테스트를 유지했다.
- 회원가입 GET/POST/서비스 차단, 기존 로그인, 익명 온라인 조회 100회 세션 미생성, 로그인 추적, 공개 loginId 조회 실패, 비활성 계정 제외를 검증했다.
- 잘못된 URL의 시작 실패, 실제 별도 JVM 환경변수 입력, 미설정 기본값, HTTPS + Secure=false 및 Nginx/의존성 정적 검증을 수행했다.
- Tomcat Jasper 11.0.26: 최종 WAR 전개본 JSP 컴파일 오류 0.
- 수정한 JS 5개 node --check 통과. pom.xml/web.xml XML 파싱 통과.
- WAR의 WEB-INF/lib에는 JSTL만 있으며 JDBC/H2/JUnit/Mockito/Servlet API jar는 없다. 배포 비밀정보 스캔 결과는 빈 목록이다.
- 최종 배포 묶음: dist/production-20260928-134506. SHA256.csv의 12개 파일 해시가 모두 일치한다.
- WAR SHA-256: 8C317DA135816FDDA9BEF2B8BFB36AD4BEC8C2BB6CDEF5965A799B31D3FF3BF7.
- 로그: .work-recheck/clean-test.log, clean-package.log, jsp-final.log.

한계: 로컬 Java는 25.0.2다. 목표 Temurin 25.0.4.1+1 및 실제 OCI/Tomcat/JNDI 환경 실행은 하지 않았다. SQL 회귀 테스트는 H2 Oracle mode이며 실제 Oracle CONNECT BY/잠금 동시성 검증을 대체하지 않는다. Nginx는 정적 검증이며 실제 nginx -t/TLS 테스트는 staging에서 필요하다.

## 5. DB migration 추가 여부

추가 없음. 기존 STATUS 및 FK 구조를 사용한다. SQL/schema 파일은 재점검 전과 동일하다. 배포 묶음의 기존 1차 점검 SQL은 유지되었으며 이번 작업에서 DB 삭제·재생성·migration 실행은 하지 않았다. H2는 테스트 의존성이다.

## 6. 기존 기능에 영향을 준 부분

- 댓글 삭제 후 placeholder가 남으며 모든 하위 답글을 기존 스타일로 표시한다. 일반 사용자는 삭제 표시된 댓글을 다시 삭제하지 못하고, 관리자는 기존 step-up moderation으로 트리 삭제가 가능하다.
- 탈퇴는 계정/게시글 행의 즉시 물리 제거 대신 개인정보·본문을 제거한 연결 행을 유지한다. 타인 답글 작성자와 소유권은 바뀌지 않는다.
- 회원가입 비활성 설정만 신규 가입을 막으며 기존 로그인은 유지한다. 이미 만들어진 다중 계정의 업로드 총량을 제한하는 기능은 아니므로 볼륨 한도가 필요하다.
- 익명 온라인 API 호출만으로 새로운 방문자 세션이 집계되지는 않는다. 로그인 사용자 추적은 유지한다. 익명 응답은 인원 수와 빈 이름 목록을 반환한다. 인증된 사용자에게 nickname/display name을 제공하는 기존 정책은 유지하며, nickname이 없을 때 loginId fallback이 있으므로 공개 범위는 운영 privacy 정책으로 검토해야 한다.
- 공개 프로필은 nickname으로만 접근하며 내부 인증 검색은 유지한다.
- 잘못된 운영 URL/가입 설정은 시작을 실패시킨다.
- 기존 AdminFilter, CSRF, 업로드 검증/사용자 quota, RateLimitService, PublicProfileDTO, CSS와 SQL은 baseline 해시 비교로 변경 없음을 확인했다. 기존 URL 및 UI 디자인은 유지했다.

## 7. 사람이 실제 OCI staging에서 검증할 항목

1. 목표 JDK 25/Tomcat 11.0.26과 ojdbc17 23.26.3.0.0 단일 외부 jar로 JNDI 연결, 재시작, 기존 데이터 조회 확인. 예전 JDBC jar를 Tomcat lib 밖으로 이동해 중복 로딩 방지.
2. 실제 Oracle에서 A/B/C 댓글 삭제와 탈퇴, 탈퇴자가 게시글 작성자인 경우, 동시 답글 작성/삭제/탈퇴, 실패 시 rollback, 댓글 수 및 FK 보존 확인.
3. 관리자 fresh step-up 만료/재인증 및 삭제 표시 부모를 포함한 subtree moderation과 감사 기록 확인.
4. 정상 도메인 HTTP→HTTPS, 알 수 없는 Host/SNI, 정상 SNI+비정상 Host, 인증서 갱신 및 nginx -t 확인. 기존 default_server 중복 제거, DNS-01 갱신 운영 절차 확인.
5. malformed REDWEB_PUBLIC_BASE_URL 및 HTTPS+Secure=false로 시작 실패, 정상 HTTPS의 Secure/HttpOnly/SameSite 쿠키 확인.
6. 운영 회원가입 false에서 GET/POST 거부, 기존 관리자/사용자 로그인, nickname 프로필 성공 및 loginId만으로 공개 조회 실패 확인.
7. Cookie 없는 온라인 API 반복 요청에 새 JSESSIONID가 발급되지 않는지 실제 프록시·필터 체인에서 확인하고, 로그인 추적 및 이름 노출 privacy 정책 검토.
8. 업로드 별도 볼륨의 강제 용량 한도, bytes/inodes/임시파일/cleanup 및 경고 설정 확인. 기존 파일·개수·사용자 quota·여유 공간·MIME/signature 검증 유지 확인.

OCI 배포 및 운영 DB 변경은 수행하지 않았다. 상세 운영 절차는 deploy/gcp-test/PRODUCTION.md에 정리했다.
