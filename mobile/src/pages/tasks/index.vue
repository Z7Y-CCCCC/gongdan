<template>
  <view class="container">
    <view class="header">
      <text class="title">我的待办任务</text>
      <text class="subtitle">共 {{ tasks.length }} 项待处理工序</text>
    </view>

    <ListLimitNotice :total="orderTotal" :loaded="loadedOrders" label="工单" />

    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <view v-else-if="errorMessage" class="state">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadTasks">重试</button>
    </view>

    <scroll-view v-else scroll-y class="task-list">
      <view v-for="task in tasks" :key="task.id" class="task-card" @click="handleTask(task)">
        <view class="task-icon">
          <uni-icons type="paperplane-filled" size="24" color="#00b889"></uni-icons>
        </view>
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
        <uni-icons type="right" size="16" color="#ccc"></uni-icons>
      </view>

      <view v-if="tasks.length === 0" class="empty">
        <text class="empty-text">当前暂无指派给您的任务</text>
      </view>
    </scroll-view>
    <!-- #ifdef H5 -->
    <MobileTabBar current="tasks" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { ensureLogin, fetchWorkOrders, fetchWorkOrderReporters } from '../../services/api'
import type { WorkOrderInfo, WorkOrderTask } from '../../types/api'
import MobileTabBar from '../../components/MobileTabBar.vue'
import ListLimitNotice from '../../components/ListLimitNotice.vue'
import { canReportTask } from '../../utils/report-permission'

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
let checkingPermission = false

onShow(() => {
  if (!ensureLogin()) return
  loadTasks()
})

onMounted(() => {
  if (!ensureLogin()) return
  loadTasks()
})

async function loadTasks() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await fetchWorkOrders()
    orderTotal.value = res.total
    loadedOrders.value = res.items.length
    tasks.value = (res.items || [])
      .flatMap(order => buildTaskCards(order))
      .sort((a, b) => a.workOrderCode.localeCompare(b.workOrderCode) || a.sequenceNo - b.sequenceNo)
  } catch (e: any) {
    tasks.value = []
    orderTotal.value = 0
    loadedOrders.value = 0
    errorMessage.value = e.message || '加载任务失败'
  } finally {
    loading.value = false
  }
}

function buildTaskCards(order: WorkOrderInfo): TaskCard[] {
  if (isFinishedOrder(order) || !order.tasks || order.tasks.length === 0) {
    return []
  }
  return order.tasks
    .filter(task => task.enabled !== false)
    .filter(task => hasReportPermission(task))
    .filter(task => {
      const planned = task.plannedQuantity || order.plannedQuantity || 0
      const completed = (task.completedQuantity || 0) + (task.defectQuantity || 0)
      return planned <= 0 || completed < planned
    })
    .map((task, index) => {
      const planned = task.plannedQuantity || order.plannedQuantity || 0
      const goodQuantity = task.completedQuantity || 0
      const defectQuantity = task.defectQuantity || 0
      const completed = goodQuantity + defectQuantity
      const sequenceNo = task.sequenceNo || index + 1
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
  if (checkingPermission || !ensureLogin()) return
  checkingPermission = true
  try {
    const users = await fetchWorkOrderReporters(task.workOrderId, task.sequenceNo)
    if (!users.some(user => user.id === uni.getStorageSync('user')?.id)) throw new Error('您没有此工序的报工权限')
  } catch (e: any) {
    uni.showToast({ title: e.message || '报工权限确认失败，请重试', icon: 'none' })
    return
  } finally {
    checkingPermission = false
  }
  const url = `/pages/report-form/index?orderId=${task.workOrderId}&processNo=${task.sequenceNo}&processName=${encodeURIComponent(task.processName)}&plannedQty=${task.plannedQuantity}&completedQty=${task.completedQuantity}&defectQty=${task.defectQuantity}&reportPermission=${encodeURIComponent(task.reportPermission || '')}`
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = `#${url}`
    return
  }
  uni.navigateTo({
    url
  })
}
</script>

<style scoped>
.container {
  padding: 40rpx;
  background: #f6f7f7;
  min-height: 100vh;
  padding-bottom: 130rpx;
  box-sizing: border-box;
}

.header {
  margin-bottom: 40rpx;
}

.title {
  font-size: 44rpx;
  font-weight: bold;
  color: #333;
  display: block;
}

.subtitle {
  font-size: 26rpx;
  color: #999;
  margin-top: 10rpx;
}

.task-list {
  height: calc(100vh - 160rpx);
}

.task-list::-webkit-scrollbar { display: none; }

.task-card {
  background: white;
  padding: 30rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
}

.task-icon {
  width: 80rpx;
  height: 80rpx;
  background: #f0faf7;
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
  color: #333;
  display: block;
}

.task-order {
  font-size: 24rpx;
  color: #999;
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
  background: #edf0ef;
  border-radius: 999rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #00b889;
  border-radius: 999rpx;
}

.progress-text {
  font-size: 22rpx;
  color: #6b7471;
  min-width: 110rpx;
  text-align: right;
}

.state,
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 100rpx;
  color: #999;
  font-size: 28rpx;
}

.retry-btn {
  margin-top: 24rpx;
  background: #00b889;
  color: white;
  border-radius: 999rpx;
  font-size: 26rpx;
  padding: 0 40rpx;
}

.empty-text {
  font-size: 26rpx;
  color: #999;
}
</style>
