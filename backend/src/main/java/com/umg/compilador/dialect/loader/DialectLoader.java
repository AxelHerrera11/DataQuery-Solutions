package com.umg.compilador.dialect.loader;

import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.model.*;
import com.umg.compilador.repository.*;
import com.umg.compilador.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DialectLoader — carga todos los dialectos desde MySQL al arrancar.
 *
 * Reemplaza el Map.of(...) hardcodeado en DialectRegistry.
 * Al iniciar Spring Boot lee dialect + keywords + data_type_mapping
 * + statement_syntax y construye una implementación dinámica de
 * DBDialect para cada motor registrado en la BD.
 *
 * Agregar un motor nuevo = INSERT en la BD, sin tocar Java.
 */
@Component
public class DialectLoader {

    private static final Logger log = LoggerFactory.getLogger(DialectLoader.class);

    private final DialectRepository          dialectRepo;
    private final KeywordRepository          keywordRepo;
    private final DataTypeMappingRepository  typeMappingRepo;
    private final StatementSyntaxRepository  syntaxRepo;

    // Cache: dialectName → implementación dinámica
    private final Map<String, DBDialect> loaded = new ConcurrentHashMap<>();

    public DialectLoader(DialectRepository dialectRepo,
                         KeywordRepository keywordRepo,
                         DataTypeMappingRepository typeMappingRepo,
                         StatementSyntaxRepository syntaxRepo) {
        this.dialectRepo     = dialectRepo;
        this.keywordRepo     = keywordRepo;
        this.typeMappingRepo = typeMappingRepo;
        this.syntaxRepo      = syntaxRepo;
    }

    /**
     * ApplicationReadyEvent se dispara DESPUÉS de que Spring ejecuta
     * schema.sql y data.sql — garantiza que las tablas ya existen.
     * @PostConstruct se disparaba ANTES y causaba "Table doesn't exist".
     */
    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        List<DialectEntity> dialects = dialectRepo.findAllWithKeywords();
        dialects.forEach(entity -> {
            DBDialect dynamic = buildDynamic(entity);
            loaded.put(entity.getName().toUpperCase(), dynamic);
        });
        log.info("{} dialectos cargados desde BD: {}", loaded.size(), loaded.keySet());
    }

    public Map<String, DBDialect> getLoaded() {
        if (loaded.isEmpty()) {
            // Carga bajo demanda si ApplicationReadyEvent aún no disparó
            // (puede ocurrir en tests o llamadas tempranas)
            load();
        }
        return Collections.unmodifiableMap(loaded);
    }

    // ── Construcción de la implementación dinámica ────────────────────

    private DBDialect buildDynamic(DialectEntity entity) {
        // keywords del dialecto
        Set<String> keywords = keywordRepo
            .findWordsByDialectName(entity.getName());

        // mappings nativeType → abstractType (nombre)
        Map<String, String> typeMap = typeMappingRepo
            .findByDialectName(entity.getName()).stream()
            .collect(Collectors.toMap(
                m -> m.getNativeType().toUpperCase(),
                m -> m.getAbstractType().getName(),
                (a, b) -> a  // si hay duplicado, conservar el primero
            ));

        // templates de sintaxis por sentencia
        Map<String, String> syntaxMap = syntaxRepo
            .findAllByDialectName(entity.getName()).stream()
            .filter(StatementSyntaxEntity::isSupported)
            .filter(s -> s.getSyntaxTemplate() != null)
            .collect(Collectors.toMap(
                s -> s.getStatement().getName(),
                StatementSyntaxEntity::getSyntaxTemplate,
                (a, b) -> a
            ));

        return new DynamicDialect(entity, keywords, typeMap, syntaxMap);
    }

    // ── DynamicDialect — implementación generada desde BD ─────────────

    private static class DynamicDialect implements DBDialect {

        private final DialectEntity     entity;
        private final Set<String>       keywords;
        private final Map<String,String> typeMap;
        private final Map<String,String> syntaxMap;

        DynamicDialect(DialectEntity entity, Set<String> keywords,
                       Map<String,String> typeMap, Map<String,String> syntaxMap) {
            this.entity    = entity;
            this.keywords  = Set.copyOf(keywords);
            this.typeMap   = Map.copyOf(typeMap);
            this.syntaxMap = Map.copyOf(syntaxMap);
        }

        @Override public String  getName()          { return entity.getName(); }
        @Override public String  getDisplayName()   { return entity.getDisplayName(); }
        @Override public int     getDefaultPort()   { return entity.getDefaultPort(); }
        @Override public String  getBrandColor()    { return entity.getBrandColor(); }
        @Override public boolean supportsTransactions() { return entity.isSupportsTxn(); }
        @Override public String  getDriverClass()   { return entity.getDriverClass() != null ? entity.getDriverClass() : ""; }
        @Override public Set<String> getDialectKeywords() { return keywords; }

        @Override
        public String buildJdbcUrl(ConnectionConfig config) {
            return entity.getJdbcUrlPattern()
                .replace("{host}",     config.host())
                .replace("{port}",     String.valueOf(config.port()))
                .replace("{db}",       config.database());
        }

        /**
         * Extrae el schema real de la BD.
         * Para MySQL/PostgreSQL/SQLServer: consulta INFORMATION_SCHEMA.
         * Para MongoDB: DynamicDialect delega al MongoDialect real.
         */
        @Override
        public DatabaseSchema extractSchema(Connection connection, String databaseName) {
            if ("MONGODB".equals(entity.getName())) {
                // MongoDB no usa JDBC — retorna vacío; SchemaService lo maneja aparte
                return DatabaseSchema.empty();
            }

            List<SchemaTable> tables = new ArrayList<>();
            String sql = buildSchemaQuery();

            try (var ps = connection.prepareStatement(sql)) {
                ps.setString(1, databaseName);
                if ("POSTGRESQL".equals(entity.getName())) ps.setString(2, databaseName);
                var rs = ps.executeQuery();

                Map<String, List<SchemaColumn>> map = new LinkedHashMap<>();
                while (rs.next()) {
                    String tName    = rs.getString("table_name");
                    String colName  = rs.getString("column_name");
                    String nativeT  = rs.getString("data_type").toUpperCase();
                    boolean nullable= "YES".equals(rs.getString("is_nullable"));
                    boolean pk      = "PRI".equals(rs.getString("column_key"))
                                   || "YES".equals(rs.getString("column_key"));

                    DataType abstractType = resolveDataType(nativeT);
                    map.computeIfAbsent(tName, k -> new ArrayList<>())
                       .add(new SchemaColumn(colName, abstractType, nullable, pk));
                }
                map.forEach((n, cols) -> tables.add(new SchemaTable(n, cols)));
            } catch (Exception e) {
                throw new RuntimeException(
                    "Error extrayendo schema [%s]: %s".formatted(entity.getName(), e.getMessage()), e);
            }
            return new DatabaseSchema(databaseName, entity.getName(), tables);
        }

        @Override
        public List<String> validateDialectSyntax(String sql) {
            List<String> errors = new ArrayList<>();
            String upper = sql.toUpperCase().trim();

            // Validaciones genéricas basadas en syntaxMap
            if (upper.contains("LIMIT") && !syntaxMap.containsKey("SELECT")) {
                errors.add(entity.getName() + ": LIMIT no está soportado en este dialecto");
            }
            if (upper.contains("RETURNING") && !keywords.contains("RETURNING")) {
                errors.add(entity.getName() + ": RETURNING no está soportado en este dialecto");
            }
            if (upper.startsWith("SELECT") && upper.contains("TOP") && !keywords.contains("TOP")) {
                errors.add(entity.getName() + ": TOP no está soportado; usa LIMIT");
            }
            if (upper.startsWith("SELECT") && upper.contains("LIMIT") && keywords.contains("TOP")
                && !keywords.contains("LIMIT")) {
                errors.add(entity.getName() + ": usa TOP en lugar de LIMIT");
            }

            return errors;
        }

        /** Devuelve el template de sintaxis para una sentencia. */
        public String getSyntaxTemplate(String statementName) {
            return syntaxMap.getOrDefault(statementName, "Sin template disponible");
        }

        // ── Helpers privados ──────────────────────────────────────────

        private DataType resolveDataType(String nativeType) {
            String abstractName = typeMap.get(nativeType);
            if (abstractName == null) return DataType.UNKNOWN;
            try { return DataType.valueOf(abstractName); }
            catch (IllegalArgumentException e) { return DataType.UNKNOWN; }
        }

        private String buildSchemaQuery() {
            return switch (entity.getName()) {
                case "POSTGRESQL" -> """
                    SELECT c.table_name, c.column_name, c.data_type, c.is_nullable,
                           CASE WHEN pk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END AS column_key
                    FROM information_schema.columns c
                    LEFT JOIN (
                        SELECT ku.table_name, ku.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage ku
                          ON tc.constraint_name = ku.constraint_name
                        WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = 'public'
                    ) pk ON c.table_name = pk.table_name AND c.column_name = pk.column_name
                    WHERE c.table_schema = 'public'
                    ORDER BY c.table_name, c.ordinal_position
                    """;
                case "SQLSERVER" -> """
                    SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name,
                           c.DATA_TYPE AS data_type, c.IS_NULLABLE AS is_nullable,
                           CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'YES' ELSE 'NO' END AS column_key
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
                default -> // MySQL
                    """
                    SELECT TABLE_NAME AS table_name, COLUMN_NAME AS column_name,
                           DATA_TYPE AS data_type, IS_NULLABLE AS is_nullable,
                           CASE WHEN COLUMN_KEY = 'PRI' THEN 'YES' ELSE 'NO' END AS column_key
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = ?
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """;
            };
        }
    }
}
