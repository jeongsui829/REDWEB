# scuredweb UI → testredweb 선택 병합 결과

최종 프로젝트는 `redweb` / `com.redweb`인 기존 `testredweb`입니다. 원본 ZIP의 생성물·운영 파일은 이식하지 않았습니다. 새 디자인 없이 참조 CSS와 화면 구조를 사용했습니다.

## 1. 가져온 CSS

다음 8개 파일은 scuredweb 원본과 바이트 단위로 일치합니다.

- `src/main/webapp/assets/css/admin/admin.css`
- `src/main/webapp/assets/css/common.css`
- `src/main/webapp/assets/css/components.css`
- `src/main/webapp/assets/css/layout.css`
- `src/main/webapp/assets/css/pages/auth.css`
- `src/main/webapp/assets/css/pages/board.css`
- `src/main/webapp/assets/css/pages/home.css`
- `src/main/webapp/assets/css/pages/user.css`

추가한 `assets/css/editor-compat.css`는 기존 raw HTML 편집 div에 참조 textarea의 동일한 테두리·색상·간격·포커스 스타일을 적용하는 호환 파일입니다. 8개 원본 CSS는 수정하지 않았습니다.

## 2. 병합한 JSP

각 파일을 양측 비교한 뒤 서버 초기화·폼 계약·원시 출력 지점을 보존하여 병합했습니다. 원본 비교는 `side-by-side.html`, 최종 차이는 각 `*.final.diff`에 있습니다.

- `admin/dashboard.jsp`
- `admin/notices.jsp`
- `admin/posts.jsp`
- `admin/reports.jsp`
- `admin/users.jsp`
- `admin/verify.jsp`
- `auth/find-id.jsp`
- `auth/find-password.jsp`
- `auth/login.jsp`
- `auth/register.jsp`
- `auth/reset-password.jsp`
- `auth/verify-email.jsp`
- `board/detail.jsp`
- `board/edit.jsp`
- `board/list.jsp`
- `board/write.jsp`
- `common/admin-header.jsp`
- `common/admin-sidebar.jsp`
- `common/footer.jsp`
- `common/header.jsp`
- `common/sidebar.jsp`
- `error/404.jsp`
- `error/500.jsp`
- `home/index.jsp`
- `search/results.jsp`
- `user/messages.jsp`
- `user/mypage.jsp`
- `user/profile.jsp`

## 3. 정적 리소스와 JavaScript

- `common/head.jspf`: favicon 연결. 기존 `/` servlet 매핑을 보존하기 위해 `/assets/favicon.svg`로 연결합니다.
- `assets/js/board-detail.js`: 참조 답글 toggle. 기존 댓글 endpoint/필드/권한 조건 그대로 유지합니다.
- `assets/images/profile-default.png`, `favicon.svg`: 참조 파일 그대로 추가. 동일 SVG를 `assets/favicon.svg`에도 배치합니다.
- `assets/js/common.js`: 기존 코드에 검색 단축키, 모바일 게시판 메뉴, 확인 UI, 알림 접근성 상태, 온라인 표시만 선택 병합했습니다. 기존 신고 GET 링크 및 알림 POST 요청 본문은 유지합니다.
- `assets/js/editor-compat.js`: 기존 편집기의 raw HTML 제출·이미지 marker 처리만 분리했습니다. 기존 HTML 실행/출력 문맥을 보존합니다.

## 4. 이식한 추가 기능

- 온라인 세션 집계/API/리스너: 최근 2분의 활성 세션, 회원·게스트 수, 회원 중복 제거, 15초 화면 갱신.
- 홈의 3열 배치·최신/인기 글·태그·시계: 기존 HomeController가 제공하던 데이터에 연결했습니다.
- 게시판 인기글: 기존 BoardService.getTrendingPosts()를 JSP에서 읽기 전용 호출합니다. 기존 BoardController/DAO는 변경하지 않았습니다.
- 기본 프로필 경로: 기존 seed의 `/assets/images/profile-default.png`만 contextPath를 보완합니다. 사용자 지정 URL·업로드 경로는 그대로 출력합니다.

## 5. 이식하지 않은 기능과 이유

- CsrfFilter/CsrfUtil, SecurityHeadersFilter, AuthorizationUtil, PasswordUtil, RateLimitService, HtmlUtil, InputValidator, FileValidationUtil: 기존 교육용 취약점 동작을 바꾸므로 제외.
- FileImageController/FileDAO/StoredFileCleanup: 파일 검증·권한·정리 처리와 연결되므로 제외. 기존 업로드/다운로드를 유지.
- 계정 복구 notifier 및 새 재설정 흐름: 기존 보안 질문/답변·세션 흐름을 유지해야 하므로 제외.
- AppConfig/HealthController/AdminAuditDAO/DataAccessException/RedirectUtil 등 운영·설정·감사 기반: UI 이식에 필요하지 않거나 기존 동작에 영향을 주어 제외.
- 전체 추가 Java 파일별 분류는 `analysis.md`에 기록했습니다.

## 6. Java 변경 목록

기존 Java 61개 수정 없음. 다음 3개만 package를 com.redweb으로 바꾸어 추가했습니다.

- `src/main/java/com/redweb/common/listener/OnlineUserListener.java`
- `src/main/java/com/redweb/common/util/OnlineUserTracker.java`
- `src/main/java/com/redweb/social/controller/OnlineUserController.java`

## 7. 보안 강화 유입 감사

- 기존 Java/리소스/pom.xml/web.xml 총 68개 파일이 작업 전 원본과 바이트 단위로 동일합니다.
- 변경 JSP/JS에서 신규 CSRF·HtmlUtil·c:out·sanitization·입력/파일 검증 처리가 없음을 확인했습니다.
- 최종 WAR에 com.scuredweb, QA fixture, 신규 보안 필터·유틸이 포함되지 않았음을 확인했습니다.
- 원시 출력 식의 차이는 문구·기본 표시값·동일 조건의 변수화·동적 메뉴 활성 표시입니다. 기존 사용자 데이터 출력은 escaping 없이 유지했습니다.

## 8. Maven build

`mvn clean package`: **BUILD SUCCESS**. Java 25, 64개 Java 소스 컴파일, `target/redweb.war` 생성. 최종 빌드 로그: `maven-build.log`.
일반 Maven 테스트 소스는 기존 프로젝트에 없으므로, JSP 실행과 신규 온라인 기능 검증을 별도로 수행했습니다.
WAR SHA-256: `a08e11576a3942be0c315b9042b4a567f288b030dfc7e15aa14c71adad2d715d`

## 9. 실행·시각 검증

- 격리 Tomcat 11, 127.0.0.1:18080에서 최종 애플리케이션 실행.
- 양쪽 JSP 28개 + 마이페이지 세부 화면 7개씩: 총 70회 HTTP 200 및 JSP 실행/컴파일 성공.
- 최종 앱 Home/Login/Register/Board List/Search/404/500/온라인 API/favicon: 9개 GET 응답 확인. 오류 화면의 직접 URL은 기존 동작대로 HTTP 200입니다.
- 전체 화면 JSP 23개를 데스크톱 1440px에서 참조와 캡처 비교. 공통 fragment 5개는 포함 페이지 및 직접 렌더링으로 확인.
- 요청에 명시된 17개 화면을 모바일 390px에서 양쪽 비교: 가로 넘침·깨진 이미지 없음. 참조에 존재하는 좁은 화면의 헤더 문구 줄바꿈은 그대로 유지.
- 모바일 게시판 메뉴, 답글 열기/포커스, `/` 검색 단축키 확인.
- 온라인 집계·회원/게스트 분리·회원 중복 제거·세션 ID 변경·세션 제거·2분 만료 검증 통과 (`online-check.txt`).
- 2026-09-10 재검증: 중지된 Oracle XE/리스너 서비스를 시작하여 ORA-12541을 해결했습니다. 기존 DB 연결 및 회원 13행·게시글 20행 조회 성공. 실제 홈·자유게시판(15개 글)·검색(RedWeb 검색 결과 1건)을 브라우저에서 확인하고 79개 HTTP 검증도 다시 통과했습니다. 증거: `database-retry.txt`, `database-ui-retry.json`, `screenshots/live-home.png`, `screenshots/live-board-list.png`, `screenshots/live-search.png`.
- **제한:** 프로젝트 샘플 로그인 정보가 현재 DB와 일치하지 않습니다. 실제 DB 기반 로그인·게시글 CRUD·업로드/다운로드·관리 작업 및 취약점 재현의 end-to-end 실행은 검증하지 못했습니다. 기존 계정·비밀번호·데이터는 변경하지 않았습니다. 보호된 화면 비교는 별도 DTO fixture와 읽기 전용 service stub을 사용했으며 이 코드는 최종 WAR에 없습니다.
- 참조/이식 캡처: `visual-comparison.html`, 실행 결과: `http-results.json`, 모바일 결과: `mobile-results.json`.

## 10. 기존 취약점 보존 및 최종 판정

AuthDAO/BoardDAO/SearchController/게시글 CRUD/댓글 DAO·Service·Controller/FileDownloadController/UserDAO/기존 필터와 DB schema 모두 바이트 동일입니다. 전체 JSP의 form action/method/enctype, 이름 있는 입력의 name/type/value/multiple/accept 계약 비교도 동일합니다. GET 로그아웃·GET 신고, savedName + originalName 다운로드 URL, raw 게시글·댓글·검색·방명록·쪽지·프로필 출력 및 raw HTML 편집 문맥을 유지했습니다.

| 조건 | 판정 | 범위 |
|---|---|---|
| Frontend parity with scuredweb | 충족 — 디자인 체계·레이아웃 기준 | 8개 CSS 원본 동일, 화면 비교 완료. 기존 보안 질문/이메일 폼/회원 정보/HTML 편집 등 백엔드 보존에 필요한 차이는 의도적으로 유지. 픽셀 단위 완전 동일을 뜻하지 않음. |
| Backend parity with vulnerable testredweb | 충족 — 원본 코드·요청 계약 기준 | 기존 Java/설정/SQL 동일, XSS 원시 출력 및 폼 계약 보존. 온라인 API만 독립 추가. DB 기반 전체 실행 검증은 미완료. |

두 조건은 위 코드·UI 이식 범위에서 충족합니다. 실제 DB 연결 후의 전체 서비스 동작까지 검증 완료라고 판정하지는 않습니다.
