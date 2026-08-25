package dsk.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit en mémoire une image Extended DSK (EDSK) minimale, piste par piste, pour tester la
 * table de tailles variable, les pistes absentes (size=0) et la longueur de secteur déclarée
 * (par opposition au non-extended, où elle est déduite du sizeCode).
 */
public final class EdskImageBuilder {

    private final List<byte[]> trackBlocks = new ArrayList<>(); // null = piste absente (size=0)
    private int numberOfSides = 1;

    public EdskImageBuilder sides(int n) {
        this.numberOfSides = n;
        return this;
    }

    /** Ajoute une piste absente (non formatée) : taille 0 dans la table, aucun Track-Info émis. */
    public EdskImageBuilder emptyTrack() {
        trackBlocks.add(null);
        return this;
    }

    /**
     * Ajoute une piste avec un seul secteur dont la longueur déclarée peut différer de celle
     * induite par sizeCode (comme observé sur des disquettes réelles, ex. Discology).
     */
    public EdskImageBuilder track(int trackNumber, int side, int sectorId, int sizeCode,
                                   int declaredLength, byte[] data) {
        byte[] tib = new byte[256];
        writeAscii(tib, 0, "Track-Info\r\n");
        tib[0x10] = (byte) trackNumber;
        tib[0x11] = (byte) side;
        tib[0x14] = (byte) sizeCode;
        tib[0x15] = 1; // un seul secteur, suffisant pour le test
        tib[0x16] = 0x4E;
        tib[0x17] = (byte) 0xE5;
        tib[0x18] = (byte) trackNumber;
        tib[0x18 + 1] = (byte) side;
        tib[0x18 + 2] = (byte) sectorId;
        tib[0x18 + 3] = (byte) sizeCode;
        writeU16le(tib, 0x18 + 6, declaredLength);

        ByteArrayOutputStream block = new ByteArrayOutputStream();
        block.writeBytes(tib);
        byte[] payload = new byte[declaredLength];
        System.arraycopy(data, 0, payload, 0, Math.min(data.length, declaredLength));
        block.writeBytes(payload);

        trackBlocks.add(block.toByteArray());
        return this;
    }

    public byte[] build() {
        int totalTracks = trackBlocks.size();

        byte[] dib = new byte[256];
        writeAscii(dib, 0, "EXTENDED CPC DSK File\r\nDisk-Info\r\n");
        writeAscii(dib, 0x22, "javadsk-tests");
        dib[0x30] = (byte) (totalTracks / numberOfSides);
        dib[0x31] = (byte) numberOfSides;
        for (int i = 0; i < totalTracks; i++) {
            byte[] blk = trackBlocks.get(i);
            int size256 = (blk == null) ? 0 : (blk.length + 255) / 256;
            dib[0x34 + i] = (byte) size256;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(dib);
        for (byte[] blk : trackBlocks) {
            if (blk == null) continue; // piste absente : aucun octet, taille 0 dans la table
            // pad jusqu'au multiple de 256 déclaré dans la table (comme un vrai fichier EDSK)
            int size256 = (blk.length + 255) / 256;
            byte[] padded = new byte[size256 * 256];
            System.arraycopy(blk, 0, padded, 0, blk.length);
            out.writeBytes(padded);
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
