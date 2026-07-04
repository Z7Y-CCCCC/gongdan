<template>
  <view class="container">
    <!-- 顶部筛选 -->
    <view class="dashboard-header">
      <view class="filter-row">
        <view class="dropdown" :class="{ active: period === 'today' }" @click="period = 'today'; loadReports()">今天</view>
        <view class="dropdown" :class="{ active: period === 'lastMonth' }" @click="period = 'lastMonth'; loadReports()">上月</view>
        <view class="dropdown" :class="{ active: period === '' }" @click="period = ''; loadReports()">全部</view>
      </view>

      <view class="stat-cards">
        <view class="stat-card green">
          <text class="st">已审批</text>
          <text class="sv">{{ approvedCount }}</text>
        </view>
        <view class="stat-card blue">
          <text class="st">待审批</text>
          <text class="sv">{{ pendingCount }}</text>
        </view>
        <view class="stat-card orange">
          <text class="st">总良品 / 总不良品</text>
          <text class="sv">{{ totalGood }} / {{ totalDefect }}</text>
        </view>
        <view class="stat-card red">
          <text class="st">已拒绝</text>
          <text class="sv">{{ rejectedCount }}</text>
        </view>
      </view>

      <view class="tab-row">
        <view class="tab" :class="{ active: statusFilter === '' }" @click="statusFilter = ''; loadReports()">
          全部 {{ reports.length }}
          <view v-if="statusFilter === ''" class="line"></view>
        </view>
        <view class="tab" :class="{ active: statusFilter === 'PENDING' }" @click="statusFilter = 'PENDING'; loadReports()">
          未审批 {{ pendingCount }}
          <view v-if="statusFilter === 'PENDING'" class="line"></view>
        </view>
        <view class="tab" :class="{ active: statusFilter === 'APPROVED' }" @click="statusFilter = 'APPROVED'; loadReports()">
          已审批 {{ approvedCount }}
          <view v-if="statusFilter === 'APPROVED'" class="line"></view>
        </view>
      </view>
    </view>

    <!-- 记录列表 -->
    <scroll-view scroll-y class="list-content">
      <view v-for="item in filteredReports" :key="item.id" class="report-card">
        <view class="card-header">
          <text class="user-info">{{ item.reporterName }} | {{ item.createdAt }}</text>
        </view>
        
        <view class="card-body">
          <view class="field">
            <text class="field-icon">📋</text>
            <text class="fl">工单编号：</text>
            <text class="fv">{{ item.workOrderCode }}</text>
          </view>
          <view class="field">
            <text class="field-icon">⚙️</text>
            <text class="fl">工序名称：</text>
            <text class="fv">{{ item.processName }}</text>
          </view>
          <view class="field">
            <text class="field-icon">📦</text>
            <text class="fl">产品信息：</text>
            <text class="fv">{{ item.productCode }} | {{ item.productName }}{{ item.productSpec ? ' | ' + item.productSpec : '' }}</text>
          </view>
          <view class="field" v-if="item.startTime || item.endTime">
            <text class="field-icon">📅</text>
            <text class="fl">报工时间：</text>
            <text class="fv">{{ item.startTime || '-' }} ~ {{ item.endTime || '-' }}</text>
          </view>
          <view class="field">
            <text class="field-icon">✅</text>
            <text class="fl">报工数量：</text>
            <text class="fv">总{{ item.reportQuantity }} / 良品 <text class="green">{{ item.goodQuantity }}</text> / 不良品 <text class="red">{{ item.defectQuantity }}</text></text>
          </view>
          <view class="field" v-if="item.remark">
            <text class="field-icon">💬</text>
            <text class="fl">备注：</text>
            <text class="fv">{{ item.remark }}</text>
          </view>
        </view>

        <!-- 审批状态印章 -->
        <view v-if="item.status === 'APPROVED'" class="stamp green-stamp">审批通过</view>
        <view v-else-if="item.status === 'REJECTED'" class="stamp red-stamp">已拒绝</view>
        <view v-else class="stamp gray-stamp">待审批</view>
      </view>
      
      <view v-if="filteredReports.length === 0 && !loading" class="no-more">暂无报工记录</view>
      <view v-if="filteredReports.length > 0" class="no-more">没有更多了</view>
    </scroll-view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-hint">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchReports, ensureLogin } from '../../services/api'
import type { WorkOrderReport } from '../../types/api'

const reports = ref<WorkOrderReport[]>([])
const loading = ref(false)
const statusFilter = ref('')
const period = ref('')

const filteredReports = computed(() => {
  if (!statusFilter.value) return reports.value
  return reports.value.filter(r => r.status === statusFilter.value)
})

const pendingCount = computed(() => reports.value.filter(r => r.status === 'PENDING').length)
const approvedCount = computed(() => reports.value.filter(r => r.status === 'APPROVED').length)
const rejectedCount = computed(() => reports.value.filter(r => r.status === 'REJECTED').length)
const totalGood = computed(() => reports.value.reduce((s, r) => s + (r.goodQuantity || 0), 0))
const totalDefect = computed(() => reports.value.reduce((s, r) => s + (r.defectQuantity || 0), 0))

function isCurrentH5Route() {
  return typeof window !== 'undefined' && (window.location.hash || '').includes('/pages/report-list/index')
}

const loadReports = async () => {
  loading.value = true
  try {
    // 加载所有状态的报工记录
    const res = await fetchReports(statusFilter.value as any, period.value)
    reports.value = res.items || []
  } catch (e) {
    console.error('加载报工记录失败', e)
    reports.value = []
  } finally {
    loading.value = false
  }
}

function reloadWhenCurrentRoute() {
  if (uni.getStorageSync('token') && isCurrentH5Route()) {
    loadReports()
  }
}

function reloadWhenVisible() {
  if (typeof document === 'undefined' || document.visibilityState === 'visible') {
    reloadWhenCurrentRoute()
  }
}

onMounted(() => {
  if (!ensureLogin()) return
  loadReports()
  if (typeof window !== 'undefined') {
    window.addEventListener('hashchange', reloadWhenCurrentRoute)
  }
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', reloadWhenVisible)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('hashchange', reloadWhenCurrentRoute)
  }
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', reloadWhenVisible)
  }
})

onShow(() => {
  if (uni.getStorageSync('token')) {
    loadReports()
  }
})
</script>

<style scoped>
.container { background: #f6f7f7; height: 100vh; display: flex; flex-direction: column; }

.dashboard-header { background: white; padding: 20rpx; }
.filter-row { display: flex; margin-bottom: 20rpx; gap: 20rpx; }
.dropdown {
  padding: 10rpx 24rpx;
  background: #f1f3f3;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #666;
}
.dropdown.active { background: #00b889; color: white; }

.stat-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 15rpx; margin-bottom: 30rpx; }
.stat-card { border-radius: 12rpx; padding: 20rpx; color: white; display: flex; flex-direction: column; }
.stat-card.green { background: #52c41a; }
.stat-card.blue { background: #1890ff; }
.stat-card.orange { background: #faad14; }
.stat-card.red { background: #ff4d4f; }
.st { font-size: 20rpx; opacity: 0.8; margin-bottom: 10rpx; }
.sv { font-size: 32rpx; font-weight: bold; }

.tab-row { display: flex; border-bottom: 1px solid #eee; position: relative; }
.tab { padding: 20rpx 0; margin-right: 40rpx; font-size: 28rpx; color: #999; position: relative; }
.tab.active { color: #00b889; font-weight: bold; }
.tab.active .line { position: absolute; bottom: 0; left: 0; right: 0; height: 4rpx; background: #00b889; }

.list-content { flex: 1; padding: 20rpx; box-sizing: border-box; }
.report-card { background: white; border-radius: 16rpx; padding: 30rpx; margin-bottom: 20rpx; position: relative; overflow: hidden; }
.card-header { border-bottom: 1px solid #f8f8f8; padding-bottom: 20rpx; margin-bottom: 20rpx; }
.user-info { font-size: 28rpx; font-weight: bold; color: #333; }

.field { display: flex; align-items: flex-start; margin-bottom: 12rpx; }
.field-icon { font-size: 22rpx; margin-right: 8rpx; }
.fl { font-size: 24rpx; color: #999; width: 140rpx; flex-shrink: 0; }
.fv { font-size: 24rpx; color: #666; }
.green { color: #00b889; }
.red { color: #ff4d4f; }

.no-more { text-align: center; font-size: 24rpx; color: #ccc; padding: 40rpx 0; }
.loading-hint { text-align: center; padding: 40rpx 0; font-size: 28rpx; color: #999; }

/* 印章 CSS */
.stamp {
  position: absolute;
  right: 60rpx;
  bottom: 80rpx;
  width: 140rpx;
  height: 140rpx;
  border: 4rpx solid;
  border-radius: 50%;
  font-size: 24rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  transform: rotate(-25deg);
  font-weight: bold;
}
.green-stamp { border-color: rgba(0, 184, 137, 0.2); color: rgba(0, 184, 137, 0.2); }
.red-stamp { border-color: rgba(255, 77, 79, 0.2); color: rgba(255, 77, 79, 0.2); }
.gray-stamp { border-color: rgba(153, 153, 153, 0.2); color: rgba(153, 153, 153, 0.2); }
</style>
