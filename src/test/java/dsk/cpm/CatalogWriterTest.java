package dsk.cpm;

import dsk.DiskImage;
import dsk.amsdos.AmsdosHeader;
import dsk.support.AmsdosHeaderBuilder;
import dsk.support.DskImageBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Construit une image .dsk synthétique, ajoute/remplace un fichier via {@link
 * CatalogWriter#putFile}, puis relit le catalogue pour vérifier le résultat.
 */
class CatalogWriterTest {

    private static final int BLOCK_SIZE = DiskFormat.DATA.blockSize;

    @Test
    void replacesFileWithLongerContentReusingHeaderMetadata() throws IOException {
        byte[] originalPayload = "HELLO".getBytes(StandardCharsets.US_ASCII);
        byte[] header = AmsdosHeaderBuilder.build("TEST", "BIN", AmsdosHeader.TYPE_BINARY,
                0x4000, 0x4000, originalPayload.length);
        byte[] originalFile = new byte[header.length + originalPayload.length];
        System.arraycopy(header, 0, originalFile, 0, header.length);
        System.arraycopy(originalPayload, 0, originalFile, header.length, originalPayload.length);

        byte[] catalogEntry = catalogEntryBytes("TEST", "BIN", 0,
                (int) Math.ceil(originalFile.length / 128.0), new int[]{2});

        byte[] raw = new DskImageBuilder()
                .writeAt(0, catalogEntry)
                .writeAt(2 * BLOCK_SIZE, originalFile)
                .build();
        DiskImage disk = DiskImage.parse(raw);

        byte[] newPayload = "HELLO WORLD, THIS IS LONGER THAN BEFORE".getBytes(StandardCharsets.US_ASCII);
        boolean created = CatalogWriter.putFile(disk, DiskFormat.DATA, "TEST.BIN", newPayload,
                null, null, null, null, null, null);
        assertFalse(created);

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        Map<String, List<CatalogEntry>> files = catalog.filesByName();
        assertTrue(files.containsKey("TEST.BIN"));

        byte[] rawFile = catalog.extractRawData(files.get("TEST.BIN"));
        AmsdosHeader parsedHeader = AmsdosHeader.parse(rawFile);
        assertTrue(parsedHeader.isValid());
        assertEquals(newPayload.length, parsedHeader.logicalLength);
        assertEquals(0x4000, parsedHeader.loadAddress);
        assertEquals(0x4000, parsedHeader.entryAddress);

        byte[] extractedPayload = new byte[parsedHeader.logicalLength];
        System.arraycopy(rawFile, AmsdosHeader.HEADER_SIZE, extractedPayload, 0, extractedPayload.length);
        assertArrayEquals(newPayload, extractedPayload);
    }

    @Test
    void explicitTypeOverrideOnExistingFileStillKeepsOriginalLoadAndExecAddresses() throws IOException {
        // Reproduit un bug réel : forcer --type (ex: après --tokenize, pour reconstruire le header
        // suite à une retokenisation) sans préciser --load/--exec ne doit PAS remettre load/exec à
        // 0 quand le fichier remplacé avait déjà un header valide - ils doivent rester ceux
        // d'origine, exactement comme quand --type n'est pas précisé du tout.
        byte[] originalPayload = "10 PRINT 1".getBytes(StandardCharsets.US_ASCII);
        byte[] header = AmsdosHeaderBuilder.build("PROG", "BAS", AmsdosHeader.TYPE_BASIC,
                0x0170, 0x0000, originalPayload.length);
        byte[] originalFile = new byte[header.length + originalPayload.length];
        System.arraycopy(header, 0, originalFile, 0, header.length);
        System.arraycopy(originalPayload, 0, originalFile, header.length, originalPayload.length);

        byte[] catalogEntry = catalogEntryBytes("PROG", "BAS", 0,
                (int) Math.ceil(originalFile.length / 128.0), new int[]{2});

        byte[] raw = new DskImageBuilder()
                .writeAt(0, catalogEntry)
                .writeAt(2 * BLOCK_SIZE, originalFile)
                .build();
        DiskImage disk = DiskImage.parse(raw);

        byte[] newPayload = "10 PRINT 2".getBytes(StandardCharsets.US_ASCII);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "PROG.BAS", newPayload,
                null, null, null, null, null, CatalogWriter.TYPE_BASIC);

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        byte[] rawFile = catalog.extractRawData(catalog.filesByName().get("PROG.BAS"));
        AmsdosHeader parsedHeader = AmsdosHeader.parse(rawFile);
        assertTrue(parsedHeader.isValid());
        assertEquals(0x0170, parsedHeader.loadAddress);
        assertEquals(0x0000, parsedHeader.entryAddress);
    }

    @Test
    void replacesFileWithoutHeaderKeepsContentRaw() throws IOException {
        byte[] originalContent = new byte[BLOCK_SIZE];
        System.arraycopy("PLAIN TEXT, NO AMSDOS HEADER".getBytes(StandardCharsets.US_ASCII), 0, originalContent, 0, 28);

        byte[] catalogEntry = catalogEntryBytes("PLAIN", "TXT", 0, BLOCK_SIZE / 128, new int[]{2});
        byte[] raw = new DskImageBuilder()
                .writeAt(0, catalogEntry)
                .writeAt(2 * BLOCK_SIZE, originalContent)
                .build();
        DiskImage disk = DiskImage.parse(raw);

        byte[] newContent = "REPLACED".getBytes(StandardCharsets.US_ASCII);
        boolean created = CatalogWriter.putFile(disk, DiskFormat.DATA, "PLAIN.TXT", newContent,
                null, null, null, null, null, null);
        assertFalse(created);

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        byte[] rawFile = catalog.extractRawData(catalog.filesByName().get("PLAIN.TXT"));
        assertFalse(AmsdosHeader.parse(rawFile).isValid());
        // Sans header AMSDOS, la longueur exacte n'est connue que du contenu applicatif : la
        // donnée brute reste arrondie au bloc CP/M (1024) le plus proche, complétée de zéros.
        assertEquals(BLOCK_SIZE, rawFile.length);
        assertArrayEquals(newContent, java.util.Arrays.copyOf(rawFile, newContent.length));
    }

    @Test
    void createsNewAsciiFileWhenTargetMissing() throws IOException {
        byte[] raw = new DskImageBuilder().writeAt(0, emptyCatalog()).build();
        DiskImage disk = DiskImage.parse(raw);

        byte[] content = "BRAND NEW FILE".getBytes(StandardCharsets.US_ASCII);
        boolean created = CatalogWriter.putFile(disk, DiskFormat.DATA, "NEW.TXT", content,
                null, null, null, null, null, null);
        assertTrue(created);

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        byte[] rawFile = catalog.extractRawData(catalog.filesByName().get("NEW.TXT"));
        assertFalse(AmsdosHeader.parse(rawFile).isValid());
        assertArrayEquals(content, java.util.Arrays.copyOf(rawFile, content.length));
    }

    @Test
    void createsNewBinaryFileWithExplicitTypeAndAddresses() throws IOException {
        byte[] raw = new DskImageBuilder().writeAt(0, emptyCatalog()).build();
        DiskImage disk = DiskImage.parse(raw);

        byte[] content = "BINARY CONTENT".getBytes(StandardCharsets.US_ASCII);
        boolean created = CatalogWriter.putFile(disk, DiskFormat.DATA, "NEW.BIN", content,
                7, true, true, 0x4000, 0x4010, CatalogWriter.TYPE_BINARY);
        assertTrue(created);

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        List<CatalogEntry> entries = catalog.filesByName().get("NEW.BIN");
        assertEquals(7, entries.get(0).userNumber);
        assertTrue(entries.get(0).readOnly);
        assertTrue(entries.get(0).system);

        byte[] rawFile = catalog.extractRawData(entries);
        AmsdosHeader header = AmsdosHeader.parse(rawFile);
        assertTrue(header.isValid());
        assertEquals(AmsdosHeader.TYPE_BINARY, header.fileType);
        assertEquals(0x4000, header.loadAddress);
        assertEquals(0x4010, header.entryAddress);
        assertEquals(content.length, header.logicalLength);

        byte[] extractedPayload = new byte[header.logicalLength];
        System.arraycopy(rawFile, AmsdosHeader.HEADER_SIZE, extractedPayload, 0, extractedPayload.length);
        assertArrayEquals(content, extractedPayload);
    }

    @Test
    void createsMultiExtentFileForContentLargerThanOneCpmExtent() throws IOException {
        // Un extent CP/M plafonne à 128 records (16384 octets) : au-delà, putFile doit créer une
        // 2e entrée de catalogue (extentLow=1). Jamais exercé par aucun autre test/fichier réel
        // rencontré jusqu'ici (linter : "extentLow est toujours 0") - vérifié explicitement ici.
        DiskImage disk = DiskImage.formatted("test", 40, 512, 9, 0xC1);
        byte[] content = new byte[20000];
        new java.util.Random(42).nextBytes(content);

        boolean created = CatalogWriter.putFile(disk, DiskFormat.DATA, "BIG.BIN", content,
                null, null, null, null, null, "ascii");
        assertTrue(created);

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        List<CatalogEntry> extents = catalog.filesByName().get("BIG.BIN");
        assertEquals(2, extents.size());
        assertEquals(0, extents.get(0).extentLow);
        assertEquals(1, extents.get(1).extentLow);

        byte[] readBack = catalog.extractRawData(extents);
        assertArrayEquals(content, java.util.Arrays.copyOf(readBack, content.length));
    }

    @Test
    void removesExistingFileLeavingOthersIntact() throws IOException {
        byte[] header = AmsdosHeaderBuilder.build("TEST", "BIN", AmsdosHeader.TYPE_BINARY, 0x4000, 0x4000, 5);
        byte[] file = new byte[header.length + 5];
        System.arraycopy(header, 0, file, 0, header.length);
        System.arraycopy("HELLO".getBytes(StandardCharsets.US_ASCII), 0, file, header.length, 5);

        byte[] entry0 = catalogEntryBytes("TEST", "BIN", 0, (int) Math.ceil(file.length / 128.0), new int[]{2});
        byte[] entry1 = catalogEntryBytes("OTHER", "TXT", 0, 1, new int[]{3});

        byte[] raw = new DskImageBuilder()
                .writeAt(0, entry0)
                .writeAt(32, entry1)
                .writeAt(2 * BLOCK_SIZE, file)
                .writeAt(3 * BLOCK_SIZE, "OTHER CONTENT".getBytes(StandardCharsets.US_ASCII))
                .build();
        DiskImage disk = DiskImage.parse(raw);

        CatalogWriter.removeFile(disk, DiskFormat.DATA, "TEST.BIN");

        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);
        Map<String, List<CatalogEntry>> files = catalog.filesByName();
        assertFalse(files.containsKey("TEST.BIN"));
        assertTrue(files.containsKey("OTHER.TXT"));
    }

    @Test
    void removeMissingFileThrows() throws IOException {
        byte[] raw = new DskImageBuilder().build();
        DiskImage disk = DiskImage.parse(raw);

        assertThrows(IOException.class, () -> CatalogWriter.removeFile(disk, DiskFormat.DATA, "NOPE.BIN"));
    }

    /** Catalogue "formaté" (64 entrées à 0xE5 = vide), comme un vrai disque après formatage. */
    private static byte[] emptyCatalog() {
        byte[] c = new byte[DiskFormat.DATA.directoryEntries * 32];
        java.util.Arrays.fill(c, (byte) CatalogEntry.DELETED_USER);
        return c;
    }

    private static byte[] catalogEntryBytes(String name, String ext, int extentLow, int recordCount, int[] blocks) {
        byte[] e = new byte[32];
        e[0] = 0;
        System.arraycopy(pad(name, 8).getBytes(StandardCharsets.US_ASCII), 0, e, 1, 8);
        System.arraycopy(pad(ext, 3).getBytes(StandardCharsets.US_ASCII), 0, e, 9, 3);
        e[0x0C] = (byte) extentLow;
        e[0x0E] = 0;
        e[0x0F] = (byte) recordCount;
        for (int i = 0; i < blocks.length && i < 16; i++) {
            e[0x10 + i] = (byte) blocks[i];
        }
        return e;
    }

    private static String pad(String s, int len) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.substring(0, len);
    }
}
