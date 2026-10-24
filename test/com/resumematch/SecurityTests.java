package com.resumematch;

import com.resumematch.util.HtmlUtil;
import com.resumematch.util.LoginAttemptLimiter;
import com.resumematch.util.PasswordUtil;
import com.resumematch.web.SaveJobServlet;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Checks for the salted password hashing, the login attempt limiter (including the
 * 15-minute window, driven by a movable clock) and the HTML escaping.
 *
 * <p>Demonstrates: string parsing, Base64, objects with state, a test double for
 * {@code java.time.Clock}.</p>
 */
public class SecurityTests {

    private static final String LEGACY_HASH =
            "0ead2060b65992dca4769af601a1b3a35ef38cfad2c2c465bb160ea764157c5d";

    static void run() {
        passwordTests();
        limiterTests();
        htmlTests();
        redirectTests();
    }

    private static void passwordTests() {
        System.out.println("== PasswordUtil (salted) ==");
        String hash = PasswordUtil.hash("demo1234");
        String[] parts = hash.split("\\$");
        TestRunner.checkEquals("hash has 4 parts", 4, parts.length);
        TestRunner.checkEquals("hash starts with pbkdf2", "pbkdf2", parts[0]);
        TestRunner.checkEquals("iteration count is 600000", "600000", parts[1]);
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
        String older = "pbkdf2$210000$A4xz70OwsDPn0SSGyca8gw==$CMC+nktYjhPFCpi0G2ObkPnmTCaexNZ2kSRuEdzif2I=";
        TestRunner.check("a 210000-iteration hash still matches", PasswordUtil.matches("demo1234", older));
        TestRunner.check("a 210000-iteration hash is upgraded at login", PasswordUtil.needsRehash(older));

        // An unknown email must cost the same as a wrong password for a real account. Compare the
        // work done (PBKDF2 iterations) instead of the clock, so the check cannot fail on a busy machine.
        int dummyWork = PasswordUtil.spendVerifyTime("wrong-password");
        int realWork = Integer.parseInt(hash.split("\\$")[1]);
        TestRunner.check("dummy verify does the same number of iterations as a real hash (" + dummyWork + ")",
                dummyWork == realWork && dummyWork > 0);
        TestRunner.check("dummy verify without a password does nothing", PasswordUtil.spendVerifyTime(null) == 0);
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
        MovableClock clock = new MovableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
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

        String email3 = "limit-test3@example.com";
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL; i++) {
            limiter.recordFailure(email3, "10.0.1." + i);
        }
        TestRunner.check("blocked inside the window", limiter.isBlocked(email3, "10.0.2.1"));
        TestRunner.checkEquals("15 minutes are left at the start of the window", 15,
                limiter.minutesLeft(email3, "10.0.2.1"));
        clock.advance(Duration.ofMinutes(14).plusSeconds(1));
        TestRunner.checkEquals("minutes left are rounded up", 1, limiter.minutesLeft(email3, "10.0.2.1"));
        clock.advance(Duration.ofMinutes(1));
        TestRunner.check("block ends when the window is over", !limiter.isBlocked(email3, "10.0.2.1"));
        limiter.recordFailure(email3, "10.0.2.1");
        TestRunner.check("a new window starts counting from one", !limiter.isBlocked(email3, "10.0.2.1"));

        String ipOnly = "10.0.9.9";
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("many-" + i + "@example.com", ipOnly);
        }
        TestRunner.check("30 failures block the IP for any email", limiter.isBlocked("fresh@example.com", ipOnly));
        limiter.recordSuccess("many-0@example.com");
        TestRunner.check("a good login does not clear the IP count", limiter.isBlocked("fresh@example.com", ipOnly));

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL; i++) {
            limiter.recordFailure(" Case@Example.com ", "10.0.7." + i);
        }
        TestRunner.check("email counting ignores case and spaces", limiter.isBlocked("case@example.com", "10.0.8.8"));

        TestRunner.checkEquals("IPv4 key is the address", "203.0.113.7", LoginAttemptLimiter.ipKey(" 203.0.113.7 "));
        TestRunner.checkEquals("IPv6 addresses in one /64 share a key",
                LoginAttemptLimiter.ipKey("2001:db8:1:2::1"), LoginAttemptLimiter.ipKey("2001:DB8:1:2:ffff:eeee:dddd:cccc"));
        TestRunner.check("IPv6 addresses in different /64 networks differ",
                !LoginAttemptLimiter.ipKey("2001:db8:1:2::1").equals(LoginAttemptLimiter.ipKey("2001:db8:1:3::1")));
        TestRunner.checkEquals("IPv4-mapped IPv6 is the IPv4 address", "203.0.113.7",
                LoginAttemptLimiter.ipKey("::ffff:203.0.113.7"));
        TestRunner.checkEquals("text that is not an address is only trimmed and lower-cased", "not:an:ip!",
                LoginAttemptLimiter.ipKey(" Not:An:IP! "));
        TestRunner.checkEquals("null IP gives an empty key", "", LoginAttemptLimiter.ipKey(null));
        String v6 = LoginAttemptLimiter.ipKey("2001:db8:aaaa:bbbb:1:2:3:4");
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("v6-" + i + "@example.com", v6);
        }
        TestRunner.check("30 failures block a whole IPv6 /64",
                limiter.isBlocked("x@example.com", LoginAttemptLimiter.ipKey("2001:db8:aaaa:bbbb:ffff::9")));

        // Random emails must not make the tables grow without limit.
        LoginAttemptLimiter small = new LoginAttemptLimiter(new MovableClock());
        for (int i = 0; i < LoginAttemptLimiter.MAX_TRACKED + 500; i++) {
            small.recordFailure("flood-" + i + "@example.com", "198.51.100." + (i % 250));
        }
        small.recordFailure("late@example.com", "198.51.100.1");
        TestRunner.check("tables stop growing at the cap", small.trackedEmails() <= LoginAttemptLimiter.MAX_TRACKED);
    }

    private static void redirectTests() {
        System.out.println("== Redirect after save (Referer) ==");
        String ctx = "/ResumeMatch";
        String jobs = ctx + "/jobs";
        TestRunner.checkEquals("same-app page is kept, with its query", ctx + "/jobs?q=java",
                SaveJobServlet.backTarget("http://localhost:8080/ResumeMatch/jobs?q=java", "localhost", ctx));
        TestRunner.checkEquals("missing header gives the job list", jobs, SaveJobServlet.backTarget(null, "localhost", ctx));
        TestRunner.checkEquals("other host gives the job list", jobs,
                SaveJobServlet.backTarget("http://evil.com/ResumeMatch/jobs", "localhost", ctx));
        TestRunner.checkEquals("user-info trick gives the job list", jobs,
                SaveJobServlet.backTarget("http://localhost@evil.com/ResumeMatch/jobs", "localhost", ctx));
        TestRunner.checkEquals("protocol-relative path gives the job list", jobs,
                SaveJobServlet.backTarget("//evil.com/x", "localhost", ctx));
        TestRunner.checkEquals("javascript: gives the job list", jobs,
                SaveJobServlet.backTarget("javascript:alert(1)", "localhost", ctx));
        TestRunner.checkEquals("../ out of the app gives the job list", jobs,
                SaveJobServlet.backTarget("http://localhost:8080/ResumeMatch/../manager/html", "localhost", ctx));
        TestRunner.checkEquals("encoded ../ out of the app gives the job list", jobs,
                SaveJobServlet.backTarget("http://localhost:8080/ResumeMatch/%2e%2e/manager", "localhost", ctx));
        TestRunner.checkEquals("path ending in /.. gives the job list", jobs,
                SaveJobServlet.backTarget("http://localhost:8080/ResumeMatch/..", "localhost", ctx));
        TestRunner.checkEquals("another app with the same prefix gives the job list", jobs,
                SaveJobServlet.backTarget("http://localhost:8080/ResumeMatchX/jobs", "localhost", ctx));
        TestRunner.checkEquals("garbage header gives the job list", jobs,
                SaveJobServlet.backTarget("http://local host/ResumeMatch/jobs", "localhost", ctx));
    }

    private static void htmlTests() {
        System.out.println("== HtmlUtil ==");
        TestRunner.checkEquals("null becomes empty text", "", HtmlUtil.escape(null));
        TestRunner.checkEquals("plain text is unchanged", "Java and SQL", HtmlUtil.escape("Java and SQL"));
        TestRunner.checkEquals("script tag is escaped", "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;",
                HtmlUtil.escape("<script>alert('x')</script>"));
        TestRunner.checkEquals("quotes and ampersand are escaped", "&quot;a&quot; &amp; b",
                HtmlUtil.escape("\"a\" & b"));
        TestRunner.checkEquals("any object is escaped through toString", "12", HtmlUtil.escape(12));
    }

    /** A clock the test can move forward, so the 15-minute window can be checked instantly. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
