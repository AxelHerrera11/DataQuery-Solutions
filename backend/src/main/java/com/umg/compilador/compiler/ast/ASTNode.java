package com.umg.compilador.compiler.ast;

import java.util.List;

/**
 * ASTNode — árbol de sintaxis abstracta.
 * Migrado sin cambios desde el proyecto base.
 * Usa sealed interface para pattern matching exhaustivo (Java 21).
 */
public sealed interface ASTNode permits ASTNode.SelectNode, ASTNode.ConditionNode, ASTNode.ExpressionNode {

    void print(int indent);

    enum ExprType  { IDENTIFIER, NUMBER, STRING }

    enum CompOperator {
        EQUAL("="), GREATER(">"), LESS("<"),
        GREATER_EQUAL(">="), LESS_EQUAL("<="), NOT_EQUAL("!=");
        private final String symbol;
        CompOperator(String symbol) { this.symbol = symbol; }
        public String symbol() { return symbol; }
    }

    record ExpressionNode(ExprType exprType, String value) implements ASTNode {
        @Override public void print(int indent) {
            String s = " ".repeat(indent);
            String label = switch (exprType) {
                case IDENTIFIER -> "Identificador";
                case NUMBER     -> "Número";
                case STRING     -> "Cadena";
            };
            System.out.println(s + label + ": " + value);
        }
    }

    record ConditionNode(ExpressionNode left, CompOperator operator, ExpressionNode right) implements ASTNode {
        @Override public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "Condición:");
            System.out.println(s + "  Izquierda:"); left.print(indent + 4);
            System.out.println(s + "  Operador: " + operator.symbol());
            System.out.println(s + "  Derecha:");  right.print(indent + 4);
        }
    }

    final class SelectNode implements ASTNode {
        private final List<String>  columns;
        private final boolean       selectAll;
        private final String        tableName;
        private final ConditionNode whereCondition;

        public SelectNode(List<String> columns, boolean selectAll,
                          String tableName, ConditionNode whereCondition) {
            this.columns        = List.copyOf(columns);
            this.selectAll      = selectAll;
            this.tableName      = tableName;
            this.whereCondition = whereCondition;
        }

        public List<String>   columns()        { return columns; }
        public boolean        isSelectAll()    { return selectAll; }
        public String         tableName()      { return tableName; }
        public ConditionNode  whereCondition() { return whereCondition; }
        public boolean        hasWhere()       { return whereCondition != null; }

        @Override public void print(int indent) {
            String s = " ".repeat(indent);
            System.out.println(s + "SELECT Query:");
            System.out.print(s + "  Columnas: ");
            System.out.println(selectAll ? "*" : String.join(", ", columns));
            System.out.println(s + "  FROM: " + tableName);
            if (whereCondition != null) {
                System.out.println(s + "  WHERE:");
                whereCondition.print(indent + 4);
            }
        }
    }
}
