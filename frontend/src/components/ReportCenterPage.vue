<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { fetchReports, fetchWorkOrders, type WorkOrderInfo, type WorkOrderReportInfo } from '../api'
import UiIcon from './UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'
import PageLoadFeedback from './PageLoadFeedback.vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchAllPages } from '../utils/pagination'

const props = defineProps<{ token: string }>()
defineEmits<{ notice: [message: string] }>()
const loading = ref(true)
const loadError = ref('')
const hasData = ref(false)
const beginLoad = useLatestRequest()
const workOrders = ref<WorkOrderInfo[]>([])
const reports = ref<WorkOrderReportInfo[]>([])

const totalPlanned = computed(() => sum(workOrders.value, item => item.plannedQuantity || 0))
const totalGood = computed(() => sum(workOrders.value, item => item.goodQuantity || 0))
const totalDefect = computed(() => sum(workOrders.value, item => item.defectQuantity || 0))
const pendingReports = computed(() => reports.value.filter(item => item.status === 'PENDING').length)
const avgProgress = computed(() => {
  if (workOrders.value.length === 0) return 0
  return Math.round(sum(workOrders.value, item => item.progressPercent || 0) / workOrders.value.length)
})
const defectRate = computed(() => {
  const total = totalGood.value + totalDefect.value
  if (total <= 0) return '0.00%'
  return `${((totalDefect.value / total) * 100).toFixed(2)}%`
})

const productRows = computed(() => {
  const map = new Map<string, { name: string; planned: number; good: number; defect: number }>()
  workOrders.value.forEach(item => {
    const key = item.productName || '-'
    const row = map.get(key) || { name: key, planned: 0, good: 0, defect: 0 }
    row.planned += item.plannedQuantity || 0
    row.good += item.goodQuantity || 0
    row.defect += item.defectQuantity || 0
    map.set(key, row)
  })
  return Array.from(map.values()).sort((a, b) => b.good - a.good).slice(0, 8)
})

const reporterRows = computed(() => {
  const map = new Map<string, { name: string; good: number; defect: number; count: number }>()
  reports.value.forEach(item => {
    const key = item.reporterName || '-'
    const row = map.get(key) || { name: key, good: 0, defect: 0, count: 0 }
    row.good += item.goodQuantity || 0
    row.defect += item.defectQuantity || 0
    row.count += 1
    map.set(key, row)
  })
  return Array.from(map.values()).sort((a, b) => b.good - a.good).slice(0, 8)
})

const recentReports = computed(() => reports.value.slice(0, 10))

async function loadData() {
  const isCurrent = beginLoad()
  const token = props.token
  const current = () => isCurrent() && token === props.token
  if (!token) { loading.value = false; return }
  loading.value = true
  loadError.value = ''
  try {
    const [workOrderRes, reportRes] = await Promise.all([
      fetchAllPages(params => fetchWorkOrders(token, params), 500, current),
      fetchAllPages(params => fetchReports(token, params), 500, current)
    ])
    if (!current()) return
    workOrders.value = workOrderRes.items
    reports.value = reportRes.items
    hasData.value = true
  } catch (error) {
    if (current()) loadError.value = error instanceof Error && error.message ? error.message : '报表数据加载失败'
  } finally {
    if (current()) loading.value = false
  }
}

function sum<T>(items: T[], getter: (item: T) => number) {
  return items.reduce((total, item) => total + getter(item), 0)
}

function statusLabel(status: string) {
  if (status === 'PENDING') return '未审批'
  if (status === 'APPROVED') return '已审批'
  if (status === 'REJECTED') return '已驳回'
  return status || '-'
}

useRefreshOnActivated(loadData)
onMounted(loadData)
watch(() => props.token, () => {
  workOrders.value = []
  reports.value = []
  hasData.value = false
  loadError.value = ''
  void loadData()
}, { flush: 'sync' })
</script>

<template>
  <section class="content report-center-page">
    <div class="report-header">
      <div>
        <h2>生产报表</h2>
      </div>
      <button class="ghost-btn ui-icon-button" type="button" :disabled="loading" @click="loadData" title="刷新生产报表" aria-label="刷新生产报表"><UiIcon name="refresh" /></button>
    </div>

    <PageLoadFeedback :loading="loading" :error="loadError" :has-data="hasData" @retry="loadData" />

    <template v-if="hasData">
    <div class="metric-grid" :aria-busy="loading">
      <div class="metric-card">
        <span>工单数</span>
        <strong>{{ workOrders.length }}</strong>
      </div>
      <div class="metric-card">
        <span>计划数</span>
        <strong>{{ totalPlanned }}</strong>
      </div>
      <div class="metric-card">
        <span>良品数</span>
        <strong>{{ totalGood }}</strong>
      </div>
      <div class="metric-card">
        <span>不良率</span>
        <strong>{{ defectRate }}</strong>
      </div>
      <div class="metric-card">
        <span>平均进度</span>
        <strong>{{ avgProgress }}%</strong>
      </div>
      <div class="metric-card warn">
        <span>待审报工</span>
        <strong>{{ pendingReports }}</strong>
      </div>
    </div>

    <div class="report-grid" :aria-busy="loading">
      <div class="report-panel">
        <div class="panel-title report-panel-heading"><span>产品产出排行</span><TableConfigButton table-id="report-product-table" /></div>
        <table id="report-product-table" class="data-table compact-table">
          <thead>
            <tr>
              <th>产品</th>
              <th>计划数</th>
              <th>良品数</th>
              <th>不良数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in productRows" :key="row.name">
              <td>{{ row.name }}</td>
              <td>{{ row.planned }}</td>
              <td>{{ row.good }}</td>
              <td>{{ row.defect }}</td>
            </tr>
            <tr v-if="productRows.length === 0">
              <td colspan="4" class="empty-cell">暂无工单数据</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="report-panel">
        <div class="panel-title report-panel-heading"><span>人员产出排行</span><TableConfigButton table-id="report-worker-table" /></div>
        <table id="report-worker-table" class="data-table compact-table">
          <thead>
            <tr>
              <th>报工人</th>
              <th>报工次数</th>
              <th>良品数</th>
              <th>不良数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in reporterRows" :key="row.name">
              <td>{{ row.name }}</td>
              <td>{{ row.count }}</td>
              <td>{{ row.good }}</td>
              <td>{{ row.defect }}</td>
            </tr>
            <tr v-if="reporterRows.length === 0">
              <td colspan="4" class="empty-cell">暂无报工数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="report-panel" :aria-busy="loading">
      <div class="panel-title report-panel-heading"><span>最近报工</span><TableConfigButton table-id="report-latest-table" /></div>
      <table id="report-latest-table" class="data-table compact-table">
        <thead>
          <tr>
            <th>工单编号</th>
            <th>产品</th>
            <th>工序</th>
            <th>报工人</th>
            <th>良品</th>
            <th>不良</th>
            <th>状态</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in recentReports" :key="item.id">
            <td class="green-link">{{ item.workOrderCode }}</td>
            <td>{{ item.productName || '-' }}</td>
            <td>{{ item.processName || '-' }}</td>
            <td>{{ item.reporterName || '-' }}</td>
            <td>{{ item.goodQuantity || 0 }}</td>
            <td>{{ item.defectQuantity || 0 }}</td>
            <td>{{ statusLabel(item.status) }}</td>
            <td>{{ item.createdAt || '-' }}</td>
          </tr>
          <tr v-if="recentReports.length === 0">
            <td colspan="8" class="empty-cell">暂无报工数据</td>
          </tr>
        </tbody>
      </table>
    </div>
    </template>
  </section>
</template>

<style scoped>
.report-center-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.report-header h2 {
  margin: 0 0 6px;
  color: #1f2d2a;
  font-size: 20px;
}

.report-header p {
  margin: 0;
  color: #64716d;
  font-size: 13px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  border: 1px solid #e4ece8;
  border-radius: 8px;
  background: #fff;
  padding: 14px;
}

.metric-card span {
  display: block;
  margin-bottom: 8px;
  color: #71807a;
  font-size: 12px;
}

.metric-card strong {
  color: #16211e;
  font-size: 22px;
}

.metric-card.warn strong {
  color: #b85b00;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.report-panel {
  border: 1px solid #e4ece8;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.panel-title {
  padding: 12px 14px;
  border-bottom: 1px solid #edf2ef;
  color: #1f2d2a;
  font-size: 14px;
  font-weight: 600;
}

.report-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.compact-table th,
.compact-table td {
  padding: 10px 12px;
}

.empty-cell {
  padding: 28px;
  text-align: center;
  color: #8a9693;
}

@media (max-width: 980px) {
  .metric-grid,
  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
