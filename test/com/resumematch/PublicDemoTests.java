package com.resumematch;

import static com.resumematch.TestRunner.check;
import static com.resumematch.TestRunner.checkEquals;

import com.resumematch.exception.StorageFullException;
import com.resumematch.service.FileService;
import com.resumematch.web.DemoMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Checks for the settings of a public site: the live-demo switch (environment value, context
 * parameter and the flag file that is looked up at most every 10 seconds) and the total
 * storage limit of the resume folder.
 *
 * <p>Demonstrates: a movable clock as a test double ({@code LongSupplier}), temporary folders
 * and files, custom exceptions.</p>
 */
final class PublicDemoTests {

    private PublicDemoTests() {
    }

    static void run() throws Exception {
        demoModeTests();
        storageTests();
    }

    private static void demoModeTests() throws Exception {
        System.out.println("== DemoMode ==");
        check("env true wins over param false", DemoMode.parseSwitch("true", "false"));
        check("env false wins over param true", !DemoMode.parseSwitch("false", "true"));
        check("param used when env is not set", DemoMode.parseSwitch(null, "TRUE"));
        check("blank env falls back to the param", DemoMode.parseSwitch("  ", "true"));
        check("nothing set means off", !DemoMode.parseSwitch(null, null));
        check("junk means off", !DemoMode.parseSwitch("yes", null));

        Path temp = Files.createTempDirectory("resumematch-demo");
        try {
            Path flag = temp.resolve(DemoMode.FLAG_FILE_NAME);
            AtomicLong now = new AtomicLong(1_000_000);
            DemoMode mode = new DemoMode(false, flag, now::get);
            check("off without the flag file", !mode.isActive());
            Files.createFile(flag);
            now.addAndGet(5_000);
            check("flag file is not noticed before 10 seconds", !mode.isActive());
            now.addAndGet(5_000);
            check("flag file is noticed after 10 seconds", mode.isActive());
            Files.delete(flag);
            now.addAndGet(9_000);
            check("a deleted flag is still remembered inside 10 seconds", mode.isActive());
            now.addAndGet(1_000);
            check("a deleted flag turns the mode off after 10 seconds", !mode.isActive());
            check("always-on ignores the flag file", new DemoMode(true, flag, now::get).isActive());
            check("no flag path and not forced means off", !new DemoMode(false, null, now::get).isActive());
        } finally {
            deleteTree(temp);
        }
    }

    private static void storageTests() throws Exception {
        System.out.println("== Storage limit ==");
        long mb = 1024 * 1024;
        checkEquals("default limit is 200 MB", 200 * mb, FileService.resolveStorageLimitBytes(null, null));
        checkEquals("param sets the limit", 7 * mb, FileService.resolveStorageLimitBytes(null, "7"));
        checkEquals("env wins over param", 3 * mb, FileService.resolveStorageLimitBytes("3", "7"));
        checkEquals("bad env falls back to param", 7 * mb, FileService.resolveStorageLimitBytes("abc", "7"));
        checkEquals("zero is ignored", 200 * mb, FileService.resolveStorageLimitBytes("0", "-5"));

        Path temp = Files.createTempDirectory("resumematch-storage");
        try {
            byte[] text = "Skills: Java, SQL\n".repeat(20).getBytes(StandardCharsets.UTF_8);
            FileService roomy = new FileService(temp, 10 * mb);
            checkEquals("empty folder uses 0 bytes", 0L, roomy.usedBytes());
            roomy.saveResume(1, "a.txt", text.length, new ByteArrayInputStream(text));
            checkEquals("used bytes counts the saved file", (long) text.length, roomy.usedBytes());

            // Room for exactly one more file of this size.
            FileService tight = new FileService(temp, 2L * text.length);
            tight.saveResume(2, "b.txt", text.length, new ByteArrayInputStream(text));
            TestRunner.checkThrows("upload over the storage limit is refused", StorageFullException.class,
                    () -> tight.saveResume(2, "c.txt", text.length, new ByteArrayInputStream(text)));
            checkEquals("a refused upload writes nothing", 2L * text.length, tight.usedBytes());
            check("the message is friendly", StorageFullException.MESSAGE.startsWith("Uploads are paused"));
        } finally {
            deleteTree(temp);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of the temp folder
                }
            });
        }
    }
}
