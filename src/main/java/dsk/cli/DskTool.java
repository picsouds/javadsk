package dsk.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "dsktool", mixinStandardHelpOptions = true, version = "javadsk 1.0.0",
        description = "Lit/extrait des images disquette Amstrad CPC (.dsk / EDSK).",
        subcommands = {ListCommand.class, ExtractCommand.class, BasicCommand.class, AsciiCommand.class,
                HexCommand.class, HeaderCommand.class, PutCommand.class, RemoveCommand.class, NewCommand.class})
public class DskTool implements Runnable {

    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new DskTool());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setExecutionExceptionHandler((ex, cl, parseResult) -> {
            cl.getErr().println("Erreur : " + ex.getMessage());
            return 1;
        });
        System.exit(cmd.execute(args));
    }

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
