package com.umg.compilador.dto.catalog;

public record DataTypeMappingDTO(
    String  nativeType,
    String  abstractType,
    Integer maxLength,
    boolean requiresLength,
    String  notes
) {}
