<script setup lang="ts">
/**
 * DeleteConfirmDialog - 统一删除确认弹窗组件
 *
 * 提供一致的删除确认体验，包含红色警告图标、标题、描述信息和操作按钮。
 * 支持 v-model:visible 绑定、loading 状态、键盘 Escape 关闭、焦点管理。
 *
 * Requirements: 1.1-1.9, 2.1-2.9, 3.1-3.7
 */
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    message?: string
    loading?: boolean
  }>(),
  {
    title: '删除确认',
    message: '',
    loading: false
  }
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: []
}>()

const titleId = 'delete-confirm-title-' + Math.random().toString(36).slice(2, 9)
const cancelBtnRef = ref<HTMLButtonElement | null>(null)

function handleDismiss() {
  if (props.loading) return
  emit('update:visible', false)
}

function handleConfirm() {
  if (props.loading) return
  emit('confirm')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible && !props.loading) {
    emit('update:visible', false)
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      nextTick(() => {
        cancelBtnRef.value?.focus()
      })
    }
  }
)

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-fade">
      <div
        v-if="visible"
        class="delete-confirm-backdrop"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
      >
        <div class="delete-confirm-backdrop__overlay" @click="handleDismiss" />
        <section class="delete-confirm-dialog" @click.stop>
          <div class="delete-confirm-dialog__header">
            <div class="delete-confirm-dialog__icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="11" stroke="#d74434" stroke-width="2" fill="#fff0ee" />
                <path
                  d="M8 8L16 16M16 8L8 16"
                  stroke="#d74434"
                  stroke-width="2"
                  stroke-linecap="round"
                />
              </svg>
            </div>
            <h2 :id="titleId" class="delete-confirm-dialog__title">{{ title }}</h2>
          </div>
          <p class="delete-confirm-dialog__message">{{ message }}</p>
          <footer class="delete-confirm-dialog__footer">
            <button
              ref="cancelBtnRef"
              class="btn btn--ghost"
              :disabled="loading"
              @click="handleDismiss"
            >
              取消
            </button>
            <button
              class="btn btn--danger"
              :disabled="loading"
              @click="handleConfirm"
            >
              <span v-if="loading" class="spinner" aria-hidden="true" />
              确定
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.delete-confirm-backdrop {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.delete-confirm-backdrop__overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
}

.delete-confirm-dialog {
  position: relative;
  background: #fff;
  border-radius: 12px;
  width: 400px;
  min-width: 320px;
  max-width: 480px;
  padding: 28px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delete-confirm-dialog__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.delete-confirm-dialog__icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.delete-confirm-dialog__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-confirm-dialog__message {
  margin: 0;
  font-size: 14px;
  color: #6d7775;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.delete-confirm-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #d9d9d9;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn--ghost {
  background: #fff;
  color: #333;
  border-color: #d9d9d9;
}

.btn--ghost:hover:not(:disabled) {
  border-color: #999;
  color: #111;
}

.btn--danger {
  background: #d74434;
  color: #fff;
  border-color: #d74434;
}

.btn--danger:hover:not(:disabled) {
  background: #c0392b;
  border-color: #c0392b;
}

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Transition animations */
.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: opacity 0.2s ease;
}

.dialog-fade-enter-active .delete-confirm-dialog,
.dialog-fade-leave-active .delete-confirm-dialog {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}

.dialog-fade-enter-from .delete-confirm-dialog {
  transform: scale(0.9);
  opacity: 0;
}

.dialog-fade-leave-to .delete-confirm-dialog {
  transform: scale(0.9);
  opacity: 0;
}
</style>
