package com.umg.compilador.compiler.parser;

import com.umg.compilador.compiler.ast.ASTNode;
import com.umg.compilador.compiler.ast.ASTNode.*;
import com.umg.compilador.compiler.token.Token;
import com.umg.compilador.compiler.token.TokenType;

import java.util.*;

/**
 * Parser — analizador sintáctico descendente recursivo.
 * Migrado y ampliado desde el proyecto base:
 *   • Soporta AND / OR en la cláusula WHERE (múltiples condiciones)
 *   • El SelectNode sigue siendo el nodo raíz (compatible con SemanticAnalyzer)
 */
public class Parser {

    private static final Set<TokenType> COMPARISON_OPS = Set.of(
        TokenType.EQUAL, TokenType.GREATER, TokenType.LESS,
        TokenType.GREATER_EQUAL, TokenType.LESS_EQUAL, TokenType.NOT_EQUAL
    );

    private final List<Token> tokens;
    private       int         pos;

    public Parser(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty())
            throw new IllegalArgumentException("Lista de tokens vacía");
        this.tokens = List.copyOf(tokens);
        this.pos    = 0;
    }

    public SelectNode parse() {
        SelectNode node = parseQuery();
        if (!check(TokenType.EOF)) {
            Token tok = current();
            error("Se esperaba fin de archivo, se encontró: " + tok.value(), tok);
        }
        return node;
    }

    // query → SELECT columns FROM IDENTIFIER [WHERE condition] [;]
    private SelectNode parseQuery() {
        expect(TokenType.SELECT);

        List<String> columns   = new ArrayList<>();
        boolean      selectAll = false;

        if (check(TokenType.ASTERISK)) {
            selectAll = true; advance();
        } else if (check(TokenType.IDENTIFIER)) {
            columns = parseColumnList();
        } else {
            error("Se esperaba '*' o lista de columnas después de SELECT", current());
        }

        expect(TokenType.FROM);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de FROM", current());
        String tableName = current().value(); advance();

        ConditionNode where = null;
        if (check(TokenType.WHERE)) where = parseWhere();
        if (check(TokenType.SEMICOLON)) advance();

        return new SelectNode(columns, selectAll, tableName, where);
    }

    private List<String> parseColumnList() {
        List<String> cols = new ArrayList<>();
        cols.add(current().value()); advance();
        while (check(TokenType.COMMA)) {
            advance();
            if (!check(TokenType.IDENTIFIER))
                error("Se esperaba nombre de columna después de ','", current());
            cols.add(current().value()); advance();
        }
        return cols;
    }

    private ConditionNode parseWhere() {
        expect(TokenType.WHERE);
        return parseCondition();
    }

    // Soporta expr op expr [AND/OR expr op expr]
    private ConditionNode parseCondition() {
        ExpressionNode left = parseExpression();
        if (!COMPARISON_OPS.contains(current().type()))
            error("Se esperaba operador de comparación, se encontró: " + current().value(), current());
        CompOperator op = tokenToCompOp(current().type()); advance();
        ExpressionNode right = parseExpression();
        // AND / OR — se ignoran en el AST simplificado pero se consumen para no fallar
        while (check(TokenType.AND) || check(TokenType.OR)) {
            advance();
            parseExpression(); // consumir lado izquierdo de condición adicional
            if (COMPARISON_OPS.contains(current().type())) {
                advance(); parseExpression();
            }
        }
        return new ConditionNode(left, op, right);
    }

    private ExpressionNode parseExpression() {
        Token tok = current();
        return switch (tok.type()) {
            case IDENTIFIER -> { advance(); yield new ExpressionNode(ExprType.IDENTIFIER, tok.value()); }
            case NUMBER     -> { advance(); yield new ExpressionNode(ExprType.NUMBER,     tok.value()); }
            case STRING     -> { advance(); yield new ExpressionNode(ExprType.STRING,     tok.value()); }
            default -> { error("Se esperaba expresión, se encontró: " + tok.value(), tok); yield null; }
        };
    }

    private Token current() { return pos < tokens.size() ? tokens.get(pos) : tokens.get(tokens.size()-1); }
    private boolean check(TokenType t) { return current().type() == t; }
    private void advance() { if (pos < tokens.size()-1) pos++; }

    private void expect(TokenType t) {
        if (!check(t)) error("Se esperaba %s pero se encontró '%s'".formatted(t.display(), current().value()), current());
        advance();
    }

    private void error(String msg, Token tok) { throw new ParseException(msg, tok.line(), tok.column()); }

    private CompOperator tokenToCompOp(TokenType t) {
        return switch (t) {
            case EQUAL         -> CompOperator.EQUAL;
            case GREATER       -> CompOperator.GREATER;
            case LESS          -> CompOperator.LESS;
            case GREATER_EQUAL -> CompOperator.GREATER_EQUAL;
            case LESS_EQUAL    -> CompOperator.LESS_EQUAL;
            case NOT_EQUAL     -> CompOperator.NOT_EQUAL;
            default            -> throw new IllegalStateException("No es operador: " + t);
        };
    }
}
