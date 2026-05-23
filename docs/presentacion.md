# DataQuery Solutions — Compilador SQL Multi-Motor

**Universidad Mariano Galvez**  
**Curso: Compiladores — 2026**  
**Tecnologias: Java 21 + Spring Boot 3.2 / Vue 3 + Vite + Monaco Editor**

---

## Que es DataQuery Solutions?

DataQuery Solutions es una plataforma web interactiva que funciona como un **compilador de consultas SQL multi-motor**. Su proposito es validar y analizar sintacticamente sentencias SQL contra cuatro motores de base de datos distintos (MySQL, PostgreSQL, SQL Server y MongoDB) utilizando las tres fases clasicas de compilacion: **analisis lexico**, **analisis sintactico** y **analisis semantico**.

## Problema que resuelve

Los desarrolladores y administradores de bases de datos necesitan validar consultas SQL antes de ejecutarlas en produccion. Cada motor de BD tiene su propio dialecto SQL con keywords, tipos de dato y reglas sintacticas diferentes. DataQuery Solutions unifica la validacion en un solo lugar, permitiendo:

- Verificar la correccion lexica y sintactica de una consulta **sin conexion** a la BD
- Validar semanticamente contra el **schema real** de una base de datos conectada
- Explorar la sintaxis soportada y las keywords de cada motor
- Gestionar conexiones persistentes a multiples bases de datos

## Arquitectura general

El proyecto sigue una arquitectura **cliente-servidor** con dos componentes principales:

```
┌──────────────────────────────────────────────────────────┐
│                    FRONTEND (Vue 3)                      │
│  Puerto 5173  ·  Monaco Editor  ·  Pinia Store           │
│  Componentes: SqlEditor, PhaseStatus, ErrorPanel,        │
│              SchemaExplorer, ConnectionManager,           │
│              SyntaxHelper                                │
└────────────────────────┬─────────────────────────────────┘
                         │  HTTP / REST (JSON)
                         ▼
┌──────────────────────────────────────────────────────────┐
│                    BACKEND (Spring Boot)                  │
│  Puerto 8080  ·  Java 21  ·  Maven                       │
│                                                          │
│  Controladores REST:                                      │
│    /api/compile  ·  /api/dialects  ·  /api/connections    │
│    /api/catalog/{dialect}/keywords/types/syntax           │
│                                                          │
│  Pipeline de compilacion:                                 │
│    SQL ─► Lexer ─► Parser ─► SemanticAnalyzer ─► Result  │
│                                                          │
│  Base de datos interna: MariaDB (dataquery_compiler)     │
└──────────────────────────────────────────────────────────┘
```

## Pipeline de compilacion de 3 fases

### 1. Analisis Lexico (Lexer)

Convierte el texto SQL en una secuencia de tokens. Reconoce palabras clave (SELECT, FROM, WHERE...), identificadores (nombres de tablas y columnas), numeros, cadenas de texto, operadores de comparacion y simbolos especiales. Soporta comentarios de linea (--) y bloque (/* */).

Inyecta las keywords especificas de cada dialecto para tokenizacion correcta.

### 2. Analisis Sintactico (Parser)

Toma la secuencia de tokens y construye un **Arbol de Sintaxis Abstracta (AST)**. Soporta 11 tipos de sentencias SQL:

| Sentencia | Categoria | Ejemplo |
|-----------|-----------|---------|
| SELECT | DML | SELECT * FROM users WHERE age > 18 |
| INSERT | DML | INSERT INTO users (name, age) VALUES ('Ana', 25) |
| UPDATE | DML | UPDATE users SET age = 26 WHERE id = 1 |
| DELETE | DML | DELETE FROM users WHERE id = 1 |
| CREATE TABLE | DDL | CREATE TABLE users (id INT PRIMARY KEY) |
| DROP TABLE | DDL | DROP TABLE IF EXISTS users |
| ALTER TABLE | DDL | ALTER TABLE users ADD COLUMN email VARCHAR(100) |
| CREATE INDEX | DDL | CREATE INDEX idx_name ON users (name) |
| BEGIN / COMMIT / ROLLBACK | TCL | BEGIN TRANSACTION |
| GRANT | DCL | GRANT SELECT ON users TO admin |
| REVOKE | DCL | REVOKE INSERT ON users FROM admin |

### 3. Analisis Semantico (SemanticAnalyzer)

Valida la correccion semantica contra el schema real de la base de datos (si hay conexion activa). Verifica:

- Existencia de tablas y columnas
- Compatibilidad de tipos de dato en asignaciones y comparaciones
- Coincidencia de numero de columnas en INSERT
- Restricciones NOT NULL
- Deteccion de columnas duplicadas en CREATE TABLE

## Motores de base de datos soportados

| Motor | Puerto default | Color | Driver | Transacciones |
|-------|---------------|-------|--------|---------------|
| MySQL 8.x | 3306 | #4479A1 | JDBC | Si |
| PostgreSQL 15+ | 5432 | #336791 | JDBC | Si |
| SQL Server 2019+ | 1433 | #CC2927 | JDBC | Si |
| MongoDB 6+ | 27017 | #47A248 | Nativo (MongoDB Driver) | No |

## Componentes del Frontend

| Componente | Proposito |
|------------|-----------|
| **CompilerView** | Vista principal que orquesta toda la interfaz |
| **SqlEditor** | Editor de codigo Monaco con marcadores de error |
| **PhaseStatus** | Muestra el estado de cada fase (OK/Error/Espera) |
| **ErrorPanel** | Lista de errores y advertencias con posicion |
| **ConnectionManager** | Formulario para gestionar conexiones a BD |
| **SchemaExplorer** | Arbol explorador del schema de la BD conectada |
| **SyntaxHelper** | Referencia de sintaxis y keywords por dialecto |

## Stack tecnologico completo

**Backend:**
- Java 21 + Spring Boot 3.2.5
- Spring Data JPA + Hibernate
- MariaDB (base de datos interna del compilador)
- Drivers JDBC: MySQL, PostgreSQL, SQL Server
- MongoDB Sync Driver (nativo, no JDBC)
- Cifrado AES para contraseñas de conexion
- Maven como gestor de dependencias

**Frontend:**
- Vue 3 Composition API + Vite 5
- Pinia (manejo de estado)
- Monaco Editor (editor de codigo tipo VS Code)
- Axios (cliente HTTP)
- Sin framework CSS externo — estilos propios con tema oscuro

## Catalogo de base de datos

El compilador mantiene un catalogo completo en MariaDB con:

- **4 dialectos** configurados con metadatos (display name, color, puerto, patron JDBC)
- **~350 keywords** distribuidas entre los 4 motores
- **13 tipos de dato abstractos** con mapeos a tipos nativos (+50 mapeos)
- **14 tipos de sentencia SQL** con plantillas de sintaxis
- **Restricciones de keywords por sentencia** (requeridas, opcionales, exclusivas)

---

*Documentacion generada el 21 de mayo de 2026*
