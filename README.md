# DataQuery Solutions — Compilador SQL Multi-Motor
**Universidad Mariano Gálvez · Curso de Compiladores 2026**

## Stack
- **Backend**: Java 21 + Spring Boot 3.2 (Maven)
- **Frontend**: Vue 3 + Vite + Monaco Editor + Pinia

## Estructura
```
DataQuery-Solutions/
├── backend/     ← Spring Boot (puerto 8080)
└── frontend/    ← Vue 3 + Vite (puerto 5173)
```

## Cómo ejecutar

### Backend
```bash
cd backend
./mvnw spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

Abrir: http://localhost:5173

## Motores soportados
| Motor       | Puerto | Driver         |
|-------------|--------|----------------|
| MySQL 8.x   | 3306   | JDBC           |
| PostgreSQL  | 5432   | JDBC           |
| SQL Server  | 1433   | JDBC           |
| MongoDB     | 27017  | Driver nativo  |

## Pipeline de compilación
```
SQL → Léxico (Lexer) → Sintáctico (Parser) → Semántico (SemanticAnalyzer) → Resultado
```
- Sin conexión: valida léxico + sintaxis únicamente
- Con conexión: valida también contra el schema real de la BD

## Endpoints REST
| Método | URL                            | Descripción                    |
|--------|--------------------------------|--------------------------------|
| POST   | /api/compile                   | Compilar una query SQL         |
| GET    | /api/dialects                  | Listar motores disponibles     |
| GET    | /api/connections               | Listar conexiones guardadas    |
| POST   | /api/connections               | Guardar nueva conexión         |
| POST   | /api/connections/test          | Probar conexión sin guardar    |
| DELETE | /api/connections/{id}          | Eliminar conexión              |
| GET    | /api/connections/{id}/schema   | Obtener schema real de la BD   |
