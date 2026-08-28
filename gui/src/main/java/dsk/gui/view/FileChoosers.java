package dsk.gui.view;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** Petits utilitaires JFileChooser partagés par les boîtes de dialogue du GUI. */
public final class FileChoosers {

    private FileChoosers() {
    }

    public static JFileChooser newFileChooser() {
        JFileChooser chooser = new JFileChooser();
        Action details = chooser.getActionMap().get("viewTypeDetails");
        if (details != null) {
            details.actionPerformed(null);
        }
        return chooser;
    }

    public static boolean confirmOverwriteIfExists(Component parent, File file) {
        if (!file.exists()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(parent,
                "Le fichier \"" + file.getName() + "\" existe déjà. Le remplacer ?",
                "Confirmer le remplacement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    public static Optional<Path> pickSaveTarget(Component owner, Path defaultDir) {
        JFileChooser chooser = newFileChooser();
        if (defaultDir != null) {
            chooser.setCurrentDirectory(defaultDir.toFile());
        }
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        if (!confirmOverwriteIfExists(owner, chooser.getSelectedFile())) {
            return Optional.empty();
        }
        return Optional.of(chooser.getSelectedFile().toPath());
    }
}
