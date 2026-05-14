package com.umg.compilador.compiler.semantic;

import com.umg.compilador.compiler.ast.ASTNode.SelectNode;
import com.umg.compilador.compiler.ast.ASTNode.ConditionNode;
import com.umg.compilador.compiler.ast.ASTNode.ExpressionNode;
import com.umg.compilador.schema.DataType;
import com.umg.compilador.schema.DatabaseSchema;
import com.umg.compilador.schema.SchemaColumn;
import com.umg.compilador.schema.SchemaTable;

import java.util.*;

/**
 * SemanticAnalyzer — validación semántica del AST.
 * Adaptado del proyecto base:
 *   • Recibe DatabaseSchema en lugar de SymbolTable hardcoded
 *   • La lógica de validación es idéntica al original
 *   • Si el schema está vacío (sin conexión), omite validaciones de existencia
 */
public class SemanticAnalyzer {

    private final DatabaseSchema schema;
    private final List<String>   errors   = new ArrayList<>();
    private final List<String>   warnings = new ArrayList<>();

    public SemanticAnalyzer(DatabaseSchema schema) {
        this.schema = schema;
    }

    public SemanticResult analyze(SelectNode ast) {
        errors.clear();
        warnings.clear();

        if (ast == null) {
            errors.add("El AST está vacío");
            return result();
        }

        // Si no hay schema real (sin conexión) no se valida semánticamente
        if (schema.isEmpty()) {
            warnings.add("Sin conexión activa: se omite la validación semántica de tablas y columnas");
            return result();
        }

        Optional<SchemaTable> tableOpt = schema.findTable(ast.tableName());
        if (tableOpt.isEmpty()) {
            errors.add("La tabla '%s' no existe en el schema".formatted(ast.tableName()));
            return result();
        }

        SchemaTable table = tableOpt.get();
        validateColumns(ast, table);
        if (ast.hasWhere()) validateCondition(ast.whereCondition(), table);

        return result();
    }

    private void validateColumns(SelectNode node, SchemaTable table) {
        if (node.isSelectAll()) return;
        for (String colName : node.columns()) {
            if (table.findColumn(colName).isEmpty())
                errors.add("La columna '%s' no existe en la tabla '%s'".formatted(colName, table.name()));
        }
    }

    private void validateCondition(ConditionNode cond, SchemaTable table) {
        DataType left  = resolveType(cond.left(),  table);
        DataType right = resolveType(cond.right(), table);
        if (!areCompatible(left, right))
            errors.add("Tipos incompatibles en WHERE: %s %s %s".formatted(left, cond.operator().symbol(), right));
    }

    private DataType resolveType(ExpressionNode expr, SchemaTable table) {
        return switch (expr.exprType()) {
            case STRING     -> DataType.VARCHAR;
            case NUMBER     -> expr.value().contains(".") ? DataType.FLOAT : DataType.INT;
            case IDENTIFIER -> {
                Optional<SchemaColumn> col = table.findColumn(expr.value());
                yield col.map(SchemaColumn::type).orElse(DataType.VARCHAR);
            }
        };
    }

    private boolean areCompatible(DataType a, DataType b) {
        if (a == b) return true;
        boolean aNum = (a == DataType.INT || a == DataType.FLOAT);
        boolean bNum = (b == DataType.INT || b == DataType.FLOAT);
        return aNum && bNum;
    }

    private SemanticResult result() {
        return new SemanticResult(List.copyOf(errors), List.copyOf(warnings));
    }

    public record SemanticResult(List<String> errors, List<String> warnings) {
        public boolean isValid() { return errors.isEmpty(); }
    }
}
