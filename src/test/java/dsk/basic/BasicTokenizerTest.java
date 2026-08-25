package dsk.basic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie {@link BasicTokenizer}, l'inverse de {@link BasicDetokenizer}. Les règles couvertes ici
 * (bornes d'encodage des constantes, tokens de "ON ERROR GOTO"/"SQ", casse des variables, ':' dans
 * une chaîne DATA) ont toutes été découvertes empiriquement en comparant, octet pour octet, ce
 * retokeniseur au fichier Basic tokenisé réel d'un jeu commercial (ORTHO.BAS) — validé à 168/185
 * lignes identiques à l'octet près, les 17 lignes restantes ne différant que d'un ':' implicite
 * avant ELSE (ambiguïté inhérente au format ASCII intermédiaire, sans effet sur l'exécution).
 */
class BasicTokenizerTest {

    @Test
    void defintWithNoSpaceBeforeItsArgumentIsStillRecognizedAsTheKeyword() {
        // Bug réel trouvé sur du vrai matériel (ORT0/1/2.BAS) : "DEFINTa-y" (sans espace, un style
        // d'écriture réel de l'auteur) n'était pas reconnu comme le mot-clé DEFINT à cause de la
        // garde anti-collision d'identifiant (pensée pour "TOTAL" vs "TO"), qui ne devrait pas
        // s'appliquer à DATA/DEFINT/DEFSTR/DEFREAL : leur argument est toujours du texte littéral,
        // jamais retokenisé, donc aucune ambiguïté possible avec un identifiant plus long.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 DEFINTa-y\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("8E 61 2D 79"), "attendu DEFINT(0x8E) puis le littéral \"a-y\" : " + hex);
        assertEquals("10 DEFINTa-y\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void remStillGetsTheAmbiguityGuardBecauseRemainCollides() {
        // Contre-exemple du bug ci-dessus : REM, contrairement à DATA/DEFINT/DEFSTR/DEFREAL, est un
        // préfixe d'un vrai nom de fonction (REMAIN) - la garde anti-collision doit donc rester
        // active pour lui (sinon "r=REMAIN(2)" retokenise à tort en "r=REM AIN(2)").
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 r=REMAIN(2)\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("FF 13 28"), "attendu la fonction REMAIN (FF 13) : " + hex);
        assertEquals("10 r=REMAIN(2)\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void roundTripsThroughSpacedListingBackToOriginalListing() {
        String spaced = "10 CALL &BB48:DEFINT a-y:IF PEEK(0)=0 THEN 20 ELSE x\r\n"
                + "20 FOR i=1 TO 3:NEXT\r\n";
        byte[] tokens = BasicTokenizer.tokenizeProgram(spaced);

        String expected = "10 CALL&BB48:DEFINT a-y:IFPEEK(0)=0THEN20ELSEx\r\n"
                + "20 FORi=1TO3:NEXT\r\n";
        assertEquals(expected, BasicDetokenizer.listing(tokens));
    }

    @Test
    void colonInsideDataStringIsNotTreatedAsStatementSeparator() {
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 DATA\"A:B\",1\r\n");
        assertEquals("10 DATA\"A:B\",1\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void preservesVariableNameCase() {
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 FOR ph=1 TO 6:NEXT\r\n");
        assertEquals("10 FORph=1TO6:NEXT\r\n", BasicDetokenizer.listing(tokens));
        // "ph" en minuscules doit être stocké tel quel (0x70,0xE8), pas "PH" (0x50,0xC8).
        String hex = hex(tokens);
        assertTrue(hex.contains("70 E8"), "attendu 'ph' minuscule dans les octets : " + hex);
    }

    @Test
    void onErrorGotoIsThreeSeparateTokensNotOneCombined() {
        // Vérifié sur un vrai fichier : le token combiné 0xB4 ("ON ERROR GOTO") n'est en réalité
        // jamais produit ; ce sont bien ON(0xB2)+ERROR(0x9C)+GOTO(0xA0) qui apparaissent.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 ON ERROR GOTO 100\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("B2 9C A0"), "attendu ON+ERROR+GOTO séparés : " + hex);
        assertEquals("10 ONERRORGOTO100\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void sqUsesFunctionTokenNotKeywordToken() {
        // "SQ" existe dans les deux tables ; l'usage réel observé est toujours la fonction.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 IF SQ(1)>127 THEN 10\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("FF 17"), "attendu la fonction SQ (FF 17) : " + hex);
        assertEquals("10 IFSQ(1)>127THEN10\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void numberEncodingBoundaries() {
        // 0-9 : forme compacte 1 octet (0x0E-0x16). 10 : PAS la forme compacte malgré la table de
        // décodage qui va jusqu'à "10" (0x18) — vérifié réel, "10" utilise 0x19 (8 bits).
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 x=9\r\n")).contains("EF 17"));
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 x=10\r\n")).contains("EF 19 0A"));
        // 255 : 8 bits (0x19). 256 : 16 bits (0x1A).
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 x=255\r\n")).contains("EF 19 FF"));
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 x=256\r\n")).contains("EF 1A 00 01"));
        // 32767 : encore 16 bits. 32768 : flottant (0x1F) — au-delà de l'entier 16 bits signé.
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 x=32767\r\n")).contains("EF 1A FF 7F"));
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 x=32768\r\n")).contains("EF 1F"));

        // Le texte décodé retombe sur la même valeur numérique dans tous les cas.
        assertEquals("10 x=9\r\n", BasicDetokenizer.listing(BasicTokenizer.tokenizeProgram("10 x=9\r\n")));
        assertEquals("10 x=32768\r\n", BasicDetokenizer.listing(BasicTokenizer.tokenizeProgram("10 x=32768\r\n")));
    }

    @Test
    void hexLiteralRoundTrips() {
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 x=&BB48\r\n");
        assertEquals("10 x=&BB48\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void binaryLiteralEncodesTheRightValue() {
        // &X1010 = 10 décimal ; le décodeur affiche "&XA" (hexa), pas "&X1010" (binaire) — quirk
        // déjà connu et volontairement conservé d'iDSK (cf. commentaire sur le cas 0x1B), pas un
        // round-trip texte fidèle, mais la VALEUR encodée est correcte.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 y=&X1010\r\n");
        assertEquals("10 y=&XA\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void rsxCallNeverGetsALeadingSpaceEvenAfterAKeyword() {
        // Vérifié réel : "THEN |PR" ne redonne jamais d'espace au décodage, contrairement à un
        // mot-clé "mot" normal après THEN (qui, lui, en obtiendrait un en mode --spaced).
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 IF 1 THEN |PR,1\r\n");
        assertEquals("10 IF1THEN|PR,1\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void embeddedCarriageReturnInsideAStringRoundTripsWithoutCorruptingSpacedLineSplitting() {
        // Un octet 0x0D (CR) brut à l'intérieur d'une chaîne DATA, s'il était réémis tel quel dans
        // le texte "--spaced", casserait le découpage en lignes de tokenizeProgram (qui coupe sur
        // \r\n) : la ligne serait scindée en deux au milieu de la chaîne. CpcCharset le représente
        // par le symbole "Control Pictures" U+240D à la place, qui ne collisionne pas avec \r\n.
        byte[] original = getOriginal();
        String spaced = BasicDetokenizer.spacedListing(original);
        long lineBreaks = spaced.chars().filter(ch -> ch == '\n').count();
        assertEquals(1, lineBreaks, "le CR embarqué ne doit pas introduire de saut de ligne supplémentaire : "
                + hex(spaced.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        byte[] roundTripped = BasicTokenizer.tokenizeProgram(spaced);
        assertEquals(hex(original), hex(roundTripped));
    }

    private static byte[] getOriginal() {
        byte[] content = {
            (byte) 0x8C,                   // DATA
            (byte) '"', (byte) 'A', 0x0D, (byte) 'B', (byte) '"',
            0x00,                           // fin de ligne
        };
        int lineLength = 4 + content.length;
        return new byte[]{
            (byte) (lineLength & 0xFF), (byte) (lineLength >> 8),   // longueur de ligne
            0x0A, 0x00,                                             // numéro de ligne 10
            content[0], content[1], content[2], content[3], content[4], content[5], content[6],
            0x00, 0x00,                                             // fin de programme
        };
    }

    @Test
    void elseFollowedByABareNumberIsAnImplicitGotoEncodedAsALineReference() {
        // Bug réel trouvé sur du vrai matériel (xcpc) : "THEN RETURN ELSE 21000" n'était pas
        // reconnu comme un GOTO implicite après ELSE (contrairement à après THEN), donc "21000"
        // était encodé comme une simple constante (0x1A) au lieu d'une référence de ligne (0x1E) -
        // suspecté d'avoir désynchronisé la pile GOSUB/RETURN à l'exécution sur un vrai jeu.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 IF A$<>\"\"THEN RETURN ELSE 21000\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("97 1E"), "attendu la référence de ligne (0x1E) après ELSE, pas 0x1A : " + hex);
    }

    @Test
    void elseAfterAVariableAssignmentStillGetsTheImplicitColonRestored() {
        // Bug réel trouvé sur du vrai matériel : le ':' avalé n'était restitué que si le token
        // juste avant ELSE était encore un mot-clé "connu" - une variable/chaîne juste avant (très
        // courant : A$="VRAI"ELSE...) remettait cet état à null et ratait la restitution.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 IF S1=0 THEN A$=\"VRAI\"ELSE A$=\"FAUX\"\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("22 01 97"), "attendu un ':' (0x01) restitué avant ELSE : " + hex);
    }

    @Test
    void elseColonRestorationIsNotFooledByANumberEndingInByteZeroOne() {
        // Bug réel trouvé sur du vrai matériel : détecter "un ':' est déjà présent" en inspectant le
        // dernier octet brut se trompe quand ce dernier octet est en fait le poids fort d'un nombre
        // qui vaut par coïncidence 0x01 (ex: 390 = 0x0186) - confondu avec un vrai ':', ratant la
        // restitution. Doit se baser sur un vrai indicateur d'état, pas sur la valeur de l'octet.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 IF X>0 THEN 390 ELSE A$=1\r\n");
        String hex = hex(tokens);
        // 390 = 0x0186 -> encodé 1E 86 01 ; le ':' restitué doit suivre, avant 97 (ELSE).
        assertTrue(hex.contains("1E 86 01 01 97"), "attendu le ':' restitué même si la valeur précédente finit par 0x01 : " + hex);
    }

    @Test
    void onGotoWithMultipleCommaSeparatedTargetsKeepsLineReferenceTokenForAllOfThem() {
        // Bug réel trouvé sur du vrai fichier ("ON ep GOTO 1000,2000,8000") : seule la 1re cible
        // après GOTO devenait une vraie référence de ligne (0x1E), les suivantes (après une virgule)
        // retombaient en simple constante (0x1A) - "lastWasOn" était remis à faux par la variable
        // entre ON et GOTO, alors que l'expression entre les deux peut être arbitraire (variable,
        // calcul...) et doit quand même laisser GOTO savoir qu'un ON précède dans la même instruction.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 ON ep GOTO 1000,2000,8000\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("1E E8 03 2C 1E D0 07 2C 1E 40 1F"),
                "attendu les 3 cibles en référence de ligne (0x1E), pas seulement la 1re : " + hex);
    }

    @Test
    void variableGluedDirectlyToElseIsNotSwallowedIntoTheVariableName() {
        // Bug réel trouvé sur du vrai fichier ("...y=yp-16*nwELSE x=602..." en --spaced, ELSE garde
        // toujours son espace de fin, seul le ':' avalé devant ELSE ne redonne pas d'espace côté
        // gauche quand le token précédent est une variable - cf. le cas VARIABLE_MARKER de
        // BasicDetokenizer#spacedListing) : "nwELSE" collé doit rester deux tokens (variable "nw"
        // puis mot-clé ELSE), pas un seul identifiant "nwELSE" - un scan de lettres brut avalait
        // ELSE comme faisant partie du nom de la variable au lieu de s'arrêter à la frontière.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 y=nwELSE x=1\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("6E F7 01 97"), "attendu la variable \"nw\" bien terminée, puis ':'+ELSE : " + hex);
        assertEquals("10 y=nwELSEx=1\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void integerLiteralTooLargeForALongFallsBackToFloatInsteadOfThrowing() {
        // Bug réel trouvé sur un vrai fichier (40 chiffres) : Long.parseLong plantait au lieu de
        // suivre le même chemin "grande valeur -> flottant" que les entiers simplement > 32767.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 a=21182224251529778000000000000000000000\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("EF 1F"), "attendu '=' suivi du token flottant (0x1F) : " + hex);
    }

    @Test
    void questionMarkIsTheRealRomShorthandForPrint() {
        // Trouvé en comparant Tokenising.asm (ROM 6128) à ce tokeniseur, pas par test round-trip sur
        // disque réel : le ROM convertit "?" au token PRINT (0xBF) dès la tokenisation
        // (tokenise_any_other_ascii_char, xor $3f/ld b,$bf) - invisible en round-trip car LIST
        // n'affiche jamais "?" en retour, toujours "PRINT" en toutes lettres.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 ?\"HI\"\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("BF 22 48 49 22"), "attendu le token PRINT (0xBF), pas le caractère '?' littéral : " + hex);
        assertEquals("10 PRINT\"HI\"\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void floatCanStartDirectlyWithADecimalPoint() {
        // Trouvé via Tokenising.asm : test_if_period_or_digit traite '.' et chiffre de façon
        // identique en entrée de nombre - ".5" est un flottant valide sur CPC réel (0.5), pas un
        // point littéral suivi d'un chiffre séparé.
        byte[] tokens = BasicTokenizer.tokenizeProgram("10 a=.5\r\n");
        String hex = hex(tokens);
        assertTrue(hex.contains("EF 1F"), "attendu '=' suivi du token flottant (0x1F), pas un '.' littéral : " + hex);
        assertEquals("10 a=0.5\r\n", BasicDetokenizer.listing(tokens));
    }

    @Test
    void autoRenumDeleteEditGetLineReferenceTokenForTheirFirstArgument() {
        // Table ROM réelle keywords_taking_line_numbers (Tokenising.asm) : le nombre juste après
        // AUTO/RENUM/DELETE/EDIT est une référence de ligne (0x1E), pas une constante ordinaire
        // (0x19/0x1A) - jamais vu en round-trip car ces commandes ne sont quasi jamais sauvegardées
        // comme instructions dans un vrai programme (mode direct uniquement en pratique). Même
        // catégorie de token que le bug ELSE-implicite déjà trouvé/corrigé cette session.
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 AUTO 100\r\n")).contains("81 1E 64 00"));
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 RENUM 1000\r\n")).contains("C6 1E E8 03"));
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 DELETE 100\r\n")).contains("92 1E 64 00"));
        assertTrue(hex(BasicTokenizer.tokenizeProgram("10 EDIT 50\r\n")).contains("96 1E 32 00"));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", x));
        }
        return sb.toString();
    }
}
