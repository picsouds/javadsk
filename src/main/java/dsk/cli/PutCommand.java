package dsk.cli;

import dsk.DiskImage;
import dsk.basic.BasicTokenizer;
import dsk.cpm.CatalogWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "put", mixinStandardHelpOptions = true,
        description = "Ajoute ou remplace un fichier dans le catalogue d'une image .dsk/.edsk "
                + "(comme le -put/-i du projet Go d'origine / iDSK)")
public class PutCommand extends AbstractDskCommand {

    @Parameters(index = "0", paramLabel = "FICHIER", description = "Image .dsk / .edsk à modifier (pas d'archive .7z/.zip)")
    Path dskPath;

    @Parameters(index = "1", paramLabel = "FICHIER_LOCAL", description = "Fichier local à importer (contenu sans header AMSDOS, comme extrait par 'extract'/'basic')")
    Path localFile;

    @Parameters(index = "2", paramLabel = "NOM_CIBLE", description = "Nom du fichier dans le catalogue à créer ou remplacer (ex: ORTHO.BAS)")
    String targetName;

    @Parameters(index = "3", paramLabel = "FORMAT", arity = "0..1", defaultValue = "data",
            description = "Format CP/M : ${COMPLETION-CANDIDATES} (défaut : ${DEFAULT-VALUE})")
    CpmFormat format;

    @Option(names = {"--out"}, paramLabel = "SORTIE", required = true,
            description = "Fichier .dsk de sortie (jamais écrasé en place, toujours explicite)")
    Path out;

    @Option(names = {"--user"}, paramLabel = "N", description = "Numéro d'utilisateur (défaut : celui du fichier remplacé, 0 pour un nouveau fichier)")
    Integer user;

    @Option(names = {"--readonly"}, negatable = true,
            description = "Force l'attribut protection en écriture (--no-readonly pour le retirer)")
    Boolean readOnly;

    @Option(names = {"--hidden"}, negatable = true,
            description = "Force l'attribut caché (--no-hidden pour le retirer)")
    Boolean hidden;

    @Option(names = {"--load"}, paramLabel = "ADRESSE", description = "Adresse de chargement (ex: 0x4000), défaut : celle du fichier remplacé, sinon 0")
    String load;

    @Option(names = {"--exec"}, paramLabel = "ADRESSE", description = "Adresse d'exécution (ex: 0x4000), défaut : celle du fichier remplacé, sinon 0")
    String exec;

    @Option(names = {"--type"}, paramLabel = "TYPE",
            description = "Type AMSDOS : ${COMPLETION-CANDIDATES}. Défaut : reprend le header du fichier remplacé "
                    + "s'il en avait un valide, sinon 'ascii' (aucun header). 'basic'/'binary' (re)construisent "
                    + "toujours un header AMSDOS ; 'ascii' n'en ajoute jamais.")
    ImportType type;

    @Option(names = {"--tokenize"},
            description = "FICHIER_LOCAL est un listing Basic ASCII (comme produit par 'basic --spaced') à "
                    + "retokeniser avant stockage, plutôt que des octets déjà tokenisés. À utiliser pour "
                    + "réinjecter un .BAS édité en clair sans dépendre de la retokenisation du firmware CPC "
                    + "au LOAD (celle-ci échoue pour certains programmes réels, vérifié sur du matériel réel).")
    boolean tokenize;

    @Override
    int run(PrintWriter writer) throws IOException {

        DiskImage disk = DiskImage.read(dskPath);
        byte[] content = Files.readAllBytes(localFile);
        if (tokenize) {
            // UTF-8 : symétrique de 'basic --spaced' (cf. BasicCommand/CpcCharset).
            content = BasicTokenizer.tokenizeProgram(new String(content, StandardCharsets.UTF_8));
        }

        boolean created = CatalogWriter.putFile(disk, format.diskFormat, targetName, content,
                user, readOnly, hidden, parseAddress(load), parseAddress(exec),
                type == null ? null : type.value);

        disk.writeToFile(out);
        writer.println((created ? "Créé : " : "Remplacé : ") + targetName + " (" + content.length + " octets) -> " + out);
        return 0;
    }

    private static Integer parseAddress(String s) {
        return (s == null) ? null : Integer.decode(s);
    }
}
