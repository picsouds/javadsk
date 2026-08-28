package dsk.archive;

import dsk.DiskImage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Résout un chemin (image .dsk/.edsk directe, ou archive .7z/.zip la contenant) en {@link DiskImage}.
 * Partagé par la CLI (dsk.cli.AbstractReadCommand) et le GUI (dsk.gui.model.DiskSession).
 */
public final class DskLoader {

    private DskLoader() {
    }

    public static DiskImage load(Path path, String entry) throws IOException {
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
