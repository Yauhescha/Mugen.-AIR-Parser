package def;

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
 * <h2>M.U.G.E.N .def parser (character / stage / motif)</h2>
 * <p>
 *     The .def format is an INI‑style text file consisting of <code>[Section]</code>
 *     headers followed by <code>key = value</code> pairs. Compared with CNS it
 *     is simpler – no nested controllers – yet we still need permissive parsing
 *     because real‑world files frequently contain duplicate keys or rely on
 *     colon (<code>:</code>) instead of equals (<code>=</code>) as the delimiter.
 * </p>
 * <p>
 *     This implementation is feature‑complete and thread‑safe:
 *     <ul>
 *         <li>UTF‑8 (BOM optional)</li>
 *         <li>Comment stripping with <code>;</code></li>
 *         <li>Preserves original order of sections and entries</li>
 *         <li>Allows duplicate keys – each maps to a <code>List&lt;String&gt;</code></li>
 *         <li>No runtime deps other than Lombok</li>
 *     </ul>
 * </p>
 */
public final class DefParser {

    // --------------------------------------------------
    // Regex patterns
    // --------------------------------------------------

    private static final Pattern SECTION =
            Pattern.compile("^\\s*\\[(?<name>[^]]+)]\\s*$");

    private static final Pattern KEY_VALUE =
            Pattern.compile("^(?<key>[^:=]+?)\\s*[:=]\\s*(?<val>.*)$");

    private DefParser() { /* util */ }

    // --------------------------------------------------
    // Public API
    // --------------------------------------------------

    /** Parse a .def file from disk. */
    public static @NonNull DefFile parse(@NonNull Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(r);
        }
    }

    /** Parse from already opened reader (useful for tests). */
    public static @NonNull DefFile parse(@NonNull BufferedReader reader) throws IOException {
        List<Section> sections = new ArrayList<>();
        Builder cur = null;

        String line;
        int ln = 0;
        while ((line = reader.readLine()) != null) {
            ++ln;
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1); // strip BOM
            }
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher mSec = SECTION.matcher(line);
            if (mSec.matches()) {
                if (cur != null) sections.add(cur.build());
                cur = new Builder(mSec.group("name"));
                continue;
            }
            if (cur == null) throw err("Data before any section", ln);

            Matcher mKv = KEY_VALUE.matcher(line);
            if (mKv.matches()) {
                cur.put(mKv.group("key").trim(), mKv.group("val").trim());
            } else {
                // some DEF lines are bare tokens (e.g. "sprite = xyz") – treat whole line as key, empty value
                cur.put(line, "");
            }
        }
        if (cur != null) sections.add(cur.build());

        Map<String, List<Section>> idx = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Section s : sections) idx.computeIfAbsent(s.getRawName(), k -> new ArrayList<>()).add(s);

        return DefFile.builder()
                .sections(Collections.unmodifiableList(sections))
                .byName(Collections.unmodifiableMap(idx))
                .build();
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private static RuntimeException err(String m, int ln) { return new DefParseException("Line " + ln + ": " + m); }

    private static final class Builder {
        final String raw;
        final List<Entry> entries = new ArrayList<>();
        final Map<String, List<String>> map = new LinkedHashMap<>();
        Builder(String raw) { this.raw = raw; }
        void put(String k, String v) {
            entries.add(Entry.builder().key(k).value(v).build());
            map.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
        }
        Section build() {
            return Section.builder()
                    .rawName(raw)
                    .entries(Collections.unmodifiableList(entries))
                    .values(Collections.unmodifiableMap(map))
                    .build();
        }
    }

    // --------------------------------------------------
    // Model
    // --------------------------------------------------

    @Data
    @lombok.Builder
    public static class DefFile {
        @NonNull List<Section> sections;
        @NonNull Map<String, List<Section>> byName;
        public Optional<Section> first(String name) {
            List<Section> l = byName.get(name);
            return l==null||l.isEmpty()?Optional.empty():Optional.of(l.get(0));
        }
    }

    @Data
    @lombok.Builder
    public static class Section {
        @NonNull String rawName;
        @NonNull List<Entry> entries;
        @NonNull Map<String, List<String>> values;
        public List<String> getAll(String key){return values.get(key);}        // may be null
        public String getFirst(String key){List<String> l=values.get(key);return l==null||l.isEmpty()?null:l.get(0);} }

    @Data
    @lombok.Builder
    public static class Entry { String key, value; }

    // --------------------------------------------------
    // Exception
    // --------------------------------------------------

    public static class DefParseException extends RuntimeException { public DefParseException(String m){super(m);} }
}
