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

/**
 * SchemaService — carga y cachea el schema de cada conexión.
 */
@Service
public class SchemaService {

    private final ConnectionManager connectionManager;
    private final DialectRegistry   dialectRegistry;
    private final Map<String, DatabaseSchema> schemaCache = new ConcurrentHashMap<>();

    public SchemaService(ConnectionManager connectionManager, DialectRegistry dialectRegistry) {
        this.connectionManager = connectionManager;
        this.dialectRegistry   = dialectRegistry;
    }

    /**
     * Obtiene el schema (desde caché o extrayéndolo en tiempo real).
     */
    public DatabaseSchema getSchema(String connectionId) {
        return schemaCache.computeIfAbsent(connectionId, this::fetchSchema);
    }

    /** Fuerza re-extracción del schema (útil tras cambios en la BD). */
    public DatabaseSchema refreshSchema(String connectionId) {
        schemaCache.remove(connectionId);
        return getSchema(connectionId);
    }

    private DatabaseSchema fetchSchema(String connectionId) {
        ConnectionConfig config = connectionManager.getConfig(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Conexión no encontrada: " + connectionId));

        DBDialect dialect = dialectRegistry.findByName(config.dialect())
            .orElseThrow(() -> new IllegalArgumentException("Dialecto desconocido: " + config.dialect()));

        if (dialect instanceof MongoDialect mongoDial) {
            String mongoUri = dialect.buildJdbcUrl(config);
            try (MongoClient client = MongoClients.create(mongoUri)) {
                return mongoDial.extractSchemaFromClient(client, config.database());
            }
        }

        Connection conn = connectionManager.getConnection(connectionId);
        return dialect.extractSchema(conn, config.database());
    }

    public void evictCache(String connectionId) {
        schemaCache.remove(connectionId);
    }
}
