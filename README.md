# javadsk

[![Build Status](https://github.com/picsouds/javadsk/actions/workflows/build.yml/badge.svg)](https://github.com/picsouds/javadsk/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=picsouds_javadsk&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=picsouds_javadsk)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=picsouds_javadsk&metric=bugs)](https://sonarcloud.io/summary/new_code?id=picsouds_javadsk)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=picsouds_javadsk&metric=coverage)](https://sonarcloud.io/summary/new_code?id=picsouds_javadsk)

javadsk est une bibliothèque Java et un outil en ligne de commande/GUI pour manipuler des images de
disquette Amstrad CPC (`.dsk` / `.edsk`).

Inspiré du projet Go [jeromelesaux/dsk](https://github.com/jeromelesaux/dsk) (lecture/écriture des
images) et du projet [iDSK](https://github.com/cpcsdk/iDSK).

Le projet permet de :

- lire des images DSK et Extended DSK (EDSK), y compris directement dans une archive `.7z`/`.zip`
- parcourir le catalogue CP/M (formats Data, System, IBM)
- extraire des fichiers
- détecter, valider et afficher le détail des headers AMSDOS
- afficher un programme BASIC en clair (listing compact, comme `iDSK -b`), ou **avec les espaces d'un
  vrai `LIST` CPC (`--spaced`)**
- **retokeniser un listing BASIC édité en texte vers le format tokenisé CPC** (vrai compilateur
    texte → bytes)
- ajouter, remplacer ou supprimer des fichiers dans une image
- créer des images DSK vierges

## Compilation

Projet Gradle multi-module (racine = CLI, `gui/` = interface graphique, `benchmarks/` = JMH).
Nécessite un JDK 21+ pour compiler (Gradle/JUnit 6) ; les jars produits sont Java 11
(`compileJava.options.release`), donc exécutables sur un JRE 11 ou plus récent.

```
./gradlew build      # compile + exécute les tests de tous les sous-projets
./gradlew test       # tests uniquement
```

Deux jars exécutables sont générés versionnés (`x.x.x`, défini dans
`build.gradle.kts` à la racine du projet) :

- `build/libs/javadsk-x.x.x.jar` — CLI
- `gui/build/libs/gui-x.x.x.jar` — interface graphique (voir [Interface graphique](#interface-graphique))

## Utilisation CLI

```
java -jar build/libs/javadsk-x.x.x.jar --help
java -jar build/libs/javadsk-x.x.x.jar --version
java -jar build/libs/javadsk-x.x.x.jar extract --help    # aide d'une sous-commande
```

> [!NOTE]
> **Sous Windows (PowerShell)**, les caractères accentués peuvent mal s'afficher (`tokenis├®` au
> lieu de `tokenisé`) : la sortie est en UTF-8, mais la console attend souvent un autre encodage.
>
> Le correctif :
> ```powershell
> [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
> ```
> à exécuter une fois par session PowerShell avant de lancer `java -jar ...`.


### Lire une image

```
java -jar build/libs/javadsk-x.x.x.jar list image.dsk
java -jar build/libs/javadsk-x.x.x.jar list image.dsk system              # format CP/M non standard
java -jar build/libs/javadsk-x.x.x.jar list archive.7z                    # image unique dans l'archive
java -jar build/libs/javadsk-x.x.x.jar list archive.zip --entry image.dsk # archive à plusieurs images
```

Exemple de sortie :

```
DiskImage[DSK tracks=40 sides=1 creator='javadsk']
Fichier       User RO H  Taille  Type           Load     Exec
PROG.BAS      00             30  Basic          0x0000   0x0000
README.TXT    00           1024  (sans header)  -        -
PROG.BIN      00           2048  Binaire        0x4000   0x4000
LOADER.BIN    00   RO H      46  Binaire        0x8000   0x8000
```

`README.TXT` n'a pas de header AMSDOS, qui est le seul endroit où le catalogue CP/M stocke la taille
exacte d'un fichier. Sans lui, `list` ne peut afficher que le nombre de blocs alloués (1024 octets
chacun) : d'où 1024, même si le vrai contenu ne fait que 27 octets.

NB : Les commandes `list`, `extract`, `basic`, `ascii`, `hex` et `header` acceptent toutes `--entry NOM`
lorsque l'archive contient plusieurs images.

### Extraire des fichiers

```
java -jar build/libs/javadsk-x.x.x.jar extract image.dsk dossier_sortie/
java -jar build/libs/javadsk-x.x.x.jar extract image.dsk dossier_sortie/ -f PROG.BAS   # un seul fichier
java -jar build/libs/javadsk-x.x.x.jar extract image.dsk dossier_sortie/ --raw         # garde le header AMSDOS
```

Par défaut, un header AMSDOS valide est automatiquement retiré.

### Inspection

```
java -jar build/libs/javadsk-x.x.x.jar ascii image.dsk README.TXT     # contenu brut (comme iDSK -a)
java -jar build/libs/javadsk-x.x.x.jar hex image.dsk PROG.BIN         # dump hexa + ASCII (comme iDSK -h)
java -jar build/libs/javadsk-x.x.x.jar header image.dsk PROG.BIN      # détail du header AMSDOS
```

Exemple de sortie de `hex` (16 octets par ligne, non-imprimables représentés par `.`) :

```
#0000 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F | ................
#0010 10 11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F | ................
#0020 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F | .!"#$%&'()*+,-./
#0030 30 31 32 33 34 35 36 37 38 39 3A 3B 3C 3D 3E 3F | 0123456789:;<=>?
#0040 40 41 42 43 44 45 46 47 48 49 4A 4B 4C 4D 4E 4F | @ABCDEFGHIJKLMNO
#0050 50 51 52 53 54 55 56 57 58 59 5A 5B 5C 5D 5E 5F | PQRSTUVWXYZ[\]^_
#0060 60 61 62 63 64 65 66 67 68 69 6A 6B 6C 6D 6E 6F | `abcdefghijklmno
#0070 70 71 72 73 74 75 76 77 78 79 7A 7B 7C 7D 7E 7F | pqrstuvwxyz{|...
#0080 80 81 82 83 84 85 86 87 88 89 8A 8B 8C 8D 8E 8F | ................
#0090 90 91 92 93 94 95 96 97 98 99 9A 9B 9C 9D 9E 9F | ................
```

Exemple de sortie de `header` :

```
Nom                 : PROG.BIN
User                : 0
Type                : Binaire (2)
Bloc                : 0 (dernier : 0)
Indicateur 1er bloc : 0xFF
Longueur logique    : 4 octets
Longueur data       : 128 octets (champ 0x13-0x14, non fiable sur les headers réels)
Longueur réelle     : 4 octets (24 bits)
Adresse chargement  : 0x4000
Adresse exécution   : 0x4000
Checksum            : 0x049A (valide)
```

### BASIC

```
java -jar build/libs/javadsk-x.x.x.jar basic image.dsk PROG.BAS             # listing en clair (comme iDSK -b)
java -jar build/libs/javadsk-x.x.x.jar basic image.dsk PROG.BAS --spaced    # listing "vrai CPC", pour ré-écrire ensuite
java -jar build/libs/javadsk-x.x.x.jar basic image.dsk -- -3DC.BAS          # nom de fichier commençant par '-'
```

`--debug` est une option de trace "token par token" (offset, octets bruts, texte produit)

### Modifier une image

Toutes les modifications produisent une **nouvelle** image : l'original n'est jamais modifiée.

```
java -jar build/libs/javadsk-x.x.x.jar put image.dsk fichier_modifie.BAS PROG.BAS --out sortie.dsk
java -jar build/libs/javadsk-x.x.x.jar put image.dsk texte.txt README.TXT --out sortie.dsk --type ascii
java -jar build/libs/javadsk-x.x.x.jar put image.dsk prog.bin PROG.BIN --out sortie.dsk --type binary --load 0x4000 --exec 0x4000
java -jar build/libs/javadsk-x.x.x.jar put image.dsk prog.txt PROG.BAS --out sortie.dsk --type basic --tokenize
```

| Option                        | Obligatoire | Description                                                                                                          |
|-------------------------------|-------------|----------------------------------------------------------------------------------------------------------------------|
| `--out`                       | oui         | image DSK de sortie                                                                                                  |
| `--user`                      | non         | numéro d'utilisateur CP/M                                                                                            |
| `--readonly` / `--hidden`     | non         | attributs (`--no-readonly`/`--no-hidden` pour les retirer)                                                           |
| `--type ascii\|basic\|binary` | non         | type AMSDOS                                                                                                          |
| `--load` / `--exec`           | non         | adresses de chargement/exécution (ex: `0x4000`)                                                                      |
| `--tokenize`                  | non         | le fichier source (2e argument, ex: `prog.txt` ci-dessus) est un listing Basic en texte produit par `basic --spaced` |

Si le fichier remplacé a déjà un header AMSDOS valide et qu'aucun `--type` n'est donné, celui-ci
est reconstruit automatiquement (nouvelle taille, checksum recalculé).

### Supprimer un fichier

```
java -jar build/libs/javadsk-x.x.x.jar remove image.dsk PROG.BAS --out sortie.dsk        # format data
java -jar build/libs/javadsk-x.x.x.jar remove image.dsk PROG.BAS system --out sortie.dsk # format CP/M non standard
```

Comme `iDSK -r` : le fichier n'est pas effacé, seule son entrée de catalogue est marquée
supprimée (octet user `0xE5`).

### Créer une image vierge

```
java -jar build/libs/javadsk-x.x.x.jar new vierge.dsk                       # 40 pistes, format data
java -jar build/libs/javadsk-x.x.x.jar new vierge.dsk system --tracks 42
```

Comme `iDSK -n`. Le format (2e argument, `data`/`system`/`ibm`) et `--tracks` (40 par défaut,
standard disquette CPC simple face) sont les seuls réglages ; le nombre de faces et la taille de
secteur suivent le format choisi.

### Éditer un programme BASIC

```
java -jar build/libs/javadsk-x.x.x.jar basic image.dsk PROG.BAS --spaced > prog.txt
# ... éditer prog.txt ...
java -jar build/libs/javadsk-x.x.x.jar put image.dsk prog.txt PROG.BAS --out sortie.dsk --type basic --tokenize
```

`--spaced` produit le listing compatible avec `--tokenize` : les espaces entre mots-clés/valeurs y
sont réintroduits comme un vrai `LIST` CPC. Les octets CPC non-ASCII à l'intérieur d'une chaîne/DATA y sont
représentés par un équivalent Unicode lisible et réversible plutôt que l'octet brut.

> [!IMPORTANT]
> `--tokenize` retokenise **réellement** le texte en bytes CPC (`dsk.basic.BasicTokenizer`), comme le
> ferait le vrai tokeniseur ROM BASIC ([`Tokenising.asm`](https://github.com/Bread80/Amstrad-CPC-BASIC-Source/blob/main/Tokenising.asm)).

> [!CAUTION]
> La source de `--tokenize` doit toujours être la sortie de `basic --spaced`, jamais celle de
> `basic` (listing compact, sans les espaces). Sans ces espaces, le retokeniseur ne peut pas
> délimiter les mots-clés collés les uns aux autres et produira un fichier incorrect.

> [!TIP]
> Pour éditer le fichier produit par `--spaced` avec une coloration syntaxique BASIC dans
> [Geany](https://www.geany.org/) : copier
> [`editors/geany/filetypes.cpcbasic.conf`](editors/geany/filetypes.cpcbasic.conf) vers
> `~/.config/geany/filedefs/filetypes.cpcbasic.conf` (Linux/macOS) ou
> `%APPDATA%\geany\filedefs\filetypes.cpcbasic.conf` (Windows) puis redémarrer Geany.
>
> Sélectionner ensuite manuellement Document > Définir le type de fichier > CPCBasic après
> ouverture du fichier.

#### NB `--spaced`

Le listing produit par `--spaced` est encodé en **UTF-8** (à cause de ces caractères Unicode) : si
vous l'éditez, enregistrez-le en UTF-8 (`put --tokenize` le relit en UTF-8 symétriquement, un autre
encodage corromprait les octets CPC non-ASCII).

#### NB `--tokenize`

Testé (décodage → réencodage → comparaison) sur plusieurs milliers de fichiers Basic réels issus de [DSK TOSEC](https://www.tosecdev.org/).

## Interface graphique

Sous-projet `gui/` (Swing + [FlatLaf](https://github.com/JFormDesigner/FlatLaf)) : reprend
directement les mêmes classes `dsk.*` que la CLI.

### Utilisation

```
java -jar gui/build/libs/gui-x.x.x.jar
java -jar gui/build/libs/gui-x.x.x.jar image.dsk                    # ouvre directement une image
java -jar gui/build/libs/gui-x.x.x.jar archive.zip antregob.dsk     # archive à plusieurs images, équivalent GUI de --entry
```

Sans le 2e argument, si l'archive contient plusieurs images, un sélecteur s'affiche pour choisir laquelle ouvrir.

### Fonctionnalité

| Fonctionnalité                                                            | Équivalent CLI             |
|---------------------------------------------------------------------------|----------------------------|
| Catalogue (table triable)                                                 | `list`                     |
| Ouvrir une image depuis une archive `.7z`/`.zip` (sélecteur si plusieurs) | `list archive.zip --entry` |
| Extraire (normal / brut AMSDOS)                                           | `extract`                  |
| Visualisation : Ascii / Hex                                               | `ascii`, `hex`             |
| Visualisation  : Basic (compact / `--spaced`)                             | `basic`                    |
| Visualisation : Header AMSDOS                                             | `header`                   |
| Importer un fichier                                                       | `put`                      |
| Supprimer un fichier                                                      | `remove`                   |
| Créer une image vierge                                                    | `new`                      |

> [!NOTE]
> Importer/supprimer un fichier n'est possible que sur une image `.dsk`/`.edsk` directe, jamais
> depuis une archive (comme `put`/`remove` en CLI) : ouvrir le fichier `.dsk` directement pour ça.

Menu **Thèmes** : les 4 thèmes de base FlatLaf (Clair, Sombre, IntelliJ, Darcula) plus tout le pack
[FlatLaf IntelliJ Themes](https://github.com/JFormDesigner/FlatLaf/tree/main/flatlaf-intellij-themes)
(Arc Dark, Dracula, One Dark, Nord, Solarized, Material...).

> [!TIP]
> Dans les fenêtres de Visualisation (Ascii/Hex/Basic), le menu **Fichier** de la fenêtre permet
> d'**enregistrer** le résultat sur disque ou de le **copier dans le presse-papier**.

## Architecture

| Package       | Rôle                                                    |
|---------------|---------------------------------------------------------|
| `dsk`         | lecture/écriture des images DSK / EDSK                  |
| `dsk.archive` | lecture d'images depuis une archive `.7z` / `.zip`      |
| `dsk.amsdos`  | parsing et validation des headers AMSDOS                |
| `dsk.cpm`     | lecture et écriture du catalogue CP/M                   |
| `dsk.basic`   | détokenisation, retokenisation et jeu de caractères CPC |
| `dsk.hex`     | dump hexadécimal                                        |
| `dsk.cli`     | interface en ligne de commande                          |

Sous-projet `gui/` (interface graphique)

| Package              | Rôle                                                                                 |
|----------------------|--------------------------------------------------------------------------------------|
| `dsk.gui`            | point d'entrée (`MainWindow`)                                                        |
| `dsk.gui.model`      | état du disque ouvert et modèle de la table du catalogue                             |
| `dsk.gui.view`       | fenêtre principale et boîtes de dialogue Swing/FlatLaf                               |
| `dsk.gui.controller` | actions du GUI (orchestration dialogues → service → mise à jour vue)                 |
| `dsk.gui.service`    | logique métier pure (put/remove/extract), sans dépendance Swing, testée unitairement |

## Performance

Benchmarks JMH dans le sous-projet `benchmarks/`

```
./gradlew :benchmarks:jmh
```

Résultat sur un programme synthétique de 5000 lignes, temps moyen par appel (5 mesures) :

| Opération                      | Temps moyen |
|--------------------------------|-------------|
| Détokenisation (bytes → texte) | 8,3 ms      |
| Tokenisation (texte → bytes)   | 43,8 ms     |

## Limites connues

- Simple face uniquement (la double-face n'est gérée qu'en filtrant la face 0).
- Pas de support des disques "vendor"/protégés à géométrie non-standard au-delà de ce que permet
  le format EDSK.
- `put` ne fait pas grandir le disque au-delà des pistes déjà formatées : erreur explicite si
  l'espace/catalogue est insuffisant, plutôt que d'étendre le nombre de pistes.

## Références format

- [DSK](https://cpctech.cpcwiki.de/docs/dsk.html) / [Extended DSK](https://cpctech.cpcwiki.de/docs/extdsk.html)
- [Header AMSDOS](https://cpctech.cpcwiki.de/docs/allhead.html)
- [DPB par format CP/M](https://cpctech.cpcwiki.de/docs/amsdos.asm)
- [Table des tokens BASIC](https://cpctech.cpcwiki.de/docs/bastech.html)

## Licence

[GPL-3.0](LICENSE)
