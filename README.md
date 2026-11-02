# REDWEB · SCUREDWEB

Java/JSP 기반 웹 취약점 분석과 시큐어코딩 비교를 위한 졸업작품의 웹 애플리케이션 소스입니다.

- **REDWEB**: 취약점 재현과 원인 분석을 위한 실습 버전
- **SCUREDWEB**: 인증·권한·입력 및 파일 처리 등을 보완한 비교 버전

## 저장소 구조

```text
redweb/       취약 버전: Java, JSP, 정적 자원, Oracle SQL, 문서
scuredweb/    보안 강화 버전: 애플리케이션, 테스트, 배포 예제, 문서
infra/       VPS 관제 코드·설정 및 복원 범위 문서
tools/       관제 설정 수집 도구
docs/        원본 ZIP 정리 기준과 GitHub 저장 내역
```

각 앱은 독립적인 Maven WAR 프로젝트입니다. 회원가입·로그인, 게시판·댓글, 검색, 파일 처리, 마이페이지 및 관리자 기능의 소스를 포함합니다.

## 개발 환경

| 구분 | 구성 |
|---|---|
| 언어·웹 | Java, JSP, Jakarta Servlet, HTML, CSS, JavaScript |
| 빌드 | Maven, WAR |
| Java 버전 | 두 `pom.xml`의 `maven.compiler.release`는 **25** |
| Servlet / WAS | Servlet API 6.1 / Apache Tomcat 11 |
| 데이터베이스 | Oracle Database |
| 보안 버전 테스트 | JUnit Jupiter, Mockito, H2 |

## 로컬 설정 및 빌드

1. JDK 25 및 Maven을 준비합니다.
2. Oracle 접속 정보와 앱별 스키마를 로컬 환경에 구성합니다.
3. REDWEB은 `redweb/src/main/resources/properties/jdbc.properties.example`을 같은 폴더의 `jdbc.properties`로 복사하고 실제 접속 정보를 입력합니다. 실제 설정 파일은 Git에서 제외됩니다.
4. SCUREDWEB은 `scuredweb/docs/ECLIPSE-LOCAL-SETUP.md` 및 `scuredweb/deploy/`의 예제를 참고해 JNDI 또는 외부 DB 설정을 구성합니다. 저장된 `jdbc.properties`에는 접속 비밀번호가 없습니다.
5. 필요한 프로젝트 폴더에서 빌드합니다.

```powershell
cd redweb
mvn clean package
```

```powershell
cd scuredweb
mvn clean package
```

SCUREDWEB의 Oracle JDBC 드라이버는 `provided` 범위이므로 Tomcat 환경에 별도 제공해야 합니다. 배포 템플릿의 비밀번호 자리표시는 로컬에서 설정합니다.

## 공개본의 설정 및 샘플 데이터

- 원본 ZIP의 DB 비밀번호는 업로드하지 않았으며 REDWEB 설정은 `.example`로 분리했습니다.
- 샘플 사용자 비밀번호·해시 리터럴은 `LOCAL_DEMO_PASSWORD_REQUIRED`로 대체했습니다. 이 문자열은 실제 비밀번호로 사용하지 말고, SQL 시드 실행 전에 로컬 전용 값과 해당 버전에 맞는 저장 형식을 설정합니다.
- 샘플 계정·SQL은 실습용 자료입니다. 운영 DB에 일괄 실행하는 설치 스크립트로 취급하지 않습니다.
- 취약한 요청 처리 코드 등 비교 분석 대상은 보존했습니다. [정리 내역](docs/IMPORT_NOTES.md)에 변경 범위를 기록했습니다.

## 관제 시스템과의 관계

프로젝트 전체에는 Wazuh·Loki·Grafana 기반 보안 관제와 Python 상관분석·자동 대응 기능이 포함됩니다. VPS에서 수집·검토한 관제 코드와 설정은 [`infra/`](infra/README.md)에 추가했습니다. 방화벽 초기화·자동 대응 실행 파일 및 Grafana DB 설정 등 [추가 수집 항목](infra/MISSING_COMPONENTS.md)이 있어 완전한 서버 복구 백업과는 구분됩니다.

배포·점검 문서는 작성 당시 환경의 기록입니다. 이 최초 소스 저장 작업에서 애플리케이션 실행이나 DB 통합 테스트를 새로 수행하지는 않았습니다.

