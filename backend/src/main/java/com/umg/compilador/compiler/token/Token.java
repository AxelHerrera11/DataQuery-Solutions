package com.umg.compilador.compiler.token;

/**
 * Token — unidad léxica inmutable.
 * Migrado sin cambios desde el proyecto base (Java record).
 */
public record Token(TokenType type, String value, int line, int column) {

    public static Token eof(int line, int column) {
        return new Token(TokenType.EOF, "", line, column);
    }

    public static Token invalid(String value, int line, int column) {
        return new Token(TokenType.INVALID, value, line, column);
    }

    @Override
    public String toString() {
        String pos = "(L%d:C%d)".formatted(line, column);
        return value.isEmpty()
            ? "[%s] %s".formatted(type.display(), pos)
            : "[%s] '%s' %s".formatted(type.display(), value, pos);
    }
}
