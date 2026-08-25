package dsk.archive;

import dsk.support.DskImageBuilder;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZipDskTest {

    @Test
    void isZipDetectsExtensionCaseInsensitively() {
        assertTrue(ZipDsk.isZip(Path.of("game.zip")));
        assertTrue(ZipDsk.isZip(Path.of("GAME.ZIP")));
        assertTrue(ZipDsk.isZip(Path.of("archive.tar.zip")));
        assertFalse(ZipDsk.isZip(Path.of("game.dsk")));
    }

    @Test
    void extractsTheSingleDskEntry(@TempDir Path tmp) throws IOException {
        byte[] dsk = new DskImageBuilder().build();
        Path archive = writeArchive(tmp, "single.zip", "game.dsk", dsk);

        assertEquals(List.of("game.dsk"), ZipDsk.listDskEntries(archive));
        assertArrayEquals(dsk, ZipDsk.extractSingle(archive));
        assertArrayEquals(dsk, ZipDsk.extract(archive, "game.dsk"));
    }

    @Test
    void extractSingleFailsWhenNoDskEntry(@TempDir Path tmp) throws IOException {
        Path archive = writeArchive(tmp, "empty.zip", "readme.txt", "hello".getBytes());

        assertThrows(IOException.class, () -> ZipDsk.extractSingle(archive));
    }

    @Test
    void extractSingleFailsWhenMultipleDskEntriesRequiringEntryOption(@TempDir Path tmp) throws IOException {
        byte[] sideA = new DskImageBuilder().build();
        byte[] sideB = new DskImageBuilder().build();
        Path archive = writeArchiveMulti(tmp, "multi.zip",
                new String[]{"Side A.dsk", "Side B.dsk"},
                new byte[][]{sideA, sideB});

        assertEquals(List.of("Side A.dsk", "Side B.dsk"), ZipDsk.listDskEntries(archive));
        assertThrows(IOException.class, () -> ZipDsk.extractSingle(archive));
        assertArrayEquals(sideB, ZipDsk.extract(archive, "Side B.dsk"));
    }

    private static Path writeArchive(Path dir, String archiveName, String entryName, byte[] content) throws IOException {
        return writeArchiveMulti(dir, archiveName, new String[]{entryName}, new byte[][]{content});
    }

    private static Path writeArchiveMulti(Path dir, String archiveName, String[] entryNames, byte[][] contents) throws IOException {
        Path archive = dir.resolve(archiveName);
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(archive.toFile())) {
            for (int i = 0; i < entryNames.length; i++) {
                ZipArchiveEntry entry = new ZipArchiveEntry(entryNames[i]);
                out.putArchiveEntry(entry);
                out.write(contents[i]);
                out.closeArchiveEntry();
            }
        }
        return archive;
    }
}
