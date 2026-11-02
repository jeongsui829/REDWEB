# 소스 가져오기 및 정리 내역

원본: 사용자가 제공한 `redweb 최종본.zip` / 정리일: 2026-11-02

## 폴더 매핑

- `취약버전 REDWEB - 복사본/testredweb/` → `redweb/`
- `scuredweb/` → `scuredweb/`

## 포함·제외 기준

- Java·JSP·CSS·JavaScript·SQL, Maven 설정, 테스트, 실제 문서, 배포 예제를 포함했습니다.
- Eclipse 프로젝트 설정처럼 내용이 있는 개발 파일은 유지했습니다.
- 0바이트 파일과 빈 폴더는 포함하지 않았습니다. Git은 파일이 없는 폴더를 저장하지 않습니다.
- `target/`, `dist/`, `.porting/`, `.work-*`의 빌드 결과·중복 소스·임시 작업 자료는 제외했습니다.
- 생성된 WAR·JAR·CLASS·로그·중복 ZIP은 제외했습니다.
- 개인 실습 메모와 생성된 HTTP 캡처·브라우저 화면·실행 결과 JSON/HTML은 공개 저장소에서 제외했습니다. 실행 증거가 필요하면 별도 마스킹 후 추가합니다.
- 원본 ZIP은 변경하지 않았습니다. 이는 소스 정리본이며 원본의 바이트 단위 전체 백업과는 구분됩니다.

## 비밀 값 처리

1. REDWEB의 실제 `jdbc.properties`는 제외하고 비밀번호 없는 예제 파일을 추가했습니다. DBConfig의 설정 읽기 방식은 변경하지 않았습니다.
2. 다음 여섯 파일에 포함된 샘플 계정 비밀번호·해시 리터럴만 로컬 설정용 자리표시자로 변경했습니다.

   - `redweb/src/main/java/com/redweb/common/util/SampleDataFactory.java`
   - `redweb/src/main/resources/sql/create-users-3-6.sql`
   - `redweb/src/main/resources/sql/redweb-db-upgrade.sql`
   - `redweb/src/main/resources/sql/redweb-seed.sql`
   - `scuredweb/src/main/resources/sql/create-users-3-6.sql`
   - `scuredweb/src/main/resources/sql/scuredweb-seed.sql`

그 외 가져온 소스 파일은 원본 ZIP과 바이트 단위로 대조했습니다. 실제 비밀번호·인증 토큰·쿠키 값은 정리 기록에 기재하지 않습니다.

## 검증 범위

- ZIP 경로 안전성, 파일 제외 기준, 빈 파일·빈 폴더 여부 확인
- 알려진 비밀번호 및 샘플 인증 값의 제거·치환 확인
- 파일 경로 및 Git blob SHA를 이용한 업로드 내용 대조
- 빌드·DB 연결·배포·웹 취약점 재점검은 이번 소스 저장 작업의 검증 범위에 포함하지 않음
