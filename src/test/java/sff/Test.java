package sff;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class Test {
    public static void main(String[] args) throws IOException {
        testSffVersion_1_0_1();
//        SffFile file = SffParser.parse(Path.of("src/main/resources/kfm.sff"));
        SffParser.SffFile file = SffParser.parse(Path.of("src/main/resources/sff/2.0.0/zz.sff"));
    }

    private static void testSffVersion_1_0_1() throws IOException {
        SffParser.SffFile file = SffParser.parse(Path.of("src/main/resources/sff/1.0.1/DNaruto.sff"));
        System.out.println(file.getImages());

        for (int i = 0; i < 5; i++) {
            SffParser.Sprite sprite = file.getSprites().get(i);
            BufferedImage img = SffParser.toImage(file, sprite);
            new Shower(img).show();
        }

        System.out.println(file.getSprites().getFirst().getData());
    }

}
