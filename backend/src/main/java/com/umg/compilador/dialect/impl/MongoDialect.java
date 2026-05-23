package com.umg.compilador.dialect.impl;

import com.mongodb.client.*;
import com.umg.compilador.connection.ConnectionConfig;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.schema.*;
import org.bson.Document;

import java.sql.Connection;
import java.util.*;
import java.util.regex.*;

public class MongoDialect implements DBDialect {

    private static final Set<String> VALID_COMMANDS = Set.of(
        "find", "findOne", "findOneAndUpdate", "findOneAndDelete",
        "aggregate", "insertOne", "insertMany",
        "updateOne", "updateMany", "replaceOne",
        "deleteOne", "deleteMany", "countDocuments",
        "estimatedDocumentCount", "distinct", "bulkWrite",
        "createIndex", "dropIndex", "drop",
        "renameCollection", "watch", "mapReduce"
    );

    private static final Pattern NATIVE_PATTERN =
        Pattern.compile("^db\\.(\\w+)\\.(\\w+)\\s*\\((.*)\\)\\s*$", Pattern.DOTALL);

    @Override public String getName()        { return "MONGODB"; }
    @Override public String getDisplayName() { return "MongoDB 6+"; }
    @Override public int    getDefaultPort() { return 27017; }
    @Override public String getBrandColor()  { return "#47A248"; }
    @Override public boolean supportsTransactions() { return false; }

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
        if (c.username() != null && !c.username().isBlank()) {
            return "mongodb://%s:%s@%s:%d/%s".formatted(
                c.username(), c.password(), c.host(), c.port(), c.database());
        }
        return "mongodb://%s:%d/%s".formatted(c.host(), c.port(), c.database());
    }

    @Override
    public DatabaseSchema extractSchema(Connection ignored, String databaseName) {
        return DatabaseSchema.empty();
    }

    public DatabaseSchema extractSchemaFromClient(MongoClient client, String databaseName) {
        List<SchemaTable> tables = new ArrayList<>();
        MongoDatabase db = client.getDatabase(databaseName);

        for (String collectionName : db.listCollectionNames()) {
            MongoCollection<Document> collection = db.getCollection(collectionName);
            List<SchemaColumn> columns = new ArrayList<>();

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

    // ── SQL-like mode ──────────────────────────────────────────────────

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

    // ── Native MongoDB mode ────────────────────────────────────────────

    /**
     * Detecta si la query es sintaxis nativa MongoDB (empieza con "db.").
     */
    public boolean isNativeSyntax(String query) {
        return query != null && query.trim().startsWith("db.");
    }

    /**
     * Valida una query en sintaxis nativa MongoDB.
     * Soporta: db.collection.command(args) y encadenamiento .limit().skip().sort()
     */
    public List<String> validateNativeSyntax(String query) {
        List<String> errors = new ArrayList<>();
        String trimmed = normalizeQuery(query.trim());

        if (trimmed.isEmpty()) {
            errors.add("La query MongoDB está vacía");
            return errors;
        }

        if (!trimmed.startsWith("db.")) {
            errors.add("Sintaxis nativa MongoDB debe empezar con 'db.'  — ej: db.coleccion.find({...})");
            return errors;
        }

        int bodyStart = trimmed.indexOf('(');
        if (bodyStart == -1) {
            errors.add("Faltan paréntesis — ej: db.coleccion.find({campo: valor})");
            return errors;
        }

        if (!trimmed.endsWith(")")) {
            errors.add("Paréntesis sin cerrar al final");
            return errors;
        }

        // Validar balance de paréntesis
        int depth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth < 0) {
                errors.add("Paréntesis de cierre sin apertura en posición " + i);
                return errors;
            }
        }
        if (depth != 0) {
            errors.add("Paréntesis desbalanceados: faltan " + depth + " paréntesis de cierre");
        }

        // Validar cada comando encadenado
        List<String> parts = splitChainedCommands(trimmed, errors);
        if (parts == null) return errors;

        for (String part : parts) {
            validateSingleCommand(part.trim(), errors);
        }

        return errors;
    }

    private List<String> splitChainedCommands(String query, List<String> errors) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;

        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;

            if (depth == 0 && c == '.' && i > 0 && query.charAt(i - 1) == ')') {
                String part = query.substring(start, i);
                if (!part.isBlank()) parts.add(part);
                start = i;
            }
        }
        if (start < query.length()) {
            String part = query.substring(start);
            if (!part.isBlank()) parts.add(part);
        }

        // First part must be db.collection.command
        if (parts.isEmpty()) {
            errors.add("No se pudo parsear la query");
            return null;
        }

        return parts;
    }

    private void validateSingleCommand(String command, List<String> errors) {
        Matcher m = NATIVE_PATTERN.matcher(command);
        if (!m.matches()) {
            // Try chained method call: .limit(10), .skip(5), .sort({...})
            Pattern chainPattern = Pattern.compile("^\\.(\\w+)\\s*\\((.*)\\)\\s*$", Pattern.DOTALL);
            Matcher cm = chainPattern.matcher(command);
            if (cm.matches()) {
                String methodName = cm.group(1);
                String args = cm.group(2).trim();
                validateChainedMethod(methodName, args, errors);
                return;
            }
            errors.add("Sintaxis inválida: " + command);
            return;
        }

        String collection = m.group(1);
        String cmdName = m.group(2);
        String args = m.group(3).trim();

        if (collection.isEmpty()) {
            errors.add("Falta el nombre de la colección");
        }

        if (!VALID_COMMANDS.contains(cmdName)) {
            errors.add("Comando MongoDB no reconocido: '" + cmdName
                + "'. Válidos: " + String.join(", ", VALID_COMMANDS));
        }

        // Validate JSON-like arguments if present
        if (!args.isEmpty()) {
            validateArguments(cmdName, args, errors);
        }
    }

    private void validateChainedMethod(String method, String args, List<String> errors) {
        Set<String> validChained = Set.of(
            "limit", "skip", "sort", "project", "hint",
            "maxTimeMS", "batchSize", "noCursorTimeout",
            "collation", "allowDiskUse", "maxAwaitTimeMS"
        );

        if (!validChained.contains(method)) {
            errors.add("Método encadenado no reconocido: '." + method
                + "()'. Válidos: " + String.join(", ", validChained));
        }

        if (!args.isEmpty()) {
            if (method.equals("limit") || method.equals("skip")
                || method.equals("batchSize") || method.equals("maxTimeMS")) {
                try {
                    Integer.parseInt(args);
                } catch (NumberFormatException e) {
                    errors.add("." + method + "() espera un número entero, se recibió: " + args);
                }
            }
        }
    }

    private void validateArguments(String cmdName, String args, List<String> errors) {
        if (args.isEmpty()) return;

        // Validate bracket balance
        int curly = 0, square = 0, paren = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '{') curly++;
            else if (c == '}') curly--;
            else if (c == '[') square++;
            else if (c == ']') square--;
            else if (c == '(') paren++;
            else if (c == ')') paren--;
        }
        if (curly != 0) errors.add("Llaves {} desbalanceadas en los argumentos de " + cmdName);
        if (square != 0) errors.add("Corchetes [] desbalanceados en los argumentos de " + cmdName);
        if (paren != 0) errors.add("Paréntesis () desbalanceados en los argumentos de " + cmdName);

        // Try BSON parse for simple queries (find, insertOne, etc.)
        if (curly == 0 && square == 0 && paren == 0 && !cmdName.equals("aggregate")) {
            try {
                if (args.startsWith("{")) {
                    Document.parse(fixKeys(args));
                }
            } catch (Exception e) {
                errors.add("Error sintáctico en los argumentos de " + cmdName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Normaliza la query eliminando espacios alrededor de puntos encadenados.
     * Ej: "}) .limit(" -> "}).limit("
     */
    private String normalizeQuery(String q) {
        return q.replaceAll("\\)\\s*\\.\\s*", ").");
    }

    /**
     * Convierte keys sin comillas a JSON válido para parseo con Document.parse()
     * Ej: {name: "John", age: 25} -> {"name": "John", "age": 25}
     * No afecta $operators ($match, $gt) ni keys ya entrecomilladas.
     */
    private String fixKeys(String jsonLike) {
        return jsonLike.replaceAll("(?<![\\w$. \"'(:])(\\w+)(\\s*:)", "\"$1\"$2");
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
