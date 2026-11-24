<script setup lang="ts">
/**
 * LiveBroadcastSection - 生产实时播报区
 *
 * 按 createdAt 倒序展示最近 20 条报工记录。
 * 挂载时启动 30 秒轮询，增量追加新记录到列表顶部。
 * 列表超过 20 条时丢弃旧记录。
 * 页面离开时停止轮询。
 *
 * Requirements: 10.1-10.8
 */
import { ref, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { fetchHomeRecentReports, type RecentReportItem } from '../api'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'

const MAX_ITEMS = 20
const POLL_INTERVAL_MS = 30000

const props = defineProps<{
  token: string
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'viewWorkOrder', workOrderId: number): void
}>()

const { handleApiError } = useHomeDashboardErrorHandler()

// --- State ---
const reports = ref<RecentReportItem[]>([])
const initialLoaded = ref(false)
let pollingTimer: ReturnType<typeof setInterval> | null = null
let componentActive = true
let disposed = false
let requestInFlight = false

// --- Helpers ---

/**
 * Format createdAt from "yyyy-MM-dd HH:mm:ss" to "YYYY-MM-DD HH:mm"
 * Simply trim the last 3 characters (":ss")
 */
function formatTime(createdAt: string): string {
  return createdAt.substring(0, 16).replace('T', ' ')
}

/**
 * Get the createdAt of the most recent report in the list (for incremental polling)
 */
function getLastCreatedAt(): string | undefined {
  if (reports.value.length === 0) return undefined
  return reports.value[0].createdAt
}

// --- Data loading ---

async function loadInitial() {
  if (disposed || requestInFlight) return
  requestInFlight = true
  try {
    const result = await fetchHomeRecentReports(props.token, {})
    if (disposed) return
    reports.value = result.items.slice(0, MAX_ITEMS)
  } catch (error) {
    if (!disposed && !handleApiError(error)) {
      // Non-401/403 error: silently ignore, keep current list
    }
  } finally {
    requestInFlight = false
    if (!disposed) initialLoaded.value = true
  }
}

async function pollNewReports() {
  if (disposed || !componentActive || !props.active || requestInFlight) return
  const since = getLastCreatedAt()
  if (!since) {
    // If list is empty, do a full load
    await loadInitial()
    return
  }
  requestInFlight = true
  try {
    const result = await fetchHomeRecentReports(props.token, { since })
    if (disposed) return
    if (result.items.length > 0) {
      // Protect the list from duplicate rows if a server cursor is ever rounded.
      const knownIds = new Set(reports.value.map(item => item.id))
      const newItems = result.items.filter(item => !knownIds.has(item.id))
      reports.value = [...newItems, ...reports.value].slice(0, MAX_ITEMS)
    }
  } catch (error) {
    if (!disposed && !handleApiError(error)) {
      // Non-401/403 error: silently ignore, keep current list
    }
  } finally {
    requestInFlight = false
  }
}

// --- Polling control ---

function startPolling() {
  stopPolling()
  if (disposed || !componentActive || !props.active) return
  pollingTimer = setInterval(pollNewReports, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollingTimer !== null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// Watch active prop: stop polling when false, restart when true
watch(() => props.active, (isActive) => {
  if (isActive) {
    startPolling()
  } else {
    stopPolling()
  }
})

// --- Lifecycle ---

onMounted(async () => {
  await loadInitial()
  if (props.active) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  disposed = true
  stopPolling()
})

onActivated(() => {
  componentActive = true
  if (initialLoaded.value && props.active) {
    void pollNewReports()
    startPolling()
  }
})

onDeactivated(() => {
  componentActive = false
  stopPolling()
})
</script>

<template>
  <div class="live-broadcast">
    <h3 class="live-broadcast__title">生产动态</h3>

    <!-- Empty state -->
    <div v-if="initialLoaded && reports.length === 0" class="live-broadcast__empty">
      暂无播报
    </div>

    <!-- Report list -->
    <ul v-else class="live-broadcast__list">
      <li
        v-for="report in reports"
        :key="report.id"
        class="live-broadcast__item"
      >
        <span class="live-broadcast__dot">●</span>
        <span class="live-broadcast__time">{{ formatTime(report.createdAt) }}</span>
        <span class="live-broadcast__text">{{ report.reporterName }} {{ report.processName }}</span>
        <button
          class="live-broadcast__link"
          type="button"
          :title="`查看工单 ${report.workOrderCode}`"
          :aria-label="`查看工单 ${report.workOrderCode}`"
          @click="emit('viewWorkOrder', report.workOrderId)"
        >
          {{ report.workOrderCode }}
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.live-broadcast {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.live-broadcast__title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.live-broadcast__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
}

.live-broadcast__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  max-height: 360px;
}

.live-broadcast__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #333;
  line-height: 1.5;
  flex-wrap: wrap;
}

.live-broadcast__dot {
  color: #409eff;
  font-size: 10px;
  flex-shrink: 0;
}

.live-broadcast__time {
  color: #999;
  white-space: nowrap;
  flex-shrink: 0;
}

.live-broadcast__text {
  color: #333;
  white-space: nowrap;
}

.live-broadcast__link {
  color: var(--accent, #00b889);
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
}

.live-broadcast__link:hover {
  text-decoration: underline;
}
</style>
