package dsk.hex;

/**
 * Format iDSK ({@code DSK::Hexdecimal}, GestDsk.cpp). Vérifié sur le binaire réel : son
 * {@code snprintf(OffSet, 6, "#%.4X:", ...)} tronque le ':' final (buffer de 6 trop court) - offset
 * sans ':' reproduit à l'identique. Seule vraie divergence : sa dernière ligne déborde d'un octet
 * au-delà du fichier, ici simplement complétée de blancs.
 */
public final class HexDump {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HexDump() {
    }

    private static void appendHexByte(StringBuilder sb, int b) {
        sb.append(HEX[(b >>> 4) & 0xF]);
        sb.append(HEX[b & 0xF]);
    }

    /** Comme {@code %04X} : 4 chiffres minimum, mais grandit sans tronquer au-delà de 0xFFFF. */
    private static void appendHexWord(StringBuilder sb, int w) {
        int nibbles = 4;
        while ((w >>> (nibbles * 4)) != 0) {
            nibbles++;
        }
        for (int shift = (nibbles - 1) * 4; shift >= 0; shift -= 4) {
            sb.append(HEX[(w >>> shift) & 0xF]);
        }
    }

    public static String dump(byte[] data) {
        StringBuilder out = new StringBuilder(((data.length + 15) / 16) * 73);

        for (int offset = 0; offset < data.length; offset += 16) {
            out.append('#');
            appendHexWord(out, offset);
            out.append(' ');

            StringBuilder ascii = new StringBuilder(16);

            int end = Math.min(offset + 16, data.length);

            for (int pos = offset; pos < end; pos++) {
                int b = data[pos] & 0xFF;
                appendHexByte(out, b);
                out.append(' ');
                ascii.append((b > 32 && b < 125) ? (char) b : '.');
            }

            out.append("   ".repeat(offset + 16 - end));

            out.append("| ").append(ascii).append('\n');
        }

        return out.toString();
    }
}
