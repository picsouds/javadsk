# Changelog

## 1.0.3 - 2026-08-28

### Ajouté

- `dsk.amsdos.BasicProtect` : déchiffrement du "Basic protégé" AMSDOS (BASIC `SAVE ,P`).

## 1.0.2 - 2026-08-27

### Ajouté

- Interface graphique (sous-projet `gui/`, Swing + [FlatLaf](https://github.com/JFormDesigner/FlatLaf)) utilisant  directement les classes `dsk.*` (mêmes résultats, octet pour octet, que la CLI).
- Le jar GUI (`gui-x.x.x.jar`) est désormais publié en plus du jar CLI (`javadsk-x.x.x.jar`) sur
  chaque release GitHub.

### Modifié

- `DskLoader` (résolution image directe/archive) déplacé de `dsk.cli` (interne) vers `dsk.archive`
  (public), pour être partagé entre la CLI et le GUI.

## 1.0.1 - 2026-08-26

### Corrigé

- `put --tokenize` perdait un octet étendu (`0xFF <fn>`, plage non définie 0x80-0xFF) apparaissant
  hors chaîne/REM/DATA. Préservé via `CpcCharset` comme le reste des octets de contrôle.

### Performance

- `BasicTokenizer` retokenise désormais ~4,5x plus vite : les mots-clés et fonctions sont regroupés
  par première lettre (`matchLongestFirst`) au lieu d'un scan linéaire de toute la table à chaque
  position. Mesuré via de nouveaux benchmarks JMH (sous-projet `benchmarks/`, `./gradlew :benchmarks:jmh`).

### Ajouté

- Configuration Geany (`editors/geany/filetypes.cpcbasic.conf`) pour la coloration syntaxique BASIC
  des fichiers produits par `--spaced`.

## 1.0.0 - 2026-08-25

Première version publique.

### Ajouté

- Lecture des images DSK et Extended DSK (EDSK), y compris directement depuis une archive `.7z`/`.zip`.
- Lecture du catalogue CP/M (formats Data, System, IBM) et extraction de fichiers.
- Détection, validation (checksum) et affichage détaillé des headers AMSDOS.
- Dé tokenisation BASIC : listing compact façon iDSK (`basic`), listing "vrai CPC" avec les espaces
  d'un `LIST` réel (`--spaced`), trace token par token (`--debug`).
- Re tokenization BASIC (texte édité → bytes tokenisés CPC, `put --tokenize`) 
- Ajout, remplacement, suppression de fichiers dans une image (`put`, `remove`), création d'images
  DSK vierges (`new`).
- CLI complète basée sur picocli (`list`, `extract`, `basic`, `ascii`, `hex`, `header`, `put`,
  `remove`, `new`).
