package def;

import java.io.IOException;
import java.nio.file.Path;

public class DefParserTest {
    public static void main(String[] args) throws IOException {
        testDef();
    }

    private static void testDef() throws IOException {
        DefParser.DefFile def = DefParser.parse(Path.of("src/main/resources/kfm.def"));
        String author = def.first("Info")
                .map(sec -> sec.getFirst("author"))
                .orElse("unknown");

        assert def.getSections().size() == 4;
        System.out.println(author);
    }
}
