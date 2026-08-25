package dsk.cli;

import dsk.amsdos.AmsdosHeader;
import dsk.hex.HexDump;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;

@Command(name = "hex", mixinStandardHelpOptions = true,
        description = "Affiche un fichier en hexadécimal + ASCII (comme iDSK -h)")
public class HexCommand extends AbstractTargetFileCommand {

    @Parameters(index = "1", paramLabel = "FICHIER_CIBLE",
            description = "Nom du fichier dans le catalogue (tel qu'affiché par 'list', ex: PROG.BIN)")
    String targetFile;

    @Override
    String targetFileName() {
        return targetFile;
    }

    @Override
    int run(PrintWriter out, byte[] raw) throws IOException {
        byte[] payload = AmsdosHeader.payloadOf(raw);

        out.print(HexDump.dump(payload));
        out.flush();
        return 0;
    }
}
