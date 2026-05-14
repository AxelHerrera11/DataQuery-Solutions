package com.umg.compilador.dialect.impl;

import com.mongodb.client.*;
import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.schema.*;
import org.bson.Document;

import java.sql.Connection;
import java.util.*;

/**
 * MongoDialect — adaptador para MongoDB.
 * MongoDB no usa JDBC, por eso extractSchema recibe una Connection
 * que internamente no se usa; en su lugar se obtiene la conexión
 * nativa desde MongoClients cuando se llama desde SchemaService.
 *
 * El compilador valida "SQL-like" sobre colecciones Mongo:
 *   SELECT <campos> FROM <coleccion> WHERE <condicion>
 */
public class MongoDialect implements DBDialect {

    @Override public String getName()        { return "MONGODB"; }
    @Override public String getDisplayName() { return "MongoDB 6+"; }
    @Override public int    getDefaultPort() { return 27017; }
    @Override public String getBrandColor()  { return "#47A248"; }
    @Override public boolean supportsTransactions() { return false; }

    // MongoDB no usa JDBC driver convencional
    @Override public String getDriverClass() { return ""; }

    @Override
    public Set<String> getDialectKeywords() {
        return Set.of(
            "AGGREGATE", "LOOKUP", "UNWIND", "PROJECT", "MATCH",
            "GROUP", "SORT", "LIMIT", "SKIP", "OUT", "MERGE",
            "FACET", "BUCKET", "SAMPLE", "COLLSTATS", "INDEXSTATS",
            "OBJECTID", "ISODate", "NUMBERLONG", "NUMBERDECIMAL"
        );
    }

    @Override
    public String buildJdbcUrl(ConnectionConfig c) {
        // URL de conexión nativa de MongoDB (no JDBC)
        if (c.username() != null && !c.username().isBlank()) {
            return "mongodb://%s:%s@%s:%d/%s".formatted(
                c.username(), c.password(), c.host(), c.port(), c.database());
        }
        return "mongodb://%s:%d/%s".formatted(c.host(), c.port(), c.database());
    }

    /**
     * Extrae schema de MongoDB inspeccionando un documento de muestra
     * de cada colección para inferir tipos de campos.
     * La Connection JDBC no se usa — se abre una MongoClient nativa.
     */
    @Override
    public DatabaseSchema extractSchema(Connection ignored, String databaseName) {
        // En MongoDialect la conexión real se pasa desde SchemaService
        // Este método está implementado para compatibilidad de interfaz
        return DatabaseSchema.empty();
    }

    /**
     * Versión que recibe el MongoClient nativo directamente.
     */
    public DatabaseSchema extractSchemaFromClient(MongoClient client, String databaseName) {
        List<SchemaTable> tables = new ArrayList<>();
        MongoDatabase db = client.getDatabase(databaseName);

        for (String collectionName : db.listCollectionNames()) {
            MongoCollection<Document> collection = db.getCollection(collectionName);
            List<SchemaColumn> columns = new ArrayList<>();

            // Inspeccionar el primer documento para inferir campos
            Document sample = collection.find().limit(1).first();
            if (sample != null) {
                sample.forEach((key, value) -> {
                    DataType type = inferMongoType(value);
                    columns.add(new SchemaColumn(key, type, true, key.equals("_id")));
                });
            }
            tables.add(new SchemaTable(collectionName, columns));
        }
        return new DatabaseSchema(databaseName, getName(), tables);
    }

    @Override
    public List<String> validateDialectSyntax(String sql) {
        List<String> errors = new ArrayList<>();
        String upper = sql.toUpperCase().trim();
        if (!upper.startsWith("SELECT")) {
            errors.add("MongoDB (modo SQL): solo se soportan consultas SELECT");
        }
        if (upper.contains("JOIN")) {
            errors.add("MongoDB: JOIN no es compatible. Use $lookup en agregación nativa");
        }
        return errors;
    }

    private DataType inferMongoType(Object value) {
        if (value == null)              return DataType.UNKNOWN;
        if (value instanceof Integer
         || value instanceof Long)      return DataType.INT;
        if (value instanceof Double
         || value instanceof Float)     return DataType.FLOAT;
        if (value instanceof Boolean)   return DataType.BOOLEAN;
        if (value instanceof Document)  return DataType.JSON;
        return DataType.VARCHAR;
    }
}
