<template>
  <view class="container">
    <!-- 顶部状态摘要 -->
    <view class="summary-card">
      <view class="step-header">
        <view class="step-num">{{ processNo }}</view>
        <text class="step-name">{{ processName }}</text>
        <view class="close-icon" @click="handleClose"><uni-icons type="closeempty" size="20" color="#8a9693" /></view>
      </view>
      <view class="data-grid">
        <view class="data-item">
          <text class="dl">计划数</text>
          <text class="dv">{{ plannedQty }}</text>
        </view>
        <view class="data-item">
          <text class="dl">良品数</text>
          <text class="dv green">{{ completedQty }}</text>
        </view>
        <view class="data-item">
          <text class="dl">不良品数</text>
          <text class="dv red">{{ defectQtyDisplay }}</text>
        </view>
      </view>
    </view>

    <!-- 表单主体 -->
    <view class="form-list">
      <!-- 生产人员 -->
      <view class="form-item" @click="showReporterPicker = true">
        <view class="label"><text class="required">*</text>生产人员</view>
        <view class="value-row">
          <text>{{ selectedReporterName }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <!-- 报工数 -->
      <view class="form-item">
        <view class="label"><text class="required">*</text>报工数</view>
        <view class="value-row">
          <input type="number" v-model="form.reportQuantity" placeholder="0" class="num-input" @input="syncGoodQuantity" />
          <text class="unit">{{ productUnit }} ›</text>
        </view>
      </view>

      <!-- 良品数 -->
      <view class="form-item">
        <view class="label">良品数</view>
        <view class="value-row">
          <input type="number" v-model="form.goodQuantity" placeholder="0" class="num-input" />
        </view>
      </view>

      <!-- 不良数 -->
      <view class="form-item">
        <view class="label">不良数</view>
        <view class="value-row">
          <input type="number" v-model="form.defectQuantity" placeholder="请输入" class="num-input red-text" @input="syncGoodQuantity" />
        </view>
      </view>

      <!-- 不良品项 -->
      <view class="form-item" v-if="Number(form.defectQuantity) > 0">
        <view class="label">不良品项</view>
        <view class="value-row">
          <input v-model="form.defectItemName" class="text-input" placeholder="请输入不良原因" />
        </view>
      </view>

      <!-- 报工时长 -->
      <view class="form-item">
        <view class="label">报工时长</view>
        <view class="value-row">
          <input type="number" v-model="durationHours" class="mini-input" />
          <text class="unit-s">小时</text>
          <input type="number" v-model="durationMinutes" class="mini-input" />
          <text class="unit-s">分钟 ›</text>
        </view>
      </view>

      <!-- 开始时间 / 结束时间 -->
      <view class="time-range-box">
        <view class="time-item" @click="pickStartTime">
          <text class="tl">开始时间 <text class="required">*</text></text>
          <text class="tv">{{ form.startTime || '请选择' }}</text>
        </view>
        <view class="time-divider"></view>
        <view class="time-item" @click="pickEndTime">
          <text class="tl">结束时间 <text class="required">*</text></text>
          <text class="tv">{{ form.endTime || '请选择' }}</text>
        </view>
      </view>

      <!-- 领料产品 -->
      <view class="form-item">
        <view class="label">领料产品</view>
        <view class="value-row">
          <input v-model="form.materialName" class="text-input" placeholder="可选，填写领料产品" />
        </view>
      </view>

      <!-- 备注 -->
      <view class="form-item-textarea">
        <view class="label">备注</view>
        <textarea class="textarea-input" v-model="form.remark" placeholder="请输入备注..." maxlength="500" />
      </view>

      <!-- 底部图片上传 -->
      <view class="upload-section">
        <view class="label">报废/次品照片</view>
        <view class="image-grid">
          <view v-for="(img, idx) in uploadedImages" :key="idx" class="img-box">
            <image :src="img.previewPath || img.accessPath" mode="aspectFill" class="uploaded-img" />
            <view class="img-delete" @click="removeImage(idx)"><uni-icons type="closeempty" size="12" color="#fff" /></view>
          </view>
          <view class="add-box" @click="chooseImage" v-if="uploadedImages.length < 9">
            <uni-icons type="camera-filled" size="24" color="#8a9693" />
          </view>
        </view>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="footer">
      <view class="btn outline" @click="handleSubmit(true)">继续报工</view>
      <view class="btn solid" @click="handleSubmit(false)">提交</view>
    </view>

    <!-- 生产人员选择弹窗 -->
    <view v-if="showReporterPicker" class="picker-mask" @click="showReporterPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text class="picker-title">选择生产人员</text>
          <view class="picker-close" @click="showReporterPicker = false"><uni-icons type="closeempty" size="20" color="#8a9693" /></view>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view v-for="user in reporterList" :key="user.id"
                class="picker-item" :class="{ selected: selectedReporterId === user.id }"
                @click="selectReporter(user)">
            <text>{{ user.name }}</text>
            <text class="picker-sub">{{ user.username }}</text>
            <text v-if="selectedReporterId === user.id" class="picker-check">✓</text>
          </view>
          <view v-if="reporterList.length === 0" class="picker-empty">暂无可选人员</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createReport, uploadReportImage, fetchWorkOrderReporters } from '../../services/api'
import type { ReportImage } from '../../types/api'
import { usePageDraft } from '../../composables/usePageDraft'

// URL 参数
let paramOrderId = ''
let paramProcessNo = ''
let paramProcessName = ''
let paramPlannedQty = ''
let paramCompletedQty = ''
let paramDefectQty = ''
let paramReportPermission = ''

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function applyRouteOptions(options: Record<string, any> = {}) {
  paramOrderId = String(options.orderId || '')
  paramProcessNo = String(options.processNo || '1')
  paramProcessName = safeDecode(String(options.processName || ''))
  paramPlannedQty = String(options.plannedQty || '0')
  paramCompletedQty = String(options.completedQty || '0')
  paramDefectQty = String(options.defectQty || '0')
  paramReportPermission = safeDecode(String(options.reportPermission || ''))
}

function readH5RouteOptions() {
  if (typeof window === 'undefined' || !window.location) {
    return {}
  }
  const hash = window.location.hash || ''
  const queryIndex = hash.indexOf('?')
  if (queryIndex < 0) {
    return {}
  }
  const result: Record<string, string> = {}
  new URLSearchParams(hash.slice(queryIndex + 1)).forEach((value, key) => {
    result[key] = value
  })
  return result
}

function formatDateTime(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

onLoad((options) => {
  applyRouteOptions(options || {})
})

const processNo = ref(1)
const processName = ref('')
const plannedQty = ref(0)
const completedQty = ref(0)
const defectQtyDisplay = ref(0)
const productUnit = ref('个')
const loading = ref(false)
const showReporterPicker = ref(false)

// 报工人员列表
const reporterList = ref<Array<{ id: number; name: string; username: string }>>([])
const selectedReporterId = ref(0)
const selectedReporterName = ref('')

// 图片
const uploadedImages = ref<ReportImage[]>([])

// 时长
const durationHours = ref('0')
const durationMinutes = ref('0')

// 表单
const form = reactive({
  workOrderId: 0,
  processSequenceNo: 0,
  processName: '',
  goodQuantity: '',
  defectQuantity: '',
  reportQuantity: '',
  reportDurationMinutes: 0,
  defectItemName: '',
  startTime: '',
  endTime: '',
  materialName: '',
  remark: ''
})

onMounted(async () => {
  if (!paramOrderId) {
    applyRouteOptions(readH5RouteOptions())
  }

  processNo.value = parseInt(paramProcessNo) || 1
  processName.value = paramProcessName
  plannedQty.value = parseInt(paramPlannedQty) || 0
  completedQty.value = parseInt(paramCompletedQty) || 0
  defectQtyDisplay.value = parseInt(paramDefectQty) || 0

  form.workOrderId = parseInt(paramOrderId) || 0
  form.processSequenceNo = processNo.value
  form.processName = processName.value

  // 设置默认时间
  const now = new Date()
  const timeStr = formatDateTime(now)
  form.startTime = timeStr
  form.endTime = timeStr

  // 加载当前登录用户信息
  const user = uni.getStorageSync('user')
  if (user) {
    selectedReporterId.value = user.id
    selectedReporterName.value = user.name || user.username || '当前用户'
  }

  // 加载有报工权限的用户列表
  if (form.workOrderId) {
    try {
      const users = await fetchWorkOrderReporters(form.workOrderId)
      
      // 根据工序的报工权限过滤人员列表
      if (paramReportPermission && paramReportPermission !== '所有人' && paramReportPermission !== 'ALL') {
        const allowedList = paramReportPermission.split(',').map((s: string) => s.trim())
        const currentUserAllowedByDepartment = Boolean(user?.departmentName && allowedList.includes(user.departmentName))
        reporterList.value = currentUserAllowedByDepartment ? users : users.filter(u =>
          allowedList.includes(u.name) ||
          allowedList.includes(u.username) ||
          allowedList.includes(String(u.id))
        )
      } else {
        reporterList.value = users
      }
    } catch (e) {
      // 如果接口不存在，用当前用户作为默认
      if (user) {
        reporterList.value = [{ id: user.id, name: user.name || user.username, username: user.username }]
      }
    }
  }
})

const { clearDraft: clearReportDraft } = usePageDraft({
  key: () => `gongdan:draft:report:${paramOrderId || form.workOrderId}:${paramProcessNo || form.processSequenceNo}`,
  source: () => ({
    form: { ...form },
    durationHours: durationHours.value,
    durationMinutes: durationMinutes.value,
    selectedReporterId: selectedReporterId.value,
    selectedReporterName: selectedReporterName.value,
    uploadedImages: uploadedImages.value
  }),
  restore: (draft) => {
    if (draft.form) Object.assign(form, draft.form)
    durationHours.value = draft.durationHours ?? '0'
    durationMinutes.value = draft.durationMinutes ?? '0'
    selectedReporterId.value = Number(draft.selectedReporterId || 0)
    selectedReporterName.value = draft.selectedReporterName || ''
    uploadedImages.value = Array.isArray(draft.uploadedImages) ? draft.uploadedImages : []
  }
})

const selectReporter = (user: any) => {
  selectedReporterId.value = user.id
  selectedReporterName.value = user.name
  showReporterPicker.value = false
}

const pickStartTime = () => {
  if (typeof document !== 'undefined') {
    const input = document.createElement('input')
    input.type = 'datetime-local'
    input.onchange = () => {
      if (input.value) {
        const d = new Date(input.value)
        form.startTime = formatDateTime(d)
      }
    }
    input.click()
    return
  }
  uni.showToast({ title: '请在小程序中选择时间', icon: 'none' })
}

const pickEndTime = () => {
  if (typeof document !== 'undefined') {
    const input = document.createElement('input')
    input.type = 'datetime-local'
    input.onchange = () => {
      if (input.value) {
        const d = new Date(input.value)
        form.endTime = formatDateTime(d)
      }
    }
    input.click()
    return
  }
  uni.showToast({ title: '请在小程序中选择时间', icon: 'none' })
}

const chooseImage = () => {
  uni.chooseImage({
    count: 9 - uploadedImages.value.length,
    success: async (res) => {
      for (const path of res.tempFilePaths) {
        try {
          const img = await uploadReportImage(path)
          img.previewPath = path
          uploadedImages.value.push(img)
        } catch (e) {
          uni.showToast({ title: '图片上传失败', icon: 'none' })
        }
      }
    }
  })
}

const removeImage = (idx: number) => {
  uploadedImages.value.splice(idx, 1)
}

function syncGoodQuantity() {
  const reportQuantity = Math.max(0, Number(form.reportQuantity) || 0)
  const defectQuantity = Math.max(0, Number(form.defectQuantity) || 0)
  form.goodQuantity = String(Math.max(0, reportQuantity - defectQuantity))
}

function goBack() {
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = `#/pages/work-order-detail/index?id=${form.workOrderId}`
    return
  }
  uni.navigateBack()
}

function goAfterSubmit() {
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = '#/pages/report-list/index'
    return
  }
  uni.switchTab({ url: '/pages/report-list/index' })
}

const handleSubmit = async (isContinue: boolean) => {
  if (!form.workOrderId || !form.processSequenceNo) {
    uni.showToast({ title: '工单或工序信息未加载，请返回重进', icon: 'none' })
    return
  }
  if (!selectedReporterId.value) {
    uni.showToast({ title: '请选择生产人员', icon: 'none' })
    return
  }
  if (!form.reportQuantity || Number(form.reportQuantity) <= 0) {
    uni.showToast({ title: '请填写报工数', icon: 'none' })
    return
  }
  if (!form.startTime || !form.endTime) {
    uni.showToast({ title: '请选择开始和结束时间', icon: 'none' })
    return
  }
  if ((Number(form.goodQuantity) || 0) + (Number(form.defectQuantity) || 0) !== Number(form.reportQuantity)) {
    uni.showToast({ title: '良品数与不良数之和必须等于报工数', icon: 'none' })
    return
  }

  const totalMinutes = (parseInt(durationHours.value) || 0) * 60 + (parseInt(durationMinutes.value) || 0)

  uni.showLoading({ title: '提交中...' })
  try {
    await createReport({
      workOrderId: form.workOrderId,
      processSequenceNo: form.processSequenceNo,
      processName: form.processName,
      reportQuantity: Number(form.reportQuantity) || 0,
      goodQuantity: Number(form.goodQuantity) || 0,
      defectQuantity: Number(form.defectQuantity) || 0,
      defectItemName: form.defectItemName || undefined,
      reportDurationMinutes: totalMinutes,
      startTime: form.startTime || undefined,
      endTime: form.endTime || undefined,
      materialName: form.materialName || undefined,
      remark: form.remark || undefined,
      imageIds: uploadedImages.value.map(img => img.id),
      reporterUserId: selectedReporterId.value || undefined
    })
    uni.hideLoading()
    uni.showToast({ title: '提交成功', icon: 'success' })
    clearReportDraft()
    if (!isContinue) {
      setTimeout(() => goAfterSubmit(), 1500)
    } else {
      // 继续报工：清空表单
      form.reportQuantity = ''
      form.goodQuantity = ''
      form.defectQuantity = ''
      form.defectItemName = ''
      form.materialName = ''
      form.remark = ''
      uploadedImages.value = []
      durationHours.value = '0'
      durationMinutes.value = '0'
    }
  } catch (e: any) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  }
}

const handleClose = () => {
  goBack()
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

.close-icon {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #ccc;
  font-size: 28rpx;
}

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

.form-item-textarea {
  padding: 30rpx;
  border-bottom: 1px solid #f8f8f8;
}

.label { font-size: 28rpx; color: #333; }
.required { color: #ff4d4f; margin-right: 4rpx; }

.value-row { display: flex; align-items: center; }
.placeholder { color: #ccc; font-size: 28rpx; }
.num-input {
  text-align: right;
  width: 180rpx;
  height: 58rpx;
  padding: 0 16rpx;
  font-size: 28rpx;
  background: #f7f9f8;
  border: 1rpx solid #dfe5e3;
  border-radius: 8rpx;
  box-sizing: border-box;
}
.red-text { color: #ff4d4f; }
.text-input { width: 260rpx; height: 58rpx; text-align: right; font-size: 26rpx; color: #202926; }
.unit { font-size: 26rpx; color: #666; margin-left: 10rpx; }
.arrow { font-size: 32rpx; color: #ccc; margin-left: 10rpx; }

.mini-input { width: 72rpx; height: 56rpx; text-align: center; font-size: 28rpx; background: #f7f9f8; border: 1rpx solid #dfe5e3; border-radius: 8rpx; }
.unit-s { font-size: 24rpx; color: #999; margin: 0 10rpx; }

.textarea-input {
  width: 100%;
  min-height: 100rpx;
  font-size: 28rpx;
  color: #333;
  margin-top: 16rpx;
  padding: 16rpx;
  box-sizing: border-box;
  background: #f9f9f9;
  border-radius: 8rpx;
}

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
}

.upload-section {
  padding: 30rpx;
}

.image-grid { margin-top: 20rpx; display: flex; flex-wrap: wrap; gap: 16rpx; }

.img-box {
  position: relative;
  width: 120rpx;
  height: 120rpx;
}

.uploaded-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
}

.img-delete {
  position: absolute;
  top: -10rpx;
  right: -10rpx;
  width: 32rpx;
  height: 32rpx;
  background: #ff4d4f;
  color: white;
  border-radius: 50%;
  font-size: 18rpx;
  display: flex;
  justify-content: center;
  align-items: center;
}

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
  z-index: 20;
  background: white;
  padding: 20rpx 40rpx;
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -4rpx 20rpx rgba(0,0,0,0.05);
  pointer-events: auto;
}

.btn {
  flex: 1;
  height: 90rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 30rpx;
  font-weight: bold;
  margin: 0 10rpx;
}

.btn.outline { border: 1px solid #00b889; color: #00b889; }
.btn.solid { background: #00b889; color: white; }

/* 人员选择弹窗 */
.picker-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.picker-content {
  background: white;
  width: 100%;
  max-height: 60vh;
  border-radius: 24rpx 24rpx 0 0;
  overflow: hidden;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  border-bottom: 1px solid #f0f0f0;
}

.picker-title { font-size: 32rpx; font-weight: bold; }
.picker-close { font-size: 32rpx; color: #999; padding: 0 10rpx; }

.picker-list {
  max-height: 50vh;
  padding: 0 30rpx;
}

.picker-item {
  display: flex;
  align-items: center;
  padding: 30rpx 0;
  border-bottom: 1px solid #f8f8f8;
  font-size: 30rpx;
}

.picker-item.selected {
  color: #00b889;
}

.picker-sub {
  font-size: 24rpx;
  color: #999;
  margin-left: 20rpx;
}

.picker-check {
  margin-left: auto;
  color: #00b889;
  font-weight: bold;
}

.picker-empty {
  text-align: center;
  padding: 60rpx 0;
  color: #999;
  font-size: 28rpx;
}
</style>
