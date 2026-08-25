package dsk.basic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie que {@link CpcCharset} est une vraie bijection sur les 256 octets CPC : chaque octet doit
 * round-tripper vers lui-même via toUnicode/toCpcByte, et surtout deux octets distincts ne doivent
 * jamais produire le même point de code Unicode (sinon la retokenisation ne peut plus les distinguer
 * - bug réel trouvé manuellement : l'octet 0xA0 collisionnait avec l'ASCII '^' 0x5E avant correction).
 */
class CpcCharsetTest {

    @Test
    void everyByteRoundTripsThroughUnicodeAndBackToItself() {
        for (int b = 0; b <= 0xFF; b++) {
            int cp = CpcCharset.toUnicode(b);
            assertEquals(b, CpcCharset.toCpcByte(cp), "octet " + Integer.toHexString(b) + " ne round-trip pas");
        }
    }

    @Test
    void noTwoDistinctBytesMapToTheSameCodepoint() {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int b = 0; b <= 0xFF; b++) {
            int cp = CpcCharset.toUnicode(b);
            Integer previous = seen.put(cp, b);
            assertNull(previous, "collision : octets 0x" + Integer.toHexString(previous == null ? 0 : previous)
                    + " et 0x" + Integer.toHexString(b) + " produisent tous deux U+"
                    + Integer.toHexString(cp));
        }
    }

    @Test
    void printableAsciiPassesThroughUnchanged() {
        for (int b = 0x20; b <= 0x7E; b++) {
            assertEquals(b, CpcCharset.toUnicode(b));
        }
    }

    @Test
    void toUnicodeRejectsValuesOutsideAByte() {
        assertThrows(IllegalArgumentException.class, () -> CpcCharset.toUnicode(500));
        assertThrows(IllegalArgumentException.class, () -> CpcCharset.toUnicode(-1));
    }
}
