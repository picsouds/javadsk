package dsk.cpm;

/**
 * Paramètres CP/M nécessaires pour localiser le catalogue et les blocs de
 * données sur une disquette CPC. Ce sont les valeurs du DPB (Disk Parameter
 * Block) utilisées par AMSDOS pour les formats les plus courants.
 * Référence : <a href="https://cpctech.cpcwiki.de/docs/amsdos.asm">...</a> (désassemblage ROM AMSDOS, table XDPB)
 */
public class DiskFormat {

    public final int sectorSize;      // en octets (512 pour Data/System/IBM standard)
    public final int sectorsPerTrack; // 9 pour Data/System, 8 pour IBM
    public final int firstSectorId;   // 0xC1 pour Data, 0x41 pour System, 0x01 pour IBM
    public final int reservedTracks;  // pistes réservées avant la zone de données (0 Data, 2 System, 1 IBM)
    public final int blockSize;       // taille de bloc d'allocation, 1024 pour ces formats
    public final int directoryEntries; // nombre d'entrées de catalogue (63 -> DRM=63 donc 64 entrées)

    public DiskFormat(int sectorSize, int sectorsPerTrack, int firstSectorId,
                       int reservedTracks, int blockSize, int directoryEntries) {
        this.sectorSize = sectorSize;
        this.sectorsPerTrack = sectorsPerTrack;
        this.firstSectorId = firstSectorId;
        this.reservedTracks = reservedTracks;
        this.blockSize = blockSize;
        this.directoryEntries = directoryEntries;
    }

    /** Format "Data" : le plus courant pour les disquettes de développement (180 Ko, pas de piste réservée). */
    public static final DiskFormat DATA = new DiskFormat(512, 9, 0xC1, 0, 1024, 64);

    /** Format "System" : 2 pistes réservées pour le chargeur CP/M. */
    public static final DiskFormat SYSTEM = new DiskFormat(512, 9, 0x41, 2, 1024, 64);

    /** Format "IBM" (compatibilité CP/M générique) : 1 piste réservée, 8 secteurs/piste. */
    public static final DiskFormat IBM = new DiskFormat(512, 8, 0x01, 1, 1024, 64);
}
