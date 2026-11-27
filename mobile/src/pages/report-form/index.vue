<template>
  <view class="container" :class="{ 'report-has-feedback': submitPhase !== 'idle', 'report-awaiting-review': submitPhase === 'uncertain' }" role="main" aria-label="工序报工">
    <view :inert="showReporterPicker || undefined">
    <!-- 顶部状态摘要 -->
    <view class="summary-card">
      <view class="step-header">
        <view class="step-num">{{ processNo }}</view>
        <text class="step-name" role="heading" aria-level="1" :aria-label="'报工 · ' + processName">{{ processName }}</text>
        <IconButton icon="closeempty" label="关闭报工" :disabled="(loading && submitPhase !== 'success') || uploading" @click="handleClose" />
      </view>
      <view v-if="!reporterLoading && !loadError" class="data-grid">
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
    <view v-if="loadError" class="form-message form-error" role="alert"><text>{{ loadError }}</text><IconButton icon="refreshempty" label="重试报工信息" :busy="reporterLoading" @click="loadReportContext" /></view>
    <view v-else-if="isCorrection" class="form-message">重报需审批，原记录保留。</view>
    <view v-if="submitError && !['failed', 'uncertain'].includes(submitPhase)" ref="submitErrorSummary" class="form-message form-error" role="alert" tabindex="-1"><text>{{ submitError }}</text></view>
    <view class="form-list">
      <!-- 生产人员 -->
      <button ref="reporterTrigger" class="form-item reporter-select" aria-label="选择生产人员" aria-haspopup="dialog"
              :aria-expanded="showReporterPicker" :disabled="formLocked || reporterLoading || !!loadError" @click="openReporterPicker">
        <view class="label"><text class="required">*</text>生产人员</view>
        <view class="value-row">
          <text>{{ selectedReporterName }}</text>
          <uni-icons type="right" size="16" color="#64756d" aria-hidden="true" />
        </view>
      </button>

      <!-- 报工数 -->
      <view class="form-item">
        <view class="label"><text class="required">*</text>报工数</view>
        <view class="value-row">
          <input type="number" v-model="form.reportQuantity" :disabled="formLocked" aria-label="报工数" placeholder="0" class="num-input" @input="syncGoodQuantity" />
          <text class="unit">{{ productUnit }}</text>
        </view>
      </view>

      <!-- 良品数 -->
      <view class="form-item">
        <view class="label">良品数</view>
        <view class="value-row">
          <input type="number" v-model="form.goodQuantity" :disabled="formLocked" aria-label="良品数" placeholder="0" class="num-input" />
        </view>
      </view>

      <!-- 不良数 -->
      <view class="form-item">
        <view class="label">不良数</view>
        <view class="value-row">
          <input type="number" v-model="form.defectQuantity" :disabled="formLocked" aria-label="不良数" placeholder="请输入" class="num-input red-text" @input="syncGoodQuantity" />
        </view>
      </view>

      <!-- 不良品项 -->
      <!-- #ifdef H5 -->
      <Transition name="field-reveal">
      <!-- #endif -->
      <view class="form-item" v-if="Number(form.defectQuantity) > 0">
        <view class="label">不良品项</view>
        <view class="value-row">
          <input v-model="form.defectItemName" :disabled="formLocked" aria-label="不良原因" class="text-input" placeholder="请输入不良原因" />
        </view>
      </view>
      <!-- #ifdef H5 -->
      </Transition>
      <!-- #endif -->

      <!-- 报工时长 -->
      <view class="form-item">
        <view class="label">报工时长</view>
        <view class="value-row">
          <input type="number" v-model="durationHours" :disabled="formLocked" aria-label="报工小时" class="mini-input" />
          <text class="unit-s">小时</text>
          <input type="number" v-model="durationMinutes" :disabled="formLocked" aria-label="报工分钟" class="mini-input" />
          <text class="unit-s">分钟</text>
        </view>
      </view>

      <!-- 开始时间 / 结束时间 -->
      <view class="time-range-box">
        <view class="time-item">
          <text class="tl">开始时间 <text class="required">*</text></text>
          <!-- #ifdef H5 -->
          <input type="datetime-local" :disabled="formLocked" aria-label="开始时间" class="datetime-input" :value="form.startTime.replace(' ', 'T').slice(0, 16)" @change="changeH5Time('startTime', $event)" />
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <picker mode="date" :disabled="formLocked" :value="form.startTime.slice(0, 10)" @change="changeTime('startTime', 'date', $event.detail.value)"><text class="tv">{{ form.startTime.slice(0, 10) || '选择日期' }}</text></picker>
          <picker mode="time" :disabled="formLocked" :value="form.startTime.slice(11, 16)" @change="changeTime('startTime', 'time', $event.detail.value)"><text class="tv">{{ form.startTime.slice(11, 16) || '选择时间' }}</text></picker>
          <!-- #endif -->
        </view>
        <view class="time-divider"></view>
        <view class="time-item">
          <text class="tl">结束时间 <text class="required">*</text></text>
          <!-- #ifdef H5 -->
          <input type="datetime-local" :disabled="formLocked" aria-label="结束时间" class="datetime-input" :value="form.endTime.replace(' ', 'T').slice(0, 16)" @change="changeH5Time('endTime', $event)" />
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <picker mode="date" :disabled="formLocked" :value="form.endTime.slice(0, 10)" @change="changeTime('endTime', 'date', $event.detail.value)"><text class="tv">{{ form.endTime.slice(0, 10) || '选择日期' }}</text></picker>
          <picker mode="time" :disabled="formLocked" :value="form.endTime.slice(11, 16)" @change="changeTime('endTime', 'time', $event.detail.value)"><text class="tv">{{ form.endTime.slice(11, 16) || '选择时间' }}</text></picker>
          <!-- #endif -->
        </view>
      </view>

      <!-- 领料产品 -->
      <view class="form-item">
        <view class="label">领料产品</view>
        <view class="value-row">
          <input v-model="form.materialName" :disabled="formLocked" aria-label="领料产品" class="text-input" placeholder="产品（可选）" />
        </view>
      </view>

      <!-- 备注 -->
      <view class="form-item-textarea">
        <view class="label">备注</view>
        <textarea class="textarea-input" v-model="form.remark" :disabled="formLocked" aria-label="报工备注" placeholder="备注（可选）" maxlength="500" />
      </view>

      <!-- 底部图片上传 -->
      <view class="upload-section">
        <view class="label">照片 <text class="ui-meta">{{ uploadedImages.length }} / 9</text></view>
        <view class="image-grid">
          <view v-for="(img, idx) in uploadedImages" :key="idx" class="img-box">
            <!-- #ifdef H5 -->
            <img :src="img.previewPath || ''" alt="报工附件" class="uploaded-img" />
            <!-- #endif -->
            <!-- #ifndef H5 -->
            <image :src="img.previewPath || ''" mode="aspectFill" class="uploaded-img" />
            <!-- #endif -->
            <button class="img-delete" :aria-label="'移除照片 ' + (idx + 1)" :title="'移除照片 ' + (idx + 1)" :disabled="formLocked || uploading" @click="removeImage(idx)"><uni-icons type="closeempty" size="20" color="#d84545" aria-hidden="true" /></button>
          </view>
          <button class="add-box" aria-label="添加报工照片" title="添加报工照片" :disabled="formLocked || uploading || reporterLoading || !!loadError" @click="chooseImage" v-if="uploadedImages.length < 9">
            <uni-icons type="camera-filled" size="24" color="#64756d" aria-hidden="true" />
          </button>
        </view>
        <text v-if="uploading" class="tl">图片上传中，请稍候…</text>
      </view>
    </view>
    </view>

    <view class="footer" :inert="showReporterPicker || undefined">
      <view v-if="submitPhase !== 'idle'" :key="submitPhase" class="report-submit-feedback" :class="'report-feedback-' + submitPhase">
        <uni-icons :type="submitPhase === 'success' ? 'checkmarkempty' : submitPhase === 'submitting' ? 'paperplane' : 'info'" color="inherit" size="21" aria-hidden="true" />
        <view class="report-feedback-copy">
          <text ref="submitFeedbackTitle" class="report-feedback-title" role="status" tabindex="-1">{{ submitStatusLabel }}</text>
          <text v-if="submitError && ['failed', 'uncertain'].includes(submitPhase)" class="report-feedback-detail report-feedback-error">{{ submitError }}</text>
          <text v-if="submitPhase === 'success'" class="report-feedback-detail">{{ manuallyConfirmed ? '已核对到本次报工记录，不必重复提交。' : completedSubmissionContinues ? (loading ? '正在刷新工序数据…' : '可填写下一条报工。') : '已进入待审批。' }}</text>
          <text v-else-if="submitPhase === 'failed'" class="report-feedback-detail">已保留填写内容，修改后可重新提交。</text>
          <text v-else-if="submitPhase === 'uncertain'" class="report-feedback-detail">已保留填写内容，请先在记录中核对是否生成。</text>
        </view>
      </view>
      <template v-if="submitPhase === 'uncertain'">
        <view class="footer-actions">
          <button class="btn outline" :disabled="loading || !!loadError || reporterLoading" @click="confirmNotSubmitted">未找到记录</button>
          <button class="btn outline" :disabled="loading" @click="confirmExistingReport">已找到记录</button>
        </view>
        <view class="footer-actions report-review-lookup">
          <button class="btn solid" :disabled="loading" @click="goToReportRecords(false)">查看报工记录</button>
        </view>
      </template>
      <view v-else-if="submitPhase === 'success' && !completedSubmissionContinues" class="footer-actions">
        <button class="btn solid" @click="goToReportRecords(true)">查看报工记录</button>
      </view>
      <view v-else-if="submitPhase === 'failed'" class="footer-actions">
        <button class="btn solid" :disabled="submitDisabled" @click="handleSubmit(retryContinues)">重新提交</button>
      </view>
      <view v-else class="footer-actions">
        <button class="btn outline" :disabled="submitDisabled" @click="handleSubmit(true)">提交继续</button>
        <button class="btn solid" :aria-busy="submitPhase === 'submitting'" :disabled="submitDisabled" @click="handleSubmit(false)">{{ submitPhase === 'submitting' ? '提交中…' : '提交' }}</button>
      </view>
    </view>

    <!-- 生产人员选择弹窗 -->
    <!-- #ifdef H5 -->
    <Transition name="report-sheet">
    <!-- #endif -->
    <view v-if="showReporterPicker" class="reporter-backdrop" @click="showReporterPicker = false">
      <view ref="pickerDialog" class="reporter-sheet" role="dialog" aria-modal="true" aria-label="选择生产人员" @click.stop @keydown="trapPickerFocus">
        <view class="picker-header">
          <text class="picker-title">选择生产人员</text>
          <IconButton icon="closeempty" label="关闭人员选择" @click="showReporterPicker = false" />
        </view>
        <scroll-view scroll-y class="picker-list" :style="{ height: Math.min(reporterList.length * 56 + 12, 280) + 'px' }">
          <button v-for="user in reporterList" :key="user.id"
                class="picker-item" :class="{ selected: selectedReporterId === user.id }"
                :aria-pressed="selectedReporterId === user.id"
                @click="selectReporter(user)">
            <view class="picker-person"><text class="picker-name">{{ user.name }}</text><text class="picker-sub">{{ user.username }}</text></view>
            <uni-icons v-if="selectedReporterId === user.id" class="picker-check" type="checkmarkempty" color="inherit" size="22" aria-hidden="true" />
          </button>
          <view v-if="reporterList.length === 0" class="picker-empty">暂无可选人员</view>
        </scroll-view>
      </view>
    </view>
    <!-- #ifdef H5 -->
    </Transition>
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onBeforeUnmount, onMounted, nextTick, watch } from 'vue'
import { onHide, onLoad } from '@dcloudio/uni-app'
import { createReport, downloadReportImage, ensureLogin, fetchWorkOrder, uploadReportImage, fetchWorkOrderReporters } from '../../services/api'
import type { ReportImage } from '../../types/api'
import { usePageDraft } from '../../composables/usePageDraft'
import { formatDateTime, updateDateTime, validateReportValues } from '../../utils/report'
import { showPendingReportsOnReturn, showReportsForReview } from '../../utils/report-flow'
import { navigatePage } from '../../utils/navigation'
import IconButton from '../../components/IconButton.vue'
import { mobileSessionKey } from '../../utils/session-key'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'

const pageActivity = useMobilePageActivity()

// URL 参数
let paramOrderId = ''
let paramProcessNo = ''
let paramProcessName = ''
let paramPlannedQty = ''
let paramCompletedQty = ''
let paramDefectQty = ''
let initialReporterId = 0
let originalProcessName = ''
const isCorrection = ref(false)

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}
function numericRouteId(value: string) {
  const number = /^\d+$/.test(value) ? Number(value) : NaN
  return Number.isSafeInteger(number) && number > 0 ? number : 0
}

function applyRouteOptions(options: Record<string, any> = {}) {
  paramOrderId = String(options.orderId || '')
  paramProcessNo = String(options.processNo || '1')
  originalProcessName = String(options.processName || '')
  paramProcessName = safeDecode(originalProcessName)
  paramPlannedQty = String(options.plannedQty || '0')
  paramCompletedQty = String(options.completedQty || '0')
  paramDefectQty = String(options.defectQty || '0')
  isCorrection.value = String(options.correction || '') === '1'
  initialReporterId = isCorrection.value ? Number(options.reporterId) || 0 : 0
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
const productUnit = ref('')
const loading = ref(false)
const uploading = ref(false)
const reporterLoading = ref(true)
const loadError = ref('')
const submitError = ref('')
type SubmitPhase = 'idle' | 'submitting' | 'success' | 'failed' | 'uncertain'
const submitPhase = ref<SubmitPhase>('idle')
const retryContinues = ref(false)
const completedSubmissionContinues = ref(false)
const manuallyConfirmed = ref(false)
const reportNeedsReview = ref(false)
const submissionStartedAt = ref('')
const formLocked = computed(() => loading.value || reportNeedsReview.value)
const submitDisabled = computed(() => formLocked.value || uploading.value || reporterLoading.value || !!loadError.value)
const submitStatusLabel = computed(() => ({
  idle: '', submitting: '正在提交报工…', success: manuallyConfirmed.value ? '记录已核对' : '提交成功', failed: '提交失败', uncertain: '暂未确认提交结果'
})[submitPhase.value])
const submitFeedbackTitle = ref<any>(null)
const submitErrorSummary = ref<any>(null)
async function showSubmitError(message: string) {
  const canFocus = pageActivity.capture()
  submitError.value = message
  await nextTick()
  const element = submitErrorSummary.value?.$el || submitErrorSummary.value
  if (canFocus() && typeof document !== 'undefined') {
    if (element) element.focus()
    else void focusSubmitFeedback()
  }
}
const showReporterPicker = ref(false)
const pickerDialog = ref<any>(null)
const reporterTrigger = ref<any>(null)
let pickerReturnFocus: HTMLElement | null = null
function openReporterPicker() {
  if (formLocked.value || reporterLoading.value || loadError.value || !hasCurrentReportContext()) return
  pickerReturnFocus = reporterTrigger.value?.$el || reporterTrigger.value
  showReporterPicker.value = true
}
watch(showReporterPicker, async shown => {
  if (typeof document === 'undefined') return
  if (!shown) { if (pickerReturnFocus?.isConnected && pageAlive) pickerReturnFocus?.focus(); return }
  const current = pageActivity.capture()
  await nextTick()
  const dialog = pickerDialog.value?.$el || pickerDialog.value
  if (current() && showReporterPicker.value) dialog?.querySelector('button:not([disabled])')?.focus()
}, { flush: 'post' })

function trapPickerFocus(event: KeyboardEvent) {
  if (typeof document === 'undefined' || !showReporterPicker.value) return
  if (event.key === 'Escape') { event.preventDefault(); showReporterPicker.value = false; return }
  if (event.key !== 'Tab') return
  const dialog = pickerDialog.value?.$el || pickerDialog.value
  const buttons = Array.from(dialog?.querySelectorAll('button:not([disabled])') || []) as HTMLElement[]
  if (!buttons.length) return
  const first = buttons[0], last = buttons[buttons.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
let contextSequence = 0
let reportContextSession = ''
let pageAlive = true
let uploadSequence = 0
onHide(() => { pickerReturnFocus = null; showReporterPicker.value = false })
onBeforeUnmount(() => {
  pageAlive = false
  contextSequence++
  uploadSequence++
  uploading.value = false
})

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

watch(() => [form.reportQuantity, form.goodQuantity, form.defectQuantity, form.startTime, form.endTime,
  form.materialName, form.remark, durationHours.value, durationMinutes.value, selectedReporterId.value, uploadedImages.value.length], () => {
  if (submitPhase.value === 'success' && completedSubmissionContinues.value && !loading.value) submitPhase.value = 'idle'
})

onMounted(async () => {
  if (!ensureLogin()) return
  if (!paramOrderId) {
    applyRouteOptions(readH5RouteOptions())
  }

  processNo.value = numericRouteId(paramProcessNo)
  processName.value = paramProcessName
  plannedQty.value = parseInt(paramPlannedQty) || 0
  completedQty.value = parseInt(paramCompletedQty) || 0
  defectQtyDisplay.value = parseInt(paramDefectQty) || 0

  form.workOrderId = numericRouteId(paramOrderId)
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
    selectedReporterId.value = initialReporterId || user.id
    selectedReporterName.value = user.name || user.username || '当前用户'
  }

  await loadReportContext()
})

async function loadReportContext() {
  if (!pageAlive) return
  const sequence = ++contextSequence
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && sequence === contextSequence && session === mobileSessionKey()
  const user = uni.getStorageSync('user')
  reporterLoading.value = true
  loadError.value = ''
  if (ensureLogin() && Number.isSafeInteger(form.workOrderId) && form.workOrderId > 0 && Number.isSafeInteger(form.processSequenceNo) && form.processSequenceNo > 0) {
    try {
      const [order, users] = await Promise.all([
        fetchWorkOrder(form.workOrderId),
        fetchWorkOrderReporters(form.workOrderId, form.processSequenceNo)
      ])
      if (!current()) return
      if (!order || !Array.isArray(order.tasks) || !Array.isArray(users)) throw new Error('报工信息返回格式异常，请重试')
      if (!['NOT_STARTED', 'EXECUTING', 'IN_PROGRESS'].includes(order.status)) throw new Error('已暂停、结束或取消的工单不可报工')
      const task = order.tasks.find((item, index) => (item.sequenceNo || index + 1) === form.processSequenceNo)
      if (!task || task.enabled === false) throw new Error('工序不存在或已停用')
      if (isCorrection.value && ![originalProcessName, paramProcessName].includes(task.processName)) {
        throw new Error('原报工工序已变更，请回到工单选择当前工序。')
      }
      if (!users.some(item => item.id === user?.id)) throw new Error('当前账号没有该工序的报工权限')
      reportContextSession = session
      form.processName = task.processName
      processName.value = task.processName
      productUnit.value = order.unitName || ''
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
          const previewPath = await downloadReportImage(image.id)
          if (!current()) return
          image.previewPath = previewPath
        } catch (e: any) {
          if (!current()) return
          // A temporary download failure must not silently remove a saved
          // attachment. Keep the draft and verified reporter; retry explicitly.
          loadError.value = e.message || '图片暂时无法加载，请重试'
          return
        }
      }
    } catch (e: any) {
      if (!current()) return
      loadError.value = e.message || '报工信息加载失败，请返回重试'
      reporterList.value = []
      selectedReporterId.value = 0
      selectedReporterName.value = '不可报工'
      uni.showToast({ title: loadError.value, icon: 'none' })
    } finally {
      if (sequence === contextSequence) {
        if (session !== mobileSessionKey()) { reporterList.value = []; selectedReporterId.value = 0; loadError.value = '登录状态已变更，请重试' }
        reporterLoading.value = false
      }
    }
  } else {
    loadError.value = '工单或工序编号无效，请返回重试'
    reporterLoading.value = false
  }
}

const { clearDraft: clearReportDraft, saveNow: saveReportDraftNow } = usePageDraft({
  key: () => numericRouteId(paramOrderId || String(form.workOrderId)) && numericRouteId(paramProcessNo || String(form.processSequenceNo))
    ? `gongdan:draft:report:${paramOrderId || form.workOrderId}:${paramProcessNo || form.processSequenceNo}` : '',
  source: () => ({
    form: { ...form },
    durationHours: durationHours.value,
    durationMinutes: durationMinutes.value,
    selectedReporterId: selectedReporterId.value,
    selectedReporterName: selectedReporterName.value,
    reportNeedsReview: reportNeedsReview.value,
    retryContinues: retryContinues.value,
    submissionStartedAt: submissionStartedAt.value,
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
    reportNeedsReview.value = draft.reportNeedsReview === true
    retryContinues.value = draft.retryContinues === true
    submissionStartedAt.value = typeof draft.submissionStartedAt === 'string' ? draft.submissionStartedAt : ''
    manuallyConfirmed.value = false
    submitPhase.value = reportNeedsReview.value ? 'uncertain' : 'idle'
    submitError.value = ''
    uploadedImages.value = Array.isArray(draft.uploadedImages)
      ? draft.uploadedImages.filter(image => Number.isInteger(image.id) && image.id > 0).slice(0, 9).map(({ previewPath, ...image }: ReportImage) => image)
      : []
  }
})

const selectReporter = (user: any) => {
  if (formLocked.value || reporterLoading.value || loadError.value || !hasCurrentReportContext()) return
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
  if (!pageAlive || formLocked.value || uploading.value || reporterLoading.value || loadError.value || uploadedImages.value.length >= 9 || !ensureLogin()) return
  if (!hasCurrentReportContext()) return
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture(true)
  const sequence = ++uploadSequence
  const current = () => pageAlive && isPageCurrent() && sequence === uploadSequence && session === mobileSessionKey()
  uploading.value = true
  uni.chooseImage({
    count: 9 - uploadedImages.value.length,
    success: async (res) => {
      try {
        for (const path of res.tempFilePaths.slice(0, 9 - uploadedImages.value.length)) {
          if (!current()) return
          try {
            const img = await uploadReportImage(path)
            if (!current()) return
            img.previewPath = path
            uploadedImages.value.push(img)
          } catch (e: any) {
            if (!current()) return
            uni.showToast({ title: e.message || '图片上传失败', icon: 'none' })
          }
        }
      } finally {
        if (sequence === uploadSequence) uploading.value = false
      }
    },
    fail: () => { if (sequence === uploadSequence) uploading.value = false }
  })
}

const removeImage = (idx: number) => {
  if (!pageAlive || formLocked.value || uploading.value || !hasCurrentReportContext()) return
  uploadedImages.value.splice(idx, 1)
}

function syncGoodQuantity() {
  const reportQuantity = Math.max(0, Number(form.reportQuantity) || 0)
  const defectQuantity = Math.max(0, Number(form.defectQuantity) || 0)
  form.goodQuantity = String(Math.max(0, reportQuantity - defectQuantity))
}

function goBack() {
  if (typeof window !== 'undefined' && window.location) {
    if (isCorrection.value) {
      navigatePage('/pages/report-list/index')
      return
    }
    navigatePage(`/pages/work-order-detail/index?id=${form.workOrderId}`, true)
    return
  }
  uni.navigateBack()
}

function goToReportRecords(confirmed: boolean) {
  if (!pageAlive || !ensureLogin() || (confirmed && !manuallyConfirmed.value && reportContextSession !== mobileSessionKey())) return
  if (confirmed ? submitPhase.value !== 'success' : submitPhase.value !== 'uncertain') return
  if (confirmed && !manuallyConfirmed.value) showPendingReportsOnReturn()
  else showReportsForReview({ workOrderId: form.workOrderId, processName: form.processName, quantity: Number(form.reportQuantity) || 0, startedAt: submissionStartedAt.value })
  navigatePage('/pages/report-list/index')
}

function confirmNotSubmitted() {
  if (loading.value || reporterLoading.value || loadError.value || submitPhase.value !== 'uncertain' || !hasCurrentReportContext()) return
  reportNeedsReview.value = false
  submitError.value = ''
  submitPhase.value = 'failed'
  saveReportDraftNow()
  void focusSubmitFeedback()
}

function confirmExistingReport() {
  if (loading.value || submitPhase.value !== 'uncertain' || !ensureLogin()) return
  reportNeedsReview.value = false
  submitError.value = ''
  manuallyConfirmed.value = true
  completedSubmissionContinues.value = false
  clearReportDraft()
  submitPhase.value = 'success'
  void focusSubmitFeedback()
}

async function focusSubmitFeedback() {
  const current = pageActivity.capture()
  await nextTick()
  const heading = submitFeedbackTitle.value?.$el || submitFeedbackTitle.value
  if (current()) heading?.focus?.({ preventScroll: true })
}

function hasCurrentReportContext() {
  if (reportContextSession === mobileSessionKey()) return true
  loadError.value = '登录状态已变更，请重试报工信息'
  reporterList.value = []
  selectedReporterId.value = 0
  return false
}

const handleSubmit = async (isContinue: boolean) => {
  if (!pageAlive || loading.value || reporterLoading.value || loadError.value || reportNeedsReview.value || submitPhase.value === 'uncertain'
      || (submitPhase.value === 'success' && !completedSubmissionContinues.value)) return
  submitError.value = ''
  if (uploading.value) {
    uni.showToast({ title: '请等待图片上传完成', icon: 'none' })
    return
  }
  if (!ensureLogin()) return
  if (!hasCurrentReportContext()) return
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
    if (submitPhase.value === 'success') submitPhase.value = 'idle'
    void showSubmitError(error)
    return
  }

  const totalMinutes = Number(durationHours.value) * 60 + Number(durationMinutes.value)
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && session === mobileSessionKey()

  loading.value = true
  manuallyConfirmed.value = false
  submitPhase.value = 'submitting'
  retryContinues.value = isContinue
  submissionStartedAt.value = formatDateTime(new Date())
  // A lost response can follow a successful write. Block re-submission across a
  // route change as well as on this page until the user has checked the records.
  reportNeedsReview.value = true
  saveReportDraftNow()
  try {
    const result = await createReport({
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
    if (!current()) return
    if (!Number.isSafeInteger(result?.id) || result.id <= 0) throw new Error('报工响应未返回有效记录')
    reportNeedsReview.value = false
    completedSubmissionContinues.value = isContinue
    clearReportDraft()
    submitPhase.value = 'success'
    void focusSubmitFeedback()
    if (isContinue) {
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
      // A committed report can change totals, permissions or the order state.
      // Keep submit locked until the real context is re-read, never guess totals.
      await loadReportContext()
    }
  } catch (e: any) {
    if (!current()) return
    const status = Number(e?.status)
    // Explicit 4xx rejections (apart from timeout) did not accept the write.
    // A timeout, lost connection, malformed 2xx or a gateway/server error
    // cannot tell the client whether a report was committed.
    if (status >= 400 && status < 500 && status !== 408) {
      reportNeedsReview.value = false
      submitPhase.value = 'failed'
      submitError.value = e?.message || '服务器未接受本次报工'
      saveReportDraftNow()
    } else {
      submitPhase.value = 'uncertain'
      submitError.value = e?.message || '未能收到报工确认'
    }
    void focusSubmitFeedback()
  } finally {
    if (pageAlive) {
      if (session !== mobileSessionKey()) loadError.value = '登录状态已变更，请重试'
      else if (!isPageCurrent()) loadError.value = '页面已切换，请刷新报工信息并确认已提交记录'
      if (!current() && submitPhase.value === 'submitting') submitPhase.value = 'uncertain'
      loading.value = submitPhase.value === 'success' && !isContinue && current()
    }
  }
}

const handleClose = () => {
  if (submitPhase.value === 'success' && !completedSubmissionContinues.value) { goBack(); return }
  if (loading.value || uploading.value) return
  goBack()
}
</script>

<style scoped>
.form-message { margin: 20rpx; padding: 20rpx; background: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #007c63); font-size: 26rpx; }
.form-error { background: var(--mobile-error-bg, #fff1f0); color: var(--mobile-error, #a0332c); }
.container { background: var(--mobile-bg, #f6f7f7); min-height: 100vh; padding-bottom: 66px; padding-bottom: calc(66px + env(safe-area-inset-bottom)); }
.report-has-feedback { padding-bottom: 152px; padding-bottom: calc(152px + env(safe-area-inset-bottom)); }
.report-awaiting-review { padding-bottom: 205px; padding-bottom: calc(205px + env(safe-area-inset-bottom)); }

.summary-card {
  background: var(--mobile-surface, white);
  padding: 30rpx;
  border-bottom: 1px solid var(--mobile-border, #f0f0f0);
}

.step-header {
  display: flex;
  align-items: center;
  margin-bottom: 30rpx;
}

.step-num {
  width: 36rpx;
  height: 36rpx;
  background: #00856b;
  color: white;
  border-radius: 50%;
  font-size: 20rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 15rpx;
}

.step-name { font-size: 32rpx; font-weight: bold; flex: 1; min-width: 0; overflow-wrap: anywhere; line-height: 1.4; }

.close-icon {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--mobile-muted, #64756d);
  font-size: 28rpx;
}

.data-grid {
  display: flex;
  justify-content: space-between;
}

.data-item { display: flex; flex-direction: column; }
.dl { font-size: 22rpx; color: var(--mobile-muted, #64756d); margin-bottom: 8rpx; }
.dv { font-size: 28rpx; font-weight: bold; }
.dv.green { color: var(--mobile-link, #00856b); }
.dv.red { color: var(--mobile-error, #b53b3b); }

.form-list {
  background: var(--mobile-surface, white);
  margin-top: 20rpx;
}

.form-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  border-bottom: 1px solid var(--mobile-border, #f8f8f8);
}
.reporter-select { width: 100%; text-align: left; background: var(--mobile-surface, #fff); border-radius: 0; }
.field-reveal-enter-active, .field-reveal-leave-active { transition: opacity var(--motion-change, 180ms) ease, transform var(--motion-change, 180ms) ease; }
.field-reveal-enter-from, .field-reveal-leave-to { opacity: 0; transform: translateY(-8rpx); }

.form-item-textarea {
  padding: 30rpx;
  border-bottom: 1px solid var(--mobile-border, #f8f8f8);
}

.label { font-size: 28rpx; color: var(--mobile-text, #333); }
.required { color: var(--mobile-error, #b53b3b); margin-right: 4rpx; }

.value-row { display: flex; align-items: center; justify-content: flex-end; flex: 1; min-width: 0; flex-wrap: wrap; row-gap: 12rpx; }
.form-item > .label { flex: 0 0 auto; min-width: 110rpx; }
.step-num { flex-shrink: 0; }
.placeholder { color: var(--mobile-muted, #64756d); font-size: 28rpx; }
.num-input {
  text-align: right;
  width: 180rpx;
  height: 58rpx;
  padding: 0 16rpx;
  font-size: 28rpx;
  background: var(--mobile-bg, #f7f9f8);
  border: 1rpx solid var(--mobile-border, #dfe5e3);
  border-radius: 8rpx;
  box-sizing: border-box;
}
.red-text { color: var(--mobile-error, #b53b3b); }
.text-input { width: 260rpx; height: 58rpx; text-align: right; font-size: 26rpx; color: var(--mobile-text, #202926); }
.unit { font-size: 26rpx; color: var(--mobile-muted, #666); margin-left: 10rpx; min-width: 0; max-width: 176rpx; overflow-wrap: anywhere; text-align: right; }
.arrow { font-size: 32rpx; color: var(--mobile-muted, #64756d); margin-left: 10rpx; }

.mini-input { width: 88rpx; min-width: 44px; height: 88rpx; text-align: center; font-size: 28rpx; background: var(--mobile-bg, #f7f9f8); border: 1rpx solid var(--mobile-border, #dfe5e3); border-radius: 8rpx; }
.unit-s { font-size: 24rpx; color: var(--mobile-muted, #64756d); margin: 0 10rpx; }

.textarea-input {
  width: 100%;
  min-height: 100rpx;
  font-size: 28rpx;
  color: var(--mobile-text, #333);
  margin-top: 16rpx;
  padding: 16rpx;
  box-sizing: border-box;
  background: var(--mobile-bg, #f9f9f9);
  border-radius: 8rpx;
}

.time-range-box {
  display: flex;
  padding: 30rpx;
  align-items: center;
  border-bottom: 1px solid var(--mobile-border, #f8f8f8);
}

.time-item { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.datetime-input { display: block; width: 100%; min-width: 0; height: 72rpx; font-size: 23rpx; color: var(--mobile-text, #202926); }
.tl { font-size: 22rpx; color: var(--mobile-muted, #64756d); margin-bottom: 8rpx; }
.tv { font-size: 28rpx; color: var(--mobile-text, #333); }
.time-divider {
  width: 40rpx;
  height: 60rpx;
  border-left: 1px solid var(--mobile-border, #eee);
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
  width: 88rpx;
  height: 88rpx;
  background: transparent;
  color: white;
  border-radius: 16rpx;
  font-size: 18rpx;
  display: flex;
  justify-content: center;
  align-items: center;
}

.add-box {
  width: 120rpx;
  height: 120rpx;
  background: var(--mobile-bg, #f9f9f9);
  border: 1px dashed var(--mobile-border, #ddd);
  border-radius: 8rpx;
  display: flex;
  justify-content: center;
  align-items: center;
}
.img-delete :deep(.uni-icons) { background: rgba(255,255,255,0.94); border-radius: 50%; padding: 4rpx; }
.time-item picker { min-height: 88rpx; display: flex; align-items: center; }


.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  background: var(--mobile-surface, white);
  padding: 16rpx 24rpx 18rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
  border-top: 1px solid var(--mobile-border, #dce6e1);
  box-shadow: 0 -4rpx 20rpx rgba(0,0,0,0.05);
}

.footer-actions { display: flex; gap: 16rpx; }
.report-review-lookup { margin-top: 12rpx; }
.btn {
  flex: 1 1 0;
  min-height: 44px;
  padding: 8rpx 12rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: clamp(14px, 3.8vw, 16px);
  font-weight: bold;
  line-height: 1.3;
  white-space: normal;
}

.btn.outline { border: 1px solid var(--mobile-link, #007a60); background: var(--mobile-surface, #fff); color: var(--mobile-link, #007a60); }
.btn.solid { background: #007a60; color: white; }
.btn[disabled] { opacity: 0.6; }

.report-submit-feedback {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-bottom: 14rpx;
  padding: 14rpx 18rpx;
  background: var(--mobile-info-bg, #ebf4fc);
  color: var(--mobile-info, #214d75);
  border-radius: 12rpx;
  animation: report-feedback-in var(--motion-change, 180ms) var(--motion-ease, ease-out) both;
}
.report-feedback-success { background: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #007a60); }
.report-feedback-failed { background: var(--mobile-error-bg, #fff1f0); color: var(--mobile-error, #a0332c); }
.report-feedback-uncertain { background: var(--mobile-warning-bg, #fff4dc); color: var(--mobile-warning, #8b5908); }
.report-feedback-copy { min-width: 0; max-height: min(24vh, 92px); overflow-y: auto; line-height: 1.4; }
.report-feedback-title { display: block; width: fit-content; font-size: 15px; font-weight: 700; }
.report-feedback-detail { display: block; font-size: 13px; overflow-wrap: anywhere; }
.report-feedback-error { font-weight: 600; }
@keyframes report-feedback-in {
  from { opacity: 0.5; transform: translateY(10rpx); }
  to { opacity: 1; transform: translateY(0); }
}

/* 人员选择弹窗 */
.reporter-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--mobile-overlay, rgba(16, 30, 24, .46));
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  overscroll-behavior: contain;
}

.reporter-sheet {
  background: var(--mobile-surface, white);
  width: 100%;
  max-width: 600px;
  max-height: 60vh;
  border-radius: 28rpx 28rpx 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -12rpx 48rpx rgba(0, 0, 0, .12);
  overflow: hidden;
}

/* #ifdef H5 */
.report-sheet-enter-active { transition: opacity var(--motion-base, 220ms) ease; }
.report-sheet-leave-active { transition: opacity var(--motion-fast, 140ms) ease; }
.report-sheet-leave-active { pointer-events: none; }
.report-sheet-enter-active .reporter-sheet { transition: transform var(--motion-base, 220ms) var(--motion-ease, ease-out); }
.report-sheet-leave-active .reporter-sheet { transition: transform var(--motion-fast, 140ms) ease-in; }
.report-sheet-enter-from, .report-sheet-leave-to { opacity: 0; }
.report-sheet-enter-from .reporter-sheet, .report-sheet-leave-to .reporter-sheet { transform: translateY(100%); }
/* #endif */
/* #ifndef H5 */
.reporter-backdrop { animation: mobile-mask-in var(--motion-base, 220ms) ease both; }
.reporter-sheet { animation: reporter-slide-in var(--motion-base, 220ms) var(--motion-ease, ease-out) both; }
@keyframes reporter-slide-in { from { transform: translateY(100%); } to { transform: translateY(0); } }
/* #endif */
@media (prefers-reduced-motion: reduce) {
  .reporter-sheet, .reporter-backdrop, .report-submit-feedback { animation: none !important; transition: none !important; }
  .report-sheet-enter-from .reporter-sheet, .report-sheet-leave-to .reporter-sheet { transform: none; }
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 26rpx;
  border-bottom: 1px solid var(--mobile-border, #f0f0f0);
}

.picker-title { font-size: 32rpx; font-weight: bold; color: var(--mobile-text, #202926); }

.picker-list {
  max-height: calc(60vh - 128rpx);
  padding: 8rpx 18rpx;
}

.picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  width: 100%;
  min-height: 56px;
  padding: 8rpx 16rpx;
  border-bottom: 1px solid var(--mobile-border, #edf2ef);
  border-radius: 12rpx;
  background: transparent;
  color: var(--mobile-text, #202926);
  text-align: left;
  line-height: 1.35;
}

.picker-item.selected {
  color: var(--mobile-link, #007a60);
  background: var(--mobile-subtle, #e6f7f3);
}

.picker-person { min-width: 0; }
.picker-name, .picker-sub { display: block; overflow-wrap: anywhere; word-break: break-word; }
.picker-name { font-size: 28rpx; font-weight: 600; }
.picker-sub {
  font-size: 24rpx;
  color: var(--mobile-muted, #64756d);
}

.picker-check {
  flex-shrink: 0;
}

.picker-empty {
  text-align: center;
  padding: 60rpx 0;
  color: var(--mobile-muted, #64756d);
  font-size: 28rpx;
}
</style>
