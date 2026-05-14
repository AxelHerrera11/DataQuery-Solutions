package com.umg.compilador.dialect.impl;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.schema.*;

import java.sql.*;
import java.util.*;

public class PostgreSQLDialect implements DBDialect {

    @Override public String getName()        { return "POSTGRESQL"; }
    @Override public String getDisplayName() { return "PostgreSQL 15+"; }
    @Override public int    getDefaultPort() { return 5432; }
    @Override public String getBrandColor()  { return "#336791"; }
    @Override public boolean supportsTransactions() { return true; }
    @Override public String getDriverClass() { return "org.postgresql.Driver"; }

    @Override
    public Set<String> getDialectKeywords() {
        return Set.of(
            "RETURNING", "ILIKE", "SERIAL", "BIGSERIAL", "SMALLSERIAL",
            "JSONB", "LATERAL", "EXCEPT", "INTERSECT", "FETCH", "NEXT",
            "ROWS", "ONLY", "NULLS", "FIRST", "LAST", "FILTER",
            "WITHIN", "OVER", "PARTITION", "WINDOW", "TABLESAMPLE",
            "ARRAY", "ANY", "ALL", "SOME", "COPY", "VACUUM", "ANALYZE",
            "UNLOGGED", "CONCURRENTLY", "MATERIALIZED", "REFRESH"
        );
    }

    @Override
    public String buildJdbcUrl(ConnectionConfig c) {
        return "jdbc:postgresql://%s:%d/%s".formatted(c.host(), c.port(), c.database());
    }

    @Override
    public DatabaseSchema extractSchema(Connection connection, String databaseName) {
        List<SchemaTable> tables = new ArrayList<>();
        String sql = """
            SELECT c.table_name, c.column_name, c.data_type,
                   c.is_nullable,
                   CASE WHEN pk.column_name IS NOT NULL THEN 'PRI' ELSE '' END AS column_key
            FROM information_schema.columns c
            LEFT JOIN (
                SELECT ku.table_name, ku.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage ku
                  ON tc.constraint_name = ku.constraint_name
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_schema = ?
            ) pk ON c.table_name = pk.table_name AND c.column_name = pk.column_name
            WHERE c.table_schema = ?
            ORDER BY c.table_name, c.ordinal_position
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "public");
            ps.setString(2, "public");
            ResultSet rs = ps.executeQuery();
            Map<String, List<SchemaColumn>> map = new LinkedHashMap<>();
            while (rs.next()) {
                String tName   = rs.getString("table_name");
                String colName = rs.getString("column_name");
                String dt      = rs.getString("data_type");
                boolean null_  = "YES".equals(rs.getString("is_nullable"));
                boolean pk     = "PRI".equals(rs.getString("column_key"));
                map.computeIfAbsent(tName, k -> new ArrayList<>())
                   .add(new SchemaColumn(colName, mapType(dt), null_, pk));
            }
            map.forEach((n, cols) -> tables.add(new SchemaTable(n, cols)));
        } catch (SQLException e) {
            throw new RuntimeException("Error extrayendo schema PostgreSQL: " + e.getMessage(), e);
        }
        return new DatabaseSchema(databaseName, getName(), tables);
    }

    @Override
    public List<String> validateDialectSyntax(String sql) {
        List<String> errors = new ArrayList<>();
        String upper = sql.toUpperCase().trim();
        if (upper.contains("LIMIT") && upper.contains("TOP")) {
            errors.add("PostgreSQL: usa LIMIT en lugar de TOP");
        }
        return errors;
    }

    private DataType mapType(String t) {
        return switch (t.toLowerCase()) {
            case "integer","bigint","smallint","numeric","int4","int8" -> DataType.INT;
            case "real","double precision","float4","float8","decimal" -> DataType.FLOAT;
            case "character varying","varchar","char","text","bpchar"  -> DataType.VARCHAR;
            case "timestamp","timestamp without time zone","date","time" -> DataType.DATETIME;
            case "boolean"                                              -> DataType.BOOLEAN;
            case "json","jsonb"                                         -> DataType.JSON;
            case "bytea"                                                -> DataType.BLOB;
            default                                                     -> DataType.UNKNOWN;
        };
    }
}
