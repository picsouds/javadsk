package dsk;

import dsk.support.DskImageBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiskImageTest {

    @Test
    void parsesStandardDiskInfoBlock() throws IOException {
        byte[] raw = new DskImageBuilder().build();

        DiskImage disk = DiskImage.parse(raw);

        assertFalse(disk.extended);
        assertEquals(1, disk.numberOfTracks);
        assertEquals(1, disk.numberOfSides);
        assertEquals(1, disk.getTracks().size());
    }

    @Test
    void locatesTrackAndSectorsById() throws IOException {
        byte[] payload = "HELLO".getBytes(StandardCharsets.US_ASCII);
        byte[] raw = new DskImageBuilder()
                .writeAt(0, payload) // dans le 1er secteur (0xC1)
                .build();

        DiskImage disk = DiskImage.parse(raw);
        TrackInfo track = disk.findTrack(0, 0);

        assertNotNull(track);
        assertEquals(9, track.getSectors().size());

        SectorInfo sector = track.getSectorById(0xC1);
        assertNotNull(sector);
        assertEquals(512, sector.getData().length);
        assertEquals("HELLO", new String(sector.getData(), 0, 5, StandardCharsets.US_ASCII));

        assertNull(track.getSectorById(0x99));
    }

    @Test
    void rejectsUnknownSignature() {
        byte[] raw = new byte[300];
        System.arraycopy("NOT A DSK FILE".getBytes(StandardCharsets.US_ASCII), 0, raw, 0, 14);

        assertThrows(IOException.class, () -> DiskImage.parse(raw));
    }

    @Test
    void rejectsFileShorterThanDiskInfoBlock() {
        byte[] raw = new byte[100];

        assertThrows(IOException.class, () -> DiskImage.parse(raw));
    }

    @Test
    void writeThenParseRoundTripsSectorData() throws IOException {
        byte[] payload = "HELLO WORLD".getBytes(StandardCharsets.US_ASCII);
        byte[] raw = new DskImageBuilder().writeAt(0, payload).build();
        DiskImage original = DiskImage.parse(raw);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.write(out);
        DiskImage roundTripped = DiskImage.parse(out.toByteArray());

        assertEquals(original.extended, roundTripped.extended);
        assertEquals(original.numberOfTracks, roundTripped.numberOfTracks);
        assertEquals(original.numberOfSides, roundTripped.numberOfSides);
        assertEquals(original.getTracks().size(), roundTripped.getTracks().size());

        TrackInfo originalTrack = original.findTrack(0, 0);
        TrackInfo roundTrippedTrack = roundTripped.findTrack(0, 0);
        assertEquals(originalTrack.getSectors().size(), roundTrippedTrack.getSectors().size());
        assertArrayEquals(originalTrack.getSectorById(0xC1).getData(), roundTrippedTrack.getSectorById(0xC1).getData());
    }

    @Test
    void formattedDiskIsReadableAndFilledWithE5() throws IOException {
        DiskImage disk = DiskImage.formatted("javadsk-tests", 40, 512, 9, 0xC1);

        assertFalse(disk.extended);
        assertEquals(40, disk.numberOfTracks);
        assertEquals(1, disk.numberOfSides);
        assertEquals(40, disk.getTracks().size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        disk.write(out);
        DiskImage reread = DiskImage.parse(out.toByteArray());

        assertEquals(40, reread.getTracks().size());
        TrackInfo track0 = reread.findTrack(0, 0);
        assertEquals(9, track0.getSectors().size());
        // entrelacement "+4" standard CPC : C1,C6,C2,C7,C3,C8,C4,C9,C5
        int[] expectedIds = {0xC1, 0xC6, 0xC2, 0xC7, 0xC3, 0xC8, 0xC4, 0xC9, 0xC5};
        for (int i = 0; i < expectedIds.length; i++) {
            assertEquals(expectedIds[i], track0.getSectors().get(i).sectorId);
        }
        byte[] expectedFill = new byte[512];
        java.util.Arrays.fill(expectedFill, (byte) 0xE5);
        assertArrayEquals(expectedFill, track0.getSectorById(0xC1).getData());
    }
}
