package dsk.gui.service;

import java.nio.file.Path;

public final class PutRequest {

    private final Path localFile;
    private final String targetName;
    private final String typeOverride;
    private final boolean tokenize;
    private final Integer load;
    private final Integer exec;
    private final Boolean readOnly;
    private final Boolean hidden;
    private final Path outPath;

    public PutRequest(Path localFile, String targetName, String typeOverride, boolean tokenize,
                       Integer load, Integer exec, Boolean readOnly, Boolean hidden, Path outPath) {
        this.localFile = localFile;
        this.targetName = targetName;
        this.typeOverride = typeOverride;
        this.tokenize = tokenize;
        this.load = load;
        this.exec = exec;
        this.readOnly = readOnly;
        this.hidden = hidden;
        this.outPath = outPath;
    }

    public Path localFile() {
        return localFile;
    }

    public String targetName() {
        return targetName;
    }

    public String typeOverride() {
        return typeOverride;
    }

    public boolean tokenize() {
        return tokenize;
    }

    public Integer load() {
        return load;
    }

    public Integer exec() {
        return exec;
    }

    public Boolean readOnly() {
        return readOnly;
    }

    public Boolean hidden() {
        return hidden;
    }

    public Path outPath() {
        return outPath;
    }
}
