package dsk.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Construit en mémoire une image .dsk standard (non extended) minimale,
 * avec une seule piste/face, pour les tests. La piste contient 9 secteurs
 * de 512 octets (format "Data"), ce qui permet d'y loger un catalogue CP/M
 * complet (2048 octets) suivi d'un bloc de données.
 */
public final class DskImageBuilder {

    public static final int SECTOR_SIZE = 512;
    public static final int SECTORS_PER_TRACK = 9;
    public static final int FIRST_SECTOR_ID = 0xC1;
    public static final int TRACK_DATA_SIZE = SECTOR_SIZE * SECTORS_PER_TRACK; // 4608
    public static final int TRACK_SIZE = 256 + TRACK_DATA_SIZE; // 4864

    private final byte[][] sectors = new byte[SECTORS_PER_TRACK][SECTOR_SIZE];

    /** Écrit {@code data} dans le flux linéaire de la piste à partir de l'offset donné. */
    public DskImageBuilder writeAt(int offset, byte[] data) {
        int pos = offset;
        for (byte b : data) {
            sectors[pos / SECTOR_SIZE][pos % SECTOR_SIZE] = b;
            pos++;
        }
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] dib = new byte[256];
        writeAscii(dib, 0, "MV - CPCEMU Disk-File\r\nDisk-Info\r\n");
        writeAscii(dib, 0x22, "javadsk-tests");
        dib[0x30] = 1; // 1 piste
        dib[0x31] = 1; // 1 face
        writeU16le(dib, 0x32, TRACK_SIZE);
        out.writeBytes(dib);

        byte[] tib = new byte[256];
        writeAscii(tib, 0, "Track-Info\r\n");
        tib[0x10] = 0; // trackNumber
        tib[0x11] = 0; // side
        tib[0x12] = 0; // dataRate
        tib[0x13] = 0; // recordingMode
        tib[0x14] = 2; // sectorSizeCode -> 0x80<<2 = 512
        tib[0x15] = (byte) SECTORS_PER_TRACK;
        tib[0x16] = 0x4E; // gap3
        tib[0x17] = (byte) 0xE5; // filler
        for (int s = 0; s < SECTORS_PER_TRACK; s++) {
            int e = 0x18 + s * 8;
            tib[e] = 0;      // track
            tib[e + 1] = 0;  // side
            tib[e + 2] = (byte) (FIRST_SECTOR_ID + s); // sector id
            tib[e + 3] = 2;  // size code
            tib[e + 4] = 0;  // st1
            tib[e + 5] = 0;  // st2
            writeU16le(tib, e + 6, SECTOR_SIZE); // longueur déclarée (ignorée en non-extended)
        }
        out.writeBytes(tib);

        for (byte[] sector : sectors) {
            out.writeBytes(sector);
        }

        return out.toByteArray();
    }

    private static void writeAscii(byte[] buf, int offset, String s) {
        byte[] a = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(a, 0, buf, offset, Math.min(a.length, buf.length - offset));
    }

    private static void writeU16le(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
