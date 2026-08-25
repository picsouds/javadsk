package dsk.cli;

import dsk.DiskImage;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

/** Base commune aux sous-commandes en lecture seule : image/archive source + option --entry. */
abstract class AbstractReadCommand extends AbstractDskCommand {

    @Parameters(index = "0", paramLabel = "FICHIER", description = "Image .dsk / .edsk à lire, ou archive .7z/.zip la contenant")
    Path dskPath;

    @Option(names = {"--entry"}, paramLabel = "NOM",
            description = "Si FICHIER est une archive .7z/.zip contenant plusieurs images .dsk/.edsk, "
                    + "précise celle à lire (nom tel qu'affiché dans l'archive).")
    String entry;

    @Override
    final int run(PrintWriter out) throws IOException {
        return run(out, DskLoader.load(dskPath, entry));
    }

    abstract int run(PrintWriter out, DiskImage disk) throws IOException;
}
