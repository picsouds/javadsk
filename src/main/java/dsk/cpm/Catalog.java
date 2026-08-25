package dsk.cpm;

import dsk.DiskImage;
import dsk.SectorInfo;
import dsk.TrackInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class Catalog {

    public final List<CatalogEntry> entries;
    private final byte[] linearData; // flux linéaire de la zone de données (hors pistes réservées)
    private final DiskFormat format;

    private Catalog(List<CatalogEntry> entries, byte[] linearData, DiskFormat format) {
        this.entries = entries;
        this.linearData = linearData;
        this.format = format;
    }

    public static Catalog read(DiskImage disk, DiskFormat format) throws IOException {
        byte[] linear = buildLinearStream(disk, format);

        int dirBytes = format.directoryEntries * 32;
        if (linear.length < dirBytes) {
            throw new IOException("Flux de données trop court pour contenir le catalogue");
        }

        // nombre max d'enregistrements (128 octets) par extent : 16 emplacements de bloc,
        // chacun couvrant blockSize/128 enregistrements (128 pour blockSize=1024).
        int maxRecordsPerExtent = (format.blockSize / 128) * 16;

        List<CatalogEntry> entries = new ArrayList<>();
        for (int i = 0; i < format.directoryEntries; i++) {
            CatalogEntry e = CatalogEntry.parse(linear, i * 32);
            if (!e.isDeleted() && !e.filename.isEmpty() && e.recordCount <= maxRecordsPerExtent) {
                entries.add(e);
            }
        }
        return new Catalog(entries, linear, format);
    }

    /** Regroupe les entrées de catalogue par nom de fichier complet (un fichier peut avoir plusieurs extents). */
    public Map<String, List<CatalogEntry>> filesByName() {
        Map<String, List<CatalogEntry>> map = new LinkedHashMap<>();
        for (CatalogEntry e : entries) {
            map.computeIfAbsent(e.fullName(), k -> new ArrayList<>()).add(e);
        }
        for (List<CatalogEntry> list : map.values()) {
            list.sort(Comparator.comparingInt(CatalogEntry::extentNumber));
        }
        return map;
    }

    /** Reconstitue les données brutes (header AMSDOS inclus s'il y en a un) à partir des extents ordonnés d'un fichier. */
    public byte[] extractRawData(List<CatalogEntry> fileExtents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int recordsPerBlock = format.blockSize / 128;
        for (CatalogEntry extent : fileExtents) {
            int blocksNeeded = (extent.recordCount + recordsPerBlock - 1) / recordsPerBlock;
            blocksNeeded = Math.min(blocksNeeded, extent.blocks.length);
            for (int j = 0; j < blocksNeeded; j++) {
                int block = extent.blocks[j];
                if (block == 0) continue; // emplacement non alloué dans cet extent partiel
                int blockOffset = block * format.blockSize;
                if (blockOffset < 0 || blockOffset >= linearData.length) {
                    continue; // référence hors limites : catalogue corrompu/protégé, vu en réel
                }
                int len = format.blockSize;
                if (blockOffset + len > linearData.length) {
                    len = linearData.length - blockOffset;
                }
                out.write(linearData, blockOffset, len);
            }
        }
        return out.toByteArray();
    }

    // Partagé par buildLinearStream/scatterLinearStream : DOIVENT parcourir pistes/secteurs dans le
    // même ordre, sous peine de désaligner le flux linéaire entre lecture et écriture.
    private static List<TrackInfo> dataTracksInOrder(DiskImage disk, DiskFormat format) {
        List<TrackInfo> side0 = new ArrayList<>();
        for (TrackInfo t : disk.getTracks()) {
            if (t.side == 0) side0.add(t);
        }
        side0.sort(Comparator.comparingInt(a -> a.trackNumber));

        List<TrackInfo> result = new ArrayList<>();
        int skipped = 0;
        for (TrackInfo t : side0) {
            if (skipped < format.reservedTracks) {
                skipped++;
                continue;
            }
            result.add(t);
        }
        return result;
    }

    static byte[] buildLinearStream(DiskImage disk, DiskFormat format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TrackInfo t : dataTracksInOrder(disk, format)) {
            List<SectorInfo> sectors = new ArrayList<>(t.getSectors());
            sectors.sort(Comparator.comparingInt(a -> a.sectorId));
            for (SectorInfo s : sectors) {
                byte[] data = s.getData();
                out.write(data, 0, Math.min(data.length, format.sectorSize));
            }
        }
        return out.toByteArray();
    }

    /** Inverse de {@link #buildLinearStream} : mute directement les tableaux de données des secteurs. */
    static void scatterLinearStream(DiskImage disk, DiskFormat format, byte[] linear) {
        int pos = 0;
        for (TrackInfo t : dataTracksInOrder(disk, format)) {
            List<SectorInfo> sectors = new ArrayList<>(t.getSectors());
            sectors.sort(Comparator.comparingInt(a -> a.sectorId));
            for (SectorInfo s : sectors) {
                byte[] data = s.getData();
                int len = Math.min(data.length, format.sectorSize);
                System.arraycopy(linear, pos, data, 0, len);
                pos += len;
            }
        }
    }
}
