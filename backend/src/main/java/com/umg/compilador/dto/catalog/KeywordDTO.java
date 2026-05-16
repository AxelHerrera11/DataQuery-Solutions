package com.umg.compilador.dto.catalog;

public record KeywordDTO(
    String  word,
    String  tokenType,
    String  category,
    boolean isReserved,
    String  sinceVersion,
    boolean deprecated,
    String  notes
) {}
