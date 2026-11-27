<template>
  <view class="container" role="main" aria-label="工序任务">
    <view class="header ui-page-head">
      <text class="title" role="heading" aria-level="1">任务</text>
      <IconButton icon="refreshempty" label="刷新任务" :busy="loading" @click="loadTasks" />
    </view>
    <text class="search-label">工单 / 产品</text>
    <view class="search-row">
      <input v-model="keyword" maxlength="128" placeholder="工单号 / 产品" aria-label="搜索工单或产品" confirm-type="search" @confirm="loadTasks" />
      <IconButton v-if="keyword" icon="closeempty" label="清空任务搜索" @click="keyword = ''; loadTasks()" />
      <IconButton icon="search" label="搜索任务" @click="loadTasks" />
    </view>
    <view class="coverage-notice">
      可报 {{ tasks.length }} 项 · 已查 {{ loadedOrders }} / {{ orderTotal }} 工单
    </view>
    <text class="sr-only" role="status">{{ checkingTaskId ? '正在确认报工权限…' : '' }}</text>
    <scroll-view scroll-y class="task-list" role="region" aria-label="任务列表" tabindex="0" @scrolltolower="loadMore">
      <view v-for="task in tasks" :key="task.id" class="task-card-row">
      <button class="task-card ui-card-button" :class="{ 'permission-checking': checkingTaskId === task.id }"
              :aria-label="task.processName + '，' + task.workOrderCode + '，报工'" :aria-busy="checkingTaskId === task.id"
              :disabled="!!checkingTaskId" @click="handleTask(task)">
        <view class="task-info">
          <text class="task-name">{{ task.processName }}</text>
          <text class="task-order">{{ task.workOrderCode }} | {{ task.productName }}</text>
          <view class="progress-row">
            <view class="progress-bar">
              <view class="progress-fill" :style="{ width: task.progress + '%' }"></view>
            </view>
            <text class="progress-text">{{ task.completedQuantity + task.defectQuantity }}/{{ task.plannedQuantity }}</text>
          </view>
        </view>
        <text v-if="checkingTaskId === task.id" class="task-check-label" aria-hidden="true">确认权限中…</text>
        <uni-icons v-else type="right" size="16" color="#ccc" aria-hidden="true"></uni-icons>
      </button>
      <text v-if="permissionError?.taskId === task.id" class="task-permission-error" role="alert">{{ permissionError.message }}</text>
      </view>

      <view v-if="tasks.length === 0 && !loading && !errorMessage" class="empty">
        <text class="empty-text">{{ hasMore ? '暂无可报工序，可继续加载' : '暂无可报工序' }}</text>
      </view>
      <view v-if="errorMessage" class="state" role="alert">
        <text>{{ errorMessage }}</text>
        <IconButton icon="refreshempty" label="重试任务列表" @click="retryPage" />
      </view>
      <view v-else-if="loading" class="state"><text>加载中...</text></view>
      <view v-else-if="hasMore" class="load-more">
        <IconButton icon="arrow-down" label="加载更多工单" @click="loadMore" />
      </view>
    </scroll-view>
    <!-- #ifdef H5 -->
    <MobileTabBar current="tasks" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import { ensureLogin, fetchWorkOrderPage, fetchWorkOrderReporters } from '../../services/api'
import type { WorkOrderInfo, WorkOrderTask } from '../../types/api'
import MobileTabBar from '../../components/MobileTabBar.vue'
import IconButton from '../../components/IconButton.vue'
import { canReportTask } from '../../utils/report-permission'
import { mobileSessionKey } from '../../utils/session-key'
import { navigatePage } from '../../utils/navigation'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'

const pageActivity = useMobilePageActivity()

interface TaskCard {
  id: string
  workOrderId: number
  workOrderCode: string
  productName: string
  processName: string
  sequenceNo: number
  plannedQuantity: number
  completedQuantity: number
  defectQuantity: number
  reportPermission?: string
  progress: number
}

const tasks = ref<TaskCard[]>([])
const loading = ref(false)
const orderTotal = ref(0)
const loadedOrders = ref(0)
const errorMessage = ref('')
const keyword = ref('')
const hasMore = ref(false)
const checkingTaskId = ref('')
const permissionError = ref<{ taskId: string; message: string } | null>(null)
const pageSize = 20
type TaskQuery = { keyword: string; session: string }
let activeQuery: TaskQuery | undefined
let nextPage = 1
const seen = new Set<number>()
let permissionSequence = 0
let pageAlive = true

onShow(loadTasks)
onMounted(loadTasks)
onHide(() => { activeQuery = undefined; loading.value = false; resetPermissionCheck() })
onBeforeUnmount(() => { pageAlive = false; activeQuery = undefined; permissionSequence++ })

function resetPermissionCheck() {
  permissionSequence++
  checkingTaskId.value = ''
  permissionError.value = null
}

async function loadTasks() {
  const query = { keyword: keyword.value.trim(), session: mobileSessionKey() }
  if (loading.value && activeQuery?.keyword === query.keyword && activeQuery.session === query.session) return
  resetPermissionCheck()
  activeQuery = query
  tasks.value = []
  loadedOrders.value = 0
  orderTotal.value = 0
  hasMore.value = false
  errorMessage.value = ''
  seen.clear()
  nextPage = 1
  if (!ensureLogin()) {
    activeQuery = undefined
    loading.value = false
    return
  }
  await requestTaskPage(query, 1)
}

async function requestTaskPage(query: TaskQuery, page: number) {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await fetchWorkOrderPage({ page, size: pageSize, keyword: query.keyword, reportableOnly: true })
    if (activeQuery !== query || mobileSessionKey() !== query.session) return
    if (!Array.isArray(res?.items) || !Number.isSafeInteger(res.total) || res.total < 0) {
      throw new Error('工单列表返回格式异常，请重试')
    }
    orderTotal.value = res.total
    for (const order of res.items) {
      if (seen.has(order.id)) continue
      seen.add(order.id)
      loadedOrders.value += 1
      tasks.value.push(...buildTaskCards(order))
    }
    hasMore.value = res.items.length === pageSize && page * pageSize < res.total
    nextPage = page + 1
  } catch (e: any) {
    if (activeQuery === query && mobileSessionKey() === query.session) errorMessage.value = e.message || '加载任务失败'
  } finally {
    if (activeQuery === query) {
      loading.value = false
      if (mobileSessionKey() !== query.session) {
        activeQuery = undefined
        tasks.value = []
        loadedOrders.value = 0
        orderTotal.value = 0
        hasMore.value = false
        errorMessage.value = '登录状态已变更，请刷新'
      }
    }
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  await retryPage()
}

async function retryPage() {
  if (loading.value) return
  if (!activeQuery || mobileSessionKey() !== activeQuery.session) return loadTasks()
  await requestTaskPage(activeQuery, nextPage)
}

function buildTaskCards(order: WorkOrderInfo): TaskCard[] {
  if (isFinishedOrder(order) || !order.tasks || order.tasks.length === 0) {
    return []
  }
  return order.tasks
    .map((task, index) => ({ ...task, sequenceNo: task.sequenceNo || index + 1 }))
    .filter(task => task.enabled !== false)
    .filter(task => hasReportPermission(task))
    .filter(task => {
      const planned = task.plannedQuantity || order.plannedQuantity || 0
      const completed = (task.completedQuantity || 0) + (task.defectQuantity || 0)
      return planned <= 0 || completed < planned
    })
    .map(task => {
      const planned = task.plannedQuantity || order.plannedQuantity || 0
      const goodQuantity = task.completedQuantity || 0
      const defectQuantity = task.defectQuantity || 0
      const completed = goodQuantity + defectQuantity
      const sequenceNo = task.sequenceNo
      return {
        id: `${order.id}-${sequenceNo}`,
        workOrderId: order.id,
        workOrderCode: order.code,
        productName: order.productName || '-',
        processName: task.processName || `工序${sequenceNo}`,
        sequenceNo,
        plannedQuantity: planned,
        completedQuantity: goodQuantity,
        defectQuantity,
        reportPermission: task.reportPermission,
        progress: planned > 0 ? Math.min(100, Math.round((completed / planned) * 100)) : 0
      }
    })
}

function isFinishedOrder(order: WorkOrderInfo) {
  return !['NOT_STARTED', 'EXECUTING', 'IN_PROGRESS'].includes(order.status)
}

function hasReportPermission(task: WorkOrderTask) {
  return canReportTask(task, uni.getStorageSync('user'))
}

async function handleTask(task: TaskCard) {
  if (!pageAlive || checkingTaskId.value || !ensureLogin()) return
  const session = mobileSessionKey()
  const sequence = ++permissionSequence
  const query = activeQuery
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && sequence === permissionSequence && query === activeQuery
  checkingTaskId.value = task.id
  permissionError.value = null
  try {
    const users = await fetchWorkOrderReporters(task.workOrderId, task.sequenceNo)
    if (!current()) return
    if (mobileSessionKey() !== session) throw new Error('登录状态已变更，请刷新')
    if (!users.some(user => user.id === uni.getStorageSync('user')?.id)) throw new Error('您没有此工序的报工权限')
  } catch (e: any) {
    if (!current()) return
    if (mobileSessionKey() !== session) uni.showToast({ title: '登录状态已变更，请刷新', icon: 'none' })
    else permissionError.value = { taskId: task.id, message: e?.message || '报工权限确认失败，请重试' }
    return
  } finally {
    if (pageAlive && sequence === permissionSequence) checkingTaskId.value = ''
  }
  const url = `/pages/report-form/index?orderId=${task.workOrderId}&processNo=${task.sequenceNo}&processName=${encodeURIComponent(task.processName)}&plannedQty=${task.plannedQuantity}&completedQty=${task.completedQuantity}&defectQty=${task.defectQuantity}&reportPermission=${encodeURIComponent(task.reportPermission || '')}`
  navigatePage(url)
}
</script>

<style scoped>
.container {
  padding: 40rpx;
  background: var(--mobile-bg, #f6f7f7);
  min-height: 100vh;
  padding-bottom: 130rpx;
  box-sizing: border-box;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  margin-bottom: 20rpx;
}

.title {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--mobile-text, #333);
  display: block;
}

.subtitle {
  font-size: 26rpx;
  color: var(--mobile-muted, #64756d);
  margin-top: 10rpx;
}

.task-list {
  height: 0;
  min-height: 0;
  flex: 1;
}

.search-row { display: flex; align-items: center; gap: 16rpx; }
.search-label { display: block; margin-bottom: 10rpx; font-size: 24rpx; color: var(--mobile-muted, #536b60); }
.search-row input { flex: 1; min-width: 0; background: var(--mobile-surface, #fff); border-radius: 12rpx; padding: 20rpx; font-size: 26rpx; }
.search-btn { font-size: 24rpx; color: var(--mobile-link, #00856b); margin: 0; }
.coverage-notice { color: var(--mobile-muted, #64756d); padding: 16rpx 0; font-size: 24rpx; line-height: 1.5; }
.load-more { padding: 20rpx 0; display: flex; justify-content: center; }

.task-list::-webkit-scrollbar { display: none; }

.task-card-row { margin-bottom: 24rpx; }
.task-card {
  background: var(--mobile-surface, white);
  padding: 30rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
  width: 100%;
}

.task-card[disabled] { color: var(--mobile-text, #333); cursor: default; }
.task-card.permission-checking { background: var(--mobile-subtle, #e6f7f3); }
.task-check-label { flex-shrink: 0; font-size: 23rpx; color: var(--mobile-link, #007a60); }
.task-permission-error { display: block; padding: 12rpx 18rpx; color: var(--mobile-error, #a0332c); font-size: 24rpx; overflow-wrap: anywhere; }

.task-icon {
  width: 80rpx;
  height: 80rpx;
  background: var(--mobile-subtle, #f0faf7);
  border-radius: 40rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 24rpx;
  flex-shrink: 0;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-name {
  font-size: 32rpx;
  font-weight: bold;
  color: var(--mobile-text, #333);
  display: block;
}

.task-order {
  font-size: 24rpx;
  color: var(--mobile-muted, #64756d);
  margin-top: 6rpx;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 18rpx;
}

.progress-bar {
  flex: 1;
  height: 10rpx;
  background: var(--mobile-bg, #edf0ef);
  border-radius: 999rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #00856b;
  border-radius: 999rpx;
}

.progress-text {
  font-size: 22rpx;
  color: var(--mobile-muted, #6b7471);
  min-width: 110rpx;
  text-align: right;
}

.state,
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 100rpx;
  color: var(--mobile-muted, #64756d);
  font-size: 28rpx;
}

.retry-btn {
  margin-top: 24rpx;
  background: #00856b;
  color: white;
  border-radius: 999rpx;
  font-size: 26rpx;
  padding: 0 40rpx;
}

.empty-text {
  font-size: 26rpx;
  color: var(--mobile-muted, #64756d);
}
</style>
