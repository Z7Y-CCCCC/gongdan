<script setup lang="ts">
/**
 * ProcessCardSettingsModal - 工序任务卡片设置弹窗
 *
 * 列出全部工序，每行含勾选项、工序名称、颜色选择控件。
 * 支持上下移动按钮调整顺序。
 * 保存时 emit 'save' 事件，由父组件通过 PUT /api/user/preferences/home_dashboard.process_cards 持久化。
 * 保存后父组件触发卡片区重新渲染。
 *
 * Requirements: 6.1-6.8
 */
import { ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import { useDialogFocus } from '../composables/useDialogFocus'
import { workspaceDialogMotion } from '../workspaceDialogMotion'

export interface ProcessCardSetting {
  processId: number
  processName: string
  color: string
  visible: boolean
}

const props = defineProps<{
  visible: boolean
  settings: ProcessCardSetting[]
  token: string
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [newSettings: Array<{ processId: number; color: string; visible: boolean }>]
}>()

// Local editable copy of settings
const localSettings = ref<ProcessCardSetting[]>([])
const dialogRef = ref<HTMLElement | null>(null)
useDialogFocus(dialogRef, () => props.visible, handleCancel)

// Sync local copy when modal opens
watch(
  () => props.visible,
  (val) => {
    if (val) {
      localSettings.value = props.settings.map((s) => ({ ...s }))
    }
  },
  { immediate: true }
)

function moveUp(index: number) {
  if (props.saving) return
  if (index <= 0) return
  const items = [...localSettings.value]
  const temp = items[index]
  items[index] = items[index - 1]
  items[index - 1] = temp
  localSettings.value = items
}

function moveDown(index: number) {
  if (props.saving) return
  const items = [...localSettings.value]
  if (index >= items.length - 1) return
  const temp = items[index]
  items[index] = items[index + 1]
  items[index + 1] = temp
  localSettings.value = items
}

function handleSave() {
  if (props.saving) return
  const newSettings = localSettings.value.map((s) => ({
    processId: s.processId,
    color: s.color,
    visible: s.visible
  }))
  emit('save', newSettings)
}

function handleCancel() {
  if (props.saving) return
  emit('update:visible', false)
}
</script>

<template>
  <teleport to="body">
    <Transition v-bind="workspaceDialogMotion">
    <div v-if="visible" class="modal-overlay modal-backdrop workspace-dialog-motion-backdrop" @click.self="handleCancel">
      <div ref="dialogRef" class="modal-card modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" tabindex="-1" aria-labelledby="settings-modal-title">
        <div class="modal-header">
          <h3 id="settings-modal-title" class="modal-title">工序任务卡片设置</h3>
          <button class="close-btn" type="button" title="关闭" aria-label="关闭" :disabled="saving" @click="handleCancel">
            <UiIcon name="close" :size="18" />
          </button>
        </div>

        <div class="modal-body">
          <div
            v-for="(item, index) in localSettings"
            :key="item.processId"
            class="setting-row"
          >
            <label class="setting-checkbox">
              <input
                type="checkbox"
                v-model="item.visible"
                :disabled="saving"
                :aria-label="`显示 ${item.processName}`"
              />
            </label>

            <span class="setting-name">{{ item.processName }}</span>

            <input
              type="color"
              v-model="item.color"
              :disabled="saving"
              class="setting-color"
              :title="`选择 ${item.processName} 的颜色`"
              :aria-label="`${item.processName} 颜色`"
            />

            <div class="setting-actions">
              <button
                class="btn-move"
                :disabled="saving || index === 0"
                title="上移"
                :aria-label="`上移 ${item.processName}`"
                @click="moveUp(index)"
              >
                <UiIcon name="chevronUp" :size="16" />
              </button>
              <button
                class="btn-move"
                :disabled="saving || index === localSettings.length - 1"
                title="下移"
                :aria-label="`下移 ${item.processName}`"
                @click="moveDown(index)"
              >
                <UiIcon name="chevronDown" :size="16" />
              </button>
            </div>
          </div>

          <div v-if="localSettings.length === 0" class="empty-hint">
            暂无工序
          </div>
        </div>

        <p v-if="error" role="alert">{{ error }}</p>
        <div class="modal-footer">
          <button class="btn btn-cancel" :disabled="saving" @click="handleCancel">取消</button>
          <button class="btn btn-save ui-icon-button" title="保存" aria-label="保存" :disabled="saving" :aria-busy="saving" @click="handleSave"><UiIcon name="save" :size="18" /></button>
        </div>
      </div>
    </div>
    </Transition>
  </teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-card {
  background: #fff;
  border-radius: 8px;
  width: 480px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}

.modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e8e8e8;
}

.modal-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
}

.setting-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-checkbox input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.setting-name {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.setting-color {
  width: 32px;
  height: 28px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 2px;
  cursor: pointer;
  background: none;
}

.setting-actions {
  display: flex;
  gap: 4px;
}

.btn-move {
  width: 28px;
  height: 28px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fafafa;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  transition: background 0.2s;
}

.btn-move:hover:not(:disabled) {
  background: #e6f7ff;
  border-color: #1890ff;
  color: #1890ff;
}

.btn-move:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.empty-hint {
  text-align: center;
  color: #999;
  padding: 24px 0;
  font-size: 14px;
}

.modal-footer {
  padding: 12px 20px;
  border-top: 1px solid #e8e8e8;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  padding: 6px 16px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #d9d9d9;
  transition: all 0.2s;
}

.btn-cancel {
  background: #fff;
  color: #333;
}

.btn-cancel:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.btn-save {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.btn-save:hover {
  background: #40a9ff;
  border-color: #40a9ff;
}
</style>
