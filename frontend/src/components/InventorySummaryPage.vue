<script setup lang="ts">
import { t, formatUiDate } from '../i18n'
import { downloadXlsx } from '../utils/spreadsheet'
import UiIcon from './UiIcon.vue'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { fetchInventoryDetails, type InventoryDetailInfo } from '../api'
import TableConfigButton from './TableConfigButton.vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchAllPages } from '../utils/pagination'
import { resolveInventoryTimeRange } from '../utils/inventoryTimeRange'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

interface SummaryRow {
  key: string
  productCode: string
  productName: string
  productSpec: string
  productAttribute: string
  unitName: string
  inboundQty: number
  outboundQty: number
  netQty: number
  transactionCount: number
  lastOperationTime: string
}

const rows = ref<SummaryRow[]>([])
const sourceTotal = ref(0)
const loading = ref(false)
const beginLoad = useLatestRequest()
const sortKey = ref<keyof SummaryRow>('netQty')
const sortOrder = ref<'asc' | 'desc'>('desc')

const filterForm = reactive({
  productCode: '',
  productName: '',
  productSpec: '',
  productAttribute: '',
  timeRange: 'month',
  startTime: '',
  endTime: ''
})

const productAttributeOptions = ['自制', '外购', '委外']

const totals = computed(() => rows.value.reduce((acc, row) => {
  acc.inboundQty += row.inboundQty
  acc.outboundQty += row.outboundQty
  acc.netQty += row.netQty
  acc.transactionCount += row.transactionCount
  return acc
}, { inboundQty: 0, outboundQty: 0, netQty: 0, transactionCount: 0 }))

const sortedRows = computed(() => {
  const list = [...rows.value]
  return list.sort((a, b) => {
    const va = a[sortKey.value]
    const vb = b[sortKey.value]
    if (typeof va === 'number' && typeof vb === 'number') {
      return sortOrder.value === 'asc' ? va - vb : vb - va
    }
    const sa = String(va || '')
    const sb = String(vb || '')
    return sortOrder.value === 'asc' ? sa.localeCompare(sb, 'zh') : sb.localeCompare(sa, 'zh')
  })
})

async function loadData() {
  const isCurrent = beginLoad()
  loading.value = true
  try {
    const filter = {
      productCode: filterForm.productCode.trim() || undefined,
      productName: filterForm.productName.trim() || undefined,
      productSpec: filterForm.productSpec.trim() || undefined,
      productAttribute: filterForm.productAttribute || undefined,
      ...resolveInventoryTimeRange(filterForm)
    }
    const res = await fetchAllPages(params => fetchInventoryDetails(props.token, { ...filter, ...params }), 500, isCurrent)
    if (!isCurrent()) return
    sourceTotal.value = res.total
    rows.value = buildSummary(res.items)
  } catch (e: any) {
    if (isCurrent()) emit('notice', e.message || '加载进销存汇总失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

function buildSummary(items: InventoryDetailInfo[]) {
  const map = new Map<string, SummaryRow>()
  items.forEach(item => {
    const key = JSON.stringify([item.productCode, item.productName, item.productSpec, item.productAttribute, item.unitName])
    const row = map.get(key) || {
      key,
      productCode: item.productCode || '',
      productName: item.productName || '',
      productSpec: item.productSpec || '',
      productAttribute: item.productAttribute || '',
      unitName: item.unitName || '',
      inboundQty: 0,
      outboundQty: 0,
      netQty: 0,
      transactionCount: 0,
      lastOperationTime: ''
    }
    const qty = Math.abs(Number(item.changeQty || 0))
    if (item.sourceType === 'IN' || item.changeQty > 0) {
      row.inboundQty += qty
    } else {
      row.outboundQty += qty
    }
    row.netQty += Number(item.changeQty || 0)
    row.transactionCount += 1
    if (!row.lastOperationTime || String(item.operationTime || '').localeCompare(row.lastOperationTime) > 0) {
      row.lastOperationTime = item.operationTime || ''
    }
    map.set(key, row)
  })
  return Array.from(map.values())
}

function handleSearch() {
  loadData()
}

function handleReset() {
  filterForm.productCode = ''
  filterForm.productName = ''
  filterForm.productSpec = ''
  filterForm.productAttribute = ''
  filterForm.timeRange = 'month'
  filterForm.startTime = ''
  filterForm.endTime = ''
  loadData()
}

function toggleSort(key: keyof SummaryRow) {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortOrder.value = key === 'productCode' || key === 'productName' ? 'asc' : 'desc'
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
  if (!sortedRows.value.length) { emit('notice', '暂无数据可导出'); return }
  const token = props.token, epoch = exportEpoch
  const current = () => exportActive && !exportDisposed && props.token === token && exportEpoch === epoch
  const data = sortedRows.value.map((item, index) => [item.productCode, item.productName, item.productSpec, item.productAttribute, item.unitName, item.inboundQty, item.outboundQty, item.netQty, item.transactionCount, item.lastOperationTime])
  const headers = ['产品编号', '产品名称', '规格', '属性', '单位', '入库数量', '出库数量', '净变动', '收发次数', '最后收发时间'].map(label => t(label))
  exportError.value = ''; exporting.value = true
  try {
    const downloaded = await downloadXlsx(`${t('进销存汇总')}_${formatUiDate(new Date()).slice(0, 10)}.xlsx`, t('进销存汇总'), [headers, ...data], current)
    if (downloaded && current()) emit('notice', '导出Excel成功')
  } catch (error) { if (current()) { exportError.value = error instanceof Error ? error.message : '导出失败'; emit('notice', exportError.value) } }
  finally { if (current()) exporting.value = false }
}

function exportCSV() {
  if (exporting.value || loading.value) return
  if (sortedRows.value.length === 0) {
    emit('notice', '暂无数据可导出')
    return
  }
  const headers = ['产品编号', '产品名称', '规格', '属性', '单位', '入库数量', '出库数量', '净变动', '收发次数', '最后收发时间']
  const body = sortedRows.value.map(row => [
    row.productCode,
    row.productName,
    row.productSpec,
    row.productAttribute,
    row.unitName,
    row.inboundQty,
    row.outboundQty,
    row.netQty,
    row.transactionCount,
    row.lastOperationTime
  ].map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(','))
  const blob = new Blob(['\uFEFF' + [headers.join(','), ...body].join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `进销存汇总_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  emit('notice', '导出成功')
}

useRefreshOnActivated(loadData)
onMounted(loadData)
</script>

<template>
  <section class="content">
    <div class="filters inline-filters" style="margin-bottom: 8px">
      <div class="filter-group">
        <label>产品编号</label>
        <input v-model="filterForm.productCode" placeholder="请输入产品编号" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-group">
        <label>产品名称</label>
        <input v-model="filterForm.productName" placeholder="请输入产品名称" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-group">
        <label>产品规格</label>
        <input v-model="filterForm.productSpec" placeholder="请输入产品规格" @keyup.enter="handleSearch" />
      </div>
      <div class="filter-actions">
        <button class="primary-btn query-btn ui-icon-button" type="button" @click="handleSearch" title="查询库存汇总" aria-label="查询库存汇总"><UiIcon name="search" /></button>
        <button class="toolbar-btn ui-icon-button" type="button" @click="handleReset" title="重置条件" aria-label="重置条件">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
          </svg>
        </button>
      </div>
    </div>

    <div class="filters inline-filters" style="margin-bottom: 16px">
      <div class="filter-group">
        <label>产品属性</label>
        <select v-model="filterForm.productAttribute">
          <option value="">全部</option>
          <option v-for="item in productAttributeOptions" :key="item" :value="item">{{ item }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>收发时间</label>
        <select v-model="filterForm.timeRange" style="width: 140px">
          <option value="">全部</option>
          <option value="today">今天</option>
          <option value="week">本周</option>
          <option value="month">本月</option>
          <option value="custom">自定义</option>
        </select>
      </div>
      <div v-if="filterForm.timeRange === 'custom'" class="filter-group" style="display:flex; gap:8px; align-items:center;">
        <input type="date" v-model="filterForm.startTime" />
        <span style="color:#8a9693">-</span>
        <input type="date" v-model="filterForm.endTime" />
      </div>
    </div>

    <div class="summary-strip">
      <div>
        <span>入库数量</span>
        <strong class="positive">{{ totals.inboundQty }}</strong>
      </div>
      <div>
        <span>出库数量</span>
        <strong class="negative">{{ totals.outboundQty }}</strong>
      </div>
      <div>
        <span>净变动</span>
        <strong :class="totals.netQty < 0 ? 'negative' : 'positive'">{{ totals.netQty }}</strong>
      </div>
      <div>
        <span>产品数</span>
        <strong>{{ rows.length }}</strong>
      </div>
      <div>
        <span>明细数</span>
        <strong>{{ sourceTotal }}</strong>
      </div>
    </div>

    <div class="toolbar" style="margin-bottom:16px">
      <button class="toolbar-btn ui-action-button" type="button" :disabled="exporting || loading" @click="exportXlsx" title="导出Excel" aria-label="导出Excel"><UiIcon name="download" />Excel</button>
      <button class="toolbar-btn ui-action-button" type="button" :disabled="exporting || loading" @click="exportCSV" title="CSV备份" aria-label="CSV备份"><UiIcon name="archive" />CSV备份</button>
      <span class="export-scope-note">已加载 {{ sortedRows.length }} 条 · CSV仅用于备份，不宜直接用Excel打开</span>
      <p v-if="exportError" class="export-error" role="alert">{{ exportError }}</p>
      <TableConfigButton table-id="inventory-summary-table" />
    </div>

    <div class="table-container">
      <table id="inventory-summary-table" class="data-table">
        <thead>
          <tr>
            <th class="sortable" @click="toggleSort('productCode')">产品编号</th>
            <th class="sortable" @click="toggleSort('productName')">产品名称</th>
            <th>规格</th>
            <th>属性</th>
            <th>单位</th>
            <th class="sortable" @click="toggleSort('inboundQty')">入库数量</th>
            <th class="sortable" @click="toggleSort('outboundQty')">出库数量</th>
            <th class="sortable" @click="toggleSort('netQty')">净变动</th>
            <th class="sortable" @click="toggleSort('transactionCount')">收发次数</th>
            <th class="sortable" @click="toggleSort('lastOperationTime')">最后收发时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in sortedRows" :key="row.key">
            <td>{{ row.productCode || '-' }}</td>
            <td>{{ row.productName || '-' }}</td>
            <td>{{ row.productSpec || '-' }}</td>
            <td>{{ row.productAttribute || '-' }}</td>
            <td>{{ row.unitName || '-' }}</td>
            <td class="positive">{{ row.inboundQty }}</td>
            <td class="negative">{{ row.outboundQty }}</td>
            <td :class="row.netQty < 0 ? 'negative' : 'positive'">{{ row.netQty }}</td>
            <td>{{ row.transactionCount }}</td>
            <td>{{ row.lastOperationTime || '-' }}</td>
          </tr>
          <tr v-if="!loading && sortedRows.length === 0">
            <td colspan="10" style="text-align:center;color:#8a9693;padding:32px 0">暂无进销存汇总数据</td>
          </tr>
          <tr v-if="loading">
            <td colspan="10" style="text-align:center;color:#8a9693;padding:32px 0">加载中...</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 1px;
  margin: 0 0 16px;
  border: 1px solid #dce3e0;
  background: #dce3e0;
}
.summary-strip > div {
  min-height: 64px;
  padding: 10px 12px;
  background: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.summary-strip span {
  color: #586360;
  font-size: 12px;
}
.summary-strip strong {
  color: #1e2724;
  font-size: 20px;
  line-height: 1.4;
}
.positive {
  color: #00a37a;
  font-weight: 600;
}
.negative {
  color: #d84a4a;
  font-weight: 600;
}
.sortable {
  cursor: pointer;
  user-select: none;
}
.primary-stroke {
  background: white !important;
  color: #00b889 !important;
  border: 1px solid #00b889 !important;
}
@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
