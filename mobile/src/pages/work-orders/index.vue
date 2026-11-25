<template>
  <view class="container" role="main" aria-label="工单列表">
    <!-- 顶部导航与搜索 -->
    <view class="top-nav">
      <view class="nav-left">
        <text role="heading" aria-level="1">工单</text>
      </view>
      <view class="nav-right">
        <view class="search-box" v-if="showSearch">
          <text class="search-label">工单 / 产品</text>
          <input class="search-input" v-model="keyword" aria-label="搜索工单或产品" placeholder="工单号 / 产品" maxlength="128" confirm-type="search" @confirm="doSearch" />
          <IconButton icon="search" label="搜索工单" @click="doSearch" />
          <IconButton icon="closeempty" label="清空并收起搜索" @click="clearSearch" />
        </view>
        <template v-else>
          <IconButton icon="scan" label="扫码查找工单" @click="handleScan" />
          <IconButton icon="search" label="展开工单搜索" @click="showSearch = true" />
        </template>
      </view>
    </view>

    <!-- 状态切换页签 -->
    <scroll-view scroll-x class="status-tabs">
      <view class="status-tab-row" role="tablist" aria-label="工单状态">
      <button v-for="(tab, index) in statusTabs" :key="tab.key"
            class="tab" :class="{ active: currentTab === tab.key }"
            role="tab" :aria-selected="currentTab === tab.key"
            :tabindex="currentTab === tab.key ? 0 : -1" @keydown="moveStatusFocus($event, index)"
            @click="selectTab(tab.key)">
        {{ tab.label }}
      </button>
      </view>
    </scroll-view>

    <!-- 列表内容 -->
    <view v-if="loaded" class="total-count">{{ orders.length }} / {{ orderTotal }} 工单</view>

    <scroll-view scroll-y class="order-list" role="region" aria-label="工单列表" tabindex="0" @scrolltolower="loadMore">
      <button v-for="item in orders" :key="item.id" class="order-card ui-card-button" :aria-label="item.code + '，查看工单'" @click="goToDetail(item)">
        <view class="card-main">
          <view class="card-info">
            <view class="info-top">
              <view class="tags">
                <view v-if="item.priority === 'HIGH' || item.priority === 'URGENT'" class="tag urgent">
                  {{ item.priority === 'URGENT' ? '加急' : '高' }}
                </view>
                <view class="tag status" :class="'status-' + item.status">{{ statusLabel(item.status) }}</view>
              </view>
            </view>
            <text class="order-code">{{ item.code }}</text>
            <view class="info-field">
              <uni-icons class="field-icon" type="shop" size="14" color="#64756d"  aria-hidden="true" />
              <text class="label">产品：</text>
              <text class="val">{{ item.productCode }} | {{ item.productName }}</text>
            </view>
            <view class="info-field">
              <uni-icons class="field-icon" type="bars" size="14" color="#64756d"  aria-hidden="true" />
              <text class="label">完成：</text>
              <text class="val">{{ item.goodQuantity || 0 }}/{{ item.plannedQuantity || 0 }}{{ item.unitName || '' }}</text>
            </view>
          </view>
        </view>

        <!-- 底部工序进度轴 -->
        <view v-if="item.tasks && item.tasks.length > 0" class="process-steps">
          <view v-for="(p, pi) in item.tasks.slice(0, 5)" :key="pi" class="step-item">
            <view class="step-circle" :class="{ 'step-done': calculateStepPercent(item, p) >= 100, 'step-active': calculateStepPercent(item, p) > 0 && calculateStepPercent(item, p) < 100 }">
              {{ calculateStepPercent(item, p) }}%
            </view>
            <text class="step-name">{{ p.processName || '工序' + (pi+1) }}</text>
          </view>
          <view class="step-line"></view>
        </view>
      </button>
      
      <view v-if="loading" class="list-state">加载中…</view>
      <view v-else-if="loadError" class="list-state" role="alert">
        <text>{{ loadError }}</text>
        <IconButton icon="refreshempty" label="重试工单列表" @click="retryPage" />
      </view>
      <view v-else-if="hasMore" class="load-more"><IconButton icon="arrow-down" label="加载更多工单" @click="loadMore" /></view>
      <view v-else-if="loaded" class="list-state">{{ orders.length ? '没有更多了' : '未找到符合条件的工单' }}</view>
    </scroll-view>

    <!-- #ifdef H5 -->
    <ScanWorkOrderDialog v-model="showScan" />
    <MobileTabBar current="orders" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchWorkOrderPage, ensureLogin } from '../../services/api'
import type { WorkOrderInfo } from '../../types/api'
import MobileTabBar from '../../components/MobileTabBar.vue'
import IconButton from '../../components/IconButton.vue'
import { usePageDraft } from '../../composables/usePageDraft'
import { navigatePage } from '../../utils/navigation'
import { scannedWorkOrderRoute } from '../../utils/scan-target'
// #ifdef H5
import ScanWorkOrderDialog from '../../components/ScanWorkOrderDialog.vue'
const showScan = ref(false)
// #endif

const orders = ref<WorkOrderInfo[]>([])
const orderTotal = ref(0)
const loading = ref(false)
const loaded = ref(false)
const hasMore = ref(false)
const loadError = ref('')
const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'NOT_STARTED', label: '未开始' },
  { key: 'EXECUTING', label: '执行中' },
  { key: 'PAUSED', label: '已暂停' },
  { key: 'COMPLETED', label: '已结束' },
  { key: 'CANCELED', label: '已取消' }
] as const
type TabKey = typeof statusTabs[number]['key']
const currentTab = ref<TabKey>('all')
const keyword = ref('')
const showSearch = ref(false)
const pageSize = 20
type BrowseQuery = { keyword: string; status: TabKey; token: string }
let activeQuery: BrowseQuery | undefined
let nextPage = 1
let mounted = false
const seen = new Set<number>()

usePageDraft({
  key: 'gongdan:draft:work-order-filter',
  source: () => ({ currentTab: currentTab.value, keyword: keyword.value, showSearch: showSearch.value }),
  restore: (draft) => {
    currentTab.value = statusTabs.some(tab => tab.key === draft.currentTab) ? draft.currentTab : 'all'
    keyword.value = typeof draft.keyword === 'string' ? draft.keyword.slice(0, 128) : ''
    showSearch.value = Boolean(draft.showSearch || keyword.value)
  }
})

async function requestPage(query: BrowseQuery, page: number) {
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchWorkOrderPage({
      page, size: pageSize, keyword: query.keyword,
      status: query.status === 'all' ? undefined : query.status
    })
    if (activeQuery !== query || uni.getStorageSync('token') !== query.token) return
    if (!Array.isArray(res?.items) || !Number.isSafeInteger(res.total) || res.total < 0) {
      throw new Error('工单列表返回格式异常，请重试')
    }
    for (const order of res.items) {
      if (!seen.has(order.id)) {
        seen.add(order.id)
        orders.value.push(order)
      }
    }
    orderTotal.value = res.total
    loaded.value = true
    hasMore.value = res.items.length === pageSize && page * pageSize < res.total
    nextPage = page + 1
  } catch (error: any) {
    if (activeQuery === query && uni.getStorageSync('token') === query.token) {
      loadError.value = error?.message || '加载工单失败，请重试'
    }
  } finally {
    if (activeQuery === query) {
      if (uni.getStorageSync('token') !== query.token) resetList()
      loading.value = false
    }
  }
}

function resetList() {
  seen.clear()
  orders.value = []
  orderTotal.value = 0
  loaded.value = false
  hasMore.value = false
  loadError.value = ''
  nextPage = 1
}

function loadOrders() {
  if (!ensureLogin()) {
    activeQuery = undefined
    resetList()
    loading.value = false
    return
  }
  const query = { keyword: keyword.value.trim(), status: currentTab.value, token: uni.getStorageSync('token') }
  if (loading.value && activeQuery?.keyword === query.keyword && activeQuery.status === query.status
      && activeQuery.token === query.token) return
  activeQuery = query
  resetList()
  return requestPage(query, 1)
}

function handleHashChange() {
  if (window.location.hash.split('?')[0] === '#/pages/work-orders/index') loadOrders()
}

onMounted(() => {
  mounted = true
  // H5 can keep tab pages mounted while password login redirects away and back.
  if (typeof window !== 'undefined') window.addEventListener('hashchange', handleHashChange)
  return loadOrders()
})

onShow(() => {
  if (mounted && typeof window === 'undefined') loadOrders()
})

onBeforeUnmount(() => {
  mounted = false
  activeQuery = undefined
  if (typeof window !== 'undefined') window.removeEventListener('hashchange', handleHashChange)
})

function doSearch() {
  return loadOrders()
}

function clearSearch() {
  showSearch.value = false
  keyword.value = ''
  return loadOrders()
}

function selectTab(tab: TabKey) {
  if (currentTab.value === tab) return
  currentTab.value = tab
  return loadOrders()
}
function moveStatusFocus(event: KeyboardEvent, index: number) {
  let next: number
  if (event.key === 'ArrowRight') next = (index + 1) % statusTabs.length
  else if (event.key === 'ArrowLeft') next = (index + statusTabs.length - 1) % statusTabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = statusTabs.length - 1
  else return
  event.preventDefault()
  ;(event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
}

function loadMore() {
  if (!loading.value && hasMore.value && !loadError.value && activeQuery) return requestPage(activeQuery, nextPage)
}

function retryPage() {
  if (!loading.value && loadError.value && activeQuery) return requestPage(activeQuery, nextPage)
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始', EXECUTING: '执行中', IN_PROGRESS: '执行中', PAUSED: '已暂停',
    COMPLETED: '已完成', FINISHED: '已结束', CLOSED: '已关闭', CANCELED: '已取消', CANCELLED: '已取消'
  }
  return map[status] || status || '未知'
}

function calculateStepPercent(order: WorkOrderInfo, step: any) {
  const planned = step.plannedQuantity || order.plannedQuantity || 0
  if (!planned) return 0
  return Math.min(100, Math.round((((step.completedQuantity || 0) + (step.defectQuantity || 0)) / planned) * 100))
}

const goToDetail = (item: WorkOrderInfo) => {
  navigatePage(`/pages/work-order-detail/index?id=${item.id}`)
}

const handleScan = () => {
  if (!ensureLogin()) return
  // #ifdef H5
  if (typeof window !== 'undefined' && window.location) {
    showScan.value = true
    return
  }
  // #endif
  uni.scanCode({
    success: (res) => {
      try { navigatePage(scannedWorkOrderRoute(String(res.result || ''))) }
      catch (error) { uni.showToast({ title: error instanceof Error ? error.message : '不是工单二维码', icon: 'none' }) }
    },
    fail: error => { if (!error.errMsg?.includes('cancel')) uni.showToast({ title: '扫码失败，请重试', icon: 'none' }) }
  })
}
</script>

<style scoped>
.container { background: var(--mobile-bg, #f6f7f7); height: 100vh; display: flex; flex-direction: column; }

.top-nav {
  background: var(--mobile-surface, white);
  padding: 20rpx 30rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nav-left { font-size: 32rpx; font-weight: bold; display: flex; align-items: center; flex-shrink: 0; }
.nav-right { display: flex; align-items: center; min-width: 0; margin-left: 16rpx; }
.nav-right { gap: 12rpx; }
.search-icon,
.icon-button { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; }

.search-box {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10rpx;
}
.search-box { flex-wrap: wrap; }
.search-label { flex: 0 0 100%; font-size: 24rpx; color: var(--mobile-muted, #536b60); }
.search-box .search-input { flex-basis: 100rpx; }
.search-input { flex: 1; min-width: 0; font-size: 26rpx; height: 88rpx; background: var(--mobile-bg, #f3f5f5); border-radius: 20rpx; padding: 0 16rpx; }
.search-submit { flex-shrink: 0; margin: 0; padding: 0 12rpx; color: var(--mobile-link, #007a60); background: transparent; }
.search-submit::after { border: 0; }

.status-tabs {
  background: var(--mobile-surface, white);
  white-space: nowrap;
  padding: 0 20rpx 12rpx;
  flex-shrink: 0;
}
.status-tab-row { display: inline-flex; gap: 12rpx; }
.tab {
  padding: 10rpx 24rpx;
  background: var(--mobile-subtle, #f1f3f3);
  border-radius: 30rpx;
  font-size: 24rpx;
  color: var(--mobile-muted, #666);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tab.active { background: #00856b; color: white; font-weight: 700; box-shadow: inset 0 -4rpx 0 #006b51; }

.total-count { padding: 20rpx 30rpx; font-size: 24rpx; color: var(--mobile-muted, #64756d); }

.order-list { flex: 1; height: 0; min-height: 0; padding: 0 20rpx 130rpx; box-sizing: border-box; }
.order-card {
  background: var(--mobile-surface, white);
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
  width: 100%;
}

.card-main { margin-bottom: 20rpx; }

.card-info { flex: 1; }
.info-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.tags { display: flex; gap: 10rpx; }
.tag { font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 4rpx; }
.tag.urgent { background: #a0332c; color: white; }
.tag.status { background: var(--mobile-info-bg, #e6f7ff); color: var(--mobile-info, #2459a6); }
.tag.status.status-EXECUTING, .tag.status.status-IN_PROGRESS { background: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #00856b); }
.tag.status.status-NOT_STARTED { background: var(--mobile-bg, #f0f0f0); color: var(--mobile-muted, #64756d); }
.tag.status.status-COMPLETED,
.tag.status.status-FINISHED { background: var(--mobile-info-bg, #eef3fe); color: var(--mobile-info, #2459a6); }
.tag.status.status-PAUSED { background: var(--mobile-warning-bg, #fff4e6); color: var(--mobile-warning, #8b5908); }

.order-code { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 15rpx; }
.info-field { display: flex; align-items: center; margin-bottom: 6rpx; }
.field-icon { margin-right: 8rpx; flex-shrink: 0; }
.info-field .label { font-size: 22rpx; color: var(--mobile-muted, #64756d); }
.info-field .val { font-size: 22rpx; color: var(--mobile-muted, #666); flex: 1; min-width: 0; overflow-wrap: anywhere; }

/* 底部进度轴 */
.process-steps {
  display: flex;
  justify-content: space-between;
  position: relative;
  padding: 0 10rpx;
  margin-top: 10rpx;
  border-top: 1px solid var(--mobile-border, #f8f8f8);
  padding-top: 20rpx;
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
  background: var(--mobile-surface, white);
}
.step-circle {
  width: 50rpx;
  height: 50rpx;
  border: 2rpx solid var(--mobile-border, #eee);
  border-radius: 50%;
  font-size: 16rpx;
  color: var(--mobile-muted, #64756d);
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 8rpx;
}
.step-circle.step-active { border-color: var(--mobile-link, #00856b); color: var(--mobile-link, #00856b); }
.step-circle.step-done { background: #00856b; border-color: var(--mobile-link, #00856b); color: white; }
.step-name { font-size: 20rpx; color: var(--mobile-muted, #64756d); max-width: 80rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-line {
  position: absolute;
  top: 45rpx;
  left: 40rpx;
  right: 40rpx;
  height: 2rpx;
  background: var(--mobile-bg, #eee);
  z-index: 0;
}

.list-state { text-align: center; font-size: 24rpx; color: var(--mobile-muted, #586360); padding: 32rpx 0; }
.list-action { margin-left: 12rpx; color: var(--mobile-link, #007a60); }
.load-more { display: flex; justify-content: center; margin: 20rpx auto; }

/* 扫码悬浮按钮 */
.scan-fab {
  position: fixed;
  right: 40rpx;
  bottom: 160rpx;
  width: 100rpx;
  height: 100rpx;
  background: rgba(0,184,137,0.9);
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 8rpx 20rpx rgba(0,184,137,0.3);
}
</style>
