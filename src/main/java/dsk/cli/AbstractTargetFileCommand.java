package dsk.cli;

import dsk.DiskImage;
import dsk.cpm.Catalog;
import dsk.cpm.CatalogEntry;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Base commune aux sous-commandes qui lisent un seul fichier nommé du catalogue
 * (ascii/basic/hex/header) : format CP/M, recherche insensible à la casse, message d'erreur si
 * absent, puis extraction des octets bruts du fichier trouvé.
 */
abstract class AbstractTargetFileCommand extends AbstractReadCommand {

    @Parameters(index = "2", paramLabel = "FORMAT", arity = "0..1", defaultValue = "data",
            description = "Format CP/M : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE})")
    CpmFormat format;

    /** Nom du fichier ciblé (index "1"), tel que déclaré avec son propre libellé par chaque sous-commande. */
    abstract String targetFileName();

    @Override
    final int run(PrintWriter out, DiskImage disk) throws IOException {
        Catalog catalog = Catalog.read(disk, format.diskFormat);
        Map<String, List<CatalogEntry>> files = catalog.filesByName();

        String name = targetFileName();
        String match = files.keySet().stream()
                .filter(f -> f.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (match == null) {
            fileNotFound(name);
            return 1;
        }

        return run(out, catalog.extractRawData(files.get(match)));
    }

    abstract int run(PrintWriter out, byte[] raw) throws IOException;
}
