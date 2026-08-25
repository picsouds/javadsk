package dsk.hex;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexDumpTest {

    @Test
    void formatsFullLineWithOffsetHexAndAscii() {
        byte[] data = "HELLO, WORLD!!!!".getBytes(StandardCharsets.US_ASCII); // 16 octets exactement

        // l'espace (0x20) devient '.' : condition d'iDSK "cur > 32", strictement, 32 exclu
        String expected = "#0000 48 45 4C 4C 4F 2C 20 57 4F 52 4C 44 21 21 21 21 | HELLO,.WORLD!!!!\n";
        assertEquals(expected, HexDump.dump(data));
    }

    @Test
    void padsPartialLastLineWithSpacesNotGarbage() {
        byte[] data = {0x41, 0x42, 0x43}; // "ABC", 3 octets < 16

        String result = HexDump.dump(data);
        assertTrue(result.startsWith("#0000 41 42 43 "));
        assertEquals("#0000 41 42 43 " + "   ".repeat(13) + "| ABC\n", result);
    }

    @Test
    void nonPrintableBytesAndSpaceBecomeDot() {
        byte[] data = {0x00, 0x20, 0x7F, (byte) 0xFF, 0x41};

        String result = HexDump.dump(data);
        assertTrue(result.contains("| ...." + "A\n"));
    }

    @Test
    void emptyInputProducesNoLines() {
        assertEquals("", HexDump.dump(new byte[0]));
    }

    @Test
    void secondLineStartsAtOffset16() {
        byte[] data = new byte[20];
        String result = HexDump.dump(data);
        assertTrue(result.contains("\n#0010 "));
    }
}
