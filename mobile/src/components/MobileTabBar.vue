<template>
  <view class="mobile-tabbar">
    <view
      v-for="item in items"
      :key="item.key"
      class="tab-item"
      :class="{ active: current === item.key }"
      @click="open(item.path)"
    >
      <uni-icons :type="item.icon" size="21" :color="current === item.key ? '#00a982' : '#8a9693'" />
      <text>{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{ current: string }>()

const items = [
  { key: 'orders', label: '工单', icon: 'compose', path: '/pages/work-orders/index' },
  { key: 'tasks', label: '任务', icon: 'list', path: '/pages/tasks/index' },
  { key: 'home', label: '报工', icon: 'plus-filled', path: '/pages/home/index' },
  { key: 'reports', label: '记录', icon: 'bars', path: '/pages/report-list/index' },
  { key: 'board', label: '看板', icon: 'map', path: '/pages/board/index' }
]

function open(path: string) {
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = `#${path}`
    return
  }
  uni.switchTab({ url: path })
}
</script>

<style scoped>
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  height: calc(104rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  background: #fff;
  border-top: 1rpx solid #e8eceb;
  box-sizing: border-box;
}

.tab-item {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  color: #8a9693;
  font-size: 20rpx;
  position: relative;
}

.tab-item.active {
  color: #00a982;
}

.tab-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 36rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: #00a982;
  opacity: 0;
  transform: translateX(-50%) scaleX(0.4);
  transition: opacity 180ms ease, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.tab-item.active::before {
  opacity: 1;
  transform: translateX(-50%) scaleX(1);
}
</style>
