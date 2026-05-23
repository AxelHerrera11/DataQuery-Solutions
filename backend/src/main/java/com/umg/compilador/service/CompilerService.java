package com.umg.compilador.service;

import com.umg.compilador.compiler.ast.ASTNode.*;
import com.umg.compilador.compiler.lexer.*;
import com.umg.compilador.compiler.parser.*;
import com.umg.compilador.compiler.semantic.*;
import com.umg.compilador.compiler.token.Token;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.dialect.DialectRegistry;
import com.umg.compilador.dialect.impl.MongoDialect;
import com.umg.compilador.dto.*;
import com.umg.compilador.schema.DatabaseSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CompilerService {

    private final DialectRegistry   dialectRegistry;
    private final SchemaService     schemaService;

    public CompilerService(DialectRegistry dialectRegistry, SchemaService schemaService) {
        this.dialectRegistry = dialectRegistry;
        this.schemaService   = schemaService;
    }

    public CompileResponse compile(CompileRequest request) {
        List<CompileError> errors   = new ArrayList<>();
        List<CompileError> warnings = new ArrayList<>();

        if (request.sql() == null || request.sql().isBlank()) {
            errors.add(new CompileError("LEXER", 1, 1, "La query está vacía", "ERROR"));
            return response(false, errors, warnings, null,
                failed("Léxico"), skipped("Sintáctico"), skipped("Semántico"));
        }

        String dialectName = request.dialect() != null ? request.dialect() : "MYSQL";
        DBDialect dialect  = dialectRegistry.findByName(dialectName)
            .orElseGet(() -> dialectRegistry.findByName("MYSQL").orElseThrow());

        Set<String> dialectKeywords = dialect.getDialectKeywords();

        // ── MongoDB NATIVE MODE ────────────────────────────────────────
        if ("MONGODB".equals(dialectName)
            && request.sql() != null && request.sql().trim().startsWith("db.")) {

            MongoDialect mongoDialect = dialect instanceof MongoDialect md
                ? md : new MongoDialect();

            List<String> nativeErrors = mongoDialect.validateNativeSyntax(request.sql());
            nativeErrors.forEach(msg ->
                errors.add(new CompileError("DIALECT", 0, 0, msg, "ERROR"))
            );

            boolean valid = errors.isEmpty();
            String astJson = valid
                ? "{\"type\":\"MongoNativeQuery\",\"raw\":\""
                    + escapeJson(request.sql()) + "\"}"
                : null;

            return new CompileResponse(valid, errors, warnings, astJson,
                new CompileResponse.PhaseResult(true, "Nativo MongoDB"),
                valid
                    ? new CompileResponse.PhaseResult(true, "Sintaxis MongoDB válida")
                    : new CompileResponse.PhaseResult(false, "Sintaxis MongoDB con errores"),
                new CompileResponse.PhaseResult(true, "Modo nativo — semántica no aplicada"));
        }

        // ── FASE 1: LÉXICO (modo SQL) ─────────────────────────────────
        List<Token> tokens;
        try {
            Lexer lexer = new Lexer(request.sql(), dialectKeywords);
            tokens = lexer.tokenize();
        } catch (LexerException e) {
            errors.add(new CompileError("LEXER", e.getLine(), e.getColumn(), e.getMessage(), "ERROR"));
            return response(false, errors, warnings, null,
                failed("Léxico"), skipped("Sintáctico"), skipped("Semántico"));
        }

        // ── FASE 2: SINTÁCTICO ────────────────────────────────────────
        StatementNode ast;
        try {
            Parser parser = new Parser(tokens);
            ast = parser.parse();
        } catch (ParseException e) {
            errors.add(new CompileError("PARSER", e.getLine(), e.getColumn(), e.getMessage(), "ERROR"));
            return response(false, errors, warnings, null,
                passed("Léxico"), failed("Sintáctico"), skipped("Semántico"));
        }

        // Validación de dialecto específico
        dialect.validateDialectSyntax(request.sql()).forEach(msg ->
            errors.add(new CompileError("DIALECT", 0, 0, msg, "ERROR"))
        );
        if (!errors.isEmpty()) {
            return response(false, errors, warnings, ast,
                passed("Léxico"), failed("Dialecto"), skipped("Semántico"));
        }

        // ── FASE 3: SEMÁNTICO ─────────────────────────────────────────
        DatabaseSchema schema = request.connectionId() != null
            ? loadSchema(request.connectionId(), warnings)
            : DatabaseSchema.empty();

        SemanticAnalyzer.SemanticResult semResult =
            new SemanticAnalyzer(schema).analyze(ast);

        semResult.errors().forEach(msg ->
            errors.add(new CompileError("SEMANTIC", 0, 0, msg, "ERROR")));
        semResult.warnings().forEach(msg ->
            warnings.add(new CompileError("SEMANTIC", 0, 0, msg, "WARNING")));

        boolean valid = errors.isEmpty();
        return response(valid, errors, warnings, ast,
            passed("Léxico"), passed("Sintáctico"),
            valid ? passed("Semántico") : failed("Semántico"));
    }

    private DatabaseSchema loadSchema(String connectionId, List<CompileError> warnings) {
        try {
            return schemaService.getSchema(connectionId);
        } catch (Exception e) {
            warnings.add(new CompileError("SEMANTIC", 0, 0,
                "No se pudo cargar el schema: " + e.getMessage(), "WARNING"));
            return DatabaseSchema.empty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private CompileResponse.PhaseResult passed(String phase) {
        return new CompileResponse.PhaseResult(true, phase + " correcto");
    }
    private CompileResponse.PhaseResult failed(String phase) {
        return new CompileResponse.PhaseResult(false, phase + " con errores");
    }
    private CompileResponse.PhaseResult skipped(String phase) {
        return new CompileResponse.PhaseResult(false, phase + " omitido");
    }

    private CompileResponse response(boolean valid, List<CompileError> errors,
            List<CompileError> warnings, StatementNode ast,
            CompileResponse.PhaseResult lexer,
            CompileResponse.PhaseResult parser,
            CompileResponse.PhaseResult semantic) {
        String astJson = ast != null ? astToJson(ast) : null;
        return new CompileResponse(valid, errors, warnings, astJson, lexer, parser, semantic);
    }

    private String astToJson(StatementNode node) {
        return switch (node) {
            case SelectNode n      -> selectToJson(n);
            case InsertNode n      -> insertToJson(n);
            case UpdateNode n      -> updateToJson(n);
            case DeleteNode n      -> deleteToJson(n);
            case CreateTableNode n -> createTableToJson(n);
            case DropTableNode n   -> dropTableToJson(n);
            case AlterTableNode n  -> alterTableToJson(n);
            case TransactionNode n -> transactionToJson(n);
            case GrantNode n       -> grantToJson(n);
            case RevokeNode n      -> revokeToJson(n);
            case CreateIndexNode n -> createIndexToJson(n);
        };
    }

    // ── JSON serializers per node type ───────────────────────────────

    private String selectToJson(SelectNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"SelectNode\",");
        sb.append("\"selectAll\":").append(n.selectAll()).append(",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        if (!n.selectAll()) {
            sb.append("\"columns\":[");
            for (int i = 0; i < n.columns().size(); i++) {
                sb.append("\"").append(n.columns().get(i)).append("\"");
                if (i < n.columns().size() - 1) sb.append(",");
            }
            sb.append("],");
        }
        if (n.hasJoins()) {
            sb.append("\"joins\":[");
            for (int i = 0; i < n.joins().size(); i++) {
                if (i > 0) sb.append(",");
                var j = n.joins().get(i);
                sb.append("{\"type\":\"").append(j.type()).append("\",");
                sb.append("\"table\":\"").append(j.tableName()).append("\",");
                sb.append("\"condition\":").append(conditionToJson(j.condition())).append("}");
            }
            sb.append("],");
        }
        sb.append("\"where\":").append(conditionToJson(n.whereCondition()));
        if (n.hasGroupBy()) {
            sb.append(",\"groupBy\":[");
            for (int i = 0; i < n.groupBy().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(n.groupBy().get(i)).append("\"");
            }
            sb.append("]");
        }
        if (n.hasHaving()) {
            sb.append(",\"having\":").append(conditionToJson(n.having()));
        }
        if (n.hasOrderBy()) {
            sb.append(",\"orderBy\":[");
            for (int i = 0; i < n.orderBy().size(); i++) {
                if (i > 0) sb.append(",");
                var o = n.orderBy().get(i);
                sb.append("{\"column\":\"").append(o.column()).append("\",");
                sb.append("\"order\":\"").append(o.order()).append("\"}");
            }
            sb.append("]");
        }
        if (n.hasLimit()) {
            sb.append(",\"limit\":").append(n.limit());
        }
        if (n.hasOffset()) {
            sb.append(",\"offset\":").append(n.offset());
        }
        sb.append("}");
        return sb.toString();
    }

    private String insertToJson(InsertNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"InsertNode\",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        if (!n.columns().isEmpty()) {
            sb.append("\"columns\":[");
            for (int i = 0; i < n.columns().size(); i++) {
                sb.append("\"").append(n.columns().get(i)).append("\"");
                if (i < n.columns().size() - 1) sb.append(",");
            }
            sb.append("],");
        }
        if (n.isValuesInsert()) {
            sb.append("\"values\":[");
            for (int r = 0; r < n.values().size(); r++) {
                if (r > 0) sb.append(",");
                sb.append("[");
                var row = n.values().get(r);
                for (int c = 0; c < row.size(); c++) {
                    if (c > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(row.get(c).value())).append("\"");
                }
                sb.append("]");
            }
            sb.append("]");
        }
        if (n.isSelectInsert()) {
            sb.append(",\"selectQuery\":").append(selectToJson(n.selectQuery()));
        }
        sb.append("}");
        return sb.toString();
    }

    private String updateToJson(UpdateNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"UpdateNode\",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        sb.append("\"set\":[");
        for (int i = 0; i < n.assignments().size(); i++) {
            if (i > 0) sb.append(",");
            var a = n.assignments().get(i);
            sb.append("{\"column\":\"").append(a.column()).append("\",");
            sb.append("\"value\":\"").append(escapeJson(a.value().value())).append("\"}");
        }
        sb.append("],");
        sb.append("\"where\":").append(conditionToJson(n.whereCondition()));
        sb.append("}");
        return sb.toString();
    }

    private String deleteToJson(DeleteNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"DeleteNode\",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        sb.append("\"where\":").append(conditionToJson(n.whereCondition()));
        sb.append("}");
        return sb.toString();
    }

    private String createTableToJson(CreateTableNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"CreateTableNode\",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        sb.append("\"ifNotExists\":").append(n.ifNotExists()).append(",");
        sb.append("\"columns\":[");
        for (int i = 0; i < n.columns().size(); i++) {
            if (i > 0) sb.append(",");
            var c = n.columns().get(i);
            sb.append("{\"name\":\"").append(c.name()).append("\",");
            sb.append("\"type\":\"").append(c.type()).append("\"");
            if (!c.constraints().isEmpty()) {
                sb.append(",\"constraints\":[");
                for (int j = 0; j < c.constraints().size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(c.constraints().get(j)).append("\"");
                }
                sb.append("]");
            }
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String dropTableToJson(DropTableNode n) {
        return "{\"type\":\"DropTableNode\",\"tableName\":\""
            + n.tableName() + "\",\"ifExists\":" + n.ifExists() + "}";
    }

    private String alterTableToJson(AlterTableNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"AlterTableNode\",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        sb.append("\"alterType\":\"").append(n.alterType()).append("\",");
        sb.append("\"targetName\":\"").append(n.targetName()).append("\"");
        if (n.dataType() != null)
            sb.append(",\"dataType\":\"").append(n.dataType()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String transactionToJson(TransactionNode n) {
        return "{\"type\":\"TransactionNode\",\"txnType\":\"" + n.txnType() + "\"}";
    }

    private String grantToJson(GrantNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"GrantNode\",");
        sb.append("\"privileges\":[");
        for (int i = 0; i < n.privileges().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(n.privileges().get(i)).append("\"");
        }
        sb.append("],");
        sb.append("\"object\":\"").append(n.object()).append("\",");
        sb.append("\"user\":\"").append(n.user()).append("\"}");
        return sb.toString();
    }

    private String revokeToJson(RevokeNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"RevokeNode\",");
        sb.append("\"privileges\":[");
        for (int i = 0; i < n.privileges().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(n.privileges().get(i)).append("\"");
        }
        sb.append("],");
        sb.append("\"object\":\"").append(n.object()).append("\",");
        sb.append("\"user\":\"").append(n.user()).append("\"}");
        return sb.toString();
    }

    private String createIndexToJson(CreateIndexNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"CreateIndexNode\",");
        sb.append("\"indexName\":\"").append(n.indexName()).append("\",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
            sb.append("\"unique\":").append(n.unique()).append(",");
        sb.append("\"columns\":[");
        for (int i = 0; i < n.columns().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(n.columns().get(i)).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String conditionToJson(Condition cond) {
        if (cond == null) return "null";
        return switch (cond) {
            case SimpleCondition sc ->
                "{\"left\":\"" + escapeJson(sc.left().value())
                + "\",\"operator\":\"" + sc.operator().symbol()
                + "\",\"right\":\"" + escapeJson(sc.right().value()) + "\"}";
            case CompoundCondition cc ->
                "{\"operator\":\"" + cc.operator()
                + "\",\"left\":" + conditionToJson(cc.left())
                + ",\"right\":" + conditionToJson(cc.right()) + "}";
        };
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
