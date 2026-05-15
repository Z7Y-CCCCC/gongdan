<template>
  <view class="container">
    <view class="header">
      <text class="title">我的待办任务</text>
      <text class="subtitle">共 {{ tasks.length }} 项待处理工序</text>
    </view>

    <view class="task-list">
      <view v-for="task in tasks" :key="task.id" class="task-card" @tap="handleTask(task)">
        <view class="task-icon">
          <uni-icons type="paperplane-filled" size="24" color="#00b889"></uni-icons>
        </view>
        <view class="task-info">
          <text class="task-name">{{ task.processName }}</text>
          <text class="task-order">{{ task.workOrderCode }}</text>
        </view>
        <uni-icons type="right" size="16" color="#ccc"></uni-icons>
      </view>
      
      <view v-if="tasks.length === 0" class="empty">
        <image class="empty-img" src="/static/empty-task.png" mode="aspectFit"></image>
        <text class="empty-text">当前暂无指派给您的任务</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const tasks = ref<any[]>([])

onMounted(() => {
  // 暂时模拟数据，后续对接 API
  tasks.value = [
    { id: 1, processName: '切削加工', workOrderCode: 'WO20260515001' },
    { id: 2, processName: '精磨工序', workOrderCode: 'WO20260515002' }
  ]
})

const handleTask = (task: any) => {
  // 跳转到对应的报工或详情
}
</script>

<style scoped>
.container {
  padding: 40rpx;
  background: #f6f7f7;
  min-height: 100vh;
}

.header {
  margin-bottom: 40rpx;
}

.title {
  font-size: 44rpx;
  font-weight: bold;
  color: #333;
  display: block;
}

.subtitle {
  font-size: 26rpx;
  color: #999;
  margin-top: 10rpx;
}

.task-list {
  margin-top: 20rpx;
}

.task-card {
  background: white;
  padding: 30rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.03);
}

.task-icon {
  width: 80rpx;
  height: 80rpx;
  background: #f0faf7;
  border-radius: 40rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 24rpx;
}

.task-info {
  flex: 1;
}

.task-name {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
  display: block;
}

.task-order {
  font-size: 24rpx;
  color: #999;
  margin-top: 6rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 100rpx;
}

.empty-img {
  width: 200rpx;
  height: 200rpx;
  opacity: 0.5;
}

.empty-text {
  font-size: 26rpx;
  color: #ccc;
  margin-top: 20rpx;
}
</style>
