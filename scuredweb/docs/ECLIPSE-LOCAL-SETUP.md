# Eclipse 로컬 Tomcat 실행 복구

2026-09-28 확인: 실행 중인 Tomcat은 C:/tomcat-11 (화면 버전 11.0.18), Eclipse base는 C:/Users/gkrjs/eclipse-workspace/.metadata/.plugins/org.eclipse.wst.server.core/tmp0다. Eclipse의 과거 모듈 이름 redweb은 실제 /scuredweb Context에 매핑되어 있어 폴더 이름만으로 오류라고 볼 수 없다.

확인한 장애 조건: jdbc/RedWebDB JNDI Resource 없음, 실행 인자/Eclipse Environment/사용자·시스템 환경에 REDWEB_DB_* 없음, Tomcat lib에 Oracle JDBC 없음. DBConfig.validateConfiguration은 DB 미설정/연결 실패 시 앱 시작을 중단하므로 Tomcat 자체가 실행되어도 /scuredweb 및 /scuredweb/home은 404가 된다. Console의 실제 시작 예외도 확인해야 한다.

## 적용한 조치

Maven 로컬 저장소의 ojdbc17 23.26.3.0.0을 C:/tomcat-11/lib/ojdbc17-23.26.3.0.0.jar로 복사하고 SHA256 일치를 확인했다. 앱 코드·WAR·운영 보안 설정·DB는 변경하지 않았다. 새 드라이버 적용에는 Tomcat 전체 재시작이 필요하다.

## Eclipse 개발 환경 설정

1. Servers에서 Tomcat을 중지한다.
2. 서버 더블클릭 → Open launch configuration → Environment에서 아래 키에 **본인의 로컬 Oracle 설정**을 입력한다. 비밀번호를 채팅/스크린샷/보고서에 붙이지 않는다.
   - REDWEB_DB_URL: 실제 JDBC URL (예시 형식 jdbc:oracle:thin:@//localhost:1521/실제서비스명)
   - REDWEB_DB_USERNAME: 프로젝트 테이블을 소유한 개발 DB 사용자
   - REDWEB_DB_PASSWORD: 그 사용자의 비밀번호
3. 로컬 direct JDBC 방식에서는 REDWEB_REQUIRE_JNDI=true를 지정하지 않는다. 운영용 HTTPS env 예시를 Eclipse에 그대로 가져오지 않는다. 개발 기본값에서는 PUBLIC_BASE_URL/registration 설정을 생략할 수 있다.
4. Apply → Servers의 Clean/Publish → Tomcat 재시작 → http://localhost:8080/scuredweb/home 확인.

이 방식은 원래 허용된 개발용 direct JDBC fallback을 사용하는 것이다. OCI production은 REQUIRE_JNDI=true를 그대로 유지한다. 로컬에서도 JNDI를 사용하려면 Eclipse가 관리하는 서버 설정에 jdbc/RedWebDB Resource를 구성해야 하며 tmp0 생성 파일만 임의 수정하면 publish 때 덮어써질 수 있다.

## DB schema 확인

최종 보완 버전은 RW_USER.AUTH_VERSION이 필요하다. 연결 복구 후 이전 DB에 컬럼이 없다면 로그인 등에서 SQL 오류가 발생할 수 있다. 이 문서 작성 시 migration은 실행하지 않았다.

개발 DB owner로 아래 COUNT 확인(비밀번호/hash 출력 없음):

```sql
SELECT COUNT(*) AS AUTH_VERSION_COLUMN_COUNT
FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'RW_USER' AND COLUMN_NAME = 'AUTH_VERSION';
```

0이면 백업 및 대상 DB를 확인한 뒤 src/main/resources/sql/migrations/20260928-auth-version.sql을 1회 적용한다. 1이면 재적용하지 않는다. 전체 scuredweb-schema.sql을 기존 DB에 실행하지 않는다.

## 계속 404인 경우

Eclipse Console에서 Tomcat 시작 직후의 첫 SEVERE/심각 및 Caused by 메시지를 확인한다. DB 설정 오류, 리스너 실패, 컴파일 오류를 구분해야 한다. 공유할 경우 DB 비밀번호·토큰·연결 secret은 지운다. 이미 시작된 Tomcat에 단순 브라우저 새로고침만 해서는 실패한 앱이 재시작되지 않는다.

로컬 11.0.18의 현재 404를 버전 차이만으로 단정하지 않는다. 최종 패키지의 JSP 컴파일 검증은 11.0.26에서 수행했으므로, 배포 전에는 목표 버전으로 별도 검증한다.
