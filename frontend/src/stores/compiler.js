import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { compilerApi, dialectApi, connectionApi } from '../services/api.js'

export const useCompilerStore = defineStore('compiler', () => {
  // Estado
  const sql          = ref('SELECT * FROM usuarios WHERE edad > 18;')
  const dialect      = ref('MYSQL')
  const connectionId = ref(null)
  const result       = ref(null)
  const loading      = ref(false)
  const dialects     = ref([])
  const connections  = ref([])
  const schema       = ref(null)

  // Getters
  const hasErrors   = computed(() => result.value?.errors?.length > 0)
  const hasWarnings = computed(() => result.value?.warnings?.length > 0)
  const isValid     = computed(() => result.value?.valid === true)

  // Acciones
  async function compile() {
    loading.value = true
    result.value  = null
    try {
      result.value = await compilerApi.compile(sql.value, dialect.value, connectionId.value)
    } catch (e) {
      result.value = {
        valid: false,
        errors: [{ phase: 'NETWORK', line: 0, column: 0, message: e.message, severity: 'ERROR' }],
        warnings: []
      }
    } finally {
      loading.value = false
    }
  }

  async function loadDialects() {
    dialects.value = await dialectApi.getAll()
  }

  async function loadConnections() {
    connections.value = await connectionApi.getAll()
  }

  async function saveConnection(form) {
    const saved = await connectionApi.save(form)
    connections.value.push(saved)
    return saved
  }

  async function deleteConnection(id) {
    await connectionApi.delete(id)
    connections.value = connections.value.filter(c => c.id !== id)
    if (connectionId.value === id) {
      connectionId.value = null
      schema.value = null
    }
  }

  async function selectConnection(id) {
    connectionId.value = id
    schema.value = null
    if (id) {
      schema.value = await connectionApi.getSchema(id)
    }
  }

  async function testConnection(form) {
    return connectionApi.test(form)
  }

  return {
    sql, dialect, connectionId, result, loading,
    dialects, connections, schema,
    hasErrors, hasWarnings, isValid,
    compile, loadDialects, loadConnections,
    saveConnection, deleteConnection, selectConnection, testConnection
  }
})
