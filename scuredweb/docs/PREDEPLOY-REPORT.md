# REDWEB 공개 배포 전 수정 보고서

작성: 2026-09-28. 실제 클라우드 배포 및 운영 DB 변경은 수행하지 않았다. 구조·기존 URL·CSS를 유지하면서 보안 경계와 운영 패키지를 보완했다. **목표 환경의 DB/인프라 검증이 남아 있으므로 무조건적인 배포 승인 상태는 아니다.**

## 1. 발견한 문제

* 일반 게시글/댓글 삭제와 비공개 첨부 접근에서 역할만으로 관리자 권한을 행사할 수 있었다.
* 공개 프로필에 이메일을 출력하고 전체 UserDTO를 조회했으며 비활성 계정도 대상이 될 수 있었다.
* 사용자 누적 저장 quota와 파일시스템 여유 공간 검사가 없었다. 두 업로드 필드/파일 처리 중간 실패에 orphan이 생길 수 있었고, DB 완료 뒤 redirect 실패가 저장된 첨부를 삭제할 수도 있었다.
* HTTPS URL과 Secure=false 조합을 허용했다. 알림·쪽지 GET이 읽음 상태를 변경했다.
* 실제 기능에서 읽지 않는 LOCAL_USERS 캐시가 민감 UserDTO를 보유했다. 비밀번호 최대 길이가 없었다.
* 주요 DAO 인덱스, 기존 평문 계정 사전 점검, 고정 runtime·영속 볼륨·복구·최소 배포 패키지·Maven 테스트가 부족했다.

## 2. 실제 수정한 파일

전체 파일 목록은 이 문서 하단 **변경 파일 명세**에 기록했다. 사전 원인/대상 계획은 [PREDEPLOY-ANALYSIS.md](PREDEPLOY-ANALYSIS.md), 운영 절차는 [PRODUCTION.md](../deploy/gcp-test/PRODUCTION.md)다. 기존 .git은 유효한 Git 저장소가 아니어서 시작 시 파일 SHA256과 기존 source archive를 대조했다. CSS 변경은 0개다.

## 3. 각 수정의 이유

|수정 영역|이유와 결과|
|---|---|
|BoardController, CommentService|일반 삭제는 작성자 ID를 DAO에 전달하며 관리자 분기를 제거. 수정은 업로드 저장 전에 소유권 검사|
|AdminCommentController, detail.jsp|관리자 댓글 삭제를 /admin/comments/delete로 분리하고 기존 AdminFilter/CSRF 통과 및 감사 기록. 기존 관리자 게시글 삭제 endpoint 유지|
|AuthorizationUtil, AdminFilter, 파일 controllers|공통 10분 fresh 검증 사용. 미래 timestamp도 거부. 비공개 첨부 관리자 예외는 현재 ACTIVE 관리자와 fresh 세션을 모두 요구|
|PublicProfileDTO 및 social 계층/JSP|userId/nickname/bio/profileImagePath만 사용. 공개 SELECT에서 비공개 필드를 제거하고 ACTIVE 조건 적용. 메시지/방명록 INSERT 때도 대상 ACTIVE 재확인. 비활성 프로필 이미지 조회 차단|
|UploadBudget, BoardDAO, 업로드 controllers|500 MiB 기본 quota, 1 GiB 최소 여유 공간 설정. 저장 전 검사와 DB row lock 후 quota 재검사. 부분 파일 포함 cleanup, commit 이후 응답 실패에서는 파일 보존|
|AbuseFilter, RateLimitService, web.xml|가입 5회/10분, 업로드 변경 20회/10분. multipart 파싱 전 제한. 단일 JVM 업로드 변경 동시 실행 1개, 경쟁 시 429. 기존 login limiter 정책 유지|
|AppConfig, AppContextListener|HTTPS 공개 URL + Secure=false는 DB 연결 검사보다 먼저 시작 실패. HttpOnly/SameSite 유지|
|UserController, MessageController, common.js|GET에서 읽음 UPDATE 제거. /notifications/read 재사용 및 /messages/read POST 추가. 화면 진입 후 기존 CSRF token으로 POST해 사용성 유지|
|AuthService 및 캐시 호출부|LOCAL_USERS와 sync/remove/status helper 제거. 기존 DB 기반 상태 확인 유지|
|PasswordUtil, 인증/사용자 service, JSP|모든 비밀번호 인증 입력 최대 128, 신규 비밀번호 최소 8 유지. truncate 없음. PBKDF2 및 legacy login migration 유지|
|SQL, pom, 배포 설정/스크립트|추가 인덱스, COUNT 점검, JDBC pin/provided 유지, Nginx/Tomcat 하드닝, 운영 복구와 최소 패키징, 테스트 추가|

## 4. DB migration 내용

`src/main/resources/sql/predeploy-indexes.sql`: 11개 비고유 인덱스를 추가한다. 게시판/상태/고정/날짜, 작성자/날짜, 댓글 게시글/상태/날짜 및 부모 ID, 첨부 저장명·게시글·업로더, 메시지/알림 수신자·읽음·날짜, 방명록 owner/date, 신고 상태/date. 기존 동일 컬럼 인덱스는 재사용한다. 신규 설치 schema에도 같은 인덱스를 반영했다.

DROP/재생성/기존 데이터 변경은 없다. Oracle DDL implicit commit과 이름 충돌 가능성 때문에 백업 후 maintenance window에서 실행한다. SQL과 schema의 정적 일치 테스트는 통과했으나 **실제 Oracle 실행과 두 번째 적용 검증은 미실행**이다.

`verify-legacy-passwords.sql`은 비-PBKDF2/null PASSWORD_HASH의 COUNT만 출력하고 0보다 크면 경고와 SQL 오류 종료를 한다. 실제 값은 출력하지 않는다. 운영 COUNT는 이 작업에서 조회하지 않았다.

## 5. 보안상 달라진 동작

* ADMIN/SUPER_ADMIN도 일반 게시글·댓글 삭제 URL로 타인의 데이터를 삭제할 수 없다. 타인 댓글 삭제 버튼은 관리자 URL로 전송된다. 재인증이 만료되면 인증 후 원래 화면에서 삭제를 다시 시도해야 하며, 인증 과정에서 삭제 POST를 자동 재실행하지 않는다.
* 비공개 첨부의 관리자 접근에는 fresh step-up이 필요하다. 공개 파일 및 작성자 경로는 기존 정책을 유지한다.
* 공개 프로필 이메일이 사라지고 정지/탈퇴 계정은 공개 조회·메시지/방명록 대상에서 제외된다. 관리자/마이페이지 개인정보 조회는 유지한다.
* quota·여유 공간·rate 제한 초과는 업로드를 거부한다. 기존 파일당/요청당 한도와 MIME/signature/UUID/path 보호를 유지한다.
* GET만 호출해도 알림/쪽지가 읽음 처리되던 동작이 사라졌다. JavaScript가 차단되거나 POST가 실패하면 읽지 않음 상태가 남는다.
* 129자 이상 비밀번호는 자르지 않고 거부한다. 기존 129자 이상 계정은 재설정 절차가 필요하다.
* WAR에는 SQL 자체를 포함하지 않고 운영 SQL만 배포 폴더에 별도로 제공한다.

## 6. Maven build/test 결과

|검증|결과|
|---|---|
|최종 mvn clean test|성공, 33 tests / 0 failures / 0 errors / 0 skipped|
|최종 mvn clean package|성공, 동일 33 tests 통과 및 WAR 생성|
|Tomcat 11.0.26 Jasper 전체 JSP compile|성공, 오류 0건|
|node --check common.js|성공|
|web.xml XML 파싱, password maxlength, GET 읽음 호출 제거, cache 제거|통과|
|새 SQL 및 사용자 입력 직접 문자열 결합 검색/검토|새 입력 직접 결합 없음. 값은 PreparedStatement bind. migration 동적 DDL 인자는 파일 내 고정 상수|
|JSP 출력/신규 POST/path 검사|기존 HTML/attribute escaping 유지. 새 action은 고정 경로. POST는 전역 CSRF, 관리자 endpoint는 /admin/*, UUID/safeResolve 유지|
|WAR 구조|JDBC/Servlet API jar, SQL/seed, 개발 metadata 없음|
|최소 배포 패키지 secret scan|검출 0건; SHA256 manifest 생성 성공|

실행 JDK는 로컬 **25.0.2**다. 테스트는 Mockito 기반 servlet/service/DAO 경계 회귀 테스트이며 실제 Oracle 통합/E2E 시험을 대신하지 않는다. 파일 테스트는 임시 디렉터리에서 실제 쓰기/정리를 수행한다. 정상/실패 로그인, session rotation, ACTIVE 차단, ADMIN/SUPER_ADMIN step-up, 소유권, CSRF, 첨부 IDOR, 파일 허용/거부, public DTO/query, GET 부작용, 비밀번호 최대 길이, quota/디스크/rollback/부분 업로드 및 commit 후 응답 실패를 검증했다. 테스트의 고의 upload 실패 로그는 예상된 시나리오다.

최종 배포 폴더: `dist/production-20260928-094447` (WAR/config/운영 SQL 및 SHA256.csv).

WAR SHA256: `84c64363a68c47617a963e0dfec366bd592b706b28e3bc03b209c77028db7f29`.

## 7. 수동 테스트가 필요한 항목

* Temurin 25.0.4.1+1 / Tomcat 11.0.26 / Oracle Free 23.26.3 / ojdbc11 23.26.3.0.0 실제 연결, JNDI, CRUD, 재시작.
* Oracle migration 두 번 실행, 실행 계획, 기존 데이터 수·FK·sequence·첨부 호환, legacy COUNT=0.
* 두 사용자와 관리자 계정으로 실제 브라우저 소유권/CSRF/step-up 만료 및 재인증 후 moderation/감사 로그 확인.
* 동시 업로드·DB 장애·디스크 부족·JVM 강제 종료 후 파일/DB 일관성 확인.
* Linux nginx -t/systemd-analyze verify, TLS/쿠키/포트/health 차단, 볼륨 미마운트 시 기동 차단, 별도 VM 복원 훈련.

## 8. 아직 남은 위험

* Oracle 공식 인증 표에서 이 ojdbc11은 JDK 11/21 인증이고 JDK 25 인증은 ojdbc17에 명시되어 있다. 요청대로 ojdbc11 provided를 유지했으므로 운영 조합 검증이 필요하다. 공식 출처와 runtime pin 근거는 PRODUCTION.md에 링크했다.
* Oracle registry의 정확한 23.26.3 이미지 태그/digest는 확인하지 못했다. 추측한 값을 넣지 않고 배포 전에 digest 설정을 요구한다.
* 세션 UserDTO의 passwordHash는 기존 비밀번호 변경/관리자 재인증 의존성 때문에 유지했다. SessionPrincipal 및 DB 기반 비밀번호 검증으로 분리하는 후속 작업이 필요하다. JVM LOCAL_USERS 캐시는 제거했다.
* Board/search는 전체 결과 조회를 유지한다. 페이지 UI가 없는 상태에서 OFFSET/FETCH를 넣으면 결과가 잘리므로 이 작업에서 전환하지 않았다. 데이터 증가 전 pagination·부하 테스트가 필요하다.
* single-node limiter/semaphore, 임시 multipart 공간, 디스크 검사 경쟁, 비정상 종료 orphan, 파일 cleanup 실패 및 보상 트랜잭션의 원자성 한계가 남는다. 운영 quota는 현재 DB에 등록된 첨부 합계이고 orphan/프로필은 별도 관리한다.
* 기존 source/개발 ZIP에 개발용 seed password hash가 있다: `src/main/resources/sql/scuredweb-seed.sql`, `create-users-3-6.sql`, `dist/scuredweb-current-full-source.zip` 내부 동일 파일. 값은 출력하지 않았고 최종 WAR/package에는 없다. 스캐너의 다른 seed 경고는 parameterized DAO/verification INSERT 위치다. 전체 개발 ZIP은 배포 금지다.
* 휴리스틱 secret scan은 미검출을 보장하지 않는다. 운영 비밀번호/API key/private key는 외부 secret 저장소에서 관리하고 별도 검토한다.
* 자동 읽음 POST 실패는 console warning만 남긴다. 현재 화면은 유지되며 새로고침 시 재시도한다. 보안정책에 따라 명시적 읽음 버튼을 추가하는 UX는 후속 검토할 수 있다.
* 기존 게시글 조회수의 GET UPDATE와 댓글 하위 답글 삭제 정책은 이 요청의 알림/쪽지 읽음 분리 범위 밖이라 유지했다. 비밀번호 최소 길이를 강화하는 정책도 별도 검토 대상이다.

## 9. 클라우드 배포 전 최종 체크리스트

- [x] 일반 삭제 관리자 우회 제거, 관리자 endpoint·fresh 첨부 검증
- [x] 공개 DTO/ACTIVE 필터, cache 제거, 128자 제한
- [x] quota/free-space/rate/cleanup, CSRF POST 읽음
- [x] 추가 SQL·신규 schema·운영 하드닝·백업/복원 문서
- [x] clean test/package, 33 tests, Jasper 11.0.26 compile, 최소 패키지/검출 0/SHA256
- [ ] 목표 JDK/Oracle/JDBC 실제 통합 검증 및 인증 조합 결정
- [ ] Oracle image digest 확정, legacy COUNT=0, backup 후 migration 실행/재실행 검증
- [ ] nginx/systemd/TLS/Secure cookie/health/firewall 확인
- [ ] persistent mounts·외부 백업·복원 훈련
- [ ] staging 브라우저 회귀·동시 업로드/장애·성능 검증 후 공개

## 변경 파일 명세


- `deploy/gcp-test/PRODUCTION.md`
- `deploy/gcp-test/README.md`
- `deploy/gcp-test/conf/nginx-redweb.conf`
- `deploy/gcp-test/conf/oracle-volume.conf.example`
- `deploy/gcp-test/conf/persistent-volumes.conf.example`
- `deploy/gcp-test/conf/redweb-https.env.example`
- `deploy/gcp-test/conf/remote-ip-valve.xml`
- `deploy/gcp-test/results/README.md`
- `docs/PREDEPLOY-ANALYSIS.md`
- `docs/PREDEPLOY-REPORT.md`
- `pom.xml`
- `scripts/package-production.ps1`
- `scripts/scan-deployment-secrets.py`
- `src/main/java/com/scuredweb/admin/controller/AdminCommentController.java`
- `src/main/java/com/scuredweb/admin/controller/AdminVerificationController.java`
- `src/main/java/com/scuredweb/admin/service/AdminManagementService.java`
- `src/main/java/com/scuredweb/auth/controller/AuthController.java`
- `src/main/java/com/scuredweb/auth/service/AuthService.java`
- `src/main/java/com/scuredweb/board/controller/BoardController.java`
- `src/main/java/com/scuredweb/board/dao/BoardDAO.java`
- `src/main/java/com/scuredweb/comment/service/CommentService.java`
- `src/main/java/com/scuredweb/common/config/AppConfig.java`
- `src/main/java/com/scuredweb/common/filter/AbuseFilter.java`
- `src/main/java/com/scuredweb/common/filter/AdminFilter.java`
- `src/main/java/com/scuredweb/common/listener/AppContextListener.java`
- `src/main/java/com/scuredweb/common/security/AuthorizationUtil.java`
- `src/main/java/com/scuredweb/common/security/PasswordUtil.java`
- `src/main/java/com/scuredweb/common/security/RateLimitService.java`
- `src/main/java/com/scuredweb/common/security/UploadBudget.java`
- `src/main/java/com/scuredweb/file/controller/FileDownloadController.java`
- `src/main/java/com/scuredweb/file/controller/FileImageController.java`
- `src/main/java/com/scuredweb/social/controller/MessageController.java`
- `src/main/java/com/scuredweb/social/controller/ProfileController.java`
- `src/main/java/com/scuredweb/social/controller/ReportController.java`
- `src/main/java/com/scuredweb/social/dao/SocialDAO.java`
- `src/main/java/com/scuredweb/social/dto/PublicProfileDTO.java`
- `src/main/java/com/scuredweb/social/service/SocialService.java`
- `src/main/java/com/scuredweb/user/controller/UserController.java`
- `src/main/java/com/scuredweb/user/dao/UserDAO.java`
- `src/main/java/com/scuredweb/user/service/UserService.java`
- `src/main/resources/sql/predeploy-indexes.sql`
- `src/main/resources/sql/scuredweb-schema.sql`
- `src/main/resources/sql/verify-legacy-passwords.sql`
- `src/main/webapp/WEB-INF/views/admin/verify.jsp`
- `src/main/webapp/WEB-INF/views/auth/login.jsp`
- `src/main/webapp/WEB-INF/views/auth/register.jsp`
- `src/main/webapp/WEB-INF/views/auth/reset-password.jsp`
- `src/main/webapp/WEB-INF/views/board/detail.jsp`
- `src/main/webapp/WEB-INF/views/user/messages.jsp`
- `src/main/webapp/WEB-INF/views/user/mypage.jsp`
- `src/main/webapp/WEB-INF/views/user/profile.jsp`
- `src/main/webapp/WEB-INF/web.xml`
- `src/main/webapp/assets/js/common.js`
- `src/test/java/com/scuredweb/common/filter/PredeploySecurityTest.java`
