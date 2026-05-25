<template>
  <view class="container">
    <!-- 顶部导航与搜索 -->
    <view class="top-nav">
      <view class="nav-left">
        <text>全部工单</text>
      </view>
      <view class="nav-right">
        <view class="search-box" v-if="showSearch">
          <input class="search-input" v-model="keyword" placeholder="搜索工单编号/产品名" @confirm="doSearch" />
          <view @tap="showSearch = false; keyword = ''; doSearch()">✕</view>
        </view>
        <view v-else class="search-icon" @tap="showSearch = true">🔍</view>
      </view>
    </view>

    <!-- 状态切换页签 -->
    <view class="status-tabs">
      <view v-for="tab in statusTabs" :key="tab.key"
            class="tab" :class="{ active: currentTab === tab.key }"
            @tap="currentTab = tab.key">
        {{ tab.label }} {{ tab.count > 0 ? tab.count : '' }}
      </view>
    </view>

    <!-- 列表内容 -->
    <view class="total-count">总计 ({{ filteredOrders.length }})</view>

    <scroll-view scroll-y class="order-list" @scrolltolower="loadMore">
      <view v-for="item in filteredOrders" :key="item.id" class="order-card" @tap="goToDetail(item)">
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
              <text class="field-icon">📋</text>
              <text class="label">工单编号：</text>
              <text class="val">{{ item.code }}</text>
            </view>
            <view class="info-field">
              <text class="field-icon">📦</text>
              <text class="label">产品信息：</text>
              <text class="val">{{ item.productCode }} | {{ item.productName }}</text>
            </view>
            <view class="info-field">
              <text class="field-icon">📊</text>
              <text class="label">工单进度：</text>
              <text class="val">{{ item.goodQuantity || 0 }}/{{ item.plannedQuantity || 0 }}个</text>
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
      </view>
      
      <view class="no-more" v-if="filteredOrders.length > 0">没有更多了</view>
      <view class="no-more" v-if="filteredOrders.length === 0 && !loading">暂无工单</view>
    </scroll-view>

    <!-- 悬浮扫码按钮 -->
    <view class="scan-fab" @tap="handleScan">
      <text class="scan-icon">📷</text>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-hint">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchWorkOrders, ensureLogin } from '../../services/api'
import type { WorkOrderInfo } from '../../types/api'

const orders = ref<WorkOrderInfo[]>([])
const loading = ref(false)
const currentTab = ref('all')
const keyword = ref('')
const showSearch = ref(false)

const statusTabs = computed(() => {
  const all = orders.value
  return [
    { key: 'all', label: '全部', count: all.length },
    { key: 'NOT_STARTED', label: '未开始', count: all.filter(i => i.status === 'NOT_STARTED').length },
    { key: 'EXECUTING', label: '执行中', count: all.filter(i => i.status === 'EXECUTING').length },
    { key: 'COMPLETED', label: '已结束', count: all.filter(i => ['COMPLETED', 'FINISHED', 'CLOSED'].includes(i.status)).length }
  ]
})

const filteredOrders = computed(() => {
  let list = orders.value
  if (currentTab.value !== 'all') {
    if (currentTab.value === 'COMPLETED') {
      list = list.filter(i => ['COMPLETED', 'FINISHED', 'CLOSED'].includes(i.status))
    } else {
      list = list.filter(i => i.status === currentTab.value)
    }
  }
  if (keyword.value) {
    const kw = keyword.value.toLowerCase()
    list = list.filter(i => (i.code + i.productName + i.productCode).toLowerCase().includes(kw))
  }
  return list
})

const loadOrders = async () => {
  loading.value = true
  try {
    const res = await fetchWorkOrders()
    orders.value = res.items || []
  } catch (e) {
    console.error('加载工单失败', e)
    orders.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!ensureLogin()) return
  loadOrders()
})

onShow(() => {
  // 每次回到列表页刷新数据（报工后回来能看到最新数据）
  if (uni.getStorageSync('token')) {
    loadOrders()
  }
})

const doSearch = () => {
  // 搜索通过 computed 自动过滤
}

const loadMore = () => {
  // 目前一次加载全部，无需分页
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始', EXECUTING: '执行中', PAUSED: '已暂停',
    COMPLETED: '已完成', FINISHED: '已结束', CLOSED: '已关闭', CANCELED: '已取消'
  }
  return map[status] || status || '未知'
}

function calculateStepPercent(order: WorkOrderInfo, step: any) {
  const planned = step.plannedQuantity || order.plannedQuantity || 0
  if (!planned) return 0
  return Math.min(100, Math.round(((step.completedQuantity || 0) / planned) * 100))
}

const goToDetail = (item: WorkOrderInfo) => {
  uni.navigateTo({
    url: `/pages/work-order-detail/index?id=${item.id}`
  })
}

const handleScan = () => {
  if (typeof window !== 'undefined' && window.location) {
    uni.showModal({
      title: '提示',
      content: 'H5 模式暂不支持扫码，请直接在工单列表中选择工单进行报工。',
      showCancel: false
    })
    return
  }
  uni.scanCode({
    success: (res) => {
      console.log('扫码结果：', res.result)
      const url = res.result
      if (url.includes('/pages/work-order-detail/')) {
        const match = url.match(/[?&]id=(\d+)/)
        if (match) {
          uni.navigateTo({ url: `/pages/work-order-detail/index?id=${match[1]}` })
          return
        }
        const codeMatch = url.match(/[?&]code=([^&]+)/)
        if (codeMatch) {
          uni.navigateTo({ url: `/pages/work-order-detail/index?code=${codeMatch[1]}` })
          return
        }
      }
      if (/^\d+$/.test(url)) {
        uni.navigateTo({ url: `/pages/work-order-detail/index?id=${url}` })
      } else {
        uni.navigateTo({ url: `/pages/work-order-detail/index?code=${encodeURIComponent(url)}` })
      }
    }
  })
}
</script>

<style scoped>
.container { background: #f6f7f7; height: 100vh; display: flex; flex-direction: column; }

.top-nav {
  background: white;
  padding: 20rpx 30rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nav-left { font-size: 32rpx; font-weight: bold; display: flex; align-items: center; }
.nav-right { display: flex; align-items: center; }
.search-icon { font-size: 36rpx; }

.search-box {
  display: flex;
  align-items: center;
  background: #f3f5f5;
  border-radius: 30rpx;
  padding: 0 20rpx;
  height: 60rpx;
  gap: 10rpx;
}
.search-input { flex: 1; font-size: 26rpx; height: 60rpx; }

.status-tabs {
  background: white;
  display: flex;
  padding: 10rpx 20rpx;
  gap: 20rpx;
  flex-wrap: wrap;
}
.tab {
  padding: 10rpx 24rpx;
  background: #f1f3f3;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #666;
}
.tab.active { background: #00b889; color: white; }

.total-count { padding: 20rpx 30rpx; font-size: 24rpx; color: #999; }

.order-list { flex: 1; padding: 0 20rpx; box-sizing: border-box; }
.order-card {
  background: white;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
}

.card-main { margin-bottom: 20rpx; }

.card-info { flex: 1; }
.info-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.tags { display: flex; gap: 10rpx; }
.tag { font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 4rpx; }
.tag.urgent { background: #ff4d4f; color: white; }
.tag.status { background: #e6f7ff; color: #1890ff; }
.tag.status.status-EXECUTING { background: #e6f7f3; color: #00b889; }
.tag.status.status-NOT_STARTED { background: #f0f0f0; color: #999; }
.tag.status.status-COMPLETED,
.tag.status.status-FINISHED { background: #eef3fe; color: #4080ff; }
.tag.status.status-PAUSED { background: #fff4e6; color: #ff9900; }

.order-code { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 15rpx; }
.info-field { display: flex; align-items: center; margin-bottom: 6rpx; }
.field-icon { font-size: 22rpx; margin-right: 8rpx; }
.info-field .label { font-size: 22rpx; color: #999; }
.info-field .val { font-size: 22rpx; color: #666; }

/* 底部进度轴 */
.process-steps {
  display: flex;
  justify-content: space-between;
  position: relative;
  padding: 0 10rpx;
  margin-top: 10rpx;
  border-top: 1px solid #f8f8f8;
  padding-top: 20rpx;
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
  background: white;
}
.step-circle {
  width: 50rpx;
  height: 50rpx;
  border: 2rpx solid #eee;
  border-radius: 50%;
  font-size: 16rpx;
  color: #999;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 8rpx;
}
.step-circle.step-active { border-color: #00b889; color: #00b889; }
.step-circle.step-done { background: #00b889; border-color: #00b889; color: white; }
.step-name { font-size: 20rpx; color: #999; max-width: 80rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-line {
  position: absolute;
  top: 45rpx;
  left: 40rpx;
  right: 40rpx;
  height: 2rpx;
  background: #eee;
  z-index: 0;
}

.no-more { text-align: center; font-size: 24rpx; color: #ccc; padding: 40rpx 0; }

.loading-hint { text-align: center; padding: 40rpx 0; font-size: 28rpx; color: #999; }

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
.scan-icon { font-size: 40rpx; }
</style>
