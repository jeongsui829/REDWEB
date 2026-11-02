package com.scuredweb.auth.service;

public final class AccountRecoveryNotifierFactory {
    private AccountRecoveryNotifierFactory() {
    }

    public static AccountRecoveryNotifier create() {
        String provider = System.getenv("REDWEB_RECOVERY_NOTIFIER");
        if (provider == null || provider.isBlank() || "noop".equalsIgnoreCase(provider.trim())) {
            return new NoOpAccountRecoveryNotifier();
        }

        /*
         * Future production providers can be selected here, for example smtp or
         * sms. Unknown providers fail closed so recovery delivery is not
         * silently disabled by a typo in REDWEB_RECOVERY_NOTIFIER.
         */
        throw new IllegalStateException("Unsupported recovery notifier provider: " + provider.trim());
    }
}

