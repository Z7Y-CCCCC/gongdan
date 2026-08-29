<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ColumnSettingsPanel, { type ColumnSetting } from './ColumnSettingsPanel.vue'
import UiIcon from './UiIcon.vue'

const props = defineProps<{
  tableId: string
  title?: string
}>()

const emit = defineEmits<{
  updated: [columns: ColumnSetting[]]
}>()

const open = ref(false)
const columns = ref<ColumnSetting[]>([])
const defaults = ref<ColumnSetting[]>([])
let styleElement: HTMLStyleElement | null = null

const storageKey = `table_columns_${props.tableId}`

function getTable() {
  return document.getElementById(props.tableId)
}

function readSavedColumns() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || 'null')
    return Array.isArray(saved) ? saved as ColumnSetting[] : []
  } catch {
    return []
  }
}

function collectColumns() {
  const table = getTable()
  const headerRow = table?.querySelector('thead tr')
  if (!headerRow) return

  const saved = readSavedColumns()
  const next = Array.from(headerRow.children).map((cell, index) => {
    const element = cell as HTMLElement
    const label = element.innerText.replace(/\s+/g, ' ').trim() || `第 ${index + 1} 列`
    const key = `column-${index}`
    const isOperation = label === '操作'
    const previous = saved.find(item => item.key === key || item.label === label)
    return {
      key,
      label,
      visible: isOperation ? true : previous?.visible ?? true,
      width: previous?.width ?? Math.max(70, Math.round(element.getBoundingClientRect().width || 120)),
      fixed: isOperation ? 'right' : previous?.fixed || 'none',
      locked: isOperation
    } satisfies ColumnSetting
  })

  defaults.value = next.map(column => ({ ...column, visible: true }))
  columns.value = next
  applyColumnStyles(next)
}

function ensureStyleElement() {
  if (styleElement) return styleElement
  styleElement = document.createElement('style')
  styleElement.id = `table-column-style-${props.tableId}`
  document.head.appendChild(styleElement)
  return styleElement
}

function applyColumnStyles(next: ColumnSetting[]) {
  const table = getTable()
  if (!table) return
  const hidden = next
    .map((column, index) => column.visible ? '' : [
      `#${props.tableId} thead th:nth-child(${index + 1})`,
      `#${props.tableId} tbody td:nth-child(${index + 1})`,
      `#${props.tableId} tfoot td:nth-child(${index + 1})`
    ].join(', '))
    .filter(Boolean)
    .map(selector => `${selector} { display: none !important; }`)
    .join('\n')
  ensureStyleElement().textContent = hidden
}

function handleConfirm(value: ColumnSetting[]) {
  columns.value = value.map(column => ({ ...column }))
  localStorage.setItem(storageKey, JSON.stringify(columns.value))
  applyColumnStyles(columns.value)
  open.value = false
  emit('updated', columns.value)
}

function handleCancel() {
  open.value = false
}

function openPanel() {
  collectColumns()
  open.value = true
}

watch(open, value => {
  if (value) nextTick(collectColumns)
})

onMounted(() => nextTick(collectColumns))

onBeforeUnmount(() => {
  styleElement?.remove()
  styleElement = null
})
</script>

<template>
  <div class="table-config-control">
    <button
      class="table-config-trigger"
      type="button"
      :title="title || '字段配置'"
      :aria-label="title || '字段配置'"
      :aria-expanded="open"
      @click="openPanel"
    >
      <UiIcon name="table-columns" :size="16" />
    </button>

    <ColumnSettingsPanel
      v-if="open && columns.length"
      v-model="columns"
      :defaults="defaults"
      :title="title || '字段配置'"
      @confirm="handleConfirm"
      @cancel="handleCancel"
    />
  </div>
</template>
