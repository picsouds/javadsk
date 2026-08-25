package dsk.cli;

import dsk.amsdos.AmsdosHeader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;

@Command(name = "ascii", mixinStandardHelpOptions = true,
        description = "Affiche le contenu brut d'un fichier texte/ASCII sur la sortie standard (comme iDSK -a)")
public class AsciiCommand extends AbstractTargetFileCommand {

    @Parameters(index = "1", paramLabel = "FICHIER_ASCII",
            description = "Nom du fichier dans le catalogue (tel qu'affiché par 'list', ex: README.TXT)")
    String asciiFile;

    @Override
    String targetFileName() {
        return asciiFile;
    }

    @Override
    int run(PrintWriter out, byte[] raw) throws IOException {
        byte[] payload = AmsdosHeader.payloadOf(raw);

        // Octets bruts, pas la PrintWriter de picocli (réencode en UTF-8 : un octet CPC >0x7F
        // devenait 2 octets, vérifié réel 0x81 -> 0xC2 0x81). Comme le strncpy brut d'iDSK -a.
        out.flush();
        System.out.write(payload);
        System.out.flush();
        return 0;
    }
}
