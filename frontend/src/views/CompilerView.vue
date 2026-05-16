<template>
  <div class="layout">

    <!-- ── SIDEBAR ──────────────────────────────────────────── -->
    <aside class="sidebar">

      <!-- Selector de dialecto -->
      <section class="panel">
        <h3 class="panel-title">Motor de BD</h3>
        <div class="dialect-grid">
          <button
            v-for="d in store.dialects" :key="d.name"
            :class="['dialect-btn', { active: store.dialect === d.name }]"
            :style="store.dialect === d.name ? { borderColor: d.brandColor } : {}"
            @click="store.dialect = d.name"
          >
            <span class="dialect-dot" :style="{ background: d.brandColor }" />
            {{ d.displayName }}
          </button>
        </div>
      </section>

      <!-- Conexiones -->
      <section class="panel">
        <h3 class="panel-title">Conexiones</h3>
        <ConnectionManager
          :connections="store.connections"
          :dialects="store.dialects"
          :selected="store.connectionId"
          @select="store.selectConnection($event)"
          @delete="store.deleteConnection($event)"
          @save="handleSave"
          @test="handleTest"
        />
      </section>

      <!-- Schema explorer -->
      <section class="panel flex-grow">
        <h3 class="panel-title">
          Schema
          <button v-if="store.connectionId" class="icon-btn"
                  title="Refrescar schema" @click="refreshSchema">↻</button>
        </h3>
        <SchemaExplorer :schema="store.schema" />
      </section>

    </aside>

    <!-- ── MAIN ─────────────────────────────────────────────── -->
    <main class="main">

      <!-- Toolbar -->
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="dialect-badge"
            :style="{ background: activeBrandColor + '22', color: activeBrandColor, borderColor: activeBrandColor + '55' }">
            {{ store.dialect }}
          </span>
          <span v-if="store.connectionId" class="conn-on">⚡ Conexión activa</span>
          <span v-else class="conn-off">Solo sintaxis</span>
        </div>
        <div class="toolbar-right">
          <button class="btn-secondary" @click="activeTab = 'syntax'">
            📖 Referencia
          </button>
          <button class="btn-compile" :disabled="store.loading" @click="store.compile()">
            <span v-if="store.loading" class="spinner" />
            <span v-else>▶ Compilar</span>
          </button>
        </div>
      </div>

      <!-- Editor SQL -->
      <SqlEditor
        v-model="store.sql"
        :errors="store.result?.errors ?? []"
        :warnings="store.result?.warnings ?? []"
      />

      <!-- Fases -->
      <PhaseStatus :result="store.result" />

      <!-- Panel de resultados con tab de Referencia agregada -->
      <div class="results-section">
        <div class="results-tabs">
          <button v-for="tab in tabs" :key="tab.id"
            :class="['tab', { active: activeTab === tab.id }]"
            @click="activeTab = tab.id">
            {{ tab.label }}
            <span v-if="tab.count" class="tab-count" :class="tab.countClass">
              {{ tab.count }}
            </span>
          </button>
        </div>

        <div class="tab-content">

          <!-- Errores -->
          <template v-if="activeTab === 'errors'">
            <ErrorPanel
              :items="store.result?.errors ?? []"
              :show-ok="store.result?.valid === true"
            />
          </template>

          <!-- Advertencias -->
          <template v-if="activeTab === 'warnings'">
            <ErrorPanel
              :items="store.result?.warnings ?? []"
              :show-ok="store.result != null && !store.hasWarnings"
            />
          </template>

          <!-- AST -->
          <template v-if="activeTab === 'ast'">
            <pre v-if="store.result?.astJson" class="ast-view">{{
              JSON.stringify(JSON.parse(store.result.astJson), null, 2)
            }}</pre>
            <div v-else class="empty-tab">Sin AST disponible</div>
          </template>

          <!-- Referencia de sintaxis desde BD -->
          <template v-if="activeTab === 'syntax'">
            <SyntaxHelper :dialect="store.dialect" />
          </template>

        </div>
      </div>

    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useCompilerStore } from '../stores/compiler.js'
import { connectionApi }    from '../services/api.js'

import SqlEditor         from '../components/SqlEditor.vue'
import PhaseStatus       from '../components/PhaseStatus.vue'
import ErrorPanel        from '../components/ErrorPanel.vue'
import ConnectionManager from '../components/ConnectionManager.vue'
import SchemaExplorer    from '../components/SchemaExplorer.vue'
import SyntaxHelper      from '../components/SyntaxHelper.vue'

const store     = useCompilerStore()
const activeTab = ref('errors')

onMounted(async () => {
  await store.loadDialects()
  await store.loadConnections()
})

const activeBrandColor = computed(() =>
  store.dialects.find(d => d.name === store.dialect)?.brandColor ?? '#7c6fff'
)

const tabs = computed(() => [
  { id: 'errors',   label: 'Errores',      count: store.result?.errors?.length   || null, countClass: 'count-error' },
  { id: 'warnings', label: 'Advertencias', count: store.result?.warnings?.length || null, countClass: 'count-warn'  },
  { id: 'ast',      label: 'AST',          count: null },
  { id: 'syntax',   label: '📖 Referencia', count: null },
])

async function handleSave(form) { await store.saveConnection(form) }
async function handleTest(form, cb) { cb(await store.testConnection(form)) }
async function refreshSchema() {
  if (!store.connectionId) return
  store.schema = await connectionApi.getSchema(store.connectionId)
}
</script>

<style scoped>
.layout { display: grid; grid-template-columns: 280px 1fr; height: calc(100vh - 52px); overflow: hidden; }

/* Sidebar */
.sidebar { display: flex; flex-direction: column; overflow-y: auto;
           border-right: 1px solid #2d3148; background: #13151f; }
.panel   { padding: 16px; border-bottom: 1px solid #2d3148; }
.panel.flex-grow { flex: 1; overflow-y: auto; }
.panel-title { font-size: 11px; font-weight: 600; color: #475569;
               letter-spacing: .08em; text-transform: uppercase;
               margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
.icon-btn { background: none; border: none; color: #475569; cursor: pointer;
            font-size: 14px; padding: 2px 4px; border-radius: 4px; }
.icon-btn:hover { color: #7c6fff; }

.dialect-grid { display: flex; flex-direction: column; gap: 5px; }
.dialect-btn  { display: flex; align-items: center; gap: 8px; padding: 7px 10px;
                border-radius: 7px; border: 1px solid #2d3148; background: transparent;
                color: #94a3b8; cursor: pointer; font-size: 13px; text-align: left; }
.dialect-btn:hover  { background: #1a1d27; color: #e2e8f0; }
.dialect-btn.active { background: #1a1d27; color: #e2e8f0; }
.dialect-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

/* Main */
.main { display: flex; flex-direction: column; gap: 14px; padding: 20px;
        overflow-y: auto; background: #0f1117; }

.toolbar       { display: flex; align-items: center; justify-content: space-between; }
.toolbar-left  { display: flex; align-items: center; gap: 8px; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }

.dialect-badge { font-size: 11px; font-weight: 700; padding: 3px 10px;
                 border-radius: 20px; border: 1px solid; letter-spacing: .05em; }
.conn-on  { font-size: 12px; color: #34d399; padding: 3px 8px;
            background: #0d2b1e; border: 1px solid #1a5c3a; border-radius: 20px; }
.conn-off { font-size: 12px; color: #64748b; padding: 3px 8px;
            background: #1a1d27; border: 1px solid #2d3148; border-radius: 20px; }

.btn-compile { display: flex; align-items: center; gap: 8px; padding: 9px 22px;
               border-radius: 8px; border: none; background: #7c6fff; color: #fff;
               font-size: 14px; font-weight: 600; cursor: pointer; }
.btn-compile:hover:not(:disabled) { background: #6b5ce7; }
.btn-compile:disabled { opacity: .5; cursor: not-allowed; }

.btn-secondary { padding: 8px 14px; border-radius: 8px; border: 1px solid #2d3148;
                 background: transparent; color: #94a3b8; cursor: pointer; font-size: 13px; }
.btn-secondary:hover { border-color: #7c6fff; color: #c4b5fd; }

.spinner { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,.3);
           border-top-color: #fff; border-radius: 50%;
           animation: spin .7s linear infinite; display: inline-block; }
@keyframes spin { to { transform: rotate(360deg); } }

/* Results */
.results-section { background: #1a1d27; border: 1px solid #2d3148;
                   border-radius: 10px; overflow: hidden; }
.results-tabs    { display: flex; border-bottom: 1px solid #2d3148; }
.tab { padding: 9px 16px; border: none; background: transparent; color: #64748b;
       cursor: pointer; font-size: 13px; display: flex; align-items: center; gap: 6px;
       border-bottom: 2px solid transparent; }
.tab.active         { color: #e2e8f0; border-bottom-color: #7c6fff; }
.tab:hover:not(.active) { color: #94a3b8; }
.tab-count { font-size: 10px; padding: 1px 5px; border-radius: 10px; font-weight: 700; }
.count-error { background: #5c1a1a; color: #f87171; }
.count-warn  { background: #5c4200; color: #fbbf24; }
.tab-content { padding: 14px; min-height: 100px; }
.ast-view { background: #0f1117; border: 1px solid #2d3148; border-radius: 8px;
            padding: 14px; font-size: 12px; color: #7c6fff; overflow: auto;
            max-height: 300px; white-space: pre; font-family: 'Cascadia Code', monospace; }
.empty-tab { color: #475569; font-size: 13px; padding: 8px; }
</style>
