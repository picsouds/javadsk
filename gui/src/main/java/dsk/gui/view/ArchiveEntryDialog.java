package dsk.gui.view;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.List;
import java.util.Optional;

/** Sélection d'une image .dsk/.edsk parmi plusieurs dans une archive .7z/.zip (équivalent GUI de --entry). */
public final class ArchiveEntryDialog {

    private ArchiveEntryDialog() {
    }

    public static Optional<String> pickEntry(Component owner, List<String> entries) {
        Object choice = JOptionPane.showInputDialog(owner,
                "Plusieurs images .dsk/.edsk dans l'archive, laquelle ouvrir ?",
                "Choisir une image", JOptionPane.QUESTION_MESSAGE, null,
                entries.toArray(), entries.get(0));
        return Optional.ofNullable((String) choice);
    }
}
