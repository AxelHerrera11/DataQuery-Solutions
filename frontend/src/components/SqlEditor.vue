<template>
  <div class="editor-wrap">
    <div ref="editorEl" class="editor-mount" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps({
  modelValue: { type: String, default: '' },
  errors:     { type: Array,  default: () => [] },
  warnings:   { type: Array,  default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const editorEl = ref(null)
let editor     = null
let decorIds   = []

onMounted(() => {
  editor = monaco.editor.create(editorEl.value, {
    value:              props.modelValue,
    language:           'sql',
    theme:              'vs-dark',
    fontSize:           14,
    lineHeight:         22,
    minimap:            { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout:    true,
    padding:            { top: 12 },
  })

  editor.onDidChangeModelContent(() => {
    emit('update:modelValue', editor.getValue())
  })

  applyMarkers()
})

onBeforeUnmount(() => editor?.dispose())

watch(() => props.modelValue, (val) => {
  if (editor && editor.getValue() !== val) editor.setValue(val)
})

watch([() => props.errors, () => props.warnings], applyMarkers)

function applyMarkers() {
  if (!editor) return
  const model = editor.getModel()
  const markers = [
    ...props.errors.map(e   => makeMarker(e, monaco.MarkerSeverity.Error)),
    ...props.warnings.map(w => makeMarker(w, monaco.MarkerSeverity.Warning)),
  ]
  monaco.editor.setModelMarkers(model, 'compiler', markers)
}

function makeMarker(item, severity) {
  return {
    startLineNumber: item.line   || 1,
    startColumn:     item.column || 1,
    endLineNumber:   item.line   || 1,
    endColumn:       (item.column || 1) + 10,
    message:         `[${item.phase}] ${item.message}`,
    severity,
  }
}
</script>

<style scoped>
.editor-wrap { border-radius: 8px; overflow: hidden; border: 1px solid #2d3148; }
.editor-mount { height: 280px; }
</style>
