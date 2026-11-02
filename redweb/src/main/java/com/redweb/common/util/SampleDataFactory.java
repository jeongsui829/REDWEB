package com.redweb.common.util;

import com.redweb.admin.dto.DashboardMetricDTO;
import com.redweb.board.dto.BoardDTO;
import com.redweb.board.dto.PostDTO;
import com.redweb.board.dto.TagDTO;
import com.redweb.comment.dto.CommentDTO;
import com.redweb.notification.dto.NotificationDTO;
import com.redweb.user.dto.LoginHistoryDTO;
import com.redweb.user.dto.UserDTO;

import java.util.List;

public final class SampleDataFactory {
    private SampleDataFactory() {
    }

    public static List<PostDTO> posts() {
        return List.of(
                createPost(101L, "자유게시판", "운영공지", "RedWeb 이용 가이드와 운영 안내",
                        "게시판 이용 규칙과 첨부파일 업로드 기준, 커뮤니티 기본 흐름을 한 번에 확인할 수 있도록 정리했습니다.", "admin", 928, 74, 3, 16, true, true),
                createPost(102L, "Daily 공부 로그", "학습기록", "이번 주 취약점 분석 공부 로그",
                        "OWASP Top 10 중 Broken Access Control과 SQL Injection을 중심으로 복습한 내용을 기록했습니다.", "user1", 312, 25, 1, 6, false, false),
                createPost(103L, "CTF 질문", "문제풀이", "Boolean 기반 블라인드 SQLi 문제에서 기준점을 어떻게 잡나요?",
                        "응답 길이 차이가 보이는데 참과 거짓을 구분할 때 어떤 기준으로 테스트하면 좋은지 질문드립니다.", "user2", 221, 18, 0, 4, false, false),
                createPost(104L, "Project 공유", "개발공유", "RedWeb 1차 구조와 보안 점검 포인트 공유",
                        "Servlet, JSP, Oracle 기반 구조에서 먼저 확인해야 할 흐름과 운영 포인트를 프로젝트 기준으로 정리했습니다.", "admin", 355, 28, 0, 4, false, false),
                createPost(105L, "질문답변", "JSP", "JSP include 구조를 실무형으로 나누는 방법",
                        "공통 레이아웃과 페이지별 구성을 어떤 기준으로 분리하면 유지보수가 쉬운지 정리해봤습니다.", "user2", 401, 33, 2, 7, false, false),
                createPost(106L, "OWASP Top 10", "보안지식", "OWASP Top 10 2021 핵심 요약",
                        "A01부터 A10까지 실제 서비스 개발자가 먼저 이해해야 할 포인트만 추려서 정리했습니다.", "admin", 624, 41, 1, 9, false, false),
                createPost(107L, "보안뉴스", "뉴스브리핑", "이번 주 주요 보안 이슈 정리",
                        "국내외 보안 공지와 패치 권고, 침해사고 관련 소식을 보기 쉽게 정리했습니다.", "user1", 278, 19, 0, 3, false, false),
                createPost(108L, "해킹 기초 지식", "입문", "웹 해킹 입문자가 먼저 익혀야 하는 기본 개념",
                        "HTTP 요청 구조, 세션, 쿠키, 인증과 인가 차이를 초반 학습자 관점에서 설명했습니다.", "admin", 497, 31, 1, 8, false, false),
                createPost(109L, "Linux 기초", "운영체제", "보안 공부를 위한 Linux 기본 명령어 정리",
                        "파일 권한, 프로세스, 네트워크 상태 확인 명령을 초심자 기준으로 묶어봤습니다.", "user1", 246, 16, 0, 2, false, false),
                createPost(110L, "Network", "기초네트워크", "TCP와 UDP 차이를 처음 배우는 사람을 위한 설명",
                        "패킷 흐름, 포트, 연결형과 비연결형 차이를 그림처럼 이해할 수 있게 정리했습니다.", "user2", 289, 20, 0, 5, false, false),
                createPost(111L, "Web 기초", "웹기초", "웹 서비스 구조를 처음부터 이해하는 방법",
                        "브라우저, WAS, DB, 정적 리소스, 세션이 어떤 흐름으로 이어지는지 서비스 관점으로 설명했습니다.", "admin", 334, 22, 0, 4, false, false),
                createPost(112L, "FAQ", "이용안내", "자주 묻는 질문 모음",
                        "계정, 게시글, 파일 업로드, 관리자 접근과 관련해 가장 많이 묻는 질문을 정리했습니다.", "admin", 188, 12, 0, 2, false, false)
        );
    }

    private static PostDTO createPost(Long postId, String boardName, String categoryName, String title,
                                      String content, String author, int views, int likes, int dislikes,
                                      int comments, boolean notice, boolean pinned) {
        PostDTO post = new PostDTO();
        post.setPostId(postId);
        post.setBoardName(boardName);
        post.setCategoryName(categoryName);
        post.setTitle(title);
        post.setContent(content);
        post.setAuthorNickname(author);
        post.setUserId(resolveSampleUserId(author));
        post.setBaseViewCount(views);
        post.setViewCount(views);
        post.setBaseLikeCount(likes);
        post.setLikeCount(likes);
        post.setBaseDislikeCount(dislikes);
        post.setDislikeCount(dislikes);
        post.setCommentCount(comments);
        post.setNotice(notice);
        post.setPinned(pinned);
        post.setStatus("PUBLIC");
        return post;
    }

    private static Long resolveSampleUserId(String author) {
        return switch (author) {
            case "admin" -> 1L;
            case "user1" -> 2L;
            case "user2" -> 3L;
            default -> null;
        };
    }

    public static List<BoardDTO> boards() {
        return List.of(
                board(1L, "free", "자유게시판", "자유로운 대화와 운영 공지를 함께 확인하는 기본 커뮤니티 공간입니다."),
                board(2L, "daily-log", "Daily 공부 로그", "하루 학습 기록과 복습 메모를 남기고 공유하는 공간입니다."),
                board(3L, "ctf-qna", "CTF 질문", "CTF 문제 풀이 중 막히는 부분을 질문하고 토론하는 공간입니다."),
                board(4L, "project-share", "Project 공유", "진행 중인 프로젝트와 구현 경험을 공유하는 공간입니다."),
                board(5L, "qna", "질문답변", "구현, 설계, 오류 해결과 관련한 질문과 답변을 모아두는 공간입니다."),
                board(6L, "owasp-top10", "OWASP Top 10", "대표적인 웹 취약점을 주제별로 정리하고 토론하는 공간입니다."),
                board(7L, "security-news", "보안뉴스", "최신 보안 이슈와 공지, 사고 소식을 빠르게 모아보는 공간입니다."),
                board(8L, "hacking-basic", "해킹 기초 지식", "입문자를 위한 기초 보안 개념과 웹 해킹 지식을 정리하는 공간입니다."),
                board(9L, "linux-basic", "Linux 기초", "보안 학습에 필요한 Linux 기본 개념과 명령을 공유하는 공간입니다."),
                board(10L, "network-basic", "Network", "패킷, 포트, 프로토콜 등 네트워크 기초를 다루는 공간입니다."),
                board(11L, "web-basic", "Web 기초", "브라우저, 서버, DB, 세션 등 웹 서비스 기본 구조를 다루는 공간입니다."),
                board(12L, "faq", "FAQ", "자주 묻는 질문과 운영 안내를 빠르게 확인하는 공간입니다.")
        );
    }

    private static BoardDTO board(Long id, String code, String name, String description) {
        BoardDTO board = new BoardDTO();
        board.setBoardId(id);
        board.setBoardCode(code);
        board.setBoardName(name);
        board.setDescription(description);
        return board;
    }

    public static List<TagDTO> tags() {
        TagDTO t1 = new TagDTO();
        t1.setTagId(1L);
        t1.setTagName("jakarta");
        t1.setUseCount(21);

        TagDTO t2 = new TagDTO();
        t2.setTagId(2L);
        t2.setTagName("oracle");
        t2.setUseCount(18);

        TagDTO t3 = new TagDTO();
        t3.setTagId(3L);
        t3.setTagName("jsp");
        t3.setUseCount(17);

        TagDTO t4 = new TagDTO();
        t4.setTagId(4L);
        t4.setTagName("owasp");
        t4.setUseCount(14);

        return List.of(t1, t2, t3, t4);
    }

    public static UserDTO user() {
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setLoginId("admin");
        user.setPasswordHash("LOCAL_DEMO_PASSWORD_REQUIRED");
        user.setUserName("관리자");
        user.setNickname("admin");
        user.setEmail("admin@redweb.local");
        user.setPhone("010-5555-1200");
        user.setBio("RedWeb 운영과 보안 학습 흐름을 관리하는 관리자 계정입니다.");
        user.setRoleCode("ADMIN");
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setProfileImagePath("/assets/images/profile-default.png");
        return user;
    }

    public static UserDTO memberOne() {
        UserDTO user = new UserDTO();
        user.setUserId(2L);
        user.setLoginId("user1");
        user.setPasswordHash("LOCAL_DEMO_PASSWORD_REQUIRED");
        user.setUserName("유저1");
        user.setNickname("user1");
        user.setEmail("user1@redweb.local");
        user.setPhone("010-3333-1101");
        user.setBio("웹 보안과 실습 기록을 꾸준히 남기는 학습자입니다.");
        user.setRoleCode("MEMBER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setProfileImagePath("/assets/images/profile-default.png");
        return user;
    }

    public static UserDTO memberTwo() {
        UserDTO user = new UserDTO();
        user.setUserId(3L);
        user.setLoginId("user2");
        user.setPasswordHash("LOCAL_DEMO_PASSWORD_REQUIRED");
        user.setUserName("유저2");
        user.setNickname("user2");
        user.setEmail("user2@redweb.local");
        user.setPhone("010-3333-1102");
        user.setBio("CTF와 프로젝트 공유를 즐기는 커뮤니티 사용자입니다.");
        user.setRoleCode("MEMBER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setProfileImagePath("/assets/images/profile-default.png");
        return user;
    }

    public static List<NotificationDTO> notifications() {
        NotificationDTO first = new NotificationDTO();
        first.setNotificationId(1L);
        first.setTitle("댓글이 등록되었습니다");
        first.setMessage("내 게시글에 새로운 댓글이 달렸습니다.");
        first.setTargetUrl("/board/detail?id=101");

        NotificationDTO second = new NotificationDTO();
        second.setNotificationId(2L);
        second.setTitle("운영 공지가 갱신되었습니다");
        second.setMessage("최신 공지사항을 확인해보세요.");
        second.setTargetUrl("/board/detail?id=101");

        return List.of(first, second);
    }

    public static List<LoginHistoryDTO> loginHistory() {
        LoginHistoryDTO first = new LoginHistoryDTO();
        first.setAuthLogId(1L);
        first.setLoginIp("127.0.0.1");
        first.setUserAgent("Chrome 136 / Windows");
        first.setLoginResult("SUCCESS");

        LoginHistoryDTO second = new LoginHistoryDTO();
        second.setAuthLogId(2L);
        second.setLoginIp("127.0.0.1");
        second.setUserAgent("Edge 136 / Windows");
        second.setLoginResult("SUCCESS");

        return List.of(first, second);
    }

    public static List<CommentDTO> comments() {
        CommentDTO parent = new CommentDTO();
        parent.setCommentId(1L);
        parent.setAuthorNickname("user1");
        parent.setContent("레이아웃 구조가 명확해서 게시판 확장성이 좋아 보입니다.");

        CommentDTO child = new CommentDTO();
        child.setCommentId(2L);
        child.setParentCommentId(1L);
        child.setAuthorNickname("admin");
        child.setContent("다음 단계에서는 보안 설정 가이드도 함께 보강할 예정입니다.");
        parent.getChildren().add(child);

        return List.of(parent);
    }

    public static List<DashboardMetricDTO> metrics() {
        return List.of(
                new DashboardMetricDTO("오늘 가입", "24", "+6"),
                new DashboardMetricDTO("신규 게시글", "87", "+12"),
                new DashboardMetricDTO("미처리 신고", "9", "-3"),
                new DashboardMetricDTO("오류 로그", "4", "+1")
        );
    }
}
