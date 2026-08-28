package dsk.gui.view;

import dsk.cpm.CatalogWriter;
import dsk.gui.service.PutRequest;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Optional;

import static dsk.gui.view.FileChoosers.newFileChooser;
import static dsk.gui.view.FileChoosers.pickSaveTarget;

/** Boîte de dialogue "Importer un fichier", équivalent GUI de la commande CLI 'put'. */
public final class PutDialog {

    /** Options tri-état d'un flag AMSDOS, calquées sur --xxx/--no-xxx de PutCommand (null = ne pas toucher). */
    private static final String[] TRISTATE_OPTIONS = {"Auto (garder existant)", "Oui", "Non"};

    private PutDialog() {
    }

    public static Optional<PutRequest> showAndCollect(Component owner, String[] existingNames, String prefillName,
                                                        String prefillLoad, String prefillExec, Path sourceParent) {
        JTextField localFileField = new JTextField(22);
        localFileField.setEditable(false);
        JButton browseLocal = new JButton("Parcourir");
        browseLocal.addActionListener(e -> {
            JFileChooser chooser = newFileChooser();
            if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                localFileField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JComboBox<String> nameCombo = new JComboBox<>(existingNames);
        nameCombo.setEditable(true);
        nameCombo.setSelectedItem(prefillName);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{
                "Auto (garder le header existant)", "Ascii", "Basic", "Binaire"});
        JCheckBox tokenizeCheck = new JCheckBox("Retokeniser un listing texte (--tokenize)");
        JTextField loadField = new JTextField(prefillLoad, 8);
        JTextField execField = new JTextField(prefillExec, 8);
        JComboBox<String> readOnlyCombo = new JComboBox<>(TRISTATE_OPTIONS);
        JComboBox<String> hiddenCombo = new JComboBox<>(TRISTATE_OPTIONS);

        JTextField outField = new JTextField(22);
        outField.setEditable(false);
        JButton browseOut = new JButton("Enregistrer sous");
        // Pas de nom pré-rempli : proposer le même nom que la source serait un écrasement du disque
        // d'origine à un clic près (le --out de la CLI n'a lui aucun défaut).
        browseOut.addActionListener(e -> pickSaveTarget(owner, sourceParent).ifPresent(p -> outField.setText(p.toString())));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int y = 0;
        DialogRows.add(panel, gbc, y++, "Fichier local :", localFileField, browseLocal);
        DialogRows.add(panel, gbc, y++, "Nom dans le catalogue :", nameCombo, null);
        DialogRows.add(panel, gbc, y++, "Type :", typeCombo, null);
        DialogRows.add(panel, gbc, y++, "", tokenizeCheck, null);
        DialogRows.add(panel, gbc, y++, "Adresse de chargement (hex) :", loadField, null);
        DialogRows.add(panel, gbc, y++, "Adresse d'exécution (hex) :", execField, null);
        DialogRows.add(panel, gbc, y++, "Protection écriture (RO) :", readOnlyCombo, null);
        DialogRows.add(panel, gbc, y++, "Caché (H) :", hiddenCombo, null);
        DialogRows.add(panel, gbc, y, "Sortie .dsk :", outField, browseOut);

        int choice = JOptionPane.showConfirmDialog(owner, panel, "Importer un fichier",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        String targetName = nameCombo.getEditor().getItem().toString().trim();
        if (localFileField.getText().isEmpty() || targetName.isEmpty() || outField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(owner, "Fichier local, nom cible et sortie sont obligatoires.", "javadsk", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }

        String typeOverride;
        switch (typeCombo.getSelectedIndex()) {
            case 1:
                typeOverride = CatalogWriter.TYPE_ASCII;
                break;
            case 2:
                typeOverride = CatalogWriter.TYPE_BASIC;
                break;
            case 3:
                typeOverride = CatalogWriter.TYPE_BINARY;
                break;
            default:
                typeOverride = null;
        }
        try {
            return Optional.of(new PutRequest(Path.of(localFileField.getText()), targetName, typeOverride,
                    tokenizeCheck.isSelected(), parseAddress(loadField.getText()), parseAddress(execField.getText()),
                    triState(readOnlyCombo), triState(hiddenCombo), Path.of(outField.getText())));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            return Optional.empty();
        }
    }

    private static Boolean triState(JComboBox<String> combo) {
        switch (combo.getSelectedIndex()) {
            case 1:
                return Boolean.TRUE;
            case 2:
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private static Integer parseAddress(String s) {
        return (s == null || s.isBlank()) ? null : Integer.decode(s.trim());
    }
}
