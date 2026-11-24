<script setup lang="ts">
/**
 * PerformanceSection - 绩效指标区
 *
 * 以 2 列 3 行网格展示 6 项绩效指标。
 * 响应注入的 homeRange，范围变化时重新请求数据。
 * "完成数量"数值绿色，"不良品率"数值红色。
 * 非 401/403 错误时所有指标显示 "--"。
 *
 * Requirements: 9.1-9.4, 9.14
 */
import { ref, inject, watch, onMounted, type Ref } from 'vue'
import type { HomeRange, PerformanceResponse } from '../api'
import { fetchHomePerformance } from '../api'
import { t } from '../i18n'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'

const props = defineProps<{
  token: string
}>()

// Inject homeRange from ProcessTaskSection (provided via provide/inject)
const homeRange = inject<Ref<HomeRange>>('homeRange', ref('today'))

// Error handling
const { handleApiError } = useHomeDashboardErrorHandler()

// Data state
const data = ref<PerformanceResponse | null>(null)
const hasError = ref(false)
const beginLoad = useLatestRequest()

// KPI tile definitions
interface KpiTile {
  label: string
  key: keyof PerformanceResponse
  suffix?: string
  colorClass?: string
}

const kpiTiles: KpiTile[] = [
  { label: '计划数', key: 'plannedQuantity' },
  { label: '完成数量', key: 'completedQuantity', colorClass: 'kpi-value--green' },
  { label: '工单数量', key: 'workOrderCount' },
  { label: '不良品率', key: 'defectRatePercent', suffix: '%', colorClass: 'kpi-value--red' },
  { label: '平均工单生产周期', key: 'averageCycleDays' },
  { label: '超期未完成工单', key: 'overdueUnfinishedCount' }
]

function formatValue(tile: KpiTile): string {
  if (hasError.value || !data.value) {
    return '--'
  }
  const raw = data.value[tile.key]
  if (tile.key === 'defectRatePercent') {
    return (raw as number).toFixed(2) + (tile.suffix || '')
  }
  if (tile.key === 'averageCycleDays') {
    return t('{0}天', [(raw as number).toFixed(1)])
  }
  return String(raw) + (tile.suffix || '')
}

async function loadPerformance() {
  const isCurrent = beginLoad()
  hasError.value = false
  try {
    const response = await fetchHomePerformance(props.token, homeRange.value)
    if (!isCurrent()) return
    data.value = response
  } catch (error) {
    if (!isCurrent()) return
    if (handleApiError(error)) {
      return
    }
    // Non-401/403 error: show "--" for all values
    hasError.value = true
    data.value = null
  }
}

// Watch for range changes and re-fetch
watch(homeRange, () => {
  loadPerformance()
})

// Initial load
useRefreshOnActivated(loadPerformance)

onMounted(() => {
  loadPerformance()
})
</script>

<template>
  <div class="performance-section">
    <h2 class="performance-title">绩效</h2>
    <div class="performance-grid">
      <div
        v-for="tile in kpiTiles"
        :key="tile.key"
        class="kpi-tile"
      >
        <span class="kpi-label">{{ tile.label }}</span>
        <span :class="['kpi-value', tile.colorClass]">{{ formatValue(tile) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.performance-section {
  width: 100%;
}

.performance-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 16px 0;
}

.performance-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.kpi-tile {
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  gap: 8px;
}

.kpi-label {
  font-size: 13px;
  color: #666;
}

.kpi-value {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
}

.kpi-value--green {
  color: var(--accent, #00b889);
}

.kpi-value--red {
  color: #ef4444;
}
</style>
