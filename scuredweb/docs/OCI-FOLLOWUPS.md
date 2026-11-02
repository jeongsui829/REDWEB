# OCI 후속 검증 및 쿼리 개선 계획

## 최소권한 계정: 설계만 제공, 실제 DB 미변경

현재 bootstrap/schema는 같은 runtime 계정이 테이블/sequence를 소유하는 구조다. REDWEB_OWNER(DDL·migration 전용), REDWEB_APP(CREATE SESSION와 필요한 object DML/sequence SELECT)로 분리하는 초안은 deploy/oci/sql/runtime-grants-DRAFT.sql에 있다. 모든 문장은 주석이며 자동 실행되지 않는다. SQL statement inventory로 INSERT/UPDATE/DELETE가 필요 없는 테이블 권한을 더 줄인 뒤 승인해야 한다. DBA가 private synonym을 설치하고 APP에는 DDL 권한을 주지 않는다.

현재 UserDAO/AuthDAO의 USER_TAB_COLUMNS, USER_CONSTRAINTS, USER_CONS_COLUMNS 등은 연결 사용자 소유 schema를 전제한다. synonym만 추가하고 JNDI user를 바꾸면 optional-column/FK allowlist 검사에 실패할 수 있다. owner-qualified ALL_* 조회 또는 검증된 schema metadata 전략을 staging에서 구현·검증하기 전 runtime 계정을 전환하지 않는다. AUTH_VERSION migration은 기존 owner에 먼저 적용한다. 기존 데이터 이동은 Data Pump 또는 검증된 복구 계획, 모든 FK/sequence nextval, grants, 연결 pool 재시작, rollback을 포함하는 별도 배포다.

## 실제 unbounded query 목록

| 코드 위치/메서드 | 현재 문제 | 계획 |
| --- | --- | --- |
| BoardDAO.findBoardPosts | 게시판 전체 목록 | board/status 필터 후 PINNED_YN DESC, CREATED_AT DESC, POST_ID DESC; OFFSET/FETCH |
| BoardDAO.searchBoardPosts/searchPosts | title LIKE 전체 결과 | 검색 길이 제한 유지, 일치 필터 후 CREATED_AT DESC, POST_ID DESC; query plan/대규모 검색 별도 검토 |
| BoardDAO.findAllPostsForAdmin | 전체 게시글 | status/board 필터 및 POST_ID 보조 정렬, 페이지 20~100 제한 |
| UserDAO.findAllUsers | 전체 사용자 | USER_ID ASC 기반 페이지/검색 |
| UserDAO.findPostsByUserId/findCommentsByUserId | 마이페이지 모든 작성 목록 | USER_ID + CREATED_AT DESC + POST_ID/COMMENT_ID DESC |
| SocialDAO.findReports | 신고 전체 | STATUS + CREATED_AT DESC + REPORT_ID DESC |
| SocialDAO.findMessages/findNotifications | 쪽지/알림 전체 | 수신자 + CREATED_AT DESC + MESSAGE_ID/NOTIFICATION_ID DESC |
| SocialDAO.findGuestbookEntries | 방명록 전체 | owner + CREATED_AT DESC + ENTRY_ID DESC (실제 PK명 확인 후 구현) |
| CommentDAO.findCommentsByPostId | 전체 댓글 트리 | root 단위 페이지와 그 하위 트리 묶음; 자식만 잘리는 단순 OFFSET 금지 |
| UserDAO.findAttachmentSavedNamesForUserDeletion | 탈퇴 전체 파일 목록 | streaming/batch cleanup 설계; 부분 삭제 보상 정책과 함께 변경 |

BoardDAO 최신/인기 글과 태그, UserDAO 로그인 기록은 이미 FETCH FIRST 상한이 있다. 상세 첨부 조회도 원래 데이터에 대량 첨부가 있을 수 있어 읽기 상한은 legacy 데이터 검토 후 결정한다.

Oracle OFFSET ? ROWS FETCH NEXT ? ROWS ONLY를 PreparedStatement로 바인딩하고 page>=1, pageSize 상한을 검증한다. offset은 overflow-safe long으로 계산하고 과도한 offset을 제한한다. COUNT와 페이지가 다른 transaction snapshot이면 변동 가능성을 허용하거나 snapshot 정책을 정한다. 삭제/추가 동시성, 마지막 페이지, 동일 timestamp, 한글/escape 검색, 권한 필터 전 pagination 여부를 테스트한다. 데이터 규모가 커지면 POST_ID 기반 keyset pagination을 검토한다.

현재 MyPage GET은 section과 무관하게 notification/login history/post/comment를 모두 읽고 overview count도 list.size로 계산한다. section별 필요한 목록만 로드하고 별도 COUNT query로 카드 숫자를 유지하도록 후속 변경한다. 공용 header도 전체 notifications 및 unread count 두 종류를 매 요청마다 읽는다. 화면에 실제 표시하는 최근 알림 수만 FETCH하고 unread COUNT를 요청 내 한 번만 재사용하는 DTO로 묶는다. 현재 UI·데이터 계약을 한 번에 바꾸지 않기 위해 이번 작업은 설계로 남겼다.

## Oracle index 검토 후보

기존 IX_RW_POST_BOARD_FEED, IX_RW_POST_USER_DATE, IX_RW_COMMENT_POST_STATUS/PARENT, IX_RW_ATTACHMENT_*, IX_RW_MESSAGE_UNREAD, IX_RW_NOTIFICATION_UNREAD, IX_RW_GUESTBOOK_OWNER_DATE, IX_RW_REPORT_STATUS_DATE가 있다. 이미 있는 인덱스를 중복 생성하지 않는다.

후보: feed의 POST_ID 보조 정렬, (USER_ID, CREATED_AT, COMMENT_ID), (STATUS, CREATED_AT, REPORT_ID), 메시지/알림 PK 보조 정렬, LOWER(NICKNAME) 기능 기반 인덱스. 실제 cardinality/DBMS_XPLAN/쓰기 비용을 확인하고 필요한 것만 additive migration으로 추가한다. 앞쪽 wildcard title LIKE는 일반 B-tree만으로 해결되지 않는다. Oracle Text는 기능·권한·운영 비용 검토 없이 도입하지 않는다.

## 의도적 보류

- 공통/breached password denylist: 외부 비밀번호 조회 API를 추가하지 않았다. 라이선스·업데이트·오탐 정책을 정한 로컬 목록 또는 privacy-preserving 조회를 별도 검토한다.
- 익명 JSP 세션 전면 제거: guest-online/CSRF/flash/header 의존을 보존하고 Context Manager cap과 timeout/heap 측정 절차를 제공했다.
- 공유 limiter와 영속 보안 event pipeline: 현재 단일 JVM의 메모리 budget을 유지한다. 노드 확장 전에 원자적 공유 저장소가 필요하다.
- 실제 email provider: 임의 SMTP 연결을 추가하지 않았다. production unavailable 및 관리자 지원 절차를 명시했다.
- global upload DB quota: 단순 SUM race를 도입하지 않았다. 기존 사용자 lock/quota와 별도 volume 한도를 유지한다.
- audit/security retention 기간: 임의 purge하지 않고 user anchor와 기록을 남긴다. 개인정보 보존·권한·삭제 요청 정책 결정이 필요하다.
- 실제 Oracle/TLS/OCI mount·reboot·복구·부하: 이 로컬 검증으로 수행됐다고 주장하지 않는다.
