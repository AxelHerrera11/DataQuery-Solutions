package com.umg.compilador.compiler.ast;

import java.util.List;

public sealed interface ASTNode permits ASTNode.ExpressionNode, ASTNode.Condition,
    ASTNode.StatementNode {

    void print(int indent);

    // ═══════════════════════════════════════════════════════════════
    //  Expression types
    // ═══════════════════════════════════════════════════════════════

    enum ExprType { IDENTIFIER, NUMBER, STRING }

    record ExpressionNode(ExprType exprType, String value) implements ASTNode {
        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            String label = switch (exprType) {
                case IDENTIFIER -> "Identificador";
                case NUMBER     -> "Número";
                case STRING     -> "Cadena";
            };
            System.out.println(s + label + ": " + value);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Condition types  (AND / OR tree)
    // ═══════════════════════════════════════════════════════════════

    enum CompOperator {
        EQUAL("="), GREATER(">"), LESS("<"),
        GREATER_EQUAL(">="), LESS_EQUAL("<="), NOT_EQUAL("!="), LIKE("LIKE");
        private final String symbol;
        CompOperator(String s) { this.symbol = s; }
        public String symbol() { return symbol; }
    }

    enum LogOp { AND, OR }

    sealed interface Condition extends ASTNode permits SimpleCondition, CompoundCondition {}

    record SimpleCondition(ExpressionNode left, CompOperator operator,
                           ExpressionNode right) implements Condition {
        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "Condición:");
            System.out.println(s + "  Izquierda:"); left.print(indent + 4);
            System.out.println(s + "  Operador: " + operator.symbol());
            System.out.println(s + "  Derecha:");  right.print(indent + 4);
        }
    }

    record CompoundCondition(Condition left, LogOp operator,
                             Condition right) implements Condition {
        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "Condición Compuesta (" + operator + "):");
            left.print(indent + 2);
            right.print(indent + 2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Statement types  (base for all SQL statements)
    // ═══════════════════════════════════════════════════════════════

    sealed interface StatementNode extends ASTNode permits SelectNode, InsertNode, UpdateNode,
        DeleteNode, CreateTableNode, DropTableNode, AlterTableNode,
        TransactionNode, GrantNode, RevokeNode, CreateIndexNode {}

    // ── SELECT ──────────────────────────────────────────────────────

    enum JoinType { INNER, LEFT, RIGHT, FULL, CROSS }

    record JoinClause(JoinType type, String tableName,
                      Condition condition) {}

    enum SortOrder { ASC, DESC }

    record OrderByClause(String column, SortOrder order) {}

    record SelectNode(List<String> columns, boolean selectAll, String tableName,
                      Condition whereCondition, List<JoinClause> joins,
                      List<String> groupBy, Condition having,
                      List<OrderByClause> orderBy,
                      int limit, int offset) implements StatementNode {
        public SelectNode {
            columns   = List.copyOf(columns);
            joins     = joins != null ? List.copyOf(joins) : List.of();
            groupBy   = groupBy != null ? List.copyOf(groupBy) : List.of();
            orderBy   = orderBy != null ? List.copyOf(orderBy) : List.of();
        }

        public boolean hasWhere()   { return whereCondition != null; }
        public boolean hasJoins()   { return !joins.isEmpty(); }
        public boolean hasGroupBy() { return !groupBy.isEmpty(); }
        public boolean hasHaving()  { return having != null; }
        public boolean hasOrderBy() { return !orderBy.isEmpty(); }
        public boolean hasLimit()   { return limit > 0; }
        public boolean hasOffset()  { return offset > 0; }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "SELECT Query:");
            System.out.print(s + "  Columnas: ");
            System.out.println(selectAll ? "*" : String.join(", ", columns));
            System.out.println(s + "  FROM: " + tableName);
            for (var j : joins)
                System.out.println(s + "  " + j.type() + " JOIN " + j.tableName());
            if (whereCondition != null) {
                System.out.println(s + "  WHERE:");
                whereCondition.print(indent + 4);
            }
            if (!groupBy.isEmpty())
                System.out.println(s + "  GROUP BY: " + String.join(", ", groupBy));
            if (having != null) {
                System.out.println(s + "  HAVING:");
                having.print(indent + 4);
            }
            if (!orderBy.isEmpty()) {
                System.out.print(s + "  ORDER BY: ");
                for (int i = 0; i < orderBy.size(); i++) {
                    if (i > 0) System.out.print(", ");
                    System.out.print(orderBy.get(i).column() + " " + orderBy.get(i).order());
                }
                System.out.println();
            }
            if (limit > 0) System.out.println(s + "  LIMIT: " + limit);
            if (offset > 0) System.out.println(s + "  OFFSET: " + offset);
        }
    }

    // ── INSERT ──────────────────────────────────────────────────────

    record InsertNode(String tableName, List<String> columns,
                      List<List<ExpressionNode>> values,
                      SelectNode selectQuery) implements StatementNode {
        public InsertNode {
            columns = columns != null ? List.copyOf(columns) : List.of();
        }
        public boolean isValuesInsert() { return values != null; }
        public boolean isSelectInsert() { return selectQuery != null; }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "INSERT INTO " + tableName);
            if (!columns.isEmpty())
                System.out.println(s + "  Columnas: " + String.join(", ", columns));
            if (values != null) {
                System.out.println(s + "  VALUES:");
                for (var row : values) {
                    System.out.print(s + "    (");
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) System.out.print(", ");
                        System.out.print(row.get(i).value());
                    }
                    System.out.println(")");
                }
            }
            if (selectQuery != null) selectQuery.print(indent + 2);
        }
    }

    // ── UPDATE ──────────────────────────────────────────────────────

    record Assignment(String column, ExpressionNode value) {}

    record UpdateNode(String tableName, List<Assignment> assignments,
                      Condition whereCondition) implements StatementNode {
        public UpdateNode {
            assignments = List.copyOf(assignments);
        }
        public boolean hasWhere() { return whereCondition != null; }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "UPDATE " + tableName);
            System.out.println(s + "  SET:");
            for (Assignment a : assignments)
                System.out.println(s + "    " + a.column() + " = " + a.value().value());
            if (whereCondition != null) {
                System.out.println(s + "  WHERE:");
                whereCondition.print(indent + 4);
            }
        }
    }

    // ── DELETE ──────────────────────────────────────────────────────

    record DeleteNode(String tableName, Condition whereCondition) implements StatementNode {
        public boolean hasWhere() { return whereCondition != null; }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "DELETE FROM " + tableName);
            if (whereCondition != null) {
                System.out.println(s + "  WHERE:");
                whereCondition.print(indent + 4);
            }
        }
    }

    // ── CREATE TABLE ────────────────────────────────────────────────

    record ColumnDef(String name, String type, List<String> constraints) {
        public ColumnDef {
            constraints = constraints != null ? List.copyOf(constraints) : List.of();
        }
        public void print(int indent) {
            System.out.println(" ".repeat(indent) + name + " " + type
                + (constraints.isEmpty() ? "" : " " + String.join(" ", constraints)));
        }
    }

    record CreateTableNode(String tableName, List<ColumnDef> columns,
                           boolean ifNotExists) implements StatementNode {
        public CreateTableNode {
            columns = List.copyOf(columns);
        }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "CREATE TABLE "
                + (ifNotExists ? "IF NOT EXISTS " : "") + tableName);
            for (ColumnDef col : columns) col.print(indent + 2);
        }
    }

    // ── DROP TABLE ──────────────────────────────────────────────────

    record DropTableNode(String tableName, boolean ifExists) implements StatementNode {
        @Override
        public void print(int indent) {
            System.out.println(" ".repeat(indent) + "DROP TABLE "
                + (ifExists ? "IF EXISTS " : "") + tableName);
        }
    }

    // ── ALTER TABLE ─────────────────────────────────────────────────

    enum AlterType { ADD_COLUMN, DROP_COLUMN, MODIFY_COLUMN }

    record AlterTableNode(String tableName, AlterType alterType,
                          String targetName, String dataType,
                          List<String> constraints) implements StatementNode {
        public AlterTableNode {
            constraints = constraints != null ? List.copyOf(constraints) : List.of();
        }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "ALTER TABLE " + tableName + " " + alterType + " " + targetName);
            if (dataType != null) System.out.println(s + "  Tipo: " + dataType);
        }
    }

    // ── Transactions ────────────────────────────────────────────────

    enum TxnType { BEGIN, COMMIT, ROLLBACK }

    record TransactionNode(TxnType txnType) implements StatementNode {
        @Override
        public void print(int indent) {
            System.out.println(" ".repeat(indent) + txnType);
        }
    }

    // ── GRANT / REVOKE ──────────────────────────────────────────────

    record GrantNode(List<String> privileges, String object,
                     String user) implements StatementNode {
        public GrantNode {
            privileges = List.copyOf(privileges);
        }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "GRANT " + String.join(", ", privileges)
                + " ON " + object + " TO " + user);
        }
    }

    record RevokeNode(List<String> privileges, String object,
                      String user) implements StatementNode {
        public RevokeNode {
            privileges = List.copyOf(privileges);
        }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "REVOKE " + String.join(", ", privileges)
                + " ON " + object + " FROM " + user);
        }
    }

    // ── CREATE INDEX ────────────────────────────────────────────────

    record CreateIndexNode(String indexName, String tableName,
                           List<String> columns, boolean unique) implements StatementNode {
        public CreateIndexNode {
            columns = List.copyOf(columns);
        }

        @Override
        public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "CREATE " + (unique ? "UNIQUE " : "")
                + "INDEX " + indexName + " ON " + tableName
                + " (" + String.join(", ", columns) + ")");
        }
    }
}
