# Changelog

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
