-- Reference data for external testing. No users, credentials, posts, or activity logs.
MERGE INTO RW_BOARD T
USING (
    SELECT 1 BOARD_ID, 'free' BOARD_CODE, '자유게시판' BOARD_NAME, '일상 공유와 자유로운 대화를 위한 공간' DESCRIPTION, 1 SORT_ORDER FROM DUAL
    UNION ALL SELECT 2, 'daily-log', 'Daily 공부 로그', '하루 학습 기록과 복습 메모를 공유하는 공간', 2 FROM DUAL
    UNION ALL SELECT 3, 'ctf-qna', 'CTF 질문', 'CTF 문제 풀이 중 막힌 부분을 질문하는 공간', 3 FROM DUAL
    UNION ALL SELECT 4, 'project-share', 'Project 공유', '프로젝트 구현 경험과 결과물을 공유하는 공간', 4 FROM DUAL
    UNION ALL SELECT 5, 'qna', '질문답변', '구현, 오류, 구조에 대한 질문과 답변 공간', 5 FROM DUAL
    UNION ALL SELECT 6, 'owasp-top10', 'OWASP Top 10', '대표 웹 취약점을 주제별로 정리하는 공간', 6 FROM DUAL
    UNION ALL SELECT 7, 'security-news', '보안뉴스', '최신 보안 이슈와 공지 소식을 공유하는 공간', 7 FROM DUAL
    UNION ALL SELECT 8, 'hacking-basic', '해킹 기초 지식', '입문자를 위한 기초 보안 지식 공간', 8 FROM DUAL
    UNION ALL SELECT 9, 'linux-basic', 'Linux 기초', '보안 학습에 필요한 Linux 기초 공간', 9 FROM DUAL
    UNION ALL SELECT 10, 'network-basic', 'Network', '패킷, 포트, 프로토콜 등 네트워크 기초 공간', 10 FROM DUAL
    UNION ALL SELECT 11, 'web-basic', 'Web 기초', '브라우저, 서버, DB 등 웹 구조 기초 공간', 11 FROM DUAL
    UNION ALL SELECT 12, 'faq', 'FAQ', '자주 묻는 질문과 운영 안내 공간', 12 FROM DUAL
) S
ON (T.BOARD_ID = S.BOARD_ID)
WHEN NOT MATCHED THEN INSERT (BOARD_ID, BOARD_CODE, BOARD_NAME, DESCRIPTION, SORT_ORDER)
VALUES (S.BOARD_ID, S.BOARD_CODE, S.BOARD_NAME, S.DESCRIPTION, S.SORT_ORDER);

MERGE INTO RW_CATEGORY T
USING (
    SELECT 1 CATEGORY_ID, 1 BOARD_ID, '운영공지' CATEGORY_NAME, 1 SORT_ORDER FROM DUAL
    UNION ALL SELECT 2, 2, '학습기록', 1 FROM DUAL
    UNION ALL SELECT 3, 3, '문제풀이', 1 FROM DUAL
    UNION ALL SELECT 4, 4, '개발공유', 1 FROM DUAL
    UNION ALL SELECT 5, 5, '질문', 1 FROM DUAL
    UNION ALL SELECT 6, 6, '보안지식', 1 FROM DUAL
    UNION ALL SELECT 7, 7, '뉴스', 1 FROM DUAL
    UNION ALL SELECT 8, 8, '입문', 1 FROM DUAL
    UNION ALL SELECT 9, 9, '운영체제', 1 FROM DUAL
    UNION ALL SELECT 10, 10, '네트워크', 1 FROM DUAL
    UNION ALL SELECT 11, 11, '웹기초', 1 FROM DUAL
    UNION ALL SELECT 12, 12, '안내', 1 FROM DUAL
) S
ON (T.CATEGORY_ID = S.CATEGORY_ID)
WHEN NOT MATCHED THEN INSERT (CATEGORY_ID, BOARD_ID, CATEGORY_NAME, SORT_ORDER)
VALUES (S.CATEGORY_ID, S.BOARD_ID, S.CATEGORY_NAME, S.SORT_ORDER);

MERGE INTO RW_TAG T
USING (
    SELECT 1 TAG_ID, 'jakarta' TAG_NAME, 0 USE_COUNT FROM DUAL
    UNION ALL SELECT 2, 'oracle', 0 FROM DUAL
    UNION ALL SELECT 3, 'jsp', 0 FROM DUAL
    UNION ALL SELECT 4, 'owasp', 0 FROM DUAL
) S
ON (T.TAG_ID = S.TAG_ID)
WHEN NOT MATCHED THEN INSERT (TAG_ID, TAG_NAME, USE_COUNT)
VALUES (S.TAG_ID, S.TAG_NAME, S.USE_COUNT);

COMMIT;
