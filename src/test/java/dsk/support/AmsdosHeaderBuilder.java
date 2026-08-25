package dsk.support;

import dsk.amsdos.AmsdosHeader;

/** Construit un header AMSDOS (128 octets) valide, checksum inclus, pour les tests. */
public final class AmsdosHeaderBuilder {

    public static byte[] build(String filename, String extension, int fileType,
                                int loadAddress, int entryAddress, int logicalLength) {
        return AmsdosHeader.buildBytes(0, filename, extension, fileType, loadAddress, logicalLength, entryAddress);
    }

    private AmsdosHeaderBuilder() {}
}
