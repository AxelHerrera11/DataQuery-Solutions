package com.umg.compilador.dialect;

import com.umg.compilador.dialect.impl.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DialectRegistry — registro central de dialectos.
 * Actúa como factory: dado un nombre devuelve la implementación.
 */
@Component
public class DialectRegistry {

    private final Map<String, DBDialect> dialects;

    public DialectRegistry() {
        dialects = Map.of(
            "MYSQL",      new MySQLDialect(),
            "POSTGRESQL", new PostgreSQLDialect(),
            "SQLSERVER",  new SQLServerDialect(),
            "MONGODB",    new MongoDialect()
        );
    }

    public Optional<DBDialect> findByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(dialects.get(name.toUpperCase()));
    }

    public List<DBDialect> getAllDialects() {
        return List.copyOf(dialects.values());
    }
}
