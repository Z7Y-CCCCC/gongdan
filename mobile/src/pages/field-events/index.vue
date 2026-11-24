<template>
  <view class="container">
    <view class="page-head ui-page-head">
      <IconButton icon="back" label="返回工作台" @click="navigatePage('/pages/home/index')" />
      <text class="title">现场事件</text>
      <IconButton icon="refreshempty" label="刷新现场事件" :busy="loading" @click="loadEvents()" />
    </view>

    <view class="form-card">
      <view class="type-grid">
        <button
          v-for="item in eventTypes"
          :key="item"
          class="type-pill"
          :class="{ active: form.eventType === item }"
          :aria-pressed="form.eventType === item" :disabled="saving"
          @click="!saving && (form.eventType = item)"
        >
          {{ item }}
        </button>
      </view>

      <view class="field">
        <text class="label">事件标题</text>
        <input v-model="form.title" :disabled="saving" placeholder="例如：A 线贴标机停机" />
      </view>
      <view class="field">
        <text class="label">工单编号</text>
        <input v-model="form.workOrderCode" :disabled="saving" placeholder="可选" />
      </view>
      <view class="field">
        <text class="label">工位/设备</text>
        <input v-model="form.station" :disabled="saving" placeholder="例如：包装一工位" />
      </view>
      <view class="field">
        <text class="label">详细描述</text>
        <textarea v-model="form.description" :disabled="saving" placeholder="描述现场情况、影响数量、需要谁处理" />
      </view>

      <button class="submit-btn" :loading="saving" :aria-busy="saving" :disabled="saving" @click="submitEvent">上报</button>
    </view>

    <view class="recent-head">
      <text>最近上报</text>
    </view>

    <view class="event-list">
      <view v-for="event in events" :key="event.id" class="event-card">
        <view class="event-top">
          <text class="event-title">{{ event.title }}</text>
          <text class="status" :class="event.status.toLowerCase()">{{ statusLabel(event.status) }}</text>
        </view>
        <text v-if="payloadOf(event).description" class="event-desc">{{ payloadOf(event).description }}</text>
        <view class="event-meta">
          <text v-if="payloadOf(event).eventType">{{ payloadOf(event).eventType }}</text>
          <text v-if="payloadOf(event).workOrderCode">{{ payloadOf(event).workOrderCode }}</text>
          <text v-if="payloadOf(event).station">{{ payloadOf(event).station }}</text>
          <text v-if="event.createdAt">{{ event.createdAt.replace('T', ' ').slice(0, 16) }}</text>
        </view>
      </view>
      <view v-if="errorMessage" class="empty">
        <text>{{ errorMessage }}</text>
        <IconButton icon="refreshempty" label="重试现场事件" @click="loadEvents()" />
      </view>
      <view v-else-if="events.length === 0 && !loading" class="empty">暂无现场事件</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import { createFieldEvent, ensureLogin, fetchFieldEvents } from '../../services/api'
import type { FieldEventRecord } from '../../types/api'
import { usePageDraft } from '../../composables/usePageDraft'
import IconButton from '../../components/IconButton.vue'
import { navigatePage } from '../../utils/navigation'
import { mobileSessionKey } from '../../utils/session-key'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'

const pageActivity = useMobilePageActivity()

const eventTypes = ['异常', '停机', '质量', '物料', '换线']
const events = ref<FieldEventRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
let loadSequence = 0
let pageAlive = true
let activeSession = ''
const form = reactive({
  eventType: '异常',
  title: '',
  workOrderCode: '',
  station: '',
  description: ''
})

const { clearDraft: clearEventDraft } = usePageDraft({
  key: 'gongdan:draft:field-event',
  source: () => ({ ...form }),
  restore: (draft) => Object.assign(form, draft)
})

onMounted(() => loadEvents())
onShow(() => loadEvents())
onHide(() => { loadSequence++; loading.value = false })
onBeforeUnmount(() => { pageAlive = false; loadSequence += 1 })

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
  if (!pageAlive || saving.value || !ensureLogin()) return
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写事件标题', icon: 'none' })
    return
  }
  saving.value = true
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && session === mobileSessionKey()
  try {
    await createFieldEvent({
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
    uni.showToast({ title: '已上报', icon: 'success' })
    clearEventDraft()
    form.title = ''
    form.workOrderCode = ''
    form.station = ''
    form.description = ''
    // A GET started before the POST must not hide the newly submitted event.
    await loadEvents(true)
  } catch (error) {
    if (!current()) return
    uni.showToast({ title: error instanceof Error ? error.message : '提交失败', icon: 'none' })
  } finally {
    if (pageAlive) saving.value = false
  }
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
</script>

<style scoped>
.container {
  min-height: 100vh;
  background: #f3f5f5;
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
  color: #1f2d2a;
}

.subtitle {
  font-size: 24rpx;
  color: #64756d;
}

.form-card,
.event-card {
  background: #fff;
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
  background: #f1f3f3;
  color: #586360;
  font-size: 24rpx;
  border: 2rpx solid transparent;
}

.type-pill.active {
  background: #00856b;
  color: #fff;
  font-weight: 700;
  border-color: #006b51;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 22rpx;
}

.label {
  font-size: 24rpx;
  color: #586360;
}

input,
textarea {
  background: #f6f7f7;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #1f2d2a;
  border: 1rpx solid #dfe5e3;
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

.recent-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2d2a;
  margin: 34rpx 0 18rpx;
}

.refresh {
  color: #00856b;
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
  align-items: center;
  gap: 16rpx;
}

.event-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2d2a;
}

.status {
  flex-shrink: 0;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  background: #fff4e6;
  color: #b45309;
}

.status.closed {
  background: #e8f7f3;
  color: #00a77c;
}

.event-desc {
  display: block;
  margin-top: 14rpx;
  color: #586360;
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
  background: #f1f3f3;
  color: #64756d;
  border-radius: 8rpx;
  padding: 6rpx 12rpx;
  font-size: 22rpx;
}

.empty {
  text-align: center;
  color: #9aa4a1;
  padding: 40rpx 0;
  font-size: 26rpx;
}

.retry-btn {
  margin-top: 20rpx;
  color: #00856b;
  font-size: 26rpx;
}
</style>
