package dsk.basic;

/**
 * Un événement du mode debug de {@link BasicDetokenizer#trace} : une décision de décodage
 * (position dans le fichier d'origine, octets bruts consommés, type, texte produit).
 */
public final class BasicTraceEvent {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    public final int offset;
    public final byte[] rawBytes;
    public final String kind;
    public final String text;

    BasicTraceEvent(int offset, byte[] rawBytes, String kind, String text) {
        this.offset = offset;
        this.rawBytes = rawBytes;
        this.kind = kind;
        this.text = text;
    }

    public String hex() {
        StringBuilder sb = new StringBuilder(Math.max(rawBytes.length * 3 - 1, 0));
        for (int i = 0; i < rawBytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            int b = rawBytes[i] & 0xFF;
            sb.append(HEX_DIGITS[b >>> 4]).append(HEX_DIGITS[b & 0x0F]);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        String quoted = text.isEmpty() ? "(rien)" : "\"" + text.replace("\r\n", "\\r\\n") + "\"";
        return String.format("$%04X [%-23s] %-20s -> %s", offset, hex(), kind, quoted);
    }
}
