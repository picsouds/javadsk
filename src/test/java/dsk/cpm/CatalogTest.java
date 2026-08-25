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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Construit une image .dsk synthétique
 * contenant une entrée de catalogue pointant vers un bloc de données avec un
 * header AMSDOS valide, et vérifie que Catalog reconstruit correctement le fichier.
 */
class CatalogTest {

    private static final int BLOCK_SIZE = DiskFormat.DATA.blockSize; // 1024

    @Test
    void extractsFileWithValidAmsdosHeader() throws IOException {
        byte[] payload = "HELLO WORLD".getBytes(StandardCharsets.US_ASCII);
        byte[] header = AmsdosHeaderBuilder.build("TEST", "BIN", AmsdosHeader.TYPE_BINARY,
                0x4000, 0x4000, payload.length);

        int dataBlock = 2; // 1er bloc libre après le catalogue (blocs 0 et 1)
        int recordCount = (int) Math.ceil((header.length + payload.length) / 128.0);

        byte[] catalogEntry = catalogEntryBytes(recordCount, dataBlock);

        byte[] fileBytes = new byte[header.length + payload.length];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        System.arraycopy(payload, 0, fileBytes, header.length, payload.length);

        byte[] raw = new DskImageBuilder()
                .writeAt(0, catalogEntry)                 // entrée 0 du catalogue
                .writeAt(dataBlock * BLOCK_SIZE, fileBytes) // données du fichier
                .build();

        DiskImage disk = DiskImage.parse(raw);
        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);

        assertEquals(1, catalog.entries.size());
        Map<String, List<CatalogEntry>> files = catalog.filesByName();
        assertTrue(files.containsKey("TEST.BIN"));

        byte[] rawFile = catalog.extractRawData(files.get("TEST.BIN"));
        AmsdosHeader parsedHeader = AmsdosHeader.parse(rawFile);
        assertTrue(parsedHeader.isValid());
        assertEquals(payload.length, parsedHeader.logicalLength);
        assertEquals(0x4000, parsedHeader.loadAddress);
        assertEquals(0x4000, parsedHeader.entryAddress);

        byte[] extractedPayload = new byte[parsedHeader.logicalLength];
        System.arraycopy(rawFile, AmsdosHeader.HEADER_SIZE, extractedPayload, 0, extractedPayload.length);
        assertArrayEquals(payload, extractedPayload);
    }

    @Test
    void trailingGarbageBlockSlotBeyondRecordCountIsIgnored() throws IOException {
        byte[] realBlock = new byte[BLOCK_SIZE];
        java.util.Arrays.fill(realBlock, (byte) 0xAA);
        byte[] garbageBlock = new byte[BLOCK_SIZE];
        java.util.Arrays.fill(garbageBlock, (byte) 0xBB);

        // recordCount=1 (< 8 records/bloc) -> un seul bloc est nécessaire ; le slot suivant,
        // pourtant non nul, doit être ignoré (c'est exactement ce qui faisait planter l'ancienne
        // logique basée sur un compteur de records décrémenté au fil des 16 emplacements).
        byte[] catalogEntry = catalogEntryBytes(1, new int[]{2, 3});

        byte[] raw = new DskImageBuilder()
                .writeAt(0, catalogEntry)
                .writeAt(2 * BLOCK_SIZE, realBlock)
                .writeAt(3 * BLOCK_SIZE, garbageBlock)
                .build();

        DiskImage disk = DiskImage.parse(raw);
        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);

        byte[] rawFile = catalog.extractRawData(catalog.filesByName().get("TEST.BIN"));

        assertEquals(BLOCK_SIZE, rawFile.length);
        assertArrayEquals(realBlock, rawFile);
    }

    @Test
    void outOfRangeBlockReferenceIsSkippedInsteadOfThrowing() throws IOException {
        int recordsPerBlock = BLOCK_SIZE / 128;
        // 200 : numéro de bloc représentable sur un octet (0-255, cf. format DPB) mais
        // largement hors de la petite image synthétique utilisée ici (quelques Ko).
        byte[] catalogEntry = catalogEntryBytes(recordsPerBlock, new int[]{200});

        byte[] raw = new DskImageBuilder()
                .writeAt(0, catalogEntry)
                .build();

        DiskImage disk = DiskImage.parse(raw);
        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);

        byte[] rawFile = assertDoesNotThrow(
                () -> catalog.extractRawData(catalog.filesByName().get("TEST.BIN")));

        assertEquals(0, rawFile.length);
    }

    @Test
    void entryWithOutOfRangeRecordCountIsExcludedFromCatalog() throws IOException {
        byte[] almostDeletedEntry = new byte[32];
        java.util.Arrays.fill(almostDeletedEntry, (byte) 0xE5);
        almostDeletedEntry[0] = 0; // user number valide, contrairement au reste du motif d'effacement

        byte[] raw = new DskImageBuilder()
                .writeAt(0, almostDeletedEntry)
                .build();

        DiskImage disk = DiskImage.parse(raw);
        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);

        assertEquals(0, catalog.entries.size());
    }

    @Test
    void deletedEntriesAreExcludedFromCatalog() throws IOException {
        byte[] deletedEntry = new byte[32];
        deletedEntry[0] = (byte) CatalogEntry.DELETED_USER;

        byte[] raw = new DskImageBuilder()
                .writeAt(0, deletedEntry)
                .build();

        DiskImage disk = DiskImage.parse(raw);
        Catalog catalog = Catalog.read(disk, DiskFormat.DATA);

        assertEquals(0, catalog.entries.size());
    }

    private static byte[] catalogEntryBytes(int recordCount, int block) {
        return catalogEntryBytes(recordCount, new int[]{block});
    }

    private static byte[] catalogEntryBytes(int recordCount, int[] blocks) {
        byte[] e = new byte[32];
        e[0] = 0; // user number
        System.arraycopy(pad("TEST", 8).getBytes(StandardCharsets.US_ASCII), 0, e, 1, 8);
        System.arraycopy(pad("BIN", 3).getBytes(StandardCharsets.US_ASCII), 0, e, 9, 3);
        e[0x0C] = 0; // extent low
        e[0x0E] = 0; // extent high
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
