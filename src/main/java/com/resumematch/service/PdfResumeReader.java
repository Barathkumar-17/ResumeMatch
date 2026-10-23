package com.resumematch.service;

import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Pulls the text out of a text-based PDF with plain Java (no PDF library). It reads the
 * file's indirect objects ({@code n g obj ... endobj}, including objects packed inside
 * object streams), follows the page tree to the first pages, inflates each page's
 * {@code /FlateDecode} content stream and runs through its text operators ({@code Tj},
 * {@code TJ}, {@code '}, {@code "}, {@code Td}, {@code TD}, {@code T*}, {@code Tm}).
 * Characters are turned into Unicode with the font's {@code /ToUnicode} CMap (bfchar and
 * bfrange, 1- and 2-byte codes) or, for simple fonts, the WinAnsi / MacRoman encoding plus
 * {@code /Differences}. A new line starts when the baseline moves, a space is inserted
 * when there is a gap between two pieces of text on the same line.
 *
 * <p>Not supported: encrypted PDFs and scans without a text layer (both give a friendly
 * message), LZW / ASCII85 / JPEG streams, embedded font programs without a ToUnicode map
 * that use custom encodings, and reading order for multi-column layouts (text comes out in
 * the order the file draws it). The input is untrusted, so every loop is bounded: at most
 * 20 pages, 50,000 objects, 8 MB per inflated stream and 48 MB inflated in total.</p>
 *
 * <p>Demonstrates: interface implementation, byte-level parsing and tokenizing, recursion
 * with a depth limit, Collections ({@code Map}, {@code List}, {@code Deque}), nested classes,
 * {@code java.util.zip.Inflater}, matrix arithmetic, resource limits, exception wrapping.</p>
 */
public class PdfResumeReader implements ResumeReader {

    private static final Logger LOGGER = Logger.getLogger(PdfResumeReader.class.getName());

    static final int MAX_PAGES = 20;
    static final int MAX_OBJECTS = 50_000;
    static final int MAX_STREAM_BYTES = 8 * 1024 * 1024;
    static final long MAX_TOTAL_INFLATED = 48L * 1024 * 1024;
    private static final int MAX_DEPTH = 40;
    private static final long MAX_TOKENS_PER_PAGE = 3_000_000L;
    private static final int MAX_CMAP_ENTRIES = 100_000;
    private static final int MAX_RAW_CHARS = 400_000;

    @Override
    public String read(Path p) throws FileReadException, InvalidFileException {
        if (p == null) {
            throw new FileReadException();
        }
        byte[] data;
        try {
            data = Files.readAllBytes(p);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not read the .pdf file", e);
            throw new FileReadException(e);
        }
        return extract(data);
    }

    /** Extracts the text of a PDF held in memory. */
    String extract(byte[] data) throws InvalidFileException {
        String raw;
        try {
            Document doc = new Document(data);
            doc.load();
            if (doc.isEncrypted()) {
                throw InvalidFileException.encryptedPdf();
            }
            raw = doc.extractText();
        } catch (PdfFormatException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "could not read the PDF structure", e);
            throw InvalidFileException.unreadableDocument(".pdf");
        }
        String text = ResumeReader.tidy(raw);
        if (text.trim().isEmpty()) {
            throw InvalidFileException.noTextInPdf();
        }
        return text;
    }

    // ------------------------------------------------------------------
    // PDF object model: Long/Double, Boolean, Name, PdfString, List, Map, Ref, Stream, Keyword
    // ------------------------------------------------------------------

    /** Raised internally for input that is not a usable PDF. */
    private static final class PdfFormatException extends Exception {
        private static final long serialVersionUID = 1L;

        PdfFormatException(String message) {
            super(message);
        }
    }

    private static final class Name {
        final String value;

        Name(String value) {
            this.value = value;
        }
    }

    private static final class PdfString {
        final byte[] bytes;

        PdfString(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    private static final class Ref {
        final int number;

        Ref(int number) {
            this.number = number;
        }
    }

    private static final class Keyword {
        final String text;

        Keyword(String text) {
            this.text = text;
        }
    }

    private static final class PdfStream {
        final Map<String, Object> dict;
        final byte[] raw;

        PdfStream(Map<String, Object> dict, byte[] raw) {
            this.dict = dict;
            this.raw = raw;
        }
    }

    private static final Object NULL = new Object();
    private static final Object EOF = new Object();

    // ------------------------------------------------------------------
    // Lexer: turns bytes into PDF objects (also used for content streams and CMaps)
    // ------------------------------------------------------------------

    private static final class Lexer {
        final byte[] b;
        int pos;

        Lexer(byte[] b, int pos) {
            this.b = b;
            this.pos = pos;
        }

        static boolean isWhite(int c) {
            return c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32;
        }

        static boolean isDelimiter(int c) {
            return c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']'
                    || c == '{' || c == '}' || c == '/' || c == '%';
        }

        static boolean isRegular(int c) {
            return !isWhite(c) && !isDelimiter(c);
        }

        void skipWhite() {
            while (pos < b.length) {
                int c = b[pos] & 0xFF;
                if (isWhite(c)) {
                    pos++;
                } else if (c == '%') {
                    while (pos < b.length && b[pos] != 10 && b[pos] != 13) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
        }

        /** Reads the next object; every call moves forward by at least one byte or returns EOF. */
        Object next(boolean allowRefs, int depth) {
            skipWhite();
            if (pos >= b.length) {
                return EOF;
            }
            int c = b[pos] & 0xFF;
            switch (c) {
                case '/':
                    pos++;
                    return new Name(readName());
                case '(':
                    pos++;
                    return new PdfString(readLiteralString());
                case '<':
                    if (pos + 1 < b.length && b[pos + 1] == '<') {
                        pos += 2;
                        return depth > MAX_DEPTH ? NULL : readDictionary(allowRefs, depth + 1);
                    }
                    pos++;
                    return new PdfString(readHexString());
                case '>':
                    if (pos + 1 < b.length && b[pos + 1] == '>') {
                        pos += 2;
                        return new Keyword(">>");
                    }
                    pos++;
                    return new Keyword(">");
                case '[':
                    pos++;
                    return depth > MAX_DEPTH ? NULL : readArray(allowRefs, depth + 1);
                case ']':
                case '{':
                case '}':
                case ')':
                    pos++;
                    return new Keyword(String.valueOf((char) c));
                default:
                    return readToken(allowRefs);
            }
        }

        private String readName() {
            StringBuilder sb = new StringBuilder();
            while (pos < b.length && isRegular(b[pos] & 0xFF)) {
                int c = b[pos++] & 0xFF;
                if (c == '#' && pos + 1 < b.length && hex(b[pos]) >= 0 && hex(b[pos + 1]) >= 0) {
                    c = hex(b[pos]) * 16 + hex(b[pos + 1]);
                    pos += 2;
                }
                sb.append((char) c);
            }
            return sb.toString();
        }

        private byte[] readLiteralString() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int nesting = 1;
            while (pos < b.length) {
                int c = b[pos++] & 0xFF;
                if (c == '\\' && pos < b.length) {
                    int e = b[pos++] & 0xFF;
                    switch (e) {
                        case 'n': out.write('\n'); break;
                        case 'r': out.write('\r'); break;
                        case 't': out.write('\t'); break;
                        case 'b': out.write('\b'); break;
                        case 'f': out.write('\f'); break;
                        case 13:
                            if (pos < b.length && b[pos] == 10) {
                                pos++;
                            }
                            break;
                        case 10: break;
                        default:
                            if (e >= '0' && e <= '7') {
                                int v = e - '0';
                                for (int i = 0; i < 2 && pos < b.length && b[pos] >= '0' && b[pos] <= '7'; i++) {
                                    v = v * 8 + (b[pos++] - '0');
                                }
                                out.write(v & 0xFF);
                            } else {
                                out.write(e);
                            }
                            break;
                    }
                } else if (c == '(') {
                    nesting++;
                    out.write(c);
                } else if (c == ')') {
                    if (--nesting == 0) {
                        break;
                    }
                    out.write(c);
                } else {
                    out.write(c);
                }
            }
            return out.toByteArray();
        }

        private byte[] readHexString() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int high = -1;
            while (pos < b.length) {
                int c = b[pos++] & 0xFF;
                if (c == '>') {
                    break;
                }
                int h = hex(c);
                if (h < 0) {
                    continue;
                }
                if (high < 0) {
                    high = h;
                } else {
                    out.write(high * 16 + h);
                    high = -1;
                }
            }
            if (high >= 0) {
                out.write(high * 16);
            }
            return out.toByteArray();
        }

        private Map<String, Object> readDictionary(boolean allowRefs, int depth) {
            Map<String, Object> dict = new LinkedHashMap<>();
            while (true) {
                Object key = next(allowRefs, depth);
                if (key == EOF || (key instanceof Keyword && ">>".equals(((Keyword) key).text))) {
                    break;
                }
                if (!(key instanceof Name)) {
                    if (key instanceof Keyword && "endobj".equals(((Keyword) key).text)) {
                        break;
                    }
                    continue;
                }
                Object value = next(allowRefs, depth);
                if (value == EOF) {
                    break;
                }
                if (value instanceof Keyword) {
                    if (">>".equals(((Keyword) value).text)) {
                        break;
                    }
                    continue;
                }
                if (value != NULL) {
                    dict.put(((Name) key).value, value);
                }
            }
            return dict;
        }

        private List<Object> readArray(boolean allowRefs, int depth) {
            List<Object> list = new ArrayList<>();
            while (list.size() < 1_000_000) {
                Object item = next(allowRefs, depth);
                if (item == EOF || (item instanceof Keyword && "]".equals(((Keyword) item).text))) {
                    break;
                }
                if (item instanceof Keyword && ("endobj".equals(((Keyword) item).text)
                        || ">>".equals(((Keyword) item).text))) {
                    break;
                }
                list.add(item);
            }
            return list;
        }

        /** A number, true/false/null, a reference "n g R", or a keyword such as an operator. */
        private Object readToken(boolean allowRefs) {
            int start = pos;
            while (pos < b.length && isRegular(b[pos] & 0xFF)) {
                pos++;
            }
            if (pos == start) {
                pos++; // cannot happen for regular bytes, but never stand still
                return new Keyword("");
            }
            String token = new String(b, start, pos - start, StandardCharsets.ISO_8859_1);
            Object number = parseNumber(token);
            if (number == null) {
                if ("true".equals(token)) {
                    return Boolean.TRUE;
                }
                if ("false".equals(token)) {
                    return Boolean.FALSE;
                }
                if ("null".equals(token)) {
                    return NULL;
                }
                return new Keyword(token);
            }
            if (allowRefs && number instanceof Long && (Long) number >= 0 && (Long) number < Integer.MAX_VALUE) {
                int save = pos;
                skipWhite();
                int gStart = pos;
                while (pos < b.length && b[pos] >= '0' && b[pos] <= '9') {
                    pos++;
                }
                if (pos > gStart && pos - gStart < 6) {
                    skipWhite();
                    if (pos < b.length && b[pos] == 'R' && (pos + 1 >= b.length || !isRegular(b[pos + 1] & 0xFF))) {
                        pos++;
                        return new Ref(((Long) number).intValue());
                    }
                }
                pos = save;
            }
            return number;
        }

        private static Object parseNumber(String t) {
            boolean dot = false;
            boolean digit = false;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c >= '0' && c <= '9') {
                    digit = true;
                } else if (c == '.') {
                    dot = true;
                } else if (!((c == '+' || c == '-') && i == 0)) {
                    return null;
                }
            }
            if (!digit) {
                return null;
            }
            try {
                if (dot) {
                    return Double.valueOf(t);
                }
                return Long.valueOf(t.startsWith("+") ? t.substring(1) : t);
            } catch (NumberFormatException e) {
                return t.startsWith("-") ? Long.valueOf(0) : Long.valueOf(Integer.MAX_VALUE);
            }
        }

        static int hex(int c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            }
            if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Document: object table, page tree, streams
    // ------------------------------------------------------------------

    private static final class Document {
        private final byte[] buf;
        private final Map<Integer, Object> objects = new HashMap<>();
        private final List<Map<String, Object>> trailers = new ArrayList<>();
        private final Map<Map<String, Object>, Font> fonts = new IdentityHashMap<>();
        private long totalInflated;

        Document(byte[] buf) {
            this.buf = buf;
        }

        // ---- loading ----

        void load() throws PdfFormatException {
            scanObjects();
            scanTrailers();
            unpackObjectStreams();
            if (objects.isEmpty()) {
                throw new PdfFormatException("no objects");
            }
        }

        /** Finds every "n g obj" and parses the object; stream data is skipped, not scanned. */
        private void scanObjects() throws PdfFormatException {
            int i = 0;
            int n = buf.length;
            long budget = 4L * n + 1_000_000; // bytes the object parser may read in total
            while (i + 3 <= n) {
                int idx = indexOf("obj", i);
                if (idx < 0) {
                    break;
                }
                i = idx + 3;
                if (idx == 0 || !Lexer.isWhite(buf[idx - 1] & 0xFF)
                        || (idx + 3 < n && Lexer.isRegular(buf[idx + 3] & 0xFF))) {
                    continue;
                }
                int j = idx - 1;
                while (j >= 0 && Lexer.isWhite(buf[j] & 0xFF)) {
                    j--;
                }
                int genEnd = j;
                while (j >= 0 && buf[j] >= '0' && buf[j] <= '9') {
                    j--;
                }
                if (j == genEnd) {
                    continue;
                }
                while (j >= 0 && Lexer.isWhite(buf[j] & 0xFF)) {
                    j--;
                }
                int numEnd = j;
                while (j >= 0 && buf[j] >= '0' && buf[j] <= '9') {
                    j--;
                }
                if (j == numEnd || numEnd - j > 9) {
                    continue;
                }
                int number = Integer.parseInt(new String(buf, j + 1, numEnd - j, StandardCharsets.ISO_8859_1));

                Lexer lexer = new Lexer(buf, idx + 3);
                Object value = lexer.next(true, 0);
                // An object that never ends would be parsed again from every later "obj".
                budget -= lexer.pos - (idx + 3);
                if (budget < 0) {
                    throw new PdfFormatException("too much object data");
                }
                if (value == EOF || value instanceof Keyword) {
                    continue;
                }
                if (value instanceof Map) {
                    lexer.skipWhite();
                    if (startsWith("stream", lexer.pos)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dict = (Map<String, Object>) value;
                        int dataStart = lexer.pos + 6;
                        if (dataStart < n && buf[dataStart] == 13) {
                            dataStart++;
                        }
                        if (dataStart < n && buf[dataStart] == 10) {
                            dataStart++;
                        }
                        int dataEnd = findStreamEnd(dict, dataStart);
                        value = new PdfStream(dict, Arrays.copyOfRange(buf, dataStart, dataEnd));
                        i = Math.max(i, dataEnd);
                    }
                }
                if (objects.size() >= MAX_OBJECTS && !objects.containsKey(number)) {
                    throw new PdfFormatException("too many objects");
                }
                objects.put(number, value);
            }
        }

        /** Uses /Length when it is a direct number that lands on "endstream", otherwise searches. */
        private int findStreamEnd(Map<String, Object> dict, int start) {
            Object length = dict.get("Length");
            if (length instanceof Long) {
                long end = start + (Long) length;
                if (end >= start && end <= buf.length) {
                    int k = (int) end;
                    while (k < buf.length && Lexer.isWhite(buf[k] & 0xFF)) {
                        k++;
                    }
                    if (startsWith("endstream", k)) {
                        return (int) end;
                    }
                }
            }
            int e = indexOf("endstream", start);
            if (e < 0) {
                return buf.length;
            }
            if (e > start && buf[e - 1] == 10) {
                e--;
            }
            if (e > start && buf[e - 1] == 13) {
                e--;
            }
            return e;
        }

        private void scanTrailers() {
            int i = 0;
            int found = 0;
            while (found < 64) {
                int idx = indexOf("trailer", i);
                if (idx < 0) {
                    break;
                }
                found++;
                i = idx + 7;
                Object v = new Lexer(buf, i).next(true, 0);
                if (v instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dict = (Map<String, Object>) v;
                    trailers.add(dict);
                }
            }
        }

        /** Unpacks "/Type /ObjStm" streams (PDF 1.5+): objects stored together in one compressed stream. */
        private void unpackObjectStreams() {
            List<PdfStream> streams = new ArrayList<>();
            for (Object o : objects.values()) {
                if (o instanceof PdfStream) {
                    PdfStream s = (PdfStream) o;
                    String type = nameOf(s.dict.get("Type"));
                    if ("ObjStm".equals(type)) {
                        streams.add(s);
                    } else if ("XRef".equals(type)) {
                        trailers.add(s.dict);
                    }
                }
            }
            for (PdfStream s : streams) {
                byte[] data = decode(s);
                int count = (int) number(s.dict.get("N"), 0);
                int first = (int) number(s.dict.get("First"), 0);
                if (data == null || count <= 0 || first <= 0 || first > data.length) {
                    continue;
                }
                Lexer header = new Lexer(data, 0);
                int[] nums = new int[Math.min(count, MAX_OBJECTS)];
                int[] offs = new int[nums.length];
                int read = 0;
                for (; read < nums.length; read++) {
                    Object a = header.next(false, 0);
                    Object b = header.next(false, 0);
                    if (!(a instanceof Long) || !(b instanceof Long)) {
                        break;
                    }
                    nums[read] = ((Long) a).intValue();
                    offs[read] = ((Long) b).intValue();
                }
                for (int k = 0; k < read && objects.size() < MAX_OBJECTS; k++) {
                    int at = first + offs[k];
                    if (at < 0 || at >= data.length || objects.containsKey(nums[k])) {
                        continue;
                    }
                    Object v = new Lexer(data, at).next(true, 0);
                    if (v != EOF && v != NULL && !(v instanceof Keyword)) {
                        objects.put(nums[k], v);
                    }
                }
            }
        }

        boolean isEncrypted() {
            for (Map<String, Object> t : trailers) {
                if (t.containsKey("Encrypt")) {
                    return true;
                }
            }
            return false;
        }

        // ---- small helpers over the object table ----

        private int indexOf(String word, int from) {
            int n = buf.length - word.length();
            byte first = (byte) word.charAt(0);
            for (int i = Math.max(from, 0); i <= n; i++) {
                if (buf[i] == first && startsWith(word, i)) {
                    return i;
                }
            }
            return -1;
        }

        private boolean startsWith(String word, int at) {
            if (at < 0 || at + word.length() > buf.length) {
                return false;
            }
            for (int k = 0; k < word.length(); k++) {
                if (buf[at + k] != (byte) word.charAt(k)) {
                    return false;
                }
            }
            return true;
        }

        Object resolve(Object o) {
            for (int hops = 0; hops < 16 && o instanceof Ref; hops++) {
                o = objects.get(((Ref) o).number);
            }
            return (o instanceof Ref) ? null : o;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> dictOf(Object o) {
            o = resolve(o);
            if (o instanceof PdfStream) {
                return ((PdfStream) o).dict;
            }
            return (o instanceof Map) ? (Map<String, Object>) o : null;
        }

        @SuppressWarnings("unchecked")
        List<Object> arrayOf(Object o) {
            o = resolve(o);
            return (o instanceof List) ? (List<Object>) o : null;
        }

        String nameOf(Object o) {
            o = resolve(o);
            return (o instanceof Name) ? ((Name) o).value : null;
        }

        double number(Object o, double fallback) {
            o = resolve(o);
            return (o instanceof Number) ? ((Number) o).doubleValue() : fallback;
        }

        /** Inflates a stream (only /FlateDecode is supported); null when it cannot be decoded. */
        byte[] decode(PdfStream s) {
            Object filter = resolve(s.dict.get("Filter"));
            List<Object> filters = new ArrayList<>();
            if (filter instanceof List) {
                filters.addAll(arrayOf(filter));
            } else if (filter != null) {
                filters.add(filter);
            }
            byte[] data = s.raw;
            for (Object f : filters) {
                String name = nameOf(f);
                if ("FlateDecode".equals(name) || "Fl".equals(name)) {
                    data = inflate(data);
                } else {
                    return null;
                }
            }
            return data;
        }

        /** Inflates with a size cap; a damaged or truncated stream gives the part that did decode. */
        private byte[] inflate(byte[] input) {
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(input);
                ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(input.length * 4, 1 << 20));
                byte[] chunk = new byte[16384];
                while (!inflater.finished() && out.size() < MAX_STREAM_BYTES && totalInflated < MAX_TOTAL_INFLATED) {
                    int n = inflater.inflate(chunk);
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        break;
                    }
                    out.write(chunk, 0, n);
                    totalInflated += n;
                }
                return out.toByteArray();
            } catch (DataFormatException e) {
                return new byte[0];
            } finally {
                inflater.end();
            }
        }

        // ---- page tree ----

        private static final class Page {
            final Map<String, Object> dict;
            final Map<String, Object> resources;

            Page(Map<String, Object> dict, Map<String, Object> resources) {
                this.dict = dict;
                this.resources = resources;
            }
        }

        private List<Page> findPages() {
            List<Page> pages = new ArrayList<>();
            Map<String, Object> root = null;
            for (Map<String, Object> t : trailers) {
                if (t.containsKey("Root")) {
                    root = dictOf(t.get("Root"));
                }
            }
            if (root == null) {
                for (Object o : objects.values()) {
                    Map<String, Object> d = (o instanceof Map || o instanceof PdfStream) ? dictOf(o) : null;
                    if (d != null && "Catalog".equals(nameOf(d.get("Type")))) {
                        root = d;
                    }
                }
            }
            if (root != null) {
                collectPages(root.get("Pages"), null, new HashSet<>(), 0, pages);
            }
            if (pages.isEmpty()) {
                // damaged page tree: take every /Type /Page object in file order
                List<Integer> numbers = new ArrayList<>(objects.keySet());
                Collections.sort(numbers);
                for (int number : numbers) {
                    Map<String, Object> d = (objects.get(number) instanceof Map) ? dictOf(objects.get(number)) : null;
                    if (d != null && "Page".equals(nameOf(d.get("Type"))) && pages.size() < MAX_PAGES) {
                        pages.add(new Page(d, dictOf(d.get("Resources"))));
                    }
                }
            }
            return pages;
        }

        private void collectPages(Object node, Map<String, Object> inherited, Set<Object> seen, int depth,
                                  List<Page> pages) {
            if (pages.size() >= MAX_PAGES || depth > 32) {
                return;
            }
            if (node instanceof Ref && !seen.add(((Ref) node).number)) {
                return;
            }
            Map<String, Object> d = dictOf(node);
            if (d == null) {
                return;
            }
            Map<String, Object> res = d.containsKey("Resources") ? dictOf(d.get("Resources")) : inherited;
            List<Object> kids = arrayOf(d.get("Kids"));
            if (kids != null && !"Page".equals(nameOf(d.get("Type")))) {
                for (Object kid : kids) {
                    collectPages(kid, res, seen, depth + 1, pages);
                }
            } else {
                pages.add(new Page(d, res));
            }
        }

        // ---- text extraction ----

        String extractText() {
            Interpreter interp = new Interpreter();
            for (Page page : findPages()) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                Object contents = resolve(page.dict.get("Contents"));
                List<Object> parts = new ArrayList<>();
                if (contents instanceof List) {
                    parts.addAll(arrayOf(contents));
                } else if (contents != null) {
                    parts.add(contents);
                }
                for (Object part : parts) {
                    Object s = resolve(part);
                    if (s instanceof PdfStream) {
                        byte[] bytes = decode((PdfStream) s);
                        if (bytes != null) {
                            content.write(bytes, 0, bytes.length);
                            content.write('\n');
                        }
                    }
                }
                interp.startPage();
                interp.run(content.toByteArray(), page.resources, 0);
                interp.endPage();
                if (interp.out.length() > MAX_RAW_CHARS) {
                    break;
                }
            }
            return interp.out.toString();
        }

        // ---- fonts ----

        private Font fontFor(Map<String, Object> resources, String name) {
            Map<String, Object> fontDict = (resources == null) ? null : dictOf(resources.get("Font"));
            Map<String, Object> fd = (fontDict == null) ? null : dictOf(fontDict.get(name));
            if (fd == null) {
                return Font.DEFAULT;
            }
            Font font = fonts.get(fd);
            if (font == null) {
                font = buildFont(fd);
                fonts.put(fd, font);
            }
            return font;
        }

        private Font buildFont(Map<String, Object> fd) {
            Font font = new Font();
            String subtype = nameOf(fd.get("Subtype"));
            font.composite = "Type0".equals(subtype);
            Object toUnicode = resolve(fd.get("ToUnicode"));
            if (toUnicode instanceof PdfStream) {
                byte[] cmap = decode((PdfStream) toUnicode);
                if (cmap != null) {
                    parseCMap(cmap, font.cmap);
                }
            }
            if (font.composite) {
                List<Object> descendants = arrayOf(fd.get("DescendantFonts"));
                Map<String, Object> cid = (descendants == null || descendants.isEmpty()) ? null
                        : dictOf(descendants.get(0));
                if (cid != null) {
                    font.defaultWidth = number(cid.get("DW"), 1000);
                    readCidWidths(arrayOf(cid.get("W")), font.cidWidths);
                }
            } else {
                buildSimpleEncoding(fd, font);
                List<Object> widths = arrayOf(fd.get("Widths"));
                font.firstChar = (int) number(fd.get("FirstChar"), 0);
                if (widths != null) {
                    font.widths = new double[Math.min(widths.size(), 1024)];
                    for (int i = 0; i < font.widths.length; i++) {
                        font.widths[i] = number(widths.get(i), 0);
                    }
                }
                Map<String, Object> descriptor = dictOf(fd.get("FontDescriptor"));
                font.defaultWidth = (descriptor == null) ? 500 : number(descriptor.get("MissingWidth"), 500);
            }
            return font;
        }

        private void readCidWidths(List<Object> w, Map<Integer, Double> out) {
            if (w == null) {
                return;
            }
            int i = 0;
            while (i < w.size() && out.size() < 70_000) {
                int first = (int) number(w.get(i), 0);
                Object second = (i + 1 < w.size()) ? resolve(w.get(i + 1)) : null;
                if (second instanceof List) {
                    List<?> list = (List<?>) second;
                    for (int k = 0; k < list.size() && k < 70_000; k++) {
                        out.put(first + k, number(list.get(k), 0));
                    }
                    i += 2;
                } else if (i + 2 < w.size()) {
                    int last = (int) number(second, first);
                    double width = number(w.get(i + 2), 0);
                    for (int c = first; c <= last && c - first < 70_000; c++) {
                        out.put(c, width);
                    }
                    i += 3;
                } else {
                    break;
                }
            }
        }

        private void buildSimpleEncoding(Map<String, Object> fd, Font font) {
            Object encoding = resolve(fd.get("Encoding"));
            String base = (encoding instanceof Name) ? ((Name) encoding).value : null;
            Map<String, Object> encDict = (encoding instanceof Map) ? dictOf(encoding) : null;
            if (encDict != null) {
                base = nameOf(encDict.get("BaseEncoding"));
            }
            Charset charset = StandardCharsets.ISO_8859_1;
            try {
                charset = Charset.forName("MacRomanEncoding".equals(base) ? "x-MacRoman" : "windows-1252");
            } catch (RuntimeException e) {
                // charset not available in this runtime: Latin-1 is close enough for letters and digits
            }
            for (int c = 0; c < 256; c++) {
                String s = new String(new byte[] {(byte) c}, charset);
                font.simple[c] = (c < 32 || s.equals("�")) ? "" : s;
            }
            if (encDict != null) {
                List<Object> diffs = arrayOf(encDict.get("Differences"));
                if (diffs != null) {
                    int code = 0;
                    for (Object item : diffs) {
                        Object r = resolve(item);
                        if (r instanceof Number) {
                            code = ((Number) r).intValue();
                        } else if (r instanceof Name) {
                            if (code >= 0 && code < 256) {
                                font.simple[code] = glyphToString(((Name) r).value);
                            }
                            code++;
                        }
                    }
                }
            }
        }

        // ---- ToUnicode CMaps ----

        private static void parseCMap(byte[] data, Map<Integer, String> map) {
            Lexer lexer = new Lexer(data, 0);
            long guard = 0;
            while (map.size() < MAX_CMAP_ENTRIES && guard++ < 2_000_000L) {
                Object t = lexer.next(false, 0);
                if (t == EOF) {
                    return;
                }
                if (!(t instanceof Keyword)) {
                    continue;
                }
                String kw = ((Keyword) t).text;
                if ("beginbfchar".equals(kw)) {
                    while (map.size() < MAX_CMAP_ENTRIES) {
                        Object src = lexer.next(false, 0);
                        if (!(src instanceof PdfString)) {
                            break;
                        }
                        Object dst = lexer.next(false, 0);
                        if (dst instanceof PdfString) {
                            map.put(codeValue(((PdfString) src).bytes), utf16(((PdfString) dst).bytes));
                        }
                    }
                } else if ("beginbfrange".equals(kw)) {
                    while (map.size() < MAX_CMAP_ENTRIES) {
                        Object lo = lexer.next(false, 0);
                        if (!(lo instanceof PdfString)) {
                            break;
                        }
                        Object hi = lexer.next(false, 0);
                        Object dst = lexer.next(false, 0);
                        if (!(hi instanceof PdfString)) {
                            break;
                        }
                        int from = codeValue(((PdfString) lo).bytes);
                        int to = Math.min(codeValue(((PdfString) hi).bytes), from + 65535);
                        if (dst instanceof PdfString) {
                            String base = utf16(((PdfString) dst).bytes);
                            for (int c = from; c <= to && !base.isEmpty(); c++) {
                                map.put(c, base.substring(0, base.length() - 1)
                                        + (char) (base.charAt(base.length() - 1) + (c - from)));
                            }
                        } else if (dst instanceof List) {
                            List<?> list = (List<?>) dst;
                            for (int k = 0; k < list.size() && from + k <= to; k++) {
                                if (list.get(k) instanceof PdfString) {
                                    map.put(from + k, utf16(((PdfString) list.get(k)).bytes));
                                }
                            }
                        }
                    }
                }
            }
        }

        private static int codeValue(byte[] bytes) {
            int v = 0;
            for (int i = 0; i < bytes.length && i < 4; i++) {
                v = (v << 8) | (bytes[i] & 0xFF);
            }
            return v;
        }

        private static String utf16(byte[] bytes) {
            if (bytes.length == 1) {
                return String.valueOf((char) (bytes[0] & 0xFF));
            }
            return new String(bytes, 0, bytes.length - (bytes.length % 2), StandardCharsets.UTF_16BE);
        }

        // ---- glyph names used by /Differences ----

        private static final Map<String, String> GLYPHS = new HashMap<>();

        static {
            String[][] table = {
                {"space", " "}, {"hyphen", "-"}, {"minus", "-"}, {"period", "."}, {"comma", ","},
                {"colon", ":"}, {"semicolon", ";"}, {"slash", "/"}, {"backslash", "\\"},
                {"at", "@"}, {"ampersand", "&"}, {"parenleft", "("}, {"parenright", ")"},
                {"bracketleft", "["}, {"bracketright", "]"}, {"braceleft", "{"}, {"braceright", "}"},
                {"plus", "+"}, {"equal", "="}, {"underscore", "_"}, {"percent", "%"}, {"dollar", "$"},
                {"numbersign", "#"}, {"asterisk", "*"}, {"exclam", "!"}, {"question", "?"},
                {"less", "<"}, {"greater", ">"}, {"bar", "|"}, {"asciitilde", "~"}, {"asciicircum", "^"},
                {"grave", "`"}, {"quotesingle", "'"}, {"quotedbl", "\""}, {"zero", "0"}, {"one", "1"},
                {"two", "2"}, {"three", "3"}, {"four", "4"}, {"five", "5"}, {"six", "6"},
                {"seven", "7"}, {"eight", "8"}, {"nine", "9"}, {"bullet", "•"},
                {"endash", "–"}, {"emdash", "—"}, {"quoteright", "’"},
                {"quoteleft", "‘"}, {"quotedblleft", "“"}, {"quotedblright", "”"},
                {"ellipsis", "…"}, {"fi", "fi"}, {"fl", "fl"}, {"ff", "ff"}, {"ffi", "ffi"},
                {"ffl", "ffl"}, {"degree", "°"}, {"copyright", "©"}, {"registered", "®"},
                {"trademark", "™"}, {"periodcentered", "·"}, {"eacute", "é"},
                {"egrave", "è"}, {"agrave", "à"}, {"aacute", "á"}, {"oacute", "ó"},
                {"uacute", "ú"}, {"iacute", "í"}, {"ntilde", "ñ"}, {"adieresis", "ä"},
                {"odieresis", "ö"}, {"udieresis", "ü"}, {"germandbls", "ß"},
                {"nbspace", " "}, {"nonbreakingspace", " "}
            };
            for (String[] row : table) {
                GLYPHS.put(row[0], row[1]);
            }
        }

        private static String glyphToString(String name) {
            String known = GLYPHS.get(name);
            if (known != null) {
                return known;
            }
            if (name.length() == 1) {
                return name;
            }
            if (name.length() == 7 && name.startsWith("uni")) {
                try {
                    return String.valueOf((char) Integer.parseInt(name.substring(3), 16));
                } catch (NumberFormatException e) {
                    return "";
                }
            }
            int dot = name.indexOf('.');
            if (dot > 0) {
                return glyphToString(name.substring(0, dot));
            }
            return "";
        }

        // ------------------------------------------------------------------
        // Content stream interpreter
        // ------------------------------------------------------------------

        private final class Interpreter {
            final StringBuilder out = new StringBuilder();
            private double[] ctm = identity();
            private double[] tm = identity();
            private double[] tlm = identity();
            private Font font = Font.DEFAULT;
            private double fontSize = 12;
            private double charSpace;
            private double wordSpace;
            private double hScale = 1;
            private double leading;
            private boolean haveLast;
            private double lastEndX;
            private double lastY;
            private long tokens;
            private final Deque<Object[]> saved = new ArrayDeque<>();

            void startPage() {
                ctm = identity();
                tm = identity();
                tlm = identity();
                font = Font.DEFAULT;
                saved.clear();
                tokens = 0;
                if (out.length() > 0) {
                    newLine();
                }
                haveLast = false;
            }

            void endPage() {
                haveLast = false;
            }

            void run(byte[] content, Map<String, Object> resources, int depth) {
                Lexer lexer = new Lexer(content, 0);
                List<Object> operands = new ArrayList<>();
                while (tokens++ < MAX_TOKENS_PER_PAGE && out.length() < MAX_RAW_CHARS) {
                    Object o = lexer.next(false, 0);
                    if (o == EOF) {
                        break;
                    }
                    if (o instanceof Keyword) {
                        execute(((Keyword) o).text, operands, resources, depth, lexer);
                        operands.clear();
                    } else if (operands.size() < 4096) {
                        operands.add(o);
                    }
                }
            }

            private void execute(String op, List<Object> a, Map<String, Object> resources, int depth, Lexer lexer) {
                switch (op) {
                    case "q":
                        if (saved.size() < 256) {
                            saved.push(new Object[] {ctm.clone(), font, fontSize, charSpace, wordSpace, hScale, leading});
                        }
                        break;
                    case "Q":
                        if (!saved.isEmpty()) {
                            Object[] s = saved.pop();
                            ctm = (double[]) s[0];
                            font = (Font) s[1];
                            fontSize = (Double) s[2];
                            charSpace = (Double) s[3];
                            wordSpace = (Double) s[4];
                            hScale = (Double) s[5];
                            leading = (Double) s[6];
                        }
                        break;
                    case "cm":
                        if (a.size() >= 6) {
                            ctm = multiply(matrix(a, 0), ctm);
                        }
                        break;
                    case "BT":
                        tm = identity();
                        tlm = identity();
                        break;
                    case "Tf":
                        if (a.size() >= 2 && a.get(0) instanceof Name) {
                            font = fontFor(resources, ((Name) a.get(0)).value);
                            fontSize = num(a, 1);
                        }
                        break;
                    case "Tc": charSpace = num(a, 0); break;
                    case "Tw": wordSpace = num(a, 0); break;
                    case "Tz": hScale = num(a, 0) / 100.0; break;
                    case "TL": leading = num(a, 0); break;
                    case "Td": moveLine(num(a, 0), num(a, 1)); break;
                    case "TD":
                        leading = -num(a, 1);
                        moveLine(num(a, 0), num(a, 1));
                        break;
                    case "T*": moveLine(0, -leading); break;
                    case "Tm":
                        if (a.size() >= 6) {
                            tm = matrix(a, 0);
                            tlm = tm.clone();
                        }
                        break;
                    case "Tj":
                        if (!a.isEmpty() && a.get(0) instanceof PdfString) {
                            show(((PdfString) a.get(0)).bytes);
                        }
                        break;
                    case "'":
                        moveLine(0, -leading);
                        if (!a.isEmpty() && a.get(0) instanceof PdfString) {
                            show(((PdfString) a.get(0)).bytes);
                        }
                        break;
                    case "\"":
                        if (a.size() >= 3 && a.get(2) instanceof PdfString) {
                            wordSpace = num(a, 0);
                            charSpace = num(a, 1);
                            moveLine(0, -leading);
                            show(((PdfString) a.get(2)).bytes);
                        }
                        break;
                    case "TJ":
                        if (!a.isEmpty() && a.get(0) instanceof List) {
                            for (Object item : (List<?>) a.get(0)) {
                                if (item instanceof PdfString) {
                                    show(((PdfString) item).bytes);
                                } else if (item instanceof Number) {
                                    double shift = -((Number) item).doubleValue() / 1000.0 * fontSize * hScale;
                                    tm[4] += shift * tm[0];
                                    tm[5] += shift * tm[1];
                                }
                            }
                        }
                        break;
                    case "Do":
                        if (depth < 3 && !a.isEmpty() && a.get(0) instanceof Name) {
                            drawForm(resources, ((Name) a.get(0)).value, depth);
                        }
                        break;
                    case "BI":
                        skipInlineImage(lexer);
                        break;
                    default:
                        break;
                }
            }

            private void drawForm(Map<String, Object> resources, String name, int depth) {
                Map<String, Object> xobjects = (resources == null) ? null : dictOf(resources.get("XObject"));
                Object x = (xobjects == null) ? null : resolve(xobjects.get(name));
                if (!(x instanceof PdfStream) || !"Form".equals(nameOf(((PdfStream) x).dict.get("Subtype")))) {
                    return;
                }
                PdfStream form = (PdfStream) x;
                byte[] content = decode(form);
                if (content == null) {
                    return;
                }
                Map<String, Object> formResources = form.dict.containsKey("Resources")
                        ? dictOf(form.dict.get("Resources")) : resources;
                double[] saveCtm = ctm;
                double[] saveTm = tm;
                double[] saveTlm = tlm;
                Font saveFont = font;
                List<Object> m = arrayOf(form.dict.get("Matrix"));
                if (m != null && m.size() >= 6) {
                    ctm = multiply(matrix(m, 0), ctm);
                }
                run(content, formResources, depth + 1);
                ctm = saveCtm;
                tm = saveTm;
                tlm = saveTlm;
                font = saveFont;
            }

            private void skipInlineImage(Lexer lexer) {
                Object t;
                int guard = 0;
                do {
                    t = lexer.next(false, 0);
                } while (t != EOF && guard++ < 1000 && !(t instanceof Keyword && "ID".equals(((Keyword) t).text)));
                byte[] b = lexer.b;
                int i = lexer.pos + 1;
                while (i + 1 < b.length) {
                    if (b[i] == 'E' && b[i + 1] == 'I' && Lexer.isWhite(b[i - 1] & 0xFF)
                            && (i + 2 >= b.length || Lexer.isWhite(b[i + 2] & 0xFF))) {
                        break;
                    }
                    i++;
                }
                lexer.pos = Math.min(i + 2, b.length);
            }

            private void moveLine(double tx, double ty) {
                tlm = multiply(new double[] {1, 0, 0, 1, tx, ty}, tlm);
                tm = tlm.clone();
            }

            /** Adds the text of one string operand, with a new line or a space first when the position says so. */
            private void show(byte[] s) {
                double[] trm = multiply(tm, ctm);
                double size = Math.max(fontSize * Math.hypot(trm[2], trm[3]), 0.01);
                double x = trm[4];
                double y = trm[5];
                if (haveLast && out.length() > 0) {
                    if (Math.abs(y - lastY) > 0.5 * size) {
                        newLine();
                    } else if (x - lastEndX > 0.2 * size || x - lastEndX < -2 * size) {
                        space();
                    }
                }
                int step = font.composite ? 2 : 1;
                for (int i = 0; i + step <= s.length; i += step) {
                    int code = (step == 2) ? ((s[i] & 0xFF) << 8) | (s[i + 1] & 0xFF) : (s[i] & 0xFF);
                    String u = font.toUnicode(code);
                    out.append(u);
                    double advance = font.width(code) / 1000.0 * fontSize + charSpace;
                    if (step == 1 && code == 32) {
                        advance += wordSpace;
                    }
                    advance *= hScale;
                    tm[4] += advance * tm[0];
                    tm[5] += advance * tm[1];
                }
                double[] end = multiply(tm, ctm);
                lastEndX = end[4];
                lastY = end[5];
                haveLast = true;
            }

            private void newLine() {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
            }

            private void space() {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '\n') {
                    out.append(' ');
                }
            }
        }

        private static double num(List<Object> a, int i) {
            return (i < a.size() && a.get(i) instanceof Number) ? ((Number) a.get(i)).doubleValue() : 0;
        }

        private static double[] matrix(List<Object> a, int from) {
            double[] m = new double[6];
            for (int i = 0; i < 6; i++) {
                m[i] = (from + i < a.size() && a.get(from + i) instanceof Number)
                        ? ((Number) a.get(from + i)).doubleValue() : 0;
            }
            return m;
        }

        private static double[] identity() {
            return new double[] {1, 0, 0, 1, 0, 0};
        }

        /** Row-vector matrix product x * y, as in the PDF specification. */
        private static double[] multiply(double[] x, double[] y) {
            return new double[] {
                x[0] * y[0] + x[1] * y[2],
                x[0] * y[1] + x[1] * y[3],
                x[2] * y[0] + x[3] * y[2],
                x[2] * y[1] + x[3] * y[3],
                x[4] * y[0] + x[5] * y[2] + y[4],
                x[4] * y[1] + x[5] * y[3] + y[5]
            };
        }
    }

    // ------------------------------------------------------------------
    // Font: character code -> Unicode text, and glyph width
    // ------------------------------------------------------------------

    private static final class Font {
        static final Font DEFAULT = new Font();

        boolean composite;
        final Map<Integer, String> cmap = new HashMap<>();
        final String[] simple = new String[256];
        double[] widths;
        int firstChar;
        double defaultWidth = 500;
        final Map<Integer, Double> cidWidths = new HashMap<>();

        Font() {
            for (int c = 0; c < 256; c++) {
                simple[c] = (c >= 32 && c < 127) ? String.valueOf((char) c) : "";
            }
        }

        String toUnicode(int code) {
            String mapped = cmap.get(code);
            if (mapped != null) {
                return mapped;
            }
            return (composite || code > 255) ? "" : simple[code];
        }

        double width(int code) {
            if (composite) {
                Double w = cidWidths.get(code);
                return (w != null) ? w : defaultWidth;
            }
            int index = code - firstChar;
            if (widths != null && index >= 0 && index < widths.length) {
                return widths[index];
            }
            return defaultWidth;
        }
    }
}
