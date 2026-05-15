<template>
  <view class="container">
    <!-- 顶部导航与搜索 -->
    <view class="top-nav">
      <view class="nav-left">
        <text>全部工单</text>
        <uni-icons type="bottom" size="12" color="#333"></uni-icons>
      </view>
      <view class="nav-right">
        <uni-icons type="search" size="22" color="#333"></uni-icons>
        <uni-icons type="list" size="22" color="#333" style="margin: 0 30rpx;"></uni-icons>
        <uni-icons type="tune" size="22" color="#333"></uni-icons>
      </view>
    </view>

    <!-- 状态切换页签 -->
    <view class="status-tabs">
      <view class="tab active">全部</view>
      <view class="tab">未开始 1</view>
      <view class="tab">执行中 7</view>
      <view class="tab">已结束 0</view>
    </view>

    <!-- 列表内容 -->
    <view class="total-count">总计 ({{ orders.length || 1 }})</view>

    <scroll-view scroll-y class="order-list">
      <view v-for="(item, index) in orders" :key="index" class="order-card" @tap="goToDetail(item)">
        <view class="card-main">
          <!-- 左侧缩略图 -->
          <image class="product-img" src="/static/logo.png" mode="aspectFill"></image>
          
          <view class="card-info">
            <view class="info-top">
              <view class="tags">
                <view class="tag urgent">加急</view>
                <view class="tag status">{{ item.status || '未开始' }}</view>
              </view>
              <uni-icons type="more-filled" size="18" color="#ccc"></uni-icons>
            </view>
            <text class="order-code">{{ item.code }}</text>
            <view class="info-field">
              <uni-icons type="paperclip" size="12" color="#999"></uni-icons>
              <text class="label">工单编号：</text>
              <text class="val">{{ item.code }}</text>
            </view>
            <view class="info-field">
              <uni-icons type="shop" size="12" color="#999"></uni-icons>
              <text class="label">产品信息：</text>
              <text class="val">02 | 宝慧轴承S681 | 61*8*1.5r</text>
            </view>
            <view class="info-field">
              <uni-icons type="pyramid" size="12" color="#999"></uni-icons>
              <text class="label">工单进度：</text>
              <text class="val">{{ item.actualQuantity || 0 }}/{{ item.plannedQuantity || 1000 }}个</text>
            </view>
            <view class="info-field">
              <uni-icons type="person" size="12" color="#999"></uni-icons>
              <text class="label">客户简称：</text>
              <text class="val">-</text>
            </view>
          </view>
        </view>

        <!-- 底部工序进度轴 -->
        <view class="process-steps">
          <view v-for="(p, pi) in 5" :key="pi" class="step-item">
            <view class="step-circle">0%</view>
            <text class="step-name">下料</text>
          </view>
          <view class="step-line"></view>
        </view>
      </view>
      
      <view class="no-more">没有更多了</view>
    </scroll-view>

    <!-- 悬浮扫码按钮 -->
    <view class="scan-fab" @tap="handleScan">
      <uni-icons type="scan" size="30" color="white"></uni-icons>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchWorkOrders } from '../../services/api'

const orders = ref<any[]>([])

const loadOrders = async () => {
  try {
    const res = await fetchWorkOrders()
    orders.value = res
  } catch (e) {
    // Mock data if API fails
    orders.value = [{ code: 'GD20220119', status: '未开始', plannedQuantity: 1000 }]
  }
}

onMounted(() => {
  loadOrders()
})

const goToDetail = (item: any) => {
  uni.navigateTo({
    url: `/pages/work-order-detail/index?id=${item.id || 1}`
  })
}

const handleScan = () => {
  uni.scanCode({
    success: (res) => {
      console.log('扫码结果：', res.result)
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
.nav-left text { margin-right: 10rpx; }
.nav-right { display: flex; align-items: center; }

.status-tabs {
  background: white;
  display: flex;
  padding: 10rpx 20rpx;
  gap: 20rpx;
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

.card-main { display: flex; margin-bottom: 30rpx; }
.product-img { width: 140rpx; height: 140rpx; border-radius: 8rpx; background: #f0f0f0; flex-shrink: 0; }

.card-info { flex: 1; margin-left: 20rpx; }
.info-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.tags { display: flex; gap: 10rpx; }
.tag { font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 4rpx; }
.tag.urgent { background: #ff4d4f; color: white; }
.tag.status { background: #e6f7ff; color: #1890ff; }

.order-code { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 15rpx; }
.info-field { display: flex; align-items: center; margin-bottom: 6rpx; }
.info-field .label { font-size: 22rpx; color: #999; margin-left: 10rpx; }
.info-field .val { font-size: 22rpx; color: #666; }

/* 底部进度轴 */
.process-steps {
  display: flex;
  justify-content: space-between;
  position: relative;
  padding: 0 10rpx;
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
.step-name { font-size: 20rpx; color: #999; }
.step-line {
  position: absolute;
  top: 25rpx;
  left: 40rpx;
  right: 40rpx;
  height: 2rpx;
  background: #eee;
  z-index: 0;
}

.no-more { text-align: center; font-size: 24rpx; color: #ccc; padding: 40rpx 0; }

/* 扫码悬浮按钮 */
.scan-fab {
  position: fixed;
  right: 40rpx;
  bottom: 160rpx;
  width: 100rpx;
  height: 100rpx;
  background: rgba(0,0,0,0.6);
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 8rpx 20rpx rgba(0,0,0,0.2);
}
</style>
