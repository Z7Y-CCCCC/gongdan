<template>
  <view class="container" role="main" aria-label="报工工作台">
    <view class="welcome-section ui-page-head">
      <view class="user-info">
        <text class="username" role="heading" aria-level="1" :aria-label="'工作台 · ' + (userInfo.name || userInfo.username || '工友')">{{ userInfo.name || userInfo.nickname || userInfo.username || '工友' }}</text>
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

    <button class="logout-link" aria-label="退出登录" :disabled="logoutPending" :loading="logoutPending" :aria-busy="logoutPending" @click="handleLogout">退出</button>
    <view v-if="logoutError" class="logout-error" role="alert"><text>{{ logoutError }}</text><button :disabled="logoutPending" @click="retryLogout">重试退出</button></view>
    <!-- #ifdef H5 -->
    <ScanWorkOrderDialog v-model="showScan" />
    <MobileMessageDialog v-model="pageMessage.message.visible" :title="pageMessage.message.title" :content="pageMessage.message.content" :confirm-text="pageMessage.message.confirmText" :cancel-text="pageMessage.message.cancelText" @confirm="pageMessage.decide(true)" @cancel="pageMessage.decide(false)" />
    <MobileTabBar current="home" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { clearSession, ensureLogin, logoutSession } from '../../services/api'
import MobileTabBar from '../../components/MobileTabBar.vue'
import IconButton from '../../components/IconButton.vue'
import { navigatePage } from '../../utils/navigation'
import { useMobileDashboard } from '../../composables/useMobileDashboard'
import { scannedWorkOrderRoute } from '../../utils/scan-target'
import { mobileSessionKey } from '../../utils/session-key'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'
import { usePageMessage } from '../../composables/usePageMessage'

const pageActivity = useMobilePageActivity()
const pageMessage = usePageMessage()
// #ifdef H5
import ScanWorkOrderDialog from '../../components/ScanWorkOrderDialog.vue'
import MobileMessageDialog from '../../components/MobileMessageDialog.vue'
const showScan = ref(false)
// #endif

const userInfo = ref<any>({})
const logoutPending = ref(false)
const logoutError = ref('')
let logoutSequence = 0
let failedLogoutSession = ''
let pageAlive = true
onBeforeUnmount(() => { pageAlive = false })
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
  if (!pageAlive || !ensureLogin()) return
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture(true)
  const current = () => pageAlive && isPageCurrent() && session === mobileSessionKey()
  // #ifdef H5
  if (typeof window !== 'undefined' && window.location) {
    showScan.value = true
    return
  }
  // #endif
  uni.scanCode({
    success: (res) => {
      if (!current()) return
      try { navigatePage(scannedWorkOrderRoute(String(res.result || ''))) }
      catch (error) { uni.showToast({ title: error instanceof Error ? error.message : '不是工单二维码', icon: 'none' }) }
    },
    fail: error => { if (current() && !error.errMsg?.includes('cancel')) uni.showToast({ title: '扫码失败，请重试', icon: 'none' }) }
  })
}

const navigateTo = (url: string) => {
  navigatePage(url)
}

const handleLogout = () => {
  if (!pageAlive || logoutPending.value) return
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  pageMessage.open({
    title: '退出登录',
    content: '退出此设备登录？微信绑定关系将保留。',
    cancelText: '取消',
    confirmText: '退出',
    showCancel: true
  }, accepted => {
      if (accepted && pageAlive && isPageCurrent() && session === mobileSessionKey()) {
        return performLogout(session, isPageCurrent)
      }
  })
}

async function performLogout(session: string, isPageCurrent = pageActivity.capture()) {
  if (!pageAlive || logoutPending.value || session !== mobileSessionKey()) return
  const sequence = ++logoutSequence
  const ownsPage = () => pageAlive && sequence === logoutSequence && isPageCurrent()
  const current = () => ownsPage() && session === mobileSessionKey()
  logoutPending.value = true
  logoutError.value = ''
  try {
    await logoutSession()
    if (!current()) return
    clearSession()
    failedLogoutSession = ''
    navigatePage('/pages/bind/index?manual=1', true)
  } catch (error: any) {
    const expired = error?.status === 401 && (session === mobileSessionKey() || !uni.getStorageSync('token') && !uni.getStorageSync('user'))
    if (ownsPage() && expired) {
      clearSession()
      failedLogoutSession = ''
      navigatePage('/pages/bind/index?manual=1', true)
    } else if (current()) {
      failedLogoutSession = session
      logoutError.value = `退出未完成，登录仍保留。${error?.message || '网络请求失败'}；请重试。`
    }
  } finally {
    if (pageAlive && sequence === logoutSequence) logoutPending.value = false
  }
}

function retryLogout() {
  if (failedLogoutSession && failedLogoutSession !== mobileSessionKey()) {
    logoutError.value = '登录状态已变更，请刷新后重新退出'
    return
  }
  return performLogout(mobileSessionKey())
}
</script>

<style scoped>
.container {
  padding: 30rpx;
  background-color: var(--mobile-bg, #f3f5f5);
  min-height: 100vh;
  padding-bottom: 150rpx;
}

.stats-state {
  color: var(--mobile-muted, #64756d);
  font-size: 26rpx;
  text-align: center;
  padding: 24rpx 0;
}

.retry-btn {
  margin-top: 20rpx;
  color: var(--mobile-link, #00856b);
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
  color: var(--mobile-muted, #666);
}

.username {
  font-size: 38rpx;
  font-weight: bold;
  color: var(--mobile-text, #333);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.date {
  font-size: 24rpx;
  color: var(--mobile-muted, #64756d);
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
  background: var(--mobile-surface, white);
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
  border-bottom: 1px solid var(--mobile-border, #f0f0f0);
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

.bg-green { background-color: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #00856b); }
.bg-blue { background-color: var(--mobile-info-bg, #eef3fe); color: var(--mobile-info, #2459a6); }
.bg-orange { background-color: var(--mobile-warning-bg, #fff4e6); color: var(--mobile-warning, #8b5908); }
.bg-purple { background-color: var(--mobile-info-bg, #f0edff); color: var(--mobile-info, #7c3aed); }

.menu-text {
  font-size: 30rpx;
  color: var(--mobile-text, #333);
  font-weight: 500;
}

.stats-section {
  margin-top: 40rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: var(--mobile-text, #333);
  margin-bottom: 24rpx;
  display: block;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
}

.stat-box {
  background: var(--mobile-surface, white);
  padding: 30rpx 20rpx;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--mobile-text, #333);
  margin-bottom: 8rpx;
}

.stat-label {
  font-size: 22rpx;
  color: var(--mobile-muted, #64756d);
}

.logout-link {
  margin-top: 40rpx;
  font-size: 26rpx;
  color: var(--mobile-muted, #586360);
  background: none;
  border: none;
}
.logout-link::after { border: none; }
.logout-error { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 12px; margin-top: 8px; border-radius: 8px; font-size: 13px; line-height: 1.6; background: var(--mobile-error-bg, #fff1f0); color: var(--mobile-error, #a0332c); overflow-wrap: anywhere; }
.logout-error button { padding: 0 16px; min-height: 44px; border-radius: 8px; font-size: 13px; background: var(--mobile-surface, white); color: var(--mobile-link, #007a60); }
</style>
