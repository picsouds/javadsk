package dsk.archive;

import dsk.support.DskImageBuilder;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SevenZipDskTest {

    @Test
    void isSevenZipDetectsExtensionCaseInsensitively() {
        assertTrue(SevenZipDsk.isSevenZip(Path.of("game.7z")));
        assertTrue(SevenZipDsk.isSevenZip(Path.of("GAME.7Z")));
        assertTrue(SevenZipDsk.isSevenZip(Path.of("archive.tar.7z")));
        assertFalse(SevenZipDsk.isSevenZip(Path.of("game.dsk")));
    }

    @Test
    void extractsTheSingleDskEntry(@TempDir Path tmp) throws IOException {
        byte[] dsk = new DskImageBuilder().build();
        Path archive = writeArchive(tmp, "single.7z", "game.dsk", dsk);

        assertEquals(List.of("game.dsk"), SevenZipDsk.listDskEntries(archive));
        assertArrayEquals(dsk, SevenZipDsk.extractSingle(archive));
        assertArrayEquals(dsk, SevenZipDsk.extract(archive, "game.dsk"));
    }

    @Test
    void extractSingleFailsWhenNoDskEntry(@TempDir Path tmp) throws IOException {
        Path archive = writeArchive(tmp, "empty.7z", "readme.txt", "hello".getBytes());

        assertThrows(IOException.class, () -> SevenZipDsk.extractSingle(archive));
    }

    @Test
    void extractSingleFailsWhenMultipleDskEntriesRequiringEntryOption(@TempDir Path tmp) throws IOException {
        byte[] sideA = new DskImageBuilder().build();
        byte[] sideB = new DskImageBuilder().build();
        Path archive = writeArchiveMulti(tmp, "multi.7z",
                new String[]{"Side A.dsk", "Side B.dsk"},
                new byte[][]{sideA, sideB});

        assertEquals(List.of("Side A.dsk", "Side B.dsk"), SevenZipDsk.listDskEntries(archive));
        assertThrows(IOException.class, () -> SevenZipDsk.extractSingle(archive));
        assertArrayEquals(sideB, SevenZipDsk.extract(archive, "Side B.dsk"));
    }

    private static Path writeArchive(Path dir, String archiveName, String entryName, byte[] content) throws IOException {
        return writeArchiveMulti(dir, archiveName, new String[]{entryName}, new byte[][]{content});
    }

    private static Path writeArchiveMulti(Path dir, String archiveName, String[] entryNames, byte[][] contents) throws IOException {
        Path archive = dir.resolve(archiveName);
        try (SevenZOutputFile out = new SevenZOutputFile(archive.toFile())) {
            for (int i = 0; i < entryNames.length; i++) {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(entryNames[i]);
                entry.setDirectory(false);
                out.putArchiveEntry(entry);
                out.write(contents[i]);
                out.closeArchiveEntry();
            }
        }
        return archive;
    }
}
