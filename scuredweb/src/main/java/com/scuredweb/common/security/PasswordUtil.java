package com.scuredweb.common.security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {
    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static boolean isAllowedPassword(String value) {
        return value != null && value.length() <= 128;
    }

    public static boolean verifyPassword(String password, String stored) {
        return isAllowedPassword(password) && verify(password, stored);
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        if (!isHash(stored)) {
            return com.scuredweb.common.config.AppConfig.booleanSetting("redweb.allowLegacyPlaintextPasswords", "REDWEB_ALLOW_LEGACY_PLAINTEXT_PASSWORDS", !com.scuredweb.common.config.AppConfig.production()) && password.equals(stored);
        }
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(password, salt, iterations);
            return constantTimeEquals(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean isNewPasswordAllowed(String value) { return isAllowedPassword(value) && value.length() >= 15; }

    public static boolean needsRehash(String stored) {
        if (!isHash(stored)) return true;
        try { return Integer.parseInt(stored.split("\\$")[1]) < ITERATIONS; }
        catch (RuntimeException e) { return false; }
    }

    public static boolean isHash(String value) {
        return value != null && value.startsWith(PREFIX + "$");
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Password hashing is not available", exception);
        }
    }

    private static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expected.length; i++) {
            result |= expected[i] ^ actual[i];
        }
        return result == 0;
    }
}

