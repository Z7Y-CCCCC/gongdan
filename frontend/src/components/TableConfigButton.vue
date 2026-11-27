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
    const label = element.getAttribute('data-ui-label')?.trim() || element.innerText.replace(/\s+/g, ' ').trim() || `第 ${index + 1} 列`
    const key = `column-${index}`
    const previous = saved.find(item => item.key === key || item.label === label)
    const fixed = previous?.fixed === 'left' || previous?.fixed === 'none' || previous?.fixed === 'right'
      ? previous.fixed
      : label === '操作' ? 'right' : 'none'
    return {
      key,
      label,
      visible: previous?.visible ?? true,
      width: Math.min(480, Math.max(50, Math.round(Number(previous?.width) || element.getBoundingClientRect().width || 120))),
      fixed,
      locked: false
    } satisfies ColumnSetting
  })

  defaults.value = next.map(column => ({
    ...column,
    visible: true,
    fixed: column.label === '操作' ? 'right' : 'none',
    locked: false
  }))
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
  const tableSelector = `#${props.tableId}`
  const visibleColumns = next.filter(column => column.visible)
  const rules: string[] = []

  next.forEach((column, index) => {
    const savedIndex = Number.parseInt(column.key.replace('column-', ''), 10)
    const domIndex = Number.isInteger(savedIndex) ? savedIndex : index
    const cellSelectors = [
      `${tableSelector} thead th:nth-child(${domIndex + 1})`,
      `${tableSelector} tbody td:not([colspan]):nth-child(${domIndex + 1})`,
      `${tableSelector} tfoot td:not([colspan]):nth-child(${domIndex + 1})`
    ]
    const headerSelector = `${tableSelector} thead th:nth-child(${domIndex + 1})`
    const bodySelectors = [
      `${tableSelector} tbody td:not([colspan]):nth-child(${domIndex + 1})`,
      `${tableSelector} tfoot td:not([colspan]):nth-child(${domIndex + 1})`
    ]
    const width = Math.min(480, Math.max(50, Math.round(Number(column.width) || 100)))

    if (!column.visible) {
      rules.push(`${cellSelectors.join(', ')} { display: none !important; }`)
      return
    }

    rules.push(`${cellSelectors.join(', ')} { width: ${width}px !important; min-width: ${width}px !important; }`)

    const visibleIndex = visibleColumns.findIndex(item => item.key === column.key)
    if (column.fixed === 'left') {
      const offset = visibleColumns
        .slice(0, visibleIndex)
        .filter(item => item.fixed === 'left')
        .reduce((sum, item) => sum + Number(item.width || 0), 0)
      rules.push(`${headerSelector} { position: sticky !important; left: ${offset}px !important; right: auto !important; z-index: 12 !important; background: var(--table-head-bg, #f4f7f6) !important; box-shadow: 6px 0 12px var(--table-fixed-shadow, rgba(31, 39, 37, .08)) !important; }`)
      rules.push(`${bodySelectors.join(', ')} { position: sticky !important; left: ${offset}px !important; right: auto !important; z-index: 3 !important; background: var(--table-row-bg, #fff) !important; box-shadow: 6px 0 12px var(--table-fixed-shadow, rgba(31, 39, 37, .08)) !important; }`)
    } else if (column.fixed === 'right') {
      const offset = visibleColumns
        .slice(visibleIndex + 1)
        .filter(item => item.fixed === 'right')
        .reduce((sum, item) => sum + Number(item.width || 0), 0)
      rules.push(`${headerSelector} { position: sticky !important; right: ${offset}px !important; left: auto !important; z-index: 12 !important; background: var(--table-head-bg, #f4f7f6) !important; box-shadow: -6px 0 12px var(--table-fixed-shadow, rgba(31, 39, 37, .08)) !important; }`)
      rules.push(`${bodySelectors.join(', ')} { position: sticky !important; right: ${offset}px !important; left: auto !important; z-index: 3 !important; background: var(--table-row-bg, #fff) !important; box-shadow: -6px 0 12px var(--table-fixed-shadow, rgba(31, 39, 37, .08)) !important; }`)
    } else {
      rules.push(`${headerSelector} { left: auto !important; right: auto !important; box-shadow: none !important; }`)
      rules.push(`${bodySelectors.join(', ')} { position: static !important; left: auto !important; right: auto !important; box-shadow: none !important; }`)
    }
  })

  ensureStyleElement().textContent = rules.join('\n')
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
      class="table-config-trigger ui-icon-button"
      type="button"
      :title="title || '字段配置'"
      :aria-label="title || '字段配置'"
      :aria-expanded="open"
      @click="openPanel"
    >
      <UiIcon name="table-columns" :size="16" />
    </button>

    <ColumnSettingsPanel
      :visible="open && columns.length > 0"
      v-model="columns"
      :defaults="defaults"
      :title="title || '字段配置'"
      @confirm="handleConfirm"
      @cancel="handleCancel"
    />
  </div>
</template>
