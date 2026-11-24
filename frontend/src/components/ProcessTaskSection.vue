<script setup lang="ts">
import UiIcon from './UiIcon.vue'
/**
 * ProcessTaskSection - 工序任务卡片区
 *
 * 显示工序任务卡片，支持时间范围切换、包含未开始任务复选框、刷新按钮、设置弹窗。
 * 通过 provide/inject 将 range 共享给 PerformanceSection。
 *
 * Requirements: 3.1-3.7, 5.1-5.4, 5.8, 5.9
 */
import { computed, ref, provide, watch, onBeforeUnmount, onMounted, inject, type Ref } from 'vue'
import type { HomeRange, ProcessCardItem, ProcessInfo } from '../api'
import { fetchHomeProcessCards, fetchProcesses } from '../api'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import ProcessTaskCard from './ProcessTaskCard.vue'
import ProcessCardSettingsModal, { type ProcessCardSetting } from './ProcessCardSettingsModal.vue'
import { useProcessCardSettings, type ProcessCardSettingItem } from '../composables/useProcessCardSettings'
import { fetchAllPages } from '../utils/pagination'

const props = defineProps<{
  token: string
}>()

// --- Local state ---
const range = ref<HomeRange>('today')
const includeNotStarted = ref(false)
const cards = ref<ProcessCardItem[]>([])
const dataLoaded = ref(false)
const loading = ref(false)
const beginLoad = useLatestRequest()
const errorMessage = ref('')
const showSettings = ref(false)

const processes = ref<ProcessInfo[]>([])
const cardSettings = ref<ProcessCardSettingItem[]>([])
const settingsLoaded = ref(false)
const settingsLoading = ref(false)
const savingSettings = ref(false)
const settingsError = ref('')
const beginSettingsLoad = useLatestRequest()
let saveGeneration = 0

const modalSettings = computed<ProcessCardSetting[]>(() => {
  const available = new Map(processes.value.map(process => [process.id, process]))
  const configured = cardSettings.value.filter(item => available.has(item.processId))
  const configuredIds = new Set(configured.map(item => item.processId))
  return [
    ...configured.map(item => ({ ...item, processName: available.get(item.processId)!.name })),
    ...processes.value.filter(process => !configuredIds.has(process.id)).map((process, index) => ({
      processId: process.id, processName: process.name, color: getCardColor(index), visible: false
    }))
  ]
})

const visibleCards = computed(() => {
  if (!dataLoaded.value) return []
  if (!settingsLoaded.value) return cards.value.map((card, index) => ({ ...card, color: getCardColor(index) }))
  const byId = new Map(cards.value.map(card => [card.processId, card]))
  const available = new Map(processes.value.map(process => [process.id, process]))
  return cardSettings.value.filter(item => item.visible && available.has(item.processId)).map(item => ({
    ...(byId.get(item.processId) || { processId: item.processId, processName: available.get(item.processId)!.name,
      taskCount: 0, plannedQuantity: 0, goodQuantity: 0, defectQuantity: 0 }), color: item.color
  }))
})

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
  const isCurrent = beginLoad()
  const requestToken = props.token
  if (!requestToken) { cards.value = []; loading.value = false; return }
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await fetchHomeProcessCards(requestToken, range.value, includeNotStarted.value)
    if (!isCurrent() || props.token !== requestToken) return
    cards.value = response.items
    dataLoaded.value = true
    hasLoadedOnce = true
  } catch (error) {
    if (!isCurrent()) return
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
    if (isCurrent()) loading.value = false
  }
}

async function loadSettings() {
  const isCurrent = beginSettingsLoad()
  const requestToken = props.token
  if (!requestToken) { settingsLoading.value = false; return }
  settingsLoading.value = true
  settingsError.value = ''
  const current = () => isCurrent() && props.token === requestToken
  try {
    const result = await fetchAllPages(params => fetchProcesses(requestToken, params), 500, current)
    const preference = useProcessCardSettings()
    await preference.loadSettings(requestToken, result.items)
    if (!current()) return
    processes.value = result.items
    const available = new Set(result.items.map(process => process.id))
    const used = new Set<number>()
    cardSettings.value = preference.settings.value.items.filter(item => {
      if (!available.has(item.processId) || used.has(item.processId)) return false
      used.add(item.processId)
      return true
    }).map((item, index) => ({ ...item, visible: item.visible === true,
      color: /^#[0-9a-f]{6}$/i.test(item.color) ? item.color : getCardColor(index) }))
    settingsLoaded.value = true
  } catch (error) {
    if (current() && !handleApiError(error)) settingsError.value = '卡片设置加载失败，请重试'
  } finally {
    if (current()) settingsLoading.value = false
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
  void loadCards()
  void loadSettings()
}

function handleRangeChange(newRange: HomeRange) {
  range.value = newRange
}

async function openSettings() {
  if (savingSettings.value || settingsLoading.value) return
  if (!settingsLoaded.value) await loadSettings()
  if (!settingsLoaded.value) return
  settingsError.value = ''
  showSettings.value = true
}

async function onSettingsSaved(newSettings: Array<{ processId: number; color: string; visible: boolean }>) {
  if (savingSettings.value || !settingsLoaded.value || !props.token) return
  const requestToken = props.token
  const requestGeneration = ++saveGeneration
  beginSettingsLoad()
  settingsLoading.value = false
  savingSettings.value = true
  settingsError.value = ''
  const value = newSettings.map(item => ({ ...item }))
  try {
    const preference = useProcessCardSettings()
    await preference.saveSettings(requestToken, { version: 1, items: value })
    if (props.token !== requestToken || saveGeneration !== requestGeneration) return
    cardSettings.value = value
    showSettings.value = false
  } catch (error) {
    if (props.token === requestToken && saveGeneration === requestGeneration && !handleApiError(error)) {
      settingsError.value = '卡片设置保存失败，请重试'
    }
  } finally {
    if (props.token === requestToken && saveGeneration === requestGeneration) savingSettings.value = false
  }
}

// --- Initial load ---
useRefreshOnActivated(() => { void loadCards(); void loadSettings() })

onMounted(() => {
  loadCards()
  loadSettings()
})

watch(() => props.token, () => {
  saveGeneration++
  beginLoad()
  beginSettingsLoad()
  cards.value = []
  dataLoaded.value = false
  processes.value = []
  cardSettings.value = []
  settingsLoaded.value = false
  errorMessage.value = ''
  settingsError.value = ''
  savingSettings.value = false
  showSettings.value = false
  void loadCards()
  void loadSettings()
})
onBeforeUnmount(() => { saveGeneration++; beginLoad(); beginSettingsLoad() })

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
        <button class="process-task-settings-btn ui-icon-button" title="设置" @click="openSettings" aria-label="设置" type="button" :disabled="settingsLoading || savingSettings"><UiIcon name="settings" /></button>
        <label class="process-task-checkbox-label">
          <input
            type="checkbox"
            v-model="includeNotStarted"
            class="process-task-checkbox" aria-label="包含未开始生产任务" title="包含未开始生产任务"
          />
          <span>含未开始</span>
        </label>
      </div>
      <div class="process-task-header-right">
        <button class="process-task-refresh-btn ui-icon-button" title="刷新" @click="handleRefresh" aria-label="刷新" type="button"><UiIcon name="refresh" /></button>
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
    <div v-if="errorMessage || settingsError && !showSettings" class="process-task-error" role="alert">
      {{ errorMessage || settingsError }}
    </div>

    <!-- Cards container with horizontal scroll -->
    <div class="process-task-cards-container">
      <div v-if="loading && visibleCards.length === 0" class="process-task-loading">
        加载中...
      </div>
      <div v-else-if="visibleCards.length === 0 && !errorMessage" class="process-task-empty">
        {{ settingsLoaded ? '未选择工序' : '暂无数据' }}
      </div>
      <div v-else class="process-task-cards">
        <ProcessTaskCard
          v-for="card in visibleCards"
          :key="card.processId"
          :processName="card.processName"
          :color="card.color"
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
      :saving="savingSettings"
      :error="settingsError"
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
  transition: border-color var(--motion-base);
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
  transition: border-color var(--motion-base);
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
  transition: background-color var(--motion-base), color 0.2s;
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
