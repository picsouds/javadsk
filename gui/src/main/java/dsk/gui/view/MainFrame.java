package dsk.gui.view;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import dsk.gui.controller.MainController;
import dsk.gui.model.CatalogTableModel;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** La fenêtre principale : catalogue en table, menus, barre de statut. Toute la logique métier est dans MainController. */
public final class MainFrame extends JFrame {

    private final CatalogTableModel tableModel = new CatalogTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel statusBar = new JLabel(" ");
    private final List<JMenu> basicMenus = new ArrayList<>();
    // transient : JFrame implémente Serializable par héritage AWT, jamais utilisé en pratique ici.
    private final transient MainController controller;

    public MainFrame(MainController controller) {
        super("javadsk gui" + versionSuffix());
        this.controller = controller;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setComponentPopupMenu(buildTableContextMenu());
        table.addMouseListener(new SelectRowOnRightClick());
        table.getSelectionModel().addListSelectionListener(e -> updateBasicMenuEnabled());
        updateBasicMenuEnabled();
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setSize(800, 600);
        setLocationRelativeTo(null);
    }

    private static String versionSuffix() {
        String version = MainFrame.class.getPackage().getImplementationVersion();
        return version != null ? " - " + version : "";
    }

    public CatalogTableModel getTableModel() {
        return tableModel;
    }

    public JTable getTable() {
        return table;
    }

    public void setStatus(String text) {
        statusBar.setText(" " + text);
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "javadsk", JOptionPane.INFORMATION_MESSAGE);
    }

    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public String getSelectedFileName() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Sélectionne un fichier dans la table.", "javadsk", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return (String) tableModel.getValueAt(row, CatalogTableModel.COL_NAME);
    }

    private JMenuBar buildMenuBar() {
        JMenuItem open = new JMenuItem("Ouvrir");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        open.addActionListener(e -> controller.onOpen());

        JMenuItem newDisk = new JMenuItem("Créer une image vierge");
        newDisk.addActionListener(e -> controller.onNewDisk());

        JMenuItem quit = new JMenuItem("Quitter");
        quit.addActionListener(e -> dispose());

        JMenu file = new JMenu("Fichier");
        file.add(open);
        file.add(newDisk);
        file.addSeparator();
        file.add(quit);

        JMenu catalogMenu = new JMenu("Catalogue");
        catalogMenu.add(buildExtractMenu());
        catalogMenu.add(buildVisualisationMenu());
        catalogMenu.add(buildImportMenuItem());
        catalogMenu.add(buildRemoveMenuItem());

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);
        menuBar.add(catalogMenu);
        menuBar.add(buildThemesMenu());
        return menuBar;
    }

    private JPopupMenu buildTableContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(buildExtractMenu());
        menu.add(buildVisualisationMenu());
        menu.add(buildImportMenuItem());
        menu.add(buildRemoveMenuItem());
        return menu;
    }

    /** Un sous-menu par commande du catalogue (extraire/info/...), calqué sur les sous-commandes de la CLI. */
    private JMenu buildExtractMenu() {
        JMenu extractMenu = new JMenu("Extraire");
        extractMenu.add(extractMenuItem("Normal (sans header)", false));
        extractMenu.add(extractMenuItem("Brut (AMSDOS)", true));
        return extractMenu;
    }

    /** Toutes les façons d'afficher le contenu d'un fichier sans l'extraire. */
    private JMenu buildVisualisationMenu() {
        JMenu visuMenu = new JMenu("Visualisation");
        visuMenu.add(simpleMenuItem("Ascii", controller::onAscii));
        visuMenu.add(simpleMenuItem("Hex", controller::onHex));
        visuMenu.add(buildBasicMenu());
        visuMenu.add(simpleMenuItem("Header (AMSDOS)", controller::onInfo));
        return visuMenu;
    }

    /** Import/remplacement d'un fichier dans le catalogue, équivalent GUI de la commande CLI 'put'. */
    private JMenuItem buildImportMenuItem() {
        JMenuItem item = new JMenuItem("Importer un fichier");
        item.addActionListener(e -> controller.onPut());
        return item;
    }

    /** Suppression d'un fichier du catalogue, équivalent GUI de la commande CLI 'remove'. */
    private JMenuItem buildRemoveMenuItem() {
        JMenuItem item = new JMenuItem("Supprimer un fichier");
        item.addActionListener(e -> controller.onRemove());
        return item;
    }

    private JMenu buildBasicMenu() {
        JMenu basicMenu = new JMenu("Basic");
        basicMenu.add(simpleMenuItem("Listing (compact)", () -> controller.onBasic(false)));
        basicMenu.add(simpleMenuItem("Listing espacé (--spaced)", () -> controller.onBasic(true)));
        basicMenus.add(basicMenu);
        return basicMenu;
    }

    private void updateBasicMenuEnabled() {
        int row = table.getSelectedRow();
        boolean enabled = row >= 0 && "Basic".equals(tableModel.getValueAt(row, CatalogTableModel.COL_TYPE));
        basicMenus.forEach(menu -> menu.setEnabled(enabled));
    }

    private JMenuItem simpleMenuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private JMenuItem extractMenuItem(String label, boolean keepHeader) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> controller.onExtract(keepHeader));
        return item;
    }

private final class SelectRowOnRightClick extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                }
            }
        }
    }

    /** Thèmes FlatLaf (cf. <a href="https://github.com/JFormDesigner/FlatLaf">...</a>) : les 4 de base + tout le pack IntelliJ. */
    private JMenu buildThemesMenu() {
        JMenu menu = new JMenu("Thèmes");
        ButtonGroup group = new ButtonGroup();

        menu.add(themeMenuItem("Clair (FlatLaf)", FlatLightLaf.class.getName(), group, true));
        menu.add(themeMenuItem("Sombre (FlatLaf)", FlatDarkLaf.class.getName(), group, false));
        menu.add(themeMenuItem("IntelliJ", FlatIntelliJLaf.class.getName(), group, false));
        menu.add(themeMenuItem("Darcula", FlatDarculaLaf.class.getName(), group, false));
        menu.addSeparator();

        JMenu light = new JMenu("Thèmes clairs");
        JMenu dark = new JMenu("Thèmes sombres");
        for (FlatAllIJThemes.FlatIJLookAndFeelInfo info : FlatAllIJThemes.INFOS) {
            JMenu target = info.isDark() ? dark : light;
            target.add(themeMenuItem(info.getName(), info.getClassName(), group, false));
        }
        menu.add(light);
        menu.add(dark);
        return menu;
    }

    private JRadioButtonMenuItem themeMenuItem(String label, String lafClassName, ButtonGroup group, boolean selected) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, selected);
        item.addActionListener(e -> applyTheme(lafClassName));
        group.add(item);
        return item;
    }

    private void applyTheme(String lafClassName) {
        // Différé : appelé depuis l'action d'un item de menu encore en train de se fermer, sinon le
        // rafraîchissement de l'arbre de composants ne prend pas visuellement (le menu applique bien
        // le LaF en interne mais ne se redessine pas tant que sa propre fermeture n'est pas terminée).
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(lafClassName);
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
                showError("Erreur de thème", ex.getMessage());
                return;
            }
            FlatLaf.updateUI();
        });
    }
}
