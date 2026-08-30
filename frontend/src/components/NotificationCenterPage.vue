<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  archiveNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationUnread,
  type NotificationInfo,
  type NotificationScope
} from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string }>()

const emit = defineEmits<{
  open: [item: NotificationInfo]
  notice: [message: string]
}>()

const scope = ref<NotificationScope>('active')
const unreadOnly = ref(false)
const eventType = ref('')
const priority = ref('')
const keyword = ref('')
const searchKeyword = ref('')
const page = ref(1)
const pageSize = 20
const items = ref<NotificationInfo[]>([])
const total = ref(0)
const unreadCount = ref(0)
const loading = ref(false)
const error = ref('')

const hasPrevious = computed(() => page.value > 1)
const hasNext = computed(() => page.value * pageSize < total.value)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function formatDate(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date).replace(/\//g, '-')
}

function relativeTime(value?: string) {
  if (!value) return '-'
  const diff = Date.now() - new Date(value).getTime()
  if (!Number.isFinite(diff) || diff < 0) return '刚刚'
  const minute = Math.floor(diff / 60000)
  if (minute < 1) return '刚刚'
  if (minute < 60) return `${minute}分钟前`
  const hour = Math.floor(minute / 60)
  if (hour < 24) return `${hour}小时前`
  const day = Math.floor(hour / 24)
  return day < 30 ? `${day}天前` : formatDate(value)
}

function iconName(item: NotificationInfo) {
  if (item.eventType.includes('REPORT')) return 'check'
  if (item.eventType.includes('WORK_ORDER')) return 'file'
  if (item.eventType.includes('INVENTORY')) return 'layers'
  if (item.eventType.includes('PLUGIN')) return 'layers'
  return 'bell'
}

function priorityLabel(value: string) {
  return value === 'URGENT' ? '紧急' : value === 'HIGH' ? '重要' : value === 'LOW' ? '低' : '普通'
}

async function load() {
  if (!props.token) return
  loading.value = true
  error.value = ''
  try {
    const result = await fetchNotifications(props.token, {
      scope: scope.value,
      unreadOnly: unreadOnly.value,
      eventType: eventType.value,
      priority: priority.value,
      keyword: keyword.value,
      page: page.value,
      size: pageSize
    })
    items.value = result.items || []
    total.value = result.total || 0
    unreadCount.value = result.unreadCount || 0
  } catch (err) {
    items.value = []
    total.value = 0
    error.value = err instanceof Error ? err.message : '通知加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  keyword.value = searchKeyword.value.trim()
  page.value = 1
  void load()
}

function changeScope(nextScope: NotificationScope) {
  scope.value = nextScope
  page.value = 1
}

async function openItem(item: NotificationInfo) {
  if (item.unread && props.token) {
    try {
      await markNotificationRead(props.token, item.id)
      item.unread = false
      item.state = 'READ'
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // The detail navigation remains available if the read-state request is interrupted.
    }
  }
  emit('open', item)
}

async function toggleRead(item: NotificationInfo) {
  try {
    if (item.unread) {
      await markNotificationRead(props.token, item.id)
      item.unread = false
      item.state = 'READ'
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } else {
      await markNotificationUnread(props.token, item.id)
      item.unread = true
      item.state = 'UNREAD'
      unreadCount.value += 1
    }
  } catch (err) {
    emit('notice', err instanceof Error ? err.message : '通知状态更新失败')
  }
}

async function archiveItem(item: NotificationInfo) {
  try {
    await archiveNotification(props.token, item.id)
    items.value = items.value.filter(current => current.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    if (item.unread) unreadCount.value = Math.max(0, unreadCount.value - 1)
    emit('notice', '通知已归档')
  } catch (err) {
    emit('notice', err instanceof Error ? err.message : '通知归档失败')
  }
}

async function markAllRead() {
  if (unreadCount.value === 0) return
  try {
    await markAllNotificationsRead(props.token)
    items.value.forEach(item => {
      item.unread = false
      if (item.state === 'UNREAD') item.state = 'READ'
    })
    unreadCount.value = 0
    emit('notice', '已全部标记为已读')
  } catch (err) {
    emit('notice', err instanceof Error ? err.message : '全部已读失败')
  }
}

function previousPage() {
  if (!hasPrevious.value) return
  page.value -= 1
}

function nextPage() {
  if (!hasNext.value) return
  page.value += 1
}

watch([scope, unreadOnly, eventType, priority, page], () => void load())
onMounted(() => void load())
</script>

<template>
  <section class="notification-center-page">
    <header class="notification-center-header">
      <div>
        <span class="section-eyebrow">WORKSPACE</span>
        <h1>通知中心</h1>
        <p>集中查看需要你关注的工单、报工和系统动态</p>
      </div>
      <button class="notification-center-refresh" type="button" title="刷新通知" aria-label="刷新通知" :disabled="loading" @click="load">
        <UiIcon name="refresh" :size="16" />
        <span>刷新</span>
      </button>
    </header>

    <div class="notification-center-tabs" role="tablist" aria-label="通知范围">
      <button type="button" :class="{ active: scope === 'active' && !unreadOnly }" @click="unreadOnly = false; changeScope('active')">当前通知</button>
      <button type="button" :class="{ active: unreadOnly }" @click="unreadOnly = true; changeScope('active')">未读 <b v-if="unreadCount">{{ unreadCount > 99 ? '99+' : unreadCount }}</b></button>
      <button type="button" :class="{ active: scope === 'all' }" @click="unreadOnly = false; changeScope('all')">全部记录</button>
      <button type="button" :class="{ active: scope === 'history' }" @click="unreadOnly = false; changeScope('history')">已归档</button>
    </div>

    <div class="notification-center-filter-bar">
      <div class="notification-center-search">
        <UiIcon name="search" :size="15" />
        <input v-model="searchKeyword" type="search" placeholder="搜索通知标题或内容" @keydown.enter="search" />
        <button v-if="searchKeyword" type="button" title="清除搜索" aria-label="清除搜索" @click="searchKeyword = ''; search()">
          <UiIcon name="close" :size="14" />
        </button>
      </div>
      <select v-model="eventType" aria-label="通知类型">
        <option value="">全部类型</option>
        <option value="REPORT_PENDING_APPROVAL">待审批报工</option>
        <option value="REPORT_APPROVED">已通过报工</option>
        <option value="REPORT_REJECTED">被驳回报工</option>
        <option value="WORK_ORDER_OVERDUE">逾期工单</option>
        <option value="REPORT_SUBMITTED">报工动态</option>
      </select>
      <select v-model="priority" aria-label="通知级别">
        <option value="">全部级别</option>
        <option value="URGENT">紧急</option>
        <option value="HIGH">重要</option>
        <option value="NORMAL">普通</option>
        <option value="LOW">低</option>
      </select>
      <button v-if="unreadCount" class="notification-center-read-all" type="button" @click="markAllRead">
        <UiIcon name="check" :size="15" />
        全部已读
      </button>
    </div>

    <div class="notification-center-summary">
      <span>共 {{ total }} 条记录</span>
      <span v-if="unreadCount" class="notification-center-unread-summary">{{ unreadCount }} 条未读</span>
      <span v-if="keyword">搜索：{{ keyword }}</span>
    </div>

    <div v-if="loading" class="notification-center-state">正在加载通知...</div>
    <div v-else-if="error" class="notification-center-state error">{{ error }} <button type="button" @click="load">重试</button></div>
    <div v-else-if="items.length === 0" class="notification-center-state">
      <UiIcon name="bell" :size="28" />
      <strong>暂无通知</strong>
      <span>{{ unreadOnly ? '当前没有未读通知' : '新的业务动态会显示在这里' }}</span>
    </div>
    <div v-else class="notification-center-list">
      <article v-for="item in items" :key="item.id" class="notification-center-item" :class="{ unread: item.unread }">
        <button type="button" class="notification-center-item-main" @click="openItem(item)">
          <span class="notification-center-icon" :class="item.priority.toLowerCase()"><UiIcon :name="iconName(item)" :size="16" /></span>
          <span class="notification-center-item-body">
            <span class="notification-center-item-title">
              <strong>{{ item.title }}</strong>
              <em>{{ priorityLabel(item.priority) }}</em>
            </span>
            <span class="notification-center-item-content">{{ item.content }}</span>
            <span class="notification-center-item-meta">
              <time :title="formatDate(item.createdAt)">{{ relativeTime(item.createdAt) }}</time>
              <span v-if="item.sourceType">{{ item.sourceType === 'WORK_ORDER' ? '工单' : '报工' }}</span>
              <span v-if="item.resolvedAt">已完成</span>
            </span>
          </span>
          <span v-if="item.unread" class="notification-center-unread-dot" aria-label="未读"></span>
        </button>
        <div class="notification-center-item-actions">
          <button type="button" :title="item.unread ? '标记为已读' : '标记为未读'" :aria-label="item.unread ? '标记为已读' : '标记为未读'" @click.stop="toggleRead(item)">
            <UiIcon :name="item.unread ? 'check' : 'bell'" :size="14" />
          </button>
          <button type="button" title="归档通知" aria-label="归档通知" @click.stop="archiveItem(item)">
            <UiIcon name="archive" :size="14" />
          </button>
        </div>
      </article>
    </div>

    <footer v-if="total > pageSize" class="notification-center-pagination">
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <div>
        <button type="button" title="上一页" aria-label="上一页" :disabled="!hasPrevious" @click="previousPage"><UiIcon name="arrowLeft" :size="15" /></button>
        <button type="button" title="下一页" aria-label="下一页" :disabled="!hasNext" @click="nextPage"><UiIcon name="arrowRight" :size="15" /></button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.notification-center-page {
  min-height: 100%;
  padding: 26px 30px 40px;
  color: var(--text-primary, #1f2725);
}

.notification-center-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--border-color, #e4e9e7);
}

.section-eyebrow {
  color: var(--accent-color, #00b889);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.3px;
}

h1 { margin: 5px 0 2px; font-size: 24px; line-height: 32px; }
.notification-center-header p { margin: 0; color: var(--text-secondary, #73807b); font-size: 13px; }

.notification-center-refresh,
.notification-center-read-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-color, #dfe7e4);
  border-radius: 4px;
  background: var(--surface-color, #fff);
  color: var(--text-secondary, #59635f);
  cursor: pointer;
}

.notification-center-refresh { padding: 8px 12px; }
.notification-center-refresh:hover,
.notification-center-read-all:hover { border-color: var(--accent-color, #00b889); color: var(--accent-color, #00b889); }
.notification-center-refresh:disabled { cursor: wait; opacity: .6; }

.notification-center-tabs { display: flex; gap: 24px; padding: 18px 0 12px; }
.notification-center-tabs button { position: relative; padding: 0 0 9px; border: 0; background: transparent; color: var(--text-secondary, #65716e); cursor: pointer; font-size: 13px; }
.notification-center-tabs button.active { color: var(--accent-color, #00b889); font-weight: 700; }
.notification-center-tabs button.active::after { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; background: var(--accent-color, #00b889); content: ''; }
.notification-center-tabs b { display: inline-block; min-width: 17px; margin-left: 3px; padding: 1px 5px; border-radius: 10px; background: #e45145; color: #fff; font-size: 10px; line-height: 15px; }

.notification-center-filter-bar { display: flex; align-items: center; gap: 10px; padding: 12px; border: 1px solid var(--border-color, #e4e9e7); background: var(--surface-subtle, #f7faf9); }
.notification-center-search { display: flex; align-items: center; flex: 1 1 280px; gap: 8px; min-width: 180px; height: 34px; padding: 0 9px; border: 1px solid var(--border-color, #dfe7e4); border-radius: 4px; background: var(--surface-color, #fff); color: var(--text-muted, #87918e); }
.notification-center-search input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: inherit; font: inherit; }
.notification-center-search button { display: inline-flex; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; }
.notification-center-filter-bar select { height: 34px; padding: 0 28px 0 9px; border: 1px solid var(--border-color, #dfe7e4); border-radius: 4px; background: var(--surface-color, #fff); color: var(--text-primary, #1f2725); }
.notification-center-read-all { flex: 0 0 auto; height: 34px; padding: 0 10px; border-color: transparent; background: var(--accent-color, #00b889); color: #fff; }
.notification-center-read-all:hover { background: #009f77; color: #fff; }

.notification-center-summary { display: flex; gap: 16px; padding: 15px 2px 10px; color: var(--text-muted, #87918e); font-size: 12px; }
.notification-center-unread-summary { color: #e45145; }
.notification-center-state { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; min-height: 260px; border: 1px solid var(--border-color, #e4e9e7); color: var(--text-muted, #87918e); font-size: 13px; }
.notification-center-state strong { color: var(--text-primary, #1f2725); font-size: 15px; }
.notification-center-state.error { flex-direction: row; min-height: 120px; color: #d74434; }
.notification-center-state.error button { border: 0; background: transparent; color: inherit; cursor: pointer; text-decoration: underline; }

.notification-center-list { border: 1px solid var(--border-color, #e4e9e7); background: var(--surface-color, #fff); }
.notification-center-item { display: flex; align-items: stretch; border-bottom: 1px solid var(--border-color, #eef2f0); }
.notification-center-item:last-child { border-bottom: 0; }
.notification-center-item.unread { background: color-mix(in srgb, var(--accent-color, #00b889) 5%, var(--surface-color, #fff)); }
.notification-center-item-main { display: flex; flex: 1; align-items: flex-start; gap: 13px; padding: 17px 18px; border: 0; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.notification-center-item-main:hover { background: color-mix(in srgb, var(--accent-color, #00b889) 8%, transparent); }
.notification-center-icon { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 32px; width: 32px; height: 32px; border-radius: 50%; background: #e8f7f2; color: #00a77d; }
.notification-center-icon.high, .notification-center-icon.urgent { background: #fff0ee; color: #e45145; }
.notification-center-icon.low { background: #f1f4f3; color: #75827e; }
.notification-center-item-body { display: flex; flex: 1; flex-direction: column; gap: 5px; min-width: 0; }
.notification-center-item-title { display: flex; align-items: center; gap: 8px; }
.notification-center-item-title strong { font-size: 14px; line-height: 20px; }
.notification-center-item-title em { padding: 1px 5px; border-radius: 3px; background: var(--surface-subtle, #f1f5f3); color: var(--text-muted, #87918e); font-size: 11px; font-style: normal; }
.notification-center-item-content { overflow: hidden; color: var(--text-secondary, #65716e); font-size: 13px; line-height: 20px; text-overflow: ellipsis; white-space: nowrap; }
.notification-center-item-meta { display: flex; gap: 12px; color: var(--text-muted, #9aa5a1); font-size: 12px; }
.notification-center-unread-dot { align-self: center; flex: 0 0 8px; width: 8px; height: 8px; margin-right: 3px; border-radius: 50%; background: #e45145; }
.notification-center-item-actions { display: flex; align-items: center; gap: 4px; padding: 0 14px 0 4px; }
.notification-center-item-actions button,
.notification-center-pagination button { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; border: 1px solid transparent; border-radius: 4px; background: transparent; color: var(--text-muted, #87918e); cursor: pointer; }
.notification-center-item-actions button:hover,
.notification-center-pagination button:not(:disabled):hover { border-color: var(--border-color, #dfe7e4); color: var(--accent-color, #00b889); }
.notification-center-pagination { display: flex; align-items: center; justify-content: space-between; padding: 15px 2px 0; color: var(--text-muted, #87918e); font-size: 12px; }
.notification-center-pagination div { display: flex; gap: 5px; }
.notification-center-pagination button { border-color: var(--border-color, #dfe7e4); background: var(--surface-color, #fff); }
.notification-center-pagination button:disabled { cursor: not-allowed; opacity: .45; }

:global(.admin-shell.dark-theme) .notification-center-icon { background: #18352e; }
:global(.admin-shell.dark-theme) .notification-center-icon.high,
:global(.admin-shell.dark-theme) .notification-center-icon.urgent { background: #422725; }
:global(.admin-shell.dark-theme) .notification-center-icon.low { background: #293238; }

@media (max-width: 760px) {
  .notification-center-page { padding: 20px 16px 30px; }
  .notification-center-filter-bar { align-items: stretch; flex-wrap: wrap; }
  .notification-center-search { flex-basis: 100%; }
  .notification-center-filter-bar select { flex: 1; }
  .notification-center-read-all { flex: 1; justify-content: center; }
  .notification-center-item-actions { padding-right: 6px; }
}
</style>
