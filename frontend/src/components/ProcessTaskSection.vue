<script setup lang="ts">
/**
 * ProcessTaskSection - 工序任务卡片区
 *
 * 显示工序任务卡片，支持时间范围切换、包含未开始任务复选框、刷新按钮、设置弹窗。
 * 通过 provide/inject 将 range 共享给 PerformanceSection。
 *
 * Requirements: 3.1-3.7, 5.1-5.4, 5.8, 5.9
 */
import { ref, provide, watch, onMounted, inject, type Ref } from 'vue'
import type { HomeRange, ProcessCardItem } from '../api'
import { fetchHomeProcessCards } from '../api'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import ProcessTaskCard from './ProcessTaskCard.vue'
import ProcessCardSettingsModal, { type ProcessCardSetting } from './ProcessCardSettingsModal.vue'

const props = defineProps<{
  token: string
}>()

// --- Local state ---
const range = ref<HomeRange>('today')
const includeNotStarted = ref(false)
const cards = ref<ProcessCardItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const showSettings = ref(false)

// Settings for the modal (derived from cards data with default colors)
const modalSettings = ref<ProcessCardSetting[]>([])

// Share range with PerformanceSection via provide/inject
// The parent HomeDashboardPage also provides 'homeRange', we inject it and keep it in sync
const parentRange = inject<Ref<HomeRange>>('homeRange', ref('today'))

// Provide range to sibling/child components
provide('homeRange', range)

// Keep parent range in sync when local range changes
watch(range, (newRange) => {
  parentRange.value = newRange
})

// --- Error handling ---
const { handleApiError } = useHomeDashboardErrorHandler()

// --- Data fetching ---
let hasLoadedOnce = false

async function loadCards() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await fetchHomeProcessCards(props.token, range.value, includeNotStarted.value)
    cards.value = response.items
    hasLoadedOnce = true
    // Update modal settings from loaded cards
    modalSettings.value = response.items.map((card, index) => ({
      processId: card.processId,
      processName: card.processName,
      color: getCardColor(index),
      visible: true
    }))
  } catch (error) {
    // Check if it's a 401/403 handled globally
    if (handleApiError(error)) {
      return
    }
    // Non-401/403 error: preserve previous data if available
    if (hasLoadedOnce) {
      // Keep existing cards, show error message
      errorMessage.value = '加载失败，请重试'
    } else {
      // First load failure: just show error message
      errorMessage.value = '加载失败，请重试'
    }
  } finally {
    loading.value = false
  }
}

// --- Watchers for re-fetching ---
watch(range, () => {
  loadCards()
})

watch(includeNotStarted, () => {
  loadCards()
})

// --- Actions ---
function handleRefresh() {
  loadCards()
}

function handleRangeChange(newRange: HomeRange) {
  range.value = newRange
}

function openSettings() {
  showSettings.value = true
}

function onSettingsSaved(_newSettings: Array<{ processId: number; color: string; visible: boolean }>) {
  showSettings.value = false
  // Reload cards after settings change
  loadCards()
}

// --- Initial load ---
onMounted(() => {
  loadCards()
})

// --- Default color palette for cards (used until user preferences are loaded in task 7.4) ---
const defaultColors = ['#3B82F6', '#9CA3AF', '#FACC15', '#22C55E', '#EF4444', '#8B5CF6', '#F97316', '#06B6D4']

function getCardColor(index: number): string {
  return defaultColors[index % defaultColors.length]
}

// --- Range tab definitions ---
const rangeTabs: { label: string; value: HomeRange }[] = [
  { label: '今天', value: 'today' },
  { label: '昨天', value: 'yesterday' },
  { label: '本月', value: 'this_month' }
]
</script>

<template>
  <div class="process-task-section">
    <!-- Header -->
    <div class="process-task-header">
      <div class="process-task-header-left">
        <h2 class="process-task-title">工序任务</h2>
        <button class="process-task-settings-btn" title="设置" @click="openSettings">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M6.5 1L6.2 2.6C5.8 2.8 5.4 3 5.1 3.3L3.5 2.7L2 5.3L3.4 6.4C3.4 6.6 3.3 6.8 3.3 7C3.3 7.2 3.4 7.4 3.4 7.6L2 8.7L3.5 11.3L5.1 10.7C5.4 11 5.8 11.2 6.2 11.4L6.5 13H9.5L9.8 11.4C10.2 11.2 10.6 11 10.9 10.7L12.5 11.3L14 8.7L12.6 7.6C12.6 7.4 12.7 7.2 12.7 7C12.7 6.8 12.6 6.6 12.6 6.4L14 5.3L12.5 2.7L10.9 3.3C10.6 3 10.2 2.8 9.8 2.6L9.5 1H6.5ZM8 5C9.1 5 10 5.9 10 7C10 8.1 9.1 9 8 9C6.9 9 6 8.1 6 7C6 5.9 6.9 5 8 5Z" fill="#666"/>
          </svg>
        </button>
        <label class="process-task-checkbox-label">
          <input
            type="checkbox"
            v-model="includeNotStarted"
            class="process-task-checkbox"
          />
          <span>包含未开始生产任务</span>
        </label>
      </div>
      <div class="process-task-header-right">
        <button class="process-task-refresh-btn" title="刷新" @click="handleRefresh">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M13.65 2.35A8 8 0 1 0 16 8h-2a6 6 0 1 1-1.76-4.24L10 6h6V0l-2.35 2.35z" fill="#666" transform="scale(0.85) translate(1,1)"/>
          </svg>
        </button>
        <div class="process-task-range-tabs">
          <button
            v-for="tab in rangeTabs"
            :key="tab.value"
            :class="['range-tab', { 'range-tab--active': range === tab.value }]"
            @click="handleRangeChange(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- Error message -->
    <div v-if="errorMessage" class="process-task-error">
      {{ errorMessage }}
    </div>

    <!-- Cards container with horizontal scroll -->
    <div class="process-task-cards-container">
      <div v-if="loading && cards.length === 0" class="process-task-loading">
        加载中...
      </div>
      <div v-else-if="cards.length === 0 && !errorMessage" class="process-task-empty">
        暂无数据
      </div>
      <div v-else class="process-task-cards">
        <ProcessTaskCard
          v-for="(card, index) in cards"
          :key="card.processId"
          :processName="card.processName"
          :color="getCardColor(index)"
          :taskCount="card.taskCount"
          :plannedQuantity="card.plannedQuantity"
          :goodQuantity="card.goodQuantity"
          :defectQuantity="card.defectQuantity"
        />
      </div>
    </div>

    <!-- Settings Modal -->
    <ProcessCardSettingsModal
      v-model:visible="showSettings"
      :settings="modalSettings"
      :token="props.token"
      @save="onSettingsSaved"
    />
  </div>
</template>

<style scoped>
.process-task-section {
  width: 100%;
}

.process-task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.process-task-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.process-task-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.process-task-settings-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s;
}

.process-task-settings-btn:hover {
  border-color: #1890ff;
}

.process-task-checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  user-select: none;
}

.process-task-checkbox {
  width: 14px;
  height: 14px;
  cursor: pointer;
}

.process-task-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.process-task-refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s;
}

.process-task-refresh-btn:hover {
  border-color: #1890ff;
}

.process-task-range-tabs {
  display: flex;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
}

.range-tab {
  padding: 4px 12px;
  font-size: 13px;
  border: none;
  background: #fff;
  color: #555;
  cursor: pointer;
  transition: background-color 0.2s, color 0.2s;
  border-right: 1px solid #d9d9d9;
}

.range-tab:last-child {
  border-right: none;
}

.range-tab--active {
  background: #1890ff;
  color: #fff;
}

.range-tab:hover:not(.range-tab--active) {
  background: #f0f0f0;
}

.process-task-error {
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  color: #ff4d4f;
  font-size: 13px;
}

.process-task-cards-container {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 8px;
}

.process-task-cards {
  display: flex;
  gap: 16px;
  min-width: min-content;
}

.process-task-loading,
.process-task-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: #999;
  font-size: 14px;
}
</style>
