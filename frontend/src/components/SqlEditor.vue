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
  dialect:    { type: String, default: 'MYSQL' },
  keywords:   { type: Array,  default: () => [] },
  errors:     { type: Array,  default: () => [] },
  warnings:   { type: Array,  default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const editorEl = ref(null)
let editor     = null
let decorIds   = []

// ── MongoDB Monaco Language Definition ─────────────────────────────────
function registerMongoLanguage() {
  if (monaco.languages.getLanguages().some(l => l.id === 'mongodb')) return

  monaco.languages.register({ id: 'mongodb' })

  monaco.languages.setMonarchTokensProvider('mongodb', {
    defaultToken: '',
    tokenPostfix: '.mongo',

    brackets: [
      { open: '{', close: '}', token: 'delimiter.curly' },
      { open: '[', close: ']', token: 'delimiter.square' },
      { open: '(', close: ')', token: 'delimiter.parenthesis' },
    ],

    keywords: [
      'db', 'find', 'findOne', 'findOneAndUpdate', 'findOneAndDelete',
      'aggregate', 'insertOne', 'insertMany', 'updateOne', 'updateMany',
      'deleteOne', 'deleteMany', 'replaceOne', 'countDocuments',
      'estimatedDocumentCount', 'distinct', 'bulkWrite', 'createIndex',
      'dropIndex', 'drop', 'renameCollection', 'watch', 'mapReduce',
      'collection', 'getCollection',
    ],

    typeKeywords: [
      'ObjectId', 'ISODate', 'NumberLong', 'NumberDecimal', 'BinData',
      'UUID', 'HexData', 'MD5', 'Timestamp',
    ],

    operators: [
      '$match', '$group', '$sort', '$project', '$lookup', '$unwind',
      '$limit', '$skip', '$sample', '$facet', '$bucket', '$bucketAuto',
      '$count', '$replaceRoot', '$replaceWith', '$addFields', '$set',
      '$unset', '$out', '$merge', '$geoNear', '$graphLookup',
      '$gt', '$gte', '$lt', '$lte', '$eq', '$ne', '$in', '$nin',
      '$exists', '$regex', '$options', '$text', '$search', '$language',
      '$expr', '$not', '$nor', '$and', '$or', '$all', '$elemMatch',
      '$size', '$type', '$slice', '$mod', '$comment', '$natural',
      '$currentDate', '$inc', '$min', '$max', '$mul', '$rename',
      '$setOnInsert', '$unset', '$push', '$addToSet', '$each',
      '$position', '$sort', '$pop', '$pull', '$pullAll',
      '$sum', '$avg', '$first', '$last', '$max', '$min', '$push', '$addToSet',
      '$stdDevPop', '$stdDevSamp', '$accumulator', '$function',
      '$cond', '$ifNull', '$switch', '$range', '$reduce',
      '$map', '$filter', '$zip', '$anyElementTrue', '$allElementsTrue',
      '$setDifference', '$setEquals', '$setIntersection', '$setUnion',
      '$dateToString', '$dateFromParts', '$dateFromString', '$dayOfYear',
      '$dayOfMonth', '$dayOfWeek', '$year', '$month', '$week', '$hour',
      '$minute', '$second', '$millisecond', '$isoDayOfWeek', '$isoWeek',
      '$isoWeekYear',
    ],

    tokenizer: {
      root: [
        { include: '@whitespace' },
        { include: '@comments' },
        { include: '@numbers' },
        { include: '@strings' },
        { include: '@dollarOperators' },
        { include: '@keywords' },
        { include: '@typeKeywords' },
        { include: '@brackets' },
        [/[{}()\[\]]/, '@brackets'],
        [/[=><!]+/, 'delimiter'],
        [/[.,;]/, 'delimiter'],
        [/[+\-*/%&|^~!]/, 'operator'],
        [/[a-zA-Z_$][a-zA-Z0-9_$]*/, 'identifier'],
      ],

      whitespace: [
        [/[ \t\r\n]+/, 'white'],
      ],

      comments: [
        [/\/\/.*$/, 'comment'],
        [/\/\*/, 'comment', '@commentBlock'],
      ],
      commentBlock: [
        [/[^/*]+/, 'comment'],
        [/\*\//, 'comment', '@pop'],
        [/[/*]/, 'comment'],
      ],

      numbers: [
        [/\d+\.\d*([eE][+\-]?\d+)?/, 'number.float'],
        [/0[xX][0-9a-fA-F]+/, 'number.hex'],
        [/\d+/, 'number'],
      ],

      strings: [
        [/'([^'\\]|\\.)*'/, 'string'],
        [/"([^"\\]|\\.)*"/, 'string'],
        [/`([^`\\]|\\.)*`/, 'string'],
      ],

      dollarOperators: [
        [/\$[a-zA-Z][a-zA-Z0-9]*/, {
          cases: {
            '@operators': 'keyword.operator',
            '@default': 'identifier',
          }
        }],
      ],

      keywords: [
        [/\.?\b[a-zA-Z_$][a-zA-Z0-9_$]*\b/, {
          cases: {
            '@keywords': 'keyword',
            '@default': 'identifier',
          }
        }],
      ],

      typeKeywords: [
        [/\b(?:ObjectId|ISODate|NumberLong|NumberDecimal|BinData|UUID|HexData|MD5|Timestamp)\(/, {
          cases: {
            '@typeKeywords': 'type',
            '@default': 'identifier',
          }
        }],
      ],

      brackets: [
        [/[{}()\[\]]/, '@brackets'],
      ],
    },
  })

  monaco.languages.registerCompletionItemProvider('mongodb', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      }

      const suggestions = [
        // db commands
        ...['find', 'findOne', 'aggregate', 'insertOne', 'insertMany',
           'updateOne', 'updateMany', 'deleteOne', 'deleteMany',
           'countDocuments', 'distinct', 'createIndex', 'drop',
           'renameCollection', 'watch',
        ].map(cmd => ({
          label: cmd,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: cmd + '({',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
          detail: 'MongoDB command',
        })),

        // aggregation stages
        ...['$match', '$group', '$sort', '$project', '$lookup', '$unwind',
           '$limit', '$skip', '$sample', '$facet', '$bucket', '$count',
           '$addFields', '$set', '$unset', '$out', '$merge', '$replaceRoot',
           '$geoNear', '$graphLookup',
        ].map(op => ({
          label: op,
          kind: monaco.languages.CompletionItemKind.Operator,
          insertText: op + ': {',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
          detail: 'Aggregation stage',
        })),

        // comparison operators
        ...['$gt', '$gte', '$lt', '$lte', '$eq', '$ne', '$in', '$nin',
           '$exists', '$regex', '$text', '$expr', '$not', '$nor', '$and', '$or',
           '$all', '$elemMatch', '$size', '$type', '$mod',
        ].map(op => ({
          label: op,
          kind: monaco.languages.CompletionItemKind.Operator,
          insertText: op + ': ',
          range,
          detail: 'Query operator',
        })),

        // accumulator operators
        ...['$sum', '$avg', '$first', '$last', '$max', '$min', '$push', '$addToSet',
           '$stdDevPop', '$stdDevSamp',
        ].map(op => ({
          label: op,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: op + ': ',
          range,
          detail: 'Accumulator',
        })),

        // BSON types
        ...['ObjectId', 'ISODate', 'NumberLong', 'NumberDecimal', 'BinData', 'UUID',
        ].map(t => ({
          label: t,
          kind: monaco.languages.CompletionItemKind.Constructor,
          insertText: t + '()',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
          detail: 'BSON type',
        })),
      ]

      return { suggestions }
    },
    triggerCharacters: ['.', '$', ' '],
  })
}

// ── SQL language config with custom keywords ────────────────────────────
function configureSqlLanguage(kwList) {
  const sqlKeywords = kwList?.length
    ? kwList.map(k => k.word).filter(Boolean)
    : []

  if (sqlKeywords.length) {
    monaco.languages.setMonarchTokensProvider('sql', {
      tokenizer: {
        root: [
          [/[ \t\r\n]+/, 'white'],
          [/--.*$/, 'comment'],
          [/\/\*/, 'comment', '@comment'],
          [/\d+/, 'number'],
          [/'[^']*'/, 'string'],
          [/"[^"]*"/, 'string'],
          [/[;,.]/, 'delimiter'],
          [/[=<>!]+/, 'operator'],
          [/[+\-*/%]/, 'operator'],
          [/[()\[\]]/, '@brackets'],
          [
            /[a-zA-Z_][a-zA-Z0-9_]*/,
            {
              cases: {
                '@keywords': 'keyword',
                '@default': 'identifier',
              },
            },
          ],
        ],
        comment: [
          [/[^/*]+/, 'comment'],
          [/\*\//, 'comment', '@pop'],
          [/[/*]/, 'comment'],
        ],
      },
      keywords: [
        ...sqlKeywords,
        'SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'VALUES', 'UPDATE',
        'SET', 'DELETE', 'CREATE', 'TABLE', 'DROP', 'ALTER', 'ADD',
        'COLUMN', 'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN', 'IS',
        'NULL', 'ORDER', 'BY', 'GROUP', 'HAVING', 'LIMIT', 'OFFSET',
        'JOIN', 'INNER', 'LEFT', 'RIGHT', 'OUTER', 'ON', 'AS',
        'DISTINCT', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'ASC', 'DESC',
        'BEGIN', 'COMMIT', 'ROLLBACK', 'GRANT', 'REVOKE', 'TO', 'FROM',
        'INDEX', 'UNIQUE', 'PRIMARY', 'KEY', 'DEFAULT', 'REFERENCES',
        'CHECK', 'FOREIGN', 'CONSTRAINT', 'IF', 'EXISTS', 'ALL',
        'TRANSACTION',
      ],
    })
  }
}

// ── Language switching ──────────────────────────────────────────────────
function setEditorLanguage(lang) {
  if (!editor) return
  const model = editor.getModel()
  if (!model) return

  monaco.editor.setModelLanguage(model, lang)
}

// ── Init editor ─────────────────────────────────────────────────────────
onMounted(() => {
  registerMongoLanguage()

  const lang = props.dialect === 'MONGODB' ? 'mongodb' : 'sql'

  if (lang === 'sql') {
    configureSqlLanguage(props.keywords)
  }

  editor = monaco.editor.create(editorEl.value, {
    value:              props.modelValue,
    language:           lang,
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

watch(() => props.dialect, (d) => {
  if (!editor) return
  const lang = d === 'MONGODB' ? 'mongodb' : 'sql'
  if (lang === 'sql') configureSqlLanguage(props.keywords)
  setEditorLanguage(lang)
})

watch(() => props.keywords, () => {
  if (props.dialect !== 'MONGODB') {
    configureSqlLanguage(props.keywords)
  }
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
