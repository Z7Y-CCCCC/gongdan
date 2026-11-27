<template>
  <view class="container" role="main" aria-label="现场事件">
    <view class="page-head ui-page-head">
      <IconButton icon="back" label="返回工作台" @click="navigatePage('/pages/home/index')" />
      <text class="title" role="heading" aria-level="1">现场事件</text>
      <IconButton icon="refreshempty" label="刷新现场事件" :busy="loading" @click="loadEvents()" />
    </view>

    <view class="form-card">
      <text class="label">事件类型</text>
      <view class="type-grid" role="group" aria-label="事件类型">
        <button
          v-for="item in eventTypes"
          :key="item"
          class="type-pill"
          :class="{ active: form.eventType === item }"
          :aria-pressed="form.eventType === item" :disabled="formLocked"
          @click="!formLocked && (form.eventType = item)"
        >
          {{ item }}
        </button>
      </view>

      <view class="field">
        <text class="label">事件标题（必填）</text>
        <input v-model="form.title" maxlength="128" aria-label="事件标题" aria-required="true" :aria-invalid="!!submitError && !form.title.trim()" :disabled="formLocked" placeholder="例如：A 线贴标机停机" />
      </view>
      <view class="field">
        <text class="label">工单编号</text>
        <input v-model="form.workOrderCode" aria-label="工单编号" :disabled="formLocked" placeholder="可选" />
      </view>
      <view class="field">
        <text class="label">工位/设备</text>
        <input v-model="form.station" aria-label="工位或设备" :disabled="formLocked" placeholder="例如：包装一工位" />
      </view>
      <view class="field">
        <text class="label">详细描述</text>
        <textarea v-model="form.description" aria-label="详细描述" :disabled="formLocked" placeholder="描述现场情况、影响数量、需要谁处理" />
      </view>

      <view v-if="submitPhase !== 'idle' || submitError" :key="submitPhase" class="event-feedback" :class="'event-feedback-' + submitPhase">
        <text class="event-feedback-title" :role="submitPhase === 'failed' || submitPhase === 'idle' ? 'alert' : 'status'">{{ feedbackTitle }}</text>
        <text v-if="submitError && submitPhase !== 'idle'" class="event-feedback-detail">{{ submitError }}</text>
        <template v-if="submitPhase === 'uncertain'">
          <text class="event-feedback-detail">内容已保留。为避免重复，请先按上报编号核对记录。</text>
          <text class="event-feedback-detail">编号：{{ pendingEventReference }}</text>
          <text v-if="reviewMissed" class="event-feedback-detail">最近 50 条未找到相同记录，不代表未入库。请先确认，再解除限制。</text>
        </template>
      </view>
      <template v-if="submitPhase === 'uncertain'">
        <button class="submit-btn" :aria-busy="reviewing || waitingForPreviousWrite" :disabled="saving || reviewing || waitingForPreviousWrite" @click="reviewRecentEvents">{{ waitingForPreviousWrite ? '等待上报结果…' : reviewing ? '正在核对…' : '核对最近上报' }}</button>
        <button v-if="reviewMissed" class="review-reset-btn" :disabled="reviewing" @click="acknowledgeMissingEvent">确认未上报，解除限制</button>
      </template>
      <button v-else class="submit-btn" :aria-busy="saving" :disabled="formLocked" @click="submitEvent">{{ submitPhase === 'submitting' ? '上报中…' : submitPhase === 'failed' ? '重新上报' : '上报' }}</button>
    </view>

    <view class="recent-head">
      <text>最近上报</text>
    </view>

    <view class="event-list">
      <view v-for="event in events" :key="event.id" class="event-card">
        <view class="event-top">
          <text class="event-title">{{ event.title }}</text>
          <text class="status" :class="statusClass(event.status)">{{ statusLabel(event.status) }}</text>
        </view>
        <text v-if="payloadOf(event).description" class="event-desc">{{ payloadOf(event).description }}</text>
        <view class="event-meta">
          <text v-if="payloadOf(event).eventType">{{ payloadOf(event).eventType }}</text>
          <text v-if="payloadOf(event).workOrderCode">{{ payloadOf(event).workOrderCode }}</text>
          <text v-if="payloadOf(event).station">{{ payloadOf(event).station }}</text>
          <text v-if="event.createdAt">{{ formatEventDate(event.createdAt) }}</text>
          <text v-if="reviewedEventReference && event.businessKey === reviewedEventReference" class="event-reference">已核对本次上报</text>
        </view>
      </view>
      <view v-if="errorMessage" class="empty" role="alert">
        <text>{{ errorMessage }}</text>
        <IconButton icon="refreshempty" label="重试现场事件" @click="loadEvents()" />
      </view>
      <view v-else-if="events.length === 0 && !loading" class="empty">暂无现场事件</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import { createFieldEvent, ensureLogin, fetchFieldEvents } from '../../services/api'
import type { FieldEventRecord } from '../../types/api'
import { usePageDraft } from '../../composables/usePageDraft'
import IconButton from '../../components/IconButton.vue'
import { navigatePage } from '../../utils/navigation'
import { mobileSessionKey } from '../../utils/session-key'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'
import { fieldEventInFlight } from '../../utils/field-event-in-flight'

const pageActivity = useMobilePageActivity()

const eventTypes = ['异常', '停机', '质量', '物料', '换线']
const events = ref<FieldEventRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const submitError = ref('')
type SubmitPhase = 'idle' | 'submitting' | 'success' | 'failed' | 'uncertain' | 'resolved'
const submitPhase = ref<SubmitPhase>('idle')
const pendingEventReference = ref('')
const reviewedEventReference = ref('')
const reviewing = ref(false)
const reviewMissed = ref(false)
let checkedReviewSession = ''
const waitingForPreviousWrite = computed(() => !!pendingEventReference.value && fieldEventInFlight.has(pendingEventReference.value))
const formLocked = computed(() => saving.value || !!pendingEventReference.value)
const feedbackTitle = computed(() => ({
  idle: submitError.value,
  submitting: '正在上报现场事件…',
  success: '现场事件已上报',
  failed: '未能上报',
  uncertain: '暂未确认上报结果',
  resolved: '记录已核对，不必重复上报'
})[submitPhase.value])
let loadSequence = 0
let reviewSequence = 0
let pageAlive = true
let activeSession = ''
const form = reactive({
  eventType: '异常',
  title: '',
  workOrderCode: '',
  station: '',
  description: ''
})

watch(() => ({ ...form }), () => {
  if (!saving.value && (submitPhase.value === 'success' || submitPhase.value === 'resolved')) submitPhase.value = 'idle'
}, { flush: 'sync' })

const { clearDraft: clearEventDraft, saveNow: saveEventDraftNow } = usePageDraft({
  key: 'gongdan:draft:field-event',
  source: () => ({ ...form, pendingEventReference: pendingEventReference.value }),
  restore: draft => {
    for (const key of ['title', 'workOrderCode', 'station', 'description'] as const) {
      form[key] = typeof draft[key] === 'string' ? draft[key] : ''
    }
    form.eventType = eventTypes.includes(draft.eventType) ? draft.eventType : '异常'
    pendingEventReference.value = typeof draft.pendingEventReference === 'string' && /^MFE-[a-z0-9-]+$/.test(draft.pendingEventReference) ? draft.pendingEventReference : ''
    submitPhase.value = pendingEventReference.value ? 'uncertain' : 'idle'
    submitError.value = ''
    reviewMissed.value = false
    checkedReviewSession = ''
    reviewedEventReference.value = ''
  }
})

onMounted(() => loadEvents())
onShow(() => loadEvents())
onHide(() => { loadSequence++; loading.value = false; reviewSequence++; reviewing.value = false; reviewMissed.value = false })
onBeforeUnmount(() => { pageAlive = false; loadSequence++; reviewSequence++ })

async function loadEvents(force = false) {
  if (!pageAlive) return
  const session = mobileSessionKey()
  if (activeSession !== session) {
    events.value = []
    loading.value = false
  }
  if (loading.value && !force) return
  if (!ensureLogin()) {
    loadSequence++
    events.value = []
    errorMessage.value = ''
    loading.value = false
    return
  }
  const requestId = ++loadSequence
  activeSession = session
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && requestId === loadSequence && session === mobileSessionKey()
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchFieldEvents()
    if (!current()) return
    if (!result || !Array.isArray(result.items)) throw new Error('现场事件返回格式异常，请重试')
    events.value = result.items
    return result.items
  } catch (error) {
    if (!current()) return
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
    uni.showToast({ title: errorMessage.value, icon: 'none' })
  } finally {
    if (pageAlive && requestId === loadSequence) {
      loading.value = false
      if (session !== mobileSessionKey()) {
        events.value = []
        errorMessage.value = '登录状态已变更，请刷新'
      }
    }
  }
}

async function submitEvent() {
  if (!pageAlive || formLocked.value || !ensureLogin()) return
  submitError.value = ''
  if (!form.title.trim()) {
    submitError.value = '请填写事件标题'
    uni.showToast({ title: '请填写事件标题', icon: 'none' })
    return
  }
  saving.value = true
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && session === mobileSessionKey()
  const reference = createSubmissionReference()
  fieldEventInFlight.add(reference)
  pendingEventReference.value = reference
  reviewedEventReference.value = ''
  submitPhase.value = 'submitting'
  saveEventDraftNow()
  try {
    const result = await createFieldEvent({
      businessKey: reference,
      title: form.title.trim(),
      status: 'OPEN',
      payloadJson: JSON.stringify({
        eventType: form.eventType,
        workOrderCode: form.workOrderCode.trim(),
        station: form.station.trim(),
        description: form.description.trim()
      })
    })
    if (!current()) return
    if (!Number.isSafeInteger(result?.id) || result.id <= 0) throw new Error('未收到有效的上报回执')
    completeSubmission('success')
    // A GET started before the POST must not hide the newly submitted event.
    await loadEvents(true)
  } catch (error) {
    if (!current()) return
    const status = (error as { status?: number })?.status
    if (status !== undefined && [400, 403, 404, 422, 429].includes(status)) {
      pendingEventReference.value = ''
      submitPhase.value = 'failed'
      submitError.value = error instanceof Error ? error.message : '服务器未接受本次上报'
      saveEventDraftNow()
    } else {
      submitPhase.value = 'uncertain'
      submitError.value = error instanceof Error ? error.message : '未收到上报确认'
    }
  } finally {
    fieldEventInFlight.delete(reference)
    if (pageAlive) {
      if (!current() && pendingEventReference.value === reference && submitPhase.value === 'submitting') submitPhase.value = 'uncertain'
      saving.value = false
    }
  }
}

function createSubmissionReference() {
  const time = Date.now().toString(36)
  try {
    const random = globalThis.crypto?.getRandomValues?.(new Uint32Array(4))
    if (random) return 'MFE-' + time + '-' + Array.from(random, value => value.toString(36)).join('-')
  } catch { /* Mini Program JS engines may not expose Web Crypto. */ }
  return `MFE-${time}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}

function completeSubmission(phase: 'success' | 'resolved') {
  if (phase === 'resolved') reviewedEventReference.value = pendingEventReference.value
  pendingEventReference.value = ''
  submitError.value = ''
  reviewMissed.value = false
  form.title = ''
  form.workOrderCode = ''
  form.station = ''
  form.description = ''
  clearEventDraft()
  submitPhase.value = phase
}

async function reviewRecentEvents() {
  if (!pageAlive || !pendingEventReference.value || saving.value || reviewing.value || waitingForPreviousWrite.value || !ensureLogin()) return
  const reference = pendingEventReference.value
  const session = mobileSessionKey(), userId = uni.getStorageSync('user')?.id
  const isPageCurrent = pageActivity.capture(), sequence = ++reviewSequence
  reviewing.value = true
  reviewMissed.value = false
  try {
    const recent = await loadEvents(true)
    if (!isPageCurrent() || sequence !== reviewSequence || session !== mobileSessionKey() || reference !== pendingEventReference.value) return
    if (!recent) { submitError.value = errorMessage.value || '未能加载记录，请再核对'; return }
    submitError.value = ''
    if (recent.some(item => item.businessKey === reference && item.createdById === userId)) completeSubmission('resolved')
    else { checkedReviewSession = session; reviewMissed.value = true }
  } finally {
    if (sequence === reviewSequence) reviewing.value = false
  }
}

function acknowledgeMissingEvent() {
  if (!pageAlive || !reviewMissed.value || reviewing.value || saving.value || waitingForPreviousWrite.value || submitPhase.value !== 'uncertain'
      || checkedReviewSession !== mobileSessionKey() || !ensureLogin()) return
  pendingEventReference.value = ''
  submitError.value = ''
  reviewMissed.value = false
  checkedReviewSession = ''
  submitPhase.value = 'failed'
  saveEventDraftNow()
}

function payloadOf(event: FieldEventRecord): Record<string, any> {
  if (!event.payloadJson) return {}
  try {
    const payload = JSON.parse(event.payloadJson)
    return payload && typeof payload === 'object' && !Array.isArray(payload) ? payload : {}
  } catch {
    return {}
  }
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    OPEN: '待处理',
    PROCESSING: '处理中',
    CLOSED: '已关闭'
  }
  return map[status] || status
}
function statusClass(status: string) {
  return ['OPEN', 'PROCESSING', 'CLOSED'].includes(status) ? status.toLowerCase() : ''
}
function formatEventDate(value: unknown) {
  return typeof value === 'string' ? value.replace('T', ' ').slice(0, 16) : ''
}
</script>

<style scoped>
.container {
  min-height: 100vh;
  background: var(--mobile-bg, #f3f5f5);
  padding: 30rpx;
  box-sizing: border-box;
}

.page-head {
  display: flex;
  flex-direction: row;
  gap: 10rpx;
  margin-bottom: 24rpx;
}

.title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--mobile-text, #1f2d2a);
}

.subtitle {
  font-size: 24rpx;
  color: var(--mobile-muted, #64756d);
}

.form-card,
.event-card {
  background: var(--mobile-surface, #fff);
  border-radius: 20rpx;
  padding: 28rpx;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.type-pill {
  text-align: center;
  padding: 14rpx 0;
  border-radius: 12rpx;
  background: var(--mobile-subtle, #f1f3f3);
  color: var(--mobile-muted, #586360);
  font-size: 24rpx;
  border: 2rpx solid transparent;
}

.type-pill.active {
  background: #00856b;
  color: #fff;
  font-weight: 700;
  border-color: var(--mobile-border, #006b51);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 22rpx;
}

.label {
  font-size: 24rpx;
  color: var(--mobile-muted, #586360);
}

input,
textarea {
  background: var(--mobile-bg, #f6f7f7);
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: var(--mobile-text, #1f2d2a);
  border: 1rpx solid var(--mobile-border, #dfe5e3);
  box-sizing: border-box;
}

input {
  height: 76rpx;
}

textarea {
  min-height: 150rpx;
  padding-top: 18rpx;
  box-sizing: border-box;
}

.submit-btn {
  background: #00856b;
  color: #fff;
  border-radius: 14rpx;
  font-size: 30rpx;
  margin-top: 10rpx;
  width: 100%;
  height: 82rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
}

.submit-btn::after {
  border: none;
}

.event-feedback {
  margin-bottom: 12rpx;
  padding: 18rpx 20rpx;
  border-radius: 12rpx;
  color: var(--mobile-info, #214d75);
  background: var(--mobile-info-bg, #ebf4fc);
  overflow-wrap: anywhere;
  animation: event-feedback-in var(--motion-change, 180ms) var(--motion-ease, ease-out) both;
}
.event-feedback-title { display: block; font-weight: 700; font-size: 28rpx; }
.event-feedback-detail { display: block; margin-top: 6rpx; font-size: 24rpx; line-height: 1.5; }
.event-feedback-idle, .event-feedback-failed { color: var(--mobile-error, #a0332c); background: var(--mobile-error-bg, #fff1f0); }
.event-feedback-uncertain { color: var(--mobile-warning, #8b5908); background: var(--mobile-warning-bg, #fff4dc); }
.event-feedback-success, .event-feedback-resolved { color: var(--mobile-link, #007a60); background: var(--mobile-subtle, #e6f7f3); }
.review-reset-btn { margin-top: 12rpx; width: 100%; padding: 0 12rpx; border: 1px solid var(--mobile-border, #ceded5); border-radius: 12rpx; background: var(--mobile-surface, #fff); color: var(--mobile-text, #202926); font-size: 26rpx; }
@keyframes event-feedback-in { from { opacity: .5; transform: translateY(8rpx); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { .event-feedback { animation: none !important; } }

.recent-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--mobile-text, #1f2d2a);
  margin: 34rpx 0 18rpx;
}

.refresh {
  color: var(--mobile-link, #00856b);
  font-size: 24rpx;
  font-weight: 400;
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.event-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.event-title {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--mobile-text, #1f2d2a);
}

.status {
  flex-shrink: 0;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  background: var(--mobile-warning-bg, #fff4e6);
  color: var(--mobile-warning, #b45309);
}

.status.closed {
  background: var(--mobile-bg, #e8f7f3);
  color: var(--mobile-link, #007a60);
}

.event-desc {
  overflow-wrap: anywhere;
  display: block;
  margin-top: 14rpx;
  color: var(--mobile-muted, #586360);
  font-size: 26rpx;
  line-height: 1.5;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 18rpx;
}

.event-meta text {
  min-width: 0;
  overflow-wrap: anywhere;
  background: var(--mobile-subtle, #f1f3f3);
  color: var(--mobile-muted, #64756d);
  border-radius: 8rpx;
  padding: 6rpx 12rpx;
  font-size: 22rpx;
}

.empty {
  text-align: center;
  color: var(--mobile-muted, #64756d);
  padding: 40rpx 0;
  font-size: 26rpx;
}
.event-meta .event-reference { color: var(--mobile-link, #007a60); font-weight: 700; }

.retry-btn {
  margin-top: 20rpx;
  color: var(--mobile-link, #00856b);
  font-size: 26rpx;
}
</style>
