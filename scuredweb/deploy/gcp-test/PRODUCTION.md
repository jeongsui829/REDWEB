# 공개 운영 배포와 복원

2026-09-28 기준. 기존 README는 제한된 테스트 절차이다. 공개 운영은 HTTPS Nginx 경로를 사용하고 IAP HTTP 설정과 섞지 않는다.

## 고정 런타임과 검증 범위

* Tomcat **11.0.26**: [공식 다운로드](https://tomcat.apache.org/download-11.cgi), [변경 내역](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html). Servlet 6.1/JSP 4.0과 현재 Jakarta 패키지가 일치한다. tar와 SHA512/signature를 함께 검증하고 사내 artifact 저장소에 보관한다.
* Eclipse Temurin **25.0.4.1+1**: [공식 release](https://github.com/adoptium/temurin25-binaries/releases). 대상 Linux/CPU artifact와 checksum을 고정한다. apt 사용 시 `apt-cache madison temurin-25-jdk`에서 이 upstream release에 해당하는 정확한 package version을 확인하고 `apt install temurin-25-jdk=<확인한 버전>`을 사용한다. 보안 패치는 검증 후 의도적으로 올린다. 서비스 JAVA_HOME, `java -version`, `mvn -version`이 같은 설치를 가리키는지 확인한다.
* Oracle AI Database 26ai Free **23.26.3 계열**: [Oracle registry](https://container-registry.oracle.com/ords/ocr/ba/database/free)에서 실제 플랫폼별 태그와 digest를 확인하고 `REDWEB_ORACLE_IMAGE=container-registry.oracle.com/database/free@sha256:<검증한 digest>`로 기록한다. 이 환경에서 registry 목록을 직접 확인하지 못했으므로 추측한 태그/digest는 제공하지 않는다. digest 확정 전 배포는 보류한다. 빈 볼륨에서 버전을 검증하고 기존 DB의 업데이트 지원 여부와 복구를 검증한 뒤 적용한다.
* `ojdbc17:23.26.3.0.0`은 pom에 고정하고 **provided**를 유지한다. [Oracle JDBC 다운로드/인증 표](https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html)에 JDK 17/19/21/25 인증이 명시된 artifact다. WAR에는 드라이버를 넣지 않고 `/opt/tomcat/lib/ojdbc17.jar` 하나만 설치한다. 기존 드라이버는 Tomcat 정지 후 파일명·버전을 확인하여 lib 밖의 백업 위치로 옮기고 재시작한다. 동시에 두 JDBC jar를 두지 않는다. 표준 java.sql/JNDI 코드 및 `oracle.jdbc.OracleDriver` 클래스명은 그대로 사용한다. OCI staging의 실제 연결/트랜잭션/재시작 시험은 별도로 수행한다.

`:latest`와 무제한 apt install은 편하지만 재생성 시 다른 binary가 설치된다. 버전과 checksum/digest를 릴리스 manifest에 저장하고 최신 패치를 정기적으로 검토한다. 기존 results의 JDK 25.0.2/Tomcat 11.0.24 기록은 과거 증거이므로 버전 문자열을 소급 수정하지 않는다.

## 네트워크와 서버

1. Nginx 설정은 `http {}` 아래 include하고 실제 도메인/인증서로 바꾼 뒤 `nginx -t`를 실행한다. 80은 HTTPS redirect만 한다. TLS 1.2/1.3, server_tokens off, IP별 10 req/s 및 burst 30, 연결 20을 적용한다. 서비스 부하에 맞게 측정 후 조정한다.
2. `/scuredweb/health`는 loopback 및 명시한 모니터링 CIDR만 허용한다. 인터넷에서 403인지 실제 확인한다. HSTS는 도메인·인증서·HTTPS 전환 검증 후 주석을 해제한다. includeSubDomains/preload는 기본 적용하지 않는다.
3. Tomcat connector는 `address="127.0.0.1" port="8080"`로 bind하고 외부 8080을 차단한다. RemoteIpValve는 신뢰한 loopback Nginx만 허용한다. Host 안에 제공한 ErrorReportValve (`showReport=false`, `showServerInfo=false`)를 적용한다.
4. 설치된 Tomcat의 manager, host-manager, examples, docs, 불필요한 ROOT 앱은 정확한 경로를 확인하여 제거한다. 제공 WAR만 배포한다. root 실행 금지; 기존 tomcat 사용자와 NoNewPrivileges/PrivateTmp/ProtectSystem을 유지한다.
5. Oracle Docker port는 `127.0.0.1:1521:1521`만 게시한다. cloud firewall에서도 1521/8080을 공개하지 않는다.
6. HTTPS env의 Secure=true, HttpOnly 및 SameSite=Lax를 실제 Set-Cookie에서 확인한다. HTTPS 공개 URL과 false 조합은 앱 시작이 실패한다. system property의 `scuredweb.secureCookie` 별칭이 redweb 값을 덮어쓸 수 있으므로 둘을 충돌시키지 않는다.

공개 URL의 설정 우선순위는 `-Dredweb.publicBaseUrl`, `REDWEB_PUBLIC_BASE_URL`, `app.publicBaseUrl`이다. 아예 지정하지 않으면 기존 개발 동작을 유지한다. 명시한 빈 값, 공백, 상대 URL, 잘못된 scheme/host/port, userinfo/query/fragment는 시작 오류다. 임의 보정으로 통과시키지 않으며 유효한 URL의 끝 `/`만 기존처럼 정규화한다. HTTP는 기존과 같이 loopback 개발 주소만 허용한다. 잘못된 값은 로그에 그대로 출력하지 않는다.

Nginx는 80의 알 수 없는 Host에 444를 반환하고 정상 Host만 고정된 `https://REDWEB_TEST_HOST`로 redirect한다. 443 default server는 `ssl_reject_handshake on`으로 미등록 SNI를 거부하고 정상 TLS server도 다른 HTTP Host를 거부한다. 모든 `REDWEB_TEST_HOST`를 실제 소문자 FQDN으로 교체한다. 배포 VM의 다른 enabled site와 중복 default_server가 없는지 확인한다. `ssl_reject_handshake`는 Nginx 1.19.4 이상이 필요하다([공식 문서](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_reject_handshake)).

기존 DNS-01 인증서 발급은 Host/default server 정책과 무관하게 유지된다. **인증서를 먼저 발급하고** HTTPS 설정을 설치한 다음 `nginx -t`/reload한다. 수동 DNS-01은 자동 갱신되지 않으므로 DNS provider hook을 구성하거나 만료 전에 갱신한다. HTTP-01로 전환한다면 정상 Host의 challenge location을 별도로 검증하거나 Nginx 중단 창에 standalone 방식을 사용해야 한다. 현재 server-level redirect에 location만 덧붙이면 HTTP-01 예외로 작동하지 않으므로 무작정 certbot nginx 자동수정과 혼합하지 않는다.

## 업로드와 남용 방어

이메일 verification provider가 없는 상태에서 공개 가입을 허용하면 여러 계정으로 per-user quota를 합산 우회할 수 있다. 운영 예시는 `REDWEB_PUBLIC_REGISTRATION_ENABLED=false`로 고정했다. system property `redweb.publicRegistrationEnabled`가 우선하고 미지정 개발 기본값은 true다. false에서는 GET/POST `/register`를 403으로 거부하고 service 진입도 차단하며, 기존 계정/관리자 로그인은 그대로 허용한다. true/false 이외 명시값은 시작 오류다. 신규 계정이 필요하면 비공개 접근 통제된 운영 창에서만 가입을 잠시 허용한 후 다시 닫는다.

전역 DB quota용 공유 잠금/예약 테이블은 이번 최소 변경 범위에서 추가하지 않았다. 단순 SUM 체크를 전역 방어라고 취급하지 않는다. OCI에서는 uploads를 root/DB와 분리한 **유한 용량의 전용 block volume/filesystem**에 mount하거나 해당 filesystem의 project quota를 설정하여 전체 저장량을 OS에서 제한한다. byte 한도뿐 아니라 inode 고갈과 Tomcat multipart temp도 모니터링한다. volume 전체 용량, 여유 공간 임계값, 알림 및 hard quota 도달 시 실패·cleanup을 staging에서 검증한다. 이 제한은 기존 per-user quota와 최소 여유 공간 검사를 대체하지 않는다.

`REDWEB_UPLOAD_QUOTA_BYTES=524288000` (사용자당 현재 저장된 첨부 500 MiB), `REDWEB_UPLOAD_MIN_FREE_BYTES=1073741824` (최소 여유 1 GiB). 대응 system property는 `redweb.upload.quotaBytes`, `redweb.upload.minFreeBytes`이며 system property가 우선한다. 0 quota는 새 첨부를 차단한다. 음수/비숫자는 오류다. 프로필은 기존 3 MiB 한 장 교체 정책과 disk 검사로 관리하며 첨부 quota 합계에는 포함하지 않는다.

저장 직전 요청 합계 quota 및 실제 FileStore usable space를 검사하고 첨부 INSERT transaction에서 RW_USER 행 잠금 후 합계를 재검사한다. 기존 10 MiB/10개 제한, signature/MIME, UUID, 외부 저장소, path 정규화와 보상 삭제를 유지한다. 이 JVM은 업로드 변경 요청을 한 번에 하나 처리하고 경쟁 요청은 429/Retry-After로 응답한다. IP별 가입 5회/10분, 업로드 변경 20회/10분이며 NAT 사용자는 제한을 공유한다. multipart 임시 파일도 Tomcat temp 공간을 소비하므로 해당 볼륨 모니터링과 프록시 제한이 필요하다.

in-memory rate limit과 semaphore는 노드 간 공유되지 않고 재시작 시 초기화된다. 다중 노드에서는 Redis/DB 기반 limiter, 공유 저장소 예약, 동일 upload root 정책이 추가로 필요하다. DB quota는 row lock으로 재검사하지만 디스크 공간 검사는 다른 프로세스의 동시 쓰기를 원자적으로 예약하지 않는다. 비정상 JVM 종료·파일 삭제 실패는 orphan을 남길 수 있으므로 DB SAVED_NAME 목록과 파일을 정기 대조하되 자동 무차별 삭제하지 않는다.

## 인덱스와 목록 성능

운영 DB에는 `sql/predeploy-indexes.sql`만 적용한다. 11개 비고유 인덱스를 추가하며 동일 컬럼 구성이 있으면 생성을 건너뛴다. 동일 이름에 다른 컬럼이 있으면 오류로 중단하므로 DBA가 확인한다. DDL은 implicit commit이며 대형 테이블은 maintenance window가 필요하다.

Board 목록의 BOARD_ID/STATUS/PINNED_YN/CREATED_AT, 작성자 목록, 댓글 POST_ID/STATUS 및 계층 삭제 PARENT_COMMENT_ID, 첨부 SAVED_NAME/POST_ID/UPLOADER_ID, unread count의 사용자/READ_YN, 방명록 owner/date, pending report count를 지원한다. message/notification의 전체 목록 정렬은 READ_YN 없는 별도 인덱스가 더 유리할 수 있으나 중복 쓰기 비용 때문에 우선 추가하지 않았다. 댓글의 NVL 계층 정렬, 보고서 전체 날짜 정렬, 제목 `%keyword%`는 이 인덱스만으로 정렬/검색 비용이 사라지지 않는다. 실제 실행 계획과 데이터량을 측정한다.

현재 board/search는 전체 결과를 읽으며 JSP에 페이지 이동 UI가 없다. 즉시 OFFSET/FETCH만 붙이면 기존 결과를 유실하므로 이번에 동작을 바꾸지 않았다. 후속 pagination은 제목 LIKE escape 및 기존 latest(pin/date/id), views(view/date), likes(like/view/date) 순서를 보존하고 동률 tie-breaker와 검색 lightweight query의 count 차이를 검증해야 한다. 대규모 공개 트래픽 전 반드시 부하 시험한다.

## 영속 볼륨과 백업

현재 uploads 및 Docker named volume은 **단일 VM 로컬 저장장치**에 의존한다. VM/boot disk 삭제만으로 데이터를 잃을 수 있다. 업로드는 `/var/lib/redweb/uploads`, Oracle은 `/srv/redweb/oracle`에 별도 persistent block volume을 mount하고 UUID 기반 fstab으로 관리한다. 운영 Oracle은 해당 디렉터리를 `/opt/oracle/oradata`에 bind mount한다. 기존 named volume에서 전환할 때 DB를 정상 종료하고 전체 데이터를 복사한 뒤 소유권/권한을 보존하여 복원 시험한다. 빈 경로로 컨테이너를 시작하지 않는다.

`persistent-volumes.conf.example`과 `oracle-volume.conf.example`을 각 service drop-in으로 설치하고 daemon-reload/systemd-analyze verify를 실행한다. RequiresMountsFor와 mountpoint 검사는 지정한 경로가 실제 mount일 때만 성공한다. Docker auto-restart는 이 drop-in보다 먼저 실행될 수 있으므로 persistent mount 준비 전 컨테이너가 자동 시작되지 않도록 Docker service에도 RequiresMountsFor를 추가하거나 컨테이너 시작을 mount 의존 systemd unit으로 관리한다. 제공 README의 `--restart unless-stopped`만으로는 부족하다.

### 일관된 전체 cold backup (최소 운영 절차)

1. 백업 창을 잡고 Nginx 유지보수로 쓰기 요청을 차단한다. `systemctl stop tomcat` 후 Oracle 컨테이너를 충분한 timeout으로 정상 종료하고 로그에서 clean shutdown을 확인한다. 강제 kill된 백업을 정상 백업으로 간주하지 않는다.
2. DB의 **전체** oradata 볼륨과 uploads 볼륨을 같은 중단 창에서 snapshot 또는 `tar --numeric-owner --acls --xattrs`로 별도 백업 mount에 복사한다. 실행 중인 DB 데이터 파일을 일반 cp/tar로 백업하지 않는다. 경로, image digest, DB/JDK/Tomcat/JDBC 버전, WAR SHA256, backup timestamp를 manifest에 기록한다.
3. 백업을 암호화하여 다른 VM/장애 도메인의 저장소로 전송하고 checksum을 확인한다. runtime credential/private key는 별도 secret manager에서 관리한다. 예시 정책: 일 7개/주 4개/월 3개, RPO 24시간; 실제 RPO/RTO에 맞춰 조정한다.
4. Oracle 시작 및 readiness 확인 후 Tomcat을 시작하고 DB count와 표본 첨부 다운로드를 확인한다. 삭제·변경이 많은 서비스는 RMAN+archivelog와 파일 versioned backup을 설계해야 한다.

### 새 VM 복원

1. 고정된 runtime/image와 동일 OS/CPU를 준비한다. 외부 트래픽은 닫아 둔다. 새 persistent volumes를 mount하고 빈 경로 자동 초기화를 차단한다.
2. DB와 uploads를 **같은 백업 시점**에서 복원하고 numeric ownership/ACL을 확인한다. 원본 운영 볼륨을 덮어쓰지 않는다. DB volume UID/GID는 검증한 Oracle image 요구사항을 따른다. uploads는 tomcat만 쓰게 한다.
3. secret manager에서 JNDI/runtime env를 복원하고 root 소유 최소 권한으로 저장한다. 동일 Oracle digest에 복원 DB를 연결하여 시작한다. 이 단계에서 임의의 DB major upgrade를 병행하지 않는다.
4. FREEPDB1 상태, 주요 테이블 count, FK/sequence, 표본 게시글/댓글/첨부, ACTIVE 로그인, 관리자 step-up을 검증한다. migrations는 적용 상태 확인 후 필요한 것만 실행한다.
5. WAR 및 deploy configuration을 설치하고 volume mount guard/health를 검증한 후 TLS와 DNS를 전환한다. 최소 월 1회 별도 VM 복원 훈련을 하고 실제 RTO를 기록한다.

Object Storage 전환은 URL·접근 제어·삭제 보상·백업 일관성 설계가 필요해 후속 작업으로 남긴다.

## 배포 패키지와 공개 전 게이트

`scripts/package-production.ps1`은 새 Maven 빌드 후 WAR/config/운영 migration SQL만 별도 폴더에 복사한다. 전체 개발 ZIP, .agents/.codex, Eclipse metadata, verification 결과, seed SQL을 서버에 배포하지 않는다. `scripts/scan-deployment-secrets.py`로 경로/종류만 기록하며 실제 비밀번호나 hash를 출력하지 않는다. 탐지는 휴리스틱이므로 secret manager와 수동 검토를 대체하지 않는다.

SQL*Plus에서 app schema에 접속하여 `@verify-legacy-passwords.sql`을 실행한다. 0이 아니면 COUNT와 경고만 출력하고 오류 종료한다. DB 값 자체는 출력하지 않는다. 기존 plaintext 로그인 migration은 유지하되 공개 전에 대상 계정을 안전하게 재설정/마이그레이션한다.

공개 전 필수: 목표 JDK/Tomcat/JDBC/DB에서 clean test/package, JSP 컴파일, 실제 DB migration 두 번 실행의 idempotence, 새/기존 데이터 호환, 동시 quota/업로드 보상 실패, TLS/쿠키/health/firewall, mount 누락 부팅 실패, 복원 훈련, secret scan 결과 검토. 인증 정보가 없는 현재 작업 환경에서는 실제 운영 DB 변경이나 배포를 수행하지 않는다.

### JNDI Context 읽기 권한 gate

Context는 root:tomcat 0640(other=0)으로 설치한다. 환경 secret 파일의 root:root 0600과 구분한다.
`sudo -u tomcat test -r /opt/tomcat/conf/Catalina/localhost/scuredweb.xml || exit 1` 실패 시 배포 중단.
`namei -l /opt/tomcat/conf/Catalina/localhost/scuredweb.xml`로 부모 경로 traverse 권한도 확인한다. 내용을 cat하지 않는다.

## 통합 보완 이후 배포 경로

새 OCI production은 `deploy/oci/README.md` 및 `scripts/package-oci-production.ps1`을 사용한다. 이 GCP 테스트 runbook은 유지하되 OCI production 절차와 혼합하지 않는다. 현재 WAR 배포 전 기존 DB에 `src/main/resources/sql/migrations/20260928-auth-version.sql`을 1회 적용해야 한다. HTTPS 환경에서 registration explicit 설정, upload root, REQUIRE_JNDI 및 plaintext=false를 확인한다. Nginx scratch 경로도 mount 후 준비해야 한다.
