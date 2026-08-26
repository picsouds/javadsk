package dsk;

import dsk.support.EdskImageBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdskImageTest {

    @Test
    void usesDeclaredSectorLengthRatherThanSizeCode() throws IOException {
        // sizeCode=6 induirait 0x80<<6=8192 octets ; l'EDSK déclare explicitement une longueur
        // différente
        byte[] payload = "HELLO EDSK".getBytes(StandardCharsets.US_ASCII);
        byte[] raw = new EdskImageBuilder()
                .track(0, 0, 0xC1, 6, 100, payload)
                .build();

        DiskImage disk = DiskImage.parse(raw);

        assertTrue(disk.extended);
        SectorInfo sector = disk.findTrack(0, 0).getSectorById(0xC1);
        assertNotNull(sector);
        assertEquals(8192, sector.sizeFromCode());
        assertEquals(100, sector.getData().length);
        assertArrayEquals(payload, java.util.Arrays.copyOf(sector.getData(), payload.length));
    }

    @Test
    void skipsTracksDeclaredWithZeroSizeInTheSizeTable() throws IOException {
        byte[] raw = new EdskImageBuilder()
                .track(0, 0, 0xC1, 2, 512, "T0".getBytes(StandardCharsets.US_ASCII))
                .emptyTrack() // piste 1 : non formatée, absente du fichier
                .track(2, 0, 0xC1, 2, 512, "T2".getBytes(StandardCharsets.US_ASCII))
                .build();

        DiskImage disk = DiskImage.parse(raw);

        assertEquals(2, disk.getTracks().size());
        assertNull(disk.findTrack(1, 0));
        assertNotNull(disk.findTrack(0, 0));
        TrackInfo t2 = disk.findTrack(2, 0);
        assertNotNull(t2);
        assertEquals("T2", new String(t2.getSectorById(0xC1).getData(), 0, 2, StandardCharsets.US_ASCII));
    }
}
