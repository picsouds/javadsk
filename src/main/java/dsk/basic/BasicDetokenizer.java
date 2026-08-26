package dsk.basic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Port fidèle de {@code Basic()} d'iDSK (src/Basic.cpp, mode {@code IsBasic}) - mêmes tables et décodage. */
public final class BasicDetokenizer {

    private BasicDetokenizer() {
    }

    // Index = octet de token - 0x80. Partagée avec BasicTokenizer (sens inverse).
    static final String[] KEYWORDS = {
        "AFTER", "AUTO", "BORDER", "CALL", "CAT", "CHAIN", "CLEAR", "CLG",
        "CLOSEIN", "CLOSEOUT", "CLS", "CONT", "DATA", "DEF", "DEFINT", "DEFREAL",
        "DEFSTR", "DEG", "DELETE", "DIM", "DRAW", "DRAWR", "EDIT", "ELSE",
        "END", "ENT", "ENV", "ERASE", "ERROR", "EVERY", "FOR", "GOSUB",
        "GOTO", "IF", "INK", "INPUT", "KEY", "LET", "LINE", "LIST",
        "LOAD", "LOCATE", "MEMORY", "MERGE", "MID$", "MODE", "MOVE", "MOVER",
        "NEXT", "NEW", "ON", "ON BREAK", "ON ERROR GOTO", "SQ", "OPENIN", "OPENOUT",
        "ORIGIN", "OUT", "PAPER", "PEN", "PLOT", "PLOTR", "POKE", "PRINT",
        "'", "RAD", "RANDOMIZE", "READ", "RELEASE", "REM", "RENUM", "RESTORE",
        "RESUME", "RETURN", "RUN", "SAVE", "SOUND", "SPEED", "STOP", "SYMBOL",
        "TAG", "TAGOFF", "TROFF", "TRON", "WAIT", "WEND", "WHILE", "WIDTH",
        "WINDOW", "WRITE", "ZONE", "DI", "EI", "FILL", "GRAPHICS", "MASK",
        "FRAME", "CURSOR", "#E2", "ERL", "FN", "SPC", "STEP", "SWAP",
        "#E8", "#E9", "TAB", "THEN", "TO", "USING", ">", "=",
        ">=", "<", "<>", "<=", "+", "-", "*", "/",
        "^", "\\ ", "AND", "MOD", "OR", "XOR", "NOT", "#FF",
    };

    // Sélectionné par le token 0xFF suivi d'un octet < 0x80, indexé directement par cet octet.
    static final String[] FUNCTIONS = {
        "ABS", "ASC", "ATN", "CHR$", "CINT", "COS", "CREAL", "EXP",
        "FIX", "FRE", "INKEY", "INP", "INT", "JOY", "LEN", "LOG",
        "LOG10", "LOWER$", "PEEK", "REMAIN", "SGN", "SIN", "SPACE$", "SQ",
        "SQR", "STR$", "TAN", "UNT", "UPPER$", "VAL", "", "",
        "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "EOF", "ERR", "HIMEM", "INKEY$", "PI", "RND", "TIME", "XPOS",
        "YPOS", "DERR", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "",
        "", "BIN$", "DEC$", "HEX$", "INSTR", "LEFT$", "MAX", "MIN",
        "POS", "RIGHT$", "ROUND", "STRING$", "TEST", "TESTR", "COPYCHR$", "VPOS",
    };

    // Constantes entières littérales encodées sur un seul octet de token (0x0E-0x18).
    private static final String[] SMALL_NUMBERS = {
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
    };

    /** Token ELSE (0x80 | 0x17) : un ':' juste avant est supprimé (comme iDSK). */
    private static final int TOKEN_ELSE = 0x97;

    /** Tokens REM/"'" (0x80 | index dans KEYWORDS) : tout le reste de la ligne est du texte brut. */
    private static final int TOKEN_REM_APOSTROPHE = 0xC0;
    private static final int TOKEN_REM = 0xC5;

    /** DATA/DEFINT/DEFREAL/DEFSTR : argument littéral jusqu'au premier ':' réel (hors chaîne). */
    private static final int TOKEN_DATA = 0x8C;
    private static final int TOKEN_DEFINT = 0x8E;
    private static final int TOKEN_DEFREAL = 0x8F;
    private static final int TOKEN_DEFSTR = 0x90;

    // Opérateurs : jamais entourés d'espace (contrairement aux mots-clés "mots" CALL/PRINT/...) -
    // l'espacement n'est jamais stocké, il est calculé par le firmware à l'affichage.
    private static final Set<String> SYMBOL_KEYWORDS = Set.of(
            ">", "=", ">=", "<", "<>", "<=", "+", "-", "*", "/", "^", "\\ "
    );

    public static String listing(byte[] buf) {
        Run run = new Run(buf, false, false);
        run.execute();
        return sanitize(run.out);
    }

    /**
     * Comme {@link #listing}, mais espacée comme un vrai {@code SAVE"nom",A} - source de {@code put
     * --tokenize}. Ne sanitize pas en '?' : un octet personnalisé (accent via {@code SYMBOL}) doit
     * survivre tel quel pour ne pas se perdre au retokenize.
     */
    public static String spacedListing(byte[] buf) {
        Run run = new Run(buf, false, true);
        run.execute();
        return run.out.toString();
    }

    /** Comme {@link #listing}, mais retourne la trace token par token (offset, octets bruts, texte) au lieu du texte final. */
    public static List<BasicTraceEvent> trace(byte[] buf) {
        return trace(buf, false);
    }

    /** Comme {@link #trace(byte[])}, mais {@code spaced=true} rend le texte via {@link CpcCharset} au lieu du sanitize() façon iDSK. */
    public static List<BasicTraceEvent> trace(byte[] buf, boolean spaced) {
        Run run = new Run(buf, true, spaced);
        run.execute();
        return run.events;
    }

    /** Remplace par '?' tout caractère non imprimable (hors \r \n), comme la passe finale d'iDSK. */
    private static String sanitize(CharSequence s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(((c < 0x20 || c > 0x7E) && c != '\r' && c != '\n') ? '?' : c);
        }
        return b.toString();
    }

    /** État d'une exécution de détokenisation ; {@code events} reste {@code null} hors mode trace. */
    private static final class Run {
        final byte[] buf;
        final StringBuilder out = new StringBuilder();
        final List<BasicTraceEvent> events;
        final boolean spaced;
        int pos = 0;

        // Regroupement des runs de texte littéral consécutifs pour le mode trace.
        int literalStart = -1;
        int literalTextStart = -1;

        // Dernier token spécial émis (null si littéral/début de ligne) - décide si beforeSpecial() insère un espace.
        String lastSpecial = null;
        // lastSpecial juste avant le dernier ':', pour le restaurer si ce ':' est avalé devant ELSE.
        String lastSpecialBeforeColon = null;

        // Une variable juste avant un ':' avalé devant ELSE ne redonne jamais d'espace ("C8$:ELSE"
        // -> "C8$ELSE"), contrairement à un nombre ("570:ELSE" -> "570 ELSE") - vérifié sur CPC réel.
        private static final String VARIABLE_MARKER = " VAR";

        Run(byte[] buf, boolean tracing, boolean spaced) {
            this.buf = buf;
            this.events = tracing ? new ArrayList<>() : null;
            this.spaced = spaced;
        }

        private int byteAt(int p) {
            return (p >= 0 && p < buf.length) ? (buf[p] & 0xFF) : 0;
        }

        private int word(int p) {
            return byteAt(p) | (byteAt(p + 1) << 8);
        }

        private String hex(int value) {
            return Integer.toHexString(value).toUpperCase(Locale.ROOT);
        }

        /** À appeler juste avant d'ajouter le texte d'un token spécial (hors littéral). */
        private void beforeSpecial(String upcomingText) {
            if (spaced && lastSpecial != null
                    && !SYMBOL_KEYWORDS.contains(lastSpecial) && !SYMBOL_KEYWORDS.contains(upcomingText)) {
                out.append(' ');
            }
        }

        void execute() {
            while (true) {
                int fieldStart = pos;
                int lineLength = word(pos);
                pos += 2;
                if (lineLength == 0) {
                    flushLiteral(pos);
                    break;
                }
                recordEvent(fieldStart, pos, "longueur ligne", out.length());

                fieldStart = pos;
                int lineNumber = word(pos);
                pos += 2;
                int textStart = out.length();
                out.append(lineNumber).append(' ');
                recordEvent(fieldStart, pos, "numéro ligne", textStart);
                lastSpecial = null; // le numéro de ligne inclut déjà son propre espace final

                boolean inString = false;
                // Un '|' (RSX) littéral dans un REM se lit comme un vrai appel RSX et peut dévorer les
                // lignes suivantes (bug réel, partagé avec iDSK) ==> spacedListing() le corrige.
                boolean inRemComment = false;
                // Même défaut pour DATA/DEFINT/DEFREAL/DEFSTR, littéral jusqu'au premier ':' réel.
                boolean inDataLiteral = false;
                boolean dataLiteralInString = false;
                int token;
                do {
                    int tokenStart = pos;
                    token = byteAt(pos++);
                    //noinspection StatementWithEmptyBody
                    if (token == 0) {
                        // Fin de ligne : jamais du contenu, même chaîne restée ouverte (ex: RUN "prog.bas sans guillemet fermant).
                    } else if (spaced && inRemComment) {
                        appendLiteral(tokenStart, (char) token);
                    } else if (spaced && inDataLiteral && (token != 0x01 || dataLiteralInString)) {
                        if (token == '"') {
                            dataLiteralInString = !dataLiteralInString;
                        }
                        appendLiteral(tokenStart, (char) token);
                    } else if (inString) {
                        appendLiteral(tokenStart, (char) token);
                        if (token == '"') {
                            inString = false;
                        }
                    } else if (token > 0x7F && token < 0xFF) {
                        // 0xFF exclu : c'est le préfixe fonction/caractère étendu, géré à part par appendSpecialToken().
                        flushLiteral(tokenStart);
                        if (out.length() > 0 && out.charAt(out.length() - 1) == ':' && token == TOKEN_ELSE) {
                            // Un ':' juste avant ELSE est effacé et l'espacement d'avant restauré,
                            // comme si ce ':' n'avait jamais existé (parité iDSK).
                            out.setLength(out.length() - 1);
                            lastSpecial = VARIABLE_MARKER.equals(lastSpecialBeforeColon) ? null : lastSpecialBeforeColon;
                        }
                        if (token == TOKEN_REM || token == TOKEN_REM_APOSTROPHE) {
                            inRemComment = true;
                        } else if (token == TOKEN_DATA || token == TOKEN_DEFINT || token == TOKEN_DEFREAL
                                || token == TOKEN_DEFSTR) {
                            inDataLiteral = true;
                        }
                        String keyword = KEYWORDS[token & 0x7F];
                        // "\ " (division entière) porte un espace intégré (hérité tel quel de la
                        // table d'iDSK) qu'un vrai CPC n'affiche pas : on le retire en mode spaced.
                        String rendered = spaced ? keyword.stripTrailing() : keyword;
                        beforeSpecial(keyword);
                        int ts = out.length();
                        out.append(rendered);
                        recordEvent(tokenStart, pos, "mot-clé", ts);
                        lastSpecial = keyword;
                    } else if (token >= 0x0E && token <= 0x18) {
                        // Forme compacte 1 octet pour 0-10 seulement - au-delà, cf. les cases 0x19/0x1A/0x1F d'appendSpecialToken().
                        flushLiteral(tokenStart);
                        beforeSpecial("");
                        int ts = out.length();
                        out.append(SMALL_NUMBERS[token - 0x0E]);
                        recordEvent(tokenStart, pos, "nombre", ts);
                        lastSpecial = "";
                    } else if (token >= 0x20 && token < 0x7C) {
                        appendLiteral(tokenStart, (char) token);
                        if (token == '"') {
                            inString = true;
                        }
                    } else {
                        if (token == 0x01) {
                            inDataLiteral = false; // ':' réel qui termine la portion littérale DATA/DEFxxx
                        }
                        flushLiteral(tokenStart);
                        appendSpecialToken(tokenStart, token);
                    }
                } while (token != 0);

                flushLiteral(pos);
                out.append("\r\n");
            }
        }

        private void appendLiteral(int tokenStart, char c) {
            if (literalStart < 0) {
                literalStart = tokenStart;
                literalTextStart = out.length();
            }
            if (spaced) {
                // Représentation Unicode éditable au lieu de l'octet brut - seulement en mode spaced
                // (source de put --tokenize) ; listing() garde sanitize()/'?' pour iDSK.
                out.appendCodePoint(CpcCharset.toUnicode(c));
            } else {
                out.append(c);
            }
            lastSpecial = null;
        }

        /** @param endPos fin exclusive du run littéral (le token qui l'interrompt commence ici) */
        private void flushLiteral(int endPos) {
            if (literalStart < 0) {
                return;
            }
            recordEvent(literalStart, endPos, spaced ? "littéral (unicode)" : "littéral", literalTextStart);
            literalStart = -1;
            literalTextStart = -1;
        }

        /**
         * Décode les octets de token qui ne sont ni un mot-clé (0x80-0xFE) ni du texte littéral - cf.
         * <a href="https://cpctech.cpcwiki.de/docs/bastech.html">bastech.html</a> pour la table complète.
         */
        private void appendSpecialToken(int tokenStart, int token) {
            int ts;
            switch (token) {
                case 0x01:
                    ts = out.length();
                    out.append(':');
                    recordEvent(tokenStart, pos, "séparateur ':'", ts);
                    lastSpecialBeforeColon = lastSpecial;
                    lastSpecial = null;
                    return;
                case 0x02:
                    appendTypedVariable(tokenStart, '%', "variable entière");
                    return;
                case 0x03:
                    appendTypedVariable(tokenStart, '$', "variable chaîne");
                    return;
                case 0x04:
                    appendTypedVariable(tokenStart, '!', "variable réelle");
                    return;
                case 0x0B:
                case 0x0C:
                case 0x0D:
                    // 0x0D : seule valeur écrite par le tokeniseur texte->bytes (Tokenising.asm).
                    // 0x0B : réécriture runtime de 0x0D en mémoire, jamais issue du texte source.
                    // 0x0C : jamais observé
                    beforeSpecial("");
                    ts = out.length();
                    pos = appendVariableName(pos + 2);
                    recordEvent(tokenStart, pos, "variable", ts);
                    lastSpecial = VARIABLE_MARKER;
                    return;
                case 0x19:
                    beforeSpecial("");
                    ts = out.length();
                    out.append(byteAt(pos));
                    pos = pos + 1;
                    recordEvent(tokenStart, pos, "constante 8 bits", ts);
                    lastSpecial = "";
                    return;
                case 0x1A:
                case 0x1E:
                    beforeSpecial("");
                    ts = out.length();
                    out.append(word(pos));
                    pos = pos + 2;
                    recordEvent(tokenStart, pos, "constante 16 bits", ts);
                    lastSpecial = "";
                    return;
                case 0x1B:
                    beforeSpecial("");
                    ts = out.length();
                    // iDSK affiche "&X" en hexa par erreur (gardé pour la parité) ; spacedListing() encode en vrai binaire.
                    int value = word(pos);
                    out.append("&X").append(spaced ? Integer.toBinaryString(value) : hex(value));
                    pos = pos + 2;
                    recordEvent(tokenStart, pos, "constante &X", ts);
                    lastSpecial = "";
                    return;
                case 0x1C:
                    beforeSpecial("");
                    ts = out.length();
                    out.append('&').append(hex(word(pos)));
                    pos = pos + 2;
                    recordEvent(tokenStart, pos, "constante &", ts);
                    lastSpecial = "";
                    return;
                case 0x1F:
                    beforeSpecial("");
                    ts = out.length();
                    appendFloat(pos);
                    pos = pos + 5;
                    recordEvent(tokenStart, pos, "constante flottante", ts);
                    lastSpecial = "";
                    return;
                case 0x7C:
                    // '|' RSX n'est jamais précédé d'un espace, même après un mot-clé qui en voudrait un (vérifié sur CPC réel : "THEN|PR").
                    ts = out.length();
                    out.append('|');
                    pos = appendVariableName(pos + 1);
                    recordEvent(tokenStart, pos, "appel RSX (|)", ts);
                    lastSpecial = null;
                    return;
                case 0xFF: {
                    int fn = byteAt(pos);
                    String kind;
                    if (fn < 0x80) {
                        beforeSpecial("");
                        ts = out.length();
                        out.append(FUNCTIONS[fn]);
                        kind = "fonction";
                    } else {
                        ts = out.length();
                        // fn&0x7F est un octet de contrôle échappé (seul moyen de le stocker hors
                        // chaîne/REM/DATA)
                        if (spaced) {
                            out.appendCodePoint(CpcCharset.toUnicode(fn & 0x7F));
                        } else {
                            out.append((char) (fn & 0x7F));
                        }
                        kind = "caractère étendu";
                    }
                    lastSpecial = null;
                    pos = pos + 1;
                    recordEvent(tokenStart, pos, kind, ts);
                    return;
                }
                default:
                    // octet de token non utilisé par le format (ex: 0x00, 0x05-0x0A) : ignoré, pas d'événement.
            }
        }

        /** Variable typée (0x02 entière/0x03 chaîne/0x04 réelle) : même forme, seuls le suffixe et le libellé changent. */
        private void appendTypedVariable(int tokenStart, char suffix, String kind) {
            beforeSpecial("");
            int ts = out.length();
            pos = appendVariableName(pos + 2);
            out.append(suffix);
            recordEvent(tokenStart, pos, kind, ts);
            lastSpecial = VARIABLE_MARKER;
        }

        /** Lit un nom de variable/RSX (caractères 7 bits, le dernier a le bit 7 posé). */
        private int appendVariableName(int p) {
            int guard = 0;
            int b;
            do {
                b = byteAt(p++);
                out.append((char) (b & 0x7F));
            } while ((b & 0x80) == 0 && ++guard < 0xFF);
            return p;
        }

        /** Constante flottante CPC 5 octets (mantisse 32 bits + signe, exposant biaisé 129). */
        private void appendFloat(int p) {
            double value = getValue(p);

            String s = String.format(Locale.ROOT, "%f", value);
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && s.charAt(end - 1) == '.') {
                end--;
            }
            out.append(s, 0, end);
        }

        private double getValue(int p) {
            int b0 = byteAt(p);
            int b1 = byteAt(p + 1);
            int b2 = byteAt(p + 2);
            int b3 = byteAt(p + 3);
            int b4 = byteAt(p + 4);

            long mantissa = ((long) b2 << 16) + ((long) b1 << 8) + b0 + ((long) (b3 & 0x7F) << 24);
            double f = 1 + (mantissa / (double) 0x80000000L);
            if ((b3 & 0x80) != 0) {
                f = -f;
            }
            int exp = b4 - 129;
            return Math.scalb(f, exp);
        }

        // textStart plutôt que le texte déjà substring() : sinon l'allocation a lieu à chaque site
        // d'appel même hors mode trace (listing()/spacedListing(), le chemin de loin le plus emprunté).
        private void recordEvent(int startPos, int endPos, String kind, int textStart) {
            if (events == null) {
                return;
            }
            String text = out.substring(textStart);
            events.add(new BasicTraceEvent(startPos, Arrays.copyOfRange(buf, startPos, Math.min(endPos, buf.length)),
                    kind, spaced ? text : sanitize(text)));
        }
    }
}
