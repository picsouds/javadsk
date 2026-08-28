package dsk.gui.controller;

import dsk.DiskImage;
import dsk.amsdos.AmsdosHeader;
import dsk.amsdos.BasicProtect;
import dsk.basic.BasicDetokenizer;
import dsk.cpm.DiskFormat;
import dsk.gui.model.CatalogTableModel;
import dsk.gui.model.DiskSession;
import dsk.gui.service.ExtractService;
import dsk.gui.service.PutRequest;
import dsk.gui.service.PutService;
import dsk.gui.service.RemoveService;
import dsk.gui.view.ArchiveEntryDialog;
import dsk.gui.view.FileChoosers;
import dsk.gui.view.HeaderInfoDialog;
import dsk.gui.view.MainFrame;
import dsk.gui.view.NewDiskDialog;
import dsk.gui.view.NewDiskRequest;
import dsk.gui.view.PutDialog;
import dsk.gui.view.TextViewerDialog;
import dsk.hex.HexDump;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static dsk.gui.view.FileChoosers.confirmOverwriteIfExists;
import static dsk.gui.view.FileChoosers.newFileChooser;

/** Toutes les actions du GUI, portées 1:1 depuis les commandes CLI (dsk.cli.*). */
public final class MainController {

    private static final String ERROR_TITLE = "Erreur";

    private final DiskSession session = new DiskSession();
    private final PutService putService = new PutService();
    private final RemoveService removeService = new RemoveService();
    private final ExtractService extractService = new ExtractService();
    private MainFrame frame;

    public void attach(MainFrame frame) {
        this.frame = frame;
    }

    /** @param entry nom de l'image dans l'archive .7z/.zip visée par path, si déjà connu (équivalent GUI de --entry), sinon null. */
    public void openStartupFile(Path path, String entry) {
        openPath(path, entry);
    }

    public void onOpen() {
        JFileChooser chooser = newFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images DSK/EDSK, archives .7z/.zip", "dsk", "edsk", "zip", "7z"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        openPath(chooser.getSelectedFile().toPath(), null);
    }

    private void openPath(Path path, String knownEntry) {
        try {
            String entry = knownEntry;
            if (entry == null) {
                List<String> ambiguous = DiskSession.listArchiveEntriesIfAmbiguous(path);
                if (!ambiguous.isEmpty()) {
                    Optional<String> chosen = ArchiveEntryDialog.pickEntry(frame, ambiguous);
                    if (chosen.isEmpty()) {
                        return;
                    }
                    entry = chosen.get();
                }
            }
            loadCatalog(path, entry);
        } catch (IOException e) {
            frame.showError("Erreur de lecture", e.getMessage());
        }
    }

    @SuppressWarnings("PathTraversal")
    private void loadCatalog(Path path, String entry) throws IOException {
        session.load(path, entry);
        frame.getTableModel().populate(session);
        frame.setStatus(session.getDisk() + " - " + path.getFileName() + (entry != null ? " [" + entry + "]" : ""));
    }

    @SuppressWarnings("PathTraversal")
    public void onNewDisk() {
        Optional<NewDiskRequest> request = NewDiskDialog.showAndCollect(frame);
        if (request.isEmpty()) {
            return;
        }
        NewDiskRequest r = request.get();
        try {
            DiskFormat format = r.format();
            DiskImage disk = DiskImage.formatted("javadsk", r.tracks(), format.sectorSize, format.sectorsPerTrack, format.firstSectorId);
            disk.writeToFile(r.outPath());
            frame.showInfo("Créé : " + r.outPath() + " (" + r.tracks() + " pistes, format " + r.formatName() + ")");
        } catch (IOException ex) {
            frame.showError(ERROR_TITLE, ex.getMessage());
        }
    }

    public void onExtract(boolean keepHeader) {
        String name = frame.getSelectedFileName();
        if (name == null) {
            return;
        }
        byte[] raw = session.rawDataOf(name);

        JFileChooser chooser = newFileChooser();
        chooser.setSelectedFile(new File(name.replace(' ', '_')));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (!confirmOverwriteIfExists(frame, chooser.getSelectedFile())) {
            return;
        }
        Path outFile = chooser.getSelectedFile().toPath();
        try {
            int length = extractService.extract(raw, keepHeader, outFile);
            frame.showInfo("Extrait : " + name + " -> " + outFile + " (" + length + " octets)");
        } catch (IOException e) {
            frame.showError("Erreur d'écriture", e.getMessage());
        }
    }

    public void onInfo() {
        String name = frame.getSelectedFileName();
        if (name == null) {
            return;
        }
        HeaderInfoDialog.show(frame, name, AmsdosHeader.parse(session.rawDataOf(name)));
    }

    public void onBasic(boolean spaced) {
        String name = frame.getSelectedFileName();
        if (name == null) {
            return;
        }
        byte[] raw = session.rawDataOf(name);
        AmsdosHeader header = AmsdosHeader.parse(raw);
        byte[] payload = AmsdosHeader.payloadOf(raw);
        if (header.isValid() && header.fileType == AmsdosHeader.TYPE_BASIC_PROTECTED) {
            payload = BasicProtect.decode(payload);
        }
        String listing = spaced ? BasicDetokenizer.spacedListing(payload) : BasicDetokenizer.listing(payload);
        // UTF-8 : même encodage que 'basic --spaced' en CLI, requis pour réinjecter avec 'put --tokenize'.
        TextViewerDialog.show(frame, (spaced ? "Basic --spaced - " : "Basic - ") + name, listing, StandardCharsets.UTF_8);
    }

    public void onAscii() {
        String name = frame.getSelectedFileName();
        if (name == null) {
            return;
        }
        byte[] payload = AmsdosHeader.payloadOf(session.rawDataOf(name));
        // Latin-1 et pas UTF-8 : chaque octet CPC (y compris >0x7F) doit rester lui-même, un octet -> un caractère.
        TextViewerDialog.show(frame, "Ascii - " + name, new String(payload, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
    }

    public void onHex() {
        String name = frame.getSelectedFileName();
        if (name == null) {
            return;
        }
        byte[] payload = AmsdosHeader.payloadOf(session.rawDataOf(name));
        TextViewerDialog.show(frame, "Hex - " + name, HexDump.dump(payload), StandardCharsets.UTF_8);
    }

    public void onRemove() {
        if (session.isFromArchive()) {
            frame.showInfo("Suppression non supportée depuis une archive : ouvre directement le fichier .dsk/.edsk.");
            return;
        }
        String name = frame.getSelectedFileName();
        if (name == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(frame, "Supprimer \"" + name + "\" du catalogue ?",
                "Confirmer la suppression", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        Optional<Path> outPath = FileChoosers.pickSaveTarget(frame, session.getPath().getParent());
        if (outPath.isEmpty()) {
            return;
        }
        try {
            removeService.remove(session.getPath(), name, outPath.get());
            // Le catalogue affiché reste celui d'origine, cf. onPut.
            frame.showInfo("Supprimé : " + name + " -> " + outPath.get());
        } catch (IOException ex) {
            frame.showError(ERROR_TITLE, ex.getMessage());
        }
    }

    public void onPut() {
        if (!session.isLoaded()) {
            frame.showInfo("Ouvre d'abord une image .dsk/.edsk.");
            return;
        }
        if (session.isFromArchive()) {
            frame.showInfo("Import non supporté depuis une archive : ouvre directement le fichier .dsk/.edsk.");
            return;
        }
        int row = frame.getTable().getSelectedRow();
        CatalogTableModel model = frame.getTableModel();
        String prefillName = "";
        String prefillLoad = "";
        String prefillExec = "";
        if (row >= 0) {
            prefillName = (String) model.getValueAt(row, CatalogTableModel.COL_NAME);
            String load = (String) model.getValueAt(row, CatalogTableModel.COL_LOAD);
            String exec = (String) model.getValueAt(row, CatalogTableModel.COL_EXEC);
            prefillLoad = "-".equals(load) ? "" : load;
            prefillExec = "-".equals(exec) ? "" : exec;
        }
        String[] existingNames = new String[model.getRowCount()];
        for (int i = 0; i < existingNames.length; i++) {
            existingNames[i] = (String) model.getValueAt(i, CatalogTableModel.COL_NAME);
        }

        Optional<PutRequest> request = PutDialog.showAndCollect(frame, existingNames, prefillName, prefillLoad,
                prefillExec, session.getPath().getParent());
        if (request.isEmpty()) {
            return;
        }
        PutRequest r = request.get();
        try {
            PutService.Result result = putService.put(session.getPath(), r);
            frame.showInfo((result.created() ? "Créé : " : "Remplacé : ") + r.targetName()
                    + " (" + result.contentLength() + " octets) -> " + r.outPath());
        } catch (IOException ex) {
            frame.showError(ERROR_TITLE, ex.getMessage());
        }
    }
}
