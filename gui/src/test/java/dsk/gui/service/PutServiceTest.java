package dsk.gui.service;

import dsk.DiskImage;
import dsk.amsdos.AmsdosHeader;
import dsk.cpm.Catalog;
import dsk.cpm.CatalogEntry;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PutServiceTest {

    private static Path blankDsk(Path dir) throws IOException {
        Path dskPath = dir.resolve("source.dsk");
        DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId).writeToFile(dskPath);
        return dskPath;
    }

    @Test
    void createsNewFileAndReportsCreatedTrue(@TempDir Path tempDir) throws IOException {
        Path dskPath = blankDsk(tempDir);
        Path localFile = tempDir.resolve("local.txt");
        Files.writeString(localFile, "hello world");
        Path outPath = tempDir.resolve("out.dsk");

        PutRequest request = new PutRequest(localFile, "HELLO.TXT", CatalogWriter.TYPE_ASCII, false,
                null, null, null, null, outPath);
        PutService.Result result = new PutService().put(dskPath, request);

        assertTrue(result.created());
        assertEquals("hello world".length(), result.contentLength());

        DiskImage outDisk = DiskImage.read(outPath);
        Catalog catalog = Catalog.read(outDisk, DiskFormat.DATA);
        assertTrue(catalog.filesByName().containsKey("HELLO.TXT"));
        // Ascii sans header AMSDOS : le contenu réel n'est qu'un préfixe du bloc alloué (1024 octets).
        byte[] raw = catalog.extractRawData(catalog.filesByName().get("HELLO.TXT"));
        assertArrayEquals("hello world".getBytes(), java.util.Arrays.copyOf(raw, "hello world".length()));
    }

    @Test
    void replacingExistingFileReportsCreatedFalse(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("source.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "HELLO.TXT", "old".getBytes(),
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        disk.writeToFile(dskPath);

        Path localFile = tempDir.resolve("local.txt");
        Files.writeString(localFile, "new content");
        Path outPath = tempDir.resolve("out.dsk");

        PutRequest request = new PutRequest(localFile, "HELLO.TXT", CatalogWriter.TYPE_ASCII, false,
                null, null, null, null, outPath);
        PutService.Result result = new PutService().put(dskPath, request);

        assertFalse(result.created());
        assertEquals("new content".length(), result.contentLength());
    }

    @Test
    void tokenizeRetokenizesSpacedBasicListingBeforeStoring(@TempDir Path tempDir) throws IOException {
        Path dskPath = blankDsk(tempDir);
        Path localFile = tempDir.resolve("listing.txt");
        Files.writeString(localFile, "10 PRINT \"HI\"\n", StandardCharsets.UTF_8);
        Path outPath = tempDir.resolve("out.dsk");

        PutRequest request = new PutRequest(localFile, "PROG.BAS", CatalogWriter.TYPE_BASIC, true,
                null, null, null, null, outPath);
        PutService.Result result = new PutService().put(dskPath, request);

        assertTrue(result.created());
        // Tokénisé : plus petit que le texte source, et un header AMSDOS Basic est ajouté.
        DiskImage outDisk = DiskImage.read(outPath);
        Catalog catalog = Catalog.read(outDisk, DiskFormat.DATA);
        byte[] raw = catalog.extractRawData(catalog.filesByName().get("PROG.BAS"));
        AmsdosHeader header = AmsdosHeader.parse(raw);
        assertTrue(header.isValid());
        assertEquals(AmsdosHeader.TYPE_BASIC, header.fileType);
    }

    @Test
    void loadAndExecAddressesAreAppliedToBinaryHeader(@TempDir Path tempDir) throws IOException {
        Path dskPath = blankDsk(tempDir);
        Path localFile = tempDir.resolve("prog.bin");
        Files.write(localFile, new byte[]{1, 2, 3, 4});
        Path outPath = tempDir.resolve("out.dsk");

        PutRequest request = new PutRequest(localFile, "PROG.BIN", CatalogWriter.TYPE_BINARY, false,
                0x4000, 0x4010, null, null, outPath);
        new PutService().put(dskPath, request);

        DiskImage outDisk = DiskImage.read(outPath);
        Catalog catalog = Catalog.read(outDisk, DiskFormat.DATA);
        AmsdosHeader header = AmsdosHeader.parse(catalog.extractRawData(catalog.filesByName().get("PROG.BIN")));
        assertEquals(0x4000, header.loadAddress);
        assertEquals(0x4010, header.entryAddress);
    }

    @Test
    void readOnlyAndHiddenFlagsAreAppliedToTheCatalogEntry(@TempDir Path tempDir) throws IOException {
        Path dskPath = blankDsk(tempDir);
        Path localFile = tempDir.resolve("secret.txt");
        Files.writeString(localFile, "secret");
        Path outPath = tempDir.resolve("out.dsk");

        PutRequest request = new PutRequest(localFile, "SECRET.TXT", CatalogWriter.TYPE_ASCII, false,
                null, null, true, true, outPath);
        new PutService().put(dskPath, request);

        DiskImage outDisk = DiskImage.read(outPath);
        Catalog catalog = Catalog.read(outDisk, DiskFormat.DATA);
        CatalogEntry entry = catalog.filesByName().get("SECRET.TXT").get(0);
        assertTrue(entry.readOnly);
        assertTrue(entry.system);
    }

    // Invariant central du design GUI (cf. MainController.onPut) : le disque source reste la vue
    // courante, l'opération ne produit qu'une copie
    @Test
    void sourceDskFileIsNeverModified(@TempDir Path tempDir) throws IOException {
        Path dskPath = blankDsk(tempDir);
        Path localFile = tempDir.resolve("local.txt");
        Files.writeString(localFile, "hello world");
        Path outPath = tempDir.resolve("out.dsk");
        byte[] sourceBefore = Files.readAllBytes(dskPath);

        PutRequest request = new PutRequest(localFile, "HELLO.TXT", CatalogWriter.TYPE_ASCII, false,
                null, null, null, null, outPath);
        new PutService().put(dskPath, request);

        byte[] sourceAfter = Files.readAllBytes(dskPath);
        assertArrayEquals(sourceBefore, sourceAfter);
    }
}
