import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

export const compilerApi = {
  compile: (sql, dialect, connectionId = null) =>
    http.post('/compile', { sql, dialect, connectionId }).then(r => r.data),
}

export const dialectApi = {
  getAll: () => http.get('/dialects').then(r => r.data),
}

export const connectionApi = {
  getAll:    ()       => http.get('/connections').then(r => r.data),
  save:      (body)   => http.post('/connections', body).then(r => r.data),
  delete:    (id)     => http.delete(`/connections/${id}`),
  test:      (body)   => http.post('/connections/test', body).then(r => r.data),
  getSchema: (id)     => http.get(`/connections/${id}/schema`).then(r => r.data),
}
