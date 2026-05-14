<template>
  <div class="schema-explorer">
    <div v-if="!schema" class="empty">Sin conexión activa</div>
    <template v-else>
      <div class="db-header">
        <span class="db-name">{{ schema.databaseName }}</span>
        <span class="db-dialect">{{ schema.dialectName }}</span>
      </div>
      <div v-for="table in schema.tables" :key="table.name" class="table-node">
        <button class="table-header" @click="toggle(table.name)">
          <span class="caret">{{ open.has(table.name) ? '▾' : '▸' }}</span>
          <span class="tname">{{ table.name }}</span>
          <span class="col-count">{{ table.columns.length }} cols</span>
        </button>
        <div v-if="open.has(table.name)" class="columns">
          <div v-for="col in table.columns" :key="col.name" class="col-row">
            <span v-if="col.primaryKey" class="pk-badge">PK</span>
            <span class="cname">{{ col.name }}</span>
            <span class="ctype">{{ col.type }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
defineProps({ schema: Object })
const open = ref(new Set())
function toggle(name) {
  open.value.has(name) ? open.value.delete(name) : open.value.add(name)
}
</script>

<style scoped>
.schema-explorer { font-size: 12px; }
.empty { color: #475569; padding: 8px; }
.db-header { display: flex; align-items: center; gap: 8px; padding: 8px 0; margin-bottom: 4px;
             border-bottom: 1px solid #2d3148; }
.db-name   { font-weight: 600; color: #e2e8f0; }
.db-dialect{ padding: 1px 6px; border-radius: 4px; background: #2d3148; color: #7c6fff; font-size: 10px; }
.table-node{ margin-bottom: 4px; }
.table-header{ width:100%; display:flex; align-items:center; gap:6px; padding:5px 6px;
               border-radius:6px; border:none; background:transparent; cursor:pointer;
               color:#94a3b8; text-align:left; }
.table-header:hover { background:#1a1d27; }
.caret  { color:#475569; width:10px; }
.tname  { color:#c4b5fd; font-weight:500; flex:1; }
.col-count{ color:#475569; font-size:10px; }
.columns{ padding:2px 0 4px 18px; }
.col-row{ display:flex; align-items:center; gap:6px; padding:3px 0; }
.pk-badge{ font-size:9px; padding:1px 4px; border-radius:3px; background:#2d1f5e; color:#a78bfa; }
.cname  { color:#e2e8f0; flex:1; }
.ctype  { color:#64748b; }
</style>
