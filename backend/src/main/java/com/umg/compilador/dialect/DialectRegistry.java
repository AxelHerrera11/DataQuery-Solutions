package com.umg.compilador.dialect;

import com.umg.compilador.dialect.impl.*;
import com.umg.compilador.dialect.loader.DialectLoader;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DialectRegistry — registro central de dialectos.
 *
 * Unifica los dialectos hardcodeados (MySQLDialect, PostgreSQLDialect, etc.)
 * con los dialectos dinámicos cargados desde la BD.
 *
 * Estrategia de resolución:
 *   1. Si existe implementación hardcodeada, se usa ESA como base
 *      y se inyectan los keywords adicionales desde la BD.
 *   2. Si no existe hardcodeada, se usa el DynamicDialect cargado desde BD.
 *
 * Esto permite que los dialectos hardcodeados tengan lógica richer
 * (extractSchema, validateDialectSyntax) mientras que los dinámicos
 * mantienen la extensibilidad sin tocar Java.
 */
@Component
public class DialectRegistry {

    //cometar

    private final DialectLoader loader;
    private final Map<String, DBDialect> hardcoded = new LinkedHashMap<>();

    // Dialectos que requieren driver nativo (no JDBC)
    private static final Set<String> NATIVE_DRIVER_DIALECTS = Set.of("MONGODB");

    public DialectRegistry(DialectLoader loader) {
        this.loader = loader;
        registerHardcoded();
    }

    private void registerHardcoded() {
        register(new MySQLDialect());
        register(new PostgreSQLDialect());
        register(new SQLServerDialect());
        register(new MongoDialect());
    }

    private void register(DBDialect dialect) {
        hardcoded.put(dialect.getName().toUpperCase(), dialect);
    }

    public Optional<DBDialect> findByName(String name) {
        if (name == null) return Optional.empty();
        String key = name.toUpperCase();

        // 1. Try hardcoded first (has richer extractSchema, validateDialectSyntax)
        DBDialect hc = hardcoded.get(key);
        if (hc != null) return Optional.of(hc);

        // 2. Fall back to dynamic dialect from DB
        DBDialect dyn = loader.getLoaded().get(key);
        if (dyn != null) return Optional.of(dyn);

        return Optional.empty();
    }

    public List<DBDialect> getAllDialects() {
        List<DBDialect> all = new ArrayList<>(hardcoded.values());
        loader.getLoaded().values().stream()
            .filter(d -> !hardcoded.containsKey(d.getName().toUpperCase()))
            .forEach(all::add);
        return List.copyOf(all);
    }

    /** Returns true if the dialect uses a native driver instead of JDBC. */
    public static boolean isNativeDriver(String dialectName) {
        return NATIVE_DRIVER_DIALECTS.contains(dialectName.toUpperCase());
    }
}
