<template>
  <div v-if="items.length" class="error-panel">
    <div v-for="(item, i) in items" :key="i"
         :class="['error-item', item.severity.toLowerCase()]">
      <span class="badge">{{ item.phase }}</span>
      <span class="loc" v-if="item.line">L{{ item.line }}:C{{ item.column }}</span>
      <span class="msg">{{ item.message }}</span>
    </div>
  </div>
  <div v-else-if="showOk" class="ok-banner">
    ✓ Query válida — sin errores ni advertencias
  </div>
</template>

<script setup>
defineProps({
  items:  { type: Array,   default: () => [] },
  showOk: { type: Boolean, default: false },
})
</script>

<style scoped>
.error-panel { display: flex; flex-direction: column; gap: 6px; }
.error-item  { display: flex; align-items: flex-start; gap: 8px; padding: 8px 12px;
               border-radius: 6px; font-size: 13px; }
.error-item.error   { background: #2b0d0d; border: 1px solid #5c1a1a; }
.error-item.warning { background: #2b2000; border: 1px solid #5c4200; }
.badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px;
         background: #1a1d27; color: #7c6fff; white-space: nowrap; }
.loc   { font-size: 11px; color: #94a3b8; white-space: nowrap; }
.msg   { color: #e2e8f0; flex: 1; }
.error-item.warning .msg { color: #fbbf24; }
.ok-banner { padding: 12px; background: #0d2b1e; border: 1px solid #1a5c3a;
             border-radius: 8px; color: #34d399; font-size: 13px; }
</style>
