package dsk.gui.service;

import dsk.DiskImage;
import dsk.basic.BasicTokenizer;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PutService {

    // Classe classique et pas record : le jar GUI cible Java 11, les records (Java 16+) n'y sont pas disponibles.
    public static final class Result {
        private final boolean created;
        private final int contentLength;

        Result(boolean created, int contentLength) {
            this.created = created;
            this.contentLength = contentLength;
        }

        public boolean created() {
            return created;
        }

        public int contentLength() {
            return contentLength;
        }
    }

    @SuppressWarnings("PathTraversal")
    public Result put(Path dskPath, PutRequest request) throws IOException {
        byte[] content = Files.readAllBytes(request.localFile());
        if (request.tokenize()) {
            // UTF-8 : symétrique de 'basic --spaced' (cf. PutCommand).
            content = BasicTokenizer.tokenizeProgram(new String(content, StandardCharsets.UTF_8));
        }
        DiskImage disk = DiskImage.read(dskPath);
        boolean created = CatalogWriter.putFile(disk, DiskFormat.DATA, request.targetName(), content,
                null, request.readOnly(), request.hidden(), request.load(), request.exec(), request.typeOverride());
        disk.writeToFile(request.outPath());
        return new Result(created, content.length);
    }
}
