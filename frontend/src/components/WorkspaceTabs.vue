<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { nextTick, ref, watch } from 'vue'

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

const tabBar = ref<HTMLElement | null>(null)

watch(() => props.activeId, () => {
  const bar = tabBar.value
  const tab = bar?.querySelector<HTMLElement>('[aria-selected="true"]')
  if (!bar || !tab) return
  // Reveal the selected tab within this strip without scrolling the work page.
  const outer = bar.getBoundingClientRect()
  const inner = tab.getBoundingClientRect()
  if (inner.left < outer.left) bar.scrollLeft += Math.floor(inner.left - outer.left - 8)
  else if (inner.right > outer.right) bar.scrollLeft += Math.ceil(inner.right - outer.right + 8)
}, { flush: 'post' })

function handleTabKey(event: KeyboardEvent, tab: WorkspaceTabItem) {
  if (event.target !== event.currentTarget) return
  if (event.key === 'ContextMenu' || (event.key === 'F10' && event.shiftKey)) {
    event.preventDefault()
    emit('contextmenu', event, tab)
    return
  }
  if (event.key === 'Delete' && tab.closable && !tab.pinned) {
    event.preventDefault()
    emit('close', tab)
    nextTick(() => tabBar.value?.querySelector<HTMLElement>('[aria-selected="true"]')?.focus())
    return
  }
  const direction = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0
  if ((!direction && event.key !== 'Home' && event.key !== 'End') || !props.tabs.length) return
  event.preventDefault()
  const index = props.tabs.findIndex(item => item.id === tab.id)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? props.tabs.length - 1
    : (index + direction + props.tabs.length) % props.tabs.length
  const container = (event.currentTarget as HTMLElement).parentElement
  emit('select', props.tabs[next])
  nextTick(() => (container?.querySelectorAll<HTMLElement>('[role="tab"]')[next])?.focus())
}

const emit = defineEmits<{
  select: [tab: WorkspaceTabItem]
  close: [tab: WorkspaceTabItem]
  pin: [tab: WorkspaceTabItem]
  contextmenu: [event: MouseEvent | KeyboardEvent, tab: WorkspaceTabItem]
}>()
</script>

<template>
  <nav ref="tabBar" class="workspace-tabs" role="tablist" aria-label="工作区标签页" @contextmenu.prevent>
    <div
      v-for="tab in tabs"
      :key="tab.id"
      :id="`workspace-tab-${tab.id}`"
      class="workspace-tab"
      :class="{ active: tab.id === activeId, pinned: tab.pinned }"
      role="tab"
      :aria-label="tab.label"
      :aria-selected="tab.id === activeId"
      aria-haspopup="menu"
      aria-controls="workspace-page"
      :tabindex="tab.id === activeId ? 0 : -1"
      @click="emit('select', tab)"
      @keydown.enter.self.prevent="emit('select', tab)"
      @keydown.space.self.prevent="emit('select', tab)"
      @keydown="handleTabKey($event, tab)"
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
        tabindex="-1"
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
        tabindex="-1"
        @click.stop="emit('close', tab)"
      >
        <UiIcon name="close" :size="13" />
      </button>
    </div>
  </nav>
</template>

<style scoped>
.workspace-tab {
  flex: 0 0 auto;
  position: relative;
  transition: color var(--motion-fast) ease, background-color var(--motion-base) ease,
    border-color var(--motion-fast) ease;
}

.workspace-tab::after {
  position: absolute;
  inset: auto 10px 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--accent-dark);
  content: '';
  pointer-events: none;
  opacity: 0;
  transform: scaleX(.35);
  transition: opacity var(--motion-fast) ease, transform var(--motion-base) var(--motion-ease);
}

.workspace-tab.active::after {
  opacity: 1;
  transform: scaleX(1);
}

.workspace-tab:focus-visible {
  outline: 2px solid var(--accent-dark);
  outline-offset: -2px;
}
</style>
