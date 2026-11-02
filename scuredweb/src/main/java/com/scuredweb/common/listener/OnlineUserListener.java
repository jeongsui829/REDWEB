package com.scuredweb.common.listener;

import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.util.OnlineUserTracker;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

@WebListener
public class OnlineUserListener implements HttpSessionListener, HttpSessionAttributeListener, HttpSessionIdListener {
    @Override
    public void sessionCreated(HttpSessionEvent event) {
        // A created session is not active until login or the online-user heartbeat occurs.
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        OnlineUserTracker.sessionEnded(event.getSession().getId());
    }

    @Override
    public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {
        OnlineUserTracker.sessionIdChanged(oldSessionId, event.getSession().getId());
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        updateLoginUser(event);
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        updateLoginUser(event);
    }

    @Override
    public void attributeRemoved(HttpSessionBindingEvent event) {
        if (SessionKey.LOGIN_USER.equals(event.getName())) {
            OnlineUserTracker.sessionEnded(event.getSession().getId());
        }
    }

    private void updateLoginUser(HttpSessionBindingEvent event) {
        if (!SessionKey.LOGIN_USER.equals(event.getName())) {
            return;
        }
        Object value = event.getSession().getAttribute(SessionKey.LOGIN_USER);
        OnlineUserTracker.userUpdated(event.getSession().getId(), value instanceof UserDTO user ? user : null);
    }
}
