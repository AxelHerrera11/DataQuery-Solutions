package com.umg.compilador.dialect.impl;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.schema.*;

import java.sql.*;
import java.util.*;

public class SQLServerDialect implements DBDialect {

    @Override public String getName()        { return "SQLSERVER"; }
    @Override public String getDisplayName() { return "SQL Server 2019+"; }
    @Override public int    getDefaultPort() { return 1433; }
    @Override public String getBrandColor()  { return "#CC2927"; }
    @Override public boolean supportsTransactions() { return true; }
    @Override public String getDriverClass() { return "com.microsoft.sqlserver.jdbc.SQLServerDriver"; }

    @Override
    public Set<String> getDialectKeywords() {
        return Set.of(
            "TOP", "NOLOCK", "NVARCHAR", "NCHAR", "NTEXT",
            "GETDATE", "GETUTCDATE", "IDENTITY", "NEWID",
            "COALESCE", "ISNULL", "CONVERT", "CAST", "TRY_CAST",
            "ROLLUP", "CUBE", "GROUPING", "SETS", "PIVOT", "UNPIVOT",
            "APPLY", "CROSS", "OUTPUT", "MERGE", "MATCHED",
            "ROWCOUNT", "XACT_ABORT", "NOCOUNT", "WITH"
        );
    }

    @Override
    public String buildJdbcUrl(ConnectionConfig c) {
        return "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true"
            .formatted(c.host(), c.port(), c.database());
    }

    @Override
    public DatabaseSchema extractSchema(Connection connection, String databaseName) {
        List<SchemaTable> tables = new ArrayList<>();
        String sql = """
            SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE,
                   c.IS_NULLABLE,
                   CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'PRI' ELSE '' END AS COLUMN_KEY
            FROM INFORMATION_SCHEMA.COLUMNS c
            LEFT JOIN (
                SELECT ku.TABLE_NAME, ku.COLUMN_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku
                  ON tc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'
            ) pk ON c.TABLE_NAME = pk.TABLE_NAME AND c.COLUMN_NAME = pk.COLUMN_NAME
            ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
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
            throw new RuntimeException("Error extrayendo schema SQL Server: " + e.getMessage(), e);
        }
        return new DatabaseSchema(databaseName, getName(), tables);
    }

    @Override
    public List<String> validateDialectSyntax(String sql) {
        List<String> errors = new ArrayList<>();
        String upper = sql.toUpperCase().trim();
        if (upper.contains("LIMIT") && !upper.contains("FETCH")) {
            errors.add("SQL Server: usa TOP o FETCH NEXT en lugar de LIMIT");
        }
        return errors;
    }

    private DataType mapType(String t) {
        return switch (t.toLowerCase()) {
            case "int","bigint","smallint","tinyint","numeric","decimal" -> DataType.INT;
            case "float","real","money","smallmoney"                     -> DataType.FLOAT;
            case "varchar","nvarchar","char","nchar","text","ntext"      -> DataType.VARCHAR;
            case "datetime","datetime2","smalldatetime","date","time"    -> DataType.DATETIME;
            case "bit"                                                   -> DataType.BOOLEAN;
            case "varbinary","binary","image"                            -> DataType.BLOB;
            default                                                      -> DataType.UNKNOWN;
        };
    }
}
