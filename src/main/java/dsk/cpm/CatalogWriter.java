package dsk.cpm;

import dsk.DiskImage;
import dsk.amsdos.AmsdosHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ajoute ou remplace un fichier dans le catalogue CP/M : port de l'allocation de blocs/entrées de
 * {@code PutFile} (jeromelesaux/dsk, dsk/dsk.go). Contrairement à l'original, ne fait pas grandir
 * le disque : lève {@link IOException} plutôt que d'étendre le nombre de pistes.
 */
public final class CatalogWriter {

    private static final int MAX_FILE_SIZE = 0x10000;

    /** Types de fichier importables sans reprendre le header d'un fichier déjà présent. */
    public static final String TYPE_ASCII = "ascii";
    public static final String TYPE_BASIC = "basic";
    public static final String TYPE_BINARY = "binary";

    private CatalogWriter() {
    }

    /** Supprime toutes les entrées de catalogue d'un fichier (User &lt;- 0xE5), comme {@code RemoveFile}. */
    public static void removeFile(DiskImage disk, DiskFormat format, String targetName) throws IOException {
        byte[] linear = Catalog.buildLinearStream(disk, format);
        int dirBytes = format.directoryEntries * 32;
        if (linear.length < dirBytes) {
            throw new IOException("Flux de données trop court pour contenir le catalogue");
        }

        boolean found = false;
        for (int i = 0; i < format.directoryEntries; i++) {
            CatalogEntry e = CatalogEntry.parse(linear, i * 32);
            if (!e.isDeleted() && e.fullName().equalsIgnoreCase(targetName)) {
                linear[i * 32] = (byte) CatalogEntry.DELETED_USER;
                found = true;
            }
        }
        if (!found) {
            throw new IOException("Fichier introuvable dans le catalogue : " + targetName);
        }

        Catalog.scatterLinearStream(disk, format, linear);
    }

    /**
     * @param typeOverride {@link #TYPE_ASCII}/{@link #TYPE_BASIC}/{@link #TYPE_BINARY}, ou {@code
     *                     null} pour reprendre le type du fichier remplacé s'il existe et avait un
     *                     header AMSDOS valide (défaut {@link #TYPE_ASCII} sinon, ou pour un
     *                     nouveau fichier)
     * @return {@code true} si le fichier a été créé (n'existait pas dans le catalogue), {@code
     *     false} s'il a remplacé un fichier existant
     */
    public static boolean putFile(DiskImage disk, DiskFormat format, String targetName, byte[] newContent,
                                   Integer userOverride, Boolean readOnlyOverride, Boolean hiddenOverride,
                                   Integer loadOverride, Integer execOverride, String typeOverride) throws IOException {
        byte[] linear = Catalog.buildLinearStream(disk, format);
        int dirBytes = format.directoryEntries * 32;
        if (linear.length < dirBytes) {
            throw new IOException("Flux de données trop court pour contenir le catalogue");
        }

        List<Integer> matchingIndices = new ArrayList<>();
        List<CatalogEntry> existingExtents = new ArrayList<>();
        CatalogEntry firstMatch = null;
        for (int i = 0; i < format.directoryEntries; i++) {
            CatalogEntry e = CatalogEntry.parse(linear, i * 32);
            if (!e.isDeleted() && e.fullName().equalsIgnoreCase(targetName)) {
                matchingIndices.add(i);
                existingExtents.add(e);
                if (firstMatch == null || e.extentNumber() < firstMatch.extentNumber()) {
                    firstMatch = e;
                }
            }
        }
        boolean exists = !matchingIndices.isEmpty();
        existingExtents.sort(Comparator.comparingInt(CatalogEntry::extentNumber));

        AmsdosHeader currentHeader = null;
        if (exists) {
            Catalog probe = Catalog.read(disk, format);
            currentHeader = AmsdosHeader.parse(probe.extractRawData(existingExtents));
        }

        int existingUser = exists ? firstMatch.userNumber : 0;
        int userNumber = userOverride != null ? userOverride : existingUser;
        boolean readOnly = readOnlyOverride != null ? readOnlyOverride : (exists && firstMatch.readOnly);
        boolean hidden = hiddenOverride != null ? hiddenOverride : (exists && firstMatch.system);
        String nameOnly = nameOf(targetName);
        String extOnly = extOf(targetName);

        boolean reuseExistingHeader = typeOverride == null && exists && currentHeader.isValid();
        String defaultType = reuseExistingHeader ? null : TYPE_ASCII;
        String type = typeOverride != null ? typeOverride : defaultType;

        byte[] finalBytes;
        if (reuseExistingHeader || !TYPE_ASCII.equals(type)) {
            int fileType;
            int defaultLoad = 0;
            int defaultExec = 0;
            if (exists && currentHeader != null && currentHeader.isValid()) {
                defaultLoad = currentHeader.loadAddress;
                defaultExec = currentHeader.entryAddress;
            }
            if (reuseExistingHeader) {
                fileType = currentHeader.fileType;
            } else if (TYPE_BINARY.equals(type)) {
                fileType = AmsdosHeader.TYPE_BINARY;
            } else {
                fileType = AmsdosHeader.TYPE_BASIC;
            }
            int load = loadOverride != null ? loadOverride : defaultLoad;
            int exec = execOverride != null ? execOverride : defaultExec;
            byte[] header = AmsdosHeader.buildBytes(userNumber, nameOnly, extOnly, fileType,
                    load, newContent.length, exec);
            finalBytes = new byte[header.length + newContent.length];
            System.arraycopy(header, 0, finalBytes, 0, header.length);
            System.arraycopy(newContent, 0, finalBytes, header.length, newContent.length);
        } else {
            finalBytes = newContent;
        }
        if (finalBytes.length > MAX_FILE_SIZE) {
            throw new IOException("Fichier trop volumineux (" + finalBytes.length + " octets, max " + MAX_FILE_SIZE + ")");
        }

        // Supprime les anciennes entrées, équivalent de RemoveFile (User <- 0xE5) : leurs blocs
        // redeviennent libres pour la reconstruction du bitmap ci-dessous.
        for (int i : matchingIndices) {
            linear[i * 32] = (byte) CatalogEntry.DELETED_USER;
        }

        int reservedBlocks = (dirBytes + format.blockSize - 1) / format.blockSize;
        int maxBlock = linear.length / format.blockSize;
        boolean[] used = new boolean[maxBlock];
        for (int i = 0; i < reservedBlocks && i < maxBlock; i++) {
            used[i] = true;
        }
        for (int i = 0; i < format.directoryEntries; i++) {
            CatalogEntry e = CatalogEntry.parse(linear, i * 32);
            if (e.isDeleted()) continue;
            for (int b : e.blocks) {
                if (b >= reservedBlocks && b < maxBlock) {
                    used[b] = true;
                }
            }
        }

        int recordsPerBlock = format.blockSize / 128;
        int paddedLength = ((finalBytes.length + format.blockSize - 1) / format.blockSize) * format.blockSize;
        byte[] padded = new byte[Math.max(paddedLength, format.blockSize)];
        System.arraycopy(finalBytes, 0, padded, 0, finalBytes.length);

        int posFile = 0;
        int extentIndex = 0;
        while (posFile < finalBytes.length) {
            int dirSlot = findFreeDirEntry(linear, format);
            if (dirSlot < 0) {
                throw new IOException("Plus d'entrée de catalogue libre sur le disque");
            }
            int remaining = finalBytes.length - posFile;
            int records = Math.min((remaining + 127) / 128, 128);
            int blocksNeeded = (records + recordsPerBlock - 1) / recordsPerBlock;

            int[] entryBlocks = new int[16];
            for (int j = 0; j < blocksNeeded; j++) {
                int block = findFreeBlock(used, maxBlock, reservedBlocks);
                if (block < 0) {
                    throw new IOException("Plus de bloc libre sur le disque");
                }
                entryBlocks[j] = block;
                System.arraycopy(padded, posFile, linear, block * format.blockSize, format.blockSize);
                posFile += format.blockSize;
            }

            CatalogEntry entry = new CatalogEntry(userNumber, nameOnly, extOnly, readOnly, hidden,
                    extentIndex & 0x1F, extentIndex >> 5, records, entryBlocks);
            entry.writeTo(linear, dirSlot * 32);
            extentIndex++;
        }

        Catalog.scatterLinearStream(disk, format, linear);
        return !exists;
    }

    private static int findFreeDirEntry(byte[] linear, DiskFormat format) {
        for (int i = 0; i < format.directoryEntries; i++) {
            if (CatalogEntry.parse(linear, i * 32).isDeleted()) {
                return i;
            }
        }
        return -1;
    }

    private static int findFreeBlock(boolean[] used, int maxBlock, int reservedBlocks) {
        for (int i = reservedBlocks; i < maxBlock; i++) {
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    private static String nameOf(String targetName) {
        int dot = targetName.indexOf('.');
        return (dot >= 0 ? targetName.substring(0, dot) : targetName).toUpperCase();
    }

    private static String extOf(String targetName) {
        int dot = targetName.indexOf('.');
        return (dot >= 0 ? targetName.substring(dot + 1) : "").toUpperCase();
    }
}
