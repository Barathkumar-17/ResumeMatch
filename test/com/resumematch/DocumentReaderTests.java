package com.resumematch;

import com.resumematch.exception.AppException;
import com.resumematch.exception.InvalidFileException;
import com.resumematch.model.Resume;
import com.resumematch.service.DocxResumeReader;
import com.resumematch.service.FileService;
import com.resumematch.service.PdfResumeReader;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.service.ResumeFileType;
import com.resumematch.service.ResumeReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Checks for the .pdf and .docx resume readers: the two fixtures must give the same skills
 * and score as the .txt sample, and damaged, fake, encrypted, scanned or hostile files must
 * be refused with a friendly {@link AppException} (never a runtime exception).
 *
 * <p>Demonstrates: building test input in memory ({@code ZipOutputStream},
 * {@code ByteArrayOutputStream}), temporary folders, exception testing, byte arrays.</p>
 */
public class DocumentReaderTests {

    private static final String PDF = "test/fixtures/arun_kumar_resume.pdf";
    private static final String DOCX = "test/fixtures/arun_kumar_resume.docx";
    private static final String W_NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    static void run() throws Exception {
        System.out.println("== PDF and DOCX readers ==");
        Path temp = Files.createTempDirectory("resumematch-docs");
        try {
            FileService files = new FileService(temp);
            fixtureTests(files);
            validationTests(files);
            pdfProblemTests(files);
            hostileInputTests();
            docxProblemTests(files, temp);
        } finally {
            deleteTree(temp);
        }
    }

    private static void fixtureTests(FileService files) throws Exception {
        for (String fixture : new String[] {PDF, DOCX}) {
            String ext = fixture.substring(fixture.lastIndexOf('.'));
            byte[] data = Files.readAllBytes(Paths.get(fixture));
            Path saved = files.saveResume(1, "Arun Kumar Resume" + ext, data.length, new ByteArrayInputStream(data));
            TestRunner.check(ext + " saved with a server-generated name and the right extension",
                    saved.getFileName().toString().matches("[0-9]+_[0-9a-f]{12}[.]" + ext.substring(1)));
            String text = files.readResume(saved);
            TestRunner.check(ext + " text keeps the section headings",
                    text.contains("EDUCATION") && text.contains("SKILLS") && text.contains("PROJECTS"));
            Resume resume = new Resume();
            resume.setFileName("arun" + ext);
            resume.setResumeText(text);
            new ResumeAnalyzer(TestRunner.dictionary()).analyze(resume);
            TestRunner.checkEquals(ext + " finds the same 7 skills as the .txt",
                    new java.util.HashSet<>(Arrays.asList("Java", "C++", "Python", "SQL", "JDBC", "Git", "DSA")),
                    resume.getSkills());
            TestRunner.checkEquals(ext + " scores 78 like the .txt", 78, resume.getScore());
        }
    }

    private static void validationTests(FileService files) throws Exception {
        files.validate("resume.pdf", 2L * 1024 * 1024);
        files.validate("RESUME.DOCX", 2L * 1024 * 1024);
        TestRunner.check("2 MB pdf and docx accepted", true);
        TestRunner.checkThrows("pdf over 2 MB rejected", InvalidFileException.class,
                () -> files.validate("resume.pdf", 2L * 1024 * 1024 + 1));
        TestRunner.checkThrows("txt of 300 KB still rejected", InvalidFileException.class,
                () -> files.validate("resume.txt", 300L * 1024));
        try {
            files.validate("resume.pdf", 3L * 1024 * 1024);
        } catch (AppException e) {
            TestRunner.check("pdf too-large message says 2 MB", e.getUserMessage().contains("larger than 2 MB"));
        }
        try {
            files.validate("old resume.doc", 100);
            TestRunner.check(".doc rejected", false);
        } catch (AppException e) {
            TestRunner.check(".doc rejected with the friendly message",
                    e.getUserMessage().contains("Upload a .txt, .pdf or .docx file."));
        }
        TestRunner.check("content types", ResumeFileType.PDF.getContentType().equals("application/pdf")
                && ResumeFileType.DOCX.getContentType().endsWith("wordprocessingml.document")
                && ResumeFileType.TXT.getContentType().equals("text/plain; charset=UTF-8"));
        byte[] text = "Java and SQL resume".getBytes(StandardCharsets.UTF_8);
        TestRunner.checkThrows("text renamed to .pdf rejected", InvalidFileException.class,
                () -> files.saveResume(2, "fake.pdf", text.length, new ByteArrayInputStream(text)));
        TestRunner.checkThrows("text renamed to .docx rejected", InvalidFileException.class,
                () -> files.saveResume(2, "fake.docx", text.length, new ByteArrayInputStream(text)));
        byte[] pdf = Files.readAllBytes(Paths.get(PDF));
        TestRunner.checkThrows("pdf renamed to .txt rejected", InvalidFileException.class,
                () -> files.saveResume(2, "fake.txt", pdf.length, new ByteArrayInputStream(pdf)));
        TestRunner.checkThrows("pdf renamed to .docx rejected", InvalidFileException.class,
                () -> files.saveResume(2, "fake.docx", pdf.length, new ByteArrayInputStream(pdf)));
        TestRunner.check("rejected uploads leave no file", !Files.exists(files.getBaseDir().resolve("user_2")));
    }

    private static void pdfProblemTests(FileService files) throws Exception {
        byte[] good = Files.readAllBytes(Paths.get(PDF));
        byte[] truncated = Arrays.copyOf(good, good.length / 2);
        try {
            files.readResume(files.saveResume(3, "cut.pdf", truncated.length, new ByteArrayInputStream(truncated)));
        } catch (AppException e) {
            // refusing a damaged file is fine; a runtime exception would escape and fail the run
        }
        TestRunner.check("half a pdf never throws a runtime exception", true);
        byte[] stub = Arrays.copyOf(good, 300);
        TestRunner.checkThrows("pdf cut after 300 bytes gives a friendly exception", AppException.class,
                () -> files.readResume(files.saveResume(3, "stub.pdf", stub.length, new ByteArrayInputStream(stub))));

        byte[] junk = "%PDF-1.4\nthis is not really a pdf \u0001\u0002 obj obj endobj".getBytes(StandardCharsets.ISO_8859_1);
        TestRunner.checkThrows("pdf with a header but junk inside gives a friendly exception", AppException.class,
                () -> files.readResume(files.saveResume(3, "junk.pdf", junk.length, new ByteArrayInputStream(junk))));

        byte[] encrypted = pdfWithTrailer("/Root 1 0 R /Encrypt 5 0 R");
        try {
            files.readResume(files.saveResume(3, "locked.pdf", encrypted.length, new ByteArrayInputStream(encrypted)));
            TestRunner.check("encrypted pdf rejected", false);
        } catch (AppException e) {
            TestRunner.check("encrypted pdf rejected with a password message", e.getUserMessage().contains("password"));
        }

        byte[] scanned = pdfWithTrailer("/Root 1 0 R");
        try {
            files.readResume(files.saveResume(3, "scan.pdf", scanned.length, new ByteArrayInputStream(scanned)));
            TestRunner.check("pdf without text rejected", false);
        } catch (AppException e) {
            TestRunner.check("pdf without text rejected with a scanned-image message",
                    e.getUserMessage().contains("scanned"));
        }
    }

    /** Hostile files must be refused quickly: every case here has to finish inside the time budget. */
    private static void hostileInputTests() throws Exception {
        String neverEnding = "1 0 obj <</A [ ".repeat(150_000);
        timedRefusal("150k objects that never end", ".pdf",
                ("%PDF-1.4\n" + neverEnding).getBytes(StandardCharsets.ISO_8859_1));
        timedRefusal("2M nested arrays in a pdf", ".pdf",
                ("%PDF-1.4\n1 0 obj\n" + "[".repeat(2_000_000)).getBytes(StandardCharsets.ISO_8859_1));
        timedRefusal("page tree that points at itself", ".pdf",
                ("%PDF-1.4\n1 0 obj\n<</Type/Catalog/Pages 2 0 R>>\nendobj\n"
                + "2 0 obj\n<</Type/Pages/Kids[2 0 R 2 0 R]/Count 9>>\nendobj\ntrailer\n<</Root 1 0 R>>")
                        .getBytes(StandardCharsets.ISO_8859_1));
        String entities = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY a \"aaaaaaaaaa\">"
                + "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]><w:document " + W_NS
                + "><w:body><w:p><w:r><w:t>&b;&b;</w:t></w:r></w:p></w:body></w:document>";
        timedRefusal("docx with entity definitions", ".docx", zip("word/document.xml", entities));
        timedRefusal("docx with 3M nested paragraphs", ".docx", zip("word/document.xml",
                "<w:document " + W_NS + "><w:body>" + "<w:p>".repeat(3_000_000)));
    }

    /** Reads the bytes with the matching reader; passes when it finishes or refuses within 5 seconds. */
    private static void timedRefusal(String label, String extension, byte[] data) throws Exception {
        Path file = Files.createTempFile("hostile", extension);
        try {
            Files.write(file, data);
            ResumeReader reader = extension.equals(".pdf") ? new PdfResumeReader() : new DocxResumeReader();
            long start = System.nanoTime();
            try {
                reader.read(file);
            } catch (AppException e) {
                // a friendly refusal is the expected outcome
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            TestRunner.check(label + " finishes within 5 seconds", millis < 5000);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static byte[] pdfWithTrailer(String trailer) {
        String pdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
                + "trailer\n<< " + trailer + " >>\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void docxProblemTests(FileService files, Path temp) throws Exception {
        byte[] noDocument = zip("word/other.xml", "<x/>");
        TestRunner.checkThrows("docx without word/document.xml rejected", InvalidFileException.class,
                () -> files.saveResume(4, "empty.docx", noDocument.length, new ByteArrayInputStream(noDocument)));

        // 12 MB of blanks compresses to a few KB: it passes the size limit but must hit the zip bomb guard
        StringBuilder bomb = new StringBuilder("<w:document " + W_NS + "><w:body><w:p><w:r><w:t>Java</w:t></w:r></w:p>");
        char[] blanks = new char[1024 * 1024];
        Arrays.fill(blanks, ' ');
        for (int i = 0; i < 12; i++) {
            bomb.append(blanks);
        }
        bomb.append("</w:body></w:document>");
        byte[] bombZip = zip("word/document.xml", bomb.toString());
        TestRunner.check("zip bomb is small on disk", bombZip.length < 100 * 1024);
        TestRunner.checkThrows("zip bomb refused by the size guard", InvalidFileException.class,
                () -> files.readResume(files.saveResume(4, "bomb.docx", bombZip.length, new ByteArrayInputStream(bombZip))));

        ByteArrayOutputStream many = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(many)) {
            put(zos, "word/document.xml", "<w:document " + W_NS + "><w:body><w:p><w:r><w:t>Java</w:t></w:r></w:p></w:body></w:document>");
            for (int i = 0; i < 2100; i++) {
                put(zos, "extra/" + i + ".xml", "<x/>");
            }
        }
        byte[] manyZip = many.toByteArray();
        TestRunner.checkThrows("package with too many entries refused", InvalidFileException.class,
                () -> files.readResume(files.saveResume(4, "many.docx", manyZip.length, new ByteArrayInputStream(manyZip))));

        byte[] broken = zip("word/document.xml", "<w:document " + W_NS + "><w:body><w:p><w:r><w:t>Java");
        TestRunner.checkThrows("broken XML gives a friendly exception", InvalidFileException.class,
                () -> files.readResume(files.saveResume(4, "broken.docx", broken.length, new ByteArrayInputStream(broken))));

        // XXE: an external entity pointing at a local file must not leak its content
        Path secret = temp.resolve("secret.txt");
        Files.write(secret, "TOPSECRET123".getBytes(StandardCharsets.UTF_8));
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]>"
                + "<w:document " + W_NS + "><w:body><w:p><w:r><w:t>Java &x;</w:t></w:r></w:p></w:body></w:document>";
        byte[] xxeZip = zip("word/document.xml", xxe);
        String leaked = "";
        try {
            leaked = files.readResume(files.saveResume(4, "xxe.docx", xxeZip.length, new ByteArrayInputStream(xxeZip)));
        } catch (AppException e) {
            // refusing the file is fine too
        }
        TestRunner.check("external entity content is not read", !leaked.contains("TOPSECRET123"));

        byte[] small = zip("word/document.xml", "<w:document " + W_NS + "><w:body><w:p><w:r><w:t>Ja</w:t></w:r><w:r><w:t>va</w:t></w:r>"
                + "<w:r><w:tab/><w:t>SQL</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>Git</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                + "</w:body></w:document>");
        String text = files.readResume(files.saveResume(4, "small.docx", small.length, new ByteArrayInputStream(small)));
        TestRunner.checkEquals("runs are joined, tab and table cells are kept", "Java SQL\nGit", text);
    }

    private static byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            put(zos, name, content);
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
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
