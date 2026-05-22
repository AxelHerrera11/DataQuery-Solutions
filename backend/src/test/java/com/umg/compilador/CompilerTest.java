package com.umg.compilador;

import com.umg.compilador.compiler.ast.ASTNode.*;
import com.umg.compilador.compiler.lexer.*;
import com.umg.compilador.compiler.parser.*;
import com.umg.compilador.compiler.semantic.*;
import com.umg.compilador.compiler.token.*;
import com.umg.compilador.schema.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataQuery — Suite Completa")
class CompilerTest {

    // ── Helpers ────────────────────────────────────────────────────────
    private static List<Token> lex(String q)    { return new Lexer(q).tokenize(); }
    private static StatementNode parse(String q)  { return new Parser(lex(q)).parse(); }

    private static SemanticAnalyzer.SemanticResult analyze(String q, DatabaseSchema schema) {
        return new SemanticAnalyzer(schema).analyze(parse(q));
    }

    /** Schema de prueba */
    private static DatabaseSchema testSchema() {
        return new DatabaseSchema("test_db", "MYSQL", List.of(
            new SchemaTable("usuarios", List.of(
                new SchemaColumn("id",     DataType.INT,     false, true),
                new SchemaColumn("nombre", DataType.VARCHAR, true,  false),
                new SchemaColumn("edad",   DataType.INT,     true,  false),
                new SchemaColumn("ciudad", DataType.VARCHAR, true,  false)
            )),
            new SchemaTable("productos", List.of(
                new SchemaColumn("id",        DataType.INT,     false, true),
                new SchemaColumn("nombre",    DataType.VARCHAR, true,  false),
                new SchemaColumn("precio",    DataType.FLOAT,   true,  false),
                new SchemaColumn("categoria", DataType.VARCHAR, true,  false)
            ))
        ));
    }

    // ================================================================
    // LEXER
    // ================================================================
    @Nested @DisplayName("Fase 1 — Léxico")
    class LexerTests {

        @Test @DisplayName("Tokeniza SELECT * FROM tabla")
        void tokenizesSelectStar() {
            List<Token> tokens = lex("SELECT * FROM usuarios;");
            assertEquals(TokenType.SELECT,     tokens.get(0).type());
            assertEquals(TokenType.ASTERISK,   tokens.get(1).type());
            assertEquals(TokenType.FROM,       tokens.get(2).type());
            assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
            assertEquals("usuarios",           tokens.get(3).value());
            assertEquals(TokenType.SEMICOLON,  tokens.get(4).type());
        }

        @Test @DisplayName("Tokeniza operadores de dos caracteres")
        void tokenizesMulticharOperators() {
            assertEquals(TokenType.GREATER_EQUAL, lex("edad >= 18").get(1).type());
            assertEquals(TokenType.LESS_EQUAL,    lex("edad <= 65").get(1).type());
            assertEquals(TokenType.NOT_EQUAL,     lex("id != 0").get(1).type());
        }

        @Test @DisplayName("Ignora comentarios -- y /* */")
        void ignoresComments() {
            assertEquals(TokenType.SELECT, lex("-- comentario\nSELECT * FROM t;").get(0).type());
            assertEquals(TokenType.SELECT, lex("/* bloque */ SELECT * FROM t;").get(0).type());
        }

        @Test @DisplayName("Tokeniza string con comillas escapadas")
        void tokenizesEscapedQuotes() {
            Token str = lex("WHERE nombre = 'O''Brien'").stream()
                .filter(t -> t.type() == TokenType.STRING).findFirst().orElseThrow();
            assertEquals("O'Brien", str.value());
        }

        @Test @DisplayName("Tokeniza número decimal")
        void tokenizesFloat() {
            Token num = lex("precio = 9.99").stream()
                .filter(t -> t.type() == TokenType.NUMBER).findFirst().orElseThrow();
            assertEquals("9.99", num.value());
        }

        @Test @DisplayName("Lanza LexerException por string sin cerrar")
        void throwsOnUnclosedString() {
            assertThrows(LexerException.class, () -> lex("WHERE nombre = 'sin cerrar"));
        }

        @Test @DisplayName("Keywords son case-insensitive")
        void keywordsCaseInsensitive() {
            assertEquals(TokenType.SELECT, lex("select * from t").get(0).type());
        }

        @Test @DisplayName("Tokeniza paréntesis y punto")
        void tokenizesParenAndDot() {
            List<Token> tokens = lex("COUNT(id) , t.nombre");
            assertEquals(TokenType.COUNT,      tokens.get(0).type());
            assertEquals(TokenType.LEFT_PAREN, tokens.get(1).type());
            assertEquals(TokenType.DOT,        tokens.get(6).type());
        }

        @Test @DisplayName("Tokeniza INSERT INTO")
        void tokenizesInsert() {
            List<Token> tokens = lex("INSERT INTO usuarios VALUES (1, 'Axel')");
            assertEquals(TokenType.INSERT, tokens.get(0).type());
            assertEquals(TokenType.INTO,   tokens.get(1).type());
            assertEquals(TokenType.VALUES, tokens.get(3).type());
        }

        @Test @DisplayName("Tokeniza UPDATE SET")
        void tokenizesUpdate() {
            List<Token> tokens = lex("UPDATE usuarios SET nombre = 'Nuevo'");
            assertEquals(TokenType.UPDATE, tokens.get(0).type());
            assertEquals(TokenType.SET,    tokens.get(2).type());
        }

        @Test @DisplayName("Tokeniza DELETE FROM")
        void tokenizesDelete() {
            List<Token> tokens = lex("DELETE FROM usuarios");
            assertEquals(TokenType.DELETE, tokens.get(0).type());
            assertEquals(TokenType.FROM,   tokens.get(1).type());
        }

        @Test @DisplayName("Tokeniza CREATE TABLE")
        void tokenizesCreateTable() {
            List<Token> tokens = lex("CREATE TABLE t (id INT)");
            assertEquals(TokenType.CREATE, tokens.get(0).type());
            assertEquals(TokenType.TABLE,  tokens.get(1).type());
        }

        @Test @DisplayName("Tokeniza DROP TABLE")
        void tokenizesDropTable() {
            List<Token> tokens = lex("DROP TABLE IF EXISTS t");
            assertEquals(TokenType.DROP,  tokens.get(0).type());
            assertEquals(TokenType.TABLE, tokens.get(1).type());
        }

        @Test @DisplayName("Tokeniza ALTER TABLE")
        void tokenizesAlterTable() {
            List<Token> tokens = lex("ALTER TABLE t ADD COLUMN c INT");
            assertEquals(TokenType.ALTER, tokens.get(0).type());
            assertEquals(TokenType.TABLE, tokens.get(1).type());
            assertEquals(TokenType.ADD,   tokens.get(3).type());
        }

        @Test @DisplayName("Tokeniza transacciones")
        void tokenizesTransactions() {
            assertEquals(TokenType.BEGIN,   lex("BEGIN").get(0).type());
            assertEquals(TokenType.COMMIT,  lex("COMMIT").get(0).type());
            assertEquals(TokenType.ROLLBACK, lex("ROLLBACK").get(0).type());
        }

        @Test @DisplayName("Tokeniza GRANT y REVOKE")
        void tokenizesGrantRevoke() {
            assertEquals(TokenType.GRANT,  lex("GRANT SELECT ON t TO user").get(0).type());
            assertEquals(TokenType.REVOKE, lex("REVOKE SELECT ON t FROM user").get(0).type());
        }
    }

    // ================================================================
    // PARSER
    // ================================================================
    @Nested @DisplayName("Fase 2 — Sintáctico")
    class ParserTests {

        // ── SELECT ────────────────────────────────────────────────────

        @Test @DisplayName("Parsea SELECT *")
        void parsesSelectStar() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios;");
            assertTrue(n.selectAll());
            assertEquals("usuarios", n.tableName());
            assertFalse(n.hasWhere());
        }

        @Test @DisplayName("Parsea lista de columnas")
        void parsesColumnList() {
            SelectNode n = (SelectNode) parse("SELECT id, nombre, ciudad FROM usuarios");
            assertEquals(List.of("id","nombre","ciudad"), n.columns());
        }

        @Test @DisplayName("Parsea cláusula WHERE")
        void parsesWhere() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios WHERE id = 1");
            assertTrue(n.hasWhere());
            assertInstanceOf(SimpleCondition.class, n.whereCondition());
            SimpleCondition sc = (SimpleCondition) n.whereCondition();
            assertEquals("id", sc.left().value());
            assertEquals("1",  sc.right().value());
        }

        @Test @DisplayName("Parsea AND/OR en WHERE")
        void parsesCompoundWhere() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios WHERE edad > 18 AND ciudad = 'NYC'");
            assertTrue(n.hasWhere());
            assertInstanceOf(CompoundCondition.class, n.whereCondition());
        }

        @Test @DisplayName("Lanza ParseException por FROM faltante")
        void throwsOnMissingFrom() {
            assertThrows(ParseException.class, () -> parse("SELECT * usuarios"));
        }

        @Test @DisplayName("Lanza ParseException por columna faltante tras coma")
        void throwsOnMissingColumnAfterComma() {
            assertThrows(ParseException.class, () -> parse("SELECT id, FROM usuarios"));
        }

        // ── INSERT ────────────────────────────────────────────────────

        @Test @DisplayName("Parsea INSERT INTO ... VALUES")
        void parsesInsertValues() {
            InsertNode n = (InsertNode) parse("INSERT INTO usuarios (id, nombre) VALUES (1, 'Axel')");
            assertEquals("usuarios", n.tableName());
            assertEquals(List.of("id", "nombre"), n.columns());
            assertTrue(n.isValuesInsert());
            assertEquals(1, n.values().size());
            assertEquals("1", n.values().get(0).get(0).value());
            assertEquals("Axel", n.values().get(0).get(1).value());
        }

        @Test @DisplayName("Parsea INSERT con múltiples filas")
        void parsesInsertMultipleRows() {
            InsertNode n = (InsertNode) parse("INSERT INTO productos VALUES (1, 'A', 10.5), (2, 'B', 20.0)");
            assertEquals(2, n.values().size());
        }

        @Test @DisplayName("Parsea INSERT INTO ... SELECT")
        void parsesInsertSelect() {
            InsertNode n = (InsertNode) parse("INSERT INTO productos SELECT * FROM otros");
            assertTrue(n.isSelectInsert());
            assertNotNull(n.selectQuery());
        }

        @Test @DisplayName("Lanza ParseException en INSERT sin tabla")
        void throwsOnInsertWithoutTable() {
            assertThrows(ParseException.class, () -> parse("INSERT INTO"));
        }

        // ── UPDATE ────────────────────────────────────────────────────

        @Test @DisplayName("Parsea UPDATE ... SET")
        void parsesUpdate() {
            UpdateNode n = (UpdateNode) parse("UPDATE usuarios SET nombre = 'Nuevo', edad = 30 WHERE id = 1");
            assertEquals("usuarios", n.tableName());
            assertEquals(2, n.assignments().size());
            assertEquals("nombre", n.assignments().get(0).column());
            assertEquals("edad", n.assignments().get(1).column());
            assertTrue(n.hasWhere());
        }

        @Test @DisplayName("Lanza ParseException en UPDATE sin SET")
        void throwsOnUpdateWithoutSet() {
            assertThrows(ParseException.class, () -> parse("UPDATE usuarios WHERE id = 1"));
        }

        // ── DELETE ────────────────────────────────────────────────────

        @Test @DisplayName("Parsea DELETE FROM")
        void parsesDelete() {
            DeleteNode n = (DeleteNode) parse("DELETE FROM usuarios WHERE id = 5");
            assertEquals("usuarios", n.tableName());
            assertTrue(n.hasWhere());
        }

        @Test @DisplayName("Parsea DELETE FROM sin WHERE")
        void parsesDeleteWithoutWhere() {
            DeleteNode n = (DeleteNode) parse("DELETE FROM usuarios");
            assertFalse(n.hasWhere());
        }

        @Test @DisplayName("Lanza ParseException en DELETE sin FROM")
        void throwsOnDeleteWithoutFrom() {
            assertThrows(ParseException.class, () -> parse("DELETE usuarios"));
        }

        // ── CREATE TABLE ──────────────────────────────────────────────

        @Test @DisplayName("Parsea CREATE TABLE simple")
        void parsesCreateTable() {
            CreateTableNode n = (CreateTableNode) parse("CREATE TABLE t (id INT, name VARCHAR(100))");
            assertEquals("t", n.tableName());
            assertEquals(2, n.columns().size());
            assertEquals("id", n.columns().get(0).name());
            assertEquals("INT", n.columns().get(0).type());
        }

        @Test @DisplayName("Parsea CREATE TABLE IF NOT EXISTS")
        void parsesCreateTableIfNotExists() {
            CreateTableNode n = (CreateTableNode) parse("CREATE TABLE IF NOT EXISTS t (id INT)");
            assertTrue(n.ifNotExists());
        }

        @Test @DisplayName("Parsea CREATE TABLE con constraints")
        void parsesCreateTableWithConstraints() {
            CreateTableNode n = (CreateTableNode) parse("CREATE TABLE t (id INT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
            assertEquals(2, n.columns().size());
            assertFalse(n.columns().get(0).constraints().isEmpty());
        }

        @Test @DisplayName("Lanza ParseException en CREATE TABLE sin paréntesis")
        void throwsOnCreateTableWithoutParens() {
            assertThrows(ParseException.class, () -> parse("CREATE TABLE t"));
        }

        // ── DROP TABLE ────────────────────────────────────────────────

        @Test @DisplayName("Parsea DROP TABLE")
        void parsesDropTable() {
            DropTableNode n = (DropTableNode) parse("DROP TABLE usuarios");
            assertEquals("usuarios", n.tableName());
            assertFalse(n.ifExists());
        }

        @Test @DisplayName("Parsea DROP TABLE IF EXISTS")
        void parsesDropTableIfExists() {
            DropTableNode n = (DropTableNode) parse("DROP TABLE IF EXISTS usuarios");
            assertTrue(n.ifExists());
        }

        // ── ALTER TABLE ───────────────────────────────────────────────

        @Test @DisplayName("Parsea ALTER TABLE ADD COLUMN")
        void parsesAlterAddColumn() {
            AlterTableNode n = (AlterTableNode) parse("ALTER TABLE usuarios ADD COLUMN apellido VARCHAR(100)");
            assertEquals("usuarios", n.tableName());
            assertEquals(AlterType.ADD_COLUMN, n.alterType());
            assertEquals("apellido", n.targetName());
            assertEquals("VARCHAR(100)", n.dataType());
        }

        @Test @DisplayName("Parsea ALTER TABLE DROP COLUMN")
        void parsesAlterDropColumn() {
            AlterTableNode n = (AlterTableNode) parse("ALTER TABLE usuarios DROP COLUMN apellido");
            assertEquals(AlterType.DROP_COLUMN, n.alterType());
            assertEquals("apellido", n.targetName());
        }

        // ── Transactions ──────────────────────────────────────────────

        @Test @DisplayName("Parsea BEGIN")
        void parsesBegin() {
            TransactionNode n = (TransactionNode) parse("BEGIN");
            assertEquals(TxnType.BEGIN, n.txnType());
        }

        @Test @DisplayName("Parsea COMMIT")
        void parsesCommit() {
            TransactionNode n = (TransactionNode) parse("COMMIT");
            assertEquals(TxnType.COMMIT, n.txnType());
        }

        @Test @DisplayName("Parsea ROLLBACK")
        void parsesRollback() {
            TransactionNode n = (TransactionNode) parse("ROLLBACK");
            assertEquals(TxnType.ROLLBACK, n.txnType());
        }

        @Test @DisplayName("Parsea BEGIN WORK")
        void parsesBeginWork() {
            TransactionNode n = (TransactionNode) parse("BEGIN WORK");
            assertEquals(TxnType.BEGIN, n.txnType());
        }

        // ── GRANT / REVOKE ────────────────────────────────────────────

        @Test @DisplayName("Parsea GRANT")
        void parsesGrant() {
            GrantNode n = (GrantNode) parse("GRANT SELECT, INSERT ON usuarios TO admin");
            assertEquals(List.of("SELECT", "INSERT"), n.privileges());
            assertEquals("usuarios", n.object());
            assertEquals("admin", n.user());
        }

        @Test @DisplayName("Parsea REVOKE")
        void parsesRevoke() {
            RevokeNode n = (RevokeNode) parse("REVOKE SELECT ON usuarios FROM guest");
            assertEquals(List.of("SELECT"), n.privileges());
            assertEquals("guest", n.user());
        }

        // ── CREATE INDEX ──────────────────────────────────────────────

        @Test @DisplayName("Parsea CREATE INDEX")
        void parsesCreateIndex() {
            CreateIndexNode n = (CreateIndexNode) parse("CREATE INDEX idx_nombre ON usuarios (nombre)");
            assertEquals("idx_nombre", n.indexName());
            assertEquals("usuarios", n.tableName());
            assertEquals(List.of("nombre"), n.columns());
            assertFalse(n.unique());
        }

        @Test @DisplayName("Parsea CREATE UNIQUE INDEX")
        void parsesCreateUniqueIndex() {
            CreateIndexNode n = (CreateIndexNode) parse("CREATE UNIQUE INDEX idx_id ON usuarios (id)");
            assertTrue(n.unique());
        }

        @Test @DisplayName("Lanza ParseException por sentencia no soportada")
        void throwsOnUnsupportedStatement() {
            assertThrows(ParseException.class, () -> parse("EXPLAIN SELECT * FROM t"));
        }

        @Test @DisplayName("Parsea sentencias con punto y coma opcional")
        void parsesWithOptionalSemicolon() {
            assertDoesNotThrow(() -> parse("SELECT * FROM t"));
            assertDoesNotThrow(() -> parse("SELECT * FROM t;"));
            assertDoesNotThrow(() -> parse("INSERT INTO t VALUES (1)"));
            assertDoesNotThrow(() -> parse("INSERT INTO t VALUES (1);"));
        }

        // ── SELECT avanzado — JOIN, GROUP BY, ORDER BY, LIMIT ────────

        @Test @DisplayName("Parsea SELECT con JOIN")
        void parsesSelectWithJoin() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios JOIN productos ON usuarios.id = productos.id");
            assertTrue(n.selectAll());
            assertEquals("usuarios", n.tableName());
            assertTrue(n.hasJoins());
            assertEquals(1, n.joins().size());
            assertEquals(JoinType.INNER, n.joins().get(0).type());
            assertEquals("productos", n.joins().get(0).tableName());
        }

        @Test @DisplayName("Parsea SELECT con LEFT JOIN")
        void parsesSelectWithLeftJoin() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios LEFT JOIN productos ON usuarios.id = productos.id");
            assertEquals(JoinType.LEFT, n.joins().get(0).type());
        }

        @Test @DisplayName("Parsea SELECT con GROUP BY")
        void parsesSelectWithGroupBy() {
            SelectNode n = (SelectNode) parse("SELECT ciudad FROM usuarios GROUP BY ciudad");
            assertTrue(n.hasGroupBy());
            assertEquals(List.of("ciudad"), n.groupBy());
        }

        @Test @DisplayName("Parsea SELECT con ORDER BY")
        void parsesSelectWithOrderBy() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios ORDER BY nombre ASC, edad DESC");
            assertTrue(n.hasOrderBy());
            assertEquals(2, n.orderBy().size());
            assertEquals("nombre", n.orderBy().get(0).column());
            assertEquals(SortOrder.ASC, n.orderBy().get(0).order());
            assertEquals("edad", n.orderBy().get(1).column());
            assertEquals(SortOrder.DESC, n.orderBy().get(1).order());
        }

        @Test @DisplayName("Parsea SELECT con ORDER BY por defecto ASC")
        void parsesOrderByDefaultAsc() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios ORDER BY nombre");
            assertEquals(SortOrder.ASC, n.orderBy().get(0).order());
        }

        @Test @DisplayName("Parsea SELECT con LIMIT")
        void parsesSelectWithLimit() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios LIMIT 10");
            assertTrue(n.hasLimit());
            assertEquals(10, n.limit());
        }

        @Test @DisplayName("Parsea SELECT con LIMIT y OFFSET")
        void parsesSelectWithLimitOffset() {
            SelectNode n = (SelectNode) parse("SELECT * FROM usuarios LIMIT 5 OFFSET 10");
            assertTrue(n.hasLimit());
            assertEquals(5, n.limit());
            assertTrue(n.hasOffset());
            assertEquals(10, n.offset());
        }

        @Test @DisplayName("Parsea SELECT con todas las cláusulas")
        void parsesSelectWithAllClauses() {
            SelectNode n = (SelectNode) parse(
                "SELECT * FROM usuarios " +
                "JOIN productos ON usuarios.id = productos.id " +
                "WHERE edad > 18 " +
                "GROUP BY ciudad " +
                "ORDER BY nombre ASC " +
                "LIMIT 20"
            );
            assertTrue(n.hasJoins());
            assertTrue(n.hasWhere());
            assertTrue(n.hasGroupBy());
            assertTrue(n.hasOrderBy());
            assertTrue(n.hasLimit());
        }
    }

    // ================================================================
    // SEMÁNTICO
    // ================================================================
    @Nested @DisplayName("Fase 3 — Semántico")
    class SemanticTests {

        // ── SELECT ────────────────────────────────────────────────────

        @Test @DisplayName("SELECT válido con schema real")
        void validSelect() {
            assertTrue(analyze("SELECT * FROM usuarios;", testSchema()).isValid());
        }

        @Test @DisplayName("SELECT: error tabla inexistente")
        void invalidTable() {
            var r = analyze("SELECT * FROM clientes", testSchema());
            assertFalse(r.isValid());
            assertTrue(r.errors().get(0).contains("clientes"));
        }

        @Test @DisplayName("SELECT: error columna inexistente")
        void invalidColumn() {
            var r = analyze("SELECT telefono FROM usuarios", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("SELECT: tipos incompatibles en WHERE")
        void incompatibleTypes() {
            var r = analyze("SELECT * FROM usuarios WHERE edad = 'adulto'", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("Sin conexión — solo advertencia, no error")
        void emptySchemaGivesWarning() {
            var r = analyze("SELECT * FROM cualquier_tabla", DatabaseSchema.empty());
            assertTrue(r.isValid());
            assertFalse(r.warnings().isEmpty());
        }

        @Test @DisplayName("SELECT: FLOAT compatible con INT en WHERE")
        void floatCompatibleWithInt() {
            assertTrue(analyze("SELECT * FROM productos WHERE precio = 10", testSchema()).isValid());
        }

        // ── INSERT ────────────────────────────────────────────────────

        @Test @DisplayName("INSERT válido")
        void validInsert() {
            assertTrue(analyze("INSERT INTO usuarios (id, nombre) VALUES (1, 'Axel')", testSchema()).isValid());
        }

        @Test @DisplayName("INSERT: error tabla inexistente")
        void invalidInsertTable() {
            assertFalse(analyze("INSERT INTO clientes (id) VALUES (1)", testSchema()).isValid());
        }

        @Test @DisplayName("INSERT: error columna inexistente")
        void invalidInsertColumn() {
            assertFalse(analyze("INSERT INTO usuarios (telefono) VALUES ('123')", testSchema()).isValid());
        }

        // ── UPDATE ────────────────────────────────────────────────────

        @Test @DisplayName("UPDATE válido")
        void validUpdate() {
            assertTrue(analyze("UPDATE usuarios SET nombre = 'Nuevo' WHERE id = 1", testSchema()).isValid());
        }

        @Test @DisplayName("UPDATE: error tabla inexistente")
        void invalidUpdateTable() {
            assertFalse(analyze("UPDATE clientes SET nombre = 'X'", testSchema()).isValid());
        }

        @Test @DisplayName("UPDATE: error columna SET inexistente")
        void invalidUpdateColumn() {
            assertFalse(analyze("UPDATE usuarios SET telefono = '123'", testSchema()).isValid());
        }

        // ── DELETE ────────────────────────────────────────────────────

        @Test @DisplayName("DELETE válido")
        void validDelete() {
            assertTrue(analyze("DELETE FROM usuarios WHERE id = 1", testSchema()).isValid());
        }

        @Test @DisplayName("DELETE: error tabla inexistente")
        void invalidDeleteTable() {
            assertFalse(analyze("DELETE FROM clientes", testSchema()).isValid());
        }

        // ── CREATE TABLE ──────────────────────────────────────────────

        @Test @DisplayName("CREATE TABLE válido")
        void validCreateTable() {
            assertTrue(analyze("CREATE TABLE nueva (id INT, name VARCHAR)", testSchema()).isValid());
        }

        // ── DROP TABLE ────────────────────────────────────────────────

        @Test @DisplayName("DROP TABLE válido")
        void validDropTable() {
            assertTrue(analyze("DROP TABLE usuarios", testSchema()).isValid());
        }

        @Test @DisplayName("DROP TABLE: error tabla inexistente")
        void invalidDropTable() {
            assertFalse(analyze("DROP TABLE clientes", testSchema()).isValid());
        }

        @Test @DisplayName("DROP TABLE IF EXISTS no da error si no existe")
        void validDropTableIfExists() {
            assertTrue(analyze("DROP TABLE IF EXISTS clientes", testSchema()).isValid());
        }

        // ── ALTER TABLE ───────────────────────────────────────────────

        @Test @DisplayName("ALTER TABLE ADD COLUMN válido")
        void validAlterAddColumn() {
            assertTrue(analyze("ALTER TABLE usuarios ADD COLUMN apellido VARCHAR", testSchema()).isValid());
        }

        @Test @DisplayName("ALTER TABLE DROP COLUMN válido")
        void validAlterDropColumn() {
            assertTrue(analyze("ALTER TABLE usuarios DROP COLUMN nombre", testSchema()).isValid());
        }

        // ── CREATE INDEX ──────────────────────────────────────────────

        @Test @DisplayName("CREATE INDEX válido")
        void validCreateIndex() {
            assertTrue(analyze("CREATE INDEX idx_nombre ON usuarios (nombre)", testSchema()).isValid());
        }

        @Test @DisplayName("CREATE INDEX: error columna inexistente")
        void invalidCreateIndexColumn() {
            assertFalse(analyze("CREATE INDEX idx ON usuarios (telefono)", testSchema()).isValid());
        }

        @Test @DisplayName("CREATE INDEX: error tabla inexistente")
        void invalidCreateIndexTable() {
            assertFalse(analyze("CREATE INDEX idx ON clientes (id)", testSchema()).isValid());
        }

        // ── Transactions ──────────────────────────────────────────────

        @Test @DisplayName("BEGIN no da error semántico")
        void validBegin() {
            assertTrue(analyze("BEGIN", testSchema()).isValid());
        }

        @Test @DisplayName("COMMIT no da error semántico")
        void validCommit() {
            assertTrue(analyze("COMMIT", testSchema()).isValid());
        }

        // ── New semantic validations ────────────────────────────────────

        @Test @DisplayName("INSERT: tipo incompatible en VALUES")
        void invalidInsertType() {
            var r = analyze("INSERT INTO usuarios (edad) VALUES ('texto')", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("INSERT: values count mismatch")
        void invalidInsertValueCount() {
            var r = analyze("INSERT INTO usuarios (id, nombre) VALUES (1)", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("INSERT SELECT: column count mismatch")
        void invalidInsertSelectColumnCount() {
            var r = analyze("INSERT INTO usuarios (id) SELECT * FROM productos", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("UPDATE: tipo incompatible en SET")
        void invalidUpdateType() {
            var r = analyze("UPDATE usuarios SET edad = 'texto' WHERE id = 1", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("SELECT: tipo VARCHAR compatible con VARCHAR en WHERE")
        void varcharCompatibleWithVarchar() {
            assertTrue(analyze("SELECT * FROM usuarios WHERE nombre = 'Juan'", testSchema()).isValid());
        }

        @Test @DisplayName("SELECT: tipo INT compatible con FLOAT en WHERE")
        void intCompatibleWithFloatInWhere() {
            assertTrue(analyze("SELECT * FROM productos WHERE precio = 10", testSchema()).isValid());
        }

        @Test @DisplayName("SELECT: tipo incompatible VARCHAR vs INT en WHERE")
        void varcharIncompatibleWithInt() {
            assertFalse(analyze("SELECT * FROM usuarios WHERE nombre = 123", testSchema()).isValid());
        }

        @Test @DisplayName("INSERT: error columna NOT NULL con NULL")
        void invalidInsertNotNull() {
            SchemaTable t = testSchema().findTable("usuarios").orElseThrow();
            assertFalse(t.findColumn("id").orElseThrow().nullable()); // id is NOT NULL
            var r = analyze("INSERT INTO usuarios (id) VALUES (NULL)", testSchema());
            assertFalse(r.isValid());
        }
    }

    // ================================================================
    // INTEGRACIÓN
    // ================================================================
    @Nested @DisplayName("Integración — Pipeline completo")
    class IntegrationTests {

        @Test @DisplayName("SELECT — pipeline completo válido")
        void fullPipelineSelectValid() {
            assertTrue(analyze("SELECT nombre, ciudad FROM usuarios WHERE edad > 18;", testSchema()).isValid());
        }

        @Test @DisplayName("SELECT — pipeline completo detecta error semántico")
        void fullPipelineSelectInvalid() {
            assertFalse(analyze("SELECT email FROM usuarios;", testSchema()).isValid());
        }

        @Test @DisplayName("INSERT — pipeline completo válido")
        void fullPipelineInsertValid() {
            assertTrue(analyze("INSERT INTO usuarios (id, nombre) VALUES (1, 'Test')", testSchema()).isValid());
        }

        @Test @DisplayName("INSERT — pipeline completo detecta error semántico")
        void fullPipelineInsertInvalid() {
            assertFalse(analyze("INSERT INTO clientes (id) VALUES (1)", testSchema()).isValid());
        }

        @Test @DisplayName("UPDATE — pipeline completo válido")
        void fullPipelineUpdateValid() {
            assertTrue(analyze("UPDATE usuarios SET nombre = 'X' WHERE id = 1", testSchema()).isValid());
        }

        @Test @DisplayName("DELETE — pipeline completo válido")
        void fullPipelineDeleteValid() {
            assertTrue(analyze("DELETE FROM usuarios WHERE id = 1", testSchema()).isValid());
        }
    }
}
