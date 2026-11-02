package com.scuredweb.common.filter;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.user.dao.UserDAO;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
@WebFilter("/files/*")
public class FileSessionFilter implements Filter {
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest r=(HttpServletRequest)req; HttpServletResponse p=(HttpServletResponse)res;
        HttpSession session=r.getSession(false);
        if(session!=null && session.getAttribute(SessionKey.LOGIN_USER) instanceof UserDTO old) {
            try {
                UserDTO current=new UserDAO().findById(old.getUserId());
                if(current==null || !"ACTIVE".equals(current.getStatus()) || current.getAuthVersion()!=old.getAuthVersion()) {
                    session.invalidate(); p.sendError(401); return;
                }
            } catch(Exception e) { p.sendError(503); return; }
        }
        chain.doFilter(req,res);
    }
}
