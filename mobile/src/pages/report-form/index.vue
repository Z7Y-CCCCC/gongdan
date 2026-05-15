<template>
  <view class="container">
    <!-- 顶部状态摘要 -->
    <view class="summary-card">
      <view class="step-header">
        <view class="step-num">1</view>
        <text class="step-name">{{ processName }}</text>
        <uni-icons type="closeempty" size="20" color="#ccc" class="close-icon"></uni-icons>
      </view>
      <view class="data-grid">
        <view class="data-item">
          <text class="dl">计划数</text>
          <text class="dv">2,018</text>
        </view>
        <view class="data-item">
          <text class="dl">良品数</text>
          <text class="dv green">2,298</text>
        </view>
        <view class="data-item">
          <text class="dl">不良品数</text>
          <text class="dv red">24</text>
        </view>
      </view>
    </view>

    <!-- 表单主体 -->
    <view class="form-list">
      <view class="form-item">
        <view class="label"><text class="required">*</text>生产人员</view>
        <view class="value-row">
          <text>张俊健</text>
          <uni-icons type="right" size="14" color="#ccc"></uni-icons>
        </view>
      </view>

      <view class="form-item">
        <view class="label"><text class="required">*</text>报工数</view>
        <view class="value-row">
          <input type="number" v-model="form.reportQuantity" placeholder="0" class="num-input" />
          <text class="unit">套 ></text>
        </view>
      </view>

      <view class="form-item">
        <view class="label">良品数</view>
        <view class="value-row">
          <input type="number" v-model="form.goodQuantity" placeholder="0" class="num-input" />
        </view>
      </view>

      <view class="form-item">
        <view class="label">不良数</view>
        <view class="value-row">
          <input type="number" v-model="form.defectQuantity" placeholder="请输入" class="num-input red-text" />
        </view>
      </view>

      <view class="form-item" v-if="form.defectQuantity > 0">
        <view class="label">不良品项</view>
        <view class="value-row">
          <text class="placeholder">请选择</text>
          <uni-icons type="right" size="14" color="#ccc"></uni-icons>
        </view>
      </view>

      <view class="form-item">
        <view class="label">报工时长</view>
        <view class="value-row">
          <input type="number" value="0" class="mini-input" />
          <text class="unit-s">小时</text>
          <input type="number" value="0" class="mini-input" />
          <text class="unit-s">分钟 ></text>
        </view>
      </view>

      <view class="time-range-box">
        <view class="time-item">
          <text class="tl">开始时间 <text class="required">*</text></text>
          <text class="tv">04-21 18:23</text>
        </view>
        <view class="time-divider"></view>
        <view class="time-item">
          <text class="tl">结束时间 <text class="required">*</text></text>
          <text class="tv">04-21 18:23</text>
        </view>
      </view>

      <view class="form-item">
        <view class="label">领料产品</view>
        <view class="value-row">
          <text class="placeholder">请选择</text>
          <uni-icons type="right" size="14" color="#ccc"></uni-icons>
        </view>
      </view>

      <!-- 底部图片上传 -->
      <view class="upload-section">
        <view class="label">报废/次品照片</view>
        <view class="image-grid">
          <view class="add-box" @tap="chooseImage">
            <uni-icons type="camera" size="30" color="#ccc"></uni-icons>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="footer">
      <view class="btn outline" @tap="handleSubmit(true)">继续报工</view>
      <view class="btn solid" @tap="handleSubmit(false)">提交</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { createReport } from '../../services/api'

const props = defineProps<{
  orderId: string
  processNo: string
  processName: string
}>()

const form = reactive({
  workOrderId: 0,
  processSequenceNo: 0,
  processName: '',
  goodQuantity: 0,
  defectQuantity: 0,
  reportQuantity: 0,
  reportDurationMinutes: 0
})

onMounted(() => {
  form.workOrderId = parseInt(props.orderId)
  form.processSequenceNo = parseInt(props.processNo)
  form.processName = decodeURIComponent(props.processName)
})

const chooseImage = () => {
  uni.chooseImage({ count: 1 })
}

const handleSubmit = async (isContinue: boolean) => {
  uni.showLoading({ title: '提交中...' })
  try {
    await createReport(form as any)
    uni.showToast({ title: '提交成功' })
    if (!isContinue) {
      setTimeout(() => uni.navigateBack(), 1500)
    }
  } catch (e) {
    uni.showToast({ title: '提交失败', icon: 'none' })
  }
}
</script>

<style scoped>
.container {
  background: #f6f7f7;
  min-height: 100vh;
  padding-bottom: 150rpx;
}

.summary-card {
  background: white;
  padding: 30rpx;
  border-bottom: 1px solid #f0f0f0;
}

.step-header {
  display: flex;
  align-items: center;
  margin-bottom: 30rpx;
}

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

.step-name { font-size: 32rpx; font-weight: bold; flex: 1; }

.data-grid {
  display: flex;
  justify-content: space-between;
}

.data-item { display: flex; flex-direction: column; }
.dl { font-size: 22rpx; color: #999; margin-bottom: 8rpx; }
.dv { font-size: 28rpx; font-weight: bold; }
.dv.green { color: #00b889; }
.dv.red { color: #ff4d4f; }

.form-list {
  background: white;
  margin-top: 20rpx;
}

.form-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  border-bottom: 1px solid #f8f8f8;
}

.label { font-size: 28rpx; color: #333; }
.required { color: #ff4d4f; margin-right: 4rpx; }

.value-row { display: flex; align-items: center; }
.placeholder { color: #ccc; font-size: 28rpx; }
.num-input { text-align: right; width: 200rpx; font-size: 28rpx; }
.red-text { color: #ff4d4f; }
.unit { font-size: 26rpx; color: #666; margin-left: 10rpx; }

.mini-input { width: 60rpx; text-align: center; font-size: 28rpx; }
.unit-s { font-size: 24rpx; color: #999; margin: 0 10rpx; }

.time-range-box {
  display: flex;
  padding: 30rpx;
  align-items: center;
  border-bottom: 1px solid #f8f8f8;
}

.time-item { flex: 1; display: flex; flex-direction: column; }
.tl { font-size: 22rpx; color: #999; margin-bottom: 8rpx; }
.tv { font-size: 28rpx; color: #333; }
.time-divider {
  width: 40rpx;
  height: 60rpx;
  border-left: 1px solid #eee;
  margin: 0 20rpx;
  border-radius: 50% 0 0 50%;
}

.upload-section {
  padding: 30rpx;
}

.image-grid { margin-top: 20rpx; }
.add-box {
  width: 120rpx;
  height: 120rpx;
  background: #f9f9f9;
  border: 1px dashed #ddd;
  border-radius: 8rpx;
  display: flex;
  justify-content: center;
  align-items: center;
}

.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: white;
  padding: 20rpx 40rpx;
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -4rpx 20rpx rgba(0,0,0,0.05);
}

.btn {
  flex: 1;
  height: 90rpx;
  border-radius: 45rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 30rpx;
  font-weight: bold;
  margin: 0 10rpx;
}

.btn.outline { border: 1px solid #00b889; color: #00b889; }
.btn.solid { background: #00b889; color: white; }
</style>
