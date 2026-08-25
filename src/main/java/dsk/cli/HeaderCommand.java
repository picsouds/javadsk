package dsk.cli;

import dsk.amsdos.AmsdosHeader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;

@Command(name = "header", mixinStandardHelpOptions = true,
        description = "Affiche le détail du header AMSDOS (128 octets) d'un fichier, s'il en a un")
public class HeaderCommand extends AbstractTargetFileCommand {

    @Parameters(index = "1", paramLabel = "FICHIER_CIBLE",
            description = "Nom du fichier dans le catalogue (tel qu'affiché par 'list', ex: PROG.BAS)")
    String targetFile;

    @Override
    String targetFileName() {
        return targetFile;
    }

    @Override
    int run(PrintWriter out, byte[] raw) throws IOException {
        AmsdosHeader header = AmsdosHeader.parse(raw);

        if (!header.isValid()) {
            out.println("Pas de header AMSDOS valide (checksum 0x" + Integer.toHexString(header.checksum)
                    + " != calculé 0x" + Integer.toHexString(header.computedChecksum) + ")");
            out.flush();
            return 0;
        }

        String fmt = "%-20s: %s%n";
        out.printf(fmt, "Nom", header.filename + "." + header.extension);
        out.printf(fmt, "User", header.userNumber);
        out.printf(fmt, "Type", header.fileTypeLabel() + " (" + header.fileType + ")");
        out.printf(fmt, "Bloc", header.blockNumber + " (dernier : " + header.lastBlock + ")");
        out.printf(fmt, "Indicateur 1er bloc", String.format("0x%02X", header.firstBlockFlag));
        out.printf(fmt, "Longueur logique", header.logicalLength + " octets");
        out.printf(fmt, "Longueur data", header.dataLength
                + " octets (champ 0x13-0x14, non fiable sur les headers réels - souvent laissé à 0)");
        out.printf(fmt, "Longueur réelle", header.realLength24 + " octets (24 bits)");
        out.printf(fmt, "Adresse chargement", String.format("0x%04X", header.loadAddress));
        out.printf(fmt, "Adresse exécution", String.format("0x%04X", header.entryAddress));
        out.printf(fmt, "Checksum", String.format("0x%04X (valide)", header.checksum));
        out.flush();
        return 0;
    }
}
