package dsk.cli;

import dsk.DiskImage;
import dsk.amsdos.AmsdosHeader;
import dsk.cpm.Catalog;
import dsk.cpm.CatalogEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "extract", mixinStandardHelpOptions = true, description = "Extrait tous les fichiers d'une image .dsk / .edsk")
public class ExtractCommand extends AbstractReadCommand {

    @Parameters(index = "1", paramLabel = "DOSSIER_SORTIE", description = "Dossier où écrire les fichiers extraits")
    Path outDir;

    @Parameters(index = "2", paramLabel = "FORMAT", arity = "0..1", defaultValue = "data",
            description = "Format CP/M : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE})")
    CpmFormat format;

    @Option(names = {"--raw", "--keep-header"},
            description = "Ne pas retirer le header AMSDOS : écrit les blocs bruts tels que lus sur le "
                    + "disque (comme iDSK -g), au lieu de la charge utile nettoyée (comportement par défaut).")
    boolean keepHeader;

    @Option(names = {"-f", "--file"}, paramLabel = "NOM",
            description = "N'extraire que ce fichier (nom tel qu'affiché par 'list', ex: PAWN.BAS). "
                    + "Par défaut, tout le catalogue est extrait.")
    String onlyFile;

    @Override
    int run(PrintWriter out, DiskImage disk) throws IOException {
        out.println(disk);

        Files.createDirectories(outDir);
        Catalog catalog = Catalog.read(disk, format.diskFormat);
        Map<String, List<CatalogEntry>> files = catalog.filesByName();

        if (onlyFile != null) {
            String match = files.keySet().stream()
                    .filter(name -> name.equalsIgnoreCase(onlyFile))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                fileNotFound(onlyFile);
                return 1;
            }
            files = Map.of(match, files.get(match));
        }

        for (Map.Entry<String, List<CatalogEntry>> e : files.entrySet()) {
            byte[] raw = catalog.extractRawData(e.getValue());
            AmsdosHeader header = AmsdosHeader.parse(raw);

            byte[] payload;
            String note;
            if (keepHeader) {
                payload = raw;
                note = header.isValid()
                        ? "brut, header AMSDOS conservé (" + header.fileTypeLabel() + ")"
                        : "brut, sans header AMSDOS détecté";
            } else if (header.isValid()) {
                payload = AmsdosHeader.payloadOf(raw);
                note = header.fileTypeLabel() + String.format(" load=0x%04X exec=0x%04X", header.loadAddress, header.entryAddress);
            } else {
                payload = raw; // pas de header détecté : on écrit tel quel (ASCII/BASIC non protégé, etc.)
                note = "sans header AMSDOS";
            }

            Path outFile = outDir.resolve(e.getKey().replace(' ', '_'));
            Files.write(outFile, payload);
            out.println("Extrait : " + e.getKey() + " -> " + outFile + " (" + payload.length + " octets, " + note + ")");
        }
        return 0;
    }
}
