<script setup lang="ts">
/**
 * WorkOrderProgressSection - 工单进度报表区
 *
 * 显示 7 个状态切换标签，默认选中"未开始"。
 * 切换标签时重新请求对应工单列表。
 * 表格支持客户端排序（升序/降序切换）。
 *
 * Requirements: 7.1-7.4, 8.1-8.8
 */
import { ref, computed, watch, onMounted } from 'vue'
import {
  fetchHomeWorkOrders,
  type WorkOrderStatusTab,
  type WorkOrderProgressItem
} from '../api'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import TableConfigButton from './TableConfigButton.vue'

const props = defineProps<{
  token: string
}>()

const emit = defineEmits<{
  (e: 'view', workOrderId: number): void
}>()

const { handleApiError } = useHomeDashboardErrorHandler()
const beginLoad = useLatestRequest()

// --- 标签定义 ---
interface TabDef {
  key: WorkOrderStatusTab
  label: string
}

const tabs: TabDef[] = [
  { key: 'not_started', label: '未开始' },
  { key: 'in_progress', label: '执行中' },
  { key: 'due_within_3_days', label: '3天内到期' },
  { key: 'overdue_unfinished', label: '超期未完工' },
  { key: 'overdue_finished_this_month', label: '本月超期完成' },
  { key: 'finished_today', label: '今日完成' },
  { key: 'finished_this_month', label: '本月完成' }
]

const activeTab = ref<WorkOrderStatusTab>('not_started')

// --- 数据 ---
const workOrders = ref<WorkOrderProgressItem[]>([])
const loading = ref(false)
const reachedDisplayLimit = computed(() => workOrders.value.length >= 500)

// --- 排序 ---
type SortableField = 'code' | 'productCode' | 'productName' | 'productSpec' | 'status'

const sortField = ref<SortableField | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')

function toggleSort(field: SortableField) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortOrder.value = 'asc'
  }
}

const sortedWorkOrders = computed(() => {
  if (!sortField.value) return workOrders.value
  const field = sortField.value
  const order = sortOrder.value === 'asc' ? 1 : -1
  return [...workOrders.value].sort((a, b) => {
    const valA = (a[field] || '').toString()
    const valB = (b[field] || '').toString()
    return valA.localeCompare(valB, 'zh-CN') * order
  })
})

// --- 数据加载 ---
async function loadWorkOrders() {
  const isCurrent = beginLoad()
  loading.value = true
  try {
    const result = await fetchHomeWorkOrders(props.token, activeTab.value)
    if (!isCurrent()) return
    if (result) {
      workOrders.value = result.items
    }
  } catch (error) {
    if (!isCurrent() || handleApiError(error)) return
    // 非 401/403 错误，保留空列表
    workOrders.value = []
  } finally {
    if (isCurrent()) loading.value = false
  }
}

function switchTab(tab: WorkOrderStatusTab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  // 切换标签时重置排序
  sortField.value = null
  sortOrder.value = 'asc'
}

watch(activeTab, () => {
  loadWorkOrders()
})

useRefreshOnActivated(loadWorkOrders)

onMounted(() => {
  loadWorkOrders()
})

// --- 表格列定义 ---
interface ColumnDef {
  key: string
  label: string
  sortable: boolean
  field?: SortableField
}

const columns: ColumnDef[] = [
  { key: 'index', label: '序号', sortable: false },
  { key: 'code', label: '工单编号', sortable: true, field: 'code' },
  { key: 'productCode', label: '产品编号', sortable: true, field: 'productCode' },
  { key: 'productName', label: '产品名称', sortable: true, field: 'productName' },
  { key: 'productSpec', label: '产品规格', sortable: true, field: 'productSpec' },
  { key: 'status', label: '状态', sortable: true, field: 'status' },
  { key: 'action', label: '操作', sortable: false }
]
</script>

<template>
  <section class="work-order-progress">
    <div class="work-order-progress__title-row">
      <h3 class="work-order-progress__title">工单进度报表</h3>
      <TableConfigButton table-id="home-work-order-progress-table" />
    </div>

    <!-- 状态切换标签 -->
    <div class="work-order-progress__tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="work-order-progress__tab"
        :class="{ 'work-order-progress__tab--active': activeTab === tab.key }"
        type="button"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <p v-if="!loading && reachedDisplayLimit" class="work-order-progress__limit-note" role="status">
      最多显示500条，完整工单请到工单列表
    </p>

    <!-- 表格容器 -->
    <div class="work-order-progress__table-wrapper">
      <table id="home-work-order-progress-table" class="work-order-progress__table">
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.key"
              :class="{ 'sortable-th': col.sortable }"
              @click="col.sortable && col.field && toggleSort(col.field)"
            >
              <span class="th-content">
                {{ col.label }}
                <span v-if="col.sortable && col.field" class="sort-icon">
                  <svg
                    viewBox="0 0 12 16"
                    width="12"
                    height="16"
                    class="sort-arrows"
                  >
                    <path
                      d="M6 2 L10 7 L2 7 Z"
                      :class="{ active: sortField === col.field && sortOrder === 'asc' }"
                    />
                    <path
                      d="M6 14 L2 9 L10 9 Z"
                      :class="{ active: sortField === col.field && sortOrder === 'desc' }"
                    />
                  </svg>
                </span>
              </span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="sortedWorkOrders.length === 0 && !loading">
            <td :colspan="columns.length" class="work-order-progress__empty">
              暂无数据
            </td>
          </tr>
          <tr v-for="(item, idx) in sortedWorkOrders" :key="item.id">
            <td>{{ idx + 1 }}</td>
            <td>{{ item.code }}</td>
            <td>{{ item.productCode }}</td>
            <td>{{ item.productName }}</td>
            <td>{{ item.productSpec }}</td>
            <td>{{ item.statusLabel }}</td>
            <td>
              <a
                class="work-order-progress__view-link"
                href="javascript:void(0)"
                @click.prevent="emit('view', item.id)"
              >
                查看
              </a>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.work-order-progress {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
}

.work-order-progress__title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.work-order-progress__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.work-order-progress__tab {
  padding: 6px 14px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background: #fff;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.work-order-progress__tab:hover {
  border-color: #409eff;
  color: #409eff;
}

.work-order-progress__tab--active {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}

.work-order-progress__tab--active:hover {
  background: #337ecc;
  border-color: #337ecc;
  color: #fff;
}

.work-order-progress__table-wrapper {
  flex: 1;
  overflow-x: auto;
  overflow-y: auto;
  max-height: 400px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.work-order-progress__limit-note {
  margin: 0 0 12px;
  color: var(--text-muted, #64748b);
  font-size: 12px;
}

.work-order-progress__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 700px;
}

.work-order-progress__table thead {
  position: sticky;
  top: 0;
  z-index: 1;
}

.work-order-progress__table th {
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  padding: 10px 12px;
  text-align: left;
  font-weight: 500;
  color: #606266;
  white-space: nowrap;
  user-select: none;
}

.work-order-progress__table td {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  color: #333;
}

.work-order-progress__table tbody tr:hover {
  background: #f5f7fa;
}

.sortable-th {
  cursor: pointer;
}

.sortable-th:hover {
  background: #f0f2f5;
}

.th-content {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.sort-icon {
  display: inline-flex;
  align-items: center;
}

.sort-arrows path {
  fill: #c0c4cc;
  transition: fill 0.2s;
}

.sort-arrows path.active {
  fill: #409eff;
}

.work-order-progress__empty {
  text-align: center;
  color: #999;
  padding: 48px 0 !important;
  font-size: 14px;
}

.work-order-progress__view-link {
  color: #409eff;
  text-decoration: none;
  cursor: pointer;
  font-size: 13px;
}

.work-order-progress__view-link:hover {
  text-decoration: underline;
}
</style>
