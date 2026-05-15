<template>
  <view class="container">
    <!-- 顶部工单基本信息 -->
    <view class="header">
      <view class="status-tag">执行中</view>
      <text class="order-code">{{ order.code || 'GD20230401-1' }}</text>
      <view class="info-list">
        <view class="info-item">
          <uni-icons type="calendar" size="14" color="#999"></uni-icons>
          <text class="info-label">计划时间：</text>
          <text class="info-val">2023-04-21 ~ 2023-04-23</text>
        </view>
        <view class="info-item">
          <uni-icons type="shop" size="14" color="#999"></uni-icons>
          <text class="info-label">产品信息：</text>
          <text class="info-val green-link">01 | 黑湖精密模具 | HRC46-50 ></text>
        </view>
        <view class="info-item">
          <uni-icons type="pyramid" size="14" color="#999"></uni-icons>
          <text class="info-label">工单进度：</text>
          <text class="info-val">{{ order.actualQuantity || 0 }}/{{ order.plannedQuantity || 2018 }}套</text>
        </view>
      </view>
    </view>

    <!-- 页签切换 -->
    <view class="tabs">
      <view class="tab-item active">生产进度<view class="line"></view></view>
      <view class="tab-item">生产履历</view>
    </view>

    <!-- 工序列表 -->
    <scroll-view scroll-y class="process-list">
      <view class="filter-bar">
        <uni-icons type="settings" size="16" color="#999"></uni-icons>
        <text>筛选</text>
      </view>

      <view v-for="(step, index) in order.processes" :key="index" class="process-card">
        <view class="card-top">
          <view class="step-title">
            <view class="step-num">{{ step.sequenceNo }}</view>
            <text class="step-name">{{ step.name }}</text>
          </view>
          <text class="step-status">执行中</text>
        </view>

        <view class="card-content">
          <view class="data-columns">
            <view class="col">
              <text class="cl">计划数</text>
              <text class="cv">{{ order.plannedQuantity }}</text>
            </view>
            <view class="col">
              <text class="cl">良品数</text>
              <text class="cv green">{{ step.actualQuantity || 0 }}</text>
            </view>
            <view class="col">
              <text class="cl">不良数</text>
              <text class="cv red">{{ step.defectQuantity || 0 }}</text>
            </view>
          </view>
          
          <view class="progress-circle">
            <view class="circle-box">
              <text class="percent-txt">{{ calculatePercent(step) }}%</text>
            </view>
          </view>
        </view>

        <view class="card-btns">
          <view class="btn ghost" @tap="handleReport(step)">详情</view>
          <view class="btn primary" @tap="handleReport(step)">报工</view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="footer">
      <view class="f-btn">更多...</view>
      <view class="f-btn">优先级</view>
      <view class="f-btn finish">结束</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchWorkOrder } from '../../services/api'

const props = defineProps<{
  id: string
}>()

const order = ref<any>({
  processes: []
})

const loadData = async () => {
  try {
    const res = await fetchWorkOrder(props.id || '1')
    order.value = res
  } catch (e) {}
}

onMounted(() => {
  loadData()
})

const calculatePercent = (step: any) => {
  if (!order.value.plannedQuantity) return 0
  return Math.round(((step.actualQuantity || 0) / order.value.plannedQuantity) * 100)
}

const handleReport = (step: any) => {
  uni.navigateTo({
    url: `/pages/report-form/index?orderId=${order.value.id}&processNo=${step.sequenceNo}&processName=${encodeURIComponent(step.name)}`
  })
}
</script>

<style scoped>
.container {
  background: #f6f7f7;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background: white;
  padding: 30rpx;
  position: relative;
}

.status-tag {
  background: #e6f7f3;
  color: #00b889;
  font-size: 20rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  display: inline-block;
  margin-bottom: 10rpx;
}

.order-code {
  font-size: 36rpx;
  font-weight: bold;
  display: block;
  margin-bottom: 20rpx;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 10rpx;
}

.info-label { font-size: 24rpx; color: #999; margin-left: 10rpx; }
.info-val { font-size: 24rpx; color: #666; }
.green-link { color: #00b889; text-decoration: underline; }

.tabs {
  display: flex;
  background: white;
  border-bottom: 1px solid #f0f0f0;
}

.tab-item {
  flex: 1;
  text-align: center;
  height: 90rpx;
  line-height: 90rpx;
  font-size: 28rpx;
  color: #666;
  position: relative;
}

.tab-item.active {
  color: #00b889;
  font-weight: bold;
}

.tab-item.active .line {
  position: absolute;
  bottom: 0;
  left: 30%;
  width: 40%;
  height: 4rpx;
  background: #00b889;
}

.process-list {
  flex: 1;
  padding: 20rpx;
  box-sizing: border-box;
}

.filter-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  font-size: 24rpx;
  color: #999;
  margin-bottom: 10rpx;
}

.process-card {
  background: white;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
}

.card-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 30rpx;
}

.step-title { display: flex; align-items: center; }
.step-num {
  width: 36rpx;
  height: 36rpx;
  background: #00b889;
  color: white;
  border-radius: 50%;
  font-size: 20rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 15rpx;
}

.step-name { font-size: 30rpx; font-weight: bold; }
.step-status { font-size: 24rpx; color: #00b889; }

.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30rpx;
}

.data-columns {
  display: flex;
  flex: 1;
}

.col {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.cl { font-size: 20rpx; color: #999; margin-bottom: 10rpx; }
.cv { font-size: 30rpx; font-weight: bold; }
.cv.green { color: #00b889; }
.cv.red { color: #ff4d4f; }

.circle-box {
  width: 90rpx;
  height: 90rpx;
  border: 6rpx solid #eef3fe;
  border-top-color: #4080ff;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.percent-txt { font-size: 20rpx; color: #4080ff; font-weight: bold; }

.card-btns {
  display: flex;
  justify-content: flex-end;
}

.btn {
  height: 60rpx;
  padding: 0 40rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-left: 20rpx;
}

.btn.ghost { border: 1px solid #ddd; color: #666; }
.btn.primary { border: 1px solid #00b889; color: #00b889; }

.footer {
  height: 110rpx;
  background: white;
  display: flex;
  padding: 10rpx 20rpx;
  padding-bottom: env(safe-area-inset-bottom);
  border-top: 1px solid #f0f0f0;
}

.f-btn {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 28rpx;
  color: #666;
  background: #f8f9f9;
  margin: 10rpx;
  border-radius: 10rpx;
}

.f-btn.finish {
  background: #ff4d4f;
  color: white;
}
</style>
