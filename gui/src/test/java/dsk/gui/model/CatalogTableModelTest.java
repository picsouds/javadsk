package dsk.gui.model;

import dsk.DiskImage;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CatalogTableModelTest {

    @Test
    void cellsAreNeverEditable() {
        CatalogTableModel model = new CatalogTableModel();
        assertFalse(model.isCellEditable(0, CatalogTableModel.COL_NAME));
    }

    @Test
    void populateBuildsOneRowPerFileWithHeaderColumns(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("test.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "PROG.BIN", new byte[100],
                null, null, null, 0x4000, 0x4000, CatalogWriter.TYPE_BINARY);
        disk.writeToFile(dskPath);

        DiskSession session = new DiskSession();
        session.load(dskPath);

        CatalogTableModel model = new CatalogTableModel();
        model.populate(session);

        assertEquals(1, model.getRowCount());
        assertEquals("PROG.BIN", model.getValueAt(0, CatalogTableModel.COL_NAME));
        assertEquals("Binaire", model.getValueAt(0, CatalogTableModel.COL_TYPE));
        assertEquals("0x4000", model.getValueAt(0, CatalogTableModel.COL_LOAD));
        assertEquals("0x4000", model.getValueAt(0, CatalogTableModel.COL_EXEC));
        assertEquals(100, model.getValueAt(0, CatalogTableModel.COL_SIZE));
    }

    @Test
    void populateShowsRawSizeAndNoHeaderTypeForHeaderlessFile(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("test.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "README.TXT", "some plain text".getBytes(),
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        disk.writeToFile(dskPath);

        DiskSession session = new DiskSession();
        session.load(dskPath);

        CatalogTableModel model = new CatalogTableModel();
        model.populate(session);

        assertEquals(1, model.getRowCount());
        assertEquals("(sans header)", model.getValueAt(0, CatalogTableModel.COL_TYPE));
        assertEquals("-", model.getValueAt(0, CatalogTableModel.COL_LOAD));
        // Un fichier Ascii sans header AMSDOS n'a pas de longueur exacte stockée : la taille
        // affichée est celle du bloc alloué (1024 octets), pas celle du contenu réel (15 octets).
        assertEquals(DiskFormat.DATA.blockSize, model.getValueAt(0, CatalogTableModel.COL_SIZE));
    }

    @Test
    void populateClearsPreviousRowsOnReload(@TempDir Path tempDir) throws IOException {
        Path dskPath = tempDir.resolve("test.dsk");
        DiskImage disk = DiskImage.formatted("test", 40, DiskFormat.DATA.sectorSize,
                DiskFormat.DATA.sectorsPerTrack, DiskFormat.DATA.firstSectorId);
        CatalogWriter.putFile(disk, DiskFormat.DATA, "ONE.TXT", "a".getBytes(),
                null, null, null, null, null, CatalogWriter.TYPE_ASCII);
        disk.writeToFile(dskPath);

        DiskSession session = new DiskSession();
        session.load(dskPath);
        CatalogTableModel model = new CatalogTableModel();
        model.populate(session);
        assertEquals(1, model.getRowCount());

        model.populate(session);
        assertEquals(1, model.getRowCount());
    }
}
