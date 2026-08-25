package dsk.cli;

import dsk.cpm.DiskFormat;

/** Formats CP/M sélectionnables en ligne de commande, mappés vers les constantes {@link DiskFormat}. */
public enum CpmFormat {
    // Minuscules volontaires : affichées telles quelles dans l'aide picocli (${COMPLETION-CANDIDATES}).
    data(DiskFormat.DATA),
    system(DiskFormat.SYSTEM),
    ibm(DiskFormat.IBM);

    final DiskFormat diskFormat;

    CpmFormat(DiskFormat diskFormat) {
        this.diskFormat = diskFormat;
    }
}
