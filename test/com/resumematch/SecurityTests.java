package com.resumematch;

import com.resumematch.util.LoginAttemptLimiter;
import com.resumematch.util.PasswordUtil;

import java.util.Base64;

/**
 * Checks for the salted password hashing and the login attempt limiter.
 * These need the newer PasswordUtil and LoginAttemptLimiter classes, so this file
 * only compiles once they are on the branch being tested.
 *
 * <p>Demonstrates: string parsing, Base64, objects with state.</p>
 */
public class SecurityTests {

    private static final String LEGACY_HASH =
            "0ead2060b65992dca4769af601a1b3a35ef38cfad2c2c465bb160ea764157c5d";

    static void run() {
        passwordTests();
        limiterTests();
    }

    private static void passwordTests() {
        System.out.println("== PasswordUtil (salted) ==");
        String hash = PasswordUtil.hash("demo1234");
        String[] parts = hash.split("\\$");
        TestRunner.checkEquals("hash has 4 parts", 4, parts.length);
        TestRunner.checkEquals("hash starts with pbkdf2", "pbkdf2", parts[0]);
        TestRunner.checkEquals("iteration count is 210000", "210000", parts[1]);
        TestRunner.check("salt is Base64", parts.length == 4 && isBase64(parts[2]));
        TestRunner.check("hash part is Base64", parts.length == 4 && isBase64(parts[3]));
        TestRunner.check("two hashes of the same password differ", !hash.equals(PasswordUtil.hash("demo1234")));
        TestRunner.check("correct password matches", PasswordUtil.matches("demo1234", hash));
        TestRunner.check("wrong password does not match", !PasswordUtil.matches("demo12345", hash));
        TestRunner.check("legacy SHA-256 hash still matches", PasswordUtil.matches("demo1234", LEGACY_HASH));
        TestRunner.check("legacy hash, wrong password", !PasswordUtil.matches("wrong", LEGACY_HASH));
        TestRunner.check("null password does not match", !PasswordUtil.matches(null, hash));
        TestRunner.check("null stored hash does not match", !PasswordUtil.matches("demo1234", null));
        TestRunner.check("malformed stored hash does not match", !PasswordUtil.matches("demo1234", "pbkdf2$abc"));
        TestRunner.check("garbage stored hash does not match", !PasswordUtil.matches("demo1234", "not a hash"));
        TestRunner.check("empty stored hash does not match", !PasswordUtil.matches("demo1234", ""));
        TestRunner.check("legacy hash needs rehash", PasswordUtil.needsRehash(LEGACY_HASH));
        TestRunner.check("new hash does not need rehash", !PasswordUtil.needsRehash(hash));
    }

    private static boolean isBase64(String text) {
        try {
            return Base64.getDecoder().decode(text).length > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void limiterTests() {
        System.out.println("== LoginAttemptLimiter ==");
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        String email = "limit-test@example.com";
        String ip = "10.0.0.1";

        TestRunner.check("fresh email is not blocked", !limiter.isBlocked(email, ip));
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(email, ip);
        }
        TestRunner.check("4 failures do not block", !limiter.isBlocked(email, ip));
        limiter.recordFailure(email, ip);
        TestRunner.check("5 failures block the email", limiter.isBlocked(email, ip));
        TestRunner.check("5 failures block the email from another IP", limiter.isBlocked(email, "10.0.0.2"));
        TestRunner.check("minutesLeft is positive while blocked", limiter.minutesLeft(email, ip) > 0);
        TestRunner.check("a different email is not blocked", !limiter.isBlocked("other@example.com", "10.0.0.3"));

        limiter.recordSuccess(email);
        TestRunner.check("success clears the email block", !limiter.isBlocked(email, "10.0.0.9"));

        String email2 = "limit-test2@example.com";
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(email2, ip);
        }
        limiter.recordSuccess(email2);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(email2, ip);
        }
        TestRunner.check("success resets the failure count", !limiter.isBlocked(email2, ip));
    }
}
