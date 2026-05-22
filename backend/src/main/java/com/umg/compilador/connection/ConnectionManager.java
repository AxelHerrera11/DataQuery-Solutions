package com.umg.compilador.connection;

import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.dialect.DialectRegistry;
import com.umg.compilador.model.SavedConnectionEntity;
import com.umg.compilador.repository.SavedConnectionRepository;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionManager {

    private final DialectRegistry           dialectRegistry;
    private final SavedConnectionRepository repository;
    private final PasswordEncryptor         encryptor;
    private final Map<String, Connection>   pool    = new ConcurrentHashMap<>();
    private final Map<String, ConnectionConfig> configs = new ConcurrentHashMap<>();

    public ConnectionManager(DialectRegistry dialectRegistry,
                             SavedConnectionRepository repository,
                             PasswordEncryptor encryptor) {
        this.dialectRegistry = dialectRegistry;
        this.repository      = repository;
        this.encryptor       = encryptor;
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        repository.findAllByOrderByUpdatedAtDesc().forEach(entity -> {
            configs.put(entity.getId(), new ConnectionConfig(
                entity.getId(), entity.getName(), entity.getDialect(),
                entity.getHost(), entity.getPort(), entity.getDatabaseName(),
                entity.getUsername(), encryptor.decrypt(entity.getEncryptedPassword())
            ));
        });
    }

    public void saveConfig(ConnectionConfig config) {
        configs.put(config.id(), config);
        closeConnection(config.id());
        repository.save(new SavedConnectionEntity(
            config.id(), config.name(), config.dialect(),
            config.host(), config.port(), config.database(),
            config.username(), encryptor.encrypt(config.password())
        ));
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
        repository.deleteById(id);
    }

    public Connection getConnection(String connectionId) {
        Connection existing = pool.get(connectionId);
        try {
            if (existing != null && !existing.isClosed()) return existing;
        } catch (SQLException ignored) {}

        ConnectionConfig config = configs.get(connectionId);
        if (config == null)
            throw new IllegalArgumentException("Conexión no encontrada: " + connectionId);

        DBDialect dialect = dialectRegistry.findByName(config.dialect())
            .orElseThrow(() -> new IllegalArgumentException("Dialecto desconocido: " + config.dialect()));

        if (DialectRegistry.isNativeDriver(config.dialect())) {
            return null;
        }

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

    public ConnectionResult testConnection(ConnectionConfig config) {
        DBDialect dialect = dialectRegistry.findByName(config.dialect())
            .orElse(null);
        if (dialect == null)
            return ConnectionResult.fail("Dialecto desconocido: " + config.dialect());

        if (DialectRegistry.isNativeDriver(config.dialect())) {
            try {
                String uri = dialect.buildJdbcUrl(config);
                com.mongodb.client.MongoClients.create(uri).close();
                return ConnectionResult.ok("Conexión exitosa");
            } catch (Exception e) {
                return ConnectionResult.fail(e.getMessage());
            }
        }

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
