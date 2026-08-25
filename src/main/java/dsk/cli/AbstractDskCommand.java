package dsk.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

/** Base commune aux sous-commandes : injecte la sortie picocli et fournit le message d'erreur standard. */
abstract class AbstractDskCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public final Integer call() throws IOException {
        return run(spec.commandLine().getOut());
    }

    /** @param out wrapper picocli autour de {@code System.out}, jamais à fermer par l'appelant. */
    abstract int run(PrintWriter out) throws IOException;

    /** Utilisé quand le nom passé en paramètre ne correspond à aucune entrée du catalogue. */
    void fileNotFound(String name) {
        spec.commandLine().getErr().println("Fichier introuvable dans le catalogue : " + name);
    }
}
