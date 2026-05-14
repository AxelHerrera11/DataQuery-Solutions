<template>
  <div class="app">
    <header class="app-header">
      <span class="app-logo">DataQuery</span>
      <span class="app-subtitle">Compilador SQL Multi-Motor</span>
    </header>
    <CompilerView />
  </div>
</template>

<script setup>
import CompilerView from './views/CompilerView.vue'
</script>

<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: 'Segoe UI', system-ui, sans-serif;
  background: #0f1117;
  color: #e2e8f0;
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  background: #1a1d27;
  border-bottom: 1px solid #2d3148;
}

.app-logo {
  font-size: 18px;
  font-weight: 700;
  color: #7c6fff;
  letter-spacing: -0.5px;
}

.app-subtitle {
  font-size: 13px;
  color: #64748b;
}
</style>
