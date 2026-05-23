# Documentacion Tecnica — DataQuery Solutions

---

## 1. Estructura del proyecto

```
DataQuery-Solutions/
├── backend/                          # Spring Boot (Java 21, Maven)
│   ├── pom.xml                       # Dependencias: Spring Web, JPA, MariaDB, MySQL, PostgreSQL, SQL Server, MongoDB
│   ├── mvnw / mvnw.cmd               # Maven Wrapper
│   ├── SETUP_MARIADB.md              # Guia de instalacion de MariaDB
│   └── src/
│       ├── main/java/com/umg/compilador/
│       │   ├── DataQueryApplication.java       # @SpringBootApplication (entry point)
│       │   ├── compiler/
│       │   │   ├── ast/ASTNode.java            # AST sellado: 11 tipos de sentencia + expresiones + condiciones
│       │   │   ├── lexer/Lexer.java            # Analizador lexico con inyeccion de keywords de dialecto
│       │   │   ├── lexer/LexerException.java   # Excepcion lexica con posicion (linea, columna)
│       │   │   ├── parser/Parser.java          # Analizador sintactico recursive descent
│       │   │   ├── parser/ParseException.java  # Excepcion sintactica con posicion
│       │   │   ├── semantic/SemanticAnalyzer.java # Validador semantico contra schema real
│       │   │   └── token/
│       │   │       ├── Token.java              # Record inmutable: type, value, line, column
│       │   │       └── TokenType.java          # Enum con 60+ tipos de token
│       │   ├── config/WebConfig.java           # Configuracion CORS
│       │   ├── connection/
│       │   │   ├── ConnectionConfig.java       # Record con datos de conexion
│       │   │   ├── ConnectionManager.java      # Pool de conexiones JDBC + MongoDB
│       │   │   ├── ConnectionResult.java       # Resultado de test de conexion
│       │   │   └── PasswordEncryptor.java      # Cifrado AES-128 para contraseñas
│       │   ├── controller/
│       │   │   ├── CompilerController.java     # POST /api/compile
│       │   │   ├── DialectController.java      # GET /api/dialects
│       │   │   ├── ConnectionController.java   # CRUD /api/connections + /test + /schema
│       │   │   └── CatalogController.java      # GET /api/catalog/{dialect}/keywords|types|syntax
│       │   ├── dialect/
│       │   │   ├── DBDialect.java              # Interfaz con 8 metodos (identidad, keywords, JDBC, schema, validacion)
│       │   │   ├── DialectRegistry.java        # Registro central: hardcodeados + dinamicos desde BD
│       │   │   ├── impl/
│       │   │   │   ├── MySQLDialect.java       # 27 keywords extra, extractSchema via INFORMATION_SCHEMA
│       │   │   │   ├── PostgreSQLDialect.java  # 48 keywords extra
│       │   │   │   ├── SQLServerDialect.java   # 32 keywords extra
│       │   │   │   └── MongoDialect.java       # 20 keywords extra, extractSchema via MongoDB driver nativo
│       │   │   └── loader/DialectLoader.java   # Carga dialectos desde BD al arrancar
│       │   ├── dto/
│       │   │   ├── CompileRequest.java         # { sql, dialect, connectionId }
│       │   │   ├── CompileResponse.java        # { valid, errors, warnings, astJson, phases }
│       │   │   ├── CompileError.java           # { phase, line, column, message, severity }
│       │   │   ├── ConnectionDTO.java          # DTO para UI
│       │   │   ├── ConnectionRequest.java      # Request body para guardar/probar conexion
│       │   │   ├── DialectDTO.java             # DTO para lista de dialectos
│       │   │   ├── SchemaDTO.java              # DTO con tablas y columnas
│       │   │   └── catalog/                    # DTOs: KeywordDTO, SyntaxDTO, DataTypeMappingDTO, StatementKeywordDTO
│       │   ├── model/                          # Entidades JPA (7 entidades)
│       │   │   ├── DialectEntity.java
│       │   │   ├── KeywordEntity.java
│       │   │   ├── SqlStatementEntity.java
│       │   │   ├── DataTypeEntity.java
│       │   │   ├── DataTypeMappingEntity.java
│       │   │   ├── StatementSyntaxEntity.java
│       │   │   ├── StatementKeywordEntity.java
│       │   │   └── SavedConnectionEntity.java
│       │   ├── repository/                     # Repositorios Spring Data JPA (6 repos)
│       │   ├── schema/                         # Modelos del schema de BD del usuario
│       │   │   ├── DatabaseSchema.java         # Record: databaseName, dialectName, List<SchemaTable>
│       │   │   ├── SchemaTable.java            # Record: name, List<SchemaColumn>
│       │   │   ├── SchemaColumn.java           # Record: name, type, nullable, primaryKey
│       │   │   └── DataType.java               # Enum: INT, FLOAT, VARCHAR, BOOLEAN, JSON, UUID, ARRAY...
│       │   └── service/                        # Logica de negocio (4 servicios)
│       │       ├── CompilerService.java        # Orquesta pipeline de compilacion
│       │       ├── ConnectionService.java      # CRUD de conexiones + test + obtencion de schema
│       │       ├── SchemaService.java          # Cache y obtencion de schema via dialecto
│       │       └── CatalogService.java         # Consultas al catalogo de keywords/tipos/sintaxis
│       ├── main/resources/
│       │   ├── application.properties          # Config: puerto 8080, datasource MariaDB, CORS, cifrado
│       │   ├── schema.sql                      # DDL: 8 tablas (dialect, keyword, data_type, sql_statement, etc.)
│       │   └── data.sql                        # Datos iniciales: 4 dialectos, 350+ keywords, 50+ mapeos de tipos
│       └── test/java/com/umg/compilador/
│           └── CompilerTest.java               # Tests unitarios: LexerTests, ParserTests, SemanticTests, IntegrationTests
│
├── frontend/                         # Vue 3 + Vite + Monaco Editor
│   ├── index.html                    # Entry point HTML
│   ├── package.json                  # Dependencias: vue 3.4, pinia, monaco-editor 0.45, axios 1.6
│   ├── vite.config.js                # Proxy /api → localhost:8080, puerto 5173
│   └── src/
│       ├── main.js                   # createApp + createPinia
│       ├── App.vue                   # Layout global con header
│       ├── views/
│       │   └── CompilerView.vue      # Vista principal (sidebar + editor + resultados)
│       ├── stores/
│       │   └── compiler.js           # Store Pinia: estado global, acciones asincronas
│       ├── services/
│       │   └── api.js                # Cliente Axios: compilerApi, dialectApi, connectionApi, catalogApi
│       └── components/
│           ├── SqlEditor.vue         # Monaco Editor con marcadores de error
│           ├── PhaseStatus.vue       # Indicador de estado de 3 fases (lexico, sintactico, semantico)
│           ├── ErrorPanel.vue        # Lista de errores/advertencias con badge de fase
│           ├── ConnectionManager.vue # Gestion de conexiones guardadas + formulario
│           ├── SchemaExplorer.vue    # Arbol explorador de tablas y columnas
│           └── SyntaxHelper.vue      # Referencia de sintaxis con tabs de sentencias
│
├── README.md                         # Instrucciones de ejecucion
└── .gitignore                        # Ignora node_modules, target, .DS_Store
```

---

## 2. Pipeline de compilacion detallado

### 2.1 Fase Lexica (Lexer.java)

**Clase:** `com.umg.compilador.compiler.lexer.Lexer`

El Lexer recibe el SQL como String y opcionalmente un `Set<String>` de keywords propias del dialecto. El proceso:

1. **Skip whitespace and comments**: ignora espacios, saltos de linea, comentarios `--` y `/* */`
2. **Reconocimiento de tokens**:
   - Identificadores/palabras clave: secuencia de letras, digitos y `_`
   - Numeros: secuencia de digitos (con punto decimal opcional)
   - Cadenas: entre comillas simples `'...'`, con soporte de `''` escapado
   - Operadores: `=`, `>`, `<`, `>=`, `<=`, `!=`
   - Simbolos: `*`, `,`, `;`, `(`, `)`, `.`
3. **Keywords base**: 60+ palabras reservadas SQL estandar mapeadas a `TokenType` (SELECT, FROM, WHERE, INSERT, etc.)
4. **Keywords de dialecto**: se inyectan como adicionales y se tokenizan como `IDENTIFIER` (para no romper el parser base)

Los tokens incluyen posicion exacta (linea, columna) para reportar errores con precision al Monaco Editor.

### 2.2 Fase Sintactica (Parser.java)

**Clase:** `com.umg.compilador.compiler.parser.Parser`

Parser de tipo **recursive descent** que consume la lista de tokens del Lexer y construye un AST. Soporta:

- SELECT con: `*` / lista de columnas, FROM, JOINs (INNER/LEFT/RIGHT), WHERE con condiciones compuestas (AND/OR), GROUP BY, HAVING, ORDER BY (ASC/DESC), LIMIT, OFFSET
- INSERT con: VALUES (multi-fila) o subquery SELECT
- UPDATE con: multiples asignaciones SET y WHERE
- DELETE con: FROM y WHERE
- CREATE TABLE con: columnas, tipos con parametros (VARCHAR(100)), constraints (NOT NULL, PRIMARY KEY, DEFAULT, etc.), IF NOT EXISTS
- DROP TABLE con: IF EXISTS
- ALTER TABLE con: ADD/DROP/MODIFY COLUMN
- CREATE INDEX con: UNIQUE, nombre, tabla, columnas
- Transacciones: BEGIN [WORK|TRANSACTION], COMMIT [WORK], ROLLBACK [WORK]
- DCL: GRANT/REVOKE con lista de privilegios

**Estrategia de parsing:**
- `parse()` es el entry point que delega a `parseStatement()`
- `parseStatement()` usa un `switch` sobre el tipo del token actual para determinar que sentencia parsear
- Cada tipo de sentencia tiene su propio metodo (parseSelect, parseInsert, etc.)
- Las expresiones, condiciones y listas de columnas tienen metodos auxiliares reutilizables
- Los errores se lanzan como `ParseException` con linea y columna

### 2.3 Fase Semantica (SemanticAnalyzer.java)

**Clase:** `com.umg.compilador.compiler.semantic.SemanticAnalyzer`

Requiere un `DatabaseSchema` que puede ser:
- **Schema real**: obtenido desde la BD conectada via INFORMATION_SCHEMA (SQL) o inspeccion de documentos (MongoDB)
- **Schema vacio**: cuando no hay conexion activa — solo emite advertencias

Validaciones realizadas:

| Validacion | Sentencias | Descripcion |
|-----------|-----------|-------------|
| Existencia de tabla | SELECT, INSERT, UPDATE, DELETE, CREATE INDEX, GRANT, REVOKE | La tabla referenciada debe existir en el schema |
| Existencia de columna | SELECT, INSERT, UPDATE, CREATE INDEX | Las columnas deben existir en la tabla |
| Compatibilidad de tipos | INSERT, UPDATE, WHERE | Tipos compatibles (numerico con numerico, string con string, etc.) |
| Conteo de columnas | INSERT | Numero de valores coincide con numero de columnas |
| NOT NULL | INSERT | No insertar NULL en columna NOT NULL |
| Columnas duplicadas | CREATE TABLE | No repetir nombres de columnas |
| Tabla duplicada | CREATE TABLE | No crear tabla que ya existe (a menos que sea IF NOT EXISTS) |
| Tabla inexistente | DROP TABLE | No dropear tabla que no existe (a menos que sea IF EXISTS) |

**Compatibilidad de tipos** (DataType.java):
- Numericos: INT, FLOAT son compatibles entre si
- Strings: VARCHAR, CHAR, TEXT son compatibles entre si
- Fechas: DATETIME, DATE, TIME son compatibles entre si
- UNKNOWN es compatible con todo
- Cualquier otro par incompatible genera error

---

## 3. API REST completa

### Compilacion

```
POST /api/compile
Request:  { "sql": "SELECT * FROM users", "dialect": "MYSQL", "connectionId": null }
Response: {
  "valid": true,
  "errors": [],
  "warnings": [],
  "astJson": "{\"type\":\"SelectNode\",...}",
  "lexerPhase": { "passed": true, "message": "Lexico correcto" },
  "parserPhase": { "passed": true, "message": "Sintactico correcto" },
  "semanticPhase": { "passed": true, "message": "Semantico correcto" }
}
```

### Dialectos

```
GET /api/dialects
Response: [ { "name": "MYSQL", "displayName": "MySQL 8.x", "defaultPort": 3306,
              "brandColor": "#4479A1", "supportsTransactions": true }, ... ]
```

### Conexiones

```
GET    /api/connections                     # Listar todas
POST   /api/connections                     # Guardar nueva
POST   /api/connections/test                # Probar (sin guardar)
DELETE /api/connections/{id}                # Eliminar
GET    /api/connections/{id}/schema         # Obtener schema real
```

### Catalogo

```
GET /api/catalog/{dialect}/keywords                     # Todas las keywords
GET /api/catalog/{dialect}/keywords?type=FUNCTION       # Por tipo de token
GET /api/catalog/{dialect}/keywords?statement=SELECT    # Por sentencia
GET /api/catalog/{dialect}/types                        # Mapeos de tipos de dato
GET /api/catalog/{dialect}/syntax                       # Sentencias soportadas
GET /api/catalog/{dialect}/syntax/{statement}           # Plantilla de una sentencia
GET /api/catalog/{dialect}/syntax/{statement}/keywords  # Keywords de una sentencia
```

---

## 4. Base de datos del compilador (MariaDB)

### Esquema (8 tablas)

```
dialect
├── id, name, display_name, default_port, brand_color, driver_class
├── supports_txn, jdbc_url_pattern, since_version, notes
├── 1─N → keyword (keywords del dialecto)
├── 1─N → data_type_mapping (mapeo de tipos nativos)
└── 1─N → statement_syntax (sintaxis de sentencias)

sql_statement
└── id, name, category, description

keyword
├── id, dialect_id (FK), word, token_type, category
├── is_reserved, since_version, deprecated, notes
└── UNIQUE(dialect_id, word)

data_type            (tipos abstractos: INT, VARCHAR, BOOLEAN...)
└── id, name, description

data_type_mapping    (TINYINT → INT, VARCHAR → VARCHAR...)
├── id, dialect_id (FK), native_type, abstract_type_id (FK)
├── max_length, requires_length, notes
└── UNIQUE(dialect_id, native_type)

statement_syntax     (plantillas de sintaxis)
├── id, dialect_id (FK), statement_id (FK), supported, syntax_template, notes
└── UNIQUE(dialect_id, statement_id)

statement_keyword    (keywords aplicables a sentencias)
├── id, statement_id (FK), keyword_id (FK), role, position_hint, notes
└── UNIQUE(statement_id, keyword_id)

saved_connection     (conexiones persistentes de usuario)
└── id (UUID), name, dialect, host, port, database_name, username, encrypted_password, timestamps
```

---

## 5. Sistema de dialectos (arquitectura extensible)

### Interfaz DBDialect

Define 8 metodos que cada implementacion concreta debe proveer:

| Metodo | Proposito |
|--------|-----------|
| `getName()` | Identificador unico (MYSQL, POSTGRESQL...) |
| `getDisplayName()` | Nombre para UI (MySQL 8.x) |
| `getDefaultPort()` | Puerto por defecto (3306, 5432...) |
| `getBrandColor()` | Color hex para UI (#4479A1...) |
| `supportsTransactions()` | Soporte ACID |
| `getDriverClass()` | Clase del driver JDBC |
| `buildJdbcUrl(ConnectionConfig)` | Construir URL de conexion |
| `extractSchema(Connection, String)` | Extraer schema desde BD |
| `validateDialectSyntax(String)` | Validaciones especificas |
| `getDialectKeywords()` | Keywords exclusivas del motor |

### DialectRegistry

Unifica dos fuentes de dialectos:

1. **Hardcodeados** (MySQLDialect, PostgreSQLDialect, SQLServerDialect, MongoDialect) — tienen logica rica (extractSchema, validaciones)
2. **Dinamicos** (cargados desde BD via DialectLoader) — para extensibilidad sin modificar Java

La resolucion prioriza los hardcodeados sobre los dinamicos.

---

## 6. Manejo de conexiones

### Flujo de conexion JDBC

1. El usuario guarda una conexion via `POST /api/connections`
2. `ConnectionService` genera un UUID, asigna puerto por defecto si es necesario
3. `ConnectionManager` guarda en DB (con contraseña cifrada via AES-128) y en memoria
4. `ConnectionManager.getConnection()` abre conexion JDBC via `DriverManager.getConnection()` con el URL construido por el dialecto
5. Las conexiones se cachean en un `ConcurrentHashMap<String, Connection>`
6. `SchemaService` obtiene el schema via `dialect.extractSchema()` y lo cachea

### MongoDB (driver nativo)

MongoDB no usa JDBC. Se maneja con:
- `DialectRegistry.isNativeDriver("MONGODB")` → true
- `ConnectionManager.testConnection()` crea un `MongoClients.create(uri)` temporal
- `SchemaService.fetchSchema()` usa `MongoDialect.extractSchemaFromClient(MongoClient, String)`
- El schema se infiere inspeccionando un documento de muestra por coleccion

### Cifrado de contraseñas

`PasswordEncryptor` usa AES con:
- Clave configurable via `dataquery.encryption.key` en application.properties
- Padding para 16 bytes (si la clave es menor)
- Base64 para almacenamiento en BD

---

## 7. Frontend (Vue 3)

### Store (Pinia)

El store `compiler.js` centraliza:
- Estado: sql, dialect, connectionId, result, loading, dialects, connections, schema, dialectKeywords
- Getters computados: hasErrors, hasWarnings, isValid
- Acciones asincronas: compile(), loadDialects(), setDialect(), loadDialectKeywords(), loadConnections(), saveConnection(), deleteConnection(), selectConnection(), testConnection()

### Componentes

**SqlEditor.vue**: Envuelve Monaco Editor. Sincroniza el modelo con el padre via `v-model`. Aplica marcadores de error/advertencia desde los resultados del compilador usando `monaco.editor.setModelMarkers()`.

**PhaseStatus.vue**: Muestra 3 indicadores (Lexico, Sintactico, Semantico) con iconos ○ (espera), ✓ (ok), ✗ (error) y colores segun el estado.

**ErrorPanel.vue**: Lista errores con badge de fase (LEXER/PARSER/SEMANTIC), posicion (L:C) y mensaje. Soporta severidad ERROR (rojo) y WARNING (amarillo).

**ConnectionManager.vue**: Formulario completo con seleccion de dialecto, nombre, host, puerto, BD, usuario y contraseña. Botones para probar (sin guardar), guardar y cancelar.

**SchemaExplorer.vue**: Arbol colapsable de tablas y columnas. Muestra badges PK en columnas primary key y tipos de dato.

**SyntaxHelper.vue**: Navegacion por sentencias SQL con plantillas de sintaxis y keywords asociadas con su rol (REQUIRED, OPTIONAL, EXCLUSIVE).

### Proxy de desarrollo

Vite configurado con proxy: `/api` → `http://localhost:8080`

---

## 8. Tests

`CompilerTest.java` contiene:

- **LexerTests**: tokenizacion de SELECT, INSERT, UPDATE, DELETE, strings, numeros, errores
- **ParserTests**: parsing de SELECT basico, con JOIN, WHERE, INSERT, UPDATE, DELETE, errores sintacticos
- **SemanticTests**: validacion de tablas, columnas, tipos, deteccion de errores semanticos
- **IntegrationTests**: pipeline completo (lexico + sintactico + semantico)

---

## 9. Dependencias externas

| Dependencia | Version | Uso |
|------------|---------|-----|
| Spring Boot Starter Web | 3.2.5 | API REST |
| Spring Boot Starter Data JPA | 3.2.5 | Persistencia |
| Spring Boot Starter Validation | 3.2.5 | Validacion de requests |
| MariaDB Java Client | (runtime) | Driver BD interna |
| MySQL Connector J | (runtime) | Driver MySQL usuario |
| PostgreSQL | (runtime) | Driver PostgreSQL usuario |
| MSSQL JDBC | (runtime) | Driver SQL Server usuario |
| MongoDB Driver Sync | 4.11.0 | Driver MongoDB nativo |
| Vue | 3.4 | Framework frontend |
| Pinia | 2.1 | Estado frontend |
| Monaco Editor | 0.45 | Editor de codigo |
| Axios | 1.6 | HTTP client |
| Vite | 5 | Bundler frontend |
| @vitejs/plugin-vue | 5 | Plugin Vue para Vite |

---

## 10. Configuracion del entorno

**application.properties**:
```properties
server.port=8080
spring.datasource.url=jdbc:mariadb://localhost:3306/dataquery_compiler?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=
spring.sql.init.mode=always
dataquery.cors.allowed-origins=http://localhost:5173
dataquery.connection.timeout=10
dataquery.encryption.key=DataQuery2026!Key
```
