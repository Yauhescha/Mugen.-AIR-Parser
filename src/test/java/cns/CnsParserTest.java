package cns;

import java.io.IOException;
import java.nio.file.Path;

public class CnsParserTest {
    public static void main(String[] args) throws IOException {
        testCns();
    }

    private static void testCns() throws IOException {
        CnsParser.CnsFile cns = CnsParser.parse(Path.of("src/main/resources/kfm.cns"));
        System.out.println(cns.getSections().size());
        assert cns.getSections().size() == 382;
        CnsParser.Section section = cns.getSections().get(0);
        assert section.getRawName().equals("Data");
        assert section.getTokens().size() == 1;
        assert section.getEntries().size() == 12;
        assert section.getEntries().get(0).getKey().equals("life");
        assert section.getEntries().get(0).getValue().equals("1000");
    }
}
