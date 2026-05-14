package com.umg.compilador.dto;

/** Cuerpo del POST /api/connections */
public record ConnectionRequest(
    String name,
    String dialect,
    String host,
    int    port,
    String database,
    String username,
    String password
) {}
