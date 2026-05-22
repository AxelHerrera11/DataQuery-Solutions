-- =============================================================================
-- DataQuery Solutions — Schema de la Base de Datos del Compilador
-- Motor: MySQL 8.x
-- Descripción: Catálogo completo de dialectos SQL:
--              keywords, tipos de dato y sintaxis por sentencia
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS saved_connection;
DROP TABLE IF EXISTS statement_keyword;
DROP TABLE IF EXISTS statement_syntax;
DROP TABLE IF EXISTS keyword;
DROP TABLE IF EXISTS data_type_mapping;
DROP TABLE IF EXISTS data_type;
DROP TABLE IF EXISTS sql_statement;
DROP TABLE IF EXISTS dialect;
SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 1. DIALECT — un registro por motor de BD soportado
-- -----------------------------------------------------------------------------
CREATE TABLE dialect (
    id               INT          NOT NULL AUTO_INCREMENT,
    name             VARCHAR(20)  NOT NULL UNIQUE,   -- 'MYSQL', 'POSTGRESQL'...
    display_name     VARCHAR(50)  NOT NULL,           -- 'MySQL 8.x'
    default_port     INT          NOT NULL,
    brand_color      VARCHAR(10)  NOT NULL,           -- '#4479A1'
    driver_class     VARCHAR(100),                   -- JDBC driver
    supports_txn     TINYINT(1)   NOT NULL DEFAULT 1,
    jdbc_url_pattern VARCHAR(200) NOT NULL,          -- 'jdbc:mysql://{host}:{port}/{db}'
    since_version    VARCHAR(20),
    notes            TEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 2. SQL_STATEMENT — tipos de sentencia SQL soportados
-- -----------------------------------------------------------------------------
CREATE TABLE sql_statement (
    id          INT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(30) NOT NULL UNIQUE,  -- 'SELECT', 'INSERT', 'UPDATE'...
    category    VARCHAR(20) NOT NULL,         -- 'DML', 'DDL', 'DCL', 'TCL'
    description TEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 3. KEYWORD — catálogo global de keywords SQL
-- -----------------------------------------------------------------------------
CREATE TABLE keyword (
    id              INT          NOT NULL AUTO_INCREMENT,
    dialect_id      INT          NOT NULL,
    word            VARCHAR(60)  NOT NULL,
    token_type      VARCHAR(30)  NOT NULL,
    -- RESERVED     → palabra reservada (no puede usarse como identificador)
    -- FUNCTION      → función nativa (COUNT, SUM, NOW...)
    -- DATA_TYPE     → tipo de dato (INT, VARCHAR, JSONB...)
    -- CLAUSE        → cláusula de sentencia (WHERE, GROUP BY...)
    -- OPERATOR      → operador especial (LIKE, BETWEEN, IN...)
    -- MODIFIER      → modificador (DISTINCT, ALL, TOP...)
    -- PRAGMA        → directiva de motor (NOLOCK, STRAIGHT_JOIN...)
    category        VARCHAR(30),
    -- DML / DDL / DCL / TCL / AGGREGATE / WINDOW / JSON / SPATIAL
    is_reserved     TINYINT(1)   NOT NULL DEFAULT 1,
    since_version   VARCHAR(20),
    deprecated      TINYINT(1)   NOT NULL DEFAULT 0,
    notes           TEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_dialect_word (dialect_id, word),
    CONSTRAINT fk_kw_dialect FOREIGN KEY (dialect_id) REFERENCES dialect(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 4. DATA_TYPE — tipos de dato abstractos del compilador
-- -----------------------------------------------------------------------------
CREATE TABLE data_type (
    id          INT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(30) NOT NULL UNIQUE,  -- 'INT', 'FLOAT', 'VARCHAR'...
    description TEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 5. DATA_TYPE_MAPPING — equivalencia entre tipo nativo y tipo abstracto
--    Ej: MySQL 'TINYINT' → INT | PostgreSQL 'INTEGER' → INT
-- -----------------------------------------------------------------------------
CREATE TABLE data_type_mapping (
    id               INT         NOT NULL AUTO_INCREMENT,
    dialect_id       INT         NOT NULL,
    native_type      VARCHAR(60) NOT NULL,   -- nombre exacto en el motor
    abstract_type_id INT         NOT NULL,   -- referencia a data_type
    max_length       INT,                    -- longitud máxima permitida (NULL = sin límite)
    requires_length  TINYINT(1)  NOT NULL DEFAULT 0,
    notes            TEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_dialect_native (dialect_id, native_type),
    CONSTRAINT fk_dtm_dialect FOREIGN KEY (dialect_id)       REFERENCES dialect(id),
    CONSTRAINT fk_dtm_type   FOREIGN KEY (abstract_type_id)  REFERENCES data_type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 6. STATEMENT_SYNTAX — qué sentencias soporta cada dialecto y cómo
-- -----------------------------------------------------------------------------
CREATE TABLE statement_syntax (
    id              INT          NOT NULL AUTO_INCREMENT,
    dialect_id      INT          NOT NULL,
    statement_id    INT          NOT NULL,
    supported       TINYINT(1)   NOT NULL DEFAULT 1,
    syntax_template TEXT,
    -- Ej: 'SELECT [DISTINCT] {cols} FROM {table} [WHERE {cond}] [LIMIT {n}]'
    notes           TEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_dialect_stmt (dialect_id, statement_id),
    CONSTRAINT fk_ss_dialect   FOREIGN KEY (dialect_id)   REFERENCES dialect(id),
    CONSTRAINT fk_ss_statement FOREIGN KEY (statement_id) REFERENCES sql_statement(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 7. STATEMENT_KEYWORD — qué keywords aplican a qué sentencia en qué dialecto
--    (tabla pivote de muchos a muchos con metadatos)
-- -----------------------------------------------------------------------------
CREATE TABLE statement_keyword (
    id              INT         NOT NULL AUTO_INCREMENT,
    statement_id    INT         NOT NULL,
    keyword_id      INT         NOT NULL,
    role            VARCHAR(30) NOT NULL,
    -- REQUIRED   → la keyword es obligatoria en la sentencia
    -- OPTIONAL   → puede aparecer o no
    -- EXCLUSIVE  → exclusiva de este dialecto para esta sentencia
    -- FORBIDDEN  → no puede usarse en esta sentencia con este dialecto
    position_hint   VARCHAR(30),
    -- START / BEFORE_TABLE / AFTER_SELECT / AFTER_SET / END...
    notes           TEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_stmt_kw (statement_id, keyword_id),
    CONSTRAINT fk_sk_statement FOREIGN KEY (statement_id) REFERENCES sql_statement(id),
    CONSTRAINT fk_sk_keyword   FOREIGN KEY (keyword_id)   REFERENCES keyword(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 8. SAVED_CONNECTION — conexiones persistentes a bases de datos de usuario
-- -----------------------------------------------------------------------------
CREATE TABLE saved_connection (
    id                 VARCHAR(36)  NOT NULL PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    dialect            VARCHAR(20)  NOT NULL,
    host               VARCHAR(255) NOT NULL,
    port               INT          NOT NULL,
    database_name      VARCHAR(100) NOT NULL,
    username           VARCHAR(100) NOT NULL,
    encrypted_password VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

