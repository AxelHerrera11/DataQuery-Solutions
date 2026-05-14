package com.umg.compilador.dialect.impl;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.schema.*;

import java.sql.*;
import java.util.*;

public class MySQLDialect implements DBDialect {

    @Override public String getName()        { return "MYSQL"; }
    @Override public String getDisplayName() { return "MySQL 8.x"; }
    @Override public int    getDefaultPort() { return 3306; }
    @Override public String getBrandColor()  { return "#4479A1"; }
    @Override public boolean supportsTransactions() { return true; }
    @Override public String getDriverClass() { return "com.mysql.cj.jdbc.Driver"; }

    @Override
    public Set<String> getDialectKeywords() {
        return Set.of(
            "LIMIT", "OFFSET", "AUTO_INCREMENT", "UNSIGNED", "ZEROFILL",
            "TINYINT", "BIGINT", "MEDIUMINT", "SMALLINT", "ENUM", "SET",
            "SHOW", "DESCRIBE", "EXPLAIN", "USE", "DATABASES", "TABLES",
            "ENGINE", "CHARSET", "COLLATE", "PARTITION", "REPLACE",
            "STRAIGHT_JOIN", "SQL_CALC_FOUND_ROWS", "FOUND_ROWS"
        );
    }

    @Override
    public String buildJdbcUrl(ConnectionConfig c) {
        return "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
            .formatted(c.host(), c.port(), c.database());
    }

    @Override
    public DatabaseSchema extractSchema(Connection connection, String databaseName) {
        List<SchemaTable> tables = new ArrayList<>();
        String sql = """
            SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME, ORDINAL_POSITION
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, databaseName);
            ResultSet rs = ps.executeQuery();
            Map<String, List<SchemaColumn>> map = new LinkedHashMap<>();
            while (rs.next()) {
                String tName   = rs.getString("TABLE_NAME");
                String colName = rs.getString("COLUMN_NAME");
                String dt      = rs.getString("DATA_TYPE");
                boolean null_  = "YES".equals(rs.getString("IS_NULLABLE"));
                boolean pk     = "PRI".equals(rs.getString("COLUMN_KEY"));
                map.computeIfAbsent(tName, k -> new ArrayList<>())
                   .add(new SchemaColumn(colName, mapType(dt), null_, pk));
            }
            map.forEach((n, cols) -> tables.add(new SchemaTable(n, cols)));
        } catch (SQLException e) {
            throw new RuntimeException("Error extrayendo schema MySQL: " + e.getMessage(), e);
        }
        return new DatabaseSchema(databaseName, getName(), tables);
    }

    @Override
    public List<String> validateDialectSyntax(String sql) {
        List<String> errors = new ArrayList<>();
        String upper = sql.toUpperCase().trim();
        if (upper.contains("LIMIT") && !upper.matches(".*LIMIT\\s+\\d+.*")) {
            errors.add("MySQL: LIMIT debe ir seguido de un número entero");
        }
        return errors;
    }

    private DataType mapType(String t) {
        return switch (t.toLowerCase()) {
            case "int","bigint","smallint","tinyint","mediumint" -> DataType.INT;
            case "float","double","decimal","numeric"            -> DataType.FLOAT;
            case "varchar","char","text","mediumtext","longtext","tinytext" -> DataType.VARCHAR;
            case "date","datetime","timestamp","time"            -> DataType.DATETIME;
            case "boolean","tinyint(1)"                          -> DataType.BOOLEAN;
            case "json"                                          -> DataType.JSON;
            case "blob","mediumblob","longblob"                  -> DataType.BLOB;
            default                                              -> DataType.UNKNOWN;
        };
    }
}
