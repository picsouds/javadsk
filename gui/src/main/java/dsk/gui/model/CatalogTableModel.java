package dsk.gui.model;

import dsk.amsdos.AmsdosHeader;
import dsk.cpm.CatalogEntry;

import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.Map;

/** Table du catalogue affiché (une ligne par fichier), reconstruite à chaque chargement de disque. */
public final class CatalogTableModel extends DefaultTableModel {

    public static final int COL_NAME = 0;
    public static final int COL_USER = 1;
    public static final int COL_RO = 2;
    public static final int COL_HIDDEN = 3;
    public static final int COL_SIZE = 4;
    public static final int COL_TYPE = 5;
    public static final int COL_LOAD = 6;
    public static final int COL_EXEC = 7;

    private static final String[] COLUMNS = {"Fichier", "User", "RO", "H", "Taille", "Type", "Load", "Exec"};

    public CatalogTableModel() {
        super(COLUMNS, 0);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public void populate(DiskSession session) {
        setRowCount(0);
        for (Map.Entry<String, List<CatalogEntry>> e : session.getFiles().entrySet()) {
            CatalogEntry firstExtent = e.getValue().get(0);
            byte[] raw = session.getCatalog().extractRawData(e.getValue());
            AmsdosHeader header = AmsdosHeader.parse(raw);
            String ro = firstExtent.readOnly ? "RO" : "";
            String hidden = firstExtent.system ? "H" : "";
            if (header.isValid()) {
                addRow(new Object[]{e.getKey(), firstExtent.userNumber, ro, hidden,
                        header.logicalLength, header.fileTypeLabel(),
                        String.format("0x%04X", header.loadAddress), String.format("0x%04X", header.entryAddress)});
            } else {
                addRow(new Object[]{e.getKey(), firstExtent.userNumber, ro, hidden,
                        raw.length, "(sans header)", "-", "-"});
            }
        }
    }
}
