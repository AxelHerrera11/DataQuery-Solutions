package com.umg.compilador.schema;

import java.util.List;
import java.util.Optional;

/**
 * SchemaTable — tabla extraída de una BD real (sustituye a Table.java hardcoded).
 */
public record SchemaTable(String name, List<SchemaColumn> columns) {
    public SchemaTable { columns = List.copyOf(columns); }

    public Optional<SchemaColumn> findColumn(String columnName) {
        return columns.stream()
            .filter(c -> c.name().equalsIgnoreCase(columnName))
            .findFirst();
    }
}
