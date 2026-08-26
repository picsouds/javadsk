package dsk.amsdos;

import java.nio.charset.StandardCharsets;

/**
 * Header AMSDOS (128 octets), tel que présent en tête des fichiers BASIC /
 * BINARY / PROTECTED sur une disquette CPC.
 * Offsets (relatifs au début du header, 0x00) :
 *   0x00        user number
 *   0x01-0x08   nom de fichier (8 car.)
 *   0x09-0x0B   extension (3 car.)
 *   0x0C-0x0F   inutilisé
 *   0x10        numéro de bloc
 *   0x11        dernier bloc
 *   0x12        type de fichier (0=Basic, 1=Basic protégé, 2=Binaire)
 *   0x13-0x14   longueur de données (LE)
 *   0x15-0x16   adresse de chargement (LE)
 *   0x17        indicateur premier bloc (documenté 0xFF, mais il vaut systématiquement 0 en pratique
 *               sur un fichier disque réel, pertinent seulement pour un transfert bande/mémoire live)
 *   0x18-0x19   taille logique du fichier, header exclu (LE)
 *   0x1A-0x1B   adresse d'exécution (LE)
 *   0x1C-0x3F   inutilisé (36 octets)
 *   0x40-0x42   taille réelle du fichier sur 24 bits (LE)
 *   0x43-0x44   checksum = somme des octets [0x00..0x42] (67 octets), LE
 * Référence : <a href="https://cpctech.cpcwiki.de/docs/allhead.html">...</a> (checksum sur les 67 premiers octets).
 */
public class AmsdosHeader {

    public static final int HEADER_SIZE = 128;

    public static final int TYPE_BASIC = 0;
    public static final int TYPE_BASIC_PROTECTED = 1;
    public static final int TYPE_BINARY = 2;

    // Offsets du header, cf. le tableau en javadoc ci-dessus.
    private static final int OFF_USER = 0x00;
    private static final int OFF_FILENAME = 0x01;
    private static final int OFF_EXTENSION = 0x09;
    private static final int OFF_BLOCK_NUMBER = 0x10;
    private static final int OFF_LAST_BLOCK = 0x11;
    private static final int OFF_TYPE = 0x12;
    private static final int OFF_DATA_LENGTH = 0x13;
    private static final int OFF_LOAD_ADDRESS = 0x15;
    private static final int OFF_FIRST_BLOCK_FLAG = 0x17;
    private static final int OFF_LOGICAL_LENGTH = 0x18;
    private static final int OFF_ENTRY_ADDRESS = 0x1A;
    private static final int OFF_REAL_LENGTH24 = 0x40;
    private static final int OFF_CHECKSUM = 0x43;
    private static final int CHECKSUM_REGION_END = 0x42; // dernier octet inclus dans la somme

    public final int userNumber;
    public final String filename;   // 8 caractères, espaces retirés
    public final String extension;  // 3 caractères, espaces retirés
    public final int blockNumber;
    public final int lastBlock;
    public final int fileType;
    public final int dataLength;
    public final int loadAddress;
    public final int firstBlockFlag;
    public final int logicalLength;
    public final int entryAddress;
    public final int realLength24;
    public final int checksum;
    public final int computedChecksum;
    private final boolean longEnough;

    private AmsdosHeader(int userNumber, String filename, String extension, int blockNumber,
                          int lastBlock, int fileType, int dataLength, int loadAddress,
                          int firstBlockFlag, int logicalLength, int entryAddress,
                          int realLength24, int checksum, int computedChecksum, boolean longEnough) {
        this.userNumber = userNumber;
        this.filename = filename;
        this.extension = extension;
        this.blockNumber = blockNumber;
        this.lastBlock = lastBlock;
        this.fileType = fileType;
        this.dataLength = dataLength;
        this.loadAddress = loadAddress;
        this.firstBlockFlag = firstBlockFlag;
        this.logicalLength = logicalLength;
        this.entryAddress = entryAddress;
        this.realLength24 = realLength24;
        this.checksum = checksum;
        this.computedChecksum = computedChecksum;
        this.longEnough = longEnough;
    }

    public boolean isValid() {
        // Sans longEnough, un fichier tronqué (0 octet) est complété de zéros par parse() et son
        // checksum "0 == 0" passe par coïncidence pour un header valide.
        return longEnough && checksum == computedChecksum;
    }

    /**
     * Retire le header AMSDOS de {@code raw} s'il en a un valide (tronqué à {@code
     * logicalLength}), sinon retourne {@code raw} tel quel.
     */
    public static byte[] payloadOf(byte[] raw) {
        AmsdosHeader header = parse(raw);
        if (!header.isValid()) {
            return raw;
        }
        int len = Math.min(header.logicalLength, raw.length - HEADER_SIZE);
        byte[] payload = new byte[Math.max(len, 0)];
        System.arraycopy(raw, HEADER_SIZE, payload, 0, payload.length);
        return payload;
    }

    /**
     * Tente de parser un header AMSDOS depuis les 128 premiers octets de buf.
     * Retourne toujours un AmsdosHeader (jamais null) : vérifier isValid() pour
     * savoir si le checksum correspond réellement à un header AMSDOS.
     */
    public static AmsdosHeader parse(byte[] buf) {
        boolean longEnough = buf.length >= HEADER_SIZE;
        if (!longEnough) {
            byte[] padded = new byte[HEADER_SIZE];
            System.arraycopy(buf, 0, padded, 0, buf.length);
            buf = padded;
        }

        int computed = checksum(buf);

        int userNumber = u8(buf, OFF_USER);
        String filename = new String(buf, OFF_FILENAME, 8, StandardCharsets.US_ASCII).trim();
        String extension = new String(buf, OFF_EXTENSION, 3, StandardCharsets.US_ASCII).trim();
        int blockNumber = u8(buf, OFF_BLOCK_NUMBER);
        int lastBlock = u8(buf, OFF_LAST_BLOCK);
        int fileType = u8(buf, OFF_TYPE);
        int dataLength = u16(buf, OFF_DATA_LENGTH);
        int loadAddress = u16(buf, OFF_LOAD_ADDRESS);
        int firstBlockFlag = u8(buf, OFF_FIRST_BLOCK_FLAG);
        int logicalLength = u16(buf, OFF_LOGICAL_LENGTH);
        int entryAddress = u16(buf, OFF_ENTRY_ADDRESS);
        int realLength24 = u8(buf, OFF_REAL_LENGTH24) | (u8(buf, OFF_REAL_LENGTH24 + 1) << 8)
                | (u8(buf, OFF_REAL_LENGTH24 + 2) << 16);
        int checksum = u16(buf, OFF_CHECKSUM);

        return new AmsdosHeader(userNumber, filename, extension, blockNumber, lastBlock,
                fileType, dataLength, loadAddress, firstBlockFlag, logicalLength, entryAddress,
                realLength24, checksum, computed, longEnough);
    }

    public String fileTypeLabel() {
        switch (fileType) {
            case TYPE_BASIC: return "Basic";
            case TYPE_BASIC_PROTECTED: return "Basic protégé";
            case TYPE_BINARY: return "Binaire";
            default: return "Inconnu(" + fileType + ")";
        }
    }

    private static int u8(byte[] b, int off) {
        return b[off] & 0xFF;
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    /** Somme des octets [0x00..0x42] (67 octets), tronquée sur 16 bits , cf. offset OFF_CHECKSUM. */
    private static int checksum(byte[] buf) {
        int sum = 0;
        for (int i = 0; i <= CHECKSUM_REGION_END; i++) {
            sum += u8(buf, i);
        }
        return sum & 0xFFFF;
    }

    /**
     * Construit un header AMSDOS valide (128 octets, checksum inclus) pour {@code logicalLength} octets
     */
    public static byte[] buildBytes(int userNumber, String filename, String extension, int fileType,
                                     int loadAddress, int logicalLength, int entryAddress) {
        byte[] buf = new byte[HEADER_SIZE];
        buf[OFF_USER] = (byte) userNumber;
        writePadded(buf, OFF_FILENAME, filename, 8);
        writePadded(buf, OFF_EXTENSION, extension, 3);
        int dataLength = (logicalLength + 127) & ~127;
        buf[OFF_TYPE] = (byte) fileType;
        writeU16(buf, OFF_DATA_LENGTH, dataLength);
        writeU16(buf, OFF_LOAD_ADDRESS, loadAddress);
        buf[OFF_FIRST_BLOCK_FLAG] = (byte) 0xFF;
        writeU16(buf, OFF_LOGICAL_LENGTH, logicalLength);
        writeU16(buf, OFF_ENTRY_ADDRESS, entryAddress);
        writeU16(buf, OFF_REAL_LENGTH24, logicalLength & 0xFFFF);
        buf[OFF_REAL_LENGTH24 + 2] = (byte) ((logicalLength >> 16) & 0xFF);

        writeU16(buf, OFF_CHECKSUM, checksum(buf));
        return buf;
    }

    private static void writePadded(byte[] buf, int offset, String s, int len) {
        int n = Math.min(s.length(), len);
        for (int i = 0; i < n; i++) {
            buf[offset + i] = (byte) (s.charAt(i) & 0x7F);
        }
        for (int i = n; i < len; i++) {
            buf[offset + i] = ' ';
        }
    }

    private static void writeU16(byte[] buf, int offset, int v) {
        buf[offset] = (byte) (v & 0xFF);
        buf[offset + 1] = (byte) ((v >> 8) & 0xFF);
    }

    @Override
    public String toString() {
        return String.format("AmsdosHeader[%s.%s type=%s load=0x%04X exec=0x%04X len=%d valid=%s]",
                filename, extension, fileTypeLabel(), loadAddress, entryAddress, logicalLength, isValid());
    }
}
