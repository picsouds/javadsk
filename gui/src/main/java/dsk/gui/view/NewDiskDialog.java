package dsk.gui.view;

import dsk.cpm.DiskFormat;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Optional;

/** Boîte de dialogue "Créer une image vierge", équivalent GUI de la commande CLI 'new'. */
public final class NewDiskDialog {

    private NewDiskDialog() {
    }

    public static Optional<NewDiskRequest> showAndCollect(Component owner) {
        JTextField outField = new JTextField(22);
        outField.setEditable(false);
        JButton browseOut = new JButton("Enregistrer sous");
        browseOut.addActionListener(e -> FileChoosers.pickSaveTarget(owner, null)
                .ifPresent(p -> outField.setText(p.toString())));

        JComboBox<String> formatCombo = new JComboBox<>(new String[]{"data", "system", "ibm"});
        JTextField tracksField = new JTextField("40", 8);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int y = 0;
        DialogRows.add(panel, gbc, y++, "Sortie .dsk :", outField, browseOut);
        DialogRows.add(panel, gbc, y++, "Format CP/M :", formatCombo, null);
        DialogRows.add(panel, gbc, y, "Pistes :", tracksField, null);

        int choice = JOptionPane.showConfirmDialog(owner, panel, "Créer une image vierge",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        if (outField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(owner, "Choisis un fichier de sortie.", "javadsk", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        try {
            int tracks = Integer.parseInt(tracksField.getText().trim());
            String formatName = (String) formatCombo.getSelectedItem();
            DiskFormat format;
            switch (formatName) {
                case "system":
                    format = DiskFormat.SYSTEM;
                    break;
                case "ibm":
                    format = DiskFormat.IBM;
                    break;
                default:
                    format = DiskFormat.DATA;
            }
            return Optional.of(new NewDiskRequest(Path.of(outField.getText()), formatName, format, tracks));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            return Optional.empty();
        }
    }
}
