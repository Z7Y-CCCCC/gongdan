<script setup lang="ts">
import { t, formatUiDate } from '../i18n'
import { parseCsvRows } from '../utils/csv'
import { downloadXlsx } from '../utils/spreadsheet'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { fetchProducts, fetchProductsExportBlob, fetchUserPreference, setUserPreference, type ProductInfo } from '../api'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import UiIcon from './UiIcon.vue'
import ColumnSettingsPanel, { type ColumnSetting } from './ColumnSettingsPanel.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{
  notice: [message: string]
  navigate: [menu: { main: string; sub: string }]
}>()

// ===== State =====
const products = ref<ProductInfo[]>([])
const totalProducts = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const beginLoad = useLatestRequest()

// Sorting State
const sortKey = ref('createdAt')
const sortOrder = ref<'asc' | 'desc'>('desc')

// Filter state
const filterForm = reactive({
  code: '',
  name: '',
  spec: ''
})

// Column configurations
const allColumns: ColumnSetting[] = [
  { key: 'inventoryQty', label: '库存数量', visible: true, width: 110, fixed: 'none' },
  { key: 'unitName', label: '单位', visible: true, width: 90, fixed: 'none' },
  { key: 'name', label: '产品名称', visible: true, width: 160, fixed: 'none' },
  { key: 'code', label: '产品编号', visible: true, width: 150, fixed: 'none' },
  { key: 'spec', label: '产品规格', visible: true, width: 150, fixed: 'none' },
  { key: 'routeName', label: '工艺路线', visible: true, width: 150, fixed: 'none' },
  { key: 'actions', label: '操作', visible: true, width: 160, fixed: 'right' }
]
const columns = ref<ColumnSetting[]>(allColumns.map(column => ({ ...column })))
const defaultColumnSettings = allColumns.map(column => ({ ...column }))
const visibleColumns = computed(() => columns.value.filter(column => column.visible))

// Modal visibility state
const showDetailModal = ref(false)
const activeDetail = ref<ProductInfo | null>(null)
const showColumnModal = ref(false)

// Checkbox selection state
const selectedIds = ref<number[]>([])

// ===== Computed Properties =====
const allSelected = computed(() => {
  return products.value.length > 0 && products.value.every(item => selectedIds.value.includes(item.id))
})

const totalPages = computed(() => Math.ceil(totalProducts.value / pageSize.value) || 1)

// Calculate total inventory qty for current page
const currentPageTotalQty = computed(() => {
  return sortedProducts.value.reduce((sum, item) => sum + (item.inventoryQty || 0), 0)
})

// Client-side sorting for responsiveness
const sortedProducts = computed(() => {
  const list = [...products.value]
  if (!sortKey.value) return list
  return list.sort((a, b) => {
    let valA = (a as any)[sortKey.value]
    let valB = (b as any)[sortKey.value]
    if (valA === undefined || valA === null) valA = ''
    if (valB === undefined || valB === null) valB = ''

    if (typeof valA === 'number' && typeof valB === 'number') {
      return sortOrder.value === 'asc' ? valA - valB : valB - valA
    }

    const strA = String(valA).toLowerCase()
    const strB = String(valB).toLowerCase()
    return sortOrder.value === 'asc' 
      ? strA.localeCompare(strB, 'zh') 
      : strB.localeCompare(strA, 'zh')
  })
})

// ===== Actions =====
async function loadData() {
  const isCurrent = beginLoad()
  loading.value = true
  try {
    const res = await fetchProducts(props.token, {
      code: filterForm.code.trim() || undefined,
      name: filterForm.name.trim() || undefined,
      spec: filterForm.spec.trim() || undefined,
      page: currentPage.value,
      size: pageSize.value
    })
    if (!isCurrent()) return
    products.value = res.items
    totalProducts.value = res.total
  } catch (e: any) {
    if (isCurrent()) emit('notice', e.message || '加载库存余额失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

async function loadPreferences() {
  try {
    const pref = await fetchUserPreference(props.token, 'INVENTORY_BALANCE_COLUMNS')
    if (pref && pref.value) {
      const saved = JSON.parse(pref.value)
      if (Array.isArray(saved) && saved.every(item => typeof item === 'string')) {
        const visibleKeys = new Set(saved)
        columns.value = allColumns.map(column => ({
          ...column,
          visible: column.key === 'actions' ? true : visibleKeys.has(column.key),
          locked: false
        }))
      } else if (Array.isArray(saved)) {
        const savedByKey = new Map(saved.map((column: ColumnSetting) => [column.key, column]))
        columns.value = allColumns.map(column => {
          const stored = savedByKey.get(column.key)
          const fixed = stored?.fixed === 'left' || stored?.fixed === 'right' || stored?.fixed === 'none'
            ? stored.fixed
            : column.fixed
          return { ...column, ...(stored || {}), fixed, locked: false }
        })
      }
    }
  } catch (e) {
    // Ignore
  }
}

async function handleSaveColumns(settings: ColumnSetting[]) {
  try {
    columns.value = settings.map(column => ({ ...column }))
    await setUserPreference(props.token, 'INVENTORY_BALANCE_COLUMNS', JSON.stringify(columns.value))
    showColumnModal.value = false
    emit('notice', '列配置已保存')
  } catch (e) {
    emit('notice', '配置保存失败')
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterForm.code = ''
  filterForm.name = ''
  filterForm.spec = ''
  currentPage.value = 1
  loadData()
}

function balanceColumnStyle(column: ColumnSetting, header = false) {
  const currentIndex = visibleColumns.value.findIndex(item => item.key === column.key)
  const left = 48 + visibleColumns.value
    .slice(0, currentIndex)
    .filter(item => item.fixed === 'left')
    .reduce((sum, item) => sum + item.width, 0)
  const right = visibleColumns.value
    .slice(currentIndex + 1)
    .filter(item => item.fixed === 'right')
    .reduce((sum, item) => sum + item.width, 0)
  const style: Record<string, string | number> = {
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

function toggleAll(checked: boolean) {
  selectedIds.value = checked ? products.value.map(item => item.id) : []
}

function toggleOne(id: number, checked: boolean) {
  selectedIds.value = checked
    ? Array.from(new Set([...selectedIds.value, id]))
    : selectedIds.value.filter(item => item !== id)
}

function toggleSort(key: string) {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortOrder.value = 'asc'
  }
}

function openDetail(item: ProductInfo) {
  activeDetail.value = item
  showDetailModal.value = true
}

function navigateToDetail(item: ProductInfo) {
  // Emit event to navigate to 'detail' (库存收发明细) tab
  emit('navigate', { main: 'inventory', sub: 'detail' })
}

const exporting = ref(false)
const exportError = ref('')
let exportEpoch = 0, exportDisposed = false, exportActive = true
watch(() => props.token, () => { exportEpoch++; exporting.value = false; exportError.value = '' }, { flush: 'sync' })
onActivated(() => { exportActive = true })
onDeactivated(() => { exportActive = false; exportEpoch++; exporting.value = false; exportError.value = '' })
onBeforeUnmount(() => { exportEpoch++; exportDisposed = true; exportActive = false })
async function exportXlsx() {
  if (exporting.value || !props.token || !exportActive) return
  const token = props.token, epoch = exportEpoch, filter = { ...filterForm }
  const current = () => exportActive && !exportDisposed && props.token === token && epoch === exportEpoch
  exportError.value = ''; exporting.value = true
  try {
    const blob = await fetchProductsExportBlob(token, filter)
    if (!current()) return
    const original = parseCsvRows(await blob.text(), { preserveWhitespace: true })
    const quantityColumn = original[0]?.indexOf('库存数量') ?? -1
    const matrix = original.map((row, rowIndex) => row.map((value, columnIndex) => {
      if (rowIndex === 0) return t(value)
      // Only this known numeric column may become a number; codes, whitespace and large values stay text.
      if (columnIndex === quantityColumn && /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$/.test(value)
        && value.replace(/[^0-9]/g, '').replace(/^0+/, '').length <= 15) {
        const quantity = Number(value)
        if (Number.isFinite(quantity) && Math.abs(quantity) < 1e15) return quantity
      }
      return value
    }))
    if (!current()) return
    const downloaded = await downloadXlsx(`${t('库存余额')}_${formatUiDate(new Date()).slice(0, 10)}.xlsx`, t('库存余额'), matrix, current)
    if (downloaded && current()) emit('notice', '导出Excel成功')
  } catch (error) { if (current()) { exportError.value = error instanceof Error ? error.message : '导出失败'; emit('notice', exportError.value) } }
  finally { if (current()) exporting.value = false }
}

async function handleExport() {
  if (exporting.value || !props.token || !exportActive) return
  const token = props.token, epoch = exportEpoch, filter = { ...filterForm }
  const current = () => exportActive && !exportDisposed && props.token === token && epoch === exportEpoch
  exporting.value = true
  try {
    const blob = await fetchProductsExportBlob(token, filter)
    if (!current()) return
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `库存余额_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    if (current()) emit('notice', '已导出 CSV 文件')
  } catch (e: any) {
    if (current()) emit('notice', e.message || '导出失败')
  } finally { if (current()) exporting.value = false }
}

useRefreshOnActivated(loadData)

onMounted(() => {
  loadData()
  loadPreferences()
})
</script>

<template>
  <section class="content">
    <!-- Category Tabs -->
    <div class="category-tabs">
      <button class="cat-tab active" type="button">全部</button>
    </div>

    <!-- Search Filters -->
    <div class="filters inline-filters" style="margin-bottom: 16px;">
      <div class="filter-group">
        <label>产品编号</label>
        <input v-model="filterForm.code" placeholder="请输入产品编号" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-group">
        <label>产品名称</label>
        <input v-model="filterForm.name" placeholder="请输入产品名称" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-group">
        <label>产品规格</label>
        <input v-model="filterForm.spec" placeholder="请输入产品规格" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-actions">
        <button class="primary-btn query-btn ui-icon-button" type="button" @click="handleSearch" title="查询库存结余" aria-label="查询库存结余"><UiIcon name="search" /></button>
        <button class="toolbar-btn ui-icon-button" type="button" @click="handleReset" title="重置条件" aria-label="重置条件">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
          </svg>
        </button>
      </div>
    </div>

    <!-- Action Toolbar -->
    <div class="toolbar" style="margin-bottom: 16px;">
      <button class="toolbar-btn primary-stroke ui-action-button" type="button" :disabled="exporting" @click="exportXlsx" title="导出Excel" aria-label="导出Excel"><UiIcon name="download" />Excel</button>
      <button class="toolbar-btn ui-action-button" type="button" :disabled="exporting" @click="handleExport" title="CSV备份" aria-label="CSV备份"><UiIcon name="archive" />CSV备份</button>
      <span class="export-scope-note">导出范围：当前筛选的全部记录 · CSV仅用于备份，不宜直接用Excel打开</span>
      <p v-if="exportError" class="export-error" role="alert">{{ exportError }}</p>

      <div style="margin-left: auto;">
        <button class="table-config-trigger ui-icon-button" type="button" title="字段配置" aria-label="字段配置" @click="showColumnModal = true">
          <UiIcon name="table-columns" :size="16" />
        </button>
      </div>
    </div>

    <!-- Data Table -->
    <div class="table-container">
      <table class="data-table inventory-balance-data-table">
        <thead>
          <tr>
            <th style="width:48px">
              <input type="checkbox" :checked="allSelected" @change="toggleAll(($event.target as HTMLInputElement).checked)" />
            </th>
            <th
              v-for="column in visibleColumns"
              :key="column.key"
              :class="{ sortable: column.key !== 'actions' }"
              :style="balanceColumnStyle(column, true)"
              @click="column.key !== 'actions' && toggleSort(column.key)"
            >
              {{ column.label }}
              <span v-if="column.key !== 'actions'" class="sort-icon" :class="sortKey === column.key ? sortOrder : ''"></span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in sortedProducts" :key="item.id">
            <td>
              <input type="checkbox" :checked="selectedIds.includes(item.id)" @change="toggleOne(item.id, ($event.target as HTMLInputElement).checked)" />
            </td>
            <template v-for="column in visibleColumns" :key="column.key">
              <td v-if="column.key === 'inventoryQty'" :style="balanceColumnStyle(column)">{{ item.inventoryQty }}</td>
              <td v-else-if="column.key === 'unitName'" :style="balanceColumnStyle(column)">{{ item.unitName || '-' }}</td>
              <td v-else-if="column.key === 'name'" :style="balanceColumnStyle(column)">{{ item.name }}</td>
              <td v-else-if="column.key === 'code'" :style="balanceColumnStyle(column)">{{ item.code }}</td>
              <td v-else-if="column.key === 'spec'" :style="balanceColumnStyle(column)">{{ item.spec || '-' }}</td>
              <td v-else-if="column.key === 'routeName'" :style="balanceColumnStyle(column)">{{ item.routeName || '-' }}</td>
              <td v-else-if="column.key === 'actions'" class="action-cell" :style="balanceColumnStyle(column)">
                <button class="action-link ui-icon-button" type="button" @click="openDetail(item)" title="查看库存详情" aria-label="查看库存详情"><UiIcon name="eye" /></button>
                <button class="action-link ui-action-button" type="button" @click="navigateToDetail(item)" title="收发明细" aria-label="收发明细"><UiIcon name="list" /><span>收发明细</span></button>
              </td>
            </template>
          </tr>
          <tr v-if="sortedProducts.length === 0">
            <td :colspan="visibleColumns.length + 1" style="text-align: center; color: #8a9693; padding: 32px 0;">
              暂无匹配的库存余额数据
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr style="background: #fdfdfd; font-weight: bold;">
            <td></td>
            <template v-for="column in visibleColumns" :key="column.key">
              <td v-if="column.key === 'inventoryQty'" :style="balanceColumnStyle(column, true)">当前页合计: {{ currentPageTotalQty }}</td>
              <td v-else :style="balanceColumnStyle(column, true)">-</td>
            </template>
          </tr>
        </tfoot>
      </table>
    </div>

    <!-- Pagination -->
    <div class="pagination" v-if="totalProducts > 0" style="margin-top: 16px;">
      <span class="page-info">共{{ totalProducts }}条结果</span>
      <div class="page-controls">
        <button :disabled="currentPage <= 1" @click="currentPage--; loadData()" class="ui-icon-button" title="上一页" aria-label="上一页" type="button"><UiIcon name="arrowLeft" /></button>
        <button class="active">{{ currentPage }}</button>
        <button :disabled="currentPage >= totalPages" @click="currentPage++; loadData()" class="ui-icon-button" title="下一页" aria-label="下一页" type="button"><UiIcon name="arrowRight" /></button>
      </div>
      <select class="page-size-select" v-model="pageSize" @change="currentPage = 1; loadData()">
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
        <option :value="100">100 条/页</option>
      </select>
    </div>

    <!-- Detail Modal -->
    <div v-if="showDetailModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 600px;">
        <div class="modal-header">
          <h2>产品库存详情</h2>
          <button class="close-btn ui-icon-button" type="button" @click="showDetailModal = false" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
        </div>
        <div style="padding: 24px; max-height: 480px; overflow-y: auto;">
          <div class="detail-grid">
            <div class="detail-item"><span class="label">产品编号:</span><span class="value">{{ activeDetail?.code || '-' }}</span></div>
            <div class="detail-item"><span class="label">产品名称:</span><span class="value">{{ activeDetail?.name || '-' }}</span></div>
            <div class="detail-item"><span class="label">单位:</span><span class="value">{{ activeDetail?.unitName || '-' }}</span></div>
            <div class="detail-item"><span class="label">产品规格:</span><span class="value">{{ activeDetail?.spec || '-' }}</span></div>
            <div class="detail-item"><span class="label">属性:</span><span class="value">{{ activeDetail?.attribute || '-' }}</span></div>
            <div class="detail-item"><span class="label">工艺路线:</span><span class="value">{{ activeDetail?.routeName || '-' }}</span></div>
            <div class="detail-item"><span class="label">制造方式:</span><span class="value">{{ activeDetail?.manufactureMode || '-' }}</span></div>
            <div class="detail-item"><span class="label">安全库存:</span><span class="value">{{ activeDetail?.safetyStock || '0' }}</span></div>
            <div class="detail-item"><span class="label">最小库存:</span><span class="value">{{ activeDetail?.minStock || '0' }}</span></div>
            <div class="detail-item"><span class="label">最大库存:</span><span class="value">{{ activeDetail?.maxStock || '0' }}</span></div>
            <div class="detail-item"><span class="label">成本价:</span><span class="value">¥{{ activeDetail?.costPrice || '0.00' }}</span></div>
            <div class="detail-item"><span class="label">销售价:</span><span class="value">¥{{ activeDetail?.salePrice || '0.00' }}</span></div>
            <div class="detail-item"><span class="label">供应商:</span><span class="value">{{ activeDetail?.supplierName || '-' }}</span></div>
            <div class="detail-item"><span class="label">客户名称:</span><span class="value">{{ activeDetail?.customerName || '-' }}</span></div>
            <div class="detail-item"><span class="label">产品类型:</span><span class="value">{{ activeDetail?.productType || '-' }}</span></div>
            <div class="detail-item"><span class="label">当前库存:</span><span class="value" style="color: #00b889; font-weight: bold;">{{ activeDetail?.inventoryQty || 0 }}</span></div>
            <div class="detail-item"><span class="label">创建人:</span><span class="value">{{ activeDetail?.creator || '-' }}</span></div>
            <div class="detail-item"><span class="label">创建时间:</span><span class="value">{{ activeDetail?.createdAt || '-' }}</span></div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="primary-btn wide ui-icon-button" type="button" @click="showDetailModal = false" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
        </div>
      </div>
    </div>

    <ColumnSettingsPanel
      v-if="showColumnModal"
      v-model="columns"
      :defaults="defaultColumnSettings"
      title="列设置"
      @confirm="handleSaveColumns"
      @cancel="showColumnModal = false"
    />
  </section>
</template>

<style scoped>
.sortable {
  cursor: pointer;
  user-select: none;
}
.sort-icon {
  display: inline-block;
  width: 0;
  height: 0;
  margin-left: 4px;
  vertical-align: middle;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-bottom: 4px solid #8a9693;
  opacity: 0.3;
  transition: transform var(--motion-base), opacity 0.2s;
}
.sort-icon.asc {
  transform: rotate(0deg);
  opacity: 1;
  border-bottom-color: #00b889;
}
.sort-icon.desc {
  transform: rotate(180deg);
  opacity: 1;
  border-bottom-color: #00b889;
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
  line-height: 20px;
}
.page-size-select:hover {
  border-color: #00b889;
}

.primary-stroke {
  background: white !important;
  color: #00b889 !important;
  border: 1px solid #00b889 !important;
}
.primary-stroke:hover {
  background: #f4fbf9 !important;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.detail-item {
  display: flex;
  font-size: 14px;
  line-height: 1.5;
}
.detail-item .label {
  color: #8a9693;
  width: 90px;
  flex-shrink: 0;
}
.detail-item .value {
  color: #1e2724;
  word-break: break-all;
}
</style>
