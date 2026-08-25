package dsk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorInfoTest {

    @Test
    void sizeFromCodeMatchesShiftedValue() {
        assertEquals(128, new SectorInfo(0, 0, 0xC1, 0, 0, 0, 128).sizeFromCode());
        assertEquals(256, new SectorInfo(0, 0, 0xC1, 1, 0, 0, 256).sizeFromCode());
        assertEquals(512, new SectorInfo(0, 0, 0xC1, 2, 0, 0, 512).sizeFromCode());
        assertEquals(1024, new SectorInfo(0, 0, 0xC1, 3, 0, 0, 1024).sizeFromCode());
    }

    @Test
    void noCrcErrorWhenStatusBitsClear() {
        SectorInfo s = new SectorInfo(0, 0, 0xC1, 2, 0x00, 0x00, 512);
        assertFalse(s.hasCrcError());
    }

    @Test
    void crcErrorDetectedFromStatus1() {
        SectorInfo s = new SectorInfo(0, 0, 0xC1, 2, 0x20, 0x00, 512);
        assertTrue(s.hasCrcError());
    }

    @Test
    void crcErrorDetectedFromStatus2() {
        SectorInfo s = new SectorInfo(0, 0, 0xC1, 2, 0x00, 0x20, 512);
        assertTrue(s.hasCrcError());
    }
}
