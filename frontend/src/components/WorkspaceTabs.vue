<script setup lang="ts">
import UiIcon from './UiIcon.vue'

export interface WorkspaceTabItem {
  id: string
  label: string
  pinned: boolean
  closable: boolean
}

defineProps<{
  tabs: WorkspaceTabItem[]
  activeId: string
}>()

const emit = defineEmits<{
  select: [tab: WorkspaceTabItem]
  close: [tab: WorkspaceTabItem]
  contextmenu: [event: MouseEvent, tab: WorkspaceTabItem]
  add: []
}>()
</script>

<template>
  <nav class="workspace-tabs" aria-label="工作区标签页" @contextmenu.prevent>
    <div
      v-for="tab in tabs"
      :key="tab.id"
      class="workspace-tab"
      :class="{ active: tab.id === activeId, pinned: tab.pinned }"
      role="tab"
      :aria-selected="tab.id === activeId"
      tabindex="0"
      @click="emit('select', tab)"
      @keydown.enter="emit('select', tab)"
      @contextmenu.prevent.stop="emit('contextmenu', $event, tab)"
    >
      <UiIcon :name="tab.id === 'home' ? 'grid' : 'file'" :size="14" />
      <span class="workspace-tab-label">{{ tab.label }}</span>
      <UiIcon v-if="tab.pinned" class="workspace-tab-pin" name="pin" :size="12" />
      <button
        v-if="tab.closable"
        class="workspace-tab-close"
        type="button"
        title="关闭标签页"
        @click.stop="emit('close', tab)"
      >
        <UiIcon name="close" :size="13" />
      </button>
    </div>
    <button class="workspace-tab-add" type="button" title="打开当前页面标签" @click="emit('add')">
      <UiIcon name="plus" :size="15" />
    </button>
  </nav>
</template>
