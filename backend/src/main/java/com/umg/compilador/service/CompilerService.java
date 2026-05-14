package com.umg.compilador.service;

import com.umg.compilador.compiler.ast.ASTNode.SelectNode;
import com.umg.compilador.compiler.lexer.*;
import com.umg.compilador.compiler.parser.*;
import com.umg.compilador.compiler.semantic.*;
import com.umg.compilador.compiler.token.Token;
import com.umg.compilador.dialect.DBDialect;
import com.umg.compilador.dialect.DialectRegistry;
import com.umg.compilador.dto.*;
import com.umg.compilador.schema.DatabaseSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CompilerService — orquesta el pipeline completo de compilación.
 * Equivalente a SqlCompiler.compile() del proyecto base,
 * pero devuelve un CompileResponse en lugar de imprimir en consola.
 */
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

        // Obtener dialecto (default MYSQL si no se especifica)
        String dialectName = request.dialect() != null ? request.dialect() : "MYSQL";
        DBDialect dialect  = dialectRegistry.findByName(dialectName)
            .orElseGet(() -> dialectRegistry.findByName("MYSQL").orElseThrow());

        Set<String> dialectKeywords = dialect.getDialectKeywords();

        // ── FASE 1: LÉXICO ────────────────────────────────────────────
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
        SelectNode ast;
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
            List<CompileError> warnings, SelectNode ast,
            CompileResponse.PhaseResult lexer,
            CompileResponse.PhaseResult parser,
            CompileResponse.PhaseResult semantic) {
        String astJson = ast != null ? astToJson(ast) : null;
        return new CompileResponse(valid, errors, warnings, astJson, lexer, parser, semantic);
    }

    private String astToJson(SelectNode n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"SelectNode\",");
        sb.append("\"selectAll\":").append(n.isSelectAll()).append(",");
        sb.append("\"tableName\":\"").append(n.tableName()).append("\",");
        if (!n.isSelectAll()) {
            sb.append("\"columns\":[");
            for (int i = 0; i < n.columns().size(); i++) {
                sb.append("\"").append(n.columns().get(i)).append("\"");
                if (i < n.columns().size()-1) sb.append(",");
            }
            sb.append("],");
        }
        if (n.hasWhere()) {
            var w = n.whereCondition();
            sb.append("\"where\":{");
            sb.append("\"left\":\"").append(w.left().value()).append("\",");
            sb.append("\"operator\":\"").append(w.operator().symbol()).append("\",");
            sb.append("\"right\":\"").append(w.right().value()).append("\"");
            sb.append("}");
        } else {
            sb.append("\"where\":null");
        }
        sb.append("}");
        return sb.toString();
    }
}
