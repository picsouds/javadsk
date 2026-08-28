package dsk.gui.model;

import dsk.DiskImage;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskSessionTest {

    private static Path buildDskWithOneFile(Path dir, String name, byte[] content) throws IOException {
        Path dskPath = dir.resolve("test.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, name, content,
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        disk.writeToFile(dskPath);
        return dskPath;
    }

    @Test
    void isNotLoadedBeforeLoad() {
        assertFalse(new DiskSession().isLoaded());
    }

    @Test
    void loadPopulatesCatalogFilesAndPath(@TempDir Path tempDir) throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.US_ASCII);
        Path dskPath = buildDskWithOneFile(tempDir, "HELLO.TXT", content);

        DiskSession session = new DiskSession();
        session.load(dskPath);

        assertTrue(session.isLoaded());
        assertEquals(dskPath, session.getPath());
        assertNotNull(session.getDisk());
        assertNotNull(session.getCatalog());
        assertTrue(session.getFiles().containsKey("HELLO.TXT"));
        // Un fichier Ascii sans header AMSDOS n'a pas de longueur exacte stockée : le catalogue ne
        // connaît que des blocs de 1024 octets, le contenu réel est donc un préfixe du bloc rendu.
        byte[] raw = session.rawDataOf("HELLO.TXT");
        assertArrayEquals(content, java.util.Arrays.copyOf(raw, content.length));
    }

    @Test
    void loadThrowsOnMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.dsk");
        DiskSession session = new DiskSession();
        assertThrows(IOException.class, () -> session.load(missing));
    }
}
