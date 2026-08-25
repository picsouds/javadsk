package dsk.cpm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiskFormatTest {

    @Test
    void dataFormatMatchesSpec() {
        assertEquals(512, DiskFormat.DATA.sectorSize);
        assertEquals(9, DiskFormat.DATA.sectorsPerTrack);
        assertEquals(0xC1, DiskFormat.DATA.firstSectorId);
        assertEquals(0, DiskFormat.DATA.reservedTracks);
        assertEquals(1024, DiskFormat.DATA.blockSize);
        assertEquals(64, DiskFormat.DATA.directoryEntries);
    }

    @Test
    void systemFormatReservesTwoTracks() {
        assertEquals(0x41, DiskFormat.SYSTEM.firstSectorId);
        assertEquals(2, DiskFormat.SYSTEM.reservedTracks);
    }

    @Test
    void ibmFormatUsesEightSectorsPerTrack() {
        assertEquals(0x01, DiskFormat.IBM.firstSectorId);
        assertEquals(8, DiskFormat.IBM.sectorsPerTrack);
        assertEquals(1, DiskFormat.IBM.reservedTracks);
    }
}
