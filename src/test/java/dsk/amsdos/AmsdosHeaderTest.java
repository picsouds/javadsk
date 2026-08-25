package dsk.amsdos;

import dsk.support.AmsdosHeaderBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmsdosHeaderTest {

    @Test
    void validChecksumIsRecognized() {
        byte[] raw = AmsdosHeaderBuilder.build("TEST", "BIN", AmsdosHeader.TYPE_BINARY,
                0x4000, 0x4000, 11);

        AmsdosHeader header = AmsdosHeader.parse(raw);

        assertTrue(header.isValid());
        assertEquals("TEST", header.filename);
        assertEquals("BIN", header.extension);
        assertEquals(AmsdosHeader.TYPE_BINARY, header.fileType);
        assertEquals(0x4000, header.loadAddress);
        assertEquals(0x4000, header.entryAddress);
        assertEquals(11, header.logicalLength);
    }

    @Test
    void corruptedChecksumIsRejected() {
        byte[] raw = AmsdosHeaderBuilder.build("TEST", "BIN", AmsdosHeader.TYPE_BINARY,
                0x4000, 0x4000, 11);
        raw[0x43] = (byte) (raw[0x43] ^ 0xFF); // altère le checksum stocké

        AmsdosHeader header = AmsdosHeader.parse(raw);

        assertFalse(header.isValid());
    }

    @Test
    void filenameAndExtensionAreTrimmed() {
        byte[] raw = AmsdosHeaderBuilder.build("A", "B", AmsdosHeader.TYPE_BASIC, 0, 0, 0);

        AmsdosHeader header = AmsdosHeader.parse(raw);

        assertEquals("A", header.filename);
        assertEquals("B", header.extension);
    }

    @Test
    void shortBufferIsPaddedInsteadOfThrowing() {
        byte[] shortBuf = new byte[10]; // plus court que HEADER_SIZE (128)

        AmsdosHeader header = AmsdosHeader.parse(shortBuf);

        // buffer tout à zéro une fois complété -> checksum stocké et calculé valent tous deux 0,
        // mais isValid() doit rester faux (pas de vrai header sur un fichier aussi court) : bug réel
        // trouvé sur un vrai disque (entrée catalogue 0 octet) - le "0 == 0" coïncidait avec un
        // header valide et payloadOf plantait ensuite sur l'arraycopy (source trop courte).
        assertEquals(0, header.checksum);
        assertEquals(0, header.computedChecksum);
        assertFalse(header.isValid());
        // Pas de header valide -> payloadOf renvoie le buffer tel quel, sans tenter l'arraycopy.
        assertEquals(shortBuf.length, AmsdosHeader.payloadOf(shortBuf).length);
    }

    @Test
    void payloadOfOnEmptyBufferDoesNotThrow() {
        assertEquals(0, AmsdosHeader.payloadOf(new byte[0]).length);
    }

    @Test
    void fileTypeLabels() {
        assertEquals("Basic", AmsdosHeader.parse(
                AmsdosHeaderBuilder.build("A", "BAS", AmsdosHeader.TYPE_BASIC, 0, 0, 0)).fileTypeLabel());
        assertEquals("Basic protégé", AmsdosHeader.parse(
                AmsdosHeaderBuilder.build("A", "BAS", AmsdosHeader.TYPE_BASIC_PROTECTED, 0, 0, 0)).fileTypeLabel());
        assertEquals("Binaire", AmsdosHeader.parse(
                AmsdosHeaderBuilder.build("A", "BIN", AmsdosHeader.TYPE_BINARY, 0, 0, 0)).fileTypeLabel());
        assertEquals("Inconnu(9)", AmsdosHeader.parse(
                AmsdosHeaderBuilder.build("A", "XXX", 9, 0, 0, 0)).fileTypeLabel());
    }
}
