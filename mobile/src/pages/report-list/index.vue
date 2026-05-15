<template>
  <view class="container">
    <!-- 顶部筛选与统计 -->
    <view class="dashboard-header">
      <view class="filter-row">
        <view class="dropdown">上月 <uni-icons type="bottom" size="12" color="#666"></uni-icons></view>
        <view class="dropdown">张俊健 <uni-icons type="bottom" size="12" color="#666"></uni-icons></view>
      </view>

      <view class="stat-cards">
        <view class="stat-card green">
          <text class="st">工资总计</text>
          <text class="sv">956.1</text>
        </view>
        <view class="stat-card blue">
          <text class="st">审批 / 总计</text>
          <text class="sv">26 / 26</text>
        </view>
        <view class="stat-card orange">
          <text class="st">总数 / 良品 / 不良品</text>
          <text class="sv">4387 / 4328 / 59</text>
        </view>
        <view class="stat-card red">
          <text class="st">不合格率</text>
          <text class="sv">1.34%</text>
        </view>
      </view>

      <view class="tab-row">
        <view class="tab">未审批 0</view>
        <view class="tab active">已审批 26<view class="line"></view></view>
        <view class="tab-icons">
          <uni-icons type="compose" size="20" color="#666"></uni-icons>
          <uni-icons type="list" size="20" color="#666" style="margin-left: 20rpx;"></uni-icons>
        </view>
      </view>
    </view>

    <!-- 记录列表 -->
    <scroll-view scroll-y class="list-content">
      <view v-for="i in 3" :key="i" class="report-card">
        <view class="card-header">
          <text class="user-info">张俊健 | 2023-03-31 17:19:16</text>
        </view>
        
        <view class="card-body">
          <view class="field">
            <uni-icons type="paperclip" size="14" color="#ccc"></uni-icons>
            <text class="fl">工单编号：</text>
            <text class="fv">GD20230351-1</text>
          </view>
          <view class="field">
            <uni-icons type="settings" size="14" color="#ccc"></uni-icons>
            <text class="fl">工序名称：</text>
            <text class="fv">包装</text>
          </view>
          <view class="field">
            <uni-icons type="shop" size="14" color="#ccc"></uni-icons>
            <text class="fl">产品信息：</text>
            <text class="fv">06 | 给水用钢丝SRPT双色管 | 1.2m</text>
          </view>
          <view class="field">
            <uni-icons type="calendar" size="14" color="#ccc"></uni-icons>
            <text class="fl">报工时间：</text>
            <text class="fv">2023-03-31 17:19:00 - 2023-03-31 17:19:00</text>
          </view>
          <view class="field">
            <uni-icons type="checkbox" size="14" color="#ccc"></uni-icons>
            <text class="fl">报工数量：</text>
            <text class="fv">总304 / 良品 <text class="green">300</text> / 不良品 <text class="red">4</text></text>
          </view>
          <view class="field">
            <uni-icons type="wallet" size="14" color="#ccc"></uni-icons>
            <text class="fl">工资单价：</text>
            <text class="fv">0.2元</text>
          </view>
          <view class="field">
            <uni-icons type="medal" size="14" color="#ccc"></uni-icons>
            <text class="fl">预计工资：</text>
            <text class="fv green">60元</text>
          </view>
          <view class="field urgent-row">
            <view class="left">
              <uni-icons type="help" size="14" color="#ccc"></uni-icons>
              <text class="fl">是否加急：-</text>
            </view>
            <switch checked color="#00b889" style="transform:scale(0.7)" />
          </view>
        </view>

        <!-- 审批通过印章样式 -->
        <view class="stamp">审批通过</view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.container { background: #f6f7f7; height: 100vh; display: flex; flex-direction: column; }

.dashboard-header { background: white; padding: 20rpx; }
.filter-row { display: flex; margin-bottom: 20rpx; }
.dropdown { margin-right: 40rpx; font-size: 28rpx; color: #333; display: flex; align-items: center; }

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
.tab-icons { flex: 1; display: flex; justify-content: flex-end; align-items: center; }

.list-content { flex: 1; padding: 20rpx; box-sizing: border-box; }
.report-card { background: white; border-radius: 16rpx; padding: 30rpx; margin-bottom: 20rpx; position: relative; overflow: hidden; }
.card-header { border-bottom: 1px solid #f8f8f8; padding-bottom: 20rpx; margin-bottom: 20rpx; }
.user-info { font-size: 28rpx; font-weight: bold; color: #333; }

.field { display: flex; align-items: flex-start; margin-bottom: 12rpx; }
.fl { font-size: 24rpx; color: #999; margin-left: 10rpx; width: 140rpx; flex-shrink: 0; }
.fv { font-size: 24rpx; color: #666; }
.green { color: #00b889; }
.red { color: #ff4d4f; }

.urgent-row { justify-content: space-between; align-items: center; margin-top: 10rpx; }
.urgent-row .left { display: flex; align-items: center; }

/* 印章 CSS */
.stamp {
  position: absolute;
  right: 60rpx;
  bottom: 80rpx;
  width: 140rpx;
  height: 140rpx;
  border: 4rpx solid rgba(0, 184, 137, 0.2);
  border-radius: 50%;
  color: rgba(0, 184, 137, 0.2);
  font-size: 24rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  transform: rotate(-25deg);
  font-weight: bold;
}
.stamp::after {
  content: '';
  position: absolute;
  top: 10rpx; left: 10rpx; right: 10rpx; bottom: 10rpx;
  border: 1px solid rgba(0, 184, 137, 0.1);
  border-radius: 50%;
}
</style>
