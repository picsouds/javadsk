package dsk.gui.view;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static dsk.gui.view.FileChoosers.confirmOverwriteIfExists;
import static dsk.gui.view.FileChoosers.newFileChooser;

/** Visionneuse de texte non-modale (Ascii/Hex/Basic), avec Enregistrer/Copier dans un menu Fichier. */
public final class TextViewerDialog {

    private TextViewerDialog() {
    }

    @SuppressWarnings("PathTraversal")
    public static void show(JFrame owner, String title, String content, Charset saveCharset) {
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(700, 500));

        JDialog dialog = new JDialog(owner, title, false);

        JMenuItem save = new JMenuItem("Enregistrer");
        save.addActionListener(e -> {
            JFileChooser chooser = newFileChooser();
            if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            if (!confirmOverwriteIfExists(dialog, chooser.getSelectedFile())) {
                return;
            }
            try {
                Files.write(chooser.getSelectedFile().toPath(), content.getBytes(saveCharset));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Erreur d'écriture", JOptionPane.ERROR_MESSAGE);
            }
        });
        JMenuItem copy = new JMenuItem("Copier dans le presse-papier");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(content), null));

        JMenu fileMenu = new JMenu("Fichier");
        fileMenu.add(save);
        fileMenu.add(copy);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        dialog.setJMenuBar(menuBar);

        dialog.add(scrollPane);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
