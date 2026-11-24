<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { nextTick } from 'vue'

export interface WorkspaceTabItem {
  id: string
  label: string
  pinned: boolean
  closable: boolean
}

const props = defineProps<{
  tabs: WorkspaceTabItem[]
  activeId: string
}>()

function moveTab(event: KeyboardEvent, tab: WorkspaceTabItem) {
  const direction = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0
  if (!direction || !props.tabs.length) return
  event.preventDefault()
  const index = props.tabs.findIndex(item => item.id === tab.id)
  const next = (index + direction + props.tabs.length) % props.tabs.length
  const container = (event.currentTarget as HTMLElement).parentElement
  emit('select', props.tabs[next])
  nextTick(() => (container?.querySelectorAll<HTMLElement>('[role="tab"]')[next])?.focus())
}

const emit = defineEmits<{
  select: [tab: WorkspaceTabItem]
  close: [tab: WorkspaceTabItem]
  pin: [tab: WorkspaceTabItem]
  contextmenu: [event: MouseEvent, tab: WorkspaceTabItem]
}>()
</script>

<template>
  <nav class="workspace-tabs" role="tablist" aria-label="工作区标签页" @contextmenu.prevent>
    <div
      v-for="tab in tabs"
      :key="tab.id"
      class="workspace-tab"
      :class="{ active: tab.id === activeId, pinned: tab.pinned }"
      role="tab"
      :aria-selected="tab.id === activeId"
      :tabindex="tab.id === activeId ? 0 : -1"
      @click="emit('select', tab)"
      @keydown.enter.self.prevent="emit('select', tab)"
      @keydown.space.self.prevent="emit('select', tab)"
      @keydown.left.self="moveTab($event, tab)"
      @keydown.right.self="moveTab($event, tab)"
      @contextmenu.prevent.stop="emit('contextmenu', $event, tab)"
    >
      <button
        class="workspace-tab-pin"
        :class="{ 'is-pinned': tab.pinned, 'is-unpinned': !tab.pinned }"
        type="button"
        :title="tab.pinned ? '取消固定标签页' : '固定标签页'"
        :aria-label="tab.pinned ? '取消固定标签页' : '固定标签页'"
        :aria-pressed="tab.pinned"
        :disabled="tab.id === 'home'"
        @click.stop="emit('pin', tab)"
      >
        <UiIcon name="pin" :size="13" />
      </button>
      <UiIcon class="workspace-tab-page-icon" :name="tab.id === 'home' ? 'grid' : 'file'" :size="14" />
      <span class="workspace-tab-label" :title="tab.label">{{ tab.label }}</span>
      <button
        v-if="tab.closable"
        class="workspace-tab-close"
        type="button"
        title="关闭标签页"
        :aria-label="`关闭 ${tab.label}`"
        @click.stop="emit('close', tab)"
      >
        <UiIcon name="close" :size="13" />
      </button>
    </div>
  </nav>
</template>
