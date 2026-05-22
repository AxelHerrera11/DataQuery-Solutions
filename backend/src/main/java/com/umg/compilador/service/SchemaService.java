package com.umg.compilador.service;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.connection.ConnectionManager;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.dialect.DialectRegistry;
import com.umg.compilador.dialect.impl.MongoDialect;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.umg.compilador.schema.DatabaseSchema;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchemaService {

    private final ConnectionManager connectionManager;
    private final DialectRegistry   dialectRegistry;
    private final Map<String, DatabaseSchema> schemaCache = new ConcurrentHashMap<>();

    public SchemaService(ConnectionManager connectionManager, DialectRegistry dialectRegistry) {
        this.connectionManager = connectionManager;
        this.dialectRegistry   = dialectRegistry;
    }

    public DatabaseSchema getSchema(String connectionId) {
        return schemaCache.computeIfAbsent(connectionId, this::fetchSchema);
    }

    public DatabaseSchema refreshSchema(String connectionId) {
        schemaCache.remove(connectionId);
        return getSchema(connectionId);
    }

    private DatabaseSchema fetchSchema(String connectionId) {
        ConnectionConfig config = connectionManager.getConfig(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Conexión no encontrada: " + connectionId));

        DBDialect dialect = dialectRegistry.findByName(config.dialect())
            .orElseThrow(() -> new IllegalArgumentException("Dialecto desconocido: " + config.dialect()));

        // Native drivers (MongoDB) — no usan JDBC
        if (DialectRegistry.isNativeDriver(config.dialect())) {
            String uri = dialect.buildJdbcUrl(config);
            try (MongoClient client = MongoClients.create(uri)) {
                if (dialect instanceof MongoDialect mongoDial) {
                    return mongoDial.extractSchemaFromClient(client, config.database());
                }
                // Fallback: extract using interface
                return dialect.extractSchema(null, config.database());
            }
        }

        // JDBC-based dialects
        Connection conn = connectionManager.getConnection(connectionId);
        return dialect.extractSchema(conn, config.database());
    }

    public void evictCache(String connectionId) {
        schemaCache.remove(connectionId);
    }
}
