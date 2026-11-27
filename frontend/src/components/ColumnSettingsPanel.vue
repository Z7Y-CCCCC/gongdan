<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import { useDialogFocus } from '../composables/useDialogFocus'
import { workspaceDialogMotion } from '../workspaceDialogMotion'

export type ColumnFixedSide = 'left' | 'none' | 'right'

export interface ColumnSetting {
  key: string
  label: string
  visible: boolean
  width: number
  fixed: ColumnFixedSide
  locked?: boolean
}

const props = withDefaults(defineProps<{
  visible?: boolean
  modelValue: ColumnSetting[]
  defaults: ColumnSetting[]
  title?: string
}>(), { visible: true })

const emit = defineEmits<{
  'update:modelValue': [value: ColumnSetting[]]
  confirm: [value: ColumnSetting[]]
  cancel: []
}>()

function cloneColumns(columns: ColumnSetting[]) {
  return columns.map(column => ({ ...column }))
}

const draft = ref<ColumnSetting[]>(props.visible ? cloneColumns(props.modelValue) : [])
const draggedKey = ref('')
const dialogRef = ref<HTMLElement | null>(null)
useDialogFocus(dialogRef, () => props.visible, cancel)

watch(() => props.visible ? props.modelValue : null, value => {
  if (!value) return
  draft.value = cloneColumns(value)
  draggedKey.value = ''
}, { deep: true })

const visibleCount = computed(() => draft.value.filter(column => column.visible).length)
const allSelected = computed({
  get: () => draft.value.length > 0 && draft.value.every(column => column.visible || column.locked),
  set: (value: boolean) => {
    draft.value.forEach(column => {
      if (!column.locked) column.visible = value
    })
  }
})

function updateWidth(column: ColumnSetting, delta: number) {
  column.width = Math.min(480, Math.max(50, Number(column.width || 100) + delta))
}

function normalizeWidth(column: ColumnSetting) {
  column.width = Math.min(480, Math.max(50, Math.round(Number(column.width) || 100)))
}

function onDragStart(column: ColumnSetting) {
  if (column.locked) return
  draggedKey.value = column.key
}

function onDrop(target: ColumnSetting) {
  if (!draggedKey.value || draggedKey.value === target.key || target.locked) return
  const from = draft.value.findIndex(column => column.key === draggedKey.value)
  const to = draft.value.findIndex(column => column.key === target.key)
  if (from < 0 || to < 0) return
  const [moved] = draft.value.splice(from, 1)
  draft.value.splice(to, 0, moved)
  draggedKey.value = ''
}

function restoreDefaults() {
  draft.value = cloneColumns(props.defaults)
}

function cancel() {
  draft.value = cloneColumns(props.modelValue)
  emit('cancel')
}

function confirm() {
  if (!visibleCount.value) return
  const value = cloneColumns(draft.value)
  emit('update:modelValue', value)
  emit('confirm', value)
}
</script>

<template>
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="visible" class="column-settings-overlay workspace-dialog-motion-backdrop" @click.self="cancel">
    <section ref="dialogRef" class="column-settings-panel workspace-dialog-motion-panel" role="dialog" aria-modal="true" tabindex="-1" :aria-label="title || '列设置'">
      <header class="column-settings-header">
        <h2>{{ title || '列设置' }}</h2>
        <button class="column-settings-close ui-icon-button" type="button" title="关闭" aria-label="关闭" @click="cancel">
          <UiIcon name="close" :size="18" />
        </button>
      </header>

      <div class="column-settings-table">
        <div class="column-settings-row column-settings-head">
          <label class="column-check-cell">
            <input v-model="allSelected" type="checkbox" />
            <span>全部</span>
          </label>
          <span>列标题</span>
          <span>列宽（像素）</span>
          <span>冻结位置</span>
        </div>

        <div
          v-for="column in draft"
          :key="column.key"
          class="column-settings-row"
          :class="{ dragging: draggedKey === column.key }"
          draggable="true"
          @dragstart="onDragStart(column)"
          @dragover.prevent
          @drop.prevent="onDrop(column)"
        >
          <label class="column-check-cell">
            <input v-model="column.visible" type="checkbox" :aria-label="`显示 ${column.label}`" :disabled="column.locked" />
          </label>
          <div class="column-title-cell">
            <span class="column-drag-handle" title="拖动排序" aria-hidden="true"><UiIcon name="grip" :size="16" /></span>
            <span>{{ column.label }}</span>
            <small v-if="column.locked">固定</small>
          </div>
          <div class="column-width-cell">
            <input v-model.number="column.width" type="number" :aria-label="`${column.label}列宽`" min="50" max="480" step="1" @change="normalizeWidth(column)" />
            <div class="column-stepper">
              <button type="button" title="增加列宽" aria-label="增加列宽" @click="updateWidth(column, 10)"><UiIcon name="plus" :size="12" /></button>
              <button type="button" title="减少列宽" aria-label="减少列宽" @click="updateWidth(column, -10)"><UiIcon name="minus" :size="12" /></button>
            </div>
          </div>
          <div class="column-fixed-group" role="group" aria-label="冻结位置">
            <button type="button" :disabled="column.locked" :class="{ active: column.fixed === 'left' }" @click="column.fixed = 'left'">左侧</button>
            <button type="button" :disabled="column.locked" :class="{ active: column.fixed === 'none' }" @click="column.fixed = 'none'">不设置</button>
            <button type="button" :disabled="column.locked" :class="{ active: column.fixed === 'right' }" @click="column.fixed = 'right'">右侧</button>
          </div>
        </div>
      </div>

      <footer class="column-settings-footer">
        <span class="column-settings-count" :role="visibleCount === 0 ? 'status' : undefined">{{ visibleCount === 0 ? '至少显示一列' : `${visibleCount} 列` }}</span>
        <div>
          <button class="column-settings-secondary ui-action-button" type="button" title="恢复默认" aria-label="恢复默认" @click="restoreDefaults"><UiIcon name="reset" :size="16" />默认</button>
          <button class="column-settings-secondary" type="button" @click="cancel">取消</button>
          <button class="column-settings-primary ui-icon-button" type="button" title="应用列设置" aria-label="应用列设置" :disabled="visibleCount === 0" @click="confirm"><UiIcon name="check" :size="18" /></button>
        </div>
      </footer>
    </section>
  </div>
  </Transition>
</template>
