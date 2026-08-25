package dsk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Représente une piste : le "Track Information Block" (256 octets) suivi
 * de la liste des secteurs et de leurs données.
 */
public class TrackInfo {

    public final int trackNumber;
    public final int side;
    public final int sectorSizeCode;
    public final int gap3Length;
    public final int fillerByte;
    public final int dataRate;   // extended DSK seulement (0 = inconnu/non précisé)
    public final int recordingMode; // extended DSK seulement

    private final List<SectorInfo> sectors;

    public TrackInfo(int trackNumber, int side, int sectorSizeCode,
                      int gap3Length, int fillerByte, int dataRate, int recordingMode, int sectorCount) {
        this.trackNumber = trackNumber;
        this.side = side;
        this.sectorSizeCode = sectorSizeCode;
        this.gap3Length = gap3Length;
        this.fillerByte = fillerByte;
        this.dataRate = dataRate;
        this.recordingMode = recordingMode;
        this.sectors = new ArrayList<>(sectorCount);
    }

    void addSector(SectorInfo s) {
        sectors.add(s);
    }

    /** Vue non modifiable : la liste réelle ne doit être touchée que via {@link #addSector}. */
    public List<SectorInfo> getSectors() {
        return Collections.unmodifiableList(sectors);
    }

    public SectorInfo getSectorById(int sectorId) {
        for (SectorInfo s : sectors) {
            if (s.sectorId == sectorId) return s;
        }
        return null;
    }
}
