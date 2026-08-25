package dsk.archive;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extraction d'images .dsk / .edsk contenues dans une archive .7z (lecture pure Java via
 * Apache Commons Compress, sans dépendre du binaire 7z du système).
 */
public final class SevenZipDsk {

    private SevenZipDsk() {
    }

    public static boolean isSevenZip(Path path) {
        return path.toString().toLowerCase(Locale.ROOT).endsWith(".7z");
    }

    /** Liste les entrées de l'archive dont le nom se termine par .dsk ou .edsk. */
    public static List<String> listDskEntries(Path archive) throws IOException {
        List<String> names = new ArrayList<>();
        try (SevenZFile sevenZFile = SevenZFile.builder().setFile(archive.toFile()).get()) {
            for (SevenZArchiveEntry entry : sevenZFile.getEntries()) {
                if (entry.isDirectory()) continue;
                String lower = entry.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".dsk") || lower.endsWith(".edsk")) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }

    public static byte[] extract(Path archive, String entryName) throws IOException {
        try (SevenZFile sevenZFile = SevenZFile.builder().setFile(archive.toFile()).get()) {
            for (SevenZArchiveEntry entry : sevenZFile.getEntries()) {
                if (entry.isDirectory() || !entry.getName().equals(entryName)) continue;
                try (InputStream in = sevenZFile.getInputStream(entry)) {
                    return ArchiveUtil.readFully(in, entry.getSize());
                }
            }
        }
        throw new IOException("Entrée introuvable dans l'archive : " + entryName);
    }

    public static byte[] extractSingle(Path archive) throws IOException {
        return ArchiveUtil.extractSingle(archive, listDskEntries(archive), SevenZipDsk::extract);
    }
}
