package com.umg.compilador.service;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.connection.ConnectionManager;
import com.umg.compilador.connection.ConnectionResult;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.dialect.DialectRegistry;
import com.umg.compilador.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConnectionService {

    private final ConnectionManager connectionManager;
    private final DialectRegistry   dialectRegistry;
    private final SchemaService     schemaService;

    public ConnectionService(ConnectionManager connectionManager,
                             DialectRegistry dialectRegistry,
                             SchemaService schemaService) {
        this.connectionManager = connectionManager;
        this.dialectRegistry   = dialectRegistry;
        this.schemaService     = schemaService;
    }

    public ConnectionDTO save(ConnectionRequest req) {
        String id = UUID.randomUUID().toString();
        int port  = req.port() > 0 ? req.port()
                  : dialectRegistry.findByName(req.dialect())
                      .map(DBDialect::getDefaultPort).orElse(0);

        ConnectionConfig config = new ConnectionConfig(
            id, req.name(), req.dialect().toUpperCase(),
            req.host(), port, req.database(),
            req.username(), req.password()
        );
        connectionManager.saveConfig(config);
        return toDTO(config);
    }

    public List<ConnectionDTO> getAll() {
        return connectionManager.getAllConfigs().stream().map(this::toDTO).toList();
    }

    public void delete(String id) {
        schemaService.evictCache(id);
        connectionManager.removeConfig(id);
    }

    public ConnectionResult test(ConnectionRequest req) {
        String id = UUID.randomUUID().toString();
        int port  = req.port() > 0 ? req.port()
                  : dialectRegistry.findByName(req.dialect())
                      .map(DBDialect::getDefaultPort).orElse(0);
        ConnectionConfig config = new ConnectionConfig(
            id, req.name(), req.dialect().toUpperCase(),
            req.host(), port, req.database(),
            req.username(), req.password()
        );
        return connectionManager.testConnection(config);
    }

    public SchemaDTO getSchema(String connectionId) {
        var schema = schemaService.getSchema(connectionId);
        List<SchemaDTO.TableDTO> tables = schema.tables().stream().map(t ->
            new SchemaDTO.TableDTO(
                t.name(),
                t.columns().stream().map(c ->
                    new SchemaDTO.ColumnDTO(c.name(), c.type().name(), c.nullable(), c.primaryKey())
                ).toList()
            )
        ).toList();
        return new SchemaDTO(schema.databaseName(), schema.dialectName(), tables);
    }

    private ConnectionDTO toDTO(ConnectionConfig c) {
        DBDialect d = dialectRegistry.findByName(c.dialect()).orElse(null);
        return new ConnectionDTO(
            c.id(), c.name(), c.dialect(), c.host(), c.port(), c.database(), c.username(),
            d != null ? d.getBrandColor()  : "#888",
            d != null ? d.getDisplayName() : c.dialect()
        );
    }
}
