<template>
  <Teleport to="body">
    <dialog v-if="modelValue" ref="dialog" class="mobile-message-dialog" role="dialog" aria-modal="true" :aria-label="title" @cancel.prevent="finish(false)" @keydown="trap">
      <h2 class="message-title">{{ title }}</h2>
      <p class="message-content" tabindex="0" role="region" :aria-label="title + '内容'">{{ content }}</p>
      <view class="message-actions">
        <button v-if="cancelText" class="message-cancel" @click="finish(false)">{{ cancelText }}</button>
        <button class="message-confirm" @click="finish(true)">{{ confirmText || '关闭' }}</button>
      </view>
    </dialog>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useMobilePageActivity } from '../composables/useMobilePageActivity'
const props = defineProps<{ modelValue: boolean; title: string; content: string; confirmText?: string; cancelText?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: []; cancel: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const activity = useMobilePageActivity()
let opener: HTMLElement | null = null
let canRestore = () => false
watch(() => props.modelValue, async visible => {
  if (typeof document === 'undefined') return
  if (visible) {
    opener = document.activeElement as HTMLElement | null
    canRestore = activity.capture()
    await nextTick()
    if (!props.modelValue || !canRestore()) return
    if (typeof dialog.value?.showModal === 'function') dialog.value.showModal()
    else dialog.value?.setAttribute('open', '')
    dialog.value?.querySelector<HTMLButtonElement>('button')?.focus()
  } else {
    await nextTick()
    if (canRestore() && opener?.isConnected) opener.focus()
  }
}, { flush: 'post' })
function finish(accepted: boolean) {
  emit('update:modelValue', false)
  if (accepted) emit('confirm')
  else emit('cancel')
}
function trap(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); finish(false); return }
  if (event.key !== 'Tab') return
  const targets = Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex="0"]') || [])
  const first = targets[0], last = targets[targets.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
const routeChanged = () => { if (props.modelValue) finish(false) }
if (typeof window !== 'undefined') window.addEventListener('hashchange', routeChanged)
onBeforeUnmount(() => { if (typeof window !== 'undefined') window.removeEventListener('hashchange', routeChanged) })
</script>

<style scoped>
.mobile-message-dialog { box-sizing: border-box; width: calc(100vw - 32px); max-width: 440px; max-height: calc(100dvh - 48px); margin: auto; padding: 20px; border: 1px solid var(--mobile-border, #dce6e1); border-radius: 10px; background: var(--mobile-surface, #fff); color: var(--mobile-text, #202926); }
.mobile-message-dialog::backdrop { background: rgba(18,35,29,.48); }
.message-title { margin: 0 0 14px; font-size: 18px; line-height: 1.4; overflow-wrap: anywhere; }
.message-content { margin: 0; max-height: 55vh; overflow-y: auto; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 14px; line-height: 1.7; color: var(--mobile-muted, #536b60); }
.message-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px; }
.message-actions button { min-height: 44px; padding: 0 20px; font-size: 14px; border-radius: 8px; }
.message-confirm { background: #007a60; color: white; }
.message-cancel { background: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #007a60); }
</style>
