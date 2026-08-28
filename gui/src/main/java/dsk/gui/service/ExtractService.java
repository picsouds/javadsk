package dsk.gui.service;

import dsk.amsdos.AmsdosHeader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Extraction d'un fichier du catalogue vers un fichier local - logique pure, sans Swing (cf. dsk.cli.ExtractCommand). */
public final class ExtractService {

    /** @param keepHeader garde le header AMSDOS brut (comme iDSK -g) */
    @SuppressWarnings("PathTraversal")
    public int extract(byte[] raw, boolean keepHeader, Path outFile) throws IOException {
        AmsdosHeader header = AmsdosHeader.parse(raw);
        byte[] payload = keepHeader || !header.isValid() ? raw : AmsdosHeader.payloadOf(raw);
        Files.write(outFile, payload);
        return payload.length;
    }
}
