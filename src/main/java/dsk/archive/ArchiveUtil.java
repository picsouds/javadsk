package dsk.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/** Logique partagée entre {@link ZipDsk} et {@link SevenZipDsk}. */
final class ArchiveUtil {

    private ArchiveUtil() {
    }

    @FunctionalInterface
    interface EntryExtractor {
        byte[] extract(Path archive, String entryName) throws IOException;
    }

    static byte[] readFully(InputStream in, long sizeHint) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) sizeHint);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** @throws IOException si 0 ou plusieurs images (le message liste alors les entrées du fichier). */
    static byte[] extractSingle(Path archive, List<String> candidates, EntryExtractor extractor) throws IOException {
        if (candidates.isEmpty()) {
            throw new IOException("Aucune image .dsk/.edsk trouvée dans l'archive : " + archive);
        }
        if (candidates.size() > 1) {
            StringBuilder message = new StringBuilder(
                    "Plusieurs images .dsk/.edsk dans l'archive, précisez --entry NOM parmi :");
            for (String candidate : candidates) {
                message.append(System.lineSeparator()).append("  - ").append(candidate);
            }
            throw new IOException(message.toString());
        }
        return extractor.extract(archive, candidates.get(0));
    }
}
