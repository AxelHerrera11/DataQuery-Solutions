package com.umg.compilador.connection;

import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.dialect.DialectRegistry;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConnectionManager — crea y cachea conexiones JDBC.
 * Las conexiones se guardan por connectionId para reutilizarse
 * en múltiples compilaciones sin reconectar cada vez.
 */
@Component
public class ConnectionManager {

    private final DialectRegistry dialectRegistry;
    private final Map<String, ConnectionConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, Connection>       pool    = new ConcurrentHashMap<>();

    public ConnectionManager(DialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    public void saveConfig(ConnectionConfig config) {
        configs.put(config.id(), config);
        // Si ya había una conexión activa para este ID, la cerramos
        closeConnection(config.id());
    }

    public Optional<ConnectionConfig> getConfig(String id) {
        return Optional.ofNullable(configs.get(id));
    }

    public List<ConnectionConfig> getAllConfigs() {
        return List.copyOf(configs.values());
    }

    public void removeConfig(String id) {
        closeConnection(id);
        configs.remove(id);
    }

    /**
     * Obtiene una conexión activa (desde caché o creando una nueva).
     * @throws RuntimeException si el dialecto no existe o la conexión falla
     */
    public Connection getConnection(String connectionId) {
        Connection existing = pool.get(connectionId);
        try {
            if (existing != null && !existing.isClosed()) return existing;
        } catch (SQLException ignored) {}

        ConnectionConfig config = configs.get(connectionId);
        if (config == null) throw new IllegalArgumentException("Conexión no encontrada: " + connectionId);

        DBDialect dialect = dialectRegistry.findByName(config.dialect())
            .orElseThrow(() -> new IllegalArgumentException("Dialecto desconocido: " + config.dialect()));

        try {
            Class.forName(dialect.getDriverClass());
            int port = config.port() > 0 ? config.port() : dialect.getDefaultPort();
            ConnectionConfig withPort = new ConnectionConfig(
                config.id(), config.name(), config.dialect(),
                config.host(), port, config.database(),
                config.username(), config.password()
            );
            Connection conn = DriverManager.getConnection(
                dialect.buildJdbcUrl(withPort),
                config.username(),
                config.password()
            );
            pool.put(connectionId, conn);
            return conn;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver no encontrado: " + dialect.getDriverClass(), e);
        } catch (SQLException e) {
            throw new RuntimeException("Error de conexión: " + e.getMessage(), e);
        }
    }

    /**
     * Prueba una conexión sin guardarla.
     */
    public ConnectionResult testConnection(ConnectionConfig config) {
        DBDialect dialect = dialectRegistry.findByName(config.dialect())
            .orElse(null);
        if (dialect == null) return ConnectionResult.fail("Dialecto desconocido: " + config.dialect());

        try {
            Class.forName(dialect.getDriverClass());
            int port = config.port() > 0 ? config.port() : dialect.getDefaultPort();
            ConnectionConfig withPort = new ConnectionConfig(
                config.id(), config.name(), config.dialect(),
                config.host(), port, config.database(),
                config.username(), config.password()
            );
            try (Connection conn = DriverManager.getConnection(
                    dialect.buildJdbcUrl(withPort), config.username(), config.password())) {
                String version = conn.getMetaData().getDatabaseProductVersion();
                return ConnectionResult.ok(version);
            }
        } catch (Exception e) {
            return ConnectionResult.fail(e.getMessage());
        }
    }

    private void closeConnection(String id) {
        Connection conn = pool.remove(id);
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
