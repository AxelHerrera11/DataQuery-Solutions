package com.umg.compilador.schema;

/**
 * SchemaColumn — columna de una tabla real (sustituye a Column.java hardcoded).
 */
public record SchemaColumn(String name, DataType type, boolean nullable, boolean primaryKey) {
    @Override public String toString() {
        return "    ├─ %s (%s)%s%s".formatted(
            name, type,
            primaryKey ? " PK" : "",
            nullable   ? ""    : " NOT NULL"
        );
    }
}
