package com.scuredweb.auth.service;

public interface AccountRecoveryNotifier {
    void sendFindIdNotice(String userName, String email, String loginId);

    void sendPasswordResetNotice(String loginId, String email, String resetUrl);
}

