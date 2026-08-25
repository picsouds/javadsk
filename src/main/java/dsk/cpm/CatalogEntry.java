package dsk.cpm;
/**
 * Une entrée de catalogue CP/M (32 octets) :
 *   0x00        user number (0-15 = valide, 0xE5 = effacé)
 *   0x01-0x08   nom de fichier (8 car., bit 7 parfois utilisé comme flag)
 *   0x09-0x0B   extension (3 car.), bit7 de chaque octet = flags (read-only / system)
 *   0x0C        extent low
 *   0x0D        inutilisé
 *   0x0E        extent high
 *   0x0F        nombre d'enregistrements (records, 128 octets chacun) dans cet extent
 *   0x10-0x1F   16 numéros de blocs alloués pour cet extent
 */
public class CatalogEntry {

    public static final int DELETED_USER = 0xE5;

    public final int userNumber;
    public final String filename;
    public final String extension;
    public final boolean readOnly;
    public final boolean system;
    public final int extentLow;
    public final int extentHigh;
    public final int recordCount;
    public final int[] blocks; // numéros de blocs (peuvent être sur 8 ou 16 bits selon DSM, ici 8 bits)

    public CatalogEntry(int userNumber, String filename, String extension, boolean readOnly,
                         boolean system, int extentLow, int extentHigh, int recordCount, int[] blocks) {
        this.userNumber = userNumber;
        this.filename = filename;
        this.extension = extension;
        this.readOnly = readOnly;
        this.system = system;
        this.extentLow = extentLow;
        this.extentHigh = extentHigh;
        this.recordCount = recordCount;
        this.blocks = blocks;
    }

    public boolean isDeleted() {
        return userNumber == DELETED_USER;
    }

    public String fullName() {
        return extension.isEmpty() ? filename : filename + "." + extension;
    }

    public int extentNumber() {
        return extentLow + (extentHigh << 5);
    }

    public static CatalogEntry parse(byte[] buf, int offset) {
        int userNumber = buf[offset] & 0xFF;
        char[] nameChars = new char[8];
        for (int i = 0; i < 8; i++) {
            nameChars[i] = (char) (buf[offset + 1 + i] & 0x7F);
        }
        String filename = new String(nameChars).trim();

        boolean readOnly = (buf[offset + 9] & 0x80) != 0;
        boolean system = (buf[offset + 10] & 0x80) != 0;
        char[] extChars = new char[3];
        for (int i = 0; i < 3; i++) {
            extChars[i] = (char) (buf[offset + 9 + i] & 0x7F);
        }
        String extension = new String(extChars).trim();

        int extentLow = buf[offset + 0x0C] & 0xFF;
        int extentHigh = buf[offset + 0x0E] & 0xFF;
        int recordCount = buf[offset + 0x0F] & 0xFF;

        int[] blocks = new int[16];
        for (int i = 0; i < 16; i++) {
            blocks[i] = buf[offset + 0x10 + i] & 0xFF;
        }

        return new CatalogEntry(userNumber, filename, extension, readOnly, system,
                extentLow, extentHigh, recordCount, blocks);
    }

    /** Sérialise l'entrée dans son emplacement de 32 octets, inverse de {@link #parse}. */
    public void writeTo(byte[] buf, int offset) {
        buf[offset] = (byte) userNumber;
        for (int i = 0; i < 8; i++) {
            buf[offset + 1 + i] = paddedChar(filename, i);
        }
        buf[offset + 9] = (byte) (paddedChar(extension, 0) | (readOnly ? 0x80 : 0));
        buf[offset + 10] = (byte) (paddedChar(extension, 1) | (system ? 0x80 : 0));
        buf[offset + 11] = paddedChar(extension, 2);
        buf[offset + 0x0C] = (byte) extentLow;
        buf[offset + 0x0D] = 0;
        buf[offset + 0x0E] = (byte) extentHigh;
        buf[offset + 0x0F] = (byte) recordCount;
        for (int i = 0; i < 16; i++) {
            buf[offset + 0x10 + i] = (byte) blocks[i];
        }
    }

    private static byte paddedChar(String s, int index) {
        return (byte) ((index < s.length()) ? (s.charAt(index) & 0x7F) : ' ');
    }

    @Override
    public String toString() {
        return String.format("%02d %-8s.%-3s extent=%d records=%d%s%s",
                userNumber, filename, extension, extentNumber(), recordCount,
                readOnly ? " RO" : "", system ? " SYS" : "");
    }
}
