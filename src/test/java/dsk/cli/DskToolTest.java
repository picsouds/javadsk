package dsk.cli;

import dsk.DiskImage;
import dsk.basic.BasicTokenizer;
import dsk.cpm.CatalogWriter;
import dsk.cpm.DiskFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests "fumée" du câblage picocli (dsk.cli) : chaque sous-commande est invoquée de bout en bout
 * sur un vrai fichier .dsk sur disque, pas juste sa logique métier (déjà couverte par les tests des
 * autres packages). But : attraper une régression de mapping {@code @Parameters}/{@code @Option}
 * (index, type, --entry...) comme celles qu'aurait pu introduire silencieusement les refactors
 * AbstractReadCommand/AbstractTargetFileCommand, pas mesurer la logique CP/M/AMSDOS elle-même.
 */
class DskToolTest {

    @TempDir
    Path tempDir;

    private Path dskPath;

    @BeforeEach
    void setUp() throws IOException {
        DiskImage disk = DiskImage.formatted("test", 40, 512, 9, 0xC1);
        byte[] tokenized = BasicTokenizer.tokenizeProgram("10 PRINT\"HELLO\"\n");
        CatalogWriter.putFile(disk, DiskFormat.DATA, "PROG.BAS", tokenized,
                null, null, null, null, null, "basic");
        CatalogWriter.putFile(disk, DiskFormat.DATA, "PLAIN.TXT", "salut".getBytes(StandardCharsets.US_ASCII),
                null, null, null, null, null, "ascii");
        dskPath = tempDir.resolve("test.dsk");
        disk.writeToFile(dskPath);
    }

    @Test
    void listShowsBothFilesWithTheirType() {
        Result r = run("list", dskPath.toString());
        assertEquals(0, r.exitCode);
        assertTrue(r.text().contains("PROG.BAS"));
        assertTrue(r.text().contains("Basic"));
        assertTrue(r.text().contains("PLAIN.TXT"));
        assertTrue(r.text().contains("sans header"));
    }

    @Test
    void headerPrintsAllAmsdosFieldsForAValidHeader() {
        Result r = run("header", dskPath.toString(), "PROG.BAS");
        assertEquals(0, r.exitCode);
        assertTrue(r.text().contains("Checksum"));
        assertTrue(r.text().contains("valide"));
        assertTrue(r.text().contains("Basic"));
    }

    @Test
    void headerOnHeaderlessFileSaysSo() {
        Result r = run("header", dskPath.toString(), "PLAIN.TXT");
        assertEquals(0, r.exitCode);
        assertTrue(r.text().contains("Pas de header AMSDOS valide"));
    }

    @Test
    void headerOnMissingFileFailsCleanly() {
        Result r = run("header", dskPath.toString(), "NOPE.BAS");
        assertEquals(1, r.exitCode);
        assertTrue(r.text().contains("introuvable"));
    }

    @Test
    void hexDumpsTheTokenizedPayload() {
        Result r = run("hex", dskPath.toString(), "PROG.BAS");
        assertEquals(0, r.exitCode);
        assertTrue(r.text().contains("#0000"));
    }

    @Test
    void asciiPrintsTheRawContentByteForByte() {
        Result r = run("ascii", dskPath.toString(), "PLAIN.TXT");
        assertEquals(0, r.exitCode);
        assertTrue(r.text(StandardCharsets.ISO_8859_1).startsWith("salut"));
    }

    @Test
    void basicDetokenizesTheStoredProgram() {
        Result r = run("basic", dskPath.toString(), "PROG.BAS");
        assertEquals(0, r.exitCode);
        String listing = r.text(StandardCharsets.UTF_8);
        assertTrue(listing.contains("PRINT"));
        assertTrue(listing.contains("HELLO"));
    }

    @Test
    void extractWritesTheDecodedFileToDisk() throws IOException {
        Path outDir = tempDir.resolve("out");
        Result r = run("extract", dskPath.toString(), outDir.toString(), "-f", "PROG.BAS");
        assertEquals(0, r.exitCode);
        Path extracted = outDir.resolve("PROG.BAS");
        assertTrue(Files.exists(extracted));
        assertArrayEquals(BasicTokenizer.tokenizeProgram("10 PRINT\"HELLO\"\n"), Files.readAllBytes(extracted));
    }

    @Test
    void putAddsANewFileVisibleAfterwards() {
        Path out = tempDir.resolve("with_new_file.dsk");
        Path localFile = tempDir.resolve("nouveau.txt");
        writeQuietly(localFile, "contenu");

        Result putResult = run("put", dskPath.toString(), localFile.toString(), "NOUVEAU.TXT",
                "--type=ascii", "--out=" + out);
        assertEquals(0, putResult.exitCode);
        assertTrue(putResult.text().contains("Créé"));

        Result listResult = run("list", out.toString());
        assertTrue(listResult.text().contains("NOUVEAU.TXT"));
        assertTrue(listResult.text().contains("PROG.BAS")); // le reste du catalogue est intact
    }

    @Test
    void removeDeletesAFileLeavingTheOtherIntact() {
        Path out = tempDir.resolve("without_plain.dsk");
        Result removeResult = run("remove", dskPath.toString(), "PLAIN.TXT", "--out=" + out);
        assertEquals(0, removeResult.exitCode);

        Result listResult = run("list", out.toString());
        assertFalse(listResult.text().contains("PLAIN.TXT"));
        assertTrue(listResult.text().contains("PROG.BAS"));
    }

    @Test
    void newCreatesAFreshReadableEmptyDisk() {
        Path fresh = tempDir.resolve("fresh.dsk");
        Result newResult = run("new", fresh.toString());
        assertEquals(0, newResult.exitCode);

        Result listResult = run("list", fresh.toString());
        assertEquals(0, listResult.exitCode);
        assertTrue(listResult.text().contains("Fichier"));
    }

    private static void writeQuietly(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Redirige System.out ET System.err vers le même buffer : Ascii/Basic écrivent directement sur
     * System.out (pas via la PrintWriter picocli), et fileNotFound() écrit sur getErr() (System.err par défaut)
     */
    private static Result run(String... args) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        PrintStream originalErr = System.err;
        PrintStream redirected = new PrintStream(captured, true, StandardCharsets.UTF_8);
        System.setOut(redirected);
        System.setErr(redirected);
        int exitCode;
        try {
            CommandLine cmd = new CommandLine(new DskTool());
            cmd.setCaseInsensitiveEnumValuesAllowed(true);
            exitCode = cmd.execute(args);
        } finally {
            redirected.flush();
            System.setOut(original);
            System.setErr(originalErr);
        }
        return new Result(captured.toByteArray(), exitCode);
    }

    private static final class Result {
        final byte[] rawOutput;
        final int exitCode;

        Result(byte[] rawOutput, int exitCode) {
            this.rawOutput = rawOutput;
            this.exitCode = exitCode;
        }

        String text() {
            return text(StandardCharsets.UTF_8);
        }

        String text(java.nio.charset.Charset charset) {
            return new String(rawOutput, charset);
        }
    }
}
