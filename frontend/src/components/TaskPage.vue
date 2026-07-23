<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchWorkOrders, type WorkOrderInfo, type WorkOrderTaskPayload } from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

type TaskState = 'pending' | 'running' | 'completed' | 'overdue' | 'disabled'

interface TaskRow {
  id: string
  workOrderId: number
  workOrderCode: string
  productName: string
  productSpec?: string
  processName: string
  sequenceNo: number
  plannedQuantity: number
  completedQuantity: number
  defectQuantity: number
  approvedQuantity: number
  unapprovedQuantity: number
  progress: number
  planDeadline?: string
  assignee?: string
  reportPermission?: string
  enabled: boolean
  orderStatus: string
  state: TaskState
}

const loading = ref(false)
const workOrders = ref<WorkOrderInfo[]>([])
const keyword = ref('')
const stateFilter = ref<'all' | TaskState>('all')

const taskRows = computed(() =>
  workOrders.value
    .flatMap(order => buildTaskRows(order))
    .sort((a, b) => {
      const deadlineCompare = String(a.planDeadline || '9999').localeCompare(String(b.planDeadline || '9999'))
      if (deadlineCompare !== 0) return deadlineCompare
      return a.workOrderCode.localeCompare(b.workOrderCode) || a.sequenceNo - b.sequenceNo
    })
)

const filteredRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return taskRows.value.filter(item => {
    const stateOk = stateFilter.value === 'all' || item.state === stateFilter.value
    const text = `${item.workOrderCode} ${item.productName} ${item.productSpec || ''} ${item.processName} ${item.assignee || ''}`.toLowerCase()
    const keywordOk = !q || text.includes(q)
    return stateOk && keywordOk
  })
})

const summary = computed(() => {
  const rows = taskRows.value
  return {
    total: rows.length,
    pending: rows.filter(item => item.state === 'pending').length,
    running: rows.filter(item => item.state === 'running').length,
    overdue: rows.filter(item => item.state === 'overdue').length,
    completed: rows.filter(item => item.state === 'completed').length
  }
})

async function loadData() {
  loading.value = true
  try {
    const res = await fetchWorkOrders(props.token, { size: 500 })
    workOrders.value = res.items || []
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '任务列表加载失败')
  } finally {
    loading.value = false
  }
}

function buildTaskRows(order: WorkOrderInfo): TaskRow[] {
  if (!order.tasks || order.tasks.length === 0) return []
  return order.tasks.map((task, index) => {
    const sequenceNo = task.sequenceNo || index + 1
    const planned = numberValue(task.plannedQuantity || order.plannedQuantity)
    const completed = numberValue(task.completedQuantity)
    const defect = numberValue(task.defectQuantity)
    const approved = numberValue(task.approvedQuantity)
    const unapproved = numberValue(task.unapprovedQuantity)
    const enabled = task.enabled !== false
    const progress = planned > 0 ? Math.min(100, Math.round((completed / planned) * 100)) : 0

    return {
      id: `${order.id}-${sequenceNo}-${task.processCode || task.processName || index}`,
      workOrderId: order.id,
      workOrderCode: order.code,
      productName: order.productName || '-',
      productSpec: order.productSpec,
      processName: task.processName || `工序${sequenceNo}`,
      sequenceNo,
      plannedQuantity: planned,
      completedQuantity: completed,
      defectQuantity: defect,
      approvedQuantity: approved,
      unapprovedQuantity: unapproved,
      progress,
      planDeadline: task.planDeadline || order.planEnd,
      assignee: task.assignee,
      reportPermission: task.reportPermission,
      enabled,
      orderStatus: order.status,
      state: taskState(task, order, progress, enabled)
    }
  })
}

function taskState(task: WorkOrderTaskPayload, order: WorkOrderInfo, progress: number, enabled: boolean): TaskState {
  if (!enabled || ['CANCELED', 'CLOSED'].includes(order.status)) return 'disabled'
  if (progress >= 100 || ['COMPLETED', 'FINISHED'].includes(order.status)) return 'completed'
  const deadline = task.planDeadline || order.planEnd
  if (deadline && new Date(deadline).getTime() < Date.now()) return 'overdue'
  return progress > 0 ? 'running' : 'pending'
}

function numberValue(value: unknown) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n : 0
}

function stateLabel(value: TaskState) {
  return {
    pending: '未开始',
    running: '进行中',
    completed: '已完成',
    overdue: '已逾期',
    disabled: '已停用'
  }[value]
}

function stateClass(value: TaskState) {
  return `task-state-${value}`
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(loadData)
</script>

<template>
  <section class="content task-page">
    <div class="task-summary">
      <button class="task-metric" :class="{ active: stateFilter === 'all' }" type="button" @click="stateFilter = 'all'">
        <span>全部任务</span>
        <strong>{{ summary.total }}</strong>
      </button>
      <button class="task-metric" :class="{ active: stateFilter === 'pending' }" type="button" @click="stateFilter = 'pending'">
        <span>未开始</span>
        <strong>{{ summary.pending }}</strong>
      </button>
      <button class="task-metric" :class="{ active: stateFilter === 'running' }" type="button" @click="stateFilter = 'running'">
        <span>进行中</span>
        <strong>{{ summary.running }}</strong>
      </button>
      <button class="task-metric danger" :class="{ active: stateFilter === 'overdue' }" type="button" @click="stateFilter = 'overdue'">
        <span>已逾期</span>
        <strong>{{ summary.overdue }}</strong>
      </button>
      <button class="task-metric" :class="{ active: stateFilter === 'completed' }" type="button" @click="stateFilter = 'completed'">
        <span>已完成</span>
        <strong>{{ summary.completed }}</strong>
      </button>
    </div>

    <div class="toolbar task-toolbar">
      <div class="task-search">
        <svg viewBox="0 0 24 24" width="15" height="15" stroke="currentColor" stroke-width="2" fill="none">
          <circle cx="11" cy="11" r="8"></circle>
          <path d="M21 21l-4.35-4.35"></path>
        </svg>
        <input v-model="keyword" placeholder="搜索工单、产品、工序、负责人" />
      </div>
      <button class="toolbar-btn" type="button" :disabled="loading" @click="loadData"><UiIcon name="refresh" />刷新</button>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>工单编号</th>
            <th>产品</th>
            <th>工序任务</th>
            <th>负责人/权限</th>
            <th>计划数</th>
            <th>完成数</th>
            <th>未审数</th>
            <th>不良数</th>
            <th>截止时间</th>
            <th>进度</th>
            <th style="width:90px">状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="12" class="task-empty">加载中...</td>
          </tr>
          <tr v-else-if="filteredRows.length === 0">
            <td colspan="12" class="task-empty">暂无匹配任务</td>
          </tr>
          <tr v-for="(item, index) in filteredRows" v-else :key="item.id">
            <td>{{ index + 1 }}</td>
            <td class="green-link">{{ item.workOrderCode }}</td>
            <td>
              <div class="task-main-text">{{ item.productName }}</div>
              <div class="task-sub-text">{{ item.productSpec || '-' }}</div>
            </td>
            <td>
              <div class="task-main-text">{{ item.sequenceNo }}. {{ item.processName }}</div>
              <div class="task-sub-text">工单状态：{{ item.orderStatus }}</div>
            </td>
            <td>
              <div class="task-main-text">{{ item.assignee || '-' }}</div>
              <div class="task-sub-text">{{ item.reportPermission || '所有人' }}</div>
            </td>
            <td>{{ item.plannedQuantity }}</td>
            <td>{{ item.completedQuantity }}</td>
            <td>{{ item.unapprovedQuantity }}</td>
            <td>{{ item.defectQuantity }}</td>
            <td>{{ formatDateTime(item.planDeadline) }}</td>
            <td>
              <div class="task-progress">
                <div class="task-progress-bar">
                  <div class="task-progress-fill" :style="{ width: item.progress + '%' }"></div>
                </div>
                <span>{{ item.progress }}%</span>
              </div>
            </td>
            <td>
              <span class="task-state" :class="stateClass(item.state)">{{ stateLabel(item.state) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.task-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.task-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
}

.task-metric {
  height: 70px;
  border: 1px solid #dce3e0;
  border-radius: 6px;
  background: #fff;
  color: #586360;
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
}

.task-metric span {
  display: block;
  font-size: 13px;
  margin-bottom: 8px;
}

.task-metric strong {
  display: block;
  font-size: 24px;
  color: #1e2724;
}

.task-metric.active {
  border-color: #00b889;
  box-shadow: 0 0 0 3px rgba(0, 184, 137, 0.08);
}

.task-metric.danger strong {
  color: #ff5a5a;
}

.task-toolbar {
  margin: 0;
}

.task-search {
  width: 320px;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #dce3e0;
  border-radius: 4px;
  padding: 0 10px;
  color: #8a9693;
  background: #fff;
}

.task-search input {
  width: 100%;
  border: 0;
  outline: 0;
  font-size: 13px;
  color: #1e2724;
}

.task-main-text {
  color: #1e2724;
  font-weight: 600;
}

.task-sub-text {
  margin-top: 4px;
  color: #8a9693;
  font-size: 12px;
}

.task-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}

.task-progress-bar {
  width: 84px;
  height: 7px;
  border-radius: 999px;
  background: #edf2f0;
  overflow: hidden;
}

.task-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: #00b889;
}

.task-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  min-width: 58px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  background: #eef2f1;
  color: #586360;
}

.task-state-running {
  background: #e8f5ff;
  color: #1677ff;
}

.task-state-completed {
  background: #e6f6f3;
  color: #00a67d;
}

.task-state-overdue {
  background: #fff0f0;
  color: #ff5a5a;
}

.task-state-disabled {
  background: #f3f4f6;
  color: #9aa4a1;
}

.task-empty {
  text-align: center;
  color: #8a9693;
  padding: 32px 0;
}

@media (max-width: 960px) {
  .task-summary {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .task-search {
    width: 100%;
  }
}
</style>
