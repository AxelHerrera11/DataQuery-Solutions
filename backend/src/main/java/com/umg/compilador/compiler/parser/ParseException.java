package com.umg.compilador.compiler.parser;

public class ParseException extends RuntimeException {
    private final int line;
    private final int column;

    public ParseException(String message, int line, int column) {
        super("Error sintáctico en L%d:C%d — %s".formatted(line, column, message));
        this.line   = line;
        this.column = column;
    }

    public int getLine()   { return line; }
    public int getColumn() { return column; }
}
