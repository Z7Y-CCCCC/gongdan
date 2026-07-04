<template>
  <view class="container">
    <!-- 顶部工单基本信息 -->
    <view class="header">
      <view class="status-row">
        <view class="status-tag" :class="statusClass(order.status)">{{ statusLabel(order.status) }}</view>
        <view v-if="daysRemain !== null" class="remain-tag" :class="{ overdue: daysRemain < 0 }">
          {{ daysRemain >= 0 ? '剩 ' + daysRemain + '天' : '超期 ' + Math.abs(daysRemain) + '天' }}
        </view>
      </view>
      <text class="order-code">{{ order.code || '' }}</text>
      <view class="info-list">
        <view class="info-item">
          <text class="info-icon">📅</text>
          <text class="info-label">计划时间：</text>
          <text class="info-val">{{ formatDate(order.planStart) }} ~ {{ formatDate(order.planEnd) }}</text>
        </view>
        <view class="info-item">
          <text class="info-icon">📦</text>
          <text class="info-label">产品信息：</text>
          <text class="info-val green-link">{{ order.productCode }} | {{ order.productName }}</text>
        </view>
        <view class="info-item">
          <text class="info-icon">📊</text>
          <text class="info-label">工单进度：</text>
          <text class="info-val">{{ order.goodQuantity || 0 }}/{{ order.plannedQuantity || 0 }}{{ productUnit }}</text>
        </view>
      </view>
      <!-- 进度条 -->
      <view class="progress-bar-wrap">
        <view class="progress-bar">
          <view class="progress-fill" :style="{ width: progressPercent + '%' }"></view>
        </view>
        <text class="progress-text">{{ progressPercent }}%</text>
      </view>
    </view>

    <!-- 页签切换 -->
    <view class="tabs">
      <view class="tab-item" :class="{ active: activeTab === 'progress' }" @tap="activeTab = 'progress'">
        生产进度
        <view v-if="activeTab === 'progress'" class="line"></view>
      </view>
      <view class="tab-item" :class="{ active: activeTab === 'material' }" @tap="activeTab = 'material'">
        用料明细
        <view v-if="activeTab === 'material'" class="line"></view>
      </view>
      <view class="tab-item" :class="{ active: activeTab === 'history' }" @tap="activeTab = 'history'">
        生产履历
        <view v-if="activeTab === 'history'" class="line"></view>
      </view>
    </view>

    <!-- 工序列表 (生产进度 tab) -->
    <scroll-view v-if="activeTab === 'progress'" scroll-y class="process-list">
      <view class="filter-bar">
        <text class="filter-text">筛选</text>
      </view>

      <view v-if="!order.tasks || order.tasks.length === 0" class="empty-hint">
        <text>暂无工序数据</text>
      </view>

      <view v-for="(step, index) in order.tasks" :key="index" class="process-card">
        <view class="card-top">
          <view class="step-title">
            <view class="step-num">{{ step.sequenceNo || (index + 1) }}</view>
            <text class="step-name">{{ step.processName || '工序' + (index + 1) }}</text>
          </view>
          <text class="step-status" :class="stepStatusClass(step)">{{ stepStatusText(step) }}</text>
        </view>

        <view class="card-content">
          <view class="data-columns">
            <view class="col">
              <text class="cl">计划数</text>
              <text class="cv">{{ step.plannedQuantity || order.plannedQuantity }}</text>
            </view>
            <view class="col">
              <text class="cl">良品数</text>
              <text class="cv green">{{ step.completedQuantity || 0 }}</text>
            </view>
            <view class="col">
              <text class="cl">不良数</text>
              <text class="cv red">{{ step.defectQuantity || 0 }}</text>
            </view>
          </view>
          
          <view class="progress-circle">
            <view class="circle-box" :style="circleBorderStyle(step)">
              <text class="percent-txt">{{ calculatePercent(step) }}%</text>
            </view>
          </view>
        </view>

        <view class="card-extra">
          <view class="extra-row">
            <text class="extra-icon">📋</text>
            <text class="extra-label">工单编号：</text>
            <text class="extra-val">{{ order.code }}</text>
          </view>
        </view>

        <view class="card-btns">
          <view class="btn ghost" @tap="viewStepDetail(step)">详情</view>
          <view
            class="btn"
            :class="hasReportPermission(step) ? 'primary' : 'disabled'"
            @tap="hasReportPermission(step) ? handleReport(step) : handleNoPermission()"
          >报工</view>
        </view>
      </view>
    </scroll-view>

    <!-- 用料明细 tab -->
    <scroll-view v-if="activeTab === 'material'" scroll-y class="process-list">
      <view class="empty-hint"><text>暂无用料明细</text></view>
    </scroll-view>

    <!-- 生产履历 tab -->
    <scroll-view v-if="activeTab === 'history'" scroll-y class="process-list">
      <view v-if="reports.length === 0" class="empty-hint"><text>暂无生产履历</text></view>
      <view v-for="item in reports" :key="item.id" class="history-card">
        <view class="history-head">
          <text class="history-title">{{ item.processName || '工序' }}</text>
          <text class="history-status" :class="reportStatusClass(item.status)">{{ reportStatusLabel(item.status) }}</text>
        </view>
        <view class="history-meta">
          <text>{{ item.reporterName || '-' }}</text>
          <text>{{ formatDate(item.createdAt) }}</text>
        </view>
        <view class="history-qty">
          <text>总数 {{ item.reportQuantity || 0 }}</text>
          <text class="green">良品 {{ item.goodQuantity || 0 }}</text>
          <text class="red">不良 {{ item.defectQuantity || 0 }}</text>
        </view>
        <view v-if="item.defectItemName || item.remark" class="history-note">
          <text>{{ item.defectItemName || item.remark }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="footer">
      <view class="f-btn" @tap="showMore">更多</view>
      <view class="f-btn" @tap="showPriority">优先级</view>
      <view class="f-btn finish" @tap="handleFinish">
        <text class="finish-icon">⊘</text>
        结束
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-mask">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchReports, fetchWorkOrder, fetchWorkOrderByCode, ensureLogin } from '../../services/api'
import type { WorkOrderInfo, WorkOrderReport } from '../../types/api'

const order = ref<WorkOrderInfo>({
  id: 0,
  code: '',
  productId: 0,
  productCode: '',
  productName: '',
  plannedQuantity: 0,
  goodQuantity: 0,
  defectQuantity: 0,
  status: '',
  priority: '',
  progressPercent: 0,
  tasks: []
})

const loading = ref(true)
const activeTab = ref('progress')
const productUnit = ref('个')
const reports = ref<WorkOrderReport[]>([])

// 从 URL 参数中获取 id 或 code
let orderId = ''
let orderCode = ''

onLoad((options) => {
  if (options?.id) {
    orderId = options.id
  }
  if (options?.code) {
    orderCode = options.code
  }
})

onMounted(async () => {

  // 设置 5 秒的加载安全网，防止网络挂起时永久白屏
  const safetyTimer = setTimeout(() => {
    if (loading.value) {
      loading.value = false
      uni.showToast({ title: '加载时间过长，请检查网络或后端服务', icon: 'none', duration: 4000 })
    }
  }, 5000)

  // H5 模式下：如果 onLoad 没拿到参数，从 URL hash 中手动解析
  if (!orderId && !orderCode && typeof window !== 'undefined') {
    const hash = window.location.hash || ''
    const idMatch = hash.match(/[?&]id=(\d+)/)
    if (idMatch) orderId = idMatch[1]
    const codeMatch = hash.match(/[?&]code=([^&]+)/)
    if (codeMatch) orderCode = decodeURIComponent(codeMatch[1])
  }

  if (!ensureLogin()) {
    loading.value = false
    clearTimeout(safetyTimer)
    return
  }

  try {
    await loadData()
  } finally {
    clearTimeout(safetyTimer)
  }
})

const loadData = async () => {
  loading.value = true
  try {
    let res: WorkOrderInfo
    if (orderId) {
      try {
        res = await fetchWorkOrder(orderId)
      } catch (e) {
        if (!orderCode) throw e
        res = await fetchWorkOrderByCode(orderCode)
      }
    } else if (orderCode) {
      res = await fetchWorkOrderByCode(orderCode)
    } else {
      uni.showToast({ title: '缺少工单参数', icon: 'none' })
      loading.value = false
      return
    }
    order.value = res
    await loadReports()
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const loadReports = async () => {
  if (!order.value.id && !order.value.code) return
  try {
    const res = await fetchReports()
    const items = res.items || []
    reports.value = items
      .filter(item => item.workOrderId === order.value.id || item.workOrderCode === order.value.code)
      .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
  } catch (e) {
    reports.value = []
  }
}

const progressPercent = computed(() => {
  if (!order.value.plannedQuantity) return 0
  return Math.min(100, Math.round(((order.value.goodQuantity || 0) / order.value.plannedQuantity) * 100))
})

const daysRemain = computed(() => {
  if (!order.value.planEnd) return null
  const end = new Date(order.value.planEnd)
  const now = new Date()
  const diff = Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return diff
})

function statusLabel(status: string) {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始', EXECUTING: '执行中', PAUSED: '已暂停',
    COMPLETED: '已完成', FINISHED: '已结束', CLOSED: '已关闭', CANCELED: '已取消'
  }
  return map[status] || status || '未知'
}

function statusClass(status: string) {
  const map: Record<string, string> = {
    NOT_STARTED: 'status-gray', EXECUTING: 'status-green', PAUSED: 'status-orange',
    COMPLETED: 'status-blue', FINISHED: 'status-blue', CLOSED: 'status-gray', CANCELED: 'status-red'
  }
  return map[status] || 'status-gray'
}

function stepStatusText(step: any) {
  const planned = step.plannedQuantity || order.value.plannedQuantity || 0
  const completed = step.completedQuantity || 0
  if (completed === 0) return '未开始'
  if (completed >= planned) return '已完成'
  return '执行中'
}

function stepStatusClass(step: any) {
  const text = stepStatusText(step)
  if (text === '执行中') return 'step-status-green'
  if (text === '已完成') return 'step-status-blue'
  return 'step-status-gray'
}

const calculatePercent = (step: any) => {
  const planned = step.plannedQuantity || order.value.plannedQuantity || 0
  if (!planned) return 0
  return Math.min(100, Math.round(((step.completedQuantity || 0) / planned) * 100))
}

function circleBorderStyle(step: any) {
  const pct = calculatePercent(step)
  const deg = (pct / 100) * 360
  if (pct === 0) return { borderColor: '#eef3fe' }
  if (pct >= 100) return { borderColor: '#00b889' }
  return { borderTopColor: '#4080ff', borderRightColor: deg > 90 ? '#4080ff' : '#eef3fe', borderBottomColor: deg > 180 ? '#4080ff' : '#eef3fe', borderLeftColor: deg > 270 ? '#4080ff' : '#eef3fe' }
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  const d = dateStr.substring(0, 16).replace('T', ' ')
  return d
}

function reportStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  }
  return map[status] || status || '未知'
}

function reportStatusClass(status: string) {
  const map: Record<string, string> = {
    PENDING: 'report-status-pending',
    APPROVED: 'report-status-approved',
    REJECTED: 'report-status-rejected'
  }
  return map[status] || 'report-status-pending'
}

const hasReportPermission = (step: any) => {
  const perm = step.reportPermission
  if (!perm || perm === '所有人' || perm === 'ALL') {
    return true
  }
  const user = uni.getStorageSync('user')
  if (!user) return false

  // 逗号分隔的权限列表
  const allowedList = perm.split(',').map((s: string) => s.trim())

  // 只要允许列表里包含了当前用户的 姓名、用户名、角色 或 id
  return allowedList.includes(user.name) ||
         allowedList.includes(user.username) ||
         allowedList.includes(String(user.id)) ||
         allowedList.includes(user.roleLabel)
}

const handleNoPermission = () => {
  uni.showToast({ title: '您没有此工序的报工权限', icon: 'none' })
}

const handleReport = (step: any) => {
  if (!ensureLogin()) return
  uni.navigateTo({
    url: `/pages/report-form/index?orderId=${order.value.id}&processNo=${step.sequenceNo || 1}&processName=${encodeURIComponent(step.processName || '')}&plannedQty=${step.plannedQuantity || order.value.plannedQuantity || 0}&completedQty=${step.completedQuantity || 0}&defectQty=${step.defectQuantity || 0}&reportPermission=${encodeURIComponent(step.reportPermission || '')}`
  })
}

const viewStepDetail = (step: any) => {
  uni.showModal({
    title: step.processName || '工序详情',
    content: `计划数：${step.plannedQuantity || order.value.plannedQuantity || 0}\n良品数：${step.completedQuantity || 0}\n不良数：${step.defectQuantity || 0}\n报工权限：${step.reportPermission || '所有人'}`,
    showCancel: false
  })
}

const showMore = () => {
  uni.showModal({
    title: '工单备注',
    content: order.value.remark || '暂无备注',
    showCancel: false
  })
}

const showPriority = () => {
  uni.showModal({
    title: '优先级',
    content: priorityLabel(order.value.priority),
    showCancel: false
  })
}

const handleFinish = () => {
  uni.showModal({
    title: '结束工单',
    content: '移动端暂不支持结束工单，请在管理后台处理。',
    showCancel: false
  })
}

function priorityLabel(priority?: string) {
  const map: Record<string, string> = {
    URGENT: '紧急',
    HIGH: '高',
    NORMAL: '普通',
    LOW: '低'
  }
  return map[priority || ''] || priority || '未设置'
}
</script>

<style scoped>
.container {
  background: #f6f7f7;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background: white;
  padding: 30rpx;
  position: relative;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 10rpx;
}

.status-tag {
  font-size: 20rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  display: inline-block;
}

.status-green { background: #e6f7f3; color: #00b889; }
.status-gray { background: #f0f0f0; color: #999; }
.status-orange { background: #fff4e6; color: #ff9900; }
.status-blue { background: #eef3fe; color: #4080ff; }
.status-red { background: #fff0f0; color: #ff4d4f; }

.remain-tag {
  font-size: 20rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  background: #fff4e6;
  color: #ff9900;
}

.remain-tag.overdue {
  background: #fff0f0;
  color: #ff4d4f;
}

.order-code {
  font-size: 36rpx;
  font-weight: bold;
  display: block;
  margin-bottom: 20rpx;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 10rpx;
}

.info-icon { font-size: 24rpx; margin-right: 6rpx; }
.info-label { font-size: 24rpx; color: #999; }
.info-val { font-size: 24rpx; color: #666; }
.green-link { color: #00b889; }

.progress-bar-wrap {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  gap: 16rpx;
}

.progress-bar {
  flex: 1;
  height: 12rpx;
  background: #eee;
  border-radius: 6rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #00b889, #00d4a0);
  border-radius: 6rpx;
  transition: width 0.3s;
}

.progress-text {
  font-size: 22rpx;
  color: #00b889;
  font-weight: bold;
  min-width: 60rpx;
  text-align: right;
}

.tabs {
  display: flex;
  background: white;
  border-bottom: 1px solid #f0f0f0;
}

.tab-item {
  flex: 1;
  text-align: center;
  height: 90rpx;
  line-height: 90rpx;
  font-size: 28rpx;
  color: #666;
  position: relative;
}

.tab-item.active {
  color: #00b889;
  font-weight: bold;
}

.tab-item.active .line {
  position: absolute;
  bottom: 0;
  left: 30%;
  width: 40%;
  height: 4rpx;
  background: #00b889;
}

.process-list {
  flex: 1;
  padding: 20rpx;
  box-sizing: border-box;
}

.filter-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  font-size: 24rpx;
  color: #999;
  margin-bottom: 10rpx;
}

.filter-text { margin-left: 8rpx; }

.empty-hint {
  text-align: center;
  padding: 100rpx 0;
  color: #999;
  font-size: 28rpx;
}

.process-card {
  background: white;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
}

.card-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 30rpx;
}

.step-title { display: flex; align-items: center; }
.step-num {
  width: 36rpx;
  height: 36rpx;
  background: #00b889;
  color: white;
  border-radius: 50%;
  font-size: 20rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 15rpx;
}

.step-name { font-size: 30rpx; font-weight: bold; }
.step-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
.step-status-green { background: #e6f7f3; color: #00b889; }
.step-status-blue { background: #eef3fe; color: #4080ff; }
.step-status-gray { background: #f0f0f0; color: #999; }

.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.data-columns {
  display: flex;
  flex: 1;
}

.col {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.cl { font-size: 20rpx; color: #999; margin-bottom: 10rpx; }
.cv { font-size: 30rpx; font-weight: bold; }
.cv.green { color: #00b889; }
.cv.red { color: #ff4d4f; }

.circle-box {
  width: 90rpx;
  height: 90rpx;
  border: 6rpx solid #eef3fe;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.percent-txt { font-size: 20rpx; color: #4080ff; font-weight: bold; }

.card-extra {
  margin-bottom: 20rpx;
  padding-top: 16rpx;
  border-top: 1px solid #f8f8f8;
}

.extra-row {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
}

.extra-icon { font-size: 22rpx; margin-right: 8rpx; }
.extra-label { font-size: 22rpx; color: #999; }
.extra-val { font-size: 22rpx; color: #666; }

.card-btns {
  display: flex;
  justify-content: flex-end;
}

.btn {
  height: 60rpx;
  padding: 0 40rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-left: 20rpx;
}

.btn.ghost { border: 1px solid #ddd; color: #666; }
.btn.primary { border: 1px solid #00b889; color: #00b889; }
.btn.disabled { border: 1px solid #eee; background: #fafafa; color: #ccc; cursor: not-allowed; }

.history-card {
  background: white;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}

.history-head,
.history-meta,
.history-qty {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.history-head {
  margin-bottom: 14rpx;
}

.history-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #222;
}

.history-status {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
}

.report-status-pending { background: #fff7e6; color: #fa8c16; }
.report-status-approved { background: #e6f7f3; color: #00a870; }
.report-status-rejected { background: #fff1f0; color: #ff4d4f; }

.history-meta {
  font-size: 24rpx;
  color: #999;
  margin-bottom: 18rpx;
}

.history-qty {
  font-size: 26rpx;
  color: #555;
}

.history-qty .green { color: #00b889; }
.history-qty .red { color: #ff4d4f; }

.history-note {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid #f2f2f2;
  font-size: 24rpx;
  color: #777;
}

.footer {
  height: 110rpx;
  background: white;
  display: flex;
  padding: 10rpx 20rpx;
  padding-bottom: env(safe-area-inset-bottom);
  border-top: 1px solid #f0f0f0;
}

.f-btn {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 28rpx;
  color: #666;
  background: #f8f9f9;
  margin: 10rpx;
  border-radius: 10rpx;
}

.f-btn.finish {
  background: #ff4d4f;
  color: white;
}

.finish-icon {
  margin-right: 6rpx;
}

.loading-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255,255,255,0.8);
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 28rpx;
  color: #999;
  z-index: 999;
}
</style>
