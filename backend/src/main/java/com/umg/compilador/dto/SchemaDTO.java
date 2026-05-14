package com.umg.compilador.dto;

import java.util.List;

/** Schema serializado para el explorador de la UI. */
public record SchemaDTO(
    String databaseName,
    String dialectName,
    List<TableDTO> tables
) {
    public record TableDTO(String name, List<ColumnDTO> columns) {}
    public record ColumnDTO(String name, String type, boolean nullable, boolean primaryKey) {}
}
