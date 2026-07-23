<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchReports, fetchWorkOrders, type WorkOrderInfo, type WorkOrderReportInfo } from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const loading = ref(false)
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
  loading.value = true
  try {
    const [workOrderRes, reportRes] = await Promise.all([
      fetchWorkOrders(props.token, { page: 1, size: 500 }),
      fetchReports(props.token, { page: 1, size: 500 })
    ])
    workOrders.value = workOrderRes.items || []
    reports.value = reportRes.items || []
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '报表数据加载失败')
  } finally {
    loading.value = false
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

onMounted(loadData)
</script>

<template>
  <section class="content report-center-page">
    <div class="report-header">
      <div>
        <h2>生产报表</h2>
        <p>汇总当前工厂的工单产出、报工审批和人员产出。</p>
      </div>
      <button class="ghost-btn" type="button" :disabled="loading" @click="loadData"><UiIcon name="refresh" />刷新</button>
    </div>

    <div class="metric-grid">
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

    <div v-if="loading" class="empty-cell">加载中...</div>

    <div v-else class="report-grid">
      <div class="report-panel">
        <div class="panel-title">产品产出排行</div>
        <table class="data-table compact-table">
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
        <div class="panel-title">人员产出排行</div>
        <table class="data-table compact-table">
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

    <div class="report-panel">
      <div class="panel-title">最近报工</div>
      <table class="data-table compact-table">
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
