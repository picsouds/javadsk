package dsk.gui;

import com.formdev.flatlaf.FlatLightLaf;
import dsk.gui.controller.MainController;
import dsk.gui.view.MainFrame;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

/** Point d'entrée : assemble modèle/vue/contrôleur (cf. dsk.gui.model/.view/.controller) et lance la fenêtre. */
public final class MainWindow {

    private MainWindow() {
    }

    // args[0] : image .dsk/.edsk, ou archive .7z/.zip la contenant. args[1] (optionnel) : nom de
    // l'image dans l'archive, si args[0] en contient plusieurs (équivalent GUI de --entry en CLI).
    @SuppressWarnings("PathTraversal")
    public static void main(String[] args) {
        FlatLightLaf.setup();
        Path startupFile = args.length > 0 ? Path.of(args[0]) : null;
        String startupEntry = args.length > 1 ? args[1] : null;
        SwingUtilities.invokeLater(() -> {
            MainController controller = new MainController();
            MainFrame frame = new MainFrame(controller);
            controller.attach(frame);
            frame.setVisible(true);
            if (startupFile != null) {
                controller.openStartupFile(startupFile, startupEntry);
            }
        });
    }
}
