package com.umg.compilador;

import com.umg.compilador.compiler.ast.ASTNode.SelectNode;
import com.umg.compilador.compiler.lexer.*;
import com.umg.compilador.compiler.parser.*;
import com.umg.compilador.compiler.semantic.*;
import com.umg.compilador.compiler.token.*;
import com.umg.compilador.schema.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompilerTest — suite completa migrada del proyecto base.
 * Todos los tests originales se mantienen; se agregan tests para
 * el nuevo DatabaseSchema y los dialectos.
 */
@DisplayName("DataQuery — Suite Completa")
class CompilerTest {

    // ── Helpers ────────────────────────────────────────────────────────
    private static List<Token> lex(String q)    { return new Lexer(q).tokenize(); }
    private static SelectNode  parse(String q)  { return new Parser(lex(q)).parse(); }

    private static SemanticAnalyzer.SemanticResult analyze(String q, DatabaseSchema schema) {
        return new SemanticAnalyzer(schema).analyze(parse(q));
    }

    /** Schema de prueba equivalente al SymbolTable hardcoded original. */
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
            assertEquals(TokenType.DOT,        tokens.get(4).type());
        }
    }

    // ================================================================
    // PARSER
    // ================================================================
    @Nested @DisplayName("Fase 2 — Sintáctico")
    class ParserTests {

        @Test @DisplayName("Parsea SELECT *")
        void parsesSelectStar() {
            SelectNode n = parse("SELECT * FROM usuarios;");
            assertTrue(n.isSelectAll());
            assertEquals("usuarios", n.tableName());
            assertFalse(n.hasWhere());
        }

        @Test @DisplayName("Parsea lista de columnas")
        void parsesColumnList() {
            SelectNode n = parse("SELECT id, nombre, ciudad FROM usuarios");
            assertEquals(List.of("id","nombre","ciudad"), n.columns());
        }

        @Test @DisplayName("Parsea cláusula WHERE")
        void parsesWhere() {
            SelectNode n = parse("SELECT * FROM usuarios WHERE id = 1");
            assertTrue(n.hasWhere());
            assertEquals("id", n.whereCondition().left().value());
            assertEquals("1",  n.whereCondition().right().value());
        }

        @Test @DisplayName("Lanza ParseException por FROM faltante")
        void throwsOnMissingFrom() {
            assertThrows(ParseException.class, () -> parse("SELECT * usuarios"));
        }

        @Test @DisplayName("Lanza ParseException por columna faltante tras coma")
        void throwsOnMissingColumnAfterComma() {
            assertThrows(ParseException.class, () -> parse("SELECT id, FROM usuarios"));
        }
    }

    // ================================================================
    // SEMÁNTICO
    // ================================================================
    @Nested @DisplayName("Fase 3 — Semántico")
    class SemanticTests {

        @Test @DisplayName("Query válida con schema real")
        void validQuery() {
            assertTrue(analyze("SELECT * FROM usuarios;", testSchema()).isValid());
        }

        @Test @DisplayName("Error: tabla inexistente")
        void invalidTable() {
            var r = analyze("SELECT * FROM clientes", testSchema());
            assertFalse(r.isValid());
            assertTrue(r.errors().get(0).contains("clientes"));
        }

        @Test @DisplayName("Error: columna inexistente")
        void invalidColumn() {
            var r = analyze("SELECT telefono FROM usuarios", testSchema());
            assertFalse(r.isValid());
        }

        @Test @DisplayName("Error: tipos incompatibles en WHERE")
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

        @Test @DisplayName("FLOAT compatible con INT en WHERE")
        void floatCompatibleWithInt() {
            assertTrue(analyze("SELECT * FROM productos WHERE precio = 10", testSchema()).isValid());
        }
    }

    // ================================================================
    // INTEGRACIÓN
    // ================================================================
    @Nested @DisplayName("Integración — Pipeline completo")
    class IntegrationTests {

        @Test @DisplayName("Pipeline completo válido")
        void fullPipelineValid() {
            assertTrue(analyze("SELECT nombre, ciudad FROM usuarios WHERE edad > 18;", testSchema()).isValid());
        }

        @Test @DisplayName("Pipeline completo detecta error semántico")
        void fullPipelineInvalid() {
            assertFalse(analyze("SELECT email FROM usuarios;", testSchema()).isValid());
        }
    }
}
