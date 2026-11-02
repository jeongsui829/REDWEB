package com.scuredweb.auth.service;

public final class NoOpAccountRecoveryNotifier implements AccountRecoveryNotifier {
    @Override
    public void sendFindIdNotice(String userName, String email, String loginId) {
        /*
         * Local/training build only. This method intentionally does not print,
         * log, flash, persist, or expose account identifiers. In production,
         * connect a mail/SMS provider through AccountRecoveryNotifierFactory.
         */
    }

    @Override
    public void sendPasswordResetNotice(String loginId, String email, String resetUrl) {
        /*
         * Local/training build only. This method intentionally does not print,
         * log, flash, persist, or expose resetUrl. In production, deliver it
         * through a trusted mail/SMS provider.
         */
    }
}

