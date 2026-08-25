package dsk.cli;

import dsk.DiskImage;
import dsk.amsdos.AmsdosHeader;
import dsk.cpm.Catalog;
import dsk.cpm.CatalogEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@Command(name = "list", mixinStandardHelpOptions = true, description = "Liste le catalogue d'une image .dsk / .edsk")
public class ListCommand extends AbstractReadCommand {

    @Parameters(index = "1", paramLabel = "FORMAT", arity = "0..1", defaultValue = "data",
            description = "Format CP/M : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE})")
    CpmFormat format;

    @Override
    int run(PrintWriter out, DiskImage disk) throws IOException {
        out.println(disk);

        Catalog catalog = Catalog.read(disk, format.diskFormat);
        Map<String, List<CatalogEntry>> files = catalog.filesByName();
        out.printf("%-13s %-4s %-2s %-2s %6s  %-14s %-8s %-8s%n",
                "Fichier", "User", "RO", "H", "Taille", "Type", "Load", "Exec");
        for (Map.Entry<String, List<CatalogEntry>> e : files.entrySet()) {
            CatalogEntry firstExtent = e.getValue().get(0);
            byte[] raw = catalog.extractRawData(e.getValue());
            AmsdosHeader header = AmsdosHeader.parse(raw);
            String user = String.format("%02d", firstExtent.userNumber);
            String ro = firstExtent.readOnly ? "RO" : "";
            String hidden = firstExtent.system ? "H" : "";
            if (header.isValid()) {
                out.printf("%-13s %-4s %-2s %-2s %6d  %-14s 0x%04X   0x%04X%n",
                        e.getKey(), user, ro, hidden, header.logicalLength, header.fileTypeLabel(),
                        header.loadAddress, header.entryAddress);
            } else {
                out.printf("%-13s %-4s %-2s %-2s %6d  %-14s %-8s %-8s%n",
                        e.getKey(), user, ro, hidden, raw.length, "(sans header)", "-", "-");
            }
        }
        return 0;
    }
}
