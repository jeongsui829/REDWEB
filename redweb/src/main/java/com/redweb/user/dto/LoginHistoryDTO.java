package com.redweb.user.dto;

import java.time.LocalDateTime;

public class LoginHistoryDTO {
    private Long authLogId;
    private String loginIp;
    private String userAgent;
    private String loginResult;
    private LocalDateTime loginAt;

    public Long getAuthLogId() { return authLogId; }
    public void setAuthLogId(Long authLogId) { this.authLogId = authLogId; }
    public String getLoginIp() { return loginIp; }
    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getLoginResult() { return loginResult; }
    public void setLoginResult(String loginResult) { this.loginResult = loginResult; }
    public LocalDateTime getLoginAt() { return loginAt; }
    public void setLoginAt(LocalDateTime loginAt) { this.loginAt = loginAt; }
}

