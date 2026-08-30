<template>
  <view class="container">
    <view class="page-head">
      <text class="title">现场事件</text>
      <text class="subtitle">异常、停机、质量、物料、换线都可以快速上报</text>
    </view>

    <view class="form-card">
      <view class="type-grid">
        <view
          v-for="item in eventTypes"
          :key="item"
          class="type-pill"
          :class="{ active: form.eventType === item }"
          @click="form.eventType = item"
        >
          {{ item }}
        </view>
      </view>

      <view class="field">
        <text class="label">事件标题</text>
        <input v-model="form.title" placeholder="例如：A 线贴标机停机" />
      </view>
      <view class="field">
        <text class="label">工单编号</text>
        <input v-model="form.workOrderCode" placeholder="可选" />
      </view>
      <view class="field">
        <text class="label">工位/设备</text>
        <input v-model="form.station" placeholder="例如：包装一工位" />
      </view>
      <view class="field">
        <text class="label">详细描述</text>
        <textarea v-model="form.description" placeholder="描述现场情况、影响数量、需要谁处理" />
      </view>

      <button class="submit-btn" :loading="saving" @click="submitEvent">提交上报</button>
    </view>

    <view class="recent-head">
      <text>最近上报</text>
      <text class="refresh" @click="loadEvents">刷新</text>
    </view>

    <view class="event-list">
      <view v-for="event in events" :key="event.id" class="event-card">
        <view class="event-top">
          <text class="event-title">{{ event.title }}</text>
          <text class="status" :class="event.status.toLowerCase()">{{ statusLabel(event.status) }}</text>
        </view>
        <text class="event-desc">{{ payloadOf(event).description || '暂无描述' }}</text>
        <view class="event-meta">
          <text>{{ payloadOf(event).eventType || '-' }}</text>
          <text>{{ payloadOf(event).workOrderCode || '无工单' }}</text>
          <text>{{ payloadOf(event).station || '无工位' }}</text>
        </view>
      </view>
      <view v-if="events.length === 0 && !loading" class="empty">暂无现场事件</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createFieldEvent, ensureLogin, fetchFieldEvents } from '../../services/api'
import type { FieldEventRecord } from '../../types/api'
import { usePageDraft } from '../../composables/usePageDraft'

const eventTypes = ['异常', '停机', '质量', '物料', '换线']
const events = ref<FieldEventRecord[]>([])
const loading = ref(false)
const saving = ref(false)
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

onMounted(() => {
  if (!ensureLogin()) return
  loadEvents()
})

async function loadEvents() {
  loading.value = true
  try {
    const result = await fetchFieldEvents()
    events.value = result.items || []
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function submitEvent() {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写事件标题', icon: 'none' })
    return
  }
  saving.value = true
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
    uni.showToast({ title: '已上报', icon: 'success' })
    clearEventDraft()
    form.title = ''
    form.workOrderCode = ''
    form.station = ''
    form.description = ''
    await loadEvents()
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '提交失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

function payloadOf(event: FieldEventRecord): Record<string, any> {
  if (!event.payloadJson) return {}
  try {
    return JSON.parse(event.payloadJson)
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
  flex-direction: column;
  gap: 10rpx;
  margin-bottom: 24rpx;
}

.title {
  font-size: 42rpx;
  font-weight: 700;
  color: #1f2d2a;
}

.subtitle {
  font-size: 24rpx;
  color: #8a9693;
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
}

.type-pill.active {
  background: #00b889;
  color: #fff;
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
  background: #00b889;
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
  color: #00b889;
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
  color: #8a9693;
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
</style>
