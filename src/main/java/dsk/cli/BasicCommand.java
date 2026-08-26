package dsk.cli;

import dsk.amsdos.AmsdosHeader;
import dsk.basic.BasicDetokenizer;
import dsk.basic.BasicTraceEvent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Command(name = "basic", mixinStandardHelpOptions = true,
        description = "Affiche le listing en clair d'un fichier Basic tokenisé (comme iDSK -b)")
public class BasicCommand extends AbstractTargetFileCommand {

    @Parameters(index = "1", paramLabel = "FICHIER_BASIC",
            description = "Nom du fichier Basic dans le catalogue (tel qu'affiché par 'list', ex: MYPROG.BAS)")
    String basicFile;

    @Override
    String targetFileName() {
        return basicFile;
    }

    @Option(names = {"--debug"},
            description = "Affiche une trace token par token (offset, octets bruts, type, texte produit) "
                    + "au lieu du listing, pour comprendre ce que fait chaque octet du fichier d'origine.")
    boolean debug;

    @Option(names = {"--spaced"},
            description = "Insère les espaces entre mots-clés/valeurs comme un vrai CPC (SAVE\"nom\",A), au lieu du "
                    + "listing compact façon iDSK. À utiliser pour préparer un fichier à réinjecter via "
                    + "'put --type ascii' et le faire recharger correctement sur un CPC/émulateur (sans ces "
                    + "espaces, le firmware échoue au LOAD avec \"Line too long\").")
    boolean spaced;

    @Override
    int run(PrintWriter out, byte[] raw) throws IOException {
        byte[] payload = AmsdosHeader.payloadOf(raw);

        // UTF-8 direct sur stdout, pas la PrintWriter de picocli (charset JVM non garanti) :
        StringBuilder output = new StringBuilder();
        if (debug) {
            for (BasicTraceEvent event : BasicDetokenizer.trace(payload, spaced)) {
                output.append(event).append(System.lineSeparator());
            }
        } else {
            output.append(spaced ? BasicDetokenizer.spacedListing(payload) : BasicDetokenizer.listing(payload));
        }
        out.flush();
        System.out.write(output.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
        return 0;
    }
}
