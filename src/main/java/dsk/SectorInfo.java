package dsk;

/**
 * Une entrée de la "Sector Information List" d'une piste (8 octets dans le
 * fichier DSK), accompagnée des données réelles du secteur.
 * Référence format : <a href="https://cpctech.cpcwiki.de/docs/dsk.html">...</a> et
 * <a href="https://cpctech.cpcwiki.de/docs/extdsk.html">...</a>
 */
public class SectorInfo {

    public final int track;        // numéro de piste (C)
    public final int side;         // numéro de face (H)
    public final int sectorId;     // ID logique du secteur (R) : ex. 0xC1..0xC9 (data), 0x41..0x49 (system)
    public final int sizeCode;     // N : taille = 0x80 << N (0 = 128, 1 = 256, 2 = 512, ...)
    public final int fdcStatus1;
    public final int fdcStatus2;
    public final int declaredLength; // longueur déclarée (EDSK) ou déduite de sizeCode (DSK standard)

    private byte[] data; // données réelles du secteur, renseignées après lecture

    public SectorInfo(int track, int side, int sectorId, int sizeCode,
                       int fdcStatus1, int fdcStatus2, int declaredLength) {
        this.track = track;
        this.side = side;
        this.sectorId = sectorId;
        this.sizeCode = sizeCode;
        this.fdcStatus1 = fdcStatus1;
        this.fdcStatus2 = fdcStatus2;
        this.declaredLength = declaredLength;
    }

    public int sizeFromCode() {
        return 0x80 << sizeCode;
    }

    void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public boolean hasCrcError() {
        // bit5 de ST1 ou bit5 de ST2 = erreur CRC (voir doc FDC uPD765)
        return (fdcStatus1 & 0x20) != 0 || (fdcStatus2 & 0x20) != 0;
    }

    @Override
    public String toString() {
        return String.format("Sector[track=%d side=%d id=0x%02X size=%d len=%d]",
                track, side, sectorId, sizeFromCode(), data == null ? -1 : data.length);
    }
}
