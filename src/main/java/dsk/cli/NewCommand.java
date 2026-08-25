package dsk.cli;

import dsk.DiskImage;
import dsk.cpm.DiskFormat;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

@Command(name = "new", mixinStandardHelpOptions = true,
        description = "Crée une image .dsk vierge, formatée (comme iDSK -n)")
public class NewCommand extends AbstractDskCommand {

    @Parameters(index = "0", paramLabel = "FICHIER", description = "Fichier .dsk à créer")
    Path dskPath;

    @Parameters(index = "1", paramLabel = "FORMAT", arity = "0..1", defaultValue = "data",
            description = "Format CP/M : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE})")
    CpmFormat format;

    @Option(names = {"--tracks"}, paramLabel = "N", defaultValue = "40",
            description = "Nombre de pistes (défaut : ${DEFAULT-VALUE}, standard disquette CPC simple face)")
    int tracks;

    @Override
    int run(PrintWriter writer) throws IOException {

        DiskFormat f = format.diskFormat;
        DiskImage disk = DiskImage.formatted("javadsk", tracks, f.sectorSize, f.sectorsPerTrack, f.firstSectorId);
        disk.writeToFile(dskPath);

        writer.println("Créé : " + dskPath + " (" + tracks + " pistes, format " + format + ")");
        return 0;
    }
}
