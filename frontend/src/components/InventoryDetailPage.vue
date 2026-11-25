<script setup lang="ts">
import { t, formatUiDate } from '../i18n'
import { downloadXlsx } from '../utils/spreadsheet'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch, type CSSProperties } from 'vue'
import {
  fetchInventoryDetails,
  type InventoryDetailInfo
} from '../api'
import UiIcon from './UiIcon.vue'
import ColumnSettingsPanel, { type ColumnSetting } from './ColumnSettingsPanel.vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { resolveInventoryTimeRange } from '../utils/inventoryTimeRange'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

// ===== State =====
const items = ref<InventoryDetailInfo[]>([])
const totalItems = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const beginLoad = useLatestRequest()
const showAdvancedFilter = ref(true)

const startTimeFocused = ref(false)
const endTimeFocused = ref(false)

// Filter state
const filterForm = reactive({
  receiptCode: '',
  receiptType: '',
  sourceType: '',
  remark: '',
  productCode: '',
  productName: '',
  productSpec: '',
  productAttribute: '',
  timeRange: '',
  startTime: '',
  endTime: ''
})

// Receipt type options (merged inbound and outbound types)
const receiptTypeOptions = [
  '普通入库', '采购入库', '生产入库', '退货入库', '调拨入库', '其它入库',
  '普通出库', '销售出库', '生产出库', '调拨出库', '采购退货出库', '其它出库'
]

const sourceTypeOptions = [
  { label: '入库单', value: 'IN' },
  { label: '出库单', value: 'OUT' }
]

const productAttributeOptions = ['自制', '外购', '委外']

// Column config
const allColumns: ColumnSetting[] = [
  { key: 'index', label: '序号', visible: true, width: 70, fixed: 'left' },
  { key: 'receiptCode', label: '单据编号', visible: true, width: 150, fixed: 'none' },
  { key: 'productCode', label: '产品编号', visible: true, width: 140, fixed: 'none' },
  { key: 'productName', label: '产品名称', visible: true, width: 150, fixed: 'none' },
  { key: 'productSpec', label: '产品规格', visible: true, width: 150, fixed: 'none' },
  { key: 'productAttribute', label: '产品属性', visible: true, width: 140, fixed: 'none' },
  { key: 'unitName', label: '库存单位', visible: true, width: 100, fixed: 'none' },
  { key: 'receiptQty', label: '单据数量', visible: true, width: 110, fixed: 'none' },
  { key: 'changeQty', label: '库存变更数量', visible: true, width: 140, fixed: 'none' },
  { key: 'receiptType', label: '单据类型', visible: true, width: 120, fixed: 'none' },
  { key: 'sourceType', label: '来源', visible: true, width: 100, fixed: 'none' },
  { key: 'operationTime', label: '收发时间', visible: true, width: 160, fixed: 'none' },
  { key: 'remark', label: '备注', visible: true, width: 180, fixed: 'none' }
]
const columns = ref(allColumns.map(c => ({ ...c })))
const defaultColumnSettings = allColumns.map(c => ({ ...c }))
const showColumnConfig = ref(false)

// ===== Computed =====
const visibleColumns = computed(() => columns.value.filter(c => c.visible))
const totalPages = computed(() => Math.ceil(totalItems.value / pageSize.value) || 1)

function detailColumnStyle(column: ColumnSetting, header = false): CSSProperties {
  const currentIndex = visibleColumns.value.findIndex(item => item.key === column.key)
  const left = visibleColumns.value
    .slice(0, currentIndex)
    .filter(item => item.fixed === 'left')
    .reduce((sum, item) => sum + item.width, 0)
  const right = visibleColumns.value
    .slice(currentIndex + 1)
    .filter(item => item.fixed === 'right')
    .reduce((sum, item) => sum + item.width, 0)
  const style: CSSProperties = {
    width: `${column.width}px`,
    minWidth: `${column.width}px`
  }
  if (column.fixed !== 'none') {
    style.position = 'sticky'
    style.zIndex = header ? 12 : 3
    style.background = header ? 'var(--table-head-bg)' : 'var(--table-row-bg)'
    if (column.fixed === 'left') style.left = `${left}px`
    if (column.fixed === 'right') style.right = `${right}px`
  }
  return style
}

// ===== Methods =====
function formatDisplayDateTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function loadData() {
  const isCurrent = beginLoad()
  loading.value = true
  try {
    const res = await fetchInventoryDetails(props.token, {
      receiptCode: filterForm.receiptCode.trim() || undefined,
      receiptType: filterForm.receiptType || undefined,
      sourceType: filterForm.sourceType || undefined,
      remark: filterForm.remark.trim() || undefined,
      productCode: filterForm.productCode.trim() || undefined,
      productName: filterForm.productName.trim() || undefined,
      productSpec: filterForm.productSpec.trim() || undefined,
      productAttribute: filterForm.productAttribute || undefined,
      ...resolveInventoryTimeRange(filterForm),
      page: currentPage.value,
      size: pageSize.value
    })
    if (!isCurrent()) return
    items.value = res.items
    totalItems.value = res.total
  } catch (e: any) {
    if (isCurrent()) emit('notice', e.message || '加载库存收发明细失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterForm.receiptCode = ''
  filterForm.receiptType = ''
  filterForm.sourceType = ''
  filterForm.remark = ''
  filterForm.productCode = ''
  filterForm.productName = ''
  filterForm.productSpec = ''
  filterForm.productAttribute = ''
  filterForm.timeRange = ''
  filterForm.startTime = ''
  filterForm.endTime = ''
  currentPage.value = 1
  loadData()
}

function saveColumnSettings(settings: ColumnSetting[]) {
  columns.value = settings.map(column => ({ ...column }))
  showColumnConfig.value = false
  emit('notice', '列配置已保存')
}

function goPage(p: number) {
  if (p >= 1 && p <= totalPages.value) {
    currentPage.value = p
    loadData()
  }
}

const exporting = ref(false)
const exportError = ref('')
let exportEpoch = 0, exportDisposed = false, exportActive = true
watch(() => props.token, () => { exportEpoch++; exporting.value = false; exportError.value = '' }, { flush: 'sync' })
onActivated(() => { exportActive = true })
onDeactivated(() => { exportActive = false; exportEpoch++; exporting.value = false; exportError.value = '' })
onBeforeUnmount(() => { exportEpoch++; exportDisposed = true; exportActive = false })
async function exportXlsx() {
  if (exporting.value || !props.token || loading.value || !exportActive) return
  if (!items.value.length) { emit('notice', '暂无数据可导出'); return }
  const token = props.token, epoch = exportEpoch
  const current = () => exportActive && !exportDisposed && props.token === token && exportEpoch === epoch
  const data = items.value.map((item, index) => [index + 1, item.receiptCode, item.productCode, item.productName, item.productSpec, item.productAttribute, item.unitName, item.receiptQty, item.changeQty, item.receiptType, t(item.sourceType === 'IN' ? '入库单' : '出库单'), item.operationTime, item.remark])
  const headers = ['序号', '单据编号', '产品编号', '产品名称', '产品规格', '产品属性', '库存单位', '单据数量', '库存变更数量', '单据类型', '来源', '收发时间', '备注'].map(label => t(label))
  exportError.value = ''; exporting.value = true
  try {
    const downloaded = await downloadXlsx(`${t('库存收发明细')}_${formatUiDate(new Date()).slice(0, 10)}.xlsx`, t('库存收发明细'), [headers, ...data], current)
    if (downloaded && current()) emit('notice', '导出Excel成功')
  } catch (error) { if (current()) { exportError.value = error instanceof Error ? error.message : '导出失败'; emit('notice', exportError.value) } }
  finally { if (current()) exporting.value = false }
}

function exportCSV() {
  if (exporting.value || loading.value) return
  if (items.value.length === 0) {
    emit('notice', '暂无数据可导出')
    return
  }

  const headers = ['序号', '单据编号', '产品编号', '产品名称', '产品规格', '产品属性',
    '库存单位', '单据数量', '库存变更数量', '单据类型', '来源', '收发时间', '备注']

  const csvRows: string[] = []
  // BOM for UTF-8 Excel support
  csvRows.push(headers.join(','))

  items.value.forEach((item, idx) => {
    const row = [
      idx + 1,
      item.receiptCode,
      item.productCode,
      item.productName,
      item.productSpec,
      item.productAttribute,
      item.unitName,
      item.receiptQty,
      item.changeQty,
      item.receiptType,
      item.sourceType === 'IN' ? '入库单' : '出库单',
      item.operationTime,
      item.remark
    ]
    csvRows.push(row.map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(','))
  })

  const csvContent = '\uFEFF' + csvRows.join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.setAttribute('href', url)
  link.setAttribute('download', `库存收发明细_${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  emit('notice', '导出成功')
}

function getSourceLabel(sourceType: string): string {
  return sourceType === 'IN' ? '入库' : '出库'
}

function getSourceBadgeClass(sourceType: string): string {
  return sourceType === 'IN' ? 'badge-inbound' : 'badge-outbound'
}

useRefreshOnActivated(loadData)

onMounted(() => {
  loadData()
})
</script>

<template>
  <section class="content">
    <!-- Search Filters - Row 1 -->
    <div class="filters inline-filters" style="margin-bottom: 8px">
      <div class="filter-group">
        <label>单据编号</label>
        <input v-model="filterForm.receiptCode" placeholder="请输入" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-group">
        <label>单据类型</label>
        <select v-model="filterForm.receiptType">
          <option value="">请选择</option>
          <option v-for="t in receiptTypeOptions" :key="t" :value="t">{{ t }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>库存类型</label>
        <select v-model="filterForm.productAttribute">
          <option value="">请选择</option>
          <option v-for="a in productAttributeOptions" :key="a" :value="a">{{ a }}</option>
        </select>
      </div>

      <div class="filter-actions">
        <button class="primary-btn query-btn ui-icon-button" type="button" @click="handleSearch" title="查询库存明细" aria-label="查询库存明细"><UiIcon name="search" /></button>
        <button class="toolbar-btn ui-icon-button" type="button" @click="handleReset" title="重置条件" aria-label="重置条件">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
          </svg>
        </button>
        <button class="toolbar-btn ui-action-button" type="button" @click="showAdvancedFilter = !showAdvancedFilter"
          :title="showAdvancedFilter ? '收起高级筛选' : '展开高级筛选'"
          style="font-size:12px; display:flex; align-items:center; gap:4px;" :aria-label="showAdvancedFilter ? '收起高级筛选' : '展开高级筛选'"><svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <polyline :points="showAdvancedFilter ? '18 15 12 9 6 15' : '6 9 12 15 18 9'"></polyline>
          </svg><span>高级筛选</span></button>
      </div>
    </div>

    <!-- Search Filters - Row 2 (Advanced) -->
    <template v-if="showAdvancedFilter">
      <div class="filters inline-filters" style="margin-bottom: 8px">
        <div class="filter-group">
          <label>库存收发时间</label>
          <select v-model="filterForm.timeRange" style="width: 140px">
            <option value="">选择范围</option>
            <option value="today">今天</option>
            <option value="week">本周</option>
            <option value="month">本月</option>
            <option value="custom">自定义时间</option>
          </select>
        </div>
        <div v-if="filterForm.timeRange === 'custom'" class="filter-group" style="display:flex; gap:8px; align-items:center;">
          <input
            type="date"
            v-model="filterForm.startTime"
            :class="{ focused: startTimeFocused }"
            @focus="startTimeFocused = true"
            @blur="startTimeFocused = false"
          />
          <span style="color:#8a9693">-</span>
          <input
            type="date"
            v-model="filterForm.endTime"
            :class="{ focused: endTimeFocused }"
            @focus="endTimeFocused = true"
            @blur="endTimeFocused = false"
          />
        </div>
        <div class="filter-group">
          <label>备注</label>
          <input v-model="filterForm.remark" placeholder="请输入" @keyup.enter="handleSearch" />
        </div>
        <div class="filter-group">
          <label>产品编号</label>
          <input v-model="filterForm.productCode" placeholder="请输入" @keyup.enter="handleSearch" />
        </div>
      </div>

      <div class="filters inline-filters" style="margin-bottom: 8px">
        <div class="filter-group">
          <label>产品名称</label>
          <input v-model="filterForm.productName" placeholder="请输入" @keyup.enter="handleSearch" />
        </div>
        <div class="filter-group">
          <label>产品规格</label>
          <input v-model="filterForm.productSpec" placeholder="请输入" @keyup.enter="handleSearch" />
        </div>
        <div class="filter-group">
          <label>产品属性</label>
          <select v-model="filterForm.productAttribute">
            <option value="">请选择产品属性</option>
            <option v-for="a in productAttributeOptions" :key="a" :value="a">{{ a }}</option>
          </select>
        </div>
      </div>

      <div class="filters inline-filters" style="margin-bottom: 16px">
        <div class="filter-group">
          <label>来源单据类型</label>
          <select v-model="filterForm.sourceType">
            <option value="">请选择来源单据类型</option>
            <option v-for="s in sourceTypeOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </div>
      </div>
    </template>

    <!-- Action Toolbar -->
    <div class="toolbar" style="margin-bottom:16px">
      <button class="toolbar-btn ui-action-button" type="button" :disabled="exporting || loading" @click="exportXlsx" title="导出Excel" aria-label="导出Excel"><UiIcon name="download" />Excel</button>
      <button class="toolbar-btn ui-action-button" type="button" :disabled="exporting || loading" @click="exportCSV" title="CSV备份" aria-label="CSV备份"><UiIcon name="archive" />CSV备份</button>
      <span class="export-scope-note">已加载 {{ items.length }} 条 · CSV仅用于备份，不宜直接用Excel打开</span>
      <p v-if="exportError" class="export-error" role="alert">{{ exportError }}</p>

      <button class="table-config-trigger ui-icon-button" type="button" title="字段配置" aria-label="字段配置" @click="showColumnConfig = true">
        <UiIcon name="table-columns" :size="16" />
      </button>
    </div>

    <!-- Data Table -->
    <div class="table-container">
      <table class="data-table inventory-detail-data-table">
        <thead>
          <tr>
            <th v-for="col in visibleColumns" :key="col.key" :style="detailColumnStyle(col, true)">{{ col.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in items" :key="idx">
            <template v-for="col in visibleColumns" :key="col.key">
              <td v-if="col.key === 'index'" :style="detailColumnStyle(col)">{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
              <td v-else-if="col.key === 'receiptCode'" :style="detailColumnStyle(col)">{{ item.receiptCode }}</td>
              <td v-else-if="col.key === 'productCode'" :style="detailColumnStyle(col)">{{ item.productCode }}</td>
              <td v-else-if="col.key === 'productName'" :style="detailColumnStyle(col)">{{ item.productName }}</td>
              <td v-else-if="col.key === 'productSpec'" :style="detailColumnStyle(col)">{{ item.productSpec || '-' }}</td>
              <td v-else-if="col.key === 'productAttribute'" :style="detailColumnStyle(col)">{{ item.productAttribute || '-' }}</td>
              <td v-else-if="col.key === 'unitName'" :style="detailColumnStyle(col)">{{ item.unitName || '-' }}</td>
              <td v-else-if="col.key === 'receiptQty'" :style="detailColumnStyle(col)">{{ item.receiptQty }}</td>
              <td v-else-if="col.key === 'changeQty'" :style="detailColumnStyle(col)" :class="item.changeQty < 0 ? 'change-negative' : 'change-positive'">
                {{ item.changeQty > 0 ? '+' + item.changeQty : item.changeQty }}
              </td>
              <td v-else-if="col.key === 'receiptType'" :style="detailColumnStyle(col)">
                <span class="badge" :class="getSourceBadgeClass(item.sourceType)">{{ item.receiptType }}</span>
              </td>
              <td v-else-if="col.key === 'sourceType'" :style="detailColumnStyle(col)">
                <span class="badge" :class="getSourceBadgeClass(item.sourceType)">{{ getSourceLabel(item.sourceType) }}</span>
              </td>
              <td v-else-if="col.key === 'operationTime'" :style="detailColumnStyle(col)">{{ formatDisplayDateTime(item.operationTime) }}</td>
              <td v-else-if="col.key === 'remark'" :style="detailColumnStyle(col)" :title="item.remark">
                {{ item.remark || '-' }}
              </td>
            </template>
          </tr>
          <tr v-if="items.length === 0">
            <td :colspan="visibleColumns.length" style="text-align: center; color: #8a9693; padding: 32px 0;">
              暂无库存收发明细数据
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div class="pagination" v-if="totalItems > 0" style="margin-top:16px">
      <span class="page-info">共{{ totalItems }}条结果</span>
      <div class="page-controls">
        <button :disabled="currentPage <= 1" @click="goPage(currentPage - 1)" class="ui-icon-button" title="上一页" aria-label="上一页" type="button"><UiIcon name="arrowLeft" /></button>
        <button class="active">{{ currentPage }}</button>
        <button :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)" class="ui-icon-button" title="下一页" aria-label="下一页" type="button"><UiIcon name="arrowRight" /></button>
      </div>
      <select class="page-size-select" v-model="pageSize" @change="currentPage = 1; loadData()">
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
      </select>
    </div>

    <ColumnSettingsPanel
      v-if="showColumnConfig"
      v-model="columns"
      :defaults="defaultColumnSettings"
      title="列设置"
      @confirm="saveColumnSettings"
      @cancel="showColumnConfig = false"
    />
  </section>
</template>

<style scoped>
.change-positive {
  color: #00b889;
  font-weight: 600;
}

.change-negative {
  color: #ff5a5a;
  font-weight: 600;
}

.badge-inbound {
  background: #f0faf7;
  color: #00b889;
  border: 1px solid #ccefe7;
}

.badge-outbound {
  background: #fff5f5;
  color: #ff5a5a;
  border: 1px solid #fdd;
}

.page-size-select {
  border: 1px solid #dce3e0;
  border-radius: 4px;
  background: white;
  padding: 4px 8px;
  font-size: 12px;
  color: #586360;
  outline: none;
  cursor: pointer;
  height: 28px;
}
.page-size-select:hover {
  border-color: #00b889;
}
</style>
