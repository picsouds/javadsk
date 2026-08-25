package dsk.cli;

import dsk.DiskImage;
import dsk.cpm.CatalogWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

@Command(name = "remove", mixinStandardHelpOptions = true,
        description = "Supprime un fichier du catalogue d'une image .dsk/.edsk (comme iDSK -r)")
public class RemoveCommand extends AbstractDskCommand {

    @Parameters(index = "0", paramLabel = "FICHIER", description = "Image .dsk / .edsk à modifier (pas d'archive .7z/.zip)")
    Path dskPath;

    @Parameters(index = "1", paramLabel = "NOM_CIBLE", description = "Nom du fichier à supprimer du catalogue (ex: PAWN.BAS)")
    String targetName;

    @Parameters(index = "2", paramLabel = "FORMAT", arity = "0..1", defaultValue = "data",
            description = "Format CP/M : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE})")
    CpmFormat format;

    @Option(names = {"--out"}, paramLabel = "SORTIE", required = true,
            description = "Fichier .dsk de sortie (jamais écrasé en place, toujours explicite)")
    Path out;

    @Override
    int run(PrintWriter writer) throws IOException {

        DiskImage disk = DiskImage.read(dskPath);
        CatalogWriter.removeFile(disk, format.diskFormat, targetName);
        disk.writeToFile(out);

        writer.println("Supprimé : " + targetName + " -> " + out);
        return 0;
    }
}
