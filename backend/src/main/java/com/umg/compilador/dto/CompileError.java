package com.umg.compilador.dto;

/**
 * CompileError — error individual con posición para Monaco Editor.
 *
 * @param phase    LEXER | PARSER | SEMANTIC | DIALECT
 * @param line     Línea (1-indexed, para Monaco)
 * @param column   Columna (1-indexed)
 * @param message  Descripción del error
 * @param severity ERROR | WARNING
 */
public record CompileError(String phase, int line, int column, String message, String severity) {}
