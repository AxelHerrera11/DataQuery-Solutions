package com.umg.compilador.dialect;

import com.umg.compilador.dialect.loader.DialectLoader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * DialectRegistry — registro central de dialectos.
 *
 * Versión original: Map.of(...) con las 4 implementaciones hardcodeadas.
 * Versión actual:   delega al DialectLoader que leyó todo desde MySQL.
 *
 * Para agregar un motor nuevo:
 *   1. INSERT INTO dialect (...)
 *   2. INSERT INTO keyword (...)  para sus keywords
 *   3. INSERT INTO data_type_mapping (...) para sus tipos
 *   4. INSERT INTO statement_syntax (...) para sus sentencias
 *   5. Reiniciar el backend — DialectLoader lo carga automáticamente.
 *
 * No se toca ningún archivo Java.
 */
@Component
public class DialectRegistry {

    private final DialectLoader loader;

    public DialectRegistry(DialectLoader loader) {
        this.loader = loader;
    }

    public Optional<DBDialect> findByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(loader.getLoaded().get(name.toUpperCase()));
    }

    public List<DBDialect> getAllDialects() {
        return List.copyOf(loader.getLoaded().values());
    }
}
