package dsk.gui.view;

import dsk.amsdos.AmsdosHeader;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;

/** Panneau d'information "Header AMSDOS", équivalent GUI de la commande CLI 'info'. */
public final class HeaderInfoDialog {

    private HeaderInfoDialog() {
    }

    public static void show(Component owner, String name, AmsdosHeader header) {
        if (!header.isValid()) {
            JOptionPane.showMessageDialog(owner,
                    "Pas de header AMSDOS valide (checksum 0x" + Integer.toHexString(header.checksum)
                            + " != calculé 0x" + Integer.toHexString(header.computedChecksum) + ")",
                    "Header AMSDOS - " + name, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 4));
        addRow(panel, "Nom", header.filename + "." + header.extension);
        addRow(panel, "User", String.valueOf(header.userNumber));
        addRow(panel, "Type", header.fileTypeLabel() + " (" + header.fileType + ")");
        addRow(panel, "Bloc", header.blockNumber + " (dernier : " + header.lastBlock + ")");
        addRow(panel, "Indicateur 1er bloc", String.format("0x%02X", header.firstBlockFlag));
        addRow(panel, "Longueur logique", header.logicalLength + " octets");
        addRow(panel, "Longueur réelle", header.realLength24 + " octets (24 bits)");
        addRow(panel, "Adresse chargement", String.format("0x%04X", header.loadAddress));
        addRow(panel, "Adresse exécution", String.format("0x%04X", header.entryAddress));
        addRow(panel, "Checksum", String.format("0x%04X (valide)", header.checksum));
        JOptionPane.showMessageDialog(owner, panel, "Header AMSDOS - " + name, JOptionPane.INFORMATION_MESSAGE);
    }

    private static void addRow(JPanel panel, String label, String value) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD));
        panel.add(labelComponent);
        panel.add(new JLabel(value));
    }
}
