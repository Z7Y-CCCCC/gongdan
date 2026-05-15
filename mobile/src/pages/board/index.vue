<template>
  <view class="container">
    <view class="dashboard">
      <view class="chart-header">
        <text class="chart-title">车间实时生产达成</text>
        <text class="chart-tag">实时更新</text>
      </view>
      
      <view class="main-gauge">
        <text class="gauge-value">85%</text>
        <text class="gauge-label">综合达成率</text>
      </view>

      <view class="mini-charts">
        <view class="mini-item">
          <text class="v blue">1,240</text>
          <text class="l">今日排产</text>
        </view>
        <view class="mini-item">
          <text class="v green">1,054</text>
          <text class="l">已入库</text>
        </view>
        <view class="mini-item">
          <text class="v orange">12</text>
          <text class="l">异常停机</text>
        </view>
      </view>
    </view>

    <view class="rank-section">
      <text class="section-title">今日报工排行</text>
      <view class="rank-list">
        <view v-for="(u, index) in rankings" :key="index" class="rank-item">
          <text class="rank-no">{{ index + 1 }}</text>
          <text class="rank-name">{{ u.name }}</text>
          <view class="rank-progress">
            <view class="r-p-inner" :style="{ width: u.progress + '%' }"></view>
          </view>
          <text class="rank-val">{{ u.val }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const rankings = ref([
  { name: '王大力', progress: 95, val: '450' },
  { name: '李小美', progress: 88, val: '420' },
  { name: '张工', progress: 70, val: '335' },
  { name: '赵师傅', progress: 65, val: '310' }
])
</script>

<style scoped>
.container {
  padding: 30rpx;
  background: #10141d;
  min-height: 100vh;
}

.dashboard {
  background: #1a1f2e;
  border-radius: 30rpx;
  padding: 40rpx;
  color: white;
  margin-bottom: 40rpx;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 60rpx;
}

.chart-title { font-size: 30rpx; font-weight: bold; }
.chart-tag { font-size: 20rpx; background: rgba(0,184,137,0.2); color: #00b889; padding: 4rpx 12rpx; border-radius: 4rpx; }

.main-gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200rpx;
  margin-bottom: 60rpx;
}

.gauge-value { font-size: 80rpx; font-weight: bold; color: #00b889; }
.gauge-label { font-size: 24rpx; color: #667085; }

.mini-charts {
  display: flex;
  justify-content: space-between;
}

.mini-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.v { font-size: 32rpx; font-weight: bold; margin-bottom: 10rpx; }
.v.blue { color: #4080ff; }
.v.green { color: #00b889; }
.v.orange { color: #ff9900; }
.l { font-size: 20rpx; color: #667085; }

.section-title { font-size: 32rpx; font-weight: bold; color: #fff; margin-bottom: 30rpx; display: block; }

.rank-item {
  display: flex;
  align-items: center;
  margin-bottom: 30rpx;
}

.rank-no { width: 40rpx; color: #667085; font-size: 24rpx; }
.rank-name { width: 120rpx; color: #fff; font-size: 26rpx; }
.rank-progress {
  flex: 1;
  height: 12rpx;
  background: #252b3d;
  border-radius: 6rpx;
  margin: 0 20rpx;
}
.r-p-inner { height: 100%; background: #00b889; border-radius: 6rpx; }
.rank-val { width: 80rpx; color: #667085; font-size: 24rpx; text-align: right; }
</style>
