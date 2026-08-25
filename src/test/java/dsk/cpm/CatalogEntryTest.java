package dsk.cpm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogEntryTest {

    private static byte[] entryBytes(int userNumber, String filename, String extension,
                                      boolean readOnly, boolean system, int extentLow, int extentHigh,
                                      int recordCount, int[] blocks) {
        byte[] e = new byte[32];
        e[0] = (byte) userNumber;
        byte[] nameBytes = pad(filename, 8).getBytes();
        System.arraycopy(nameBytes, 0, e, 1, 8);
        byte[] extBytes = pad(extension, 3).getBytes();
        System.arraycopy(extBytes, 0, e, 9, 3);
        if (readOnly) e[9] |= (byte) 0x80;
        if (system) e[10] |= (byte) 0x80;
        e[0x0C] = (byte) extentLow;
        e[0x0E] = (byte) extentHigh;
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

    @Test
    void parsesRegularEntry() {
        byte[] buf = entryBytes(0, "TEST", "BIN", false, false, 0, 0, 5, new int[]{2, 3});

        CatalogEntry entry = CatalogEntry.parse(buf, 0);

        assertEquals(0, entry.userNumber);
        assertEquals("TEST", entry.filename);
        assertEquals("BIN", entry.extension);
        assertEquals("TEST.BIN", entry.fullName());
        assertFalse(entry.isDeleted());
        assertFalse(entry.readOnly);
        assertFalse(entry.system);
        assertEquals(5, entry.recordCount);
        assertEquals(2, entry.blocks[0]);
        assertEquals(3, entry.blocks[1]);
    }

    @Test
    void deletedEntryIsDetected() {
        byte[] buf = entryBytes(0xE5, "XXXXXXXX", "XXX", false, false, 0, 0, 0, new int[0]);

        CatalogEntry entry = CatalogEntry.parse(buf, 0);

        assertTrue(entry.isDeleted());
    }

    @Test
    void readOnlyAndSystemFlagsAreBit7OfExtensionBytes() {
        byte[] buf = entryBytes(0, "TEST", "BIN", true, true, 0, 0, 0, new int[0]);

        CatalogEntry entry = CatalogEntry.parse(buf, 0);

        assertTrue(entry.readOnly);
        assertTrue(entry.system);
        assertEquals("BIN", entry.extension); // le bit 7 ne doit pas polluer le nom
    }

    @Test
    void extentNumberCombinesLowAndHighParts() {
        byte[] buf = entryBytes(0, "TEST", "BIN", false, false, 3, 1, 0, new int[0]);

        CatalogEntry entry = CatalogEntry.parse(buf, 0);

        assertEquals(3 + (1 << 5), entry.extentNumber());
    }
}
