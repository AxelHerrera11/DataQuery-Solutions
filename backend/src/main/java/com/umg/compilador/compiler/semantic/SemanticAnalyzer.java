package com.umg.compilador.compiler.semantic;

import com.umg.compilador.compiler.ast.ASTNode.*;
import com.umg.compilador.schema.DataType;
import com.umg.compilador.schema.DatabaseSchema;
import com.umg.compilador.schema.SchemaColumn;
import com.umg.compilador.schema.SchemaTable;

import java.util.*;

public class SemanticAnalyzer {

    private final DatabaseSchema schema;
    private final List<String>   errors   = new ArrayList<>();
    private final List<String>   warnings = new ArrayList<>();

    public SemanticAnalyzer(DatabaseSchema schema) {
        this.schema = schema;
    }

    public SemanticResult analyze(StatementNode ast) {
        errors.clear();
        warnings.clear();

        if (ast == null) {
            errors.add("El AST está vacío");
            return result();
        }

        if (schema.isEmpty()) {
            warnings.add("Sin conexión activa: se omite la validación semántica");
            return result();
        }

        switch (ast) {
            case SelectNode n      -> analyzeSelect(n);
            case InsertNode n      -> analyzeInsert(n);
            case UpdateNode n      -> analyzeUpdate(n);
            case DeleteNode n      -> analyzeDelete(n);
            case CreateTableNode n -> analyzeCreateTable(n);
            case DropTableNode n   -> analyzeDropTable(n);
            case AlterTableNode n  -> analyzeAlterTable(n);
            case TransactionNode n -> {}
            case GrantNode n       -> analyzeGrant(n);
            case RevokeNode n      -> analyzeRevoke(n);
            case CreateIndexNode n -> analyzeCreateIndex(n);
        }

        return result();
    }

    // ── SELECT ───────────────────────────────────────────────────────

    private void analyzeSelect(SelectNode node) {
        SchemaTable table = resolveTable(node.tableName());
        if (table == null) return;

        validateSelectedColumns(node.columns(), node.selectAll(), table);
        if (node.hasWhere()) validateCondition(node.whereCondition(), table);
    }

    private void validateSelectedColumns(List<String> columns, boolean selectAll, SchemaTable table) {
        if (selectAll) return;
        for (String colName : columns) {
            if (table.findColumn(colName).isEmpty())
                errors.add("La columna '%s' no existe en la tabla '%s'".formatted(colName, table.name()));
        }
    }

    // ── INSERT ───────────────────────────────────────────────────────

    private void analyzeInsert(InsertNode node) {
        SchemaTable table = resolveTable(node.tableName());
        if (table == null) return;

        List<String> targetCols = resolveInsertColumns(node, table);

        if (node.isValuesInsert()) {
            validateInsertValues(node, targetCols, table);
        }

        if (node.isSelectInsert()) {
            validateInsertSelect(node, targetCols, table);
        }
    }

    private List<String> resolveInsertColumns(InsertNode node, SchemaTable table) {
        if (!node.columns().isEmpty()) {
            for (String col : node.columns()) {
                if (table.findColumn(col).isEmpty())
                    errors.add("La columna '%s' no existe en la tabla '%s'"
                        .formatted(col, node.tableName()));
            }
            return node.columns();
        }
        return table.columns().stream().map(SchemaColumn::name).toList();
    }

    private void validateInsertValues(InsertNode node, List<String> targetCols, SchemaTable table) {
        for (int ri = 0; ri < node.values().size(); ri++) {
            var row = node.values().get(ri);
            if (row.size() != targetCols.size()) {
                errors.add("La fila %d de INSERT tiene %d valores, pero se esperaban %d columnas (%s)"
                    .formatted(ri + 1, row.size(), targetCols.size(), String.join(", ", targetCols)));
                continue;
            }
            for (int ci = 0; ci < row.size(); ci++) {
                String colName = targetCols.get(ci);
                var colOpt = table.findColumn(colName);
                if (colOpt.isEmpty()) continue;
                DataType colType = colOpt.get().type();
                DataType valType = resolveLiteralType(row.get(ci));
                if (!colType.isCompatibleWith(valType)) {
                    errors.add("Tipo incompatible en columna '%s': se esperaba %s pero el valor es %s"
                        .formatted(colName, colType, valType));
                }
                if (!colOpt.get().nullable() && isNullLiteral(row.get(ci))) {
                    errors.add("La columna '%s' es NOT NULL pero se intenta insertar NULL"
                        .formatted(colName));
                }
            }
        }
    }

    private void validateInsertSelect(InsertNode node, List<String> targetCols, SchemaTable table) {
        SelectNode select = node.selectQuery();
        SchemaTable sourceTable = resolveTable(select.tableName());
        if (sourceTable == null) return;

        List<String> sourceCols = select.selectAll()
            ? sourceTable.columns().stream().map(SchemaColumn::name).toList()
            : select.columns();

        if (sourceCols.size() != targetCols.size()) {
            errors.add("INSERT SELECT: %d columnas destino vs %d columnas origen"
                .formatted(targetCols.size(), sourceCols.size()));
        }

        for (int i = 0; i < Math.min(sourceCols.size(), targetCols.size()); i++) {
            var srcCol = sourceTable.findColumn(sourceCols.get(i));
            var tgtCol = table.findColumn(targetCols.get(i));
            if (srcCol.isPresent() && tgtCol.isPresent()) {
                if (!tgtCol.get().type().isCompatibleWith(srcCol.get().type())) {
                    errors.add("INSERT SELECT: tipo incompatible entre '%s' (%s) y '%s' (%s)"
                        .formatted(sourceCols.get(i), srcCol.get().type(),
                            targetCols.get(i), tgtCol.get().type()));
                }
            }
        }
    }

    private boolean isNullLiteral(ExpressionNode expr) {
        return expr.exprType() == ExprType.IDENTIFIER
            && expr.value().equalsIgnoreCase("NULL");
    }

    // ── UPDATE ───────────────────────────────────────────────────────

    private void analyzeUpdate(UpdateNode node) {
        SchemaTable table = resolveTable(node.tableName());
        if (table == null) return;

        for (Assignment a : node.assignments()) {
            var colOpt = table.findColumn(a.column());
            if (colOpt.isEmpty()) {
                errors.add("La columna '%s' no existe en la tabla '%s'"
                    .formatted(a.column(), node.tableName()));
            } else {
                DataType colType = colOpt.get().type();
                DataType valType = resolveLiteralType(a.value());
                if (!colType.isCompatibleWith(valType)) {
                    errors.add("Tipo incompatible en SET %s: se esperaba %s, se asignó %s"
                        .formatted(a.column(), colType, valType));
                }
            }
        }

        if (node.hasWhere()) validateCondition(node.whereCondition(), table);
    }

    // ── DELETE ───────────────────────────────────────────────────────

    private void analyzeDelete(DeleteNode node) {
        SchemaTable table = resolveTable(node.tableName());
        if (table == null) return;
        if (node.hasWhere()) validateCondition(node.whereCondition(), table);
    }

    // ── CREATE TABLE ─────────────────────────────────────────────────

    private void analyzeCreateTable(CreateTableNode node) {
        if (schema.findTable(node.tableName()).isPresent() && !node.ifNotExists()) {
            errors.add("La tabla '%s' ya existe en el schema".formatted(node.tableName()));
            return;
        }

        Set<String> colNames = new HashSet<>();
        for (ColumnDef col : node.columns()) {
            if (!colNames.add(col.name()))
                errors.add("Columna duplicada: '%s' en CREATE TABLE".formatted(col.name()));
        }
    }

    // ── DROP TABLE ───────────────────────────────────────────────────

    private void analyzeDropTable(DropTableNode node) {
        if (schema.findTable(node.tableName()).isEmpty() && !node.ifExists()) {
            errors.add("La tabla '%s' no existe, no se puede ejecutar DROP"
                .formatted(node.tableName()));
        }
    }

    // ── ALTER TABLE ──────────────────────────────────────────────────

    private void analyzeAlterTable(AlterTableNode node) {
        SchemaTable table = resolveTable(node.tableName());
        if (table == null) return;

        switch (node.alterType()) {
            case ADD_COLUMN -> {
                if (table.findColumn(node.targetName()).isPresent())
                    errors.add("La columna '%s' ya existe en la tabla '%s'"
                        .formatted(node.targetName(), node.tableName()));
            }
            case DROP_COLUMN -> {
                if (table.findColumn(node.targetName()).isEmpty())
                    errors.add("La columna '%s' no existe en la tabla '%s'"
                        .formatted(node.targetName(), node.tableName()));
            }
            case MODIFY_COLUMN -> {
                if (table.findColumn(node.targetName()).isEmpty())
                    errors.add("La columna '%s' no existe en la tabla '%s'"
                        .formatted(node.targetName(), node.tableName()));
            }
        }
    }

    // ── GRANT / REVOKE ───────────────────────────────────────────────

    private void analyzeGrant(GrantNode node) {
        if (schema.findTable(node.object()).isEmpty()) {
            warnings.add("El objeto '%s' no existe en el schema — GRANT puede fallar en ejecución"
                .formatted(node.object()));
        }
    }

    private void analyzeRevoke(RevokeNode node) {
        if (schema.findTable(node.object()).isEmpty()) {
            warnings.add("El objeto '%s' no existe en el schema — REVOKE puede fallar en ejecución"
                .formatted(node.object()));
        }
    }

    // ── CREATE INDEX ─────────────────────────────────────────────────

    private void analyzeCreateIndex(CreateIndexNode node) {
        SchemaTable table = resolveTable(node.tableName());
        if (table == null) return;

        for (String col : node.columns()) {
            if (table.findColumn(col).isEmpty())
                errors.add("La columna '%s' no existe en la tabla '%s'"
                    .formatted(col, node.tableName()));
        }
    }

    // ── Shared helpers ───────────────────────────────────────────────

    private SchemaTable resolveTable(String tableName) {
        return schema.findTable(tableName).orElseGet(() -> {
            errors.add("La tabla '%s' no existe en el schema".formatted(tableName));
            return null;
        });
    }

    private void validateCondition(Condition cond, SchemaTable table) {
        switch (cond) {
            case SimpleCondition sc -> validateSimpleCondition(sc, table);
            case CompoundCondition cc -> {
                validateCondition(cc.left(), table);
                validateCondition(cc.right(), table);
            }
        }
    }

    private void validateSimpleCondition(SimpleCondition cond, SchemaTable table) {
        DataType left  = resolveType(cond.left(),  table);
        DataType right = resolveType(cond.right(), table);
        if (!left.isCompatibleWith(right))
            errors.add("Tipos incompatibles en WHERE: %s %s %s (%s vs %s)"
                .formatted(cond.left().value(), cond.operator().symbol(),
                    cond.right().value(), left, right));
    }

    private DataType resolveType(ExpressionNode expr, SchemaTable table) {
        return switch (expr.exprType()) {
            case STRING     -> DataType.VARCHAR;
            case NUMBER     -> expr.value().contains(".") ? DataType.FLOAT : DataType.INT;
            case IDENTIFIER -> {
                if (expr.value().equalsIgnoreCase("NULL")) yield DataType.UNKNOWN;
                Optional<SchemaColumn> col = table.findColumn(expr.value());
                yield col.map(SchemaColumn::type).orElse(DataType.UNKNOWN);
            }
        };
    }

    private DataType resolveLiteralType(ExpressionNode expr) {
        return switch (expr.exprType()) {
            case STRING     -> DataType.VARCHAR;
            case NUMBER     -> expr.value().contains(".") ? DataType.FLOAT : DataType.INT;
            case IDENTIFIER -> DataType.UNKNOWN;
        };
    }

    private SemanticResult result() {
        return new SemanticResult(List.copyOf(errors), List.copyOf(warnings));
    }

    public record SemanticResult(List<String> errors, List<String> warnings) {
        public boolean isValid() { return errors.isEmpty(); }
    }
}
