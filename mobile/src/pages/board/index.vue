<template>
  <view class="container">
    <view class="ui-page-head board-head">
      <text class="title">看板</text>
      <IconButton icon="refreshempty" label="刷新看板" :busy="loading" @click="loadBoard" />
    </view>
    <view v-if="errorMessage" class="empty">
      <text>{{ errorMessage }}</text>
      <IconButton icon="refreshempty" label="重试看板" @click="loadBoard" />
    </view>
    <view v-else-if="dashboard" class="summary-card">
      <view class="gauge">
        <text class="gauge-value">{{ achievementRate }}%</text>
        <text class="gauge-label">达成率</text>
      </view>

      <view class="stat-grid">
        <view class="stat-item">
          <text class="stat-value">{{ activeOrderCount }}</text>
          <text class="stat-label">进行中工单</text>
        </view>
        <view class="stat-item">
          <text class="stat-value green">{{ todayReportCount }}</text>
          <text class="stat-label">我的今日报工</text>
        </view>
        <view class="stat-item">
          <text class="stat-value orange">{{ pendingReportCount }}</text>
          <text class="stat-label">我的待审批</text>
        </view>
      </view>
    </view>

    <view v-if="dashboard && !errorMessage" class="section">
      <view class="section-head">
        <text class="section-title">工单进度</text>
        <text class="section-sub">良品 {{ totalGood }} / 计划 {{ totalPlanned }}</text>
      </view>
      <text class="section-sub">最近 {{ orderRows.length }} / 共 {{ dashboard.workOrderTotal }} 工单 · 指标全量</text>
      <view v-if="orderRows.length === 0 && !loading" class="empty">暂无工单数据</view>
      <view v-for="item in orderRows" :key="item.code" class="order-card">
        <view class="order-top">
          <text class="order-code">{{ item.code }}</text>
          <text class="order-status">{{ statusLabel(item.status) }}</text>
        </view>
        <text class="order-product">{{ item.productCode }} {{ item.productName }}</text>
        <view class="progress-row">
          <view class="progress-track">
            <view class="progress-fill" :style="{ width: item.progress + '%' }"></view>
          </view>
          <text class="progress-text">{{ item.progress }}%</text>
        </view>
      </view>
    </view>

    <view v-if="dashboard && !errorMessage" class="section">
      <view class="section-head">
        <text class="section-title">我的工序产出</text>
        <text class="section-sub">不良 {{ totalDefect }}</text>
      </view>
      <view v-if="rankings.length === 0 && !loading" class="empty">暂无报工记录</view>
      <view v-for="(item, index) in rankings" :key="item.name" class="rank-item">
        <text class="rank-no">{{ index + 1 }}</text>
        <view class="rank-main">
          <text class="rank-name">{{ item.name }}</text>
          <view class="rank-bar">
            <view class="rank-fill" :style="{ width: item.progress + '%' }"></view>
          </view>
        </view>
        <text class="rank-value">{{ item.value }}</text>
      </view>
    </view>

    <view v-if="loading" class="loading-hint">加载中...</view>
    <!-- #ifdef H5 -->
    <MobileTabBar current="board" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MobileTabBar from '../../components/MobileTabBar.vue'
import IconButton from '../../components/IconButton.vue'
import { useMobileDashboard } from '../../composables/useMobileDashboard'

const { dashboard, loading, errorMessage, day: todayKey, load: loadBoard } = useMobileDashboard()
const totalPlanned = computed(() => dashboard.value?.totalPlanned || 0)
const totalGood = computed(() => dashboard.value?.totalGood || 0)
const totalDefect = computed(() => dashboard.value?.totalDefect || 0)
const achievementRate = computed(() => {
  if (!totalPlanned.value) return 0
  return Math.min(100, Math.round((totalGood.value / totalPlanned.value) * 100))
})
const activeOrderCount = computed(() => dashboard.value?.activeOrderCount || 0)
const pendingReportCount = computed(() => dashboard.value?.pendingReportCount || 0)
const todayReportCount = computed(() => dashboard.value?.todayReportCount || 0)

const orderRows = computed(() => {
  return (dashboard.value?.recentOrders || []).slice(0, 5).map(item => {
    const progress = item.plannedQuantity
      ? Math.round(((item.goodQuantity || 0) / item.plannedQuantity) * 100)
      : 0
    return {
      ...item,
      progress: Math.max(0, Math.min(100, progress))
    }
  })
})

const rankings = computed(() => {
  const rows = (dashboard.value?.rankings || []).slice(0, 5)
  const max = Math.max(1, ...rows.map(item => item.value))
  return rows.map(item => ({
    ...item,
    progress: Math.round((item.value / max) * 100)
  }))
})

function statusLabel(status: string) {
  return {
    NOT_STARTED: '未开始',
    EXECUTING: '执行中',
    IN_PROGRESS: '执行中',
    PAUSED: '已暂停',
    COMPLETED: '已完成',
    FINISHED: '已结束',
    CLOSED: '已关闭',
    CANCELED: '已取消',
    CANCELLED: '已取消'
  }[status] || status || '未知'
}

onMounted(loadBoard)
onShow(loadBoard)
</script>

<style scoped>
.container {
  background: #f6f7f7;
  min-height: 100vh;
  padding: 24rpx 24rpx 140rpx;
  box-sizing: border-box;
}
.board-head { margin-bottom: 24rpx; }

.summary-card,
.section {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 18rpx rgba(0, 0, 0, 0.04);
}

.card-head,
.section-head,
.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  display: block;
  font-size: 34rpx;
  font-weight: bold;
  color: #202926;
}

.subtitle,
.section-sub,
.order-product,
.stat-label {
  display: block;
  font-size: 24rpx;
  color: #64756d;
}

.tag {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #e6f7f3;
  color: #00856b;
  font-size: 22rpx;
}

.gauge {
  padding: 12rpx 0 28rpx;
  text-align: center;
}

.gauge-value {
  display: block;
  font-size: 76rpx;
  line-height: 1;
  font-weight: bold;
  color: #00856b;
}

.gauge-label {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #64756d;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
}

.stat-item {
  background: #f7faf9;
  border-radius: 12rpx;
  padding: 20rpx 12rpx;
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 36rpx;
  font-weight: bold;
  color: #202926;
}

.stat-value.green { color: #00856b; }
.stat-value.orange { color: #fa8c16; }

.section-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #202926;
}

.order-card {
  margin-top: 24rpx;
  padding-top: 22rpx;
  border-top: 1px solid #f0f2f2;
}

.order-code {
  font-size: 28rpx;
  font-weight: bold;
  color: #202926;
}

.order-status {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #e6f7f3;
  color: #00856b;
  font-size: 22rpx;
}

.progress-row {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
}

.progress-track,
.rank-bar {
  flex: 1;
  height: 12rpx;
  background: #edf1f0;
  border-radius: 999rpx;
  overflow: hidden;
}

.progress-fill,
.rank-fill {
  height: 100%;
  background: #00856b;
  border-radius: 999rpx;
}

.progress-text {
  min-width: 72rpx;
  text-align: right;
  font-size: 24rpx;
  color: #00856b;
  font-weight: bold;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 24rpx 0;
  border-top: 1px solid #f0f2f2;
}

.rank-no {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #f1f3f3;
  color: #6f7b78;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-name {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: #202926;
}

.rank-value {
  min-width: 70rpx;
  text-align: right;
  font-size: 26rpx;
  font-weight: bold;
  color: #00856b;
}

.empty,
.loading-hint {
  text-align: center;
  padding: 48rpx 0;
  color: #99a3a0;
  font-size: 26rpx;
}

.retry-btn {
  margin-top: 20rpx;
  color: #00856b;
  font-size: 26rpx;
}
</style>
