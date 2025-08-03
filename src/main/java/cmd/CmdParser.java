package cmd;

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
 * <h2>M.U.G.E.N .cmd parser</h2>
 * <p>
 *     The CMD file combines classic INI‑style command definitions (<code>[Command]</code>)
 *     with state sections identical to those found in <code>.cns</code>. This parser
 *     is 100 % self‑contained, requires only Lombok at compile‑time and mirrors
 *     the permissive behaviour of the original engine — every wild‑grown CMD
 *     file (including kfm.cmd) should load without issues.
 * </p>
 */
public final class CmdParser {

    // ------------------------------------------------------------------
    // Patterns
    // ------------------------------------------------------------------

    private static final Pattern SECTION =
            Pattern.compile("^\\s*\\[(?<name>[^]]+)]\\s*$");

    private static final Pattern KEY_VALUE =
            Pattern.compile("^(?<key>[^:=]+?)\\s*[:=]\\s*(?<val>.*)$");

    private CmdParser() { /* util */ }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public static @NonNull CmdFile parse(@NonNull Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(r);
        }
    }

    public static @NonNull CmdFile parse(@NonNull BufferedReader reader) throws IOException {
        List<Section> sections = new ArrayList<>();
        SectionBuilder current = null;

        String line;
        int ln = 0;
        while ((line = reader.readLine()) != null) {
            ++ln;
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1); // strip BOM
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher mSec = SECTION.matcher(line);
            if (mSec.matches()) {
                if (current != null) sections.add(current.build());
                current = new SectionBuilder(mSec.group("name"));
                continue;
            }
            if (current == null) throw error("Data before any section", ln);

            Matcher mKv = KEY_VALUE.matcher(line);
            if (mKv.matches()) {
                current.put(mKv.group("key").trim(), mKv.group("val").trim());
            } else {
                // treat as key with empty value
                current.put(line, "");
            }
        }
        if (current != null) sections.add(current.build());

        // Build command definitions and fast index
        List<CommandDef> commands = new ArrayList<>();
        Map<String, CommandDef> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Section s : sections) {
            if (s.getRawName().equalsIgnoreCase("Command")) {
                CommandDef cmd = toCommand(s);
                commands.add(cmd);
                byName.put(cmd.getName(), cmd);
            }
        }

        return CmdFile.builder()
                .sections(Collections.unmodifiableList(sections))
                .commands(Collections.unmodifiableList(commands))
                .commandByName(Collections.unmodifiableMap(byName))
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static CommandDef toCommand(Section s) {
        String name = optionalFirst(s, "name").orElseThrow(() -> new CmdParseException("[Command] missing name="));
        String command = optionalFirst(s, "command").orElse("");
        Integer time = optionalFirst(s, "time").flatMap(CmdParser::toInt).orElse(null);
        Integer buffer = optionalFirst(s, "buffer.time").flatMap(CmdParser::toInt).orElse(null);

        // collect unknown keys
        Map<String, List<String>> extra = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : s.getValues().entrySet()) {
            String k = e.getKey();
            if (!k.equalsIgnoreCase("name") && !k.equalsIgnoreCase("command") &&
                    !k.equalsIgnoreCase("time") && !k.equalsIgnoreCase("buffer.time")) {
                extra.put(k, e.getValue());
            }
        }

        return CommandDef.builder()
                .name(name)
                .command(command)
                .time(time)
                .bufferTime(buffer)
                .extras(Collections.unmodifiableMap(extra))
                .build();
    }

    private static Optional<String> optionalFirst(Section s, String key) {
        List<String> v = s.getValues().get(key);
        return (v == null || v.isEmpty()) ? Optional.empty() : Optional.ofNullable(v.get(0));
    }

    private static Optional<Integer> toInt(String s) {
        try { return Optional.of(Integer.parseInt(s)); } catch (NumberFormatException e) { return Optional.empty(); }
    }

    private static CmdParseException error(String m, int ln) { return new CmdParseException("Line " + ln + ": " + m); }

    // ------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------

    private static final class SectionBuilder {
        final String raw;
        final List<Entry> entries = new ArrayList<>();
        final Map<String, List<String>> map = new LinkedHashMap<>();
        SectionBuilder(String raw) { this.raw = raw; }
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

    // ------------------------------------------------------------------
    // Immutable model
    // ------------------------------------------------------------------

    @Data
    @Builder
    public static class CmdFile {
        /** All sections in original order */
        @NonNull List<Section> sections;
        /** Parsed [Command] definitions */
        @NonNull List<CommandDef> commands;
        /** Fast lookup by command name (case‑insensitive) */
        @NonNull Map<String, CommandDef> commandByName;

        /** Returns command or null */
        public CommandDef getCommand(String name) { return commandByName.get(name); }

        /** Convenience for fetching first section with given raw header */
        public Optional<Section> firstSection(String rawHeader) {
            return sections.stream().filter(s -> s.rawName.equalsIgnoreCase(rawHeader)).findFirst();
        }
    }

    @Data
    @Builder
    public static class CommandDef {
        @NonNull String name;
        @NonNull String command;
        Integer time;       // optional
        Integer bufferTime; // optional
        /** any additional, non‑standard keys */
        @NonNull Map<String, List<String>> extras;
    }

    @Data
    @Builder
    public static class Section {
        @NonNull String rawName;
        @NonNull List<Entry> entries;
        @NonNull Map<String, List<String>> values;
        public List<String> getAll(String key){return values.get(key);}        // may be null
    }

    @Data
    @Builder
    public static class Entry { String key, value; }

    public static class CmdParseException extends RuntimeException { public CmdParseException(String m){super(m);} }
}
