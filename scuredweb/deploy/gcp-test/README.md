> 공개 운영 배포는 먼저 [운영 하드닝 및 복원 절차](PRODUCTION.md)를 적용한다. 과거 results는 새 버전 검증을 의미하지 않는다.

# GCP Compute Engine 단기 실행 테스트

이 문서는 REDWEB(`scuredweb`)을 운영 배포로 개편하지 않고, Ubuntu 24.04 VM 한 대에서 Tomcat 11과 Oracle Free의 연결 및 핵심 기능을 짧게 확인하기 위한 절차다. 실제 비밀번호, 인증키, VM IP는 저장소나 명령 기록에 넣지 않는다.

## 1. 적용 범위와 사전 확인

- 권장 최소 VM: `e2-standard-2`(2 vCPU, 8 GB RAM), x86_64, 30~40 GB Balanced Persistent Disk
- Maven 빌드와 Oracle 초기화를 동시에 수행하거나 여러 명이 테스트하면 `e2-standard-4`가 더 안정적이다.
- 애플리케이션 context path는 WAR 이름에 따라 `/scuredweb`이다.
- 최신 외부 테스트 WAR에는 DB URL·사용자·비밀번호가 들어 있지 않다. Tomcat JNDI `java:comp/env/jdbc/RedWebDB`가 기본이며, 로컬 개발에서만 외부 `REDWEB_DB_*` 또는 `-Dredweb.db.*` 값을 사용할 수 있다.
- JNDI와 외부 직접 JDBC 설정이 모두 없으면 애플리케이션 배포는 명확한 설정 오류로 실패한다. DB 장애를 빈 결과나 샘플 데이터로 대체하지 않는다.
- 애플리케이션 초기화는 DB 연결을 한 번 검증하며, 실패하면 해당 웹 애플리케이션 배포도 실패한다. 애플리케이션 자체 재시도는 없으므로 Oracle이 healthy 상태가 된 뒤 Tomcat을 시작한다.
- 이 구성은 단일 VM 임시 테스트용이다. 운영용 고가용성, 백업, 비밀 관리, TLS, 프록시, 모니터링은 포함하지 않는다.
- 외부 사용자의 로그인 정보를 평문 HTTP로 전송하지 않는다. 외부 기능 테스트 전에 HTTPS, VPN, IAP/SSH 터널 중 하나를 선택한다.

## 2. Ubuntu 패키지와 JDK 25

```bash
sudo apt update
sudo apt install -y ca-certificates curl wget gpg apt-transport-https maven unzip

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | gpg --dearmor \
  | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg >/dev/null
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-25-jdk

java -version
javac -version
mvn -version
```

`java`와 Maven이 모두 JDK 25를 표시하는지 확인한다. 설치 방법은 [Eclipse Temurin Linux 설치 안내](https://adoptium.net/installation/linux/)를 기준으로 한다.

## 3. Docker Engine 설치

Ubuntu 배포판 패키지와 충돌할 수 있는 비공식 Docker 패키지를 제거한 뒤 Docker 공식 저장소를 사용한다.

```bash
sudo apt remove -y docker.io docker-compose docker-compose-v2 podman-docker containerd runc || true
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
```

세부 절차는 [Docker Engine Ubuntu 설치 문서](https://docs.docker.com/engine/install/ubuntu/)를 따른다.

## 4. Oracle Free 실행과 준비 확인

Oracle Container Registry에서 Database Free 이미지 사용 조건을 확인하고 로그인한다. DB 포트는 VM의 loopback에만 바인딩하며 5500은 게시하지 않는다.

```bash
sudo docker login container-registry.oracle.com
read -rsp "Oracle SYS password: " ORACLE_PWD; echo

sudo docker volume create oracle-free-data
sudo docker run -d \
  --name oracle-free \
  --restart unless-stopped \
  --shm-size=1g \
  -p 127.0.0.1:1521:1521 \
  -e ORACLE_PWD="$ORACLE_PWD" \
  -v oracle-free-data:/opt/oracle/oradata \
  "${REDWEB_ORACLE_IMAGE:?Set a verified 23.26.3 image digest first}"
unset ORACLE_PWD

sudo docker logs -f oracle-free
```

로그에 데이터베이스 준비 완료 메시지가 나온 뒤 다음 상태를 확인한다.

```bash
sudo docker ps --filter name=oracle-free
sudo docker inspect --format '{{json .State.Health}}' oracle-free
sudo ss -lntp | grep 1521
```

Oracle Free 이미지의 기본 PDB는 `FREEPDB1`이다. 이미지 동작과 접속 형식은 [Oracle Database SingleInstance 이미지 문서](https://github.com/oracle/docker-images/blob/main/OracleDatabase/SingleInstance/README.md)를 참고한다.

## 5. REDWEB DB 사용자와 초기화

SQL 파일을 VM의 `/tmp/redweb-sql`에 올린 후 컨테이너로 복사한다.

```bash
# VM에서 먼저 실행한다.
mkdir -p /tmp/redweb-sql

# 로컬 PC의 프로젝트 루트에서 실행한다.
gcloud compute scp src/main/resources/sql/*.sql VM_NAME:/tmp/redweb-sql/ --zone=ZONE

# 다시 VM에서 실행한다. docker cp는 대상 디렉터리를 만들지 않는다.
sudo docker exec --user root oracle-free sh -c \
  'mkdir -p /work/sql && chown -R oracle:oinstall /work/sql'
sudo docker cp /tmp/redweb-sql/. oracle-free:/work/sql/
sudo docker exec -it oracle-free bash
```

컨테이너 안에서 SYS로 `FREEPDB1`에 접속해 사용자를 만든다. SQL*Plus의 숨김 입력을 사용하고 실제 비밀번호를 문서나 스크립트에 기록하지 않는다.

```sql
sqlplus -L 'sys@//127.0.0.1:1521/FREEPDB1 as sysdba'
SET VERIFY OFF
ACCEPT redweb_password CHAR PROMPT 'REDWEB DB password: ' HIDE
CREATE USER redweb IDENTIFIED BY "&redweb_password";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO redweb;
ALTER USER redweb QUOTA UNLIMITED ON USERS;
EXIT
```

기존 테스트 DB의 `redweb` 사용자를 재사용한다면 `CREATE USER`를 반복하지 말고 SYS 세션에서 비밀번호를 새 테스트 전용 값으로 교체한다.

```sql
SET VERIFY OFF
ACCEPT redweb_password CHAR PROMPT 'New REDWEB DB password: ' HIDE
ALTER USER redweb IDENTIFIED BY "&redweb_password" ACCOUNT UNLOCK;
```

이후 `redweb`으로 한 번 접속한 세션에서 다음 순서대로 실행한다.

```sql
sqlplus -L redweb@//127.0.0.1:1521/FREEPDB1
@/work/sql/scuredweb-schema.sql
@/work/sql/scuredweb-reference-seed.sql
@/work/sql/repair-seed-sequences.sql
@/work/sql/diagnose-board-persistence.sql
EXIT
```

- 새 DB에는 `scuredweb-schema.sql`을 정확히 한 번만 실행하고, 고정 계정이 없는 `scuredweb-reference-seed.sql`로 게시판·카테고리·태그만 넣는다.
- 기존 DB에는 스키마를 다시 실행하지 않는다. Tomcat을 멈춘 뒤 `add-user-suspension-expiry.sql`, 필요한 경우 `repair-board-read-dependencies.sql`, `scuredweb-reference-seed.sql`, `repair-seed-sequences.sql` 순서로 실행한다.
- `scuredweb-seed.sql`은 고정 `admin`, `user1`~`user8` 계정을 포함하므로 외부 테스트 환경에서는 실행하지 않는다.
- 새 DB에서는 `scuredweb-db-upgrade.sql`을 실행하지 않는다. 이 파일은 기존 DB 업그레이드용이다.
- `repair-board-read-dependencies.sql`은 기존 게시판 읽기 의존성을 보존하고 `SEQ_RW_POST`를 우선 점검한다.
- `repair-seed-sequences.sql`은 시드가 명시 ID를 넣은 6개 테이블의 시퀀스를 `MAX(ID)+1` 이상으로만 전진시킨다. Tomcat을 중지한 상태에서 실행한다.
- repair 중 테이블이나 시퀀스가 하나라도 없으면 SQL*Plus가 즉시 실패한다. 출력된 `BEFORE`, `AFTER`, `MAX_ID`를 테스트 기록에 보관한다.
- `ALTER TABLE`과 `ALTER SEQUENCE`는 Oracle DDL이므로 rollback으로 되돌릴 수 없다. repair는 시퀀스를 낮추거나 기존 행을 수정하지 않는다.
- 진단 출력에서 `RW_USER`, `RW_POST`, `RW_BOARD`, `RW_CATEGORY`와 관련 시퀀스가 모두 존재하는지 확인한다.
- 신규 회원가입 전에 `SEQ_RW_USER`의 출력된 `LAST_NUMBER`가 `RW_USER.MAX(USER_ID)+1` 이상인지 확인한다.

관리자 계정은 애플리케이션 회원가입 화면에서 강력한 임의 비밀번호로 만든 뒤 SQL*Plus에서 로그인 ID를 숨김 입력으로 받아 승격한다. 비밀번호를 SQL에 넣지 않는다.

```sql
SET VERIFY OFF
ACCEPT admin_login CHAR PROMPT 'New administrator login ID: ' HIDE
UPDATE RW_USER
   SET ROLE_CODE = 'ADMIN', UPDATED_AT = SYSTIMESTAMP
 WHERE LOGIN_ID = '&admin_login'
   AND STATUS = 'ACTIVE';
COMMIT;
UNDEFINE admin_login
```

개발 시드를 이미 실행했다면 외부 참여자를 초대하기 전에 고정 계정을 삭제하지 말고 잠근다. 아래 SQL의 영향 행 수가 9인지 확인한다.

```sql
UPDATE RW_USER
   SET STATUS = 'SUSPENDED', SUSPENDED_UNTIL = NULL, UPDATED_AT = SYSTIMESTAMP
 WHERE LOGIN_ID IN ('admin', 'user1', 'user2', 'user3', 'user4', 'user5', 'user6', 'user7', 'user8');
COMMIT;
```

## 6. Tomcat 11 설치

Tomcat 11 최신 패치 버전 번호는 설치 시점에 [공식 다운로드 페이지](https://tomcat.apache.org/download-11.cgi)에서 확인한다. 아래 버전과 SHA-512 파일은 예시이며 둘을 함께 갱신한다.

```bash
export TOMCAT_VERSION=11.0.26
cd /tmp
curl -fLO "https://dlcdn.apache.org/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
curl -fLO "https://dlcdn.apache.org/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz.sha512"
sha512sum -c "apache-tomcat-${TOMCAT_VERSION}.tar.gz.sha512"

sudo useradd --system --home /opt/tomcat --create-home --shell /usr/sbin/nologin tomcat
sudo tar -xzf "apache-tomcat-${TOMCAT_VERSION}.tar.gz" -C /opt/tomcat --strip-components=1
sudo chown -R tomcat:tomcat /opt/tomcat
sudo chmod +x /opt/tomcat/bin/*.sh
```

Oracle JDBC 드라이버는 JNDI Resource factory와 웹앱이 함께 볼 수 있도록 `$CATALINA_HOME/lib`에 한 번만 설치한다. 버전은 `pom.xml`의 `ojdbc.version`과 맞춘다.

```bash
cp deploy/gcp-test/conf/tomcat.service /tmp/tomcat.service
cp deploy/gcp-test/conf/oracle-free-ready.service /tmp/oracle-free-ready.service
sudo install -o root -g root -m 0644 /tmp/tomcat.service /etc/systemd/system/tomcat.service
sudo install -o root -g root -m 0644 /tmp/oracle-free-ready.service /etc/systemd/system/oracle-free-ready.service
sudo install -o root -g root -m 0755 deploy/gcp-test/conf/wait-oracle-free.sh /usr/local/sbin/wait-oracle-free.sh

# 접근 경로 하나만 선택한다. IAP HTTP가 기본 예제다.
sudo install -d -o root -g root -m 0755 /etc/redweb
sudo install -o root -g root -m 0644 deploy/gcp-test/conf/redweb-iap-http.env.example /etc/redweb/redweb-runtime.env
# HTTPS Nginx를 선택했다면 위 명령 대신 다음 파일을 설치하고 호스트명을 편집한다.
# sudo install -o root -g root -m 0644 deploy/gcp-test/conf/redweb-https.env.example /etc/redweb/redweb-runtime.env
# sudoedit /etc/redweb/redweb-runtime.env

mvn -q dependency:copy \
  -Dartifact=com.oracle.database.jdbc:ojdbc17:23.26.3.0.0 \
  -DoutputDirectory=/tmp/redweb-jdbc
# Stop Tomcat and move the previous JDBC driver outside lib before installing this one.
sudo install -o root -g tomcat -m 0640 /tmp/redweb-jdbc/ojdbc17-23.26.3.0.0.jar /opt/tomcat/lib/ojdbc17.jar

sudo systemd-analyze verify /etc/systemd/system/oracle-free-ready.service /etc/systemd/system/tomcat.service
sudo systemctl daemon-reload
sudo systemctl enable tomcat
```

`oracle-free-ready.service`는 독립적으로 enable하지 않는 정적 oneshot unit이다. `tomcat.service`의 `Requires`와 `After`가 매 시작 시 이 unit을 호출하며, 성공 후 active 상태를 유지하지 않으므로 다음 Tomcat 시작 또는 재시작에서도 readiness를 다시 검사한다. 이 unit만 root로 Docker 상태를 읽는다. Tomcat 프로세스는 계속 `tomcat` 사용자로 실행되며 `tomcat` 사용자를 `docker` 그룹에 넣지 않는다. Oracle의 `healthy` 상태를 300초 안에 확인하지 못하면 준비 unit과 Tomcat 시작이 함께 실패한다. `systemd-analyze verify`가 오류를 출력하면 서비스를 시작하지 말고 unit 파일을 먼저 수정한다.

Tomcat 11은 Java 17 이상을 요구하므로 JDK 25와 함께 실행할 수 있다. 요구사항은 [Tomcat 11 설치 문서](https://tomcat.apache.org/tomcat-11.0-doc/appdev/installation.html)에서 확인한다.

## 7. WAR 빌드와 배포 설정

로컬 또는 VM의 프로젝트 루트에서 빌드한다.

```bash
mvn clean package
test -f target/scuredweb.war
jar tf target/scuredweb.war | grep -E '(^|/)servlet-api.*\.jar$' && echo 'ERROR: Servlet API bundled' || true
jar tf target/scuredweb.war | grep -E '(^|/)(ojdbc|oraclepki|osdt).*\.jar$' && echo 'ERROR: Oracle driver bundled' || true
unzip -p target/scuredweb.war WEB-INF/classes/properties/jdbc.properties | grep -E '^(db.url|db.username|db.password)=' \
  && echo 'ERROR: embedded database setting' || true
```

검사는 비밀번호 값을 출력하지 않는다. `XEPDB1`, `db.password=`, `ojdbc*.jar`, `servlet-api*.jar`가 WAR에서 발견되면 배포하지 않는다.

WAR와 SQL 파일을 로컬 PC에서 VM으로 전송할 수 있다.

```bash
gcloud compute scp target/scuredweb.war VM_NAME:/tmp/scuredweb.war --zone=ZONE
gcloud compute scp deploy/gcp-test/conf/scuredweb.xml VM_NAME:/tmp/scuredweb.xml --zone=ZONE
```

JNDI Context 파일에 실제 비밀번호를 대화형으로 입력한 뒤 권한을 `600`으로 제한한다. 완성 파일을 저장소나 배포 ZIP으로 다시 복사하지 않는다.

```bash
sudo systemctl stop tomcat
sudo install -o root -g tomcat -m 0640 /tmp/scuredweb.xml /opt/tomcat/conf/Catalina/localhost/scuredweb.xml
sudoedit /opt/tomcat/conf/Catalina/localhost/scuredweb.xml
sudo chmod 0640 /opt/tomcat/conf/Catalina/localhost/scuredweb.xml
sudo install -o tomcat -g tomcat -m 0640 /tmp/scuredweb.war /opt/tomcat/webapps/scuredweb.war
```

`scuredweb.xml`은 `FREEPDB1`, 최대 연결 8개, 최대 대기 10초, `SELECT 1 FROM DUAL` 대여 전 검증을 설정한다. XML 특수문자가 포함된 비밀번호는 위 단순 `sed` 대신 root 전용 편집기로 입력하고 XML escaping을 확인한다.

## 8. 업로드 디렉터리

`UploadPathUtil`은 `REDWEB_UPLOAD_ROOT` 또는 `-Dredweb.upload.root`를 우선 사용한다. 제공된 systemd 예제는 `/var/lib/redweb/uploads`를 사용한다.

```bash
sudo install -d -o tomcat -g tomcat -m 0750 \
  /var/lib/redweb/uploads \
  /var/lib/redweb/uploads/attachments \
  /var/lib/redweb/uploads/profiles
sudo -u tomcat test -w /var/lib/redweb/uploads
sudo -u tomcat touch /var/lib/redweb/uploads/.write-test
sudo rm /var/lib/redweb/uploads/.write-test
```

첨부파일 테스트 후 실제 파일이 생성되고 같은 게시글에서 다시 다운로드되는지 확인한다. 이 단기 테스트에서는 Cloud Storage나 별도 Persistent Disk로 외부화하지 않는다.

## 9. 시작, 로그, 핵심 기능 확인

Oracle 준비 완료 후에만 Tomcat을 시작한다.

IAP TCP forwarding을 선택한 경우 `server.xml`의 Connector는 VM 사설 IP에만 바인딩한다. `VM_PRIVATE_IP`를 실제 값으로 교체하고 `redweb-iap-http.env.example`의 `redweb.secureCookie=false`를 사용한다.

```xml
<Connector port="8080" address="VM_PRIVATE_IP"
           protocol="HTTP/1.1" connectionTimeout="20000"
           maxParameterCount="1000" redirectPort="8443" />
```

```bash
sudo docker ps --filter name=oracle-free
sudo docker logs --tail 100 oracle-free
sudo systemctl start tomcat
sudo systemctl status tomcat --no-pager
sudo journalctl -u tomcat -f
# 필요 시
tail -f /opt/tomcat/logs/catalina.out
```

### 9.1 본인 단독 구동 확인

외부 참여자를 초대하기 전에 VM 내부 또는 SSH/IAP 터널에서 다음을 확인한다. 이 단계에서는 8080 ingress 규칙을 만들 필요가 없다.

1. IAP Connector이면 `curl -I http://VM_PRIVATE_IP:8080/scuredweb/`, HTTPS Nginx Connector이면 `curl -I http://127.0.0.1:8080/scuredweb/`가 응답하는지 확인한다.
2. Tomcat 로그에 `ORA-12541`, `ORA-01017`, `XEPDB1` 연결 시도가 없는지 확인한다.
3. SQL*Plus에서 `FREEPDB1`의 REDWEB 테이블과 시퀀스 진단 결과를 확인한다.
4. 자신의 브라우저는 10절의 SSH/IAP 터널을 통해 `http://127.0.0.1:18080/scuredweb`에 접속한다.
5. 운영자 본인 계정 한 개로 로그인과 로그아웃을 확인한다.

### 9.2 허가된 외부 사용자 기능 테스트

HTTPS, VPN, IAP/SSH 터널 중 하나가 준비된 뒤에만 외부 사용자의 로그인 테스트를 시작한다. `http://VM_IP:8080` 평문 접속으로 비밀번호나 세션 쿠키를 전송하지 않는다.

1. 신규 테스트 계정 한 건을 등록하고 `RW_USER.USER_ID`가 기존 PK와 충돌하지 않는지 확인한다.
2. 등록한 계정으로 로그인하고 HTTP 상태 및 Tomcat 로그를 기록한다.
3. `/scuredweb/board/write`에서 제목, 내용, 첨부파일을 전송한다.
4. POST 응답, Controller 도달 여부, Oracle 오류 코드, commit/rollback 결과를 확인한다.
5. SQL*Plus에서 `RW_POST`의 실제 신규 행과 `SEQ_RW_POST` 상태를 확인한다.
6. 작성된 상세 페이지를 열고 첨부파일 저장 및 재다운로드를 확인한다.
7. 별도 관리자 테스트 계정으로 실제 관리자 화면과 필요한 관리 동작을 확인한다.
8. Tomcat을 재시작하고 회원, 게시글, 첨부파일이 유지되는지 다시 확인한다.

실시간 접속 수는 최근 2분 동안 heartbeat가 있었던 HTTP 세션 수다. `전체 = 로그인 회원 세션 + Guest 세션`을 확인하며, 회원 목록에는 로그인 회원만 표시돼야 한다. 같은 브라우저의 여러 탭은 보통 같은 세션이고 시크릿 창과 다른 브라우저는 별도 세션이다. 브라우저를 닫아도 최대 2분간 남을 수 있으며, 이 구현은 단일 Tomcat JVM 전용이다. 여러 VM으로 확장하면 Redis 같은 공유 저장소가 필요하지만 이번 테스트 범위에는 포함하지 않는다.

오류가 나면 브라우저 시각 결과만으로 판단하지 말고 `journalctl`, `catalina.out`, Oracle 컨테이너 로그, HTTP 상태 코드와 Oracle vendor code를 함께 보관한다.

정상 시작과 Oracle 재시작은 다음 순서를 사용한다. `oracle-free-ready.service`는 Tomcat 시작마다 다시 실행된다.

```bash
# 정상 시작
sudo docker start oracle-free
sudo systemctl start tomcat
sudo systemctl status oracle-free-ready tomcat --no-pager
curl -fsS http://VM_PRIVATE_IP:8080/scuredweb/health

# Oracle 재시작
sudo systemctl stop tomcat
sudo docker restart oracle-free
sudo systemctl start tomcat
sudo systemctl status oracle-free-ready tomcat --no-pager
curl -fsS http://VM_PRIVATE_IP:8080/scuredweb/health
```

Oracle이 unhealthy이면 readiness unit이 실패하고 Tomcat은 시작되지 않아야 한다. HTTPS Nginx Connector를 선택한 경우 위 `curl`의 호스트만 `127.0.0.1`로 바꾼다.

### 9.3 제한 URL HTTPS 경로

도메인이 준비된 참여자 테스트는 `deploy/gcp-test/conf/nginx-redweb.conf`를 사용한다. IP 주소만 있는 상태에서 공개 신뢰 인증서가 준비됐다고 가정하지 않는다. 도메인이 없으면 10.1의 IAP 터널을 사용한다.

HTTPS reverse proxy 방식을 선택한 경우 Tomcat `server.xml`의 HTTP Connector는 `address="127.0.0.1"`로 제한하고 `redweb-https.env.example`의 `redweb.secureCookie=true`를 사용한다. 외부 8080 방화벽 규칙은 만들지 않으며 Nginx만 Tomcat으로 프록시한다. `<Host>` 안에는 `deploy/gcp-test/conf/remote-ip-valve.xml`의 Valve를 추가한다. `internalProxies`가 loopback만 허용하므로 외부에서 임의로 보낸 `X-Forwarded-For`는 신뢰되지 않는다.

```xml
<Connector port="8080" address="127.0.0.1"
           protocol="HTTP/1.1" connectionTimeout="20000"
           maxParameterCount="1000" redirectPort="8443" />
```

```bash
sudo apt install -y nginx apache2-utils certbot python3-certbot-nginx
# DNS-01 certificate first: the HTTPS config references these files.
sudo certbot certonly --manual --preferred-challenges dns -d REDWEB_TEST_HOST
sudo install -o root -g root -m 0644 deploy/gcp-test/conf/nginx-redweb.conf /etc/nginx/sites-available/redweb
sudo ln -s /etc/nginx/sites-available/redweb /etc/nginx/sites-enabled/redweb
sudoedit /etc/nginx/sites-available/redweb

# Basic Auth를 선택한 경우에만 실행한다. 실제 파일은 저장소에 복사하지 않는다.
sudo htpasswd -c /etc/nginx/redweb-test.htpasswd TESTER_NAME
sudo chown root:www-data /etc/nginx/redweb-test.htpasswd
sudo chmod 0640 /etc/nginx/redweb-test.htpasswd

sudo nginx -t
sudo systemctl reload nginx
```

IAP HTTP 경로는 `/etc/redweb/redweb-runtime.env`에 `redweb-iap-http.env.example`을 설치해 `redweb.secureCookie=false`를 사용한다. HTTP인 `http://127.0.0.1:18080`에서 `Secure` 쿠키를 사용하면 브라우저가 세션 쿠키를 보내지 않아 로그인 상태가 유지되지 않는다. HTTPS Nginx 경로는 대신 `redweb-https.env.example`을 설치해 `redweb.secureCookie=true`와 공개 HTTPS URL을 사용한다. 두 파일을 합치거나 한 테스트 세션에서 접근 방식을 혼용하지 않는다. 두 경로 모두 `HttpOnly`와 `SameSite=Lax`를 유지한다.

선택한 경로로 로그인할 때 개발자 도구 또는 `curl -i`로 로그인 POST 응답의 `Set-Cookie`를 확인하고, 같은 쿠키 저장소로 `/scuredweb/board/write` 같은 보호 URL에 접근한다. IAP HTTP에서는 `Secure`가 없어야 하고, HTTPS에서는 `Secure`가 있어야 하며 두 경우 모두 `HttpOnly`와 `SameSite=Lax`가 있어야 한다.

배포 후 세션을 만들지 않는 헬스체크를 VM 내부에서 확인한다.

```bash
curl -fsS http://127.0.0.1:8080/scuredweb/health
curl -fsS https://REDWEB_TEST_HOST/scuredweb/health
```

정상 DB 연결은 `200 {"status":"UP"}`, DB 연결 실패는 내부 정보를 포함하지 않은 `503 {"status":"DOWN"}`이어야 한다.

## 10. GCP 방화벽

VM에 `scuredweb-test` 네트워크 태그를 붙인 뒤 현재 SSH 규칙부터 점검한다.

```bash
gcloud compute instances add-tags VM_NAME --zone=ZONE --tags=scuredweb-test
gcloud compute firewall-rules list \
  --format='table(name,network,direction,sourceRanges.list():label=SOURCE_RANGES,allowed[].map().firewall_rule().list():label=ALLOW,disabled)'
gcloud compute firewall-rules describe default-allow-ssh \
  --format='yaml(name,network,direction,sourceRanges,allowed,targetTags,disabled)' || true
```

`default-allow-ssh` 또는 다른 규칙이 `tcp:22`를 `0.0.0.0/0`에 허용하면 새 제한 규칙을 추가하는 것만으로는 기존 허용이 취소되지 않는다. 전용 테스트 프로젝트/VPC이고 영향 범위를 확인한 경우에만 해당 광역 규칙을 비활성화한다.

```bash
gcloud compute firewall-rules update default-allow-ssh --disabled
```

SSH 접근은 다음 둘 중 하나만 선택한다.

```bash
# 선택 A: 관리자 본인 공인 IP만 SSH 허용
gcloud compute firewall-rules create scuredweb-test-ssh \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:22 \
  --source-ranges=ADMIN_IP/32 \
  --target-tags=scuredweb-test

# 선택 B: IAP TCP forwarding으로만 SSH 허용
gcloud compute firewall-rules create scuredweb-test-iap-ssh \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:22 \
  --source-ranges=35.235.240.0/20 \
  --target-tags=scuredweb-test
```

본인 단독 확인은 8080을 외부에 열지 않고 다음과 같이 터널링한다.

```bash
# IAP Connector가 VM_PRIVATE_IP에 바인딩된 경우
gcloud compute ssh VM_NAME --zone=ZONE --tunnel-through-iap -- \
  -N -L 18080:VM_PRIVATE_IP:8080

# 본인 IP SSH 선택 시 --tunnel-through-iap만 생략한다.
```

### 10.1 선택한 외부 경로: 참여자별 IAP TCP forwarding

이 문서의 완성된 외부 테스트 경로는 IAP TCP forwarding이다. 전용 테스트 프로젝트 또는 전용 테스트 VPC를 사용하고, 참여자 Google 계정마다 권한을 개별 부여한다. VM에 외부 IP가 없어도 사용할 수 있다.

`roles/iap.tunnelResourceAccessor` 하나만으로 `gcloud compute start-iap-tunnel`을 실행할 수 없다. 참여자에게는 `iap.tunnelInstances.accessViaIAP` 외에 `compute.instances.get`과 `compute.instances.list`도 필요하다. `roles/compute.viewer` 대신 두 권한만 포함한 프로젝트 custom role을 사용한다. `compute.instances.list`는 프로젝트 범위 권한이므로 읽을 수 있는 인스턴스 목록 자체를 VM 한 대로 제한할 수는 없지만, 실제 터널 권한은 대상 VM 한 대와 8080 포트로 제한한다.

```bash
gcloud services enable iap.googleapis.com
gcloud compute firewall-rules create scuredweb-test-iap-web \
  --network=VPC_NAME \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:8080 \
  --source-ranges=35.235.240.0/20 \
  --target-tags=scuredweb-test

# 프로젝트당 한 번만 생성한다.
gcloud iam roles create ScuredwebIapInstanceReader \
  --project=PROJECT_ID \
  --title='Scuredweb IAP instance reader' \
  --description='Only the instance lookup permissions required by start-iap-tunnel' \
  --permissions='compute.instances.get,compute.instances.list' \
  --stage=GA

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member='user:PARTICIPANT_EMAIL' \
  --role='projects/PROJECT_ID/roles/ScuredwebIapInstanceReader'

gcloud compute instances add-iam-policy-binding VM_NAME \
  --project=PROJECT_ID \
  --zone=ZONE \
  --member='user:PARTICIPANT_EMAIL' \
  --role='roles/iap.tunnelResourceAccessor' \
  --condition='expression=destination.port == 8080,title=scuredweb_port_8080,description=Only REDWEB Tomcat'
```

관리자 계정이 아니라 실제 참여자 계정으로 Google Cloud CLI에 로그인한 뒤 활성 계정을 확인하고 터널을 실행한다. 브라우저에서는 `http://127.0.0.1:18080/scuredweb`에 접속한다.

```bash
gcloud auth login
gcloud auth list --filter=status:ACTIVE --format='value(account)'
gcloud compute start-iap-tunnel VM_NAME 8080 \
  --local-host-port=127.0.0.1:18080 \
  --zone=ZONE \
  --project=PROJECT_ID
```

성공 시 `Listening on port [18080]`을 기록하고 로그인 페이지와 인증 POST를 확인한다. 같은 참여자 계정으로 다음 명령은 모두 `PERMISSION_DENIED`여야 한다. 하나라도 열리면 조건 없는 상위 범위의 IAP 역할이 별도로 부여된 것이므로 IAM 정책을 다시 조사한다.

```bash
gcloud compute start-iap-tunnel VM_NAME 22 --local-host-port=127.0.0.1:10022 --zone=ZONE --project=PROJECT_ID
gcloud compute start-iap-tunnel VM_NAME 1521 --local-host-port=127.0.0.1:11521 --zone=ZONE --project=PROJECT_ID
gcloud compute start-iap-tunnel VM_NAME 5500 --local-host-port=127.0.0.1:15500 --zone=ZONE --project=PROJECT_ID
```

브라우저와 로컬 IAP 클라이언트 사이는 loopback이고 VM까지는 IAP 암호화 터널이다. 이 경로에서는 브라우저 URL이 HTTP이므로 `Secure` 쿠키를 켜면 세션 쿠키가 전송되지 않는다. 참여자에게 VM 공인 IP나 8080 직접 주소를 제공하지 않는다.

HTTPS 리버스 프록시로 전환하는 경우에만 Tomcat에 `-Dredweb.secureCookie=true`를 추가하고 `REDWEB_PUBLIC_BASE_URL=https://HOST/scuredweb`를 설정한다. 프록시의 전달 헤더를 임의로 신뢰하지 말고, Tomcat `RemoteIpValve`의 `internalProxies`를 실제 프록시 사설 주소로 제한해 원래 IP와 HTTPS scheme을 복원한다. HTTPS 전환은 이 IAP 절차와 동시에 섞어 적용하지 않는다.

`22`, `8080`, `1521`, `5500`을 `0.0.0.0/0`에 허용하지 않는다. `1521`과 `5500` ingress 규칙은 만들지 않고, Docker의 1521도 `127.0.0.1`에만 바인딩한다. 방화벽 명령 옵션은 [gcloud compute firewall-rules create 문서](https://cloud.google.com/sdk/gcloud/reference/compute/firewall-rules/create)를 참고한다. IAP TCP forwarding의 방화벽 원본 범위와 권한은 [IAP TCP forwarding 문서](https://cloud.google.com/iap/docs/using-tcp-forwarding)를 기준으로 확인한다.

IAP 방식을 선택하면 Tomcat 8080은 loopback이 아니라 `VM_PRIVATE_IP`에만 바인딩해야 IAP 프록시가 도달할 수 있다. 방화벽 원본은 `35.235.240.0/20`, IAM 조건은 `destination.port == 8080`으로 동시에 제한하고 `gcloud compute start-iap-tunnel`을 사용한다. Nginx HTTPS 방식과 같은 Connector 또는 쿠키 설정을 그대로 사용하지 않는다.

### 10.2 선택 가능한 외부 경로: HTTPS + Basic Auth 또는 IP allowlist

도메인과 DNS-01 인증서가 준비된 경우에만 이 방식을 사용한다. Tomcat 8080은 `127.0.0.1`에만 두고 외부 8080 방화벽 규칙은 만들지 않는다. 외부 방화벽에는 참여자 IP의 443만 허용한다. 80은 영구 허용하지 않으며 Nginx의 HTTP→HTTPS 리다이렉트가 꼭 필요하면 동일한 참여자 IP에만 연다.

```bash
gcloud compute firewall-rules create scuredweb-test-https \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:443 \
  --source-ranges=TESTER_IP_1/32,TESTER_IP_2/32 \
  --target-tags=scuredweb-test
```

고정 IP allowlist가 어려우면 HTTPS가 먼저 정상인 상태에서 Nginx Basic Auth를 적용한다. 공유 자격증명은 저장소·WAR·README·명령 기록에 넣지 않고 테스트 종료 즉시 폐기한다. Basic Auth만 믿고 Oracle 1521, Tomcat 8080 또는 관리자 SSH 22를 공개하지 않는다.

## 11. 종료와 롤백

테스트 결과와 필요한 로그만 안전한 위치로 회수한 후 자원을 중지하거나 삭제한다.

```bash
sudo systemctl stop tomcat
sudo docker stop oracle-free

# 로컬 PC 또는 Cloud Shell에서 실행한다.
gcloud compute firewall-rules delete scuredweb-test-ssh
gcloud compute firewall-rules delete scuredweb-test-iap-ssh
gcloud compute firewall-rules delete scuredweb-test-iap-web
gcloud compute firewall-rules delete scuredweb-test-https
gcloud compute instances remove-iam-policy-binding VM_NAME \
  --project=PROJECT_ID \
  --zone=ZONE \
  --member='user:PARTICIPANT_EMAIL' \
  --role='roles/iap.tunnelResourceAccessor' \
  --condition='expression=destination.port == 8080,title=scuredweb_port_8080,description=Only REDWEB Tomcat'
gcloud projects remove-iam-policy-binding PROJECT_ID \
  --member='user:PARTICIPANT_EMAIL' \
  --role='projects/PROJECT_ID/roles/ScuredwebIapInstanceReader'

# 모든 참여자 binding을 회수한 뒤 프로젝트 custom role도 삭제한다.
gcloud iam roles delete ScuredwebIapInstanceReader --project=PROJECT_ID
gcloud compute instances stop VM_NAME --zone=ZONE
```

실제로 생성한 선택지의 규칙만 삭제한다. 시퀀스 repair는 데이터를 삭제하지 않고 시퀀스를 앞으로만 이동하므로 되돌리지 않는다. 문제가 생기면 Tomcat을 중지하고 로그와 DB 상태를 보존한 뒤 임시 VM 전체를 폐기한다.

더 이상 재검증하지 않는다면 VM과 부팅 디스크를 삭제한다.

```bash
gcloud compute instances delete VM_NAME --zone=ZONE --delete-disks=all
```

중지된 VM도 디스크 등 일부 자원 비용이 남을 수 있다. 완전 종료가 목적이면 [Compute Engine 인스턴스 삭제 문서](https://cloud.google.com/sdk/gcloud/reference/compute/instances/delete)를 확인하고 VM을 삭제한다. 롤백은 애플리케이션 소스를 되돌리는 작업이 아니라 임시 VM, 방화벽 규칙, Oracle 볼륨을 폐기하는 방식으로 수행한다.

### JNDI Context 읽기 권한 gate

Context는 root:tomcat 0640(other=0)으로 설치한다. 환경 secret 파일의 root:root 0600과 구분한다.
`sudo -u tomcat test -r /opt/tomcat/conf/Catalina/localhost/scuredweb.xml || exit 1` 실패 시 배포 중단.
`namei -l /opt/tomcat/conf/Catalina/localhost/scuredweb.xml`로 부모 경로 traverse 권한도 확인한다. 내용을 cat하지 않는다.
