package dsk.gui.model;

import dsk.DiskImage;
import dsk.archive.DskLoader;
import dsk.archive.SevenZipDsk;
import dsk.archive.ZipDsk;
import dsk.cpm.Catalog;
import dsk.cpm.CatalogEntry;
import dsk.cpm.DiskFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Le disque actuellement affiché dans le GUI : chemin, catalogue et index des fichiers par nom. */
public final class DiskSession {

    private DiskImage disk;
    private Catalog catalog;
    private Map<String, List<CatalogEntry>> files;
    private Path path;

    public void load(Path path) throws IOException {
        load(path, null);
    }

    /** @param entry nom de l'image dans l'archive .7z/.zip visée par path, ou null si ce n'en est pas une (ou une seule image dedans). */
    @SuppressWarnings("PathTraversal")
    public void load(Path path, String entry) throws IOException {
        DiskImage loadedDisk = DskLoader.load(path, entry);
        Catalog loadedCatalog = Catalog.read(loadedDisk, DiskFormat.DATA);
        this.disk = loadedDisk;
        this.catalog = loadedCatalog;
        this.files = loadedCatalog.filesByName();
        this.path = path;
    }

    /** @return la liste des images .dsk/.edsk de l'archive visée par path, seulement si elle en contient plusieurs. */
    public static List<String> listArchiveEntriesIfAmbiguous(Path path) throws IOException {
        List<String> entries;
        if (SevenZipDsk.isSevenZip(path)) {
            entries = SevenZipDsk.listDskEntries(path);
        } else if (ZipDsk.isZip(path)) {
            entries = ZipDsk.listDskEntries(path);
        } else {
            return Collections.emptyList();
        }
        return entries.size() > 1 ? entries : Collections.emptyList();
    }

    /** put/remove ne savent réécrire qu'un fichier .dsk/.edsk direct, jamais une archive (comme la CLI). */
    public boolean isFromArchive() {
        return path != null && (ZipDsk.isZip(path) || SevenZipDsk.isSevenZip(path));
    }

    public boolean isLoaded() {
        return path != null;
    }

    public Path getPath() {
        return path;
    }

    public DiskImage getDisk() {
        return disk;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    public Map<String, List<CatalogEntry>> getFiles() {
        return files;
    }

    public byte[] rawDataOf(String name) {
        return catalog.extractRawData(files.get(name));
    }
}
