<template>
  <view class="container">
    <!-- 顶部工单基本信息 -->
    <view v-if="!loadError && order.id" class="header">
      <view class="ui-page-head detail-head">
        <IconButton icon="back" label="返回工单列表" @click="navigatePage('/pages/work-orders/index')" />
        <view class="button-row">
          <button class="meta-action" aria-label="查看工单备注" @click="showMore"><uni-icons type="chat" size="18" aria-hidden="true" /><text>备注</text></button>
          <button class="meta-action" aria-label="查看工单优先级" @click="showPriority"><uni-icons type="flag" size="18" aria-hidden="true" /><text>优先级</text></button>
        </view>
      </view>
      <text class="order-code">{{ order.code || '' }}</text>
      <view class="status-row">
        <view class="status-tag" :class="statusClass(order.status)">{{ statusLabel(order.status) }}</view>
        <view v-if="daysRemain !== null" class="remain-tag" :class="{ overdue: daysRemain < 0 }">
          {{ daysRemain >= 0 ? '剩 ' + daysRemain + '天' : '超期 ' + Math.abs(daysRemain) + '天' }}
        </view>
      </view>
      <view class="info-list">
        <view v-if="order.planStart || order.planEnd" class="info-item">
          <uni-icons class="info-icon" type="calendar" size="14" color="#64756d"  aria-hidden="true" />
          <text class="info-label">计划：</text>
          <text class="info-val">{{ formatDate(order.planStart) }} ~ {{ formatDate(order.planEnd) }}</text>
        </view>
        <view class="info-item">
          <uni-icons class="info-icon" type="shop" size="14" color="#64756d"  aria-hidden="true" />
          <text class="info-label">产品：</text>
          <text class="info-val green-link">{{ order.productCode }} | {{ order.productName }}</text>
        </view>
        <view class="info-item">
          <uni-icons class="info-icon" type="bars" size="14" color="#64756d"  aria-hidden="true" />
          <text class="info-label">完成：</text>
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
    <view v-if="!loadError && order.id" class="tabs">
      <button class="tab-item" :class="{ active: activeTab === 'progress' }" :aria-pressed="activeTab === 'progress'" @click="activeTab = 'progress'">
        进度
        <view v-if="activeTab === 'progress'" class="line"></view>
      </button>
      <button class="tab-item" :class="{ active: activeTab === 'material' }" :aria-pressed="activeTab === 'material'" @click="activeTab = 'material'">
        我的用料
        <view v-if="activeTab === 'material'" class="line"></view>
      </button>
      <button class="tab-item" :class="{ active: activeTab === 'history' }" :aria-pressed="activeTab === 'history'" @click="activeTab = 'history'">
        我的履历
        <view v-if="activeTab === 'history'" class="line"></view>
      </button>
    </view>

    <!-- 工序列表 (生产进度 tab) -->
    <view v-if="loadError" class="empty-hint" role="alert"><text>{{ loadError }}</text><IconButton icon="refreshempty" label="重试工单详情" @click="loadData" /><IconButton icon="back" label="返回工单列表" @click="navigatePage('/pages/work-orders/index')" /></view>
    <scroll-view v-else-if="activeTab === 'progress' && order.id" scroll-y class="process-list">
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

        <view class="card-btns">
          <button class="btn ghost" :aria-label="(step.processName || '工序') + '详情'" @click="viewStepDetail(step)"><uni-icons type="info" size="18" aria-hidden="true" /><text>详情</text></button>
          <button
            class="btn"
            :class="hasReportPermission(step) ? 'primary' : 'disabled'"
            @click="hasReportPermission(step) ? handleReport(step) : handleNoPermission()"
            :aria-label="hasReportPermission(step) ? (step.processName || '工序') + '报工' : '查看报工限制'"
          >{{ hasReportPermission(step) ? '报工' : '无权限' }}</button>
        </view>
      </view>
    </scroll-view>

    <!-- 用料明细 tab -->
    <scroll-view v-if="activeTab === 'material' && !loadError" scroll-y class="process-list">
      <view v-if="!historyError && materialReports.length === 0 && !historyLoading" class="empty-hint"><text>{{ historyHasMore ? '当前记录未含用料' : '暂无已审批用料记录' }}</text></view>
      <view v-for="item in materialReports" :key="item.id" class="history-card">
        <view class="history-head">
          <text class="history-title">{{ item.materialName }}</text>
          <text class="history-status report-status-approved">已领料</text>
        </view>
        <view class="history-meta">
          <text>{{ item.processName || '-' }}</text>
          <text>{{ item.reporterName || '-' }}</text>
        </view>
        <view class="material-time">{{ formatDate(item.createdAt) }}</view>
      </view>
      <view v-if="historyError" class="empty-hint" role="alert"><text>{{ historyError }}</text><IconButton icon="refreshempty" label="重试生产履历" @click="retryHistory" /></view>
      <view v-if="historyHasMore && !historyLoading && !historyError" class="empty-hint"><IconButton icon="arrow-down" label="加载更多生产履历" @click="loadHistoryMore" /></view>
    </scroll-view>

    <!-- 生产履历 tab -->
    <scroll-view v-if="activeTab === 'history' && !loadError" scroll-y class="process-list">
      <ListLimitNotice v-if="!historyError && !historyLoading" :total="reportTotal" :loaded="loadedReports" label="个人报工" />
      <view v-if="reports.length === 0 && !historyError && !historyLoading" class="empty-hint"><text>暂无生产履历</text></view>
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
        <view v-if="item.status === 'REJECTED'" class="history-note">
          <text>驳回原因：{{ item.approvalRemark || '未填写' }}</text>
        </view>
        <ReportImages :images="item.images || []" />
      </view>
      <view v-if="historyError" class="empty-hint" role="alert"><text>{{ historyError }}</text><IconButton icon="refreshempty" label="重试生产履历" @click="retryHistory" /></view>
      <view v-if="historyHasMore && !historyLoading && !historyError" class="empty-hint"><IconButton icon="arrow-down" label="加载更多生产履历" @click="loadHistoryMore" /></view>
    </scroll-view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-mask">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchReportPage, fetchWorkOrder, fetchWorkOrderByCode, fetchWorkOrderReporters, ensureLogin } from '../../services/api'
import type { WorkOrderInfo, WorkOrderReport } from '../../types/api'
import ListLimitNotice from '../../components/ListLimitNotice.vue'
import IconButton from '../../components/IconButton.vue'
import ReportImages from '../../components/ReportImages.vue'
import { mobileSessionKey } from '../../utils/session-key'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'
import { canReportTask } from '../../utils/report-permission'
import { navigatePage, safeDecode } from '../../utils/navigation'

const pageActivity = useMobilePageActivity()

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
const productUnit = computed(() => order.value.unitName || '')
const reports = ref<WorkOrderReport[]>([])
const reportTotal = ref(0)
const loadedReports = ref(0)
let checkingPermission = false
const materialReports = computed(() => reports.value.filter(item => item.status === 'APPROVED' && item.materialName))

// 从 URL 参数中获取 id 或 code
let orderId = ''
let orderCode = ''

onLoad((options) => {
  orderId = String(options?.id || '')
  orderCode = String(options?.code || '')
})

const loadError = ref('')
const historyError = ref('')
const historyLoading = ref(false)
const historyHasMore = ref(false)
const historySeen = new Set<number>()
let historyNextPage = 1
let historyPendingReset = true
let historySequence = 0
let activeLoad: { signature: string } | undefined
let historyOrder = 0
let historySession = ''
let mounted = false

onMounted(async () => {
  mounted = true
  if (!orderId && !orderCode && typeof window !== 'undefined') {
    const hash = window.location.hash || ''
    const query = hash.includes('?') ? new URLSearchParams(hash.slice(hash.indexOf('?') + 1)) : undefined
    orderId = query?.get('id') || ''
    orderCode = query?.get('code') || ''
  }
  await loadData()
})
onShow(() => { if (mounted) void loadData() })
onBeforeUnmount(() => { mounted = false; activeLoad = undefined; historySequence++ })

const loadData = async () => {
  const session = mobileSessionKey()
  const signature = JSON.stringify([session, orderId, orderCode])
  if (loading.value && activeLoad?.signature === signature) return
  const request = { signature }
  activeLoad = request
  const current = () => activeLoad === request && session === mobileSessionKey()
  if (!ensureLogin()) { loading.value = false; order.value = { ...order.value, id: 0, code: '', tasks: [] }; return }
  loading.value = true
  loadError.value = ''
  try {
    let res: WorkOrderInfo
    if (orderId) {
      try { res = await fetchWorkOrder(orderId) }
      catch (error) { if (!orderCode || !current()) throw error; res = await fetchWorkOrderByCode(orderCode) }
    } else if (orderCode) res = await fetchWorkOrderByCode(orderCode)
    else throw new Error('缺少工单参数')
    if (!current()) return
    if (!res || !Number.isSafeInteger(res.id) || res.id <= 0 || !Array.isArray(res.tasks)) throw new Error('工单数据格式异常，请重试')
    order.value = res
    await loadReports()
  } catch (error) {
    if (current()) {
      loadError.value = error instanceof Error ? error.message : '工单加载失败，请重试'
      uni.showToast({ title: loadError.value, icon: 'none' })
    }
  } finally {
    if (activeLoad === request) {
      if (session !== mobileSessionKey()) { order.value = { ...order.value, id: 0, code: '', tasks: [] }; reports.value = []; reportTotal.value = 0 }
      loading.value = false
    }
  }
}

const loadReports = async (reset = true) => {
  if (!order.value.id) return
  const id = order.value.id
  const session = mobileSessionKey()
  if (historyLoading.value && historyOrder === id && historySession === session) return
  const sequence = ++historySequence
  const current = () => sequence === historySequence && session === mobileSessionKey() && order.value.id === id
  if (historyOrder !== id || historySession !== session) { reports.value = []; reportTotal.value = 0; loadedReports.value = 0 }
  historyOrder = id
  historySession = session
  historyPendingReset = reset
  const page = reset ? 1 : historyNextPage
  historyLoading.value = true
  historyError.value = ''
  try {
    const response = await fetchReportPage({ page, size: 20, workOrderId: id, includeSubmitted: true })
    if (!current()) return
    if (!Array.isArray(response?.items) || !Number.isSafeInteger(response.total) || response.total < 0) throw new Error('履历数据格式异常，请重试')
    if (reset) { reports.value = []; loadedReports.value = 0; historySeen.clear() }
    for (const item of response.items) if (!historySeen.has(item.id)) { historySeen.add(item.id); reports.value.push(item) }
    reportTotal.value = response.total
    loadedReports.value = reports.value.length
    historyHasMore.value = response.items.length > 0 && page * 20 < response.total
    historyNextPage = page + 1
  } catch (error) {
    if (current()) historyError.value = error instanceof Error ? error.message : '生产履历加载失败，请重试'
  } finally {
    if (sequence === historySequence) historyLoading.value = false
  }
}
function retryHistory() { return loadReports(historyPendingReset) }
function loadHistoryMore() { if (historyHasMore.value && !historyError.value) return loadReports(false) }

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
    NOT_STARTED: '未开始', EXECUTING: '执行中', IN_PROGRESS: '执行中', PAUSED: '已暂停',
    COMPLETED: '已完成', FINISHED: '已结束', CLOSED: '已关闭', CANCELED: '已取消'
  }
  return map[status] || status || '未知'
}

function statusClass(status: string) {
  const map: Record<string, string> = {
    NOT_STARTED: 'status-gray', EXECUTING: 'status-green', IN_PROGRESS: 'status-green', PAUSED: 'status-orange',
    COMPLETED: 'status-blue', FINISHED: 'status-blue', CLOSED: 'status-gray', CANCELED: 'status-red'
  }
  return map[status] || 'status-gray'
}

function stepStatusText(step: any) {
  const planned = step.plannedQuantity || order.value.plannedQuantity || 0
  const completed = (step.completedQuantity || 0) + (step.defectQuantity || 0)
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
  return Math.min(100, Math.round((((step.completedQuantity || 0) + (step.defectQuantity || 0)) / planned) * 100))
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
  return ['NOT_STARTED', 'EXECUTING', 'IN_PROGRESS'].includes(order.value.status) && canReportTask(step, uni.getStorageSync('user'))
}

const handleNoPermission = () => {
  uni.showToast({ title: '您没有此工序的报工权限', icon: 'none' })
}

const handleReport = async (step: any) => {
  if (!mounted || checkingPermission || !ensureLogin()) return
  const session = mobileSessionKey()
  const targetOrder = order.value
  const isPageCurrent = pageActivity.capture()
  const current = () => mounted && isPageCurrent() && targetOrder === order.value
  checkingPermission = true
  try {
    const users = await fetchWorkOrderReporters(targetOrder.id, step.sequenceNo)
    if (!current()) return
    if (session !== mobileSessionKey()) throw new Error('登录状态已变更，请刷新')
    if (!users.some(user => user.id === uni.getStorageSync('user')?.id)) throw new Error('您没有此工序的报工权限')
  } catch (e: any) {
    if (!current()) return
    uni.showToast({ title: e.message || '报工权限确认失败，请重试', icon: 'none' })
    return
  } finally {
    if (mounted) checkingPermission = false
  }
  const url = `/pages/report-form/index?orderId=${order.value.id}&processNo=${step.sequenceNo || 1}&processName=${encodeURIComponent(step.processName || '')}&plannedQty=${step.plannedQuantity || order.value.plannedQuantity || 0}&completedQty=${step.completedQuantity || 0}&defectQty=${step.defectQuantity || 0}&reportPermission=${encodeURIComponent(step.reportPermission || '')}`
  navigatePage(url)
}

const viewStepDetail = (step: any) => {
  uni.showModal({
    title: step.processName || '工序详情',
    confirmText: '关闭',
    content: `计划数：${step.plannedQuantity || order.value.plannedQuantity || 0}\n良品数：${step.completedQuantity || 0}\n不良数：${step.defectQuantity || 0}\n报工权限：${step.reportPermission || '所有人'}`,
    showCancel: false
  })
}

const showMore = () => {
  uni.showModal({
    title: '工单备注',
    confirmText: '关闭',
    content: order.value.remark || '暂无备注',
    showCancel: false
  })
}

const showPriority = () => {
  uni.showModal({
    title: '优先级',
    confirmText: '关闭',
    content: priorityLabel(order.value.priority),
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
.detail-head { margin-bottom: 20rpx; }
.meta-action { display: flex; align-items: center; justify-content: center; gap: 8rpx; padding: 0 16rpx; background: #edf5f2; color: #41635a; border-radius: 20rpx; font-size: 24rpx; }
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

.status-green { background: #e6f7f3; color: #00856b; }
.status-gray { background: #f0f0f0; color: #64756d; }
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

.info-icon { margin-right: 6rpx; flex-shrink: 0; }
.info-label { font-size: 24rpx; color: #64756d; }
.info-val { font-size: 24rpx; color: #666; flex: 1; min-width: 0; overflow-wrap: anywhere; }
.green-link { color: #00856b; }

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
  transition: width 180ms ease;
}

.progress-text {
  font-size: 22rpx;
  color: #00856b;
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
  color: #00856b;
  font-weight: bold;
}

.tab-item.active .line {
  position: absolute;
  bottom: 0;
  left: 30%;
  width: 40%;
  height: 4rpx;
  background: #00856b;
}

.process-list {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 20rpx;
  box-sizing: border-box;
}

.empty-hint {
  text-align: center;
  padding: 100rpx 0;
  color: #64756d;
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
  background: #00856b;
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
.step-status-green { background: #e6f7f3; color: #00856b; }
.step-status-blue { background: #eef3fe; color: #4080ff; }
.step-status-gray { background: #f0f0f0; color: #64756d; }

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

.cl { font-size: 20rpx; color: #64756d; margin-bottom: 10rpx; }
.cv { font-size: 30rpx; font-weight: bold; }
.cv.green { color: #00856b; }
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

.extra-icon { margin-right: 8rpx; flex-shrink: 0; }
.extra-label { font-size: 22rpx; color: #64756d; }
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
  gap: 8rpx;
}

.btn.ghost { border: 1px solid #ddd; color: #666; }
.btn.primary { border: 1px solid #00856b; background: #00856b; color: #fff; }
.btn.disabled { border: 1px solid #eee; background: #fafafa; color: #64756d; cursor: not-allowed; }

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
  color: #64756d;
  margin-bottom: 18rpx;
}

.history-qty {
  font-size: 26rpx;
  color: #555;
}

.history-qty .green { color: #00856b; }
.history-qty .red { color: #ff4d4f; }

.history-note {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid #f2f2f2;
  font-size: 24rpx;
  color: #777;
}

.material-time {
  padding-top: 16rpx;
  border-top: 1px solid #f2f2f2;
  font-size: 24rpx;
  color: #64756d;
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
  color: #64756d;
  z-index: 999;
}
</style>
