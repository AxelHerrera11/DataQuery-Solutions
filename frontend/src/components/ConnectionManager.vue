<template>
  <div class="conn-manager">
    <!-- Lista de conexiones guardadas -->
    <div v-for="conn in connections" :key="conn.id"
         :class="['conn-item', { active: selected === conn.id }]"
         @click="$emit('select', conn.id)">
      <span class="dot" :style="{ background: conn.brandColor }" />
      <div class="conn-info">
        <span class="conn-name">{{ conn.name }}</span>
        <span class="conn-sub">{{ conn.host }}:{{ conn.port }} / {{ conn.database }}</span>
      </div>
      <button class="del-btn" @click.stop="$emit('delete', conn.id)">✕</button>
    </div>

    <!-- Formulario nueva conexión -->
    <div v-if="showForm" class="form">
      <select v-model="form.dialect" class="field">
        <option v-for="d in dialects" :key="d.name" :value="d.name">{{ d.displayName }}</option>
      </select>
      <input v-model="form.name"     class="field" placeholder="Nombre de la conexión" />
      <div class="row2">
        <input v-model="form.host"   class="field" placeholder="Host" />
        <input v-model.number="form.port" class="field narrow" type="number" placeholder="Puerto" />
      </div>
      <input v-model="form.database" class="field" placeholder="Base de datos" />
      <input v-model="form.username" class="field" placeholder="Usuario" />
      <input v-model="form.password" class="field" type="password" placeholder="Contraseña" />
      <div class="form-actions">
        <button class="btn-test"   @click="doTest">Probar</button>
        <button class="btn-save"   @click="doSave">Guardar</button>
        <button class="btn-cancel" @click="showForm = false">Cancelar</button>
      </div>
      <div v-if="testMsg" :class="['test-result', testMsg.ok ? 'ok' : 'fail']">
        {{ testMsg.text }}
      </div>
    </div>

    <button v-if="!showForm" class="btn-add" @click="openForm">+ Nueva conexión</button>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
const props = defineProps({ connections: Array, dialects: Array, selected: String })
const emit  = defineEmits(['select', 'delete', 'save'])

const showForm = ref(false)
const testMsg  = ref(null)
const form     = reactive({ dialect:'MYSQL', name:'', host:'localhost', port:3306,
                            database:'', username:'', password:'' })

function openForm() {
  const d = props.dialects?.[0]
  if (d) { form.dialect = d.name; form.port = d.defaultPort }
  showForm.value = true
  testMsg.value  = null
}

async function doTest() {
  emit('test', { ...form }, (r) => {
    testMsg.value = r.success
      ? { ok: true,  text: `Conexión OK — ${r.serverVersion}` }
      : { ok: false, text: `Error: ${r.message}` }
  })
}

function doSave() {
  emit('save', { ...form })
  showForm.value = false
}
</script>

<style scoped>
.conn-manager { display: flex; flex-direction: column; gap: 6px; }
.conn-item { display:flex; align-items:center; gap:10px; padding:8px 10px;
             border-radius:8px; border:1px solid #2d3148; cursor:pointer;
             background:#1a1d27; }
.conn-item.active { border-color:#7c6fff; background:#1e1b3a; }
.conn-item:hover  { border-color:#3d4165; }
.dot   { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
.conn-info { flex:1; min-width:0; }
.conn-name { display:block; font-size:13px; color:#e2e8f0; }
.conn-sub  { display:block; font-size:11px; color:#64748b; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.del-btn   { background:none; border:none; color:#475569; cursor:pointer; padding:4px;
             border-radius:4px; font-size:13px; }
.del-btn:hover { color:#f87171; background:#2b0d0d; }
.form      { display:flex; flex-direction:column; gap:8px; padding:12px;
             background:#1a1d27; border-radius:8px; border:1px solid #2d3148; }
.field     { background:#0f1117; border:1px solid #2d3148; color:#e2e8f0;
             border-radius:6px; padding:7px 10px; font-size:13px; width:100%; }
.field:focus { outline:none; border-color:#7c6fff; }
.row2      { display:grid; grid-template-columns:1fr 80px; gap:8px; }
.narrow    { width:100%; }
.form-actions { display:flex; gap:8px; }
.btn-test  { padding:7px 14px; border-radius:6px; border:1px solid #2d3148;
             background:transparent; color:#94a3b8; cursor:pointer; font-size:13px; }
.btn-save  { padding:7px 14px; border-radius:6px; border:none;
             background:#7c6fff; color:#fff; cursor:pointer; font-size:13px; }
.btn-cancel{ padding:7px 14px; border-radius:6px; border:1px solid #2d3148;
             background:transparent; color:#64748b; cursor:pointer; font-size:13px; }
.btn-add   { padding:8px; border-radius:8px; border:1px dashed #2d3148;
             background:transparent; color:#7c6fff; cursor:pointer; font-size:13px;
             width:100%; }
.btn-add:hover { background:#1a1d27; }
.test-result    { padding:8px; border-radius:6px; font-size:12px; }
.test-result.ok   { background:#0d2b1e; border:1px solid #1a5c3a; color:#34d399; }
.test-result.fail { background:#2b0d0d; border:1px solid #5c1a1a; color:#f87171; }
</style>
