package dsk.gui.service;

import dsk.DiskImage;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;

import java.io.IOException;
import java.nio.file.Path;

public final class RemoveService {

    @SuppressWarnings("PathTraversal")
    public void remove(Path dskPath, String targetName, Path outPath) throws IOException {
        DiskImage disk = DiskImage.read(dskPath);
        CatalogWriter.removeFile(disk, DiskFormat.DATA, targetName);
        disk.writeToFile(outPath);
    }
}
