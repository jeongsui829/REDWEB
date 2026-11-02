# OCI 공개 배포 전 최종 통합 보완 결과

검증일: 2026-09-28. 이 보고서는 이번 요청 범위의 로컬 코드·설정·회귀 검증 결과다. 실제 OCI 배포, 운영 DB migration, TLS/부하/복구 시험은 수행하지 않았다.

## 1. 실제 현재 코드에서 재현/확인된 문제

수정 전 파일을 `.work-final/before.zip`과 SHA256 baseline으로 보존했다. 운영 침해나 OCI 장애 재현을 주장하지 않으며, 코드 경로 확인과 로컬 회귀 테스트를 구분한다.

| 요청 범위 | 수정 전 확인 | 처리 |
| --- | --- | --- |
| JNDI Context | root:tomcat 설치에 0600 사용: tomcat 읽기 불가 | 0640, 실제 UID 읽기 및 부모 traverse gate |
| 일반 탈퇴 | 관리자 역할 제한·현재 비밀번호 확인 없이 탈퇴 DAO 호출 | HTTP/서비스/DB MEMBER guard, 잠근 현재 hash 재인증 |
| 보안 기록 | 탈퇴가 RW_ADMIN_ACTION_LOG/RW_REPORT/인증 이력 삭제 | userId anchor 및 보안 기록 보존, 개인정보 익명화 유지 |
| 신고 정지 | 보고 처리의 직접 STATUS 변경이 관리자 보호 경로 우회 | MEMBER만 자동 정지, 관리자 신고는 무정지 처리·감사 |
| 세션 철회 | 비밀번호 변경/reset 후 다른 세션의 credential freshness 비교 없음 | AUTH_VERSION 증가 및 protected request 비교 |
| 이메일 | 비밀번호 확인 없이 변경, 새 이메일에 기존 verified 상태 잔존 가능 | row lock 재인증, EMAIL_VERIFIED_YN=N, 미사용 reset token 폐기 |
| 게시글 직접 ID | 댓글/reaction/view 등 PUBLIC 조건 누락 | 공통 post lock, PUBLIC 및 활성 게시판 검사, 조건부 UPDATE |
| 비활성 게시판 | findBoards는 이미 ACTIVE 필터 있으나 direct code lookup/insert 누락 | public lookup/list/search/insert 모두 활성 조건 |
| rate limit | login pair 중심, 일부 multipart만 IP 제한 | login/step-up IP+account+pair 독립 budget, 쓰기 user/IP budget |
| 전역 쓰기 잠금 | multipart Semaphore(1) | 구성 가능한 bounded concurrency 기본2, 허용1~16 |
| JNDI fallback | JNDI 실패 후 외부 direct JDBC 선택 가능 | REQUIRE_JNDI=true면 lookup/connection 실패로 중단 |
| 운영 가입/업로드 | HTTPS에서도 가입 설정 생략 허용, upload path fallback·늦은 parsing | startup 명시값·absolute path·숫자 검증 |
| temp/session 자원 | Nginx 기본 temp 및 JSP implicit session 의존 | bounded scratch/mount 절차, 측정 기반 Manager cap |
| 비밀번호 | PBKDF2 210000, 신규 최소8, plaintext fallback 상시 | 신규600000, 성공 로그인 rehash, 신규 최소15, 운영 legacy=false |
| 관리자 audit | 게시글·댓글·신고·공지 일부가 business commit 뒤 별도 audit | 동일 Connection/transaction, audit 실패 및 runtime 예외 rollback |
| 비공개 첨부 | owner/admin 응답에 별도 cache 금지 없음 | private,no-store/no-cache/no-referrer; PUBLIC 정책 유지 |
| 메서드/헤더 | CSRF safe에 TRACE, form-action/Permissions-Policy 미설정 | TRACE/CONNECT 405, Connector allowTrace=false, 헤더 추가 |
| 배포 구조 | GCP runbook 혼용 가능, Docker restart가 mount gate 우회 가능 | OCI 전용 runbook/units, restart=no 및 mount→Oracle→ready→Tomcat |

기존 관리자 fresh step-up, CSRF/XSS/CSP 기본 정책, PreparedStatement, upload MIME/signature/UUID/path, PublicProfileDTO, 댓글 soft-delete/답글 보존, online-users getSession(false), canonical Host/SNI rejection은 유지했다. HSTS는 주석 상태로 유지했다. 이미 있는 NoOp POST 안내를 SMTP 구현으로 대체하지 않았고 production GET/POST recovery 전체 unavailable 경계를 추가했다.

## 2. 수정한 파일 전체 목록

아래는 baseline 대비 수정/신규 파일 전부다. 산출물 dist/target 및 작업 로그는 소스 변경 목록에 넣지 않았다. 기존 테스트 파일과 CSS는 SHA256 비교로 변경 없음을 확인했다.

- `deploy/gcp-test/PRODUCTION.md`
- `deploy/gcp-test/README.md`
- `deploy/gcp-test/conf/nginx-redweb.conf`
- `deploy/gcp-test/conf/redweb-https.env.example`
- `deploy/oci/README.md` (신규)
- `deploy/oci/conf/check-oracle-container.sh` (신규)
- `deploy/oci/conf/check-redweb-runtime.sh` (신규)
- `deploy/oci/conf/connector.xml.example` (신규)
- `deploy/oci/conf/journald-redweb.conf` (신규)
- `deploy/oci/conf/logrotate-redweb` (신규)
- `deploy/oci/conf/nginx-mount.conf` (신규)
- `deploy/oci/conf/nginx-redweb.conf` (신규)
- `deploy/oci/conf/oracle-free-ready.service` (신규)
- `deploy/oci/conf/oracle-free.service` (신규)
- `deploy/oci/conf/redweb-runtime.env.example` (신규)
- `deploy/oci/conf/remote-ip-valve.xml` (신규)
- `deploy/oci/conf/scuredweb.xml` (신규)
- `deploy/oci/conf/tomcat.service` (신규)
- `deploy/oci/conf/wait-oracle-free.sh` (신규)
- `deploy/oci/sql/20260928-auth-version.sql` (신규)
- `deploy/oci/sql/runtime-grants-DRAFT.sql` (신규)
- `deploy/oci/sql/verify-legacy-passwords.sql` (신규)
- `docs/FINAL-OCI-REPORT.md` (신규)
- `docs/OCI-FOLLOWUPS.md` (신규)
- `scripts/package-oci-production.ps1` (신규)
- `scripts/package-production.ps1`
- `scripts/verification/PasswordBenchmark.java` (신규)
- `src/main/java/com/scuredweb/admin/controller/AdminCommentController.java`
- `src/main/java/com/scuredweb/admin/controller/AdminManagementController.java`
- `src/main/java/com/scuredweb/admin/controller/AdminVerificationController.java`
- `src/main/java/com/scuredweb/admin/dao/AdminAuditDAO.java`
- `src/main/java/com/scuredweb/admin/service/AdminManagementService.java`
- `src/main/java/com/scuredweb/auth/controller/AuthController.java`
- `src/main/java/com/scuredweb/auth/dao/AuthDAO.java`
- `src/main/java/com/scuredweb/auth/service/AuthService.java`
- `src/main/java/com/scuredweb/board/dao/BoardDAO.java`
- `src/main/java/com/scuredweb/board/service/BoardService.java`
- `src/main/java/com/scuredweb/comment/dao/CommentDAO.java`
- `src/main/java/com/scuredweb/common/config/AppConfig.java`
- `src/main/java/com/scuredweb/common/config/DBConfig.java`
- `src/main/java/com/scuredweb/common/filter/AbuseFilter.java`
- `src/main/java/com/scuredweb/common/filter/AdminFilter.java`
- `src/main/java/com/scuredweb/common/filter/AuthFilter.java`
- `src/main/java/com/scuredweb/common/filter/CsrfFilter.java`
- `src/main/java/com/scuredweb/common/filter/FileSessionFilter.java` (신규)
- `src/main/java/com/scuredweb/common/filter/SecurityHeadersFilter.java`
- `src/main/java/com/scuredweb/common/listener/AppContextListener.java`
- `src/main/java/com/scuredweb/common/security/AccountPolicy.java` (신규)
- `src/main/java/com/scuredweb/common/security/AuthorizationUtil.java`
- `src/main/java/com/scuredweb/common/security/PasswordUtil.java`
- `src/main/java/com/scuredweb/common/security/PostAccessPolicy.java` (신규)
- `src/main/java/com/scuredweb/common/security/RateLimitService.java`
- `src/main/java/com/scuredweb/common/security/UploadBudget.java`
- `src/main/java/com/scuredweb/common/util/UploadPathUtil.java`
- `src/main/java/com/scuredweb/file/controller/FileDownloadController.java`
- `src/main/java/com/scuredweb/file/controller/FileImageController.java`
- `src/main/java/com/scuredweb/file/dao/FileDAO.java`
- `src/main/java/com/scuredweb/social/dao/SocialDAO.java`
- `src/main/java/com/scuredweb/user/controller/UserController.java`
- `src/main/java/com/scuredweb/user/dao/UserDAO.java`
- `src/main/java/com/scuredweb/user/dto/UserDTO.java`
- `src/main/java/com/scuredweb/user/service/UserService.java`
- `src/main/resources/sql/migrations/20260928-auth-version.sql` (신규)
- `src/main/resources/sql/scuredweb-schema.sql`
- `src/main/webapp/WEB-INF/views/auth/login.jsp`
- `src/main/webapp/WEB-INF/views/auth/reset-password.jsp`
- `src/main/webapp/WEB-INF/views/common/header.jsp`
- `src/main/webapp/WEB-INF/views/home/index.jsp`
- `src/main/webapp/WEB-INF/views/user/mypage.jsp`
- `src/main/webapp/WEB-INF/web.xml`
- `src/test/java/com/scuredweb/common/filter/FinalPolicyTest.java` (신규)
- `src/test/java/com/scuredweb/recheck/FinalDatabaseTest.java` (신규)
- `src/test/java/com/scuredweb/recheck/FinalFileTest.java` (신규)

## 3. DB migration 내용

`src/main/resources/sql/migrations/20260928-auth-version.sql` 및 OCI 배포 복사본은 `ALTER TABLE RW_USER ADD (AUTH_VERSION NUMBER DEFAULT 1 NOT NULL)`만 추가한다. 기존 user/hash/data를 재생성하지 않는다. 신규 schema에도 같은 컬럼을 추가했다. 기존 DB에는 전체 schema가 아닌 migration만 owner로 1회 적용하며 새 WAR보다 먼저 적용한다. 자동 DB 연결·적용은 하지 않았다.

password change는 조건부 UPDATE 한 문장에서 hash와 version을 함께 변경한다. reset은 token row 잠금, hash/version 증가, token 사용 처리를 한 transaction에 수행한다. 기존 낮은 iteration hash의 재해시는 기존 hash 조건을 가진 CAS로 동시 reset을 덮어쓰지 않으며 AUTH_VERSION을 바꾸지 않는다. 개발 legacy 평문 migration도 hash 저장 상태가 바뀌었으면 실패한다.

최소권한 REDWEB_OWNER/REDWEB_APP 설계는 `deploy/oci/sql/runtime-grants-DRAFT.sql`의 주석 초안이다. USER_* metadata 조회의 owner 의존을 해결·검증하지 않은 상태에서 자동 전환하지 않는다. 실제 운영 DB는 변경하지 않았다.

## 4. 보안 정책 변경

- 일반 탈퇴는 MEMBER + 현재 비밀번호를 요구한다. DB role/status를 잠근 상태에서 재확인한다. HTTP는 항상 password overload를 사용한다. 기존 fixture 호환용 무비밀번호 서비스/DAO overload는 deprecated이며 HTTPS production에서 거부한다. deleteUserHard는 기존 오프라인 fixture 정리용으로 남고 애플리케이션 탈퇴에서 호출하지 않는다.
- 계정 상태 guard를 AccountPolicy로 모으고 관리자 행들을 정렬해 잠근 뒤 마지막 ACTIVE 관리자·sole SUPER_ADMIN·자기 정지를 검사한다. report 경로는 관리자 상태를 변경하지 않는다. 현재 앱에 새 role-change endpoint는 추가하지 않았다.
- 비밀번호 변경한 현재 세션도 즉시 invalidate한다. 다른 기존 세션은 다음 AuthFilter/AdminFilter 보호 요청에서 version 차이로 invalidate하며 step-up 상태도 폐기된다. 비공개 첨부 요청의 기존 로그인 세션도 DB version/status를 검사한다. 이미 실행 중인 요청의 소급 취소나 컨테이너 전체 세션 객체 즉시 열거 삭제는 하지 않는다.
- 이메일만 실제 변경되면 비밀번호가 필요하다. 일반 nickname/bio 변경에는 새 비밀번호 요구를 넣지 않았다. verification=N과 이메일 변경은 원자적이며 recovery 대상은 verified 이메일로 제한한다.
- login/admin-verify 요청은 비밀번호 해시 작업 전에 IP/account/pair budget을 각각 소비한다. 성공 로그인으로 abuse budget이 지워지지 않는다. 기존 실패 차단도 유지한다. comment/message/report/board/profile/guestbook에 사용자 및 IP 제한을 적용한다.
- 신규 password 15~128자, 기존 8~14자 hash 로그인 허용. 신규 PBKDF2-HMAC-SHA256 600000; 낮은 iteration hash는 성공 로그인 후 갱신. production 기본 plaintext 비교 금지, env 예시 false. [OWASP Password Storage 기준](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)을 참고했으며 실제 OCI 비용은 별도 benchmark 대상이다.
- HTTPS에서는 registration 명시 및 absolute upload root가 필수다. JNDI-only 예시 true. 잘못된 quota/min-free/concurrency/boolean은 시작 단계에 거부한다.
- audit/security 기록은 회원 개인 콘텐츠와 별도 보존한다. 상세 사유/IP 등 기록에 남은 개인정보의 기간·접근·삭제 요청 정책은 운영 책임자가 결정해야 하며 임의 purge를 추가하지 않았다.

## 5. 기존 기능에 미치는 영향

현재 비밀번호 입력이 필요한 탈퇴/이메일 변경 필드와 회원가입 링크 조건부 표시만 기존 폼 스타일로 추가했다. CSS 디자인 및 기존 URL은 유지했다. 비밀번호 변경 뒤 로그인 화면으로 이동한다. 관리자 일반 탈퇴와 숨김/삭제 글·비활성 게시판 직접 조작은 거부한다. 비공개 첨부의 브라우저 캐시와 오래된 인증 세션 접근은 제한된다.

기존 회원은 기존 비밀번호로 로그인할 수 있다(운영 plaintext는 제외). 온라인 tracking 및 이름 노출 정책, 일반 댓글 삭제 placeholder와 B/C 답글 보존은 유지했다. public JSP guest session 의존도 유지하므로 세션 무생성이 전 사이트에 보장된다고 주장하지 않는다. OCI Context cap·timeout·heap gate를 제공했다.

DB commit 이후 파일 cleanup은 기존 보상/정리 방식으로 분리했다. audit 저장 실패 시 업무 DB 변경도 rollback한다. 신규 concurrency 기본2가 업로드 throughput/메모리/동시 사용자 quota에 미치는 영향은 staging 부하 시험이 필요하다.

## 6. 테스트 총 개수 / 실패 / 오류 / skip

`mvn -B clean test`: **120개 / 실패 0 / 오류 0 / skip 0**. `clean package`에서도 같은 120개 통과.

기존 33 + 24 + 9 + 2 = 68개 테스트 파일을 수정하지 않았다. 신규 FinalPolicyTest/FinalDatabaseTest/FinalFileTest에서 52개를 추가했다. 실제 JDBC H2 Oracle mode로 계정·신고·댓글·비공개 상태·audit rollback을 검증하고 mock Servlet/JNDI로 session/filter/headers/config를 검증했다. H2가 Oracle locking/MERGE/CONNECT BY 동작을 보증하지 않는다.

주요 신규 coverage: MEMBER 비밀번호 탈퇴, ADMIN/SUPER_ADMIN 거부, 기록 보존, MEMBER 신고 정지 및 관리자 무정지, password change/reset version 증가 및 재로그인, 두 이전 세션/관리자 step-up invalidate, 이메일 verified 초기화, HIDDEN/DELETED/missing post mutation 거부 및 PUBLIC 성공, inactive board 접근/작성 거부, login 3 bucket, comment/message/report abuse, JNDI fallback 금지, 운영 registration/upload fail-closed, plaintext 금지/해시 target/rehash/길이, 비공개 cache, TRACE/CONNECT 및 Context/boot static 검사.

검증 로그는 `.work-final/clean-test.log`, `.work-final/clean-package.log`, `.work-final/jsp.log`에 있다. JS node --check, XML parsing(pom/web/Context/Connector/Valve), PowerShell packaging parser 검증도 수행했다.

## 7. Maven package 결과

`scripts/package-oci-production.ps1` 내부 `mvn -B clean package`: **BUILD SUCCESS**. 최종 묶음: `dist/production-oci-20260928-185223`. 비밀정보 scanner 결과 `[]`, WAR 제외 항목 검사 통과.

pom.xml의 ojdbc17 23.26.3.0.0 provided는 변경하지 않았다. 최종 WAR의 WEB-INF/lib는 JSTL 하나이며 ojdbc/H2/JUnit/Mockito/Servlet API가 없다. source archive/test resources/SQL schema를 WAR에 넣지 않았다. 배포 비밀정보 scanner 결과와 허용목록 패키징 검증을 통과했다. 패키지는 placeholder 예시 파일이며 실제 운영 secret은 넣지 않았다.

## 8. JSP compile 결과

최종 WAR 전개본을 Tomcat Jasper 11.0.26으로 컴파일: **오류 0건**.

로컬 Java 25.0.2 및 Tomcat Jasper 11.0.26을 사용했다. 실제 운영 목표 JDK 패치 버전에서 동일 결과가 나는지는 staging gate다.

## 9. production package SHA256

- WAR SHA256: `FC02F56F9D3708B4F64F36547F7202348ECDAED7356A3C34243B89170995215A`
- SHA256.csv 자체 SHA256: `67EA60283D4788279EA939F084EDBBE49902463B5E9205E911F3D651B5229C6E`
- manifest의 **20개 파일 해시 모두 일치**. 이전 생성 실패/중간 산출물 대신 위 최종 묶음만 사용한다.

## 10. Oracle에서만 검증 가능한 항목

- AUTH_VERSION additive DDL의 기존 데이터/DEFAULT/NOT NULL, 재실행 실패, 실제 계정 schema 및 JNDI pool 전환.
- SELECT FOR UPDATE 잠금 순서, 동시 관리자 정지/댓글 작성·삭제/탈퇴/업로드 경쟁과 deadlock/rollback 처리.
- Oracle MERGE reaction, CONNECT BY subtree moderation, Oracle metadata FK allowlist 및 실제 audit 원자성.
- reset token/email change/password change 경합, version 증가 후 이전 세션 거부, 새 로그인, DB 장애 시 fail-closed.
- owner/app grant/synonym/ALL_* metadata 전략 및 실제 실행 계획/index 비용. H2 결과만으로 적용하지 않는다.

## 11. OCI staging에서 사람이 직접 검증해야 하는 항목

`deploy/oci/README.md`의 release gate를 수행한다. 실제 tomcat UID의 Context 0640 읽기/부모 traverse, JNDI-only lookup/connection 실패, 실제 JDK/Tomcat/외부 JDBC classloader, upload/scratch/Oracle mount, restart=no, mount 누락 reboot 시 Oracle/Tomcat 미기동, 복구 순서를 확인한다.

DNS/정상·비정상 Host/SNI/HTTP→HTTPS/인증서 갱신/RemoteIpValve 실제 client IP, 직접 8080/1521/5500 외부 차단, TRACE/CONNECT, 쿠키·비공개 cache, 가입 링크·서버 차단 및 recovery unavailable를 확인한다. TLS gate 이후에만 HSTS를 활성화한다.

PBKDF2 CPU, heap/GC/active session cap, NAT 사용자 rate limit, 느린 multipart/대용량 동시 요청의 Nginx/Tomcat temp 위치 및 bytes/inodes 상한, disk-full·DB outage·cleanup 실패를 시험한다. Oracle+업로드 백업을 별도 VM에 복구하여 RPO/RTO 및 데이터/파일 정합성을 검증한다. 로그 회전/journald/Docker log 상한과 5xx/429/실패 로그인 집계를 확인한다.

이 항목들은 **미검증**이며 이번 작업에서 OCI 시스템 설정이나 운영 DB를 실행·변경하지 않았다.

## 12. 의도적으로 이번 작업에서 보류한 항목과 이유

`docs/OCI-FOLLOWUPS.md`에 실제 unbounded query 목록, Oracle index 후보, OFFSET/FETCH/보조 정렬, MyPage section별 로딩, header unread 최소화 계획을 남겼다. 전체 pagination 변경은 현 UI·정렬·카운트 계약을 바꿀 수 있어 보류했다.

owner/app 자동 전환(Oracle metadata 호환 필요), email provider(외부 연결 임의 추가 금지), breached-password 외부 조회/denylist(출처·운영 정책 필요), 전체 익명 JSP session 제거(guest/CSRF/flash 의존), 분산 limiter/영속 event pipeline(현재 단일 JVM), naive global SUM quota(경쟁조건), retention purge(정책 미정)는 구현하지 않았다. 측정 근거 없는 세션 cap 숫자도 정하지 않았으며 필수 placeholder와 startup 검사 및 튜닝 절차를 제공했다.
