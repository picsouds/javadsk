package dsk.basic;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Inverse de {@link BasicDetokenizer#spacedListing} : reproduit ce qu'un vrai {@code SAVE"nom"}
 * (tokenisé) aurait produit, car le LOAD",A" natif échoue sur certains programmes réels.
 */
public final class BasicTokenizer {

    private BasicTokenizer() {
    }

    private static final Map<String, Integer> KEYWORD_TOKENS = new LinkedHashMap<>();
    private static final Map<String, Integer> FUNCTION_TOKENS = new LinkedHashMap<>();
    private static final List<String> KEYWORDS_LONGEST_FIRST;
    private static final List<String> FUNCTIONS_LONGEST_FIRST;
    // Groupé par 1re lettre : évite de scanner ~90 mots clés / ~64 fonctions (cf. benchmarks/).
    private static final Map<Character, List<String>> KEYWORDS_BY_FIRST_CHAR;
    private static final Map<Character, List<String>> FUNCTIONS_BY_FIRST_CHAR;

    // Opérateurs : jamais alphabétiques, donc traités à part de KEYWORDS_LONGEST_FIRST (cf. tryOperator).
    private static final String[] OPERATORS_LONGEST_FIRST = {
            ">=", "<>", "<=", ">", "=", "<", "+", "-", "*", "/", "^", "\\"
    };

    static {
        for (int i = 0; i < BasicDetokenizer.KEYWORDS.length; i++) {
            String kw = BasicDetokenizer.KEYWORDS[i].strip(); // "\ " -> "\" (espace d'affichage iDSK, jamais tapé)
            if (kw.isEmpty() || kw.startsWith("#")) {
                continue; // codes non utilisés par le format
            }
            KEYWORD_TOKENS.putIfAbsent(kw, 0x80 + i);
        }
        for (int i = 0; i < BasicDetokenizer.FUNCTIONS.length; i++) {
            String fn = BasicDetokenizer.FUNCTIONS[i];
            if (!fn.isEmpty()) {
                FUNCTION_TOKENS.putIfAbsent(fn, i);
            }
        }
        KEYWORDS_LONGEST_FIRST = new ArrayList<>(KEYWORD_TOKENS.keySet());
        KEYWORDS_LONGEST_FIRST.removeIf(kw -> kw.indexOf(' ') >= 0 || kw.equals("SQ"));
        KEYWORDS_LONGEST_FIRST.sort((a, b) -> b.length() - a.length());
        FUNCTIONS_LONGEST_FIRST = new ArrayList<>(FUNCTION_TOKENS.keySet());
        FUNCTIONS_LONGEST_FIRST.sort((a, b) -> b.length() - a.length());
        KEYWORDS_BY_FIRST_CHAR = groupByFirstChar(KEYWORDS_LONGEST_FIRST);
        FUNCTIONS_BY_FIRST_CHAR = groupByFirstChar(FUNCTIONS_LONGEST_FIRST);
    }

    private static Map<Character, List<String>> groupByFirstChar(List<String> longestFirst) {
        Map<Character, List<String>> byFirstChar = new HashMap<>();
        for (String candidate : longestFirst) {
            char first = Character.toUpperCase(candidate.charAt(0));
            byFirstChar.computeIfAbsent(first, k -> new ArrayList<>()).add(candidate);
        }
        return byFirstChar;
    }

    private static final class Tokens {
        static final int DATA = KEYWORD_TOKENS.get("DATA");
        static final int DEFINT = KEYWORD_TOKENS.get("DEFINT");
        static final int DEFSTR = KEYWORD_TOKENS.get("DEFSTR");
        static final int DEFREAL = KEYWORD_TOKENS.get("DEFREAL");
        static final int REM = KEYWORD_TOKENS.get("REM");
        static final int REM_APOSTROPHE = KEYWORD_TOKENS.get("'");

        static final int GOTO = KEYWORD_TOKENS.get("GOTO");
        static final int GOSUB = KEYWORD_TOKENS.get("GOSUB");
        static final int RESTORE = KEYWORD_TOKENS.get("RESTORE");
        static final int LIST = KEYWORD_TOKENS.get("LIST");
        static final int RUN = KEYWORD_TOKENS.get("RUN");
        static final int AUTO = KEYWORD_TOKENS.get("AUTO");
        static final int RENUM = KEYWORD_TOKENS.get("RENUM");
        static final int DELETE = KEYWORD_TOKENS.get("DELETE");
        static final int EDIT = KEYWORD_TOKENS.get("EDIT");
        static final int ERL = KEYWORD_TOKENS.get("ERL");
        static final int THEN = KEYWORD_TOKENS.get("THEN");
        static final int ELSE = KEYWORD_TOKENS.get("ELSE");
        static final int ON = KEYWORD_TOKENS.get("ON");
        static final int PRINT = KEYWORD_TOKENS.get("PRINT");

        private Tokens() {
        }
    }

    // Pas de switch possible : Tokens.XXX est calculé au chargement, pas une constante de compilation.
    private static boolean isRemToken(int token) {
        return token == Tokens.REM || token == Tokens.REM_APOSTROPHE;
    }

    private static boolean startsLiteralUntilColon(int token) {
        return token == Tokens.DATA || token == Tokens.DEFINT || token == Tokens.DEFSTR || token == Tokens.DEFREAL;
    }

    // Table ROM "keywords_taking_line_numbers" (Tokenising.asm) : le nombre qui suit encode une
    // référence de ligne (0x1E) et non une constante (0x19/0x1A) - sauf ON GOTO/GOSUB, seul cas où
    // plusieurs cibles séparées par des virgules restent toutes des références de ligne.
    private static boolean impliesLineReferenceArgument(int token) {
        return token == Tokens.GOTO || token == Tokens.GOSUB || token == Tokens.RESTORE
                || token == Tokens.LIST || token == Tokens.RUN || token == Tokens.AUTO
                || token == Tokens.RENUM || token == Tokens.DELETE || token == Tokens.EDIT
                || token == Tokens.ERL;
    }

    private static boolean isThenOrElse(int token) {
        return token == Tokens.THEN || token == Tokens.ELSE;
    }

    private static boolean isGotoOrGosub(int token) {
        return token == Tokens.GOTO || token == Tokens.GOSUB;
    }

    /** Sortie lisible par {@link BasicDetokenizer} : par ligne longueur(2)+numéro(2)+contenu+0x00, fin 0x00 0x00. */
    public static byte[] tokenizeProgram(String source) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (String rawLine : source.split("\r\n|\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            int i = 0;
            while (i < line.length() && Character.isDigit(line.charAt(i))) {
                i++;
            }
            if (i == 0) {
                throw new IllegalArgumentException("Ligne sans numéro : " + line);
            }
            int lineNumber = Integer.parseInt(line.substring(0, i));
            while (i < line.length() && line.charAt(i) == ' ') {
                i++;
            }
            byte[] content = tokenizeLine(line.substring(i));

            int recordLength = 2 + 2 + content.length + 1;
            writeU16(body, recordLength);
            writeU16(body, lineNumber);
            body.write(content, 0, content.length);
            body.write(0x00);
        }
        body.write(0x00);
        body.write(0x00);
        return body.toByteArray();
    }

    private static byte[] tokenizeLine(String text) {
        return new LineTokenizer(text).run();
    }

    /** Partagé entre {@link LineTokenizer} et {@link NumberTokenizer}. */
    private static final class Cursor {
        private final String text;
        private int pos;

        Cursor(String text) {
            this.text = text;
        }

        int pos() {
            return pos;
        }

        boolean hasMore() {
            return pos < text.length();
        }

        /** Caractère courant, ou {@code '\0'} en fin de texte (jamais d'exception hors bornes). */
        char peek() {
            return peekAt(pos);
        }

        /** Caractère à {@code pos + offset}, ou {@code '\0'} hors bornes. */
        char peek(int offset) {
            return peekAt(pos + offset);
        }

        /** Caractère à une position absolue (typiquement un résultat de {@link #endOfRun}). */
        private char peekAt(int absolutePos) {
            return (absolutePos >= 0 && absolutePos < text.length()) ? text.charAt(absolutePos) : '\0';
        }

        int peekCodePoint() {
            return text.codePointAt(pos);
        }

        void advance() {
            pos++;
        }

        void advanceBy(int chars) {
            pos += chars;
        }

        void advanceCodePoint() {
            pos += Character.charCount(peekCodePoint());
        }

        void moveTo(int newPos) {
            pos = newPos;
        }

        String substring(int start, int end) {
            return text.substring(start, end);
        }

        int endOfRun(IntPredicate condition) {
            int j = pos;
            while (j < text.length() && condition.test(text.charAt(j))) {
                j++;
            }
            return j;
        }

        /** Compare {@code candidate} au texte à la position courante, sans allouer de sous-chaîne. */
        boolean regionMatchesIgnoreCase(String candidate) {
            return text.regionMatches(true, pos, candidate, 0, candidate.length());
        }
    }

    /** Chaque {@code tryXXX()} consomme le {@link Cursor} pour un cas précis ou renvoie {@code false} sans effet. */
    private static final class LineTokenizer {
        private final Cursor cursor;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final TokenizerState s = new TokenizerState();

        /** Drapeaux/état de la machine à états, regroupés pour ne pas polluer {@link LineTokenizer}. */
        private static final class TokenizerState {
            boolean inString;
            boolean literalRestOfLine; // REM/'
            boolean literalUntilColon; // DATA/DEFINT/DEFSTR/DEFREAL
            boolean literalInString; // chaîne entre guillemets à l'intérieur du littéral ci-dessus
            boolean expectLineRef;
            boolean afterOnGotoGosub;
            // Reste vrai jusqu'au ':' de fin d'instruction (pas juste le token suivant) : "ON <expr>
            // GOTO 1000,2000" a une expression arbitraire entre ON et GOTO.
            boolean lastWasOn;
        }

        LineTokenizer(String text) {
            this.cursor = new Cursor(text);
        }

        byte[] run() {
            while (cursor.hasMore()) {
                if (!tryOneToken()) {
                    writePrintableFallback();
                }
            }
            return out.toByteArray();
        }

        /** Essaie chaque cas dans l'ordre, s'arrête au premier qui consomme du texte. */
        private boolean tryOneToken() {
            return tryStringContent() || tryRemComment() || tryDataLiteral() || tryStringStart()
                    || tryColon() || tryComma() || tryPrintShorthand() || tryRsxCall()
                    || tryEscapedControlChar() || tryNumber()
                    || trySpace() || tryKeyword() || tryFunction() || tryVariable() || tryOperator();
        }

        // Octet de contrôle hors chaîne/REM/DATA : seul moyen de le stocker, l'échappement ROM
        // "FF <0x80|octet>" (cf. BasicDetokenizer, cas 0xFF fn>=0x80).
        private boolean tryEscapedControlChar() {
            int cp = cursor.peekCodePoint();
            if (!CpcCharset.isControlPicture(cp)) {
                return false;
            }
            out.write(0xFF);
            out.write(0x80 | CpcCharset.toCpcByte(cp));
            cursor.advanceCodePoint();
            s.expectLineRef = false;
            return true;
        }

        private boolean tryStringContent() {
            if (!s.inString) {
                return false;
            }
            // CodePoint et pas char : CpcCharset mappe certains octets CPC hors du plan de base Unicode.
            int b = CpcCharset.toCpcByte(cursor.peekCodePoint());
            out.write(b);
            if (b == '"') {
                s.inString = false;
            }
            cursor.advanceCodePoint();
            return true;
        }

        private boolean tryRemComment() {
            if (!s.literalRestOfLine) {
                return false;
            }
            out.write(CpcCharset.toCpcByte(cursor.peekCodePoint()));
            cursor.advanceCodePoint();
            return true;
        }

        private boolean tryDataLiteral() {
            if (!s.literalUntilColon) {
                return false;
            }
            char c = cursor.peek();
            // ':' entre guillemets (DATA"*IMPORTANT*: ...") = texte, pas séparateur d'instruction.
            if (c == '"') {
                s.literalInString = !s.literalInString;
                out.write(c);
                cursor.advance();
            } else if (c == ':' && !s.literalInString) {
                out.write(0x01);
                s.literalUntilColon = false;
                s.expectLineRef = false;
                s.afterOnGotoGosub = false;
                s.lastWasOn = false;
                cursor.advance();
            } else {
                out.write(CpcCharset.toCpcByte(cursor.peekCodePoint()));
                cursor.advanceCodePoint();
            }
            return true;
        }

        private boolean tryStringStart() {
            if (cursor.peek() != '"') {
                return false;
            }
            out.write('"');
            s.inString = true;
            cursor.advance();
            return true;
        }

        private boolean tryColon() {
            if (cursor.peek() != ':') {
                return false;
            }
            out.write(0x01);
            s.expectLineRef = false;
            s.afterOnGotoGosub = false;
            s.lastWasOn = false;
            cursor.advance();
            return true;
        }

        private boolean tryComma() {
            if (cursor.peek() != ',') {
                return false;
            }
            out.write(',');
            cursor.advance();
            return true;
        }

        private boolean tryRsxCall() {
            if (cursor.peek() != '|') {
                return false;
            }
            cursor.advance();
            cursor.moveTo(cursor.endOfRun(ch -> ch == ' '));
            int j = cursor.endOfRun(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '.');
            out.write(0x7C);
            out.write(0x00);
            writeName(cursor.substring(cursor.pos(), j));
            cursor.moveTo(j);
            s.expectLineRef = false;
            return true;
        }

        // 7 bits par caractère, bit 7 posé sur le dernier. Casse préservée : seuls les mots-clés
        // sont insensibles à la casse côté CPC, pas les noms de variable.
        private void writeName(String name) {
            byte[] raw = name.getBytes(StandardCharsets.US_ASCII);
            if (raw.length == 0) {
                out.write(0x80);
                return;
            }
            for (int k = 0; k < raw.length - 1; k++) {
                out.write(raw[k] & 0x7F);
            }
            out.write((raw[raw.length - 1] & 0x7F) | 0x80);
        }

        private boolean tryNumber() {
            char c = cursor.peek();
            // Un flottant peut commencer directement par le point (".5" = 0.5, syntaxe CPC valide)
            // la ROM traite '.' et chiffre de façon identique en entrée de nombre (Tokenising.asm).
            boolean leadingDot = c == '.' && Character.isDigit(cursor.peek(1));
            if (!(Character.isDigit(c) || leadingDot || (c == '&' && isHexOrBinStart(cursor.peek(1))))) {
                return false;
            }
            new NumberTokenizer(out, cursor, s.expectLineRef).encode();
            if (!s.afterOnGotoGosub) {
                s.expectLineRef = false;
            }
            return true;
        }

        private boolean trySpace() {
            if (cursor.peek() != ' ') {
                return false;
            }
            // Jamais stocké : artefact d'affichage synthétisé par spacedListing.
            cursor.advance();
            return true;
        }

        // Toujours niée (garde de sortie anticipée)
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean isIdentifierStart(char c) {
            return Character.isLetter(c) || c == '\'';
        }

        private boolean tryKeyword() {
            if (!isIdentifierStart(cursor.peek())) {
                return false;
            }
            String kw = matchLongestFirst(cursor, KEYWORDS_BY_FIRST_CHAR);
            if (kw == null) {
                return false;
            }
            writeKeyword(kw);
            return true;
        }

        private boolean tryFunction() {
            if (!isIdentifierStart(cursor.peek())) {
                return false;
            }
            String fn = matchLongestFirst(cursor, FUNCTIONS_BY_FIRST_CHAR);
            if (fn == null) {
                return false;
            }
            out.write(0xFF);
            out.write(FUNCTION_TOKENS.get(fn));
            cursor.advanceBy(fn.length());
            s.expectLineRef = false;
            return true;
        }

        private boolean tryVariable() {
            if (!isIdentifierStart(cursor.peek())) {
                return false;
            }
            writeVariable();
            return true;
        }

        private void writeKeyword(String kw) {
            cursor.advanceBy(kw.length());
            writeKeywordToken(KEYWORD_TOKENS.get(kw));
        }

        /** @param token déjà résolu ; le curseur doit déjà être avancé au-delà du texte source consommé. */
        private void writeKeywordToken(int token) {
            if (token == Tokens.ELSE && out.size() > 0) {
                // spacedListing efface un ':' devant ELSE : un ':' visible ici veut dire 2 octets 0x01 à l'origine.
                out.write(0x01);
            }
            out.write(token);
            if (isRemToken(token)) {
                s.literalRestOfLine = true;
                return;
            }
            if (startsLiteralUntilColon(token)) {
                s.literalUntilColon = true;
                s.literalInString = false;
                return;
            }
            s.expectLineRef = impliesLineReferenceArgument(token);
            if (isThenOrElse(token)) {
                int afterSpaces = cursor.endOfRun(ch -> ch == ' ');
                s.expectLineRef = Character.isDigit(cursor.peek(afterSpaces - cursor.pos()));
            }
            s.afterOnGotoGosub = isGotoOrGosub(token) && s.lastWasOn;
            s.lastWasOn = (token == Tokens.ON);
        }

        // '?' est un synonyme de PRINT, converti au token PRINT dès la tokenisation (Tokenising.asm)
        private boolean tryPrintShorthand() {
            if (cursor.peek() != '?') {
                return false;
            }
            cursor.advance();
            writeKeywordToken(Tokens.PRINT);
            return true;
        }

        private void writeVariable() {
            // '.' est valide dans un nom de variable CPC ("min.interval%", "erl.") - cf. matchLongestFirst.
            int j = cursor.endOfRun(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '.');
            j = trimGluedElseSuffix(j);
            String name = cursor.substring(cursor.pos(), j);
            cursor.moveTo(j);
            // Toujours 0x0D ici (Tokenising.asm)
            int typeToken = 0x0D;
            if (cursor.peek() == '$') {
                typeToken = 0x03;
                cursor.advance();
            } else if (cursor.peek() == '%') {
                typeToken = 0x02;
                cursor.advance();
            } else if (cursor.peek() == '!') {
                typeToken = 0x04;
                cursor.advance();
            }
            out.write(typeToken);
            out.write(0x00);
            out.write(0x00);
            writeName(name);
            s.expectLineRef = false;
        }

        // "nwELSEx=1" (ELSE collé, sans espace) : le scan de lettres avale ELSE dans le nom de
        // variable si on ne le débusque pas explicitement, n'importe où dans la plage (pas que la fin).
        private int trimGluedElseSuffix(int end) {
            int nameStart = cursor.pos();
            for (int i = nameStart + 1; i + 4 <= end; i++) {
                if (cursor.substring(i, i + 4).equalsIgnoreCase("ELSE")) {
                    return i;
                }
            }
            return end;
        }

        private boolean tryOperator() {
            char c = cursor.peek();
            if (!isOperatorChar(c)) {
                return false;
            }
            String op = matchOperator(cursor);
            if (op == null) {
                return false;
            }
            out.write(KEYWORD_TOKENS.get(op));
            cursor.advanceBy(op.length());
            s.expectLineRef = false;
            return true;
        }

        private void writePrintableFallback() {
            char c = cursor.peek();
            if (c >= 0x20 && c <= 0x7E) {
                out.write(c);
            }
            cursor.advance();
        }
    }

    private static String matchLongestFirst(Cursor cursor, Map<Character, List<String>> candidatesByFirstChar) {
        List<String> candidates = candidatesByFirstChar.get(Character.toUpperCase(cursor.peek()));
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (!cursor.regionMatchesIgnoreCase(candidate)) {
                continue;
            }
            char lastChar = candidate.charAt(candidate.length() - 1);
            // DATA/DEFINT/DEFSTR/DEFREAL exemptés de la garde anti-collision (argument toujours
            // littéral, pas de risque) ; REM reste gardé, vrai préfixe de la fonction REMAIN.
            Integer token = KEYWORD_TOKENS.get(candidate);
            boolean entersLiteralMode = token != null && startsLiteralUntilColon(token);
            if (!entersLiteralMode && (Character.isLetter(lastChar) || lastChar == '$')) {
                char after = cursor.peek(candidate.length());
                if (Character.isLetterOrDigit(after) || after == '$' || after == '%' || after == '!' || after == '_'
                        || after == '.') {
                    continue; // identifiant plus long, ex: variable "TOTAL" vs mot-clé "TO"
                }
            }
            return candidate;
        }
        return null;
    }

    private static String matchOperator(Cursor cursor) {
        for (String op : OPERATORS_LONGEST_FIRST) {
            if (cursor.regionMatchesIgnoreCase(op)) {
                return op;
            }
        }
        return null;
    }

    private static boolean isOperatorChar(char c) {
        return ">=<+-*/^\\".indexOf(c) >= 0;
    }

    private static boolean isHexOrBinStart(char c) {
        char u = Character.toUpperCase(c);
        return (u >= '0' && u <= '9') || (u >= 'A' && u <= 'F') || u == 'X';
    }

    /** Encode hexa/binaire/entier/flottant/référence de ligne - extrait à part, trop gros pour une méthode. */
    private static final class NumberTokenizer {
        private final ByteArrayOutputStream out;
        private final Cursor cursor;
        private final boolean asLineRef;

        NumberTokenizer(ByteArrayOutputStream out, Cursor cursor, boolean asLineRef) {
            this.out = out;
            this.cursor = cursor;
            this.asLineRef = asLineRef;
        }

        void encode() {
            if (cursor.peek() == '&') {
                cursor.advance();
                boolean binary = cursor.hasMore() && Character.toUpperCase(cursor.peek()) == 'X';
                if (binary) {
                    cursor.advance();
                    encodeBinary();
                } else {
                    encodeHex();
                }
                return;
            }
            String numText = scanDecimalOrFloatText();
            boolean isFloat = numText.indexOf('.') >= 0 || numText.toUpperCase(Locale.ROOT).indexOf('E') >= 0;
            if (isFloat) {
                encodeFloat(Double.parseDouble(numText));
            } else {
                encodeInteger(numText);
            }
        }

        private void encodeHex() {
            int start = cursor.pos();
            while (cursor.hasMore() && isHexDigit(cursor.peek())) {
                cursor.advance();
            }
            out.write(0x1C);
            writeU16(out, Integer.parseInt(cursor.substring(start, cursor.pos()), 16));
        }

        private void encodeBinary() {
            int start = cursor.pos();
            while (cursor.hasMore() && (cursor.peek() == '0' || cursor.peek() == '1')) {
                cursor.advance();
            }
            out.write(0x1B);
            writeU16(out, Integer.parseInt(cursor.substring(start, cursor.pos()), 2));
        }

        private String scanDecimalOrFloatText() {
            int start = cursor.pos();
            while (cursor.hasMore() && (Character.isDigit(cursor.peek()) || cursor.peek() == '.')) {
                cursor.advance();
            }
            if (cursor.hasMore() && Character.toUpperCase(cursor.peek()) == 'E') {
                int save = cursor.pos();
                cursor.advance();
                if (cursor.hasMore() && (cursor.peek() == '+' || cursor.peek() == '-')) {
                    cursor.advance();
                }
                if (cursor.hasMore() && Character.isDigit(cursor.peek())) {
                    while (cursor.hasMore() && Character.isDigit(cursor.peek())) {
                        cursor.advance();
                    }
                } else {
                    cursor.moveTo(save); // "E" pas suivi de chiffres, ne fait pas partie du nombre
                }
            }
            return cursor.substring(start, cursor.pos());
        }

        private void encodeInteger(String numText) {
            if (asLineRef) {
                out.write(0x1E);
                writeU16(out, Integer.parseInt(numText));
                return;
            }
            long v;
            try {
                v = Long.parseLong(numText);
            } catch (NumberFormatException tooLargeForLong) {
                encodeFloat(Double.parseDouble(numText));
                return;
            }
            // Seuils vérifiés réels : 0x1A s'arrête à 32767 (16 bits signé), pas 65535 ; "10" tapé
            // est encodé "19 0A" (8 bits), jamais le compact 0x18 malgré SMALL_NUMBERS qui va jusque-là.
            if (v >= 0 && v <= 9) {
                out.write(0x0E + (int) v);
            } else if (v <= 255) {
                out.write(0x19);
                out.write((int) v);
            } else if (v <= 32767) {
                out.write(0x1A);
                writeU16(out, (int) v);
            } else {
                encodeFloat(v);
            }
        }

        /** Inverse exacte du décodage flottant de {@link BasicDetokenizer} (5 octets, exposant biaisé 129). */
        private void encodeFloat(double value) {
            if (value == 0.0) {
                out.write(0x1F);
                out.write(0);
                out.write(0);
                out.write(0);
                out.write(0);
                out.write(0);
                return;
            }
            boolean negative = value < 0;
            double v = Math.abs(value);
            int exp = (int) Math.floor(Math.log(v) / Math.log(2));
            double frac = v / Math.pow(2, exp) - 1.0;
            long mantissa = Math.round(frac * 2147483648.0); // 2^31
            if (mantissa >= 2147483648L) {
                mantissa = 0;
                exp++;
            }
            int b3 = (int) ((mantissa >> 24) & 0x7F) | (negative ? 0x80 : 0);
            out.write(0x1F);
            out.write((int) (mantissa & 0xFF));
            out.write((int) ((mantissa >> 8) & 0xFF));
            out.write((int) ((mantissa >> 16) & 0xFF));
            out.write(b3);
            out.write((exp + 129) & 0xFF);
        }
    }

    private static boolean isHexDigit(char c) {
        char u = Character.toUpperCase(c);
        return (u >= '0' && u <= '9') || (u >= 'A' && u <= 'F');
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
