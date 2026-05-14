package com.umg.compilador.compiler.lexer;

public class LexerException extends RuntimeException {
    private final int line;
    private final int column;

    public LexerException(String message, int line, int column) {
        super("Error léxico en L%d:C%d — %s".formatted(line, column, message));
        this.line   = line;
        this.column = column;
    }

    public int getLine()   { return line; }
    public int getColumn() { return column; }
}
