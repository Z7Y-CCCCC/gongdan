<template>
  <view class="container">
    <view class="welcome-section ui-page-head">
      <view class="user-info">
        <text class="username">{{ userInfo.name || userInfo.nickname || userInfo.username || '工友' }}</text>
        <text class="date">{{ currentDate }}</text>
      </view>
      <IconButton icon="refreshempty" label="刷新报工统计" :busy="statsLoading" @click="loadStats" />
    </view>

    <view class="action-grid">
      <button class="action-card scan" aria-label="扫码报工" @click="handleScan">
        <view class="icon-wrapper">
          <uni-icons type="scan" size="28" color="#fff" aria-hidden="true"></uni-icons>
        </view>
        <text class="card-title">扫码报工</text>
      </button>

      <view class="menu-list">
        <button class="menu-item" @click="navigateTo('/pages/work-orders/index')">
          <view class="menu-left">
            <view class="menu-icon bg-green">
              <uni-icons type="list" size="20" color="#00a982" aria-hidden="true"></uni-icons>
            </view>
            <text class="menu-text">我的工单</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc" aria-hidden="true"></uni-icons>
        </button>

        <button class="menu-item" @click="navigateTo('/pages/report-list/index')">
          <view class="menu-left">
            <view class="menu-icon bg-blue">
              <uni-icons type="paperplane" size="20" color="#4080ff" aria-hidden="true"></uni-icons>
            </view>
            <text class="menu-text">报工历史</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc" aria-hidden="true"></uni-icons>
        </button>

        <button class="menu-item" @click="navigateTo('/pages/field-events/index')">
          <view class="menu-left">
            <view class="menu-icon bg-purple">
              <uni-icons type="sound" size="20" color="#7c3aed" aria-hidden="true"></uni-icons>
            </view>
            <text class="menu-text">现场事件</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc" aria-hidden="true"></uni-icons>
        </button>

        <button class="menu-item" @click="navigateTo('/pages/board/index')">
          <view class="menu-left">
            <view class="menu-icon bg-orange">
              <uni-icons type="bars" size="20" color="#d97706" aria-hidden="true"></uni-icons>
            </view>
            <text class="menu-text">车间看板</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc" aria-hidden="true"></uni-icons>
        </button>
      </view>
    </view>

    <view class="stats-section">
      <text class="section-title">今日报工</text>
      <view v-if="statsError" class="stats-state">
        <text>{{ statsError }}</text>
        <IconButton icon="refreshempty" label="重试报工统计" @click="loadStats" />
      </view>
      <view v-else-if="statsLoading" class="stats-state">刷新中...</view>
      <view v-else-if="dashboard" class="stats-grid">
        <view class="stat-box">
          <text class="stat-value">{{ todayGood }}</text>
          <text class="stat-label">完成件数</text>
        </view>
        <view class="stat-box">
          <text class="stat-value">{{ todayPending }}</text>
          <text class="stat-label">待审记录</text>
        </view>
        <view class="stat-box">
          <text class="stat-value">{{ todayPassRate }}%</text>
          <text class="stat-label">合格率</text>
        </view>
      </view>
    </view>

    <button class="logout-link" aria-label="退出登录" @click="handleLogout">退出</button>
    <!-- #ifdef H5 -->
    <ScanWorkOrderDialog v-model="showScan" />
    <MobileTabBar current="home" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { clearSession, ensureLogin } from '../../services/api'
import MobileTabBar from '../../components/MobileTabBar.vue'
import IconButton from '../../components/IconButton.vue'
import { navigatePage } from '../../utils/navigation'
import { useMobileDashboard } from '../../composables/useMobileDashboard'
import { scannedWorkOrderRoute } from '../../utils/scan-target'
// #ifdef H5
import ScanWorkOrderDialog from '../../components/ScanWorkOrderDialog.vue'
const showScan = ref(false)
// #endif

const userInfo = ref<any>({})
const currentDate = ref('')
const { dashboard, loading: statsLoading, errorMessage: statsError, load: loadDashboard } = useMobileDashboard()
const todayGood = computed(() => dashboard.value?.todayGood || 0)
const todayDefect = computed(() => dashboard.value?.todayDefect || 0)
const todayPending = computed(() => dashboard.value?.todayPending || 0)
const todayPassRate = computed(() => {
  const total = todayGood.value + todayDefect.value
  return total > 0 ? Math.round(todayGood.value / total * 100) : 0
})

onMounted(loadStats)

onShow(loadStats)

async function loadStats() {
  userInfo.value = uni.getStorageSync('user') || {}
  const now = new Date()
  currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
  await loadDashboard()
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

const navigateTo = (url: string) => {
  navigatePage(url)
}

const handleLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确定要退出当前登录吗？微信绑定关系将保留。',
    cancelText: '取消',
    confirmText: '退出',
    success: (res) => {
      if (res.confirm) {
        clearSession()
        navigatePage('/pages/bind/index?manual=1', true)
      }
    }
  })
}
</script>

<style scoped>
.container {
  padding: 30rpx;
  background-color: #f3f5f5;
  min-height: 100vh;
  padding-bottom: 150rpx;
}

.stats-state {
  color: #64756d;
  font-size: 26rpx;
  text-align: center;
  padding: 24rpx 0;
}

.retry-btn {
  margin-top: 20rpx;
  color: #00856b;
  font-size: 26rpx;
}

.welcome-section {
  margin-bottom: 24rpx;
}

.user-info {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  margin-bottom: 10rpx;
  min-width: 0;
  flex: 1;
}

.greeting {
  font-size: 32rpx;
  color: #666;
}

.username {
  font-size: 38rpx;
  font-weight: bold;
  color: #333;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.date {
  font-size: 24rpx;
  color: #64756d;
}

.action-card.scan {
  background: #00856b;
  padding: 28rpx 32rpx;
  border-radius: 16rpx;
  color: white;
  margin-bottom: 30rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 20rpx;
  width: 100%;
  box-shadow: 0 8rpx 20rpx rgba(0, 169, 130, 0.16);
}

.icon-wrapper {
  margin: 0;
}

.card-title {
  font-size: 32rpx;
  font-weight: bold;
  margin: 0;
}

.card-desc {
  font-size: 24rpx;
  opacity: 0.8;
}

.menu-list {
  background: white;
  border-radius: 16rpx;
  padding: 0 30rpx;
  margin-bottom: 40rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  width: 100%;
  background: transparent;
  border-radius: 0;
  border-bottom: 1px solid #f0f0f0;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-left {
  display: flex;
  align-items: center;
}

.menu-icon {
  width: 70rpx;
  height: 70rpx;
  border-radius: 16rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 24rpx;
}

.bg-green { background-color: #e6f7f3; color: #00856b; }
.bg-blue { background-color: #eef3fe; color: #4080ff; }
.bg-orange { background-color: #fff4e6; color: #ff9900; }
.bg-purple { background-color: #f0edff; color: #7c3aed; }

.menu-text {
  font-size: 30rpx;
  color: #333;
  font-weight: 500;
}

.stats-section {
  margin-top: 40rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 24rpx;
  display: block;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
}

.stat-box {
  background: white;
  padding: 30rpx 20rpx;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 36rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 8rpx;
}

.stat-label {
  font-size: 22rpx;
  color: #64756d;
}

.logout-link {
  margin-top: 40rpx;
  font-size: 26rpx;
  color: #586360;
  background: none;
  border: none;
}
.logout-link::after { border: none; }
</style>
