package com.umg.compilador.connection;

/**
 * ConnectionConfig — datos de una conexión guardada.
 * @param id        UUID generado en frontend o backend
 * @param name      nombre amigable ("Mi MySQL local")
 * @param dialect   "MYSQL" | "POSTGRESQL" | "SQLSERVER" | "MONGODB"
 * @param host      hostname o IP
 * @param port      puerto (0 = usar el default del dialecto)
 * @param database  nombre de la base de datos
 * @param username  usuario
 * @param password  contraseña (no se persiste en disco)
 */
public record ConnectionConfig(
    String id,
    String name,
    String dialect,
    String host,
    int    port,
    String database,
    String username,
    String password
) {}
