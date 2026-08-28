package dsk.gui.service;

import dsk.DiskImage;
import dsk.cpm.Catalog;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveServiceTest {

    @Test
    void removesTheTargetFileFromTheOutputCatalog(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("source.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "KEEP.TXT", "a".getBytes(),
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "DROP.TXT", "b".getBytes(),
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        disk.writeToFile(dskPath);
        Path outPath = tempDir.resolve("out.dsk");

        new RemoveService().remove(dskPath, "DROP.TXT", outPath);

        DiskImage outDisk = DiskImage.read(outPath);
        Catalog catalog = Catalog.read(outDisk, DiskFormat.DATA);
        assertTrue(catalog.filesByName().containsKey("KEEP.TXT"));
        assertFalse(catalog.filesByName().containsKey("DROP.TXT"));
    }

    // Invariant central du design GUI (cf. MainController.onRemove) : le disque source reste la vue
    // courante, l'opération ne produit qu'une copie - jamais d'écriture en place sur dskPath.
    @Test
    void sourceDskFileIsNeverModified(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("source.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "ONLY.TXT", "a".getBytes(),
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        disk.writeToFile(dskPath);
        Path outPath = tempDir.resolve("out.dsk");
        byte[] sourceBefore = Files.readAllBytes(dskPath);

        new RemoveService().remove(dskPath, "ONLY.TXT", outPath);

        byte[] sourceAfter = Files.readAllBytes(dskPath);
        assertArrayEquals(sourceBefore, sourceAfter);
    }

    @Test
    void throwsWhenTargetNameDoesNotExist(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("source.dsk");
        DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId).writeToFile(dskPath);
        Path outPath = tempDir.resolve("out.dsk");

        assertThrows(IOException.class, () -> new RemoveService().remove(dskPath, "MISSING.TXT", outPath));
    }
}
