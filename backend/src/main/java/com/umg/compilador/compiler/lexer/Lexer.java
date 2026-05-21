package com.umg.compilador.compiler.lexer;

import com.umg.compilador.compiler.token.Token;
import com.umg.compilador.compiler.token.TokenType;

import java.util.*;

/**
 * Lexer — analizador léxico SQL.
 * Migrado y ampliado desde el proyecto base:
 *   • Soporta keywords extra inyectadas por el dialecto
 *   • Soporta paréntesis, punto, AND, OR, NOT, IN, LIKE, etc.
 *   • Mantiene compatibilidad total con los tests existentes
 */
public class Lexer {

    private static final Map<String, TokenType> BASE_KEYWORDS = new HashMap<>(Map.ofEntries(
        Map.entry("SELECT",       TokenType.SELECT),
        Map.entry("FROM",         TokenType.FROM),
        Map.entry("WHERE",        TokenType.WHERE),
        Map.entry("INSERT",       TokenType.INSERT),
        Map.entry("INTO",         TokenType.INTO),
        Map.entry("VALUES",       TokenType.VALUES),
        Map.entry("UPDATE",       TokenType.UPDATE),
        Map.entry("SET",          TokenType.SET),
        Map.entry("DELETE",       TokenType.DELETE),
        Map.entry("CREATE",       TokenType.CREATE),
        Map.entry("TABLE",        TokenType.TABLE),
        Map.entry("DROP",         TokenType.DROP),
        Map.entry("ALTER",        TokenType.ALTER),
        Map.entry("ADD",          TokenType.ADD),
        Map.entry("COLUMN",       TokenType.COLUMN),
        Map.entry("AND",          TokenType.AND),
        Map.entry("OR",           TokenType.OR),
        Map.entry("NOT",          TokenType.NOT),
        Map.entry("IN",           TokenType.IN),
        Map.entry("LIKE",         TokenType.LIKE),
        Map.entry("BETWEEN",      TokenType.BETWEEN),
        Map.entry("IS",           TokenType.IS),
        Map.entry("NULL",         TokenType.NULL),
        Map.entry("ORDER",        TokenType.ORDER),
        Map.entry("BY",           TokenType.BY),
        Map.entry("GROUP",        TokenType.GROUP),
        Map.entry("HAVING",       TokenType.HAVING),
        Map.entry("LIMIT",        TokenType.LIMIT),
        Map.entry("OFFSET",       TokenType.OFFSET),
        Map.entry("INNER",        TokenType.INNER),
        Map.entry("LEFT",         TokenType.LEFT),
        Map.entry("RIGHT",        TokenType.RIGHT),
        Map.entry("OUTER",        TokenType.OUTER),
        Map.entry("JOIN",         TokenType.JOIN),
        Map.entry("ON",           TokenType.ON),
        Map.entry("AS",           TokenType.AS),
        Map.entry("DISTINCT",     TokenType.DISTINCT),
        Map.entry("COUNT",        TokenType.COUNT),
        Map.entry("SUM",          TokenType.SUM),
        Map.entry("AVG",          TokenType.AVG),
        Map.entry("MIN",          TokenType.MIN),
        Map.entry("MAX",          TokenType.MAX),
        Map.entry("ASC",          TokenType.ASC),
        Map.entry("DESC",         TokenType.DESC),
        Map.entry("BEGIN",        TokenType.BEGIN),
        Map.entry("COMMIT",       TokenType.COMMIT),
        Map.entry("ROLLBACK",     TokenType.ROLLBACK),
        Map.entry("GRANT",        TokenType.GRANT),
        Map.entry("REVOKE",       TokenType.REVOKE),
        Map.entry("TO",           TokenType.TO),
        Map.entry("INDEX",        TokenType.INDEX),
        Map.entry("UNIQUE",       TokenType.UNIQUE),
        Map.entry("PRIMARY",      TokenType.PRIMARY),
        Map.entry("KEY",          TokenType.KEY),
        Map.entry("DEFAULT",      TokenType.DEFAULT),
        Map.entry("REFERENCES",   TokenType.REFERENCES),
        Map.entry("CHECK",        TokenType.CHECK),
        Map.entry("FOREIGN",      TokenType.FOREIGN),
        Map.entry("CONSTRAINT",   TokenType.CONSTRAINT),
        Map.entry("IF",           TokenType.IF),
        Map.entry("EXISTS",       TokenType.EXISTS),
        Map.entry("ALL",          TokenType.ALL),
        Map.entry("WORK",         TokenType.WORK),
        Map.entry("TRANSACTION",  TokenType.TRANSACTION)
    ));

    private final String   source;
    private final Map<String, TokenType> keywords;
    private       int      position;
    private       int      line;
    private       int      column;
    private final List<String> lexErrors = new ArrayList<>();

    /** Constructor base (sin keywords de dialecto). */
    public Lexer(String source) {
        this(source, Set.of());
    }

    /**
     * Constructor con keywords extra del dialecto.
     * Las keywords del dialecto se tratan como IDENTIFIER con tipo extendido
     * (para validación de dialecto posterior) — no rompen el parser base.
     */
    public Lexer(String source, Set<String> dialectKeywords) {
        if (source == null) throw new IllegalArgumentException("source no puede ser null");
        this.source   = source;
        this.position = 0;
        this.line     = 1;
        this.column   = 1;
        this.keywords = new HashMap<>(BASE_KEYWORDS);
        dialectKeywords.forEach(k -> this.keywords.putIfAbsent(k.toUpperCase(), TokenType.IDENTIFIER));
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = nextToken();
            tokens.add(token);
        } while (token.type() != TokenType.EOF);

        if (!lexErrors.isEmpty()) {
            throw new LexerException(
                "Se encontraron %d error(es) léxico(s):\n  • %s"
                    .formatted(lexErrors.size(), String.join("\n  • ", lexErrors)),
                line, column);
        }
        return tokens;
    }

    public List<String> getLexErrors() { return List.copyOf(lexErrors); }

    private char current() { return position < source.length() ? source.charAt(position) : '\0'; }
    private char peek()    { int n = position+1; return n < source.length() ? source.charAt(n) : '\0'; }

    private void advance() {
        if (current() == '\n') { line++; column = 1; } else { column++; }
        position++;
    }

    private void skipWhitespaceAndComments() {
        boolean skipping = true;
        while (skipping) {
            while (current() != '\0' && Character.isWhitespace(current())) advance();
            if (current() == '-' && peek() == '-') {
                while (current() != '\0' && current() != '\n') advance();
            } else if (current() == '/' && peek() == '*') {
                advance(); advance();
                while (current() != '\0' && !(current() == '*' && peek() == '/')) advance();
                if (current() != '\0') { advance(); advance(); }
            } else {
                skipping = false;
            }
        }
    }

    private Token readIdentifierOrKeyword() {
        int sl = line, sc = column;
        StringBuilder sb = new StringBuilder();
        while (current() != '\0' && (Character.isLetterOrDigit(current()) || current() == '_')) {
            sb.append(current()); advance();
        }
        String raw   = sb.toString();
        String upper = raw.toUpperCase();
        TokenType kw = keywords.get(upper);
        return kw != null ? new Token(kw, raw, sl, sc) : new Token(TokenType.IDENTIFIER, raw, sl, sc);
    }

    private Token readNumber() {
        int sl = line, sc = column;
        StringBuilder sb = new StringBuilder();
        boolean hasDecimal = false;
        while (current() != '\0' && (Character.isDigit(current()) || current() == '.')) {
            if (current() == '.') { if (hasDecimal) break; hasDecimal = true; }
            sb.append(current()); advance();
        }
        return new Token(TokenType.NUMBER, sb.toString(), sl, sc);
    }

    private Token readString() {
        int sl = line, sc = column;
        StringBuilder sb = new StringBuilder();
        advance();
        while (current() != '\0') {
            if (current() == '\'') {
                if (peek() == '\'') { sb.append('\''); advance(); advance(); }
                else                { advance(); return new Token(TokenType.STRING, sb.toString(), sl, sc); }
            } else { sb.append(current()); advance(); }
        }
        lexErrors.add("Cadena de texto sin cerrar iniciada en L%d:C%d".formatted(sl, sc));
        return Token.invalid(sb.toString(), sl, sc);
    }

    public Token nextToken() {
        skipWhitespaceAndComments();
        if (current() == '\0') return Token.eof(line, column);

        int sl = line, sc = column;

        if (Character.isLetter(current()) || current() == '_') return readIdentifierOrKeyword();
        if (Character.isDigit(current()))                       return readNumber();
        if (current() == '\'')                                  return readString();

        if (current() == '>') { advance(); if (current()=='=') { advance(); return new Token(TokenType.GREATER_EQUAL, ">=", sl, sc); } return new Token(TokenType.GREATER, ">", sl, sc); }
        if (current() == '<') { advance(); if (current()=='=') { advance(); return new Token(TokenType.LESS_EQUAL,    "<=", sl, sc); } return new Token(TokenType.LESS,    "<", sl, sc); }
        if (current() == '!') {
            advance();
            if (current() == '=') { advance(); return new Token(TokenType.NOT_EQUAL, "!=", sl, sc); }
            lexErrors.add("Carácter '!' no seguido de '=' en L%d:C%d".formatted(sl, sc));
            return Token.invalid("!", sl, sc);
        }

        char ch = current(); advance();
        return switch (ch) {
            case '=' -> new Token(TokenType.EQUAL,       "=",  sl, sc);
            case '*' -> new Token(TokenType.ASTERISK,    "*",  sl, sc);
            case ',' -> new Token(TokenType.COMMA,       ",",  sl, sc);
            case ';' -> new Token(TokenType.SEMICOLON,   ";",  sl, sc);
            case '(' -> new Token(TokenType.LEFT_PAREN,  "(",  sl, sc);
            case ')' -> new Token(TokenType.RIGHT_PAREN, ")",  sl, sc);
            case '.' -> new Token(TokenType.DOT,         ".",  sl, sc);
            default  -> {
                lexErrors.add("Carácter inesperado '%c' en L%d:C%d".formatted(ch, sl, sc));
                yield Token.invalid(String.valueOf(ch), sl, sc);
            }
        };
    }
}
