package com.umg.compilador.schema;

import java.util.*;

/**
 * DatabaseSchema — schema completo de una BD real.
 * Sustituye a SymbolTable.java hardcoded.
 * Puede estar vacío cuando no hay conexión activa.
 */
public record DatabaseSchema(String databaseName, String dialectName, List<SchemaTable> tables) {

    public DatabaseSchema { tables = List.copyOf(tables); }

    /** Schema vacío (sin conexión) — el SemanticAnalyzer emitirá solo advertencias. */
    public static DatabaseSchema empty() {
        return new DatabaseSchema("", "", List.of());
    }

    public boolean isEmpty() { return tables.isEmpty(); }

    public Optional<SchemaTable> findTable(String tableName) {
        return tables.stream()
            .filter(t -> t.name().equalsIgnoreCase(tableName))
            .findFirst();
    }

    public void print() {
        System.out.println("Schema: " + databaseName + " [" + dialectName + "]");
        tables.forEach(t -> {
            System.out.println("  Tabla: " + t.name());
            t.columns().forEach(c -> System.out.println(c));
        });
    }
}
