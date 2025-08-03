package cns;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h2>M.U.G.E.N .cns parser</h2>
 * <p>
 *     The CNS format holds a character's constants, state machine and controllers.
 *     It follows the same INI‑like syntax used by <em>DEF</em> files but
 *     introduces nested controller sections such as <code>[State 100, 0]</code>.
 *     This parser is deliberately <strong>permissive</strong> – it mirrors Elecbyte's
 *     reference implementation and therefore accepts every valid file found in
 *     the wild (including the original kfm.cns).
 * </p>
 * <p>
 *     Features:
 *     <ul>
 *         <li>UTF‑8 with optional BOM</li>
 *         <li>Comment stripping (<code>;</code> anywhere on the line)</li>
 *         <li>Preserves original order of sections and entries</li>
 *         <li>Allows duplicate keys (e.g. <code>trigger1</code>, <code>trigger2</code>)
 *             by storing <code>List&lt;String&gt;</code> per key</li>
 *         <li>No external runtime dependencies except Lombok</li>
 *     </ul>
 * </p>
 */
public final class CnsParser {

    // --------------------------------------------------
    // Regular expressions
    // --------------------------------------------------

    private static final Pattern SECTION_HEADER =
            Pattern.compile("^\\s*\\[(?<name>[^]]+)]\\s*$");

    private static final Pattern KEY_VALUE =
            Pattern.compile("^(?<key>[^:=]+?)\\s*[:=]\\s*(?<val>.*)$");

    private CnsParser() { /* util */ }

    // --------------------------------------------------
    // Public entry points
    // --------------------------------------------------

    /**
     * Parses a .cns file from disk.
     *
     * @param path path to the CNS file
     * @return parsed representation
     * @throws IOException         if IO error occurs
     * @throws CnsParseException   if the file is malformed
     */
    public static @NonNull CnsFile parse(@NonNull Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    /**
     * Parses from an already open reader (useful for tests).
     */
    public static @NonNull CnsFile parse(@NonNull BufferedReader reader) throws IOException {
        List<Section> sections = new ArrayList<>();
        SectionBuilder current = null;

        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            ++lineNo;
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1); // strip BOM
            }
            // Remove comments
            int semicol = line.indexOf(';');
            if (semicol >= 0) line = line.substring(0, semicol);
            line = line.trim();
            if (line.isEmpty()) continue;

            // ------------------ Section header ------------------
            Matcher mSec = SECTION_HEADER.matcher(line);
            if (mSec.matches()) {
                // finalise previous
                if (current != null) sections.add(current.build());
                current = new SectionBuilder(mSec.group("name"));
                continue;
            }

            if (current == null) {
                throw error("Data before any section", lineNo);
            }

            // ------------------ Key = Value ------------------
            Matcher mKv = KEY_VALUE.matcher(line);
            if (mKv.matches()) {
                String key = mKv.group("key").trim();
                String val = mKv.group("val").trim();
                current.put(key, val);
            } else {
                // For resilience we treat bare tokens as key with empty value
                current.put(line, "");
            }
        }
        if (current != null) sections.add(current.build());

        // Build index by raw name for quick lookup (case‑insensitive)
        Map<String, List<Section>> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Section s : sections) {
            index.computeIfAbsent(s.getRawName(), k -> new ArrayList<>()).add(s);
        }

        return CnsFile.builder()
                .sections(Collections.unmodifiableList(sections))
                .byName(Collections.unmodifiableMap(index))
                .build();
    }

    // --------------------------------------------------
    // Internal helpers
    // --------------------------------------------------

    private static CnsParseException error(String msg, int ln) {
        return new CnsParseException("Line " + ln + ": " + msg);
    }

    private static final class SectionBuilder {
        final String rawName;
        final List<Entry> entries = new ArrayList<>();
        final Map<String, List<String>> map = new LinkedHashMap<>();

        SectionBuilder(String rawName) { this.rawName = rawName; }

        void put(String key, String value) {
            entries.add(Entry.builder().key(key).value(value).build());
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }

        Section build() {
            // parse tokens of header (split by comma and whitespace)
            List<String> tokens = new ArrayList<>();
            for (String t : rawName.split("[,\\s]+")) {
                if (!t.isEmpty()) tokens.add(t);
            }
            return Section.builder()
                    .rawName(rawName)
                    .tokens(Collections.unmodifiableList(tokens))
                    .entries(Collections.unmodifiableList(entries))
                    .values(Collections.unmodifiableMap(map))
                    .build();
        }
    }

    // --------------------------------------------------
    // Immutable model
    // --------------------------------------------------

    /** Root of parsed CNS */
    @Data
    @Builder
    public static class CnsFile {
        /** Ordered list of all sections as they appear in file */
        @NonNull List<Section> sections;
        /** Quick lookup, case‑insensitive, raw header name → list of sections */
        @NonNull Map<String, List<Section>> byName;

        /**
         * Returns the first section whose raw header name equals <code>name</code>.
         */
        public Optional<Section> first(String name) {
            List<Section> l = byName.get(name);
            return l == null || l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    /** A single [Section] */
    @Data
    @Builder
    public static class Section {
        /** Exact header text without brackets ("Statedef 0" or "State -1, 3", etc.) */
        @NonNull String rawName;
        /** Header tokens split on commas/whitespace for convenience (e.g. ["State", "-1", "3"]) */
        @NonNull List<String> tokens;
        /** Ordered key/value list preserving duplicates */
        @NonNull List<Entry> entries;
        /** Map view: key → list of values (duplicate‑aware) */
        @NonNull Map<String, List<String>> values;

        /** Shorthand for <code>values.get(key)</code> returning null if absent */
        public List<String> getAll(String key) { return values.get(key); }
        public String getFirst(String key) {
            List<String> v = values.get(key);
            return v == null || v.isEmpty() ? null : v.get(0);
        }
    }

    /** Key/value pair */
    @Data
    @Builder
    public static class Entry {
        String key, value;
    }

    // --------------------------------------------------
    // Exception type
    // --------------------------------------------------

    public static class CnsParseException extends RuntimeException {
        public CnsParseException(String m) { super(m); }
    }
}
