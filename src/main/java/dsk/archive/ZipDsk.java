package dsk.archive;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extraction d'images .dsk / .edsk contenues dans une archive .zip
 */
public final class ZipDsk {

    private ZipDsk() {
    }

    public static boolean isZip(Path path) {
        return path.toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public static List<String> listDskEntries(Path archive) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zipFile = ZipFile.builder().setFile(archive.toFile()).get()) {
            for (ZipArchiveEntry entry : java.util.Collections.list(zipFile.getEntries())) {
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
        try (ZipFile zipFile = ZipFile.builder().setFile(archive.toFile()).get()) {
            ZipArchiveEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Entrée introuvable dans l'archive : " + entryName);
            }
            try (InputStream in = zipFile.getInputStream(entry)) {
                return ArchiveUtil.readFully(in, entry.getSize());
            }
        }
    }

    public static byte[] extractSingle(Path archive) throws IOException {
        return ArchiveUtil.extractSingle(archive, listDskEntries(archive), ZipDsk::extract);
    }
}
