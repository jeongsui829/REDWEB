package com.scuredweb.common.filter;
import com.scuredweb.common.security.RateLimitService;
import com.scuredweb.common.config.AppConfig;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Runs before multipart parsing. Bounded in-flight uploads plus independent IP/user budgets. */
public class AbuseFilter implements Filter {
    private final Semaphore upload = new Semaphore(AppConfig.positiveSetting("upload.concurrency", 2, 16));
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest r=(HttpServletRequest)req; HttpServletResponse p=(HttpServletResponse)res;
        if (!"POST".equals(r.getMethod())) { chain.doFilter(req,res); return; }
        String path=r.getServletPath();
        String scope=path.startsWith("/comment/") ? "comment" : path.startsWith("/messages") ? "message"
            : path.startsWith("/report/") ? "report" : path.equals("/register") ? "register"
            : path.equals("/mypage/profile") ? "profile" : path.startsWith("/profile/") ? "guestbook" : "board";
        int defaultLimit=switch(scope) { case "register" -> 5; case "report" -> 20; case "comment", "message" -> 60; default -> 30; };
        int limit=AppConfig.positiveSetting("rate."+scope,defaultLimit,10000);
        HttpSession session=r.getSession(false);
        Object principal=session==null?null:session.getAttribute(SessionKey.LOGIN_USER);
        boolean allowed=RateLimitService.tryConsume(scope+"-ip",r.getRemoteAddr(),scope.equals("register")?limit:limit*3);
        if(principal instanceof UserDTO u) allowed=RateLimitService.tryConsume(scope+"-user",String.valueOf(u.getUserId()),limit)&&allowed;
        if(!allowed) { p.setHeader("Retry-After","600"); p.sendError(429); return; }
        boolean multipart=path.equals("/board/write") || path.equals("/board/edit") || path.equals("/mypage/profile");
        if(multipart&&!upload.tryAcquire()) { p.setHeader("Retry-After","5"); p.sendError(429); return; }
        try { chain.doFilter(req,res); } finally { if(multipart) upload.release(); }
    }
}
