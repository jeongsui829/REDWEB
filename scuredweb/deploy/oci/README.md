# OCI production runbook

이 문서는 새 OCI production 경로다. 기존 GCP 테스트 구성과 명령을 섞지 않는다. 아래 실행은 운영자가 staging에서 검증한 뒤 수행한다. 현재 OCI/Oracle/TLS/부하/복구 실측 결과는 없다.

## 1. VM·네트워크·runtime

- 이미지/shape CPU 아키텍처와 Oracle Free 컨테이너 이미지 아키텍처를 먼저 맞춘다. JDK 25, Tomcat 11.0.26, 외부 ojdbc17 23.26.3.0.0 조합을 검증한다. 로컬 빌드 JDK 패치 버전과 운영 목표 버전을 혼동하지 않는다.
- NSG와 subnet Security List **양쪽**의 허용 규칙을 확인한다. TCP 443만 일반 공개, 80은 canonical redirect/선택한 ACME 절차에 필요할 때만 공개한다. 22는 관리자 CIDR 또는 OCI Bastion 경로로 제한한다. 8080/1521/5500 public ingress는 허용하지 않는다. IPv6 주소를 게시한다면 IPv6 firewall/NSG/리스너도 같은 정책으로 검증한다.
- Tomcat Connector는 loopback 127.0.0.1:8080만 listen하고 `allowTrace="false"`를 명시한다. `conf/connector.xml.example`을 기존 connector에 병합한다. 예시 연결/스레드 상한은 staging 출발값이며 DB pool/heap/CPU 실측으로 조정한다.
- Tomcat을 중지한 뒤 기존 JDBC jar를 lib 밖 보관 디렉터리로 옮기고 ojdbc17 하나만 설치한다. WAR에 JDBC jar를 추가하지 않는다.
- OS 보안 업데이트, 시간 동기화, 비루트 tomcat 서비스 계정, 최소 sudo 권한을 준비한다.

공식 참고: [OCI Security Lists](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securitylists.htm), [Block Volume 연결](https://docs.oracle.com/en-us/iaas/Content/Block/References/consistentdevicepaths.htm), [Tomcat 보안 설정](https://tomcat.apache.org/tomcat-11.0-doc/security-howto.html).

## 2. 영속·임시 볼륨

다음은 각각 root disk와 분리된 mount다. 기존 디스크를 자동 format하지 않는다. 신규 볼륨 초기화는 운영자가 장치 UUID와 백업을 확인한 뒤 별도 수행한다.

| Mount | 목적 | 권한/상한 |
| --- | --- | --- |
| /srv/redweb/oracle | Oracle /opt/oracle/oradata | 이미지 Oracle UID/GID 확인 후 소유권 지정, DB 용량 별도 감시 |
| /var/lib/redweb/uploads | 영속 업로드 | tomcat:tomcat, 0750, 유한 용량 볼륨 또는 project quota |
| /srv/redweb/scratch | Nginx/Tomcat 요청 임시 파일 | 유한 용량 독립 볼륨; bytes 및 inode 감시 |

UUID/OCI consistent device path로 /etc/fstab을 작성하고 attach 방식에 맞는 `_netdev`/mount 옵션을 사용한다. `findmnt -T PATH`, `mountpoint -q PATH`, `df -h`, `df -i`로 실제 장치와 mount를 확인한다. 부모 디렉터리만 존재하는 것은 mount 성공이 아니다. mount 의존성과 `mountpoint`가 authoritative gate다.

scratch mount 확인 후에만 nginx-body 디렉터리를 배포 OS의 Nginx worker 계정 소유 0700, tomcat/tmp 및 tomcat/scuredweb 디렉터리를 tomcat:tomcat 0700으로 만든다. Nginx 설정의 client_body_temp_path와 Context workDir, java.io.tmpdir를 이 경로에 맞춘다. Nginx unit에 `conf/nginx-mount.conf` drop-in을 설치한다. GCP 테스트 설정도 이 temp 경로를 사용하는 경우 동일하게 준비해야 한다.

Nginx는 기본 request buffering을 유지한다. body는 Java quota 검사 전에 scratch에 쌓일 수 있으므로 작은 테스트 후 동시 110MB 요청/느린 body/취소 요청으로 상한 및 정리 여부를 실측한다. `client_body_timeout 20s`는 읽기 사이 timeout이며 총 요청 시간 상한이 아니다. `proxy_request_buffering off`는 이번 변경에 적용하지 않았다. Tomcat의 실제 multipart 임시 파일 위치는 `jakarta.servlet.context.tempdir` 및 열린 파일/디스크 사용량으로 확인한다. 잘못된 위치가 root disk를 사용하는 상태에서는 배포하지 않는다. 실행 중인 temp 파일을 무작정 삭제하지 않는다.

[Nginx body/temp 공식 문서](https://nginx.org/en/docs/http/ngx_http_core_module.html).

## 3. Oracle 부팅 순서

기존 데이터가 있는 oracle-free 컨테이너와 데이터 볼륨을 사용하며 임의 재생성하지 않는다. 신규 설치는 별도 staging provisioning으로 이미지 digest/healthcheck/Oracle UID 및 초기 secret 전달 경로를 검증한다. 연결 포트는 127.0.0.1:1521만 bind한다. 관리 콘솔 포트는 공개하지 않는다. Docker logging은 local driver 또는 json-file max-size/max-file 제한을 설정한다.

기존 컨테이너의 restart policy를 운영자가 `docker update --restart=no oracle-free`로 바꾼다. 신규 컨테이너도 `--restart=no`를 사용한다. 컨테이너 자동 restart가 mount gate를 우회해서는 안 된다. shell tracing 및 secret이 포함된 docker inspect 전체 출력은 금지한다.

conf의 oracle-free.service, oracle-free-ready.service, tomcat.service를 /etc/systemd/system에 설치하고, wait-oracle-free.sh/check-oracle-container.sh/check-redweb-runtime.sh를 root:root 0755로 /usr/local/sbin에 설치한다. 실제 JAVA_HOME 경로를 확인한다.

```sh
sudo systemctl daemon-reload
sudo systemd-analyze verify /etc/systemd/system/oracle-free.service /etc/systemd/system/oracle-free-ready.service /etc/systemd/system/tomcat.service
sudo systemctl enable oracle-free.service tomcat.service
sudo systemctl start tomcat.service
```

순서는 mount → oracle-free.service(docker start --attach) → oracle-free-ready.service(health=healthy) → tomcat.service다. Tomcat도 upload/scratch mount를 검사한다. Oracle healthcheck가 단순 프로세스 생존이 아닌 실제 DB/PDB readiness인지 검증하고 app JNDI startup 연결도 확인한다.

staging reboot 시험: 서비스를 중지하고 데이터 mount를 안전하게 해제/비활성화한 상황에서 reboot → Oracle/Tomcat 미시작 확인 → mount 복구 → Oracle 시작 → readiness → Tomcat 시작. root 디렉터리에 새 DB가 생기지 않았는지 확인한다. 정상 mount 상태 reboot에서도 동일한 데이터/업로드/로그인이 유지되어야 한다. 라이브 DB 볼륨을 강제 unmount하지 않는다.

## 4. JNDI·설정·migration gate

```sh
sudo install -o root -g tomcat -m 0640 REVIEWED_CONTEXT_XML /opt/tomcat/conf/Catalina/localhost/scuredweb.xml
sudo -u tomcat test -r /opt/tomcat/conf/Catalina/localhost/scuredweb.xml || exit 1
namei -l /opt/tomcat/conf/Catalina/localhost/scuredweb.xml
```

Context owner root/group tomcat/mode 0640, other=0을 확인한다. 모든 부모 디렉터리가 tomcat에게 traverse 가능해야 한다. DB password가 포함된 파일은 cat/로그/스크린샷에 노출하지 않는다. XML escaping과 DB pool 연결은 실제 서비스 UID로 검사한다. runtime 환경 파일은 root:root 0600으로 설치한다.

`conf/redweb-runtime.env.example`을 비밀정보 없이 복사하여 도메인·경로·측정된 양수 세션 상한을 확정한다. MEASURED_POSITIVE_CAP placeholder가 남으면 시작 gate가 실패한다. `REDWEB_REQUIRE_JNDI=true`는 lookup/connection 실패 시 direct REDWEB_DB_*가 있어도 fallback하지 않는다.

**기존 DB**: 백업/복구 시험 후 owner 세션에서 `sql/20260928-auth-version.sql`만 1회 적용한다. 새 WAR 전환 전에 컬럼 존재/DEFAULT 1/NOT NULL 및 COUNT 결과를 확인한다. 재실행은 중복 컬럼 오류로 중단한다. 신규 DB용 전체 schema를 기존 DB에 적용하지 않는다. 롤백 시 AUTH_VERSION 컬럼은 남긴다. credential-version 미지원 구버전 앱으로 단순 rollback하면 세션 철회 보장이 사라지므로 트래픽 차단·모든 세션 폐기·보안 검토가 먼저 필요하다.

`sql/verify-legacy-passwords.sql`은 COUNT만 출력하며 0이 아니면 배포를 중단한다. 해시/평문 값을 SELECT해서 보고하지 않는다. 운영 plaintext 허용=false를 유지한다. 기존 짧은 해시 비밀번호는 로그인 가능하지만 신규/변경/reset에는 15~128자를 적용한다. PBKDF2 600000 비용은 `scripts/verification/PasswordBenchmark.java`로 CPU 실측하고 login 부하·p95·CPU와 함께 판단한다. 해시/비밀번호는 출력하지 않는다.

HTTPS URL이 있으면 registration 설정 명시와 absolute upload root가 필수다. 잘못된 quota/min-free/concurrency는 startup 실패다. publicRegistrationEnabled application attribute는 가입 링크 표시만 담당하며 GET/POST/service 차단이 실제 통제다.

## 5. TLS·Host·실제 IP

DNS A/AAAA, OCI NSG/OS firewall을 확인한다. DNS-01로 인증서를 먼저 발급하고 갱신 자동화 또는 수동 갱신 담당자를 정한 뒤 Nginx config를 설치한다. DNS credentials는 root 전용으로 관리한다. HTTP-01을 사용한다면 canonical 80 server의 ACME 예외 location을 별도로 검증해야 하며 server-level return에 가로막히지 않도록 구조를 조정한다.

REDWEB_TEST_HOST를 실제 hostname으로 치환하고 다른 default_server와 충돌하지 않게 한 뒤 `nginx -t` 성공 시 reload한다. unknown Host 및 unknown SNI, 정상 SNI+악성 Host, HTTP canonical redirect를 각각 curl/openssl로 검사한다. 인증서 만료 감시와 갱신 dry-run을 수행한다.

remote-ip-valve.xml을 실제 server.xml의 Host에 병합한다. 내부 신뢰 대상은 loopback Nginx뿐이다. 외부 요청의 임의 X-Forwarded-For를 신뢰하지 않으며 direct 8080은 노출하지 않는다. 실제 `request.getRemoteAddr()`가 클라이언트 IP로 복원되는지 staging에서 확인한다. access log의 주소/패턴도 확인하되 cookies/token/Authorization 헤더는 로그에 넣지 않는다.

HSTS는 주석으로 유지한다. DNS/인증서/갱신/redirect/정상·비정상 Host 검증 완료 후에만 활성화한다. 최초에 preload/includeSubDomains를 넣지 않는다.

## 6. 세션·rate limit·가입·복구 운영 정책

- guest tracking과 공용 JSP implicit session 의존 때문에 익명 페이지 전체를 session=false로 바꾸지는 않았다. online-users는 getSession(false)를 유지한다. [Tomcat Manager maxActiveSessions](https://tomcat.apache.org/tomcat-11.0-doc/config/manager.html)의 양수 cap은 로그인 사용자+guest 세션 평균 retained heap/여유 heap/동시 요청 부하로 측정하여 설정한다. placeholder는 임의 숫자로 채우지 않는다. 세션 timeout 30분, cap 도달 시 응답/복구, JMX activeSessions/sessionCounter/rejectedSessions와 GC/heap을 점검한다. JMX는 공개하지 않는다. 세션 파일 persistence는 비활성화한다.
- login/admin-verify는 10분간 IP 100/account 30/pair 10 요청 기본값이며 비밀번호 확인 전에 모두 소비한다. 성공해도 이 bucket은 초기화하지 않는다. 기존 실패 5회 차단도 유지한다. Java property `redweb.rate.login.ip/account/pair`, env `REDWEB_RATE_LOGIN_IP/ACCOUNT/PAIR`, 관리자 `REDWEB_RATE_ADMIN_VERIFY_IP/ACCOUNT/PAIR`로 설정한다.
- comment/message 60, report 20, board/profile/guestbook 30, register 5 요청/10분 사용자 기준. IP는 사용자 기준의 3배이며 익명 register는 기존 IP당 5회를 유지한다. 각각 `redweb.rate.comment` / `REDWEB_RATE_COMMENT` 형식으로 설정한다. NAT/실제 부하/429를 보고 조정한다. upload 동시성은 기본2, 허용1~16이며 환경변수 REDWEB_UPLOAD_CONCURRENCY. 숫자를 키우기 전에 heap/temp/DB pool을 측정한다.
- 이 limiter는 single-node 메모리이며 restart 시 초기화되고 노드 간 공유되지 않는다. 여러 노드 확장 전에 공유 원자적 limiter가 필요하다. 엔트리 한도 도달 시 새로운 요청 bucket은 거부한다.
- 이메일 provider는 NoOp다. production recovery/find-id/reset UI 및 이메일 verification은 unavailable로 거부한다. 이메일 변경에는 현재 비밀번호와 EMAIL_VERIFIED_YN=N을 같은 transaction에 적용한다. 향후 provider 도입 시 검증된 email만 recovery 대상으로 사용한다.
- 관리자 지원 reset: 별도 신원 확인·이중 검토 후 서비스 창구에서 사건 ID를 기록한다. 현재 앱에 신규 원격 reset 권한을 추가하지 않았다. owner가 보안 승인된 오프라인 도구로 새 15~128자 비밀번호의 PBKDF2 hash를 생성하고, 바인드 변수를 사용해 해당 user의 PASSWORD_HASH 및 AUTH_VERSION=AUTH_VERSION+1을 한 transaction에 갱신하고 미사용 reset token을 폐기한다. terminal echo/spool/history에 비밀값을 남기지 않는다. 검증된 별도 채널로 사용자에게 전달하고 즉시 변경을 안내한다. 마지막 관리자/역할/status guard를 우회하는 계정 상태 변경과 혼합하지 않는다.
- ADMIN/SUPER_ADMIN은 일반 탈퇴 금지. 관리자 상태 변경은 fresh step-up 경로로만 수행한다. 일반 회원 탈퇴에도 현재 비밀번호가 필요하다. 탈퇴 후 익명화된 userId anchor, 신고, 감사·인증 기록을 보존한다. reason/detail/IP 등에 남은 개인정보의 보존 기간·접근권·법적 삭제 요청 처리 정책은 운영 책임자가 결정해야 한다. 임의 purge job은 추가하지 않았다.

## 7. 백업·복구·rollback

Oracle의 일관된 백업(RMAN 또는 검증된 export/복구 절차)과 업로드 파일 백업을 같은 복구 시점 기준으로 운영한다. 실행 중인 DB 볼륨의 단순 파일 복사를 일관된 백업으로 간주하지 않는다. 암호화된 별도 저장소/접근 제어/retention을 정하고 암호 및 키를 코드에 넣지 않는다. 스냅샷만으로 복구 가능하다고 가정하지 않는다.

배포 전 기존 WAR SHA256, 설정의 비밀정보 없는 사본, DB migration 이력, 백업 ID를 기록한다. Tomcat 중지 → migration → 새 WAR 교체 → 시작 → health/login/권한/업로드 smoke. 실패하면 트래픽을 차단하고 로그의 비밀값을 제거하여 원인을 확인한다. DB 컬럼은 남기고 승인된 이전 WAR/설정을 복구하되 위 AUTH_VERSION rollback 주의사항을 따른다. 파일 cleanup은 DB commit 이후 수행되며 실패하면 재시도/수동 정리 목록을 검토한다.

복구 drill은 별도 staging VM/볼륨에 수행한다. DB restore → 파일 restore → JNDI → app → 댓글 소유권/첨부 접근/관리자 로그/새 로그인 검증. RPO/RTO와 소요 시간, 누락 파일을 기록한다. 재부팅 시험까지 통과해야 완료다.

## 8. 로그·모니터링

conf/journald-redweb.conf는 bounded retention 시작 예시다. 전역 영향 검토 후 drop-in으로 적용한다. conf/logrotate-redweb를 실제 Tomcat 경로에 맞추고 logrotate -d로 확인한다. Tomcat JULI 자체 회전/maxDays와 AccessLogValve maxDays도 설정해 중복 회전을 피한다. Nginx는 배포 OS 기본 reopen(USR1) logrotate에 maxsize/retention을 적용한다. Docker json-file도 max-size/max-file 제한 또는 local driver를 사용한다.

감시 대상: root/upload/Oracle/scratch bytes와 inode, Nginx temp, JVM heap/GC, active/rejected sessions, Tomcat/Nginx 5xx/429, Oracle healthy/readiness, 실패 로그인/차단 event 집계. login ID/비밀번호/token을 이벤트 label로 사용하지 않는다. 현 limiter는 별도 영속 event sink가 없으므로 429 및 접근 로그 기반 지표와 앱 실패 추세를 구분한다. 관리자 감사 DB row는 debug 로그와 별도 retention이다. 용량·세션·CPU·지연의 warning/critical 임계값은 staging 실측과 운영 여유량으로 확정한다.

## 9. release gate

실제 Oracle의 row-lock/MERGE/CONNECT BY, 동시 관리자 정지, password change/reset 대 기존 세션, email-change/token 경쟁, hidden/inactive ID mutation, 실패 audit rollback을 검증한다. Nginx/Tomcat 실프로세스 TLS, body temp, 세션 cap, PBKDF2 CPU, DB outage, disk full, reboot, restore는 로컬 unit/H2 테스트로 대체하지 않는다. 최소권한 계정 초안은 sql/runtime-grants-DRAFT.sql 및 docs/OCI-FOLLOWUPS.md를 검토하며 자동 적용하지 않는다.
