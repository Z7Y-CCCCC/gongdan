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
      <view class="form-item" @click="!loading && !reporterLoading && (showReporterPicker = true)">
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
        <view class="time-item">
          <text class="tl">开始时间 <text class="required">*</text></text>
          <!-- #ifdef H5 -->
          <input type="datetime-local" aria-label="开始时间" class="datetime-input" :value="form.startTime.replace(' ', 'T').slice(0, 16)" @change="changeH5Time('startTime', $event)" />
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <picker mode="date" :value="form.startTime.slice(0, 10)" @change="changeTime('startTime', 'date', $event.detail.value)"><text class="tv">{{ form.startTime.slice(0, 10) || '选择日期' }}</text></picker>
          <picker mode="time" :value="form.startTime.slice(11, 16)" @change="changeTime('startTime', 'time', $event.detail.value)"><text class="tv">{{ form.startTime.slice(11, 16) || '选择时间' }}</text></picker>
          <!-- #endif -->
        </view>
        <view class="time-divider"></view>
        <view class="time-item">
          <text class="tl">结束时间 <text class="required">*</text></text>
          <!-- #ifdef H5 -->
          <input type="datetime-local" aria-label="结束时间" class="datetime-input" :value="form.endTime.replace(' ', 'T').slice(0, 16)" @change="changeH5Time('endTime', $event)" />
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <picker mode="date" :value="form.endTime.slice(0, 10)" @change="changeTime('endTime', 'date', $event.detail.value)"><text class="tv">{{ form.endTime.slice(0, 10) || '选择日期' }}</text></picker>
          <picker mode="time" :value="form.endTime.slice(11, 16)" @change="changeTime('endTime', 'time', $event.detail.value)"><text class="tv">{{ form.endTime.slice(11, 16) || '选择时间' }}</text></picker>
          <!-- #endif -->
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
            <!-- #ifdef H5 -->
            <img :src="img.previewPath || ''" alt="报工附件" class="uploaded-img" />
            <!-- #endif -->
            <!-- #ifndef H5 -->
            <image :src="img.previewPath || ''" mode="aspectFill" class="uploaded-img" />
            <!-- #endif -->
            <view class="img-delete" @click="removeImage(idx)"><uni-icons type="closeempty" size="12" color="#fff" /></view>
          </view>
          <view class="add-box" @click="chooseImage" v-if="uploadedImages.length < 9">
            <uni-icons type="camera-filled" size="24" color="#8a9693" />
          </view>
        </view>
        <text v-if="uploading" class="tl">图片上传中，请稍候…</text>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="footer">
      <button class="btn outline" :disabled="loading || uploading || reporterLoading" @click="handleSubmit(true)">继续报工</button>
      <button class="btn solid" :loading="loading" :disabled="loading || uploading || reporterLoading" @click="handleSubmit(false)">提交</button>
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
import { ref, reactive, onBeforeUnmount, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createReport, downloadReportImage, ensureLogin, fetchWorkOrder, uploadReportImage, fetchWorkOrderReporters } from '../../services/api'
import type { ReportImage } from '../../types/api'
import { usePageDraft } from '../../composables/usePageDraft'
import { formatDateTime, updateDateTime, validateReportValues } from '../../utils/report'

// URL 参数
let paramOrderId = ''
let paramProcessNo = ''
let paramProcessName = ''
let paramPlannedQty = ''
let paramCompletedQty = ''
let paramDefectQty = ''

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
const uploading = ref(false)
const reporterLoading = ref(true)
const showReporterPicker = ref(false)
let navigationTimer: ReturnType<typeof setTimeout> | undefined
onBeforeUnmount(() => { if (navigationTimer) clearTimeout(navigationTimer) })

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
  if (!ensureLogin()) return
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
      const [order, users] = await Promise.all([
        fetchWorkOrder(form.workOrderId),
        fetchWorkOrderReporters(form.workOrderId, form.processSequenceNo)
      ])
      const task = order.tasks.find(item => item.sequenceNo === form.processSequenceNo)
      if (!task || task.enabled === false) throw new Error('工序不存在或已停用')
      form.processName = task.processName
      processName.value = task.processName
      plannedQty.value = task.plannedQuantity ?? order.plannedQuantity
      completedQty.value = task.completedQuantity || 0
      defectQtyDisplay.value = task.defectQuantity || 0
      reporterList.value = users
      const selected = users.find(item => item.id === selectedReporterId.value) || users.find(item => item.id === user?.id)
      selectedReporterId.value = selected?.id || 0
      selectedReporterName.value = selected?.name || '请选择生产人员'
      for (const image of [...uploadedImages.value]) {
        if (image.previewPath) continue
        try {
          image.previewPath = await downloadReportImage(image.id)
        } catch (e: any) {
          uploadedImages.value = uploadedImages.value.filter(item => item.id !== image.id)
          uni.showToast({ title: e.message || '草稿图片已失效，请重新上传', icon: 'none' })
        }
      }
    } catch (e: any) {
      reporterList.value = []
      selectedReporterId.value = 0
      selectedReporterName.value = '人员加载失败，请返回重试'
      uni.showToast({ title: e.message || '报工人员加载失败', icon: 'none' })
    } finally {
      reporterLoading.value = false
    }
  } else {
    reporterLoading.value = false
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
    uploadedImages: uploadedImages.value.map(({ previewPath, ...image }) => image)
  }),
  restore: (draft) => {
    if (draft.form) {
      for (const key of ['reportQuantity', 'goodQuantity', 'defectQuantity', 'defectItemName', 'startTime', 'endTime', 'materialName', 'remark'] as const) {
        if (typeof draft.form[key] === 'string') form[key] = draft.form[key]
      }
    }
    durationHours.value = draft.durationHours ?? '0'
    durationMinutes.value = draft.durationMinutes ?? '0'
    selectedReporterId.value = Number(draft.selectedReporterId || 0)
    selectedReporterName.value = draft.selectedReporterName || ''
    uploadedImages.value = Array.isArray(draft.uploadedImages)
      ? draft.uploadedImages.filter(image => Number.isInteger(image.id) && image.id > 0).slice(0, 9).map(({ previewPath, ...image }: ReportImage) => image)
      : []
  }
})

const selectReporter = (user: any) => {
  selectedReporterId.value = user.id
  selectedReporterName.value = user.name
  showReporterPicker.value = false
}

function changeTime(field: 'startTime' | 'endTime', part: 'date' | 'time', value: string) {
  form[field] = updateDateTime(form[field], part, value)
}

function changeH5Time(field: 'startTime' | 'endTime', event: Event) {
  const value = (event.target as HTMLInputElement)?.value || ''
  form[field] = value ? `${value.slice(0, 16).replace('T', ' ')}:00` : ''
}

const chooseImage = () => {
  if (loading.value || uploading.value || reporterLoading.value || uploadedImages.value.length >= 9) return
  uploading.value = true
  uni.chooseImage({
    count: 9 - uploadedImages.value.length,
    success: async (res) => {
      try {
        for (const path of res.tempFilePaths.slice(0, 9 - uploadedImages.value.length)) {
          try {
            const img = await uploadReportImage(path)
            img.previewPath = path
            uploadedImages.value.push(img)
          } catch (e: any) {
            uni.showToast({ title: e.message || '图片上传失败', icon: 'none' })
          }
        }
      } finally {
        uploading.value = false
      }
    },
    fail: () => { uploading.value = false }
  })
}

const removeImage = (idx: number) => {
  if (loading.value || uploading.value) return
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
  if (loading.value || reporterLoading.value) return
  if (uploading.value) {
    uni.showToast({ title: '请等待图片上传完成', icon: 'none' })
    return
  }
  if (!ensureLogin()) return
  if (!form.workOrderId || !form.processSequenceNo) {
    uni.showToast({ title: '工单或工序信息未加载，请返回重进', icon: 'none' })
    return
  }
  if (!reporterList.value.some(user => user.id === selectedReporterId.value)) {
    uni.showToast({ title: '请选择生产人员', icon: 'none' })
    return
  }
  const error = validateReportValues(form, durationHours.value, durationMinutes.value)
  if (error) {
    uni.showToast({ title: error, icon: 'none' })
    return
  }

  const totalMinutes = Number(durationHours.value) * 60 + Number(durationMinutes.value)

  loading.value = true
  let submitted = false
  uni.showLoading({ title: '提交中...' })
  let loadingVisible = true
  const hideSubmitLoading = () => {
    if (loadingVisible) uni.hideLoading()
    loadingVisible = false
  }
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
    submitted = true
    hideSubmitLoading()
    uni.showToast({ title: '提交成功', icon: 'success' })
    clearReportDraft()
    if (!isContinue) {
      navigationTimer = setTimeout(() => goAfterSubmit(), 1500)
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
      form.startTime = formatDateTime(new Date())
      form.endTime = form.startTime
    }
  } catch (e: any) {
    hideSubmitLoading()
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    hideSubmitLoading()
    if (!submitted || isContinue) loading.value = false
  }
}

const handleClose = () => {
  if (loading.value || uploading.value) return
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

.time-item { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.datetime-input { display: block; width: 100%; min-width: 0; height: 72rpx; font-size: 23rpx; color: #202926; }
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
  object-fit: cover;
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
.btn[disabled] { opacity: 0.6; }

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
