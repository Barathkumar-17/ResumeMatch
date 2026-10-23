package com.resumematch.service;

import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads the text of a Word (.docx) resume. A .docx is a ZIP package; the text lives in
 * {@code word/document.xml}, where every {@code <w:t>} holds a piece of text, {@code <w:tab/>}
 * is a tab, {@code <w:br/>} and {@code <w:cr/>} are line breaks and each {@code <w:p>}
 * paragraph (table cells included) ends a line. Word often splits one word over several
 * runs when the formatting changes; joining the runs puts it back together.
 *
 * <p>The package is untrusted input: only that one entry is read, never more than 10 MB of
 * it, and the XML parser has DTDs and external entities switched off so an XML bomb or an
 * external-entity file read cannot happen.</p>
 *
 * <p>Demonstrates: interface implementation, {@code java.util.zip.ZipFile}, StAX pull
 * parsing ({@code XMLStreamReader}), secure XML configuration, try-with-resources,
 * resource limits, exception wrapping.</p>
 */
public class DocxResumeReader implements ResumeReader {

    private static final Logger LOGGER = Logger.getLogger(DocxResumeReader.class.getName());

    /** Most uncompressed bytes read from word/document.xml (zip bomb guard). */
    static final long MAX_UNCOMPRESSED_BYTES = 10L * 1024 * 1024;

    /** Most entries a package may hold; a resume has a few dozen. */
    static final int MAX_ENTRIES = 2000;

    private static final String DOCUMENT_ENTRY = "word/document.xml";

    @Override
    public String read(Path p) throws FileReadException, InvalidFileException {
        if (p == null) {
            throw new FileReadException();
        }
        try (ZipFile zip = new ZipFile(p.toFile())) {
            if (zip.size() > MAX_ENTRIES) {
                throw InvalidFileException.unreadableDocument(".docx");
            }
            ZipEntry entry = zip.getEntry(DOCUMENT_ENTRY);
            if (entry == null || entry.isDirectory()) {
                throw InvalidFileException.unreadableDocument(".docx");
            }
            byte[] xml;
            try (InputStream in = zip.getInputStream(entry)) {
                xml = readCapped(in);
            }
            return ResumeReader.tidy(extractText(xml));
        } catch (ZipException e) {
            LOGGER.log(Level.WARNING, "not a readable .docx package", e);
            throw InvalidFileException.unreadableDocument(".docx");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not read the .docx file", e);
            throw new FileReadException(e);
        } catch (XMLStreamException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "could not parse word/document.xml", e);
            throw InvalidFileException.unreadableDocument(".docx");
        }
    }

    /** Reads the entry, refusing to inflate more than {@link #MAX_UNCOMPRESSED_BYTES}. */
    private static byte[] readCapped(InputStream in) throws IOException, InvalidFileException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > MAX_UNCOMPRESSED_BYTES) {
                throw InvalidFileException.unreadableDocument(".docx");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Walks the XML once and collects the text in reading order. */
    private static String extractText(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        try {
            factory.setProperty("javax.xml.accessExternalDTD", "");
        } catch (IllegalArgumentException ignored) {
            // this parser does not know the property; DTDs are already switched off
        }

        StringBuilder text = new StringBuilder();
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            int runDepth = 0;       // inside <w:r>; a <w:tab/> in paragraph properties is not a tab
            int fallbackDepth = 0;  // <mc:Fallback> repeats the text of <mc:Choice>, so skip it
            boolean inText = false;
            while (reader.hasNext() && text.length() < MAX_UNCOMPRESSED_BYTES) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (fallbackDepth > 0 || "Fallback".equals(name)) {
                        if ("Fallback".equals(name)) {
                            fallbackDepth++;
                        }
                        continue;
                    }
                    switch (name) {
                        case "r": runDepth++; break;
                        case "t": inText = runDepth > 0; break;
                        case "tab": if (runDepth > 0) { text.append('\t'); } break;
                        case "br":
                        case "cr": if (runDepth > 0) { text.append('\n'); } break;
                        default: break;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if (fallbackDepth > 0) {
                        if ("Fallback".equals(name)) {
                            fallbackDepth--;
                        }
                        continue;
                    }
                    switch (name) {
                        case "r": runDepth = Math.max(0, runDepth - 1); break;
                        case "t": inText = false; break;
                        case "p": text.append('\n'); break;
                        default: break;
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && inText && fallbackDepth == 0) {
                    text.append(reader.getText());
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }
}
