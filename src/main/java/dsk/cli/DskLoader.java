package dsk.cli;

import dsk.DiskImage;
import dsk.archive.SevenZipDsk;
import dsk.archive.ZipDsk;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Résout un chemin passé en ligne de commande (image .dsk/.edsk directe, ou archive .7z/.zip la
 * contenant) en {@link DiskImage}.
 */
final class DskLoader {

    private DskLoader() {
    }

    static DiskImage load(Path path, String entry) throws IOException {
        if (SevenZipDsk.isSevenZip(path)) {
            byte[] raw = (entry != null) ? SevenZipDsk.extract(path, entry) : SevenZipDsk.extractSingle(path);
            return DiskImage.parse(raw);
        }
        if (ZipDsk.isZip(path)) {
            byte[] raw = (entry != null) ? ZipDsk.extract(path, entry) : ZipDsk.extractSingle(path);
            return DiskImage.parse(raw);
        }
        return DiskImage.read(path);
    }
}
