package dsk.amsdos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BasicProtectTest {

    @Test
    void decodeIsSelfInverse() {
        byte[] plain = new byte[300];
        for (int i = 0; i < plain.length; i++) {
            plain[i] = (byte) i;
        }

        byte[] roundTrip = BasicProtect.decode(BasicProtect.decode(plain));

        assertArrayEquals(plain, roundTrip);
    }

    @Test
    void decodeMatchesKeyExtractedFromARealProtectedDisk() {
        // Vecteur connu (retrocomputing.SE #4388, PROT-P.BAS) : offset payload 0x80 (= offset fichier
        // 0x100, dans une ligne REM composée uniquement d'octets 0x30 '0' en clair), un tour de clé
        // pile après le début du payload.
        byte[] payload = new byte[144];
        byte[] cipherAtKeyStart = {
            (byte) 0x9b, (byte) 0x1c, (byte) 0xdd, (byte) 0xda, (byte) 0x5c, (byte) 0x07, (byte) 0x0f, (byte) 0xdc,
            (byte) 0xab, (byte) 0xef, (byte) 0x4a, (byte) 0x3c, (byte) 0x0b, (byte) 0xe4, (byte) 0x5d, (byte) 0xc5,
        };
        System.arraycopy(cipherAtKeyStart, 0, payload, 128, cipherAtKeyStart.length);

        byte[] decoded = BasicProtect.decode(payload);

        byte[] expected = new byte[16];
        java.util.Arrays.fill(expected, (byte) 0x30);
        assertArrayEquals(expected, java.util.Arrays.copyOfRange(decoded, 128, 144));
    }
}
