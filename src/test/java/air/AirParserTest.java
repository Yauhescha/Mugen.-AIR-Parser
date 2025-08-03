package air;

import java.io.IOException;
import java.nio.file.Path;

public class AirParserTest {
    public static void main(String[] args) throws IOException {
        testAir();
    }

    private static void testAir() throws IOException {
        AirParser.AirFile air = AirParser.parse(Path.of("src/main/resources/kfm.air"));
        System.out.println(air.getActions().size());
        assert air.getActions().size() == 117;
        AirParser.Animation animation = air.getActions().get(0);
        assert animation.getNumber() == 0;
        assert animation.getElements().size() == 11;
        assert animation.getClsn2Default().size() == 2;
    }
}
