package dsk.basic;

import java.util.HashMap;
import java.util.Map;

/**
 * Correspondance bijective octet CPC 0x80-0xFF &lt;-&gt; Unicode (table
 * <a href="https://en.wikipedia.org/wiki/Amstrad_CPC_character_set">...</a>), pour un texte "--spaced" lisible.
 */
final class CpcCharset {

    private CpcCharset() {
    }

    // Index = octet CPC - 0x80. 0xEF/0xFC/0xFD n'ont de point Unicode que depuis 16.0 ("Symbols for
    // Legacy Computing Supplement")
    private static final int[] CPC_TO_UNICODE = {
        // 0x80-0x8F
        0x00A0, 0x2598, 0x259D, 0x2580, 0x2596, 0x258C, 0x259E, 0x259B,
        0x2597, 0x259A, 0x2590, 0x259C, 0x2584, 0x2599, 0x259F, 0x2588,
        // 0x90-0x9F
        0x00B7, 0x2575, 0x2576, 0x2514, 0x2577, 0x2502, 0x250C, 0x251C,
        0x2574, 0x2518, 0x2500, 0x2534, 0x2510, 0x2524, 0x252C, 0x253C,
        // 0xA0-0xAF (0xA0 : U+02C6, pas '^' ASCII U+005E - sinon collision avec l'octet 0x5E)
        0x02C6, 0x00B4, 0x00A8, 0x00A3, 0x00A9, 0x00B6, 0x00A7, 0x2019,
        0x00BC, 0x00BD, 0x00BE, 0x00B1, 0x00F7, 0x00AC, 0x00BF, 0x00A1,
        // 0xB0-0xBF
        0x03B1, 0x03B2, 0x03B3, 0x03B4, 0x03B5, 0x03B8, 0x03BB, 0x03BC,
        0x03C0, 0x03C3, 0x03C6, 0x03C8, 0x03C7, 0x03C9, 0x03A3, 0x03A9,
        // 0xC0-0xCF
        0x1FBAA, 0x1FBAB, 0x1FBAD, 0x1FBAC, 0x1FBA7, 0x1FBA5, 0x1FBA6, 0x1FBA4,
        0x1FBA8, 0x1FBA9, 0x1FBAE, 0x2573, 0x2571, 0x2572, 0x1FBB5, 0x2592,
        // 0xD0-0xDF
        0x2594, 0x2595, 0x2581, 0x258F, 0x25E4, 0x25E5, 0x25E2, 0x25E3,
        0x1FBB8, 0x1FBB7, 0x1FBB9, 0x1FBB4, 0x1FBBC, 0x1FBBD, 0x1FBBE, 0x1FBBF,
        // 0xE0-0xEF
        0x263A, 0x2639, 0x2663, 0x2666, 0x2665, 0x2660, 0x25CB, 0x25CF,
        0x25A1, 0x25A0, 0x2642, 0x2640, 0x2669, 0x266A, 0x263C, 0x1CC57,
        // 0xF0-0xFF
        0x2B61, 0x2B63, 0x2B60, 0x2B62, 0x25B2, 0x25BC, 0x25B6, 0x25C0,
        0x1FBC6, 0x1FBC5, 0x1FBC7, 0x1FBC8, 0x1CC63, 0x1CC64, 0x2B65, 0x2B64,
    };

    private static final Map<Integer, Integer> UNICODE_TO_CPC = new HashMap<>();

    static {
        checkTableLength();
        for (int i = 0; i < CPC_TO_UNICODE.length; i++) {
            // put() écraserait silencieusement un doublon sans ça, cassant la bijectivité.
            Integer previous = UNICODE_TO_CPC.put(CPC_TO_UNICODE[i], 0x80 + i);
            if (previous != null) {
                throw new ExceptionInInitializerError(String.format("Doublon U+%s pour les octets CPC 0x%02X et 0x%02X",
                        Integer.toHexString(CPC_TO_UNICODE[i]), previous, 0x80 + i));
            }
        }
    }

    // garde-fou si modif CPC_TO_UNICODE
    @SuppressWarnings("ConstantConditions")
    private static void checkTableLength() {
        if (CPC_TO_UNICODE.length != 128) {
            throw new ExceptionInInitializerError("CPC_TO_UNICODE doit couvrir 0x80-0xFF (128 entrées), trouvé " + CPC_TO_UNICODE.length);
        }
    }

    // 0x00-0x1F/0x7F remappés vers un bloc Unicode dédié, jamais recopiés tels quels : un octet CPC
    // 0x0D/0x0A littéral (ex: CHR$(13) dans une chaîne) serait sinon indiscernable d'un vrai retour à
    // la ligne et casserait le split \r\n de BasicTokenizer#tokenizeProgram au retokenize.
    private static final int CONTROL_PICTURES_BASE = 0x2400;
    private static final int SYMBOL_FOR_DELETE = 0x2421;

    /** Octet CPC (0-255) -&gt; point de code Unicode à écrire dans le texte "--spaced". */
    static int toUnicode(int cpcByte) {
        if ((cpcByte & ~0xFF) != 0) { // couvre aussi les négatifs, pas juste > 255
            throw new IllegalArgumentException("Pas un octet CPC (0-255) : " + cpcByte);
        }
        if (cpcByte < 0x20) {
            return CONTROL_PICTURES_BASE + cpcByte;
        }
        if (cpcByte == 0x7F) {
            return SYMBOL_FOR_DELETE;
        }
        if (cpcByte < 0x80) {
            return cpcByte;
        }
        return CPC_TO_UNICODE[cpcByte - 0x80];
    }

    /** Inverse de {@link #toUnicode} ; lève si le caractère ne correspond à aucun octet CPC connu. */
    static int toCpcByte(int codepoint) {
        if (codepoint >= CONTROL_PICTURES_BASE && codepoint <= CONTROL_PICTURES_BASE + 0x1F) {
            return codepoint - CONTROL_PICTURES_BASE;
        }
        if (codepoint == SYMBOL_FOR_DELETE) {
            return 0x7F;
        }
        if (codepoint < 0x80) {
            return codepoint;
        }
        Integer b = UNICODE_TO_CPC.get(codepoint);
        if (b != null) {
            return b;
        }
        if (codepoint <= 0xFF) {
            // Caractère Latin-1 non utilisé par la table CPC
            return codepoint;
        }
        throw new IllegalArgumentException(
                "Caractère U+" + Integer.toHexString(codepoint).toUpperCase(java.util.Locale.ROOT)
                        + " sans correspondance dans le jeu de caractères Amstrad CPC");
    }
}
