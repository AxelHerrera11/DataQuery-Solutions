<template>
  <div class="phases">
    <div v-for="phase in phases" :key="phase.label"
         :class="['phase', phase.status]">
      <span class="phase-icon">{{ phase.icon }}</span>
      <div class="phase-info">
        <span class="phase-label">{{ phase.label }}</span>
        <span class="phase-msg">{{ phase.message }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ result: Object })

const phases = computed(() => {
  const r = props.result
  if (!r) return [
    { label: 'Léxico',     icon: '○', status: 'idle', message: 'En espera' },
    { label: 'Sintáctico', icon: '○', status: 'idle', message: 'En espera' },
    { label: 'Semántico',  icon: '○', status: 'idle', message: 'En espera' },
  ]
  return [
    toPhase('Léxico',     r.lexerPhase),
    toPhase('Sintáctico', r.parserPhase),
    toPhase('Semántico',  r.semanticPhase),
  ]
})

function toPhase(label, p) {
  if (!p) return { label, icon: '○', status: 'idle',   message: 'En espera' }
  return {
    label,
    icon:    p.passed ? '✓' : '✗',
    status:  p.passed ? 'ok' : 'error',
    message: p.message,
  }
}
</script>

<style scoped>
.phases { display: flex; gap: 12px; }
.phase  { display: flex; align-items: center; gap: 10px; padding: 10px 14px;
          border-radius: 8px; border: 1px solid #2d3148; background: #1a1d27;
          flex: 1; }
.phase.ok    { border-color: #1a5c3a; background: #0d2b1e; }
.phase.error { border-color: #5c1a1a; background: #2b0d0d; }
.phase-icon  { font-size: 16px; font-weight: 700; }
.ok    .phase-icon { color: #34d399; }
.error .phase-icon { color: #f87171; }
.idle  .phase-icon { color: #475569; }
.phase-info  { display: flex; flex-direction: column; gap: 2px; }
.phase-label { font-size: 12px; font-weight: 600; color: #94a3b8; }
.phase-msg   { font-size: 11px; color: #64748b; }
</style>
