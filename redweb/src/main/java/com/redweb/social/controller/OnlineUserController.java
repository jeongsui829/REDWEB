package com.redweb.social.controller;

import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.OnlineUserTracker;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/api/online-users")
public class OnlineUserController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);

        HttpSession session = request.getSession(true);
        Object loginUser = session.getAttribute(SessionKey.LOGIN_USER);
        boolean mayViewUserNames = loginUser instanceof UserDTO;
        OnlineUserTracker.userUpdated(session.getId(), loginUser instanceof UserDTO user ? user : null);

        OnlineUserTracker.OnlineStatus status = OnlineUserTracker.status();
        StringBuilder json = new StringBuilder();
        json.append("{\"count\":").append(status.activeSessionCount())
                .append(",\"activeSessionCount\":").append(status.activeSessionCount())
                .append(",\"authenticatedSessionCount\":").append(status.authenticatedSessionCount())
                .append(",\"guestSessionCount\":").append(status.guestSessionCount())
                .append(",\"userListVisible\":").append(mayViewUserNames)
                .append(",\"users\":[");
        int index = 0;
        for (OnlineUserTracker.OnlineUser user : mayViewUserNames ? status.users() : java.util.List.<OnlineUserTracker.OnlineUser>of()) {
            if (index++ > 0) {
                json.append(',');
            }
            json.append("{\"name\":\"")
                    .append(jsonEscape(user.displayName()))
                    .append("\",\"authenticated\":")
                    .append(user.authenticated())
                    .append('}');
        }
        json.append("]}");
        response.getWriter().write(json.toString());
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
