package com.umg.compilador.dto.catalog;

public record SyntaxDTO(
    String  statementName,
    boolean supported,
    String  syntaxTemplate,
    String  notes
) {}
