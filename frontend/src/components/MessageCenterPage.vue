<script setup lang="ts">
import UiIcon from './UiIcon.vue'

type MessageItem = {
  id: string
  type: 'approval' | 'overdue' | 'report'
  title: string
  detail: string
  time?: string
  target: { main: string; sub: string }
}

withDefaults(defineProps<{
  items: MessageItem[]
  loading?: boolean
  error?: string
}>(), {
  loading: false,
  error: ''
})

const emit = defineEmits<{
  refresh: []
  open: [item: MessageItem]
}>()
</script>

<template>
  <section class="message-center-page">
    <header class="message-center-header">
      <div>
        <p class="message-center-kicker">WORKSPACE</p>
        <h1>消息中心</h1>
        <p>集中查看工单、报工和审批待办。</p>
      </div>
      <button class="toolbar-btn" type="button" :disabled="loading" @click="emit('refresh')">
        <UiIcon name="refresh" :size="15" />
        {{ loading ? '刷新中...' : '刷新消息' }}
      </button>
    </header>

    <div class="message-center-summary">
      <span><i></i>全部待办</span>
      <strong>{{ items.length }}</strong>
    </div>

    <div v-if="loading" class="message-center-state">消息加载中...</div>
    <div v-else-if="error" class="message-center-state error">{{ error }}</div>
    <div v-else-if="items.length === 0" class="message-center-state">暂无待办消息</div>
    <div v-else class="message-center-list">
      <button
        v-for="item in items"
        :key="item.id"
        class="message-center-item"
        type="button"
        @click="emit('open', item)"
      >
        <span class="message-center-item-dot" :class="item.type"></span>
        <span class="message-center-item-body">
          <strong>{{ item.title }}</strong>
          <span>{{ item.detail }}</span>
        </span>
        <time>{{ item.time || '-' }}</time>
        <UiIcon name="arrowRight" :size="15" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.message-center-page {
  min-height: calc(100vh - 94px);
  padding: 28px 30px 42px;
  color: #24312e;
  background: #f7f9f8;
}

.message-center-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 24px;
  border-bottom: 1px solid #dfe7e4;
}

.message-center-kicker {
  margin: 0 0 8px;
  color: #1a9a7a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.message-center-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}

.message-center-header p:last-child {
  margin: 8px 0 0;
  color: #7b8985;
  font-size: 13px;
}

.message-center-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 0 12px;
  color: #6f7e79;
  font-size: 13px;
}

.message-center-summary span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.message-center-summary i,
.message-center-item-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #19aa83;
}

.message-center-summary strong {
  color: #24312e;
  font-size: 16px;
}

.message-center-list {
  overflow: hidden;
  border: 1px solid #dfe7e4;
  border-radius: 6px;
  background: #fff;
}

.message-center-item {
  width: 100%;
  min-height: 72px;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto 15px;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  color: inherit;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #edf1ef;
  text-align: left;
  cursor: pointer;
  transition: background .18s ease, padding-left .18s ease;
}

.message-center-item:last-child { border-bottom: 0; }
.message-center-item:hover { padding-left: 22px; background: #f3faf7; }
.message-center-item-dot.overdue { background: #e45145; }
.message-center-item-dot.report { background: #2f80ed; }

.message-center-item-body {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.message-center-item-body strong {
  color: #263632;
  font-size: 14px;
}

.message-center-item-body span {
  overflow: hidden;
  color: #75837e;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-center-item time {
  color: #9aa6a2;
  font-size: 12px;
  white-space: nowrap;
}

.message-center-item > .ui-icon { color: #9aa6a2; }

.message-center-state {
  padding: 72px 20px;
  border: 1px dashed #d5e0dc;
  color: #8a9792;
  background: rgba(255, 255, 255, .7);
  text-align: center;
  font-size: 13px;
}

.message-center-state.error { color: #d6534d; }

@media (max-width: 680px) {
  .message-center-page { padding: 22px 16px 30px; }
  .message-center-header { align-items: stretch; flex-direction: column; }
  .message-center-item { grid-template-columns: 10px minmax(0, 1fr) 15px; }
  .message-center-item time { display: none; }
}
</style>
