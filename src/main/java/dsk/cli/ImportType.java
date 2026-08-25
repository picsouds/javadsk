package dsk.cli;

import dsk.cpm.CatalogWriter;

/** Types de fichier sélectionnables pour {@code put}, mappés vers les constantes {@link CatalogWriter}. */
public enum ImportType {
    // Minuscules volontaires : affichées telles quelles dans l'aide picocli (${COMPLETION-CANDIDATES}).
    ascii(CatalogWriter.TYPE_ASCII),
    basic(CatalogWriter.TYPE_BASIC),
    binary(CatalogWriter.TYPE_BINARY);

    final String value;

    ImportType(String value) {
        this.value = value;
    }
}
