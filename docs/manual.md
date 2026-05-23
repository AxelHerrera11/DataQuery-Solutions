# Manual de Usuario — DataQuery Solutions

**Compilador SQL Multi-Motor**  
*Version 1.0.0*

---

## 1. Introduccion

DataQuery Solutions es una herramienta web que permite **compilar y validar consultas SQL** en tiempo real, soportando los dialectos de MySQL, PostgreSQL, SQL Server y MongoDB.

### Caracteristicas principales

- Editor de codigo SQL con resaltado de sintaxis (Monaco Editor)
- Compilacion en 3 fases (lexico, sintactico, semantico) con retroalimentacion visual
- Soporte para 4 motores de base de datos
- Gestion de conexiones persistentes a bases de datos reales
- Explorador del schema de la base de datos conectada
- Referencia de sintaxis y keywords integrada
- Validacion semantica contra schema real (tablas, columnas, tipos de dato)
- Sin necesidad de conexion para validacion basica (lexico + sintactico)

---

## 2. Requisitos del sistema

### Para ejecutar (desarrollo local)

- **Java 21** o superior
- **Node.js 18+** y **npm**
- **MariaDB 10+** (o MySQL 8.x compatible)
- Navegador web moderno (Chrome, Firefox, Edge)

### Puertos

| Componente | Puerto |
|------------|--------|
| Backend (Spring Boot) | 8080 |
| Frontend (Vite) | 5173 |
| MariaDB | 3306 |

---

## 3. Instalacion y ejecucion

### 3.1 Base de datos

1. Asegurate de tener MariaDB corriendo
2. Crea la base de datos:
   ```sql
   CREATE DATABASE dataquery_compiler
       CHARACTER SET utf8mb4
       COLLATE utf8mb4_unicode_ci;
   ```
3. Configura las credenciales en `backend/src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mariadb://localhost:3306/dataquery_compiler?useSSL=false&serverTimezone=UTC
   spring.datasource.username=root
   spring.datasource.password=tu_password
   ```

### 3.2 Backend

```bash
cd backend
./mvnw spring-boot:run
```

Al arrancar, Spring Boot ejecuta automaticamente `schema.sql` (crea 8 tablas) y `data.sql` (inserta 4 dialectos, 350+ keywords, 50+ mapeos de tipos).

Veras en consola:
```
[DialectLoader] 4 dialectos cargados desde BD: [MYSQL, POSTGRESQL, SQLSERVER, MONGODB]
```

### 3.3 Frontend

```bash
cd frontend
npm install
npm run dev
```

### 3.4 Acceder

Abre tu navegador en: **http://localhost:5173**

---

## 4. Interfaz de usuario

La interfaz se divide en dos areas principales:

### 4.1 Barra lateral izquierda (Sidebar)

**Panel "Motor de BD"**
- Botones para seleccionar el dialecto: MySQL 8.x, PostgreSQL 15+, SQL Server 2019+, MongoDB 6+
- Cada motor tiene su propio color distintivo

**Panel "Conexiones"**
- Lista de conexiones guardadas con indicador de color por motor
- Boton "+ Nueva conexion" para agregar una conexion
- Cada conexion muestra: nombre, host:puerto / base de datos
- Boton de eliminar (✕) en cada conexion
- Al hacer clic en una conexion, se selecciona y carga su schema

**Formulario de nueva conexion:**
- Motor (select con los 4 dialectos)
- Nombre de la conexion
- Host (default: localhost)
- Puerto (default segun motor)
- Base de datos
- Usuario
- Contraseña
- Boton "Probar": verifica la conexion sin guardar
- Boton "Guardar": persiste la conexion
- Boton "Cancelar"

**Panel "Schema"**
- Muestra el nombre de la BD y el motor
- Arbol explorador de tablas con numero de columnas
- Al hacer clic en una tabla, se expanden sus columnas
- Cada columna muestra: badge PK (si es primary key), nombre y tipo de dato
- Boton de refrescar (↻) para recargar el schema

### 4.2 Area principal (Main)

**Barra de herramientas (Toolbar)**
- Badge del dialecto activo con su color
- Indicador de estado: "Conexion activa" (verde) o "Solo sintaxis" (gris)
- Boton "Referencia" para abrir la guia de sintaxis
- Boton "Compilar" (▶) para ejecutar la compilacion

**Editor SQL**
- Editor de codigo Monaco (el mismo que VS Code)
- Resaltado de sintaxis SQL (tema oscuro)
- Numeros de linea
- Marcadores de error (rojo) y advertencia (amarillo) en linea y columna exacta
- Area de texto editable con altura fija

**Indicador de fases (PhaseStatus)**
Muestra el estado de cada fase del pipeline:
- **Lexico**: ○ (espera) / ✓ (ok) / ✗ (error)
- **Sintactico**: ○ (espera) / ✓ (ok) / ✗ (error)
- **Semantico**: ○ (espera) / ✓ (ok) / ✗ (error)

**Panel de resultados (tabulado)**
- **Errores**: lista de errores con icono de fase, posicion (L:C) y mensaje
- **Advertencias**: lista de advertencias con mismo formato
- **AST**: vista JSON del Arbol de Sintaxis Abstracta
- **Referencia**: guia de sintaxis del dialecto activo

---

## 5. Flujo de trabajo basico

### 5.1 Modo sin conexion (solo sintaxis)

1. Selecciona un motor de BD en la barra lateral
2. Escribe tu consulta SQL en el editor
3. Haz clic en "Compilar" (▶)
4. Revisa los resultados en las pestañas de errores/advertencias/AST
5. Si la consulta es valida, veras el mensaje "Query valida — sin errores ni advertencias"

**Ejemplos de consultas validas:**

```sql
SELECT * FROM usuarios WHERE edad > 18;

SELECT nombre, email FROM clientes
WHERE ciudad = 'Guatemala' AND activo = true
ORDER BY nombre ASC
LIMIT 10;

INSERT INTO productos (nombre, precio, stock)
VALUES ('Laptop', 1500.00, 10),
       ('Mouse', 25.50, 100);

UPDATE empleados SET salario = 5000 WHERE id = 1;

DELETE FROM logs WHERE fecha < '2024-01-01';

CREATE TABLE usuarios (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at DATETIME DEFAULT NOW()
);

BEGIN TRANSACTION;
UPDATE cuentas SET balance = balance - 100 WHERE id = 1;
UPDATE cuentas SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

### 5.2 Modo con conexion (validacion semantica)

1. En el panel "Conexiones", haz clic en "+ Nueva conexion"
2. Completa los datos: motor, host, puerto, BD, usuario, contraseña
3. Haz clic en "Probar" para verificar la conexion
4. Haz clic en "Guardar"
5. Selecciona la conexion de la lista (se cargara el schema automaticamente)
6. Escribe tu consulta SQL usando los nombres reales de tablas y columnas
7. Haz clic en "Compilar"

El analisis semantico validara:
- Que las tablas existan en la BD
- Que las columnas existan en las tablas
- Que los tipos de dato sean compatibles (ej. no insertar texto en una columna numerica)
- Que las restricciones NOT NULL se cumplan

### 5.3 Explorar la referencia de sintaxis

1. Selecciona un dialecto
2. Haz clic en el boton "Referencia" (📖) en la barra de herramientas
3. En la pestaña "Referencia", selecciona el tipo de sentencia (SELECT, INSERT, etc.)
4. Revisa la plantilla de sintaxis y las keywords disponibles con sus roles:
   - **REQUIRED**: palabra clave obligatoria para esa sentencia
   - **OPTIONAL**: palabra clave opcional
   - **EXCLUSIVE**: exclusiva de este dialecto para esa sentencia

---

## 6. Sentencias SQL soportadas

### DML (Data Manipulation Language)
| Sentencia | Soporte |
|-----------|---------|
| SELECT | Completo: columnas, FROM, JOINs, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, OFFSET |
| INSERT | VALUES (multi-fila) y INSERT SELECT |
| UPDATE | SET multiples columnas, WHERE |
| DELETE | FROM, WHERE |

### DDL (Data Definition Language)
| Sentencia | Soporte |
|-----------|---------|
| CREATE TABLE | Columnas, tipos con parametros, constraints, IF NOT EXISTS |
| DROP TABLE | IF EXISTS |
| ALTER TABLE | ADD/DROP/MODIFY COLUMN |
| CREATE INDEX | UNIQUE, nombre, tabla, columnas |
| TRUNCATE | Vaciado de tabla |

### TCL (Transaction Control Language)
| Sentencia | Soporte |
|-----------|---------|
| BEGIN [WORK / TRANSACTION] | Inicio de transaccion |
| COMMIT [WORK] | Confirmacion |
| ROLLBACK [WORK] | Reversion |

### DCL (Data Control Language)
| Sentencia | Soporte |
|-----------|---------|
| GRANT | Privilegios SELECT, INSERT, etc. sobre objeto a usuario |
| REVOKE | Revocacion de privilegios |

---

## 7. Gestion de errores

### Tipos de error por fase

**Errores Lexicos (LEXER)**
- Caracter inesperado
- Cadena de texto sin cerrar
- Token invalido

**Errores Sintacticos (PARSER)**
- Sentencia no soportada
- Se esperaba X pero se encontro Y
- Falta de punto y coma
- Estructura incorrecta

**Errores Semanticos (SEMANTIC)**
- La tabla X no existe en el schema
- La columna Y no existe en la tabla Z
- Tipo incompatible: se esperaba INT pero el valor es VARCHAR
- Numero de columnas incorrecto en INSERT
- Columna duplicada en CREATE TABLE
- Columna NOT NULL con valor NULL

**Errores de Dialecto (DIALECT)**
- Caracteristicas no soportadas por el motor seleccionado
- Ej: MongoDB no soporta JOIN

### Visualizacion de errores

Los errores aparecen en:
1. El editor SQL como subrayado rojo (errores) o amarillo (advertencias) en la linea exacta
2. La pestaña "Errores" con lista detallada
3. La pestaña "Advertencias" con lista detallada
4. El indicador de fases muestra que fase fallo

---

## 8. Consejos de uso

- **Sin conexion**: Si solo quieres validar sintaxis basica, no necesitas configurar ninguna conexion. El analisis lexico y sintactico funcionan sin BD.
- **Cambio de dialecto**: Al cambiar de motor (ej. de MySQL a PostgreSQL), las keywords y sintaxis disponibles se actualizan automaticamente en la referencia.
- **Refresh de schema**: Si modificas la estructura de la BD (agregas tablas/columnas), usa el boton ↻ en el panel Schema para recargar.
- **Auto-completado**: El editor Monaco incluye resaltado de sintaxis SQL basico; las keywords del dialecto se inyectan para reconocimiento.
- **Multiples consultas**: El compilador procesa una sentencia a la vez. Si necesitas validar varias, hazlo individualmente.

---

## 9. Solucion de problemas

| Problema | Solucion |
|----------|----------|
| Al arrancar el backend no se conecta a MariaDB | Verifica que MariaDB este corriendo y que las credenciales en application.properties sean correctas |
| El frontend no se conecta al backend | Verifica que el backend este en puerto 8080. El proxy de Vite redirige /api a localhost:8080 |
| Error "Driver no encontrado" | Verifica las dependencias en pom.xml y ejecuta `./mvnw clean install` |
| La compilacion siempre muestra "Solo sintaxis" | Significa que no hay conexion activa — la validacion semantica requiere conexion |
| El schema no se carga | Verifica que la conexion sea correcta con el boton "Probar". Luego intenta reconectar |
| El editor no muestra errores en linea | Los marcadores se actualizan solo despues de compilar. Haz clic en "Compilar" |
| MongoDB no conecta | Verifica que MongoDB este en el puerto correcto (default 27017). MongoDB no usa JDBC |

---

## 10. Limitaciones conocidas

- No se ejecutan las consultas contra la BD real — solo se validan
- No soporta subconsultas en WHERE (columna IN (SELECT ...))
- No soporta CTEs (WITH ... AS)
- No soporta funciones de ventana complejas en el parser (aunque las keywords estan en el catalogo)
- MongoDB solo soporta operaciones SELECT en modo SQL-like
- El analisis semantico requiere conexion activa a la BD para validar contra schema real
- Sin conexion activa, las validaciones semanticas se omiten (solo advertencia)
- Una consulta por compilacion (no soporta batches con multiples sentencias separadas por ;)
```

