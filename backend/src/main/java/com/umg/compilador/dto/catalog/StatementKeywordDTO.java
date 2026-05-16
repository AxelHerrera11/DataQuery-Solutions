package com.umg.compilador.dto.catalog;

public record StatementKeywordDTO(
    String word,
    String role,
    String positionHint,
    String notes
) {}
