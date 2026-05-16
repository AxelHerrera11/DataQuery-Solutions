import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

// ── Compilador ────────────────────────────────────────────────────────
export const compilerApi = {
  compile: (sql, dialect, connectionId = null) =>
    http.post('/compile', { sql, dialect, connectionId }).then(r => r.data),
}

// ── Dialectos ─────────────────────────────────────────────────────────
export const dialectApi = {
  getAll: () => http.get('/dialects').then(r => r.data),
}

// ── Conexiones ────────────────────────────────────────────────────────
export const connectionApi = {
  getAll:    ()     => http.get('/connections').then(r => r.data),
  save:      (body) => http.post('/connections', body).then(r => r.data),
  delete:    (id)   => http.delete(`/connections/${id}`),
  test:      (body) => http.post('/connections/test', body).then(r => r.data),
  getSchema: (id)   => http.get(`/connections/${id}/schema`).then(r => r.data),
}

// ── Catálogo de keywords/tipos/sintaxis ───────────────────────────────
export const catalogApi = {
  // Todas las keywords de un dialecto
  getKeywords: (dialect) =>
    http.get(`/catalog/${dialect}/keywords`).then(r => r.data),

  // Keywords filtradas por tokenType (FUNCTION, RESERVED, DATA_TYPE...)
  getKeywordsByType: (dialect, type) =>
    http.get(`/catalog/${dialect}/keywords`, { params: { type } }).then(r => r.data),

  // Keywords que aplican a una sentencia (SELECT, INSERT...)
  getKeywordsByStatement: (dialect, statement) =>
    http.get(`/catalog/${dialect}/keywords`, { params: { statement } }).then(r => r.data),

  // Mappings de tipos de dato nativos → abstractos
  getDataTypes: (dialect) =>
    http.get(`/catalog/${dialect}/types`).then(r => r.data),

  // Todas las sentencias soportadas con su template de sintaxis
  getSupportedStatements: (dialect) =>
    http.get(`/catalog/${dialect}/syntax`).then(r => r.data),

  // Template de una sentencia específica
  getSyntax: (dialect, statement) =>
    http.get(`/catalog/${dialect}/syntax/${statement}`).then(r => r.data),

  // Keywords con rol y posición para una sentencia
  getStatementKeywords: (dialect, statement) =>
    http.get(`/catalog/${dialect}/syntax/${statement}/keywords`).then(r => r.data),
}
