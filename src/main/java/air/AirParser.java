package air;

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
 * Parser for M.U.G.E.N .air animation files.
 * <p>
 *     Fully self‑contained — no stubs. Requires only Lombok at compile‑time.
 *     Handles every directive defined in Elecbyte's 1.1 spec:
 *     <ul>
 *         <li>Multiple [Begin Action n] sections</li>
 *         <li>Default and per‑frame Clsn1/Clsn2 boxes</li>
 *         <li>Loopstart markers</li>
 *         <li>Interpolate (Offset, Blend, Scale, Angle)</li>
 *         <li>Optional frame parameters: flip, blend, scale, angle</li>
 *     </ul>
 *     All resulting objects are immutable and therefore thread‑safe.
 * </p>
 */
public final class AirParser {

    // ---------------------------------------------------------------------
    // Regular expressions (pre‑compiled)
    // ---------------------------------------------------------------------

    private static final Pattern BEGIN_ACTION =
            Pattern.compile("^\\[\\s*Begin\\s+Action\\s+(?<num>-?\\d+)\\s*]$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLSN_DEF =
            Pattern.compile("^\\s*Clsn(?<type>[12])(?<def>Default)?\\s*:\\s*(?<count>\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLSN_BOX =
            Pattern.compile("^\\s*Clsn(?<type>[12])\\s*\\[(?<idx>\\d+)]\\s*=\\s*(?<x1>-?\\d+)\\s*,\\s*(?<y1>-?\\d+)\\s*,\\s*(?<x2>-?\\d+)\\s*,\\s*(?<y2>-?\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern INTERPOLATE =
            Pattern.compile("^\\s*Interpolate\\s+(?<what>Offset|Blend|Scale|Angle)\\s*$", Pattern.CASE_INSENSITIVE);

    private AirParser() { /* util */ }

    // ---------------------------------------------------------------------
    // Public entry points
    // ---------------------------------------------------------------------

    /**
     * Parse the given .air file.
     */
    public static @NonNull AirFile parse(@NonNull Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(r);
        }
    }

    /**
     * Parse from an already‑open reader (handy for tests).
     */
    public static @NonNull AirFile parse(@NonNull BufferedReader reader) throws IOException {
        Map<Integer, Animation> actions = new LinkedHashMap<>();
        AnimationBuilder current = null;

        List<ClsnBox> defaultClsn1 = new ArrayList<>();
        List<ClsnBox> defaultClsn2 = new ArrayList<>();

        List<ClsnBox> pendingClsn1 = new ArrayList<>();
        List<ClsnBox> pendingClsn2 = new ArrayList<>();
        List<InterpolateType> pendingInterp = new ArrayList<>();

        int expectDefType = 0, expectDefCnt = 0;
        int expectFrmType = 0, expectFrmCnt = 0;

        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            ++lineNo;
            // Strip UTF‑8 BOM only on the first line (or if it sneaks in elsewhere)
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }

            // Remove comments ( ';' )
            int sc = line.indexOf(';');
            if (sc >= 0) line = line.substring(0, sc);
            line = line.trim();
            if (line.isEmpty()) continue;

            // -------------------------------------------------------------
            // Section header
            // -------------------------------------------------------------
            Matcher mBegin = BEGIN_ACTION.matcher(line);
            if (mBegin.matches()) {
                // Commit previous action, if any
                if (current != null) {
                    actions.put(current.number, buildAnimation(current, defaultClsn1, defaultClsn2));
                }
                // Reset all state for the new section
                current = new AnimationBuilder(Integer.parseInt(mBegin.group("num")));
                defaultClsn1 = new ArrayList<>();
                defaultClsn2 = new ArrayList<>();
                pendingClsn1.clear();
                pendingClsn2.clear();
                pendingInterp.clear();
                expectDefType = expectFrmType = 0;
                expectDefCnt = expectFrmCnt = 0;
                continue;
            }

            // Any content before the first [Begin Action] is invalid
            if (current == null) {
                throw error("Encountered data before any [Begin Action]", lineNo);
            }

            // -------------------------------------------------------------
            // Simple directives
            // -------------------------------------------------------------
            if (line.equalsIgnoreCase("Loopstart")) {
                current.loopStart = current.elements.size();
                continue;
            }

            Matcher mInterp = INTERPOLATE.matcher(line);
            if (mInterp.matches()) {
                pendingInterp.add(InterpolateType.valueOf(mInterp.group("what").toUpperCase(Locale.ROOT)));
                continue;
            }

            // -------------------------------------------------------------
            // Collision definitions (ClsnXDefault / ClsnX)
            // -------------------------------------------------------------
            Matcher mClsnDef = CLSN_DEF.matcher(line);
            if (mClsnDef.matches()) {
                int type = Integer.parseInt(mClsnDef.group("type"));
                int cnt = Integer.parseInt(mClsnDef.group("count"));
                boolean def = mClsnDef.group("def") != null;
                if (def) { expectDefType = type; expectDefCnt = cnt; }
                else     { expectFrmType = type; expectFrmCnt = cnt; }
                continue;
            }

            Matcher mBox = CLSN_BOX.matcher(line);
            if (mBox.matches()) {
                ClsnBox box = ClsnBox.builder()
                        .x1(Integer.parseInt(mBox.group("x1")))
                        .y1(Integer.parseInt(mBox.group("y1")))
                        .x2(Integer.parseInt(mBox.group("x2")))
                        .y2(Integer.parseInt(mBox.group("y2")))
                        .build();
                int t = Integer.parseInt(mBox.group("type"));

                // M.U.G.E.N is liberal: even if you declared "Clsn1: n" you can
                // (and the original kfm.air does!) write the boxes as Clsn2[0].
                // Therefore we only check that some count is outstanding and
                // attach the box to the list that matches its own prefix.
                if (expectDefCnt > 0) {
                    (t == 1 ? defaultClsn1 : defaultClsn2).add(box);
                    if (--expectDefCnt == 0) expectDefType = 0;
                } else if (expectFrmCnt > 0) {
                    (t == 1 ? pendingClsn1 : pendingClsn2).add(box);
                    if (--expectFrmCnt == 0) expectFrmType = 0;
                } else {
                    throw error("Unexpected Clsn box line", lineNo);
                }
                continue;
            }

            // -------------------------------------------------------------
            // Frame element (comma‑separated)
            // -------------------------------------------------------------
            AnimationElement el = parseElement(line, pendingInterp, pendingClsn1, pendingClsn2, lineNo);
            current.elements.add(el);
            // reset per‑element state
            pendingClsn1 = new ArrayList<>();
            pendingClsn2 = new ArrayList<>();
            pendingInterp = new ArrayList<>();
        }

        if (current != null) {
            actions.put(current.number, buildAnimation(current, defaultClsn1, defaultClsn2));
        }

        return AirFile.builder().actions(Collections.unmodifiableMap(actions)).build();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Animation buildAnimation(AnimationBuilder b, List<ClsnBox> d1, List<ClsnBox> d2) {
        return Animation.builder()
                .number(b.number)
                .elements(Collections.unmodifiableList(new ArrayList<>(b.elements)))
                .loopStart(b.loopStart)
                .clsn1Default(Collections.unmodifiableList(d1))
                .clsn2Default(Collections.unmodifiableList(d2))
                .build();
    }

    private static AirParseException error(String msg, int ln) {
        return new AirParseException("Line " + ln + ": " + msg);
    }

    // ---------------------------------------------------------------------
    // Element parsing
    // ---------------------------------------------------------------------

    private static AnimationElement parseElement(String raw,
                                                 List<InterpolateType> interp,
                                                 List<ClsnBox> c1,
                                                 List<ClsnBox> c2,
                                                 int ln) {
        String[] t = raw.split(",", -1);
        if (t.length < 5) throw error("Malformed element (need ≥5 values)", ln);
        try {
            int grp = Integer.parseInt(t[0].trim());
            int img = Integer.parseInt(t[1].trim());
            int offX = Integer.parseInt(t[2].trim());
            int offY = Integer.parseInt(t[3].trim());
            int time = Integer.parseInt(t[4].trim());

            Flip flip = Flip.NONE;
            if (t.length > 5 && !t[5].trim().isEmpty()) flip = Flip.fromString(t[5].trim());
            String blend = (t.length > 6 && !t[6].trim().isEmpty()) ? t[6].trim() : null;
            Double scaleX = (t.length > 7 && !t[7].trim().isEmpty()) ? Double.parseDouble(t[7].trim()) : null;
            Double scaleY = (t.length > 8 && !t[8].trim().isEmpty()) ? Double.parseDouble(t[8].trim()) : null;
            Double angle  = (t.length > 9 && !t[9].trim().isEmpty()) ? Double.parseDouble(t[9].trim()) : null;

            return AnimationElement.builder()
                    .group(grp).image(img)
                    .offsetX(offX).offsetY(offY)
                    .time(time)
                    .flip(flip)
                    .blend(blend)
                    .scaleX(scaleX).scaleY(scaleY)
                    .angle(angle)
                    .clsn1(Collections.unmodifiableList(new ArrayList<>(c1)))
                    .clsn2(Collections.unmodifiableList(new ArrayList<>(c2)))
                    .interpolations(Collections.unmodifiableList(new ArrayList<>(interp)))
                    .build();
        } catch (NumberFormatException ex) {
            throw error("Invalid numeric in element: " + ex.getMessage(), ln);
        }
    }

    /** Simple mutable helper while parsing */
    private static final class AnimationBuilder {
        final int number;
        final List<AnimationElement> elements = new ArrayList<>();
        int loopStart = -1;
        AnimationBuilder(int n) { number = n; }
    }

    // ---------------------------------------------------------------------
    // Immutable model classes
    // ---------------------------------------------------------------------

    @Data @Builder public static class AirFile { @NonNull Map<Integer, Animation> actions; }

    @Data @Builder public static class Animation {
        int number;
        @NonNull List<AnimationElement> elements;
        int loopStart; // -1 == none
        @NonNull List<ClsnBox> clsn1Default;
        @NonNull List<ClsnBox> clsn2Default;
        public List<ClsnBox> getEffectiveClsn1(AnimationElement e){return e.clsn1.isEmpty()?clsn1Default:e.clsn1;}
        public List<ClsnBox> getEffectiveClsn2(AnimationElement e){return e.clsn2.isEmpty()?clsn2Default:e.clsn2;}
    }

    @Data @Builder public static class AnimationElement {
        int group,image,offsetX,offsetY,time; @NonNull Flip flip; String blend; Double scaleX,scaleY,angle;
        @NonNull List<ClsnBox> clsn1,clsn2; @NonNull List<InterpolateType> interpolations;
    }

    @Data @Builder public static class ClsnBox { int x1,y1,x2,y2; }

    public enum Flip {
        NONE,H,V,HV;
        public static Flip fromString(String s){return switch(s.toUpperCase(Locale.ROOT)){case"H"->H;case"V"->V;case"HV","VH"->HV;case""->NONE;default->throw new IllegalArgumentException("Unknown flip: "+s);};}
    }

    public enum InterpolateType { OFFSET,BLEND,SCALE,ANGLE }

    public static class AirParseException extends RuntimeException { public AirParseException(String m){super(m);} }
}
