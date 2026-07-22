<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  fetchInventoryDetails,
  type InventoryDetailInfo
} from '../api'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

// ===== State =====
const items = ref<InventoryDetailInfo[]>([])
const totalItems = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
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
const allColumns = [
  { key: 'index', label: '序号', visible: true },
  { key: 'receiptCode', label: '单据编号', visible: true },
  { key: 'productCode', label: '产品编号', visible: true },
  { key: 'productName', label: '产品名称', visible: true },
  { key: 'productSpec', label: '产品规格', visible: true },
  { key: 'productAttribute', label: '产品属性', visible: true },
  { key: 'unitName', label: '库存单位', visible: true },
  { key: 'receiptQty', label: '单据数量', visible: true },
  { key: 'changeQty', label: '库存变更数量', visible: true },
  { key: 'receiptType', label: '单据类型', visible: true },
  { key: 'sourceType', label: '来源', visible: true },
  { key: 'operationTime', label: '收发时间', visible: true },
  { key: 'remark', label: '备注', visible: true }
]
const columns = ref(allColumns.map(c => ({ ...c })))
const showColumnConfig = ref(false)

// ===== Computed =====
const visibleColumns = computed(() => columns.value.filter(c => c.visible))
const totalPages = computed(() => Math.ceil(totalItems.value / pageSize.value) || 1)

// ===== Methods =====
function formatDateTime(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}:${s}`
}

function formatDisplayDateTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function loadData() {
  loading.value = true
  try {
    let start: string | undefined = undefined
    let end: string | undefined = undefined
    if (filterForm.timeRange === 'custom') {
      if (filterForm.startTime) start = filterForm.startTime + ' 00:00:00'
      if (filterForm.endTime) end = filterForm.endTime + ' 23:59:59'
    } else if (filterForm.timeRange === 'today') {
      const today = new Date()
      start = formatDateTime(new Date(today.getFullYear(), today.getMonth(), today.getDate(), 0, 0, 0))
      end = formatDateTime(new Date(today.getFullYear(), today.getMonth(), today.getDate(), 23, 59, 59))
    } else if (filterForm.timeRange === 'week') {
      const today = new Date()
      const day = today.getDay() || 7
      const monday = new Date(today)
      monday.setDate(today.getDate() - day + 1)
      start = formatDateTime(new Date(monday.getFullYear(), monday.getMonth(), monday.getDate(), 0, 0, 0))
      end = formatDateTime(new Date())
    } else if (filterForm.timeRange === 'month') {
      const today = new Date()
      start = formatDateTime(new Date(today.getFullYear(), today.getMonth(), 1, 0, 0, 0))
      end = formatDateTime(new Date())
    }

    const res = await fetchInventoryDetails(props.token, {
      receiptCode: filterForm.receiptCode.trim() || undefined,
      receiptType: filterForm.receiptType || undefined,
      sourceType: filterForm.sourceType || undefined,
      remark: filterForm.remark.trim() || undefined,
      productCode: filterForm.productCode.trim() || undefined,
      productName: filterForm.productName.trim() || undefined,
      productSpec: filterForm.productSpec.trim() || undefined,
      productAttribute: filterForm.productAttribute || undefined,
      startTime: start,
      endTime: end,
      page: currentPage.value,
      size: pageSize.value
    })
    items.value = res.items
    totalItems.value = res.total
  } catch (e: any) {
    emit('notice', e.message || '加载库存收发明细失败')
  } finally {
    loading.value = false
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

function goPage(p: number) {
  if (p >= 1 && p <= totalPages.value) {
    currentPage.value = p
    loadData()
  }
}

function exportCSV() {
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
      `"${item.receiptCode || ''}"`,
      `"${item.productCode || ''}"`,
      `"${item.productName || ''}"`,
      `"${item.productSpec || ''}"`,
      `"${item.productAttribute || ''}"`,
      `"${item.unitName || ''}"`,
      item.receiptQty,
      item.changeQty,
      `"${item.receiptType || ''}"`,
      item.sourceType === 'IN' ? '入库单' : '出库单',
      `"${item.operationTime || ''}"`,
      `"${(item.remark || '').replace(/"/g, '""')}"`
    ]
    csvRows.push(row.join(','))
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
        <button class="primary-btn query-btn" type="button" @click="handleSearch">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          查询
        </button>
        <button class="toolbar-btn" type="button" @click="handleReset" title="重置条件">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
          </svg>
        </button>
        <button class="toolbar-btn" type="button" @click="showAdvancedFilter = !showAdvancedFilter"
          :title="showAdvancedFilter ? '收起高级筛选' : '展开高级筛选'"
          style="font-size:12px; display:flex; align-items:center; gap:4px;">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <polyline :points="showAdvancedFilter ? '18 15 12 9 6 15' : '6 9 12 15 18 9'"></polyline>
          </svg>
        </button>
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
      <button class="toolbar-btn" type="button" @click="exportCSV">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
          <polyline points="7 10 12 15 17 10"></polyline>
          <line x1="12" y1="15" x2="12" y2="3"></line>
        </svg>
        导出
      </button>

      <button class="text-btn" type="button" style="color:#586360;" @click="showColumnConfig = true">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
          <line x1="4" y1="21" x2="4" y2="14"></line>
          <line x1="4" y1="10" x2="4" y2="3"></line>
          <line x1="12" y1="21" x2="12" y2="12"></line>
          <line x1="12" y1="8" x2="12" y2="3"></line>
          <line x1="20" y1="21" x2="20" y2="16"></line>
          <line x1="20" y1="12" x2="20" y2="3"></line>
          <line x1="1" y1="14" x2="7" y2="14"></line>
          <line x1="9" y1="8" x2="15" y2="8"></line>
          <line x1="17" y1="16" x2="23" y2="16"></line>
        </svg>
        字段配置
      </button>
    </div>

    <!-- Data Table -->
    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th v-for="col in visibleColumns" :key="col.key">{{ col.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in items" :key="idx">
            <td v-if="columns[0].visible">{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
            <td v-if="columns[1].visible">{{ item.receiptCode }}</td>
            <td v-if="columns[2].visible">{{ item.productCode }}</td>
            <td v-if="columns[3].visible">{{ item.productName }}</td>
            <td v-if="columns[4].visible">{{ item.productSpec || '-' }}</td>
            <td v-if="columns[5].visible">{{ item.productAttribute || '-' }}</td>
            <td v-if="columns[6].visible">{{ item.unitName || '-' }}</td>
            <td v-if="columns[7].visible">{{ item.receiptQty }}</td>
            <td v-if="columns[8].visible" :class="item.changeQty < 0 ? 'change-negative' : 'change-positive'">
              {{ item.changeQty > 0 ? '+' + item.changeQty : item.changeQty }}
            </td>
            <td v-if="columns[9].visible">
              <span class="badge" :class="getSourceBadgeClass(item.sourceType)">{{ item.receiptType }}</span>
            </td>
            <td v-if="columns[10].visible">
              <span class="badge" :class="getSourceBadgeClass(item.sourceType)">{{ getSourceLabel(item.sourceType) }}</span>
            </td>
            <td v-if="columns[11].visible">{{ formatDisplayDateTime(item.operationTime) }}</td>
            <td v-if="columns[12].visible" style="max-width:160px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap" :title="item.remark">
              {{ item.remark || '-' }}
            </td>
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
        <button :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">&lt;</button>
        <button class="active">{{ currentPage }}</button>
        <button :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">&gt;</button>
      </div>
      <select class="page-size-select" v-model="pageSize" @change="currentPage = 1; loadData()">
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
      </select>
    </div>

    <!-- Column Config Modal -->
    <div v-if="showColumnConfig" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 480px;">
        <div class="modal-header">
          <h2>字段显示配置</h2>
          <button class="close-btn" type="button" @click="showColumnConfig = false">×</button>
        </div>
        <div style="padding: 24px;">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
            <label v-for="col in columns" :key="col.key" style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
              <input type="checkbox" v-model="col.visible" style="accent-color: #00b889; width: 16px; height: 16px;" :disabled="col.key === 'receiptCode'" />
              <span style="font-size: 14px; color: #1e2724;">{{ col.label }}</span>
            </label>
          </div>
          <div style="text-align: right; margin-top: 24px;">
            <button class="primary-btn" @click="showColumnConfig = false">确定</button>
          </div>
        </div>
      </div>
    </div>
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
