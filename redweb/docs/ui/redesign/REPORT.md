# REDWEB UI 개선 결과

2026-09-09 · 기존 JSP / Servlet / Oracle 구조 유지

## 1. 분석한 프로젝트 구조

- Maven WAR 프로젝트, Java 소스 61개, JSP 28개, CSS 8개, 공통 JS 2개.
- `src/main/java/com/redweb`: auth, board, comment, user, social, admin, search, file 및 공통 처리. Controller → Service → DAO 구조이며 `ViewUtil.forward()`가 `/WEB-INF/views/{view}.jsp`로 연결한다.
- `src/main/webapp/WEB-INF/views`: home, board, search, auth, user, admin, error 화면과 common include.
- 공개 화면: common.css → layout.css → components.css → 페이지 CSS 순서. 로그인 등 일부 화면은 components.css 없이 동작한다.
- 관리 화면: common.css와 admin/admin.css, 별도 admin-header/admin-sidebar include.
- 마이페이지 하위 메뉴와 알림은 `user/mypage.jsp`의 `currentSection`에 따라 표시된다.
- 게시판은 `posts`, `currentBoard`, `keyword`, `sort`, 상세는 `post`, `comments`, `canManagePost` 등의 기존 request attribute를 사용한다.
- JSP 내부에 별도 디자인용 style 블록은 없으며, 편집기 색상 팔레트의 inline style 및 편집기가 생성하는 콘텐츠 스타일은 기존 그대로 보존했다.
- 원본에는 게시판 페이지네이션 및 목록 작성일 표시가 구현되어 있지 않다. 관련 기능이나 조회 컬럼을 새로 추가하지 않았다.

## 2. 수정한 파일

소스 파일 26개: CSS 8개, JSP 18개. 기타 소스 파일 79개와 pom.xml은 바이트 단위로 동일하다.

| 파일 그룹 | 수정 파일 | 목적 |
| --- | --- | --- |
| 공통 CSS | assets/css/common.css, layout.css, components.css | 색상·간격·글꼴·입력·버튼·헤더·푸터·카드 |
| 페이지 CSS | assets/css/pages/home.css, board.css, auth.css, user.css | 홈·게시판·검색·계정·프로필·알림·쪽지 |
| 관리자 CSS | assets/css/admin/admin.css | 메뉴와 콘텐츠 분리, 표, 상태 배지, 반응형 |
| 홈·푸터 | views/home/index.jsp, views/common/footer.jsp | 서비스 소개 문구와 제목 줄바꿈 |
| 관리자 5개 | views/admin/dashboard.jsp, users.jsp, posts.jsp, reports.jsp, notices.jsp | body에 정적 페이지 class 추가 |
| 헤더 스크립트 3개 | views/auth/login.jsp, register.jsp, views/admin/verify.jsp | 기존 common.js 연결 |
| viewport 8개 | views/auth/find-id.jsp, find-password.jsp, reset-password.jsp, verify-email.jsp, views/board/write.jsp, edit.jsp, views/error/404.jsp, 500.jsp | 모바일 실제 화면 폭 반영 |

표의 assets 및 views 경로 기준은 각각 `src/main/webapp`과 `src/main/webapp/WEB-INF`다. 전체 변경 파일 목록은 preservation.json, 원본 대비 전체 변경은 changes.diff에 기록했다. 이 보고서와 검증 JSON·로그·스크린샷은 docs/ui/redesign에 있으며 WAR에 포함되지 않는다.

## 3. 주요 UI 변경

- 밝은 회색 배경, 흰 콘텐츠 영역, 짙은 네이비 텍스트와 절제된 레드 포인트를 사용했다. 과도한 그라데이션, 그림자, 큰 pill 모양을 정리했다.
- :root 변수로 색상, 여백, 모서리, 그림자를 중앙 관리한다. 기본 글자 15px, 보조 정보 12~14px, 버튼 40px 기준으로 통일했다.
- 공통 헤더의 280px 고정 열을 제거하고 실제 콘텐츠에 맞게 정렬했다. 데스크톱 헤더만 sticky이며 작은 화면에서는 일반 흐름으로 표시한다.
- 검색·알림 팝오버의 모바일 폭 제한, 드롭다운 레이어, 키보드 포커스 표시를 보완했다.
- 홈의 최소 530px 강제 높이를 없애고 소개, 메뉴, 커뮤니티 안내의 위계를 정리했다. 모든 기존 메뉴 링크를 유지했다.
- 게시판과 검색 결과의 제목·요약·작성자·카테고리·조회·추천 정보 정렬을 통일했다. 560px 검색창 최소 너비를 제거했다.
- 상세 화면의 제목, 메타 정보, 본문, 코드, 표, 첨부파일, 액션, 댓글/대댓글을 구분했다. 상세 카드와 콘텐츠에는 sticky/fixed를 추가하지 않았다.
- 원본에서 sticky는 공통 헤더에만 있었다. fixed 사용자 컨텍스트 메뉴는 기존 기능이므로 유지했다.
- 편집기의 도구·본문·이미지 영역을 정리했다. 숨겨진 업로드 input에 일반 파일 input의 100% 너비가 적용되어 가로 넘침이 발생하는 충돌을 해소했다.
- 프로필의 아바타 열을 실제 아바타 폭으로 지정하고 정보가 넓은 열에 놓이도록 수정했다. 통계·활동·프로필 폼·알림·쪽지를 같은 스타일로 구성했다.
- 관리자 메뉴의 현재 위치를 표시하고 콘텐츠와 분리했다. 모바일 메뉴가 남은 화면 높이에 맞춰 과도하게 늘어나는 문제를 해결했다.
- 관리자 표 및 본문 내 넓은 표는 자체 가로 스크롤을 사용한다. 긴 제목·이름·파일명은 줄바꿈한다.
- 새 프레임워크, 외부 폰트, UI 라이브러리, 이미지 의존성을 추가하지 않았다.

## 4. JSP 구조 변경

기존 scriptlet, 선언, EL, 조건·반복문, include 및 폼 태그를 그대로 보존했다. 추가한 것은 viewport 8개, 관리자 body class 5개, 기존 common.js 로딩 3개뿐이며 홈·푸터의 정적 표시 문구를 수정했다.

로그인·회원가입·관리자 인증 페이지는 공통 헤더를 포함하지만 common.js를 로딩하지 않아 검색 버튼이 동작하지 않는 상태였다. 기존 JS 파일을 연결해 UI 동작을 복구했다. common.js와 각 JSP의 inline JS 자체는 변경하지 않았다.

## 5. 기존 기능 보존 확인

원본 src 및 pom.xml 백업을 만든 뒤 파일 바이트, SHA-256, JSP 토큰과 HTML 속성 비교를 수행했다.

- Java, SQL, DAO, Controller, Filter, Listener, 서비스, 설정, 공통 JS: 변경 없음.
- 기존 form/input/textarea/select/option/button 시작 태그: 모두 동일.
- 기존 action, method, name, id, value, enctype, required, accept 등 폼 설정: 동일.
- 기존 href/src 및 URL parameter: 동일. 새 로딩은 위의 기존 common.js 3곳뿐.
- JSP 서버 토큰, EL, inline JavaScript, JSP include: 동일. common.js 경로에 추가한 contextPath 출력만 예외로 분리 검증.
- src 파일 추가·삭제 없음. pom.xml, 의존성 및 JDK 설정 변경 없음.

검증 결과: preservation.json, source-hashes.json, changes.diff.
원본 백업: C:\Users\gkrjs\AppData\Local\Temp\redweb-ui-original-20260909-163659

## 6. 취약점 실습 코드 보존

AuthDAO의 로그인 문자열 결합 SQL, BoardDAO의 검색 문자열 결합 및 상세 ID 식 처리, insertPost와 기존 CRUD는 그대로다. searchPostQueryLite의 SELECT는 다음 7개 컬럼과 순서를 유지한다.

1. P.POST_ID
2. P.USER_ID
3. P.TITLE
4. DBMS_LOB.SUBSTR(P.CONTENT, 1000, 1) AS CONTENT
5. P.VIEW_COUNT
6. U.NICKNAME
7. B.BOARD_NAME

게시글·댓글·검색어 등의 기존 직접 출력, 업로드/다운로드 처리, 인증·인가·세션·쿠키 로직을 변경하지 않았다. escaping, sanitization, CSRF 토큰, 보안 필터, CSP, 파일 검증 또는 권한 검사를 추가하지 않았다. 실제 공격 페이로드를 이용한 취약점 재현 검증은 DB 부재로 수행하지 않았다.

## 7. 빌드 결과

- 최종 `mvn clean package`: BUILD SUCCESS. Java 61개 컴파일, target/redweb.war 생성.
- Java 25.0.2, Maven 3.9.14, 기존 pom.xml 그대로 사용.
- 프로젝트에 자동 단위 테스트가 없어 Maven은 No tests to run으로 표시한다. 별도로 아래 UI 검증을 수행했다.
- Maven 기본 WAR 패키징만으로 JSP 컴파일을 단정하지 않고, 설치된 Tomcat 11.0.18의 Jasper JspC로 전체 JSP를 추가 컴파일했다.
- 최종 Jasper: 0 errors, JSP class 28개 생성. class 출력은 임시 디렉터리에만 저장했다.
- 로그: build.log, jsp-compile.log.
- WAR SHA-256: 567092FF6623F51BA8A4461573C11DF7227830FD47E40E1AFB8D645B7BC481C4

## 8. 실행 및 화면 검증

기존 Tomcat 설정을 수정하지 않고 임시 CATALINA_BASE를 만들어 127.0.0.1:18080에서 실행했다. Edge headless 브라우저를 사용했다.

| 검증 | 결과 |
| --- | --- |
| 실제 공개 경로 | 홈, 로그인, 가입, 계정 찾기, 재설정, 이메일 인증, 게시판 목록, FAQ, 작성, 검색 화면 200 |
| 접근 흐름 | 비로그인 마이페이지·관리자·쪽지는 기존 로그인 이동 유지 |
| 상세·수정 실제 조회 | Oracle 데이터 조회 불가로 예제 id=101은 기존 404 반환 |
| 화면 표시 | 31개 화면/상태 × 320·390·768·1024·1440px = 155개 검사 통과 |
| 긴 데이터 | 긴 제목, 이름, 파일명, 본문, 넓은 표에서 문서 전체 가로 넘침 없음 |
| 자산·브라우저 오류 | 최종 검사에서 CSS/JS 요청 오류와 JavaScript 예외 없음 |
| 헤더 | 드롭다운, 검색 열기 및 Escape 닫기 확인. 계정 페이지 3곳에서도 확인 |
| 상세 | 답글 폼 열기, 첨부 링크 parameter 보존, 카드 position:static 확인 |
| 편집기 | 코드 입력 영역·색상 팔레트 열기, 이미지 미리보기 확인 |
| 작성·수정 전송 | POST multipart/form-data, title/content/attachments/contentImages와 이미지 marker 보존 확인 |
| 관리자 표 | 모바일에서 표 영역만 가로 스크롤되는 것 확인 |

DB가 필요한 상세·수정·계정·관리자 화면은 임시 배포본에만 만든 샘플 DTO 주입용 JSP를 통해 실제 JSP를 렌더링했다. 원본의 SampleDataFactory를 활용했으며 이는 인증 성공이나 DB 연동 성공을 의미하지 않는다. 편집기 전송 검증은 브라우저에서 요청을 가로채 검사했고 서버에 저장하지 않았다. 전체 파일 다운로드, DB 저장·수정·삭제, 댓글 저장, 실제 인증 성공은 미검증이다.

초기 샘플 프로필에는 존재하지 않는 /assets/images/profile-default.png가 지정되어 있었다. 백엔드 샘플 데이터는 수정하지 않고 임시 표시 테스트에서만 이미지 경로를 null로 두어 기존 R 아바타 대체 화면을 검증했다. 실제 DB 레코드의 이미지 경로 검증은 별도로 필요하다.

browser-results.json에 실제 URL/응답과 각 화면 폭 검사, interaction-results.json에 추가 UI 전송 검증을 기록했다. screenshots에는 홈·로그인·목록·상세·작성·마이페이지·관리자·쪽지의 데스크톱/모바일 캡처 18개가 있다. 샘플 화면에 표시된 데이터와 로그인 상태는 UI 검사용이다.

## 9. 발견했지만 변경하지 않은 사항

- 의도된 문자열 결합 SQL과 직접 HTML 출력, 파일 및 세션 관련 기존 실습 구조.
- Oracle localhost:1521 리스너 미실행: 실행 시 ORA-12541 확인. DB/Tomcat/JDK 설정은 변경하지 않았다.
- 기존 SampleDataFactory의 누락된 기본 프로필 이미지 경로.
- 이메일 인증 JSP의 기존 폼에 명시적 action과 인증 코드 name이 없는 상태. 이번 작업에서 인증 기능을 새로 구현하지 않았다.
- 원본 게시판의 페이지네이션·목록 작성일 미구현. 기능 및 SQL을 확대하지 않았다.

## 10. 후속 확인 사항

기존 Oracle 실습 DB를 실행한 상태에서 정상 회원/관리자 계정 로그인, 회원가입, 게시글 CRUD, 댓글/대댓글, 첨부 업로드/다운로드, 계정 관리 및 기존 취약점 재현 시나리오를 확인해야 한다. UI 측 전송 규약과 서버 코드 보존은 확인했으나 DB 연동 성공을 대신하지 않는다.

최종 결과물은 target/redweb.war다. UI 검증용 임시 서버는 작업 종료 시 중지하며, 샘플 주입 JSP와 테스트 도구는 소스 및 최종 WAR에 포함하지 않는다. 배포 구조 변경은 없다.
