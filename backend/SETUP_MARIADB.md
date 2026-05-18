# Setup MariaDB en Mac — DataQuery Solutions

## 1. Verificar que MariaDB está corriendo

```bash
# Si usas Homebrew:
brew services list | grep mariadb

# Iniciar si está detenido:
brew services start mariadb
```

## 2. Crear la base de datos

Conectarte a MariaDB y crear la BD del compilador:

```bash
# Conectar (sin password si es instalación nueva)
mariadb -u root

# O si tiene password:
mariadb -u root -p
```

Una vez dentro del cliente MariaDB:

```sql
CREATE DATABASE dataquery_compiler
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Verificar que se creó
SHOW DATABASES;

-- Salir
EXIT;
```

## 3. Configurar credenciales en Spring Boot

Editar `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/dataquery_compiler?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=        ← tu password aquí (vacío si no tiene)
```

## 4. Arrancar el backend

```bash
cd backend
./mvnw spring-boot:run
```

Al arrancar, Spring Boot ejecuta automáticamente:
1. `schema.sql` → crea las 7 tablas
2. `data.sql`   → inserta keywords, tipos y sintaxis de los 4 motores

Verás en consola:
```
[DialectLoader] 4 dialectos cargados desde BD: [MYSQL, POSTGRESQL, SQLSERVER, MONGODB]
```

## 5. Verificar que los datos se insertaron

```bash
mariadb -u root dataquery_compiler -e "
  SELECT d.name, COUNT(k.id) AS keywords
  FROM dialect d
  LEFT JOIN keyword k ON k.dialect_id = d.id
  GROUP BY d.name;
"
```

Resultado esperado:
```
+------------+----------+
| name       | keywords |
+------------+----------+
| MONGODB    |       45 |
| MYSQL      |       95 |
| POSTGRESQL |      110 |
| SQLSERVER  |      100 |
+------------+----------+
```

## 6. Arrancar el frontend

```bash
cd frontend
npm install
npm run dev
```

Abrir: http://localhost:5173

---

## Notas importantes

### ¿Puedo conectar MySQL real desde la app?
Sí. MariaDB es compatible con el protocolo MySQL. El driver
`mariadb-java-client` puede conectar tanto a MariaDB como a MySQL.
Si necesitas conectar específicamente a un MySQL puro, descomenta
el bloque `mysql-connector-j` en el `pom.xml`.

### ¿Los scripts SQL funcionan en MariaDB?
Sí. `schema.sql` y `data.sql` usan sintaxis estándar MySQL/MariaDB.
La única diferencia es `ENGINE=InnoDB` que MariaDB soporta nativamente.

### Puerto diferente al 3306
Si tu MariaDB usa otro puerto, ajústalo en `application.properties`:
```properties
spring.datasource.url=jdbc:mariadb://localhost:3307/dataquery_compiler...
```
