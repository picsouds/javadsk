package dsk;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lecture/écriture d'une image disquette Amstrad CPC au format CPCEMU / EXTENDED CPC DSK.
 * Signature distinctive :
 *  - "MV - CPCEMU Disk-File..."  -> DSK standard (toutes les pistes ont la même taille)
 *  - "EXTENDED CPC DSK File..."  -> Extended DSK (taille par piste variable, pistes "muettes")
 */
public class DiskImage {

    private static final String STD_SIGNATURE = "MV - CPCEMU";
    private static final String EXT_SIGNATURE = "EXTENDED";

    // Disk Information Block (en tête du fichier).
    private static final int DISK_INFO_SIZE = 256;
    private static final int OFF_CREATOR = 0x22;
    private static final int CREATOR_SIZE = 14;
    private static final int OFF_NB_TRACKS = 0x30;
    private static final int OFF_NB_SIDES = 0x31;
    private static final int OFF_TRACK_SIZE = 0x32;        // standard : taille unique de piste (u16le)
    private static final int OFF_TRACK_SIZE_TABLE = 0x34;  // extended : table de tailles par piste
    private static final int STD_TRACK_SIZE_TABLE_SIZE = 0xCC; // remplissage du DIB, ignoré en standard

    // Signature + "Disk-Info" du DIB, précalculées une seule fois plutôt qu'à chaque write().
    private static final byte[] STD_HEADER_SIG = fixedAscii("MV - CPCEMU Disk-File\r\nDisk-Info\r\n", OFF_CREATOR);
    private static final byte[] EXT_HEADER_SIG = fixedAscii("EXTENDED CPC DSK File\r\nDisk-Info\r\n", OFF_CREATOR);

    // Track Information Block (en tête de chaque piste).
    private static final int TRACK_INFO_SIZE = 256;
    private static final int OFF_SECTOR_LIST = 0x18; // relatif au début de la piste
    private static final int MAX_SECTORS = 29;        // capacité de la liste de secteurs du TIB

    public final boolean extended;
    public final String creator;
    public final int numberOfTracks;
    public final int numberOfSides;

    private final List<TrackInfo> tracks;

    private DiskImage(boolean extended, String creator, int numberOfTracks, int numberOfSides) {
        this.extended = extended;
        this.creator = creator;
        this.numberOfTracks = numberOfTracks;
        this.numberOfSides = numberOfSides;
        this.tracks = new ArrayList<>(numberOfTracks * numberOfSides);
    }

    public static DiskImage read(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return parse(raw);
    }

    public static DiskImage parse(byte[] raw) throws IOException {
        boolean extended = isExtended(raw);

        String creator = new String(raw, OFF_CREATOR, CREATOR_SIZE, StandardCharsets.US_ASCII).trim();
        int nbTracks = u8(raw, OFF_NB_TRACKS);
        int nbSides = u8(raw, OFF_NB_SIDES);

        DiskImage disk = new DiskImage(extended, creator, nbTracks, nbSides);

        int[] trackSizes = new int[nbTracks * nbSides];
        if (extended) {
            // table par piste à partir de OFF_TRACK_SIZE_TABLE, 1 octet chacune, valeur = taille/256.
            for (int i = 0; i < trackSizes.length; i++) {
                int v = u8(raw, OFF_TRACK_SIZE_TABLE + i);
                trackSizes[i] = v * 256;
            }
        } else {
            int trackSize = u16le(raw, OFF_TRACK_SIZE);
            Arrays.fill(trackSizes, trackSize);
        }

        int offset = DISK_INFO_SIZE;
        for (int i = 0; i < trackSizes.length; i++) {
            int size = trackSizes[i];
            if (size == 0) {
                // piste non formatée / absente (courant en EDSK pour les pistes vides)
                continue;
            }
            if (offset + size > raw.length) {
                throw new IOException("Piste " + i + " dépasse la taille du fichier (offset="
                        + offset + ", size=" + size + ", fileLen=" + raw.length + ")");
            }
            TrackInfo track = parseTrack(raw, offset, extended);
            disk.tracks.add(track);
            offset += size;
        }

        return disk;
    }

    private static boolean isExtended(byte[] raw) throws IOException {
        if (raw.length < DISK_INFO_SIZE) {
            throw new IOException("Fichier trop court pour être un .dsk valide");
        }
        String signature = new String(raw, 0, 22, StandardCharsets.US_ASCII);
        boolean extended;
        if (signature.startsWith(STD_SIGNATURE)) {
            extended = false;
        } else if (signature.startsWith(EXT_SIGNATURE)) {
            extended = true;
        } else {
            throw new IOException("Signature DSK inconnue : " + signature.trim());
        }
        return extended;
    }

    /**
     * Image DSK vierge, pistes bourrées de 0xE5 (un catalogue CP/M rempli de 0xE5 est déjà valide),
     * entrelacement "+4" standard CPC. Port de {@code FormatDsk}/{@code FormatTrack} (dsk/dsk.go).
     */
    public static DiskImage formatted(String creator, int numberOfTracks, int sectorSize,
                                       int sectorsPerTrack, int firstSectorId) {
        DiskImage disk = new DiskImage(false, creator, numberOfTracks, 1);
        int sizeCode = 0;
        while ((0x80 << sizeCode) < sectorSize) {
            sizeCode++;
        }
        for (int trackNumber = 0; trackNumber < numberOfTracks; trackNumber++) {
            disk.tracks.add(formatTrack(trackNumber, sectorSize, sectorsPerTrack, firstSectorId, sizeCode));
        }
        return disk;
    }

    private static TrackInfo formatTrack(int trackNumber, int sectorSize, int sectorsPerTrack,
                                          int firstSectorId, int sizeCode) {
        TrackInfo track = new TrackInfo(trackNumber, 0, sizeCode, 0x4E, 0xE5, 0, 0, sectorsPerTrack);
        int ss = 0;
        int s = 0;
        while (s < sectorsPerTrack) {
            addFormattedSector(track, trackNumber, ss + firstSectorId, sizeCode, sectorSize);
            ss++;
            s++;
            if (s < sectorsPerTrack) {
                addFormattedSector(track, trackNumber, ss + firstSectorId + 4, sizeCode, sectorSize);
                s++;
            }
        }
        return track;
    }

    private static void addFormattedSector(TrackInfo track, int trackNumber, int sectorId, int sizeCode, int sectorSize) {
        SectorInfo sector = new SectorInfo(trackNumber, 0, sectorId, sizeCode, 0, 0, sectorSize);
        byte[] data = new byte[sectorSize];
        Arrays.fill(data, (byte) 0xE5);
        sector.setData(data);
        track.addSector(sector);
    }

    private static TrackInfo parseTrack(byte[] raw, int base, boolean extended) throws IOException {
        if (base + TRACK_INFO_SIZE > raw.length) {
            throw new IOException("Bloc d'information de piste hors limites à l'offset " + base);
        }
        String sig = new String(raw, base, 12, StandardCharsets.US_ASCII);
        if (!sig.startsWith("Track-Info")) {
            throw new IOException("Signature de piste invalide à l'offset " + base + " : " + sig.trim());
        }
        int trackNumber = u8(raw, base + 0x10);
        int side = u8(raw, base + 0x11);
        int dataRate = u8(raw, base + 0x12);       // extended seulement, 0 sinon
        int recordingMode = u8(raw, base + 0x13);  // extended seulement, 0 sinon
        int sectorSizeCode = u8(raw, base + 0x14);
        int numberOfSectors = u8(raw, base + 0x15);
        int gap3Length = u8(raw, base + 0x16);
        int fillerByte = u8(raw, base + 0x17);

        TrackInfo track = new TrackInfo(trackNumber, side, sectorSizeCode, gap3Length,
                fillerByte, dataRate, recordingMode, numberOfSectors);

        int sectorListBase = base + OFF_SECTOR_LIST;
        int dataOffset = base + TRACK_INFO_SIZE;

        for (int s = 0; s < numberOfSectors; s++) {
            int e = sectorListBase + s * 8;
            int sTrack = u8(raw, e);
            int sSide = u8(raw, e + 1);
            int sId = u8(raw, e + 2);
            int sizeCode = u8(raw, e + 3);
            int st1 = u8(raw, e + 4);
            int st2 = u8(raw, e + 5);
            int declared = u16le(raw, e + 6); // longueur réelle, fiable seulement en EDSK
            int len = extended ? declared : (0x80 << sizeCode);

            if (dataOffset + len > raw.length) {
                throw new IOException("Données de secteur hors limites (piste " + trackNumber + ")");
            }
            byte[] data = Arrays.copyOfRange(raw, dataOffset, dataOffset + len);
            SectorInfo sector = new SectorInfo(sTrack, sSide, sId, sizeCode, st1, st2, len);
            sector.setData(data);
            track.addSector(sector);
            dataOffset += len;
        }

        return track;
    }

    public List<TrackInfo> getTracks() {
        return tracks;
    }

    /**
     * Inverse de {@link #parse}. Recalcule la table des tailles de piste depuis les données
     * actuelles plutôt que de la conserver telle que lue.
     */
    public void write(OutputStream rawOut) throws IOException {
        DataOutputStream out = new DataOutputStream(rawOut);

        out.write(extended ? EXT_HEADER_SIG : STD_HEADER_SIG);
        out.write(fixedAscii(creator, CREATOR_SIZE));

        out.writeByte(numberOfTracks);
        out.writeByte(numberOfSides);

        int[] trackByteSizes = new int[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            trackByteSizes[i] = trackTotalSize(tracks.get(i));
        }

        // Purement informatif en extended (la table par piste juste après fait foi). En standard,
        // c'est la taille unique que parse() impose à toutes les pistes : on prend le max, pas
        // trackByteSizes[0], au cas où la piste 0 ne serait pas la plus grande.
        int dataSizeField = 0;
        for (int size : trackByteSizes) {
            dataSizeField = Math.max(dataSizeField, size);
        }
        writeU16LE(out, dataSizeField);

        byte[] trackSizeTable;
        if (extended) {
            trackSizeTable = new byte[tracks.size()];
            for (int i = 0; i < tracks.size(); i++) {
                trackSizeTable[i] = (byte) (trackByteSizes[i] / 256);
            }
        } else {
            trackSizeTable = new byte[STD_TRACK_SIZE_TABLE_SIZE]; // non utilisée à la lecture
        }
        out.write(trackSizeTable);

        if (extended) {
            int padLen = DISK_INFO_SIZE - (52 + trackSizeTable.length);
            if (padLen > 0) {
                out.write(new byte[padLen]);
            }
        }

        for (int i = 0; i < tracks.size(); i++) {
            TrackInfo t = tracks.get(i);
            writeTrack(out, t);
            if (!extended) {
                // Format standard : toutes les pistes doivent faire la même taille sur disque (cf.
                // javadoc de classe) - une piste vide ne fait naturellement que 256 octets sans ça.
                int pad = dataSizeField - trackByteSizes[i];
                if (pad > 0) {
                    byte[] padding = new byte[pad];
                    Arrays.fill(padding, (byte) t.fillerByte);
                    out.write(padding);
                }
            }
        }
        out.flush();
    }

    public void writeToFile(Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            write(out);
        }
    }

    private static int trackTotalSize(TrackInfo t) {
        int size = TRACK_INFO_SIZE;
        for (SectorInfo s : t.getSectors()) {
            size += s.getData().length;
        }
        return size;
    }

    private static void writeTrack(DataOutputStream out, TrackInfo t) throws IOException {
        out.write(fixedAscii("Track-Info\r\n", 0x10));
        out.writeByte(t.trackNumber);
        out.writeByte(t.side);
        out.writeByte(t.dataRate);
        out.writeByte(t.recordingMode);
        out.writeByte(t.sectorSizeCode);

        List<SectorInfo> sectors = t.getSectors();
        out.writeByte(sectors.size());
        out.writeByte(t.gap3Length);
        out.writeByte(t.fillerByte);

        for (int i = 0; i < MAX_SECTORS; i++) {
            if (i < sectors.size()) {
                SectorInfo s = sectors.get(i);
                out.writeByte(s.track);
                out.writeByte(s.side);
                out.writeByte(s.sectorId);
                out.writeByte(s.sizeCode);
                out.writeByte(s.fdcStatus1);
                out.writeByte(s.fdcStatus2);
                writeU16LE(out, s.declaredLength);
            } else {
                out.write(new byte[8]);
            }
        }
        for (SectorInfo s : sectors) {
            out.write(s.getData());
        }
    }

    public TrackInfo findTrack(int trackNumber, int side) {
        for (TrackInfo t : tracks) {
            if (t.trackNumber == trackNumber && t.side == side) return t;
        }
        return null;
    }

    private static int u8(byte[] b, int off) {
        return b[off] & 0xFF;
    }

    private static int u16le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static void writeU16LE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value);
        out.writeByte(value >>> 8);
    }

    /** Chaîne ASCII tronquée/complétée de zéros à exactement {@code len} octets. */
    private static byte[] fixedAscii(String s, int len) {
        byte[] out = new byte[len];
        byte[] src = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        return out;
    }

    @Override
    public String toString() {
        return String.format("DiskImage[%s tracks=%d sides=%d creator='%s']",
                extended ? "EDSK" : "DSK", numberOfTracks, numberOfSides, creator);
    }
}
