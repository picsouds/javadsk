package dsk.gui.service;

import dsk.amsdos.AmsdosHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractServiceTest {

    @Test
    void keepHeaderWritesTheRawBytesUntouched(@TempDir Path tempDir) throws IOException {
        byte[] header = AmsdosHeader.buildBytes(0, "PROG", "BIN", AmsdosHeader.TYPE_BINARY, 0x4000, 4, 0x4000);
        byte[] raw = new byte[header.length + 4];
        System.arraycopy(header, 0, raw, 0, header.length);
        raw[header.length] = 1;
        raw[header.length + 1] = 2;
        raw[header.length + 2] = 3;
        raw[header.length + 3] = 4;
        Path outFile = tempDir.resolve("out.bin");

        int length = new ExtractService().extract(raw, true, outFile);

        assertEquals(raw.length, length);
        assertArrayEquals(raw, Files.readAllBytes(outFile));
    }

    @Test
    void withoutKeepHeaderStripsAValidAmsdosHeader(@TempDir Path tempDir) throws IOException {
        byte[] payload = {1, 2, 3, 4};
        byte[] header = AmsdosHeader.buildBytes(0, "PROG", "BIN", AmsdosHeader.TYPE_BINARY, 0x4000, payload.length, 0x4000);
        byte[] raw = new byte[header.length + payload.length];
        System.arraycopy(header, 0, raw, 0, header.length);
        System.arraycopy(payload, 0, raw, header.length, payload.length);
        Path outFile = tempDir.resolve("out.bin");

        int length = new ExtractService().extract(raw, false, outFile);

        assertEquals(payload.length, length);
        assertArrayEquals(payload, Files.readAllBytes(outFile));
    }

    @Test
    void withoutKeepHeaderLeavesDataUntouchedWhenNoValidHeaderPresent(@TempDir Path tempDir) throws IOException {
        byte[] raw = "plain ascii content, no amsdos header".getBytes();
        Path outFile = tempDir.resolve("out.txt");

        int length = new ExtractService().extract(raw, false, outFile);

        assertEquals(raw.length, length);
        assertArrayEquals(raw, Files.readAllBytes(outFile));
    }
}
