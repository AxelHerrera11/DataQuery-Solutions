package com.umg.compilador.dto;

/**
 * CompileRequest — cuerpo del POST /api/compile
 *
 * @param sql          Query SQL a compilar
 * @param dialect      Motor: MYSQL | POSTGRESQL | SQLSERVER | MONGODB
 * @param connectionId ID de conexión guardada (null = solo sintaxis)
 */
public record CompileRequest(String sql, String dialect, String connectionId) {}
