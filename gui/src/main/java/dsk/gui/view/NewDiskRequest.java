package dsk.gui.view;

import dsk.cpm.DiskFormat;

import java.nio.file.Path;

public final class NewDiskRequest {

    private final Path outPath;
    private final String formatName;
    private final DiskFormat format;
    private final int tracks;

    public NewDiskRequest(Path outPath, String formatName, DiskFormat format, int tracks) {
        this.outPath = outPath;
        this.formatName = formatName;
        this.format = format;
        this.tracks = tracks;
    }

    public Path outPath() {
        return outPath;
    }

    public String formatName() {
        return formatName;
    }

    public DiskFormat format() {
        return format;
    }

    public int tracks() {
        return tracks;
    }
}
