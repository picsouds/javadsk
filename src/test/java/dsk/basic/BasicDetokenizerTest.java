package dsk.basic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vérifie le détokeniseur sur des lignes Basic AMSDOS construites à la main (mots-clés, chaîne
 * entre guillemets, variable, constante hexadécimale), en miroir du format lu par iDSK -b.
 */
class BasicDetokenizerTest {

    @Test
    void detokenizesKeywordsStringsVariablesAndHexConstants() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // Ligne 10 : PRINT"HI":REM done
        byte[] line10 = {
            (byte) 0xBF, // PRINT
            (byte) '"', (byte) 'H', (byte) 'I', (byte) '"',
            (byte) 0x01, // ':'
            (byte) 0xC5, // REM
            (byte) ' ', (byte) 'd', (byte) 'o', (byte) 'n', (byte) 'e',
            0x00, // fin de ligne
        };
        appendLine(buf, 10, line10);

        // Ligne 20 : x=&1A
        byte[] line20 = {
            0x0D, 0x00, 0x00, (byte) 0xF8, // variable standard "x" (2 octets pointeur + 'x'|0x80)
            (byte) 0xEF, // '='
            0x1C, 0x1A, 0x00, // constante hexa 16 bits = 0x001A
            0x00, // fin de ligne
        };
        appendLine(buf, 20, line20);

        buf.write(0x00);
        buf.write(0x00); // fin de programme

        String expected = "10 PRINT\"HI\":REM done\r\n20 x=&1A\r\n";
        byte[] payload = buf.toByteArray();
        assertEquals(expected, BasicDetokenizer.listing(payload));

        List<BasicTraceEvent> events = BasicDetokenizer.trace(payload);
        List<String> kinds = events.stream().map(e -> e.kind).collect(Collectors.toList());
        assertEquals(List.of(
                "longueur ligne", "numéro ligne", "mot-clé", "littéral", "séparateur ':'", "mot-clé", "littéral",
                "longueur ligne", "numéro ligne", "variable", "mot-clé", "constante &"
        ), kinds);

        // Concaténer le texte de chaque événement doit reconstituer le listing (hors \r\n, pas tracés).
        String reconstructed = events.stream().map(e -> e.text).collect(Collectors.joining());
        assertEquals("10 PRINT\"HI\":REM done20 x=&1A", reconstructed);

        // Premier événement : les 2 octets de longueur de la ligne 10, à l'offset 0.
        BasicTraceEvent first = events.get(0);
        assertEquals(0, first.offset);
        assertArrayEquals(new byte[]{(byte) (line10.length + 4), 0x00}, first.rawBytes);

        // L'événement "mot-clé" PRINT pointe bien sur l'octet de token 0xBF, pas sur autre chose.
        BasicTraceEvent printEvent = events.get(2);
        assertEquals("PRINT", printEvent.text);
        assertArrayEquals(new byte[]{(byte) 0xBF}, printEvent.rawBytes);
    }

    /**
     * Règles d'espacement de {@link BasicDetokenizer#spacedListing}, déduites empiriquement en
     * comparant un vrai fichier tokenisé à sa version ASCII produite par SAVE"nom",A sur un CPC
     * réel (validé à 183/184 lignes identiques sur un vrai programme de ~13 Ko).
     */
    @Test
    void spacedListingMatchesRealCpcSaveAsciiFormat() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // 10 CALL &BB48:IF PEEK(0)=0 THEN 5 ELSE x
        // Couvre : espace mot-clé<->mot-clé/constante, fonction collée à '(', opérateur '=' collé
        // des deux côtés, ':' avalé devant ELSE avec un nombre avant -> restitué en espace.
        byte[] line10 = {
            (byte) 0x83,                               // CALL
            0x1C, 0x48, (byte) 0xBB,                   // &BB48
            0x01,                                      // :
            (byte) 0xA1,                                // IF
            (byte) 0xFF, 0x12,                          // PEEK (fonction)
            (byte) '(',
            0x0E,                                       // 0
            (byte) ')',
            (byte) 0xEF,                                 // =
            0x0E,                                        // 0
            (byte) 0xEB,                                 // THEN
            0x13,                                        // 5
            0x01,                                        // : (avalé devant ELSE)
            (byte) 0x97,                                 // ELSE
            0x0D, 0x00, 0x00, (byte) 0xF8,               // variable "x"
            0x00,
        };
        appendLine(buf, 10, line10);

        // 20 yELSEZ : ':' avalé devant ELSE avec une VARIABLE avant ==> pas d'espace
        byte[] line20 = {
            0x0D, 0x00, 0x00, (byte) 0xF9,               // variable "y"
            0x01,                                        // :
            (byte) 0x97,                                 // ELSE
            (byte) 'Z',
            0x00,
        };
        appendLine(buf, 20, line20);

        buf.write(0x00);
        buf.write(0x00);

        String expected = "10 CALL &BB48:IF PEEK(0)=0 THEN 5 ELSE x\r\n20 yELSEZ\r\n";
        assertEquals(expected, BasicDetokenizer.spacedListing(buf.toByteArray()));
    }

    @Test
    void spacedListingMapsCustomCharacterBytesToUnicodeButListingSanitizesThemLikeIdsk() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] line10 = {
            (byte) '"', (byte) 0x81, (byte) 0x80, (byte) '"',
            0x00,
        };
        appendLine(buf, 10, line10);
        buf.write(0x00);
        buf.write(0x00);
        byte[] payload = buf.toByteArray();

        assertEquals("10 \"??\"\r\n", BasicDetokenizer.listing(payload));
        // 0x81 -> U+2598 (QUADRANT UPPER LEFT), 0x80 -> U+00A0 (NBSP), par la table CpcCharset.
        assertEquals("10 \"\u2598\u00A0\"\r\n", BasicDetokenizer.spacedListing(payload));

        // trace(..., true) : m\u00EAme table CpcCharset dans le mode debug ('basic --debug --spaced'),
        // pour relire la correspondance octet<->Unicode directement sur un vrai fichier au besoin.
        assertEquals("\"??\"", BasicDetokenizer.trace(payload).get(2).text);
        assertEquals("\"\u2598\u00A0\"", BasicDetokenizer.trace(payload, true).get(2).text);
    }

    @Test
    void spacedListingMapsBlockGraphicsCodeToSupplementaryPlaneCodepoint() {
        // Les codes 0xC0-0xDF correspondent au bloc Unicode "Symbols for Legacy Computing"
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] line10 = {
            (byte) '"', (byte) 0xC0, (byte) '"',
            0x00,
        };
        appendLine(buf, 10, line10);
        buf.write(0x00);
        buf.write(0x00);
        byte[] payload = buf.toByteArray();

        String spaced = BasicDetokenizer.spacedListing(payload);
        assertEquals(0x1FBAA, spaced.codePointAt(4));
    }

    @Test
    void unterminatedStringRunningToEndOfLineDoesNotRenderTheLineTerminatorAsContent() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] line10 = {
            (byte) '"', (byte) 'x', 0x00, // chaîne ouverte par '"', jamais refermée
        };
        appendLine(buf, 10, line10);
        buf.write(0x00);
        buf.write(0x00);
        byte[] payload = buf.toByteArray();

        assertEquals("10 \"x\r\n", BasicDetokenizer.listing(payload));
        assertEquals("10 \"x\r\n", BasicDetokenizer.spacedListing(payload));
    }

    private static void appendLine(ByteArrayOutputStream buf, int lineNumber, byte[] content) {
        int lineLength = 4 + content.length;
        buf.write(lineLength & 0xFF);
        buf.write((lineLength >> 8) & 0xFF);
        buf.write(lineNumber & 0xFF);
        buf.write((lineNumber >> 8) & 0xFF);
        buf.write(content, 0, content.length);
    }
}
