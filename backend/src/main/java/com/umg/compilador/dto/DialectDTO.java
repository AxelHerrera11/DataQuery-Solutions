package com.umg.compilador.dto;

/** Info de un dialecto para poblar el selector en la UI. */
public record DialectDTO(
    String name,
    String displayName,
    int    defaultPort,
    String brandColor,
    boolean supportsTransactions
) {}
