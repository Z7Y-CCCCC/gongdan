<template>
  <view class="container">
    <!-- 顶部筛选 -->
    <view class="dashboard-header">
      <view class="filter-row">
        <button class="dropdown" :class="{ active: period === 'today' }" :aria-pressed="period === 'today'" @click="period = 'today'; loadReports()">今天</button>
        <button class="dropdown" :class="{ active: period === 'lastMonth' }" :aria-pressed="period === 'lastMonth'" @click="period = 'lastMonth'; loadReports()">上月</button>
        <button class="dropdown" :class="{ active: period === '' }" :aria-pressed="period === ''" @click="period = ''; loadReports()">全部</button>
        <IconButton icon="refreshempty" label="刷新报工记录" :busy="loading" @click="loadOnReturn" />
      </view>

      <view v-if="!loading && !loadError" class="quantity-summary">
        <text>已加载良品 <text class="quantity-green">{{ totalGood }}</text></text>
        <text>不良 <text class="quantity-red">{{ totalDefect }}</text></text>
      </view>

      <view class="tab-row">
        <button class="tab" :class="{ active: statusFilter === '' }" :aria-pressed="statusFilter === ''" @click="selectStatus('')">
          全部
          <view v-if="statusFilter === ''" class="line"></view>
        </button>
        <button class="tab" :class="{ active: statusFilter === 'PENDING' }" :aria-pressed="statusFilter === 'PENDING'" @click="selectStatus('PENDING')">
          待审
          <view v-if="statusFilter === 'PENDING'" class="line"></view>
        </button>
        <button class="tab" :class="{ active: statusFilter === 'APPROVED' }" :aria-pressed="statusFilter === 'APPROVED'" @click="selectStatus('APPROVED')">
          通过
          <view v-if="statusFilter === 'APPROVED'" class="line"></view>
        </button>
        <button class="tab" :class="{ active: statusFilter === 'REJECTED' }" :aria-pressed="statusFilter === 'REJECTED'" @click="selectStatus('REJECTED')">
          驳回
          <view v-if="statusFilter === 'REJECTED'" class="line"></view>
        </button>
      </view>
    </view>
    <ListLimitNotice v-if="!loading && !loadError" :total="reportTotal" :loaded="reports.length" />

    <!-- 记录列表 -->
    <scroll-view scroll-y class="list-content" role="region" aria-label="报工记录" tabindex="0">
      <view v-for="item in filteredReports" :key="item.id" class="report-card">
        <view class="card-header">
          <text class="user-info">{{ item.reporterName }} | {{ formatDateTime(item.createdAt) }}</text>
          <text class="status-badge" :class="'decision-' + item.status.toLowerCase()">{{ item.status === 'APPROVED' ? '已通过' : item.status === 'REJECTED' ? '已驳回' : '待审批' }}</text>
        </view>
        
        <view class="card-body">
          <view class="field">
            <uni-icons class="field-icon" type="compose" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">工单：</text>
            <text class="fv">{{ item.workOrderCode }}</text>
          </view>
          <view class="field">
            <uni-icons class="field-icon" type="gear" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">工序：</text>
            <text class="fv">{{ item.processName }}</text>
          </view>
          <view v-if="item.productCode || item.productName || item.productSpec" class="field">
            <uni-icons class="field-icon" type="shop" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">产品：</text>
            <text class="fv">{{ [item.productCode, item.productName, item.productSpec].filter(Boolean).join(' · ') }}</text>
          </view>
          <view class="field" v-if="item.startTime || item.endTime">
            <uni-icons class="field-icon" type="calendar" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">时间：</text>
            <text class="fv">{{ formatDateTime(item.startTime) }} ~ {{ formatDateTime(item.endTime) }}</text>
          </view>
          <view class="field">
            <uni-icons class="field-icon" type="checkbox-filled" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">数量：</text>
            <text class="fv">总{{ item.reportQuantity }} / 良品 <text class="quantity-green">{{ item.goodQuantity }}</text> / 不良品 <text class="quantity-red">{{ item.defectQuantity }}</text></text>
          </view>
          <view class="field" v-if="item.remark">
            <uni-icons class="field-icon" type="chat" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">备注：</text>
            <text class="fv">{{ item.remark }}</text>
          </view>
          <view class="field" v-if="item.status !== 'PENDING' && (item.approverName || item.approvedAt)">
            <uni-icons class="field-icon" type="person" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">审批：</text>
            <text class="fv">{{ [item.approverName, item.approvedAt && formatDateTime(item.approvedAt)].filter(Boolean).join(' · ') }}</text>
          </view>
          <view class="field decision" v-if="item.status === 'REJECTED' || item.approvalRemark">
            <uni-icons class="field-icon" type="chat" size="14" color="#64756d"  aria-hidden="true" />
            <text class="fl">{{ item.status === 'REJECTED' ? '驳回原因：' : '审批备注：' }}</text>
            <text class="fv decision-note">{{ item.approvalRemark || '未填写' }}</text>
          </view>
        </view>

        <ReportImages :images="item.images || []" />
        <view v-if="canRetry(item)" class="button-row"><button class="retry-button" @click="retryReport(item)">重新报工</button></view>
      </view>
      
      <view v-if="loadError" class="no-more" role="alert"><text>{{ loadError }}</text><IconButton icon="refreshempty" label="重试报工记录" @click="retryPage" /></view>
      <view v-if="filteredReports.length === 0 && !loading && !loadError" class="no-more">暂无报工记录</view>
      <view v-if="hasMore && !loading && !loadError" class="no-more"><IconButton icon="arrow-down" label="加载更多报工记录" @click="loadMore" /></view>
      <view v-if="filteredReports.length > 0 && !loading && !hasMore && !loadError" class="no-more">没有更多了</view>
    </scroll-view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-hint">
      <text>加载中...</text>
    </view>
    <!-- #ifdef H5 -->
    <MobileTabBar current="reports" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchReportPage, ensureLogin } from '../../services/api'
import type { WorkOrderReport } from '../../types/api'
import MobileTabBar from '../../components/MobileTabBar.vue'
import IconButton from '../../components/IconButton.vue'
import ListLimitNotice from '../../components/ListLimitNotice.vue'
import ReportImages from '../../components/ReportImages.vue'
import { mobileSessionKey } from '../../utils/session-key'
import { navigatePage } from '../../utils/navigation'
import { consumePendingReportsOnReturn } from '../../utils/report-flow'

const reports = ref<WorkOrderReport[]>([])
const loading = ref(false)
const statusFilter = ref('')
const period = ref('')
const reportTotal = ref(0)
const loadError = ref('')
const hasMore = ref(false)
const pageSize = 20
let nextPage = 1
type ReportQuery = { session: string; period: string; status: string }
let activeQuery: ReportQuery | undefined
let pendingPage = 1
let pendingReset = true
const seen = new Set<number>()

const filteredReports = computed(() => {
  if (!statusFilter.value) return reports.value
  return reports.value.filter(r => r.status === statusFilter.value)
})

const totalGood = computed(() => reports.value.reduce((s, r) => s + (r.goodQuantity || 0), 0))
const totalDefect = computed(() => reports.value.reduce((s, r) => s + (r.defectQuantity || 0), 0))

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function canRetry(item: WorkOrderReport) {
  return item.status === 'REJECTED' && item.workOrderId > 0 && item.processSequenceNo > 0
}

function retryReport(item: WorkOrderReport) {
  if (!canRetry(item)) return
  navigatePage(`/pages/report-form/index?orderId=${item.workOrderId}&processNo=${item.processSequenceNo}&correction=1&processName=${encodeURIComponent(item.processName)}&reporterId=${item.reporterId || ''}`)
}

function isCurrentH5Route() {
  return typeof window !== 'undefined' && (window.location.hash || '').includes('/pages/report-list/index')
}

async function requestPage(query: ReportQuery, page: number, reset = false) {
  pendingPage = page; pendingReset = reset
  loading.value = true
  loadError.value = ''
  const current = () => activeQuery === query && query.session === mobileSessionKey()
  try {
    const res = await fetchReportPage({ page, size: pageSize, status: query.status || undefined, period: query.period, includeSubmitted: true })
    if (!current()) return
    if (!Array.isArray(res?.items) || !Number.isSafeInteger(res.total) || res.total < 0) throw new Error('报工记录格式异常，请重试')
    if (reset) { seen.clear(); reports.value = [] }
    for (const item of res.items) if (!seen.has(item.id)) { seen.add(item.id); reports.value.push(item) }
    reportTotal.value = res.total
    hasMore.value = res.items.length > 0 && page * pageSize < res.total
    nextPage = page + 1
  } catch (e: any) {
    if (!current()) return
    loadError.value = e.message || '加载报工记录失败'
    uni.showToast({ title: loadError.value, icon: 'none' })
  } finally {
    if (activeQuery === query) {
      if (query.session !== mobileSessionKey()) { reports.value = []; reportTotal.value = 0; loadError.value = ''; hasMore.value = false }
      loading.value = false
    }
  }
}

const loadReports = () => {
  if (!ensureLogin()) { activeQuery = undefined; reports.value = []; reportTotal.value = 0; loading.value = false; return }
  const query = { session: mobileSessionKey(), period: period.value, status: statusFilter.value }
  if (loading.value && activeQuery?.session === query.session && activeQuery.period === query.period && activeQuery.status === query.status) return
  const changed = activeQuery?.session !== query.session || activeQuery.period !== query.period || activeQuery.status !== query.status
  activeQuery = query
  if (changed) { seen.clear(); reports.value = []; reportTotal.value = 0; hasMore.value = false; nextPage = 1 }
  return requestPage(query, 1, true)
}
function selectStatus(status: string) { if (statusFilter.value !== status) { statusFilter.value = status; return loadReports() } }
function loadMore() { if (!loading.value && hasMore.value && !loadError.value && activeQuery) return requestPage(activeQuery, nextPage) }
function retryPage() { if (!loading.value && loadError.value && activeQuery) return requestPage(activeQuery, pendingPage, pendingReset) }

function reloadWhenCurrentRoute() {
  if (uni.getStorageSync('token') && isCurrentH5Route()) {
    loadOnReturn()
  }
}

function reloadWhenVisible() {
  if (typeof document === 'undefined' || document.visibilityState === 'visible') {
    reloadWhenCurrentRoute()
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('hashchange', reloadWhenCurrentRoute)
  }
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', reloadWhenVisible)
  }
  if (ensureLogin()) loadOnReturn()
})

onBeforeUnmount(() => {
  activeQuery = undefined
  if (typeof window !== 'undefined') {
    window.removeEventListener('hashchange', reloadWhenCurrentRoute)
  }
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', reloadWhenVisible)
  }
})

onShow(() => {
  if (uni.getStorageSync('token')) {
    loadOnReturn()
  }
})

function loadOnReturn() {
  if (consumePendingReportsOnReturn()) {
    statusFilter.value = 'PENDING'
    period.value = ''
    // A fetch started before the successful submission must not hide it.
    activeQuery = undefined
  }
  return loadReports()
}
</script>

<style scoped>
.container { background: #f6f7f7; height: 100vh; display: flex; flex-direction: column; }

.dashboard-header { background: white; padding: 20rpx; }
.filter-row { display: flex; align-items: center; margin-bottom: 20rpx; gap: 12rpx; }
.filter-row .dropdown { flex: 1; }
.quantity-summary { display: flex; gap: 28rpx; padding: 12rpx 0 24rpx; font-size: 26rpx; color: #71847e; }
.status-badge { padding: 6rpx 12rpx; font-size: 22rpx; border-radius: 12rpx; white-space: nowrap; }
.decision-approved { color: #008869; background: #e7f5ef; }
.decision-rejected { color: #b53b3b; background: #fff0ef; }
.decision-pending { color: #8a6726; background: #fff4dc; }
.dropdown {
  padding: 10rpx 24rpx;
  background: #f1f3f3;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #666;
}
.dropdown.active { background: #00856b; color: white; font-weight: 700; text-decoration: underline; text-underline-offset: 6rpx; }

.stat-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 15rpx; margin-bottom: 30rpx; }
.stat-card { border-radius: 12rpx; padding: 20rpx; color: #202926; display: flex; flex-direction: column; background: #f7f9f8; border-left: 5rpx solid #64756d; }
.stat-card.green { border-left-color: #00856b; }
.stat-card.blue { border-left-color: #4080ff; }
.stat-card.orange { border-left-color: #f59e0b; }
.stat-card.red { border-left-color: #ef4444; }
.st { font-size: 20rpx; color: #64756d; margin-bottom: 10rpx; }
.sv { font-size: 32rpx; font-weight: bold; color: #202926; }

.tab-row { display: flex; justify-content: space-between; gap: 8rpx; border-bottom: 1px solid #eee; }
.tab { padding: 20rpx 0; font-size: 24rpx; color: #666; position: relative; background: transparent; border-radius: 0; flex: 1; }
.tab.active { color: #00856b; font-weight: bold; }
.tab.active .line { position: absolute; bottom: 0; left: 0; right: 0; height: 4rpx; background: #00856b; }

.list-content { flex: 1; height: 0; min-height: 0; padding: 20rpx 20rpx 130rpx; box-sizing: border-box; }
.report-card { background: white; border-radius: 16rpx; padding: 30rpx; margin-bottom: 20rpx; position: relative; overflow: hidden; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; border-bottom: 1px solid #f8f8f8; padding-bottom: 20rpx; margin-bottom: 20rpx; }
.user-info { font-size: 28rpx; font-weight: bold; color: #333; }

.field { display: flex; align-items: flex-start; margin-bottom: 12rpx; }
.field-icon { margin-right: 8rpx; flex-shrink: 0; }
.fl { font-size: 24rpx; color: #64756d; width: 90rpx; flex-shrink: 0; }
.fv { font-size: 24rpx; color: #666; }
.decision { position: relative; z-index: 1; }
.decision-note { min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.retry-button { position: relative; z-index: 1; margin: 16rpx 0 0; padding: 0 24rpx; display: flex; align-items: center; justify-content: center; border-radius: 20rpx; font-size: 26rpx; color: #007c63; background: #e6f7f3; }
.decision .fl { width: 126rpx; }
.quantity-green { color: #00856b; }
.quantity-red { color: #ff4d4f; }

.no-more { text-align: center; font-size: 24rpx; color: #ccc; padding: 40rpx 0; }
.loading-hint { text-align: center; padding: 40rpx 0; font-size: 28rpx; color: #64756d; }

</style>
