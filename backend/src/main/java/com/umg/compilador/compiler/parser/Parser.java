package com.umg.compilador.compiler.parser;

import com.umg.compilador.compiler.ast.ASTNode.*;
import com.umg.compilador.compiler.token.Token;
import com.umg.compilador.compiler.token.TokenType;

import java.util.*;

public class Parser {

    private static final Set<TokenType> COMPARISON_OPS = Set.of(
        TokenType.EQUAL, TokenType.GREATER, TokenType.LESS,
        TokenType.GREATER_EQUAL, TokenType.LESS_EQUAL, TokenType.NOT_EQUAL,
        TokenType.LIKE
    );

    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty())
            throw new IllegalArgumentException("Lista de tokens vacía");
        this.tokens = List.copyOf(tokens);
        this.pos = 0;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Entry point — dispatches by first token
    // ═══════════════════════════════════════════════════════════════

    public StatementNode parse() {
        StatementNode node = parseStatement();
        if (!check(TokenType.EOF)) {
            Token tok = current();
            error("Se esperaba fin de archivo, se encontró: " + tok.value(), tok);
        }
        return node;
    }

    private StatementNode parseStatement() {
        return switch (current().type()) {
            case SELECT  -> parseSelect();
            case INSERT  -> parseInsert();
            case UPDATE  -> parseUpdate();
            case DELETE  -> parseDelete();
            case CREATE  -> parseCreate();
            case DROP    -> parseDrop();
            case ALTER   -> parseAlter();
            case BEGIN   -> parseBegin();
            case COMMIT  -> parseCommit();
            case ROLLBACK-> parseRollback();
            case GRANT   -> parseGrant();
            case REVOKE  -> parseRevoke();
            default      -> {
                Token tok = current();
                error("Sentencia no soportada: '" + tok.value() + "'", tok);
                yield null;
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  SELECT  ::= SELECT ( '*' | columnList ) FROM IDENTIFIER
    //              [JOIN ...] [WHERE condition] [GROUP BY colList]
    //              [HAVING condition] [ORDER BY col [ASC|DESC] [, ...]]
    //              [LIMIT n [OFFSET m]] [';']
    // ═══════════════════════════════════════════════════════════════

    private SelectNode parseSelect() {
        expect(TokenType.SELECT);

        List<String> columns   = new ArrayList<>();
        boolean      selectAll = false;

        if (check(TokenType.ASTERISK)) {
            selectAll = true; advance();
        } else if (check(TokenType.IDENTIFIER)) {
            columns = parseColumnList();
        } else if (check(TokenType.FROM)) {
        } else {
            error("Se esperaba '*' o lista de columnas después de SELECT", current());
        }

        expect(TokenType.FROM);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de FROM", current());
        String tableName = current().value(); advance();

        // Cláusulas opcionales
        List<JoinClause>    joins   = parseJoins();
        Condition           where   = check(TokenType.WHERE) ? parseWhere() : null;
        List<String>        groupBy = parseGroupBy();
        Condition           having  = check(TokenType.HAVING) ? parseWhere() : null;
        List<OrderByClause> orderBy = parseOrderBy();
        int                 limit   = parseLimit();
        int                 offset  = check(TokenType.OFFSET) ? parseOffset() : 0;

        consumeSemicolon();

        return new SelectNode(columns, selectAll, tableName, where,
                              joins, groupBy, having, orderBy, limit, offset);
    }

    private List<JoinClause> parseJoins() {
        List<JoinClause> joins = new ArrayList<>();
        while (check(TokenType.JOIN) || check(TokenType.INNER) || check(TokenType.LEFT)
               || check(TokenType.RIGHT)) {
            JoinType type = JoinType.INNER;
            if (check(TokenType.INNER)) { type = JoinType.INNER; advance(); }
            else if (check(TokenType.LEFT)) { type = JoinType.LEFT; advance(); }
            else if (check(TokenType.RIGHT)) { type = JoinType.RIGHT; advance(); }
            expect(TokenType.JOIN);

            if (!check(TokenType.IDENTIFIER))
                error("Se esperaba nombre de tabla después de JOIN", current());
            String joinTable = current().value(); advance();

            Condition condition = null;
            if (check(TokenType.ON)) {
                advance();
                ExpressionNode left = parseExpression();
                if (check(TokenType.DOT)) {
                    advance();
                    if (!check(TokenType.IDENTIFIER))
                        error("Se esperaba identificador después de '.'", current());
                    left = new ExpressionNode(ExprType.IDENTIFIER, left.value() + "." + current().value());
                    advance();
                }
                if (!COMPARISON_OPS.contains(current().type()))
                    error("Se esperaba operador de comparación en condición JOIN", current());
                CompOperator op = tokenToCompOp(current().type()); advance();
                ExpressionNode right = parseExpression();
                if (check(TokenType.DOT)) {
                    advance();
                    if (!check(TokenType.IDENTIFIER))
                        error("Se esperaba identificador después de '.'", current());
                    right = new ExpressionNode(ExprType.IDENTIFIER, right.value() + "." + current().value());
                    advance();
                }
                condition = new SimpleCondition(left, op, right);
            }
            joins.add(new JoinClause(type, joinTable, condition));
        }
        return joins;
    }

    private List<String> parseGroupBy() {
        if (!check(TokenType.GROUP)) return List.of();
        advance(); // GROUP
        expect(TokenType.BY);
        return parseColumnList();
    }

    private List<OrderByClause> parseOrderBy() {
        if (!check(TokenType.ORDER)) return List.of();
        advance(); // ORDER
        expect(TokenType.BY);
        List<OrderByClause> list = new ArrayList<>();
        list.add(parseSingleOrderBy());
        while (check(TokenType.COMMA)) {
            advance();
            list.add(parseSingleOrderBy());
        }
        return list;
    }

    private OrderByClause parseSingleOrderBy() {
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de columna en ORDER BY", current());
        String col = current().value(); advance();
        SortOrder order = SortOrder.ASC;
        if (check(TokenType.ASC)) { order = SortOrder.ASC; advance(); }
        else if (check(TokenType.DESC)) { order = SortOrder.DESC; advance(); }
        return new OrderByClause(col, order);
    }

    private int parseLimit() {
        if (!check(TokenType.LIMIT)) return 0;
        advance();
        if (!check(TokenType.NUMBER))
            error("Se esperaba número después de LIMIT", current());
        int val = Integer.parseInt(current().value()); advance();
        return val;
    }

    private int parseOffset() {
        advance(); // consume OFFSET
        if (!check(TokenType.NUMBER))
            error("Se esperaba número después de OFFSET", current());
        int val = Integer.parseInt(current().value()); advance();
        return val;
    }

    private String parseIdentifierWithDot() {
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba identificador", current());
        StringBuilder sb = new StringBuilder(current().value()); advance();
        if (check(TokenType.DOT)) {
            sb.append('.'); advance();
            if (!check(TokenType.IDENTIFIER))
                error("Se esperaba identificador después de '.'", current());
            sb.append(current().value()); advance();
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  INSERT
    //   i1 ::= INSERT INTO IDENTIFIER [ '(' colList ')' ]
    //           VALUES '(' exprList ')' [ ',' '(' exprList ')' ] [';']
    //   i2 ::= INSERT INTO IDENTIFIER [ '(' colList ')' ] selectQuery [';']
    // ═══════════════════════════════════════════════════════════════

    private InsertNode parseInsert() {
        expect(TokenType.INSERT);
        expect(TokenType.INTO);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de INTO", current());
        String tableName = current().value(); advance();

        List<String> columns = new ArrayList<>();
        if (check(TokenType.LEFT_PAREN)) {
            advance();
            columns = parseColumnList();
            expect(TokenType.RIGHT_PAREN);
        }

        // VALUES or SELECT?
        if (check(TokenType.VALUES)) {
            advance();
            List<List<ExpressionNode>> rows = new ArrayList<>();
            do {
                expect(TokenType.LEFT_PAREN);
                rows.add(parseExpressionList());
                expect(TokenType.RIGHT_PAREN);
            } while (check(TokenType.COMMA) && advance());
            consumeSemicolon();
            return new InsertNode(tableName, columns, rows, null);
        }

        // INSERT INTO ... SELECT
        SelectNode select = parseSelect();
        return new InsertNode(tableName, columns, null, select);
    }

    // ═══════════════════════════════════════════════════════════════
    //  UPDATE  ::= UPDATE IDENTIFIER SET assignment [',' assignment]
    //              [WHERE condition] [';']
    // ═══════════════════════════════════════════════════════════════

    private UpdateNode parseUpdate() {
        expect(TokenType.UPDATE);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de UPDATE", current());
        String tableName = current().value(); advance();

        expect(TokenType.SET);

        List<Assignment> assignments = new ArrayList<>();
        do {
            if (!check(TokenType.IDENTIFIER))
                error("Se esperaba nombre de columna en SET", current());
            String col = current().value(); advance();
            expect(TokenType.EQUAL);
            assignments.add(new Assignment(col, parseExpression()));
        } while (check(TokenType.COMMA) && advance());

        Condition where = null;
        if (check(TokenType.WHERE)) where = parseWhere();
        consumeSemicolon();

        return new UpdateNode(tableName, assignments, where);
    }

    // ═══════════════════════════════════════════════════════════════
    //  DELETE  ::= DELETE FROM IDENTIFIER [WHERE condition] [';']
    // ═══════════════════════════════════════════════════════════════

    private DeleteNode parseDelete() {
        expect(TokenType.DELETE);
        expect(TokenType.FROM);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de DELETE FROM", current());
        String tableName = current().value(); advance();

        Condition where = null;
        if (check(TokenType.WHERE)) where = parseWhere();
        consumeSemicolon();

        return new DeleteNode(tableName, where);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CREATE
    //   ct ::= CREATE TABLE [IF NOT EXISTS] IDENTIFIER
    //           '(' columnDef [',' columnDef]* ')' [';']
    //   ci ::= CREATE [UNIQUE] INDEX IDENTIFIER ON IDENTIFIER
    //           '(' IDENTIFIER [',' IDENTIFIER]* ')' [';']
    // ═══════════════════════════════════════════════════════════════

    private StatementNode parseCreate() {
        expect(TokenType.CREATE);

        if (check(TokenType.TABLE)) return parseCreateTable();
        if (check(TokenType.INDEX)) { advance(); return parseCreateIndex(); }
        if (check(TokenType.UNIQUE)) {
            advance(); // UNIQUE
            expect(TokenType.INDEX);
            return parseCreateIndex(true);
        }

        error("Se esperaba TABLE, INDEX o UNIQUE INDEX después de CREATE", current());
        return null;
    }

    private CreateTableNode parseCreateTable() {
        expect(TokenType.TABLE);

        boolean ifNotExists = false;
        if (check(TokenType.IF)) {
            advance(); expect(TokenType.NOT); expect(TokenType.EXISTS);
            ifNotExists = true;
        }

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de CREATE TABLE", current());
        String tableName = current().value(); advance();

        List<ColumnDef> columns = new ArrayList<>();
        expect(TokenType.LEFT_PAREN);
        do {
            if (!check(TokenType.IDENTIFIER))
                error("Se esperaba nombre de columna", current());
            String colName = current().value(); advance();

            String colType = parseTypeWithParams();

            List<String> constraints = new ArrayList<>();
            while (check(TokenType.IDENTIFIER) || check(TokenType.NOT) || check(TokenType.NULL)
                   || check(TokenType.PRIMARY) || check(TokenType.KEY) || check(TokenType.UNIQUE)
                   || check(TokenType.DEFAULT) || check(TokenType.AUTO_INCREMENT)
                   || check(TokenType.REFERENCES) || check(TokenType.CHECK)
                   || check(TokenType.FOREIGN) || check(TokenType.CONSTRAINT)) {
                constraints.add(current().value()); advance();
            }

            columns.add(new ColumnDef(colName, colType, constraints));
        } while (check(TokenType.COMMA) && advance());

        expect(TokenType.RIGHT_PAREN);
        consumeSemicolon();

        return new CreateTableNode(tableName, columns, ifNotExists);
    }

    private CreateIndexNode parseCreateIndex() {
        return parseCreateIndex(false);
    }

    private CreateIndexNode parseCreateIndex(boolean unique) {
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre del índice", current());
        String indexName = current().value(); advance();

        expect(TokenType.ON);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla para el índice", current());
        String tableName = current().value(); advance();

        expect(TokenType.LEFT_PAREN);
        List<String> columns = new ArrayList<>();
        do {
            if (!check(TokenType.IDENTIFIER))
                error("Se esperaba nombre de columna en índice", current());
            columns.add(current().value()); advance();
        } while (check(TokenType.COMMA) && advance());
        expect(TokenType.RIGHT_PAREN);
        consumeSemicolon();

        return new CreateIndexNode(indexName, tableName, columns, unique);
    }

    // ═══════════════════════════════════════════════════════════════
    //  DROP TABLE  ::= DROP TABLE [IF EXISTS] IDENTIFIER [';']
    // ═══════════════════════════════════════════════════════════════

    private DropTableNode parseDrop() {
        expect(TokenType.DROP);
        expect(TokenType.TABLE);

        boolean ifExists = false;
        if (check(TokenType.IF)) {
            advance(); expect(TokenType.EXISTS);
            ifExists = true;
        }

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de DROP TABLE", current());
        String tableName = current().value(); advance();
        consumeSemicolon();

        return new DropTableNode(tableName, ifExists);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ALTER TABLE  ::= ALTER TABLE IDENTIFIER
    //                    ( ADD [COLUMN] IDENTIFIER type [constraints]
    //                    | DROP [COLUMN] IDENTIFIER
    //                    | MODIFY [COLUMN] IDENTIFIER type ) [';']
    // ═══════════════════════════════════════════════════════════════

    private AlterTableNode parseAlter() {
        expect(TokenType.ALTER);
        expect(TokenType.TABLE);

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de tabla después de ALTER TABLE", current());
        String tableName = current().value(); advance();

        AlterType alterType;
        if (check(TokenType.ADD)) {
            alterType = AlterType.ADD_COLUMN;
        } else if (check(TokenType.DROP)) {
            alterType = AlterType.DROP_COLUMN;
        } else if (check(TokenType.MODIFY)) {
            // MODIFY may not be a base token — treat as identifier
            if (current().value().equalsIgnoreCase("MODIFY")) {
                alterType = AlterType.MODIFY_COLUMN;
                advance();
            } else {
                error("Se esperaba ADD, DROP o MODIFY después de ALTER TABLE", current());
                return null;
            }
        } else {
            error("Se esperaba ADD, DROP o MODIFY después de ALTER TABLE", current());
            return null;
        }

        advance(); // consume ADD/DROP/MODIFY

        // Skip optional COLUMN keyword
        if (check(TokenType.COLUMN)) advance();

        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de columna", current());
        String targetName = current().value(); advance();

        String dataType = null;
        List<String> constraints = new ArrayList<>();
        if (alterType == AlterType.ADD_COLUMN || alterType == AlterType.MODIFY_COLUMN) {
            dataType = parseTypeWithParams();

            while (check(TokenType.IDENTIFIER) || check(TokenType.NOT) || check(TokenType.NULL)
                   || check(TokenType.DEFAULT) || check(TokenType.UNIQUE)
                   || check(TokenType.PRIMARY) || check(TokenType.KEY)
                   || check(TokenType.REFERENCES) || check(TokenType.CHECK)) {
                constraints.add(current().value()); advance();
            }
        }

        consumeSemicolon();
        return new AlterTableNode(tableName, alterType, targetName, dataType, constraints);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Transactions  ::= BEGIN | COMMIT | ROLLBACK [';']
    // ═══════════════════════════════════════════════════════════════

    private TransactionNode parseBegin() {
        expect(TokenType.BEGIN);
        if (check(TokenType.WORK) || check(TokenType.TRANSACTION)) advance();
        consumeSemicolon();
        return new TransactionNode(TxnType.BEGIN);
    }

    private TransactionNode parseCommit() {
        expect(TokenType.COMMIT);
        if (check(TokenType.WORK)) advance();
        consumeSemicolon();
        return new TransactionNode(TxnType.COMMIT);
    }

    private TransactionNode parseRollback() {
        expect(TokenType.ROLLBACK);
        if (check(TokenType.WORK)) advance();
        consumeSemicolon();
        return new TransactionNode(TxnType.ROLLBACK);
    }

    // ═══════════════════════════════════════════════════════════════
    //  GRANT  ::= GRANT priv [',' priv] ON object TO user [';']
    //  REVOKE ::= REVOKE priv [',' priv] ON object FROM user [';']
    // ═══════════════════════════════════════════════════════════════

    private GrantNode parseGrant() {
        expect(TokenType.GRANT);
        List<String> privileges = parsePrivilegeList();

        expect(TokenType.ON);
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba objeto (tabla) para GRANT", current());
        String object = current().value(); advance();

        expect(TokenType.TO);
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de usuario para GRANT", current());
        String user = current().value(); advance();

        consumeSemicolon();
        return new GrantNode(privileges, object, user);
    }

    private RevokeNode parseRevoke() {
        expect(TokenType.REVOKE);
        List<String> privileges = parsePrivilegeList();

        expect(TokenType.ON);
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba objeto (tabla) para REVOKE", current());
        String object = current().value(); advance();

        expect(TokenType.FROM);
        if (!check(TokenType.IDENTIFIER))
            error("Se esperaba nombre de usuario para REVOKE", current());
        String user = current().value(); advance();

        consumeSemicolon();
        return new RevokeNode(privileges, object, user);
    }

    private List<String> parsePrivilegeList() {
        List<String> privs = new ArrayList<>();
        if (check(TokenType.EOF) || check(TokenType.INVALID))
            error("Se esperaba lista de privilegios (SELECT, INSERT, ALL, ...)", current());
        privs.add(current().value()); advance();

        while (check(TokenType.COMMA)) {
            advance();
            if (check(TokenType.EOF) || check(TokenType.ON) || check(TokenType.TO) || check(TokenType.FROM)
                || check(TokenType.INVALID))
                error("Se esperaba privilegio después de ','", current());
            privs.add(current().value()); advance();
        }
        return privs;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers — column lists, expressions, conditions
    // ═══════════════════════════════════════════════════════════════

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

    private List<ExpressionNode> parseExpressionList() {
        List<ExpressionNode> exprs = new ArrayList<>();
        exprs.add(parseExpression());
        while (check(TokenType.COMMA)) {
            advance();
            exprs.add(parseExpression());
        }
        return exprs;
    }

    private Condition parseWhere() {
        expect(TokenType.WHERE);
        return parseCondition();
    }

    private Condition parseCondition() {
        Condition left = parseSimpleCondition();
        while (check(TokenType.AND) || check(TokenType.OR)) {
            LogOp op = check(TokenType.AND) ? LogOp.AND : LogOp.OR;
            advance();
            Condition right = parseSimpleCondition();
            left = new CompoundCondition(left, op, right);
        }
        return left;
    }

    private SimpleCondition parseSimpleCondition() {
        ExpressionNode left = parseExpression();
        if (!COMPARISON_OPS.contains(current().type()))
            error("Se esperaba operador de comparación, se encontró: " + current().value(), current());
        CompOperator op = tokenToCompOp(current().type()); advance();
        ExpressionNode right = parseExpression();
        return new SimpleCondition(left, op, right);
    }

    private String parseTypeWithParams() {
        if (!check(TokenType.IDENTIFIER) && !check(TokenType.NUMBER) && !check(TokenType.STRING))
            error("Se esperaba tipo de dato", current());
        String type = current().value(); advance();
        // Consume optional type parameter: VARCHAR(100), DECIMAL(10,2)
        if (check(TokenType.LEFT_PAREN)) {
            advance(); // consume '('
            StringBuilder param = new StringBuilder();
            param.append('(');
            while (!check(TokenType.RIGHT_PAREN) && !check(TokenType.EOF)) {
                param.append(current().value());
                advance();
            }
            if (check(TokenType.RIGHT_PAREN)) {
                param.append(')');
                advance();
            }
            type += param.toString();
        }
        return type;
    }

    private ExpressionNode parseExpression() {
        Token tok = current();
        return switch (tok.type()) {
            case IDENTIFIER -> { advance(); yield new ExpressionNode(ExprType.IDENTIFIER, tok.value()); }
            case NUMBER     -> { advance(); yield new ExpressionNode(ExprType.NUMBER,     tok.value()); }
            case STRING     -> { advance(); yield new ExpressionNode(ExprType.STRING,     tok.value()); }
            case NULL       -> { advance(); yield new ExpressionNode(ExprType.IDENTIFIER, "NULL"); }
            default -> { error("Se esperaba expresión, se encontró: " + tok.value(), tok); yield null; }
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  Token helpers
    // ═══════════════════════════════════════════════════════════════

    private Token current() { return pos < tokens.size() ? tokens.get(pos) : tokens.get(tokens.size() - 1); }
    private boolean check(TokenType t) { return current().type() == t; }
    private boolean advance() { if (pos < tokens.size() - 1) pos++; return true; }

    private void expect(TokenType t) {
        if (!check(t))
            error("Se esperaba %s pero se encontró '%s'".formatted(t.display(), current().value()), current());
        advance();
    }

    private void consumeSemicolon() {
        if (check(TokenType.SEMICOLON)) advance();
    }

    private void error(String msg, Token tok) {
        throw new ParseException(msg, tok.line(), tok.column());
    }

    private CompOperator tokenToCompOp(TokenType t) {
        return switch (t) {
            case EQUAL         -> CompOperator.EQUAL;
            case GREATER       -> CompOperator.GREATER;
            case LESS          -> CompOperator.LESS;
            case GREATER_EQUAL -> CompOperator.GREATER_EQUAL;
            case LESS_EQUAL    -> CompOperator.LESS_EQUAL;
            case NOT_EQUAL     -> CompOperator.NOT_EQUAL;
            case LIKE          -> CompOperator.LIKE;
            default -> throw new IllegalStateException("No es operador: " + t);
        };
    }
}
