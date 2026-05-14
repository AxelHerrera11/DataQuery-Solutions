package com.umg.compilador.compiler.token;

/**
 * TokenType — tipos de tokens del compilador SQL.
 * Migrado sin cambios desde el proyecto base.
 */
public enum TokenType {
    SELECT, FROM, WHERE, INSERT, INTO, VALUES, UPDATE, SET,
    DELETE, CREATE, TABLE, DROP, ALTER, ADD, COLUMN,
    AND, OR, NOT, IN, LIKE, BETWEEN, IS, NULL,
    ORDER, BY, GROUP, HAVING, LIMIT, OFFSET,
    INNER, LEFT, RIGHT, OUTER, JOIN, ON, AS,
    DISTINCT, COUNT, SUM, AVG, MIN, MAX,

    IDENTIFIER, NUMBER, STRING,

    EQUAL, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL, NOT_EQUAL,

    ASTERISK, COMMA, SEMICOLON, LEFT_PAREN, RIGHT_PAREN, DOT,

    EOF, INVALID;

    public String display() {
        return switch (this) {
            case SELECT        -> "SELECT";
            case FROM          -> "FROM";
            case WHERE         -> "WHERE";
            case INSERT        -> "INSERT";
            case INTO          -> "INTO";
            case VALUES        -> "VALUES";
            case UPDATE        -> "UPDATE";
            case SET           -> "SET";
            case DELETE        -> "DELETE";
            case CREATE        -> "CREATE";
            case TABLE         -> "TABLE";
            case DROP          -> "DROP";
            case ALTER         -> "ALTER";
            case AND           -> "AND";
            case OR            -> "OR";
            case NOT           -> "NOT";
            case LIMIT         -> "LIMIT";
            case OFFSET        -> "OFFSET";
            case JOIN          -> "JOIN";
            case ON            -> "ON";
            case AS            -> "AS";
            case IDENTIFIER    -> "identificador";
            case NUMBER        -> "número";
            case STRING        -> "cadena de texto";
            case EQUAL         -> "'='";
            case GREATER       -> "'>'";
            case LESS          -> "'<'";
            case GREATER_EQUAL -> "'>='";
            case LESS_EQUAL    -> "'<='";
            case NOT_EQUAL     -> "'!='";
            case ASTERISK      -> "'*'";
            case COMMA         -> "','";
            case SEMICOLON     -> "';'";
            case LEFT_PAREN    -> "'('";
            case RIGHT_PAREN   -> "')'";
            case DOT           -> "'.'";
            case EOF           -> "fin de archivo";
            case INVALID       -> "token inválido";
            default            -> name();
        };
    }
}
