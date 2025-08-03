package cmd;

import java.io.IOException;
import java.nio.file.Path;

public class CmdParserTest {
    public static void main(String[] args) throws IOException {
        testCmd();
    }

    private static void testCmd() throws IOException {
        CmdParser.CmdFile cmd = CmdParser.parse(Path.of("src/main/resources/kfm.cmd"));

        CmdParser.CommandDef punch = cmd.getCommand("\"a\"");
        System.out.println(punch);   // => ~F, a

        // доступ к триггерам из Statedef -1
        cmd.firstSection("State -1")
                .ifPresent(sec -> System.out.println(sec.getAll("trigger1")));

        assert cmd.getSections().size() == 78;
        assert cmd.getCommands().size() == 37;
    }
}
