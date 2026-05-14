package com.umg.compilador.dto;

/** Conexión serializada para enviar al frontend (sin password). */
public record ConnectionDTO(
    String id,
    String name,
    String dialect,
    String host,
    int    port,
    String database,
    String username,
    String brandColor,
    String displayName
) {}
