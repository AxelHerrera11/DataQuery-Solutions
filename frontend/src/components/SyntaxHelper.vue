<template>
  <div class="syntax-helper">

    <!-- Selector de sentencia -->
    <div class="stmt-tabs">
      <button
        v-for="s in statements"
        :key="s.statementName"
        :class="['stmt-tab', { active: selected === s.statementName }]"
        @click="select(s.statementName)"
      >
        {{ s.statementName.replace('_', ' ') }}
      </button>
    </div>

    <!-- Template de sintaxis -->
    <div v-if="current && current.supported" class="syntax-block">
      <div class="syntax-header">
        <span class="syntax-label">Sintaxis {{ props.dialect === 'MONGODB' ? 'nativa MongoDB' : 'SQL' }}</span>
        <span v-if="props.dialect === 'MONGODB'" class="native-badge">db.</span>
      </div>
      <pre class="syntax-template">{{ current.syntaxTemplate }}</pre>
      <div v-if="current.notes" class="syntax-notes">
        <span class="notes-icon">💡</span> {{ current.notes }}
      </div>
    </div>
    <div v-else-if="current && !current.supported" class="syntax-block unsupported">
      <div class="syntax-label">{{ selected.replace('_', ' ') }}</div>
      <p class="unsupported-text">{{ current.notes || 'No soportado para ' + props.dialect }}</p>
    </div>

    <!-- Keywords de la sentencia seleccionada -->
    <div v-if="keywords.length" class="kw-section">
      <div class="syntax-label">Keywords de {{ selected }}</div>
      <div class="kw-grid">
        <div
          v-for="kw in keywords"
          :key="kw.word"
          :class="['kw-chip', kw.role.toLowerCase()]"
          :title="kw.notes ?? kw.positionHint"
        >
          <span class="kw-word">{{ kw.word }}</span>
          <span class="kw-role">{{ kw.role }}</span>
        </div>
      </div>
    </div>

    <div v-if="!statements.length" class="empty">
      Selecciona un dialecto para ver la sintaxis disponible
    </div>

  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { catalogApi } from '../services/api.js'

const props = defineProps({ dialect: String })

const statements = ref([])
const keywords   = ref([])
const selected   = ref(null)
const current    = ref(null)

watch(() => props.dialect, async (d) => {
  if (!d) return
  statements.value = await catalogApi.getSupportedStatements(d)
  keywords.value   = []
  current.value    = null
  selected.value   = null
  if (statements.value.length) select(statements.value[0].statementName)
}, { immediate: true })

async function select(name) {
  selected.value = name
  current.value  = statements.value.find(s => s.statementName === name) ?? null
  keywords.value = await catalogApi.getStatementKeywords(props.dialect, name)
}
</script>

<style scoped>
.syntax-helper { display: flex; flex-direction: column; gap: 12px; }

.stmt-tabs { display: flex; flex-wrap: wrap; gap: 6px; }
.stmt-tab  {
  padding: 5px 12px; border-radius: 20px; border: 1px solid #2d3148;
  background: transparent; color: #64748b; cursor: pointer; font-size: 12px;
}
.stmt-tab:hover  { border-color: #7c6fff; color: #c4b5fd; }
.stmt-tab.active { background: #1e1b3a; border-color: #7c6fff; color: #c4b5fd; }

.syntax-label { font-size: 10px; font-weight: 700; color: #475569;
                letter-spacing: .08em; text-transform: uppercase; margin-bottom: 6px; }

.syntax-block { background: #13151f; border: 1px solid #2d3148;
                border-radius: 8px; padding: 12px; }

.syntax-template {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 11px; color: #a78bfa; white-space: pre-wrap;
  line-height: 1.7; margin: 0;
}

.syntax-notes { font-size: 11px; color: #64748b; margin-top: 8px; }

.kw-section { background: #13151f; border: 1px solid #2d3148;
              border-radius: 8px; padding: 12px; }

.kw-grid { display: flex; flex-wrap: wrap; gap: 6px; }

.kw-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: 6px; border: 1px solid #2d3148;
  font-size: 11px;
}
.kw-chip.required  { background: #1a1d27; border-color: #3d4165; }
.kw-chip.optional  { background: #0d1f2d; border-color: #1a3a5c; }
.kw-chip.exclusive { background: #1a0d2b; border-color: #3d1a5c; }

.kw-word { color: #e2e8f0; font-weight: 600; }
.kw-role {
  font-size: 9px; padding: 1px 5px; border-radius: 3px;
  font-weight: 700; text-transform: uppercase;
}
.required  .kw-role { background: #1a5c3a; color: #34d399; }
.optional  .kw-role { background: #1a3a5c; color: #60a5fa; }
.exclusive .kw-role { background: #3d1a5c; color: #a78bfa; }

.empty { color: #475569; font-size: 12px; padding: 8px; }

.syntax-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.syntax-header .syntax-label { margin-bottom: 0; }
.native-badge {
  font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 4px;
  background: #1a3a1a; color: #4ade80; border: 1px solid #2d6a2d;
  letter-spacing: .05em;
}
.unsupported { opacity: .6; }
.unsupported-text { color: #64748b; font-size: 12px; margin: 0; }
.notes-icon { font-size: 12px; }
</style>
