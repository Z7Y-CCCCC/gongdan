<template>
  <view class="container">
    <view class="welcome-section">
      <view class="user-info">
        <text class="greeting">您好，</text>
        <text class="username">{{ userInfo.nickname || userInfo.username || '工友' }}</text>
      </view>
      <text class="date">{{ currentDate }}</text>
    </view>

    <view class="action-grid">
      <view class="action-card scan" @tap="handleScan">
        <view class="icon-wrapper">
          <uni-icons type="scan" size="30" color="#fff"></uni-icons>
        </view>
        <text class="card-title">扫码报工</text>
        <text class="card-desc">扫描工单二维码快速报工</text>
      </view>

      <view class="menu-list">
        <view class="menu-item" @tap="navigateTo('/pages/work-orders/index')">
          <view class="menu-left">
            <view class="menu-icon bg-green">
              <uni-icons type="list" size="20" color="#fff"></uni-icons>
            </view>
            <text class="menu-text">我的工单</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc"></uni-icons>
        </view>

        <view class="menu-item" @tap="navigateTo('/pages/report-list/index')">
          <view class="menu-left">
            <view class="menu-icon bg-blue">
              <uni-icons type="paperplane" size="20" color="#fff"></uni-icons>
            </view>
            <text class="menu-text">报工历史</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc"></uni-icons>
        </view>

        <view class="menu-item" @tap="navigateTo('/pages/field-events/index')">
          <view class="menu-left">
            <view class="menu-icon bg-purple">
              <uni-icons type="sound" size="20" color="#fff"></uni-icons>
            </view>
            <text class="menu-text">现场事件</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc"></uni-icons>
        </view>

        <view class="menu-item" @tap="navigateTo('/pages/board/index')">
          <view class="menu-left">
            <view class="menu-icon bg-orange">
              <uni-icons type="bars" size="20" color="#fff"></uni-icons>
            </view>
            <text class="menu-text">车间看板</text>
          </view>
          <uni-icons type="right" size="16" color="#ccc"></uni-icons>
        </view>
      </view>
    </view>

    <view class="stats-section">
      <text class="section-title">今日报工统计</text>
      <view class="stats-grid">
        <view class="stat-box">
          <text class="stat-value">0</text>
          <text class="stat-label">完成件数</text>
        </view>
        <view class="stat-box">
          <text class="stat-value">0</text>
          <text class="stat-label">待审记录</text>
        </view>
        <view class="stat-box">
          <text class="stat-value">0%</text>
          <text class="stat-label">合格率</text>
        </view>
      </view>
    </view>

    <button class="logout-link" @tap="handleLogout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { clearSession, ensureLogin } from '../../services/api'

const userInfo = ref<any>({})
const currentDate = ref('')

onMounted(() => {
  if (!ensureLogin()) return
  
  const user = uni.getStorageSync('user')
  if (user) userInfo.value = user
  
  const now = new Date()
  currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
})

const handleScan = () => {
  if (typeof window !== 'undefined' && window.location) {
    // H5 模式不支持原生扫码
    uni.showModal({
      title: '提示',
      content: 'H5 模式暂不支持扫码，请在工单列表中选择工单进行报工。',
      showCancel: false
    })
    return
  }
  uni.scanCode({
    success: (res) => {
      if (res.result) {
        const url = res.result
        const idMatch = url.match(/[?&]id=(\d+)/)
        if (idMatch) {
          uni.navigateTo({ url: `/pages/work-order-detail/index?id=${idMatch[1]}` })
          return
        }
        const codeMatch = url.match(/[?&]code=([^&]+)/)
        if (codeMatch) {
          uni.navigateTo({ url: `/pages/work-order-detail/index?code=${codeMatch[1]}` })
          return
        }
        uni.navigateTo({ url: `/pages/work-order-detail/index?id=${res.result}` })
      }
    }
  })
}

const navigateTo = (url: string) => {
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = `#${url}`
    return
  }
  uni.navigateTo({ url })
}

const handleLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录并解除绑定吗？',
    success: (res) => {
      if (res.confirm) {
        clearSession()
        uni.reLaunch({ url: '/pages/bind/index' })
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
}

.welcome-section {
  margin-bottom: 40rpx;
  padding-top: 20rpx;
}

.user-info {
  display: flex;
  align-items: baseline;
  margin-bottom: 10rpx;
}

.greeting {
  font-size: 32rpx;
  color: #666;
}

.username {
  font-size: 48rpx;
  font-weight: bold;
  color: #333;
}

.date {
  font-size: 24rpx;
  color: #999;
}

.action-card.scan {
  background: linear-gradient(135deg, #00b889, #00d4a0);
  padding: 60rpx 40rpx;
  border-radius: 24rpx;
  color: white;
  margin-bottom: 30rpx;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10rpx 30rpx rgba(0, 184, 137, 0.2);
}

.icon-wrapper {
  margin-bottom: 20rpx;
}

.card-title {
  font-size: 40rpx;
  font-weight: bold;
  margin-bottom: 10rpx;
}

.card-desc {
  font-size: 24rpx;
  opacity: 0.8;
}

.menu-list {
  background: white;
  border-radius: 24rpx;
  padding: 0 30rpx;
  margin-bottom: 40rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 40rpx 0;
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

.bg-green { background-color: #e6f7f3; color: #00b889; }
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
  border-radius: 16rpx;
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
  color: #999;
}

.logout-link {
  margin-top: 60rpx;
  font-size: 28rpx;
  color: #ff4d4f;
  background: none;
  border: none;
}
.logout-link::after { border: none; }
</style>
