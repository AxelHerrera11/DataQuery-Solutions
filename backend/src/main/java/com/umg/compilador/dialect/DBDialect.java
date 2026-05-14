package com.umg.compilador.dialect;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.schema.DatabaseSchema;

import java.sql.Connection;
import java.util.List;
import java.util.Set;

/**
 * DBDialect — contrato para cada motor de base de datos.
 *
 * Cada implementación maneja:
 *   1. Identidad (nombre, puerto, color de UI)
 *   2. Keywords propias del dialecto (para el Lexer)
 *   3. Construcción del JDBC URL
 *   4. Extracción del schema real (INFORMATION_SCHEMA / driver nativo)
 *   5. Validaciones de sintaxis específicas del dialecto
 */
public interface DBDialect {

    // ── Identidad ────────────────────────────────────────────────────

    /** Nombre único en mayúsculas. Ej: "MYSQL" */
    String getName();

    /** Nombre para mostrar en UI. Ej: "MySQL 8.x" */
    String getDisplayName();

    /** Puerto por defecto del motor. */
    int getDefaultPort();

    /** Color hexadecimal para identificar el motor en la UI. */
    String getBrandColor();

    /** Indica si el motor soporta transacciones ACID. */
    boolean supportsTransactions();

    // ── Keywords del dialecto ────────────────────────────────────────

    /**
     * Palabras clave específicas de este motor.
     * Se inyectan al Lexer para tokenización correcta.
     * Ej MySQL: LIMIT, AUTO_INCREMENT, UNSIGNED, TINYINT...
     */
    Set<String> getDialectKeywords();

    // ── Conexión JDBC ────────────────────────────────────────────────

    /** Clase del driver JDBC. Ej: "com.mysql.cj.jdbc.Driver" */
    String getDriverClass();

    /**
     * Construye el JDBC URL para este motor.
     * Ej: "jdbc:mysql://localhost:3306/mydb?useSSL=false"
     */
    String buildJdbcUrl(ConnectionConfig config);

    // ── Schema ───────────────────────────────────────────────────────

    /**
     * Extrae el schema completo desde la BD usando la conexión activa.
     * Para SQL estándar: consulta INFORMATION_SCHEMA.COLUMNS
     * Para MongoDB: usa $listCollections y sampleDocument
     */
    DatabaseSchema extractSchema(Connection connection, String databaseName);

    // ── Validación de sintaxis específica ───────────────────────────

    /**
     * Valida características de sintaxis propias del dialecto
     * que el parser genérico no puede detectar.
     * @return lista de mensajes de error (vacía = sin errores)
     */
    List<String> validateDialectSyntax(String sql);
}
