<script setup lang="ts">
import { computed, onMounted, reactive, ref, type CSSProperties } from 'vue'
import {
  createOutboundReceipt,
  deleteOutboundReceipt,
  fetchOutboundReceipts,
  fetchProducts,
  fetchCustomFields,
  type OutboundReceiptInfo,
  type OutboundReceiptItemPayload,
  type ProductInfo
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'
import UiIcon from '@/components/UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'
import ColumnSettingsPanel, { type ColumnSetting } from './ColumnSettingsPanel.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible: deleteVisible, title: deleteTitle, message: deleteMessage, loading: deleteLoading, confirm: deleteConfirm, handleConfirm: deleteHandleConfirm, handleCancel: deleteHandleCancel } = useDeleteConfirm()

// ===== State =====
const view = ref<'list' | 'create'>('list')
const receipts = ref<OutboundReceiptInfo[]>([])
const products = ref<ProductInfo[]>([])
const totalReceipts = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const startTimeFocused = ref(false)
const endTimeFocused = ref(false)

// Filter state
const filterForm = reactive({
  code: '',
  outboundType: '',
  timeRange: '',
  startTime: '',
  endTime: ''
})

// Create form state
const outboundTypes = ['普通出库', '销售出库', '生产出库', '调拨出库', '采购退货出库', '其它出库']

const customFields = ref<any[]>([])
const itemCustomFields = ref<any[]>([])

const createForm = reactive({
  code: '',
  outboundType: '普通出库',
  outboundTime: formatDateTime(new Date()),
  remark: '',
  customValues: [] as any[]
})

const formItems = ref<Array<OutboundReceiptItemPayload & { productCode?: string; productName?: string; productSpec?: string; productAttribute?: string; unitName?: string; minStock?: string; maxStock?: string; safetyStock?: string; inventoryQty?: number; customValues?: any[] }>>([])

// Add product modal
const showAddProduct = ref(false)
const addProductForm = reactive({
  productId: null as number | null,
  quantity: 1
})

const productSearchQuery = ref('')
const productDropdownOpen = ref(false)

// Column config
interface ReceiptTableColumn extends ColumnSetting {
  isCustom?: boolean
  fieldId?: number
  field?: any
}

const allColumns: ReceiptTableColumn[] = [
  { key: 'index', label: '序号', visible: true, width: 70, fixed: 'left' },
  { key: 'code', label: '出库单号', visible: true, width: 180, fixed: 'none' },
  { key: 'outboundType', label: '出库类型', visible: true, width: 140, fixed: 'none' },
  { key: 'outboundTime', label: '出库时间', visible: true, width: 160, fixed: 'none' },
  { key: 'remark', label: '备注', visible: true, width: 180, fixed: 'none' },
  { key: 'createdAt', label: '创建时间', visible: true, width: 160, fixed: 'none' },
  { key: 'creator', label: '创建人', visible: true, width: 120, fixed: 'none' },
  { key: 'actions', label: '操作', visible: true, width: 120, fixed: 'right' }
]
const columns = ref<ReceiptTableColumn[]>(allColumns.map(c => ({ ...c })))
const defaultColumnSettings = ref<ReceiptTableColumn[]>(allColumns.map(c => ({ ...c })))
const showColumnConfig = ref(false)

// Detail columns
const detailColumns: ReceiptTableColumn[] = [
  { key: 'index', label: '序号', visible: true, width: 70, fixed: 'left' },
  { key: 'productCode', label: '产品编号', visible: true, width: 140, fixed: 'none' },
  { key: 'productName', label: '产品名称', visible: true, width: 150, fixed: 'none' },
  { key: 'productSpec', label: '产品规格', visible: true, width: 150, fixed: 'none' },
  { key: 'productAttribute', label: '产品属性', visible: true, width: 140, fixed: 'none' },
  { key: 'unitName', label: '库存单位', visible: true, width: 100, fixed: 'none' },
  { key: 'minStock', label: '最小库存', visible: true, width: 110, fixed: 'none' },
  { key: 'maxStock', label: '最大库存', visible: true, width: 110, fixed: 'none' },
  { key: 'safetyStock', label: '安全库存', visible: true, width: 110, fixed: 'none' },
  { key: 'inventoryQty', label: '当前库存数量', visible: true, width: 140, fixed: 'none' },
  { key: 'quantity', label: '出库数量', visible: true, width: 120, fixed: 'none' },
  { key: 'actions', label: '操作', visible: true, width: 120, fixed: 'right' }
]
const itemColumns = ref<ReceiptTableColumn[]>(detailColumns.map(c => ({ ...c })))
const defaultItemColumnSettings = ref<ReceiptTableColumn[]>(detailColumns.map(c => ({ ...c })))
const showItemColumnConfig = ref(false)

// Detail receipt modal
const showDetailModal = ref(false)
const activeReceipt = ref<OutboundReceiptInfo | null>(null)

// ===== Computed =====
const visibleColumns = computed(() => columns.value.filter(c => c.visible))
const visibleItemColumns = computed(() => itemColumns.value.filter(c => c.visible))
const totalPages = computed(() => Math.ceil(totalReceipts.value / pageSize.value) || 1)

function outboundColumnStyle(column: ReceiptTableColumn, visible: ReceiptTableColumn[], header = false): CSSProperties {
  const currentIndex = visible.findIndex(item => item.key === column.key)
  const left = visible
    .slice(0, currentIndex)
    .filter(item => item.fixed === 'left')
    .reduce((sum, item) => sum + item.width, 0)
  const right = visible
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

const selectedProduct = computed(() => {
  if (!addProductForm.productId) return null
  return products.value.find(p => p.id === addProductForm.productId) || null
})

// Filtered products for dropdown search helper
const filteredProducts = computed(() => {
  if (!productSearchQuery.value) return products.value
  const q = productSearchQuery.value.toLowerCase()
  return products.value.filter(p => 
    p.code.toLowerCase().includes(q) || 
    p.name.toLowerCase().includes(q) || 
    (p.spec && p.spec.toLowerCase().includes(q))
  )
})

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

async function loadCustomFieldConfigs() {
  try {
    const resHeader = await fetchCustomFields(props.token, 'receipt')
    customFields.value = resHeader.items
    
    const resDetail = await fetchCustomFields(props.token, 'receipt_detail')
    itemCustomFields.value = resDetail.items
    
    // 合并单头自定义字段到 columns
    const baseCols = allColumns.map(c => ({ ...c }))
    const customCols: ReceiptTableColumn[] = customFields.value.map(f => ({
      key: `custom_${f.id}`,
      fieldId: f.id,
      label: f.name,
      visible: true,
      width: 140,
      fixed: 'none',
      isCustom: true,
      field: f
    }))
    const actionsIdx = baseCols.findIndex(c => c.key === 'actions')
    if (actionsIdx !== -1) {
      baseCols.splice(actionsIdx, 0, ...customCols)
    } else {
      baseCols.push(...customCols)
    }
    columns.value = baseCols
    defaultColumnSettings.value = baseCols.map(c => ({ ...c }))

    // 合并明细自定义字段到 itemColumns
    const baseItemCols = detailColumns.map(c => ({ ...c }))
    const customItemCols: ReceiptTableColumn[] = itemCustomFields.value.map(f => ({
      key: `custom_${f.id}`,
      fieldId: f.id,
      label: f.name,
      visible: true,
      width: 140,
      fixed: 'none',
      isCustom: true,
      field: f
    }))
    const itemActionsIdx = baseItemCols.findIndex(c => c.key === 'actions')
    if (itemActionsIdx !== -1) {
      baseItemCols.splice(itemActionsIdx, 0, ...customItemCols)
    } else {
      baseItemCols.push(...customItemCols)
    }
    itemColumns.value = baseItemCols
    defaultItemColumnSettings.value = baseItemCols.map(c => ({ ...c }))

    // 初始化 createForm.customValues
    createForm.customValues = customFields.value.map(f => ({
      fieldId: f.id,
      fieldName: f.name,
      fieldType: f.fieldType,
      value: f.fieldType === 'multiple_select' 
        ? (f.defaultValues || []) 
        : (f.defaultValues && f.defaultValues.length > 0 
            ? (f.fieldType === 'number' ? Number(f.defaultValues[0]) : f.defaultValues[0]) 
            : (f.fieldType === 'number' ? null : ''))
    }))
  } catch (e: any) {
    emit('notice', e.message || '加载自定义字段配置失败')
  }
}

function getReceiptCustomValue(receipt: any, fieldId: number | undefined) {
  if (fieldId === undefined || !receipt || !receipt.customValues) return '-'
  const cv = receipt.customValues.find((v: any) => v.fieldId === fieldId)
  if (!cv) return '-'
  if (Array.isArray(cv.value)) return cv.value.join(', ')
  return cv.value ?? '-'
}

function getItemCustomValueObj(item: any, fieldId: number | undefined) {
  if (fieldId === undefined || !item || !item.customValues) return null
  return item.customValues.find((v: any) => v.fieldId === fieldId) || null
}

function getDetailItemCustomValue(item: any, fieldId: number | undefined) {
  if (fieldId === undefined || !item || !item.customValues) return '-'
  const cv = item.customValues.find((v: any) => v.fieldId === fieldId)
  if (!cv) return '-'
  if (Array.isArray(cv.value)) return cv.value.join(', ')
  return cv.value ?? '-'
}

async function loadReceipts() {
  loading.value = true
  try {
    let start = undefined
    let end = undefined
    if (filterForm.timeRange === 'custom') {
      if (filterForm.startTime) start = filterForm.startTime + ' 00:00:00'
      if (filterForm.endTime) end = filterForm.endTime + ' 23:59:59'
    } else if (filterForm.timeRange === 'today') {
      const today = new Date()
      start = formatDateTime(new Date(today.setHours(0, 0, 0, 0)))
      end = formatDateTime(new Date(today.setHours(23, 59, 59, 999)))
    } else if (filterForm.timeRange === 'week') {
      const today = new Date()
      const day = today.getDay() || 7
      const monday = new Date(today.setDate(today.getDate() - day + 1))
      start = formatDateTime(new Date(monday.setHours(0, 0, 0, 0)))
      end = formatDateTime(new Date())
    } else if (filterForm.timeRange === 'month') {
      const today = new Date()
      start = formatDateTime(new Date(today.getFullYear(), today.getMonth(), 1, 0, 0, 0))
      end = formatDateTime(new Date())
    }

    const res = await fetchOutboundReceipts(props.token, {
      code: filterForm.code.trim() || undefined,
      outboundType: filterForm.outboundType || undefined,
      startTime: start,
      endTime: end,
      page: currentPage.value,
      size: pageSize.value
    })
    receipts.value = res.items
    totalReceipts.value = res.total
  } catch (e: any) {
    emit('notice', e.message || '加载出库单失败')
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  try {
    const res = await fetchProducts(props.token, { page: 1, size: 1000 })
    products.value = res.items
  } catch (e) {
    // ignore
  }
}

function handleSearch() {
  currentPage.value = 1
  loadReceipts()
}

function handleReset() {
  filterForm.code = ''
  filterForm.outboundType = ''
  filterForm.timeRange = ''
  filterForm.startTime = ''
  filterForm.endTime = ''
  currentPage.value = 1
  loadReceipts()
}

function saveColumnSettings(settings: ReceiptTableColumn[]) {
  columns.value = settings.map(column => ({ ...column }))
  showColumnConfig.value = false
  emit('notice', '列配置已保存')
}

function saveItemColumnSettings(settings: ReceiptTableColumn[]) {
  itemColumns.value = settings.map(column => ({ ...column }))
  showItemColumnConfig.value = false
  emit('notice', '列配置已保存')
}

function goPage(p: number) {
  if (p >= 1 && p <= totalPages.value) {
    currentPage.value = p
    loadReceipts()
  }
}

function handleCreate() {
  createForm.code = ''
  createForm.outboundType = '普通出库'
  createForm.outboundTime = formatDateTime(new Date())
  createForm.remark = ''
  createForm.customValues = customFields.value.map(f => ({
    fieldId: f.id,
    fieldName: f.name,
    fieldType: f.fieldType,
    value: f.fieldType === 'multiple_select' 
      ? (f.defaultValues || []) 
      : (f.defaultValues && f.defaultValues.length > 0 
          ? (f.fieldType === 'number' ? Number(f.defaultValues[0]) : f.defaultValues[0]) 
          : (f.fieldType === 'number' ? null : ''))
  }))
  formItems.value = []
  view.value = 'create'
}

function cancelCreate() {
  view.value = 'list'
}

function openAddProduct() {
  addProductForm.productId = null
  addProductForm.quantity = 1
  productSearchQuery.value = ''
  productDropdownOpen.value = false
  showAddProduct.value = true
}

function selectProductFromHelper(product: ProductInfo) {
  addProductForm.productId = product.id
  productSearchQuery.value = product.name
}

function addProductToItems() {
  if (!addProductForm.productId) {
    emit('notice', '请选择产品')
    return
  }
  if (addProductForm.quantity <= 0) {
    emit('notice', '数量必须大于 0')
    return
  }
  const product = products.value.find(p => p.id === addProductForm.productId)
  if (!product) return

  const currentStock = product.inventoryQty || 0
  if (addProductForm.quantity > currentStock) {
    emit('notice', `当前库存不足 (剩余: ${currentStock})`)
  }

  const existing = formItems.value.find(item => item.productId === addProductForm.productId)
  if (existing) {
    existing.quantity += addProductForm.quantity
  } else {
    formItems.value.push({
      productId: product.id,
      productCode: product.code,
      productName: product.name,
      productSpec: product.spec,
      productAttribute: product.attribute,
      unitName: product.unitName,
      minStock: String(product.minStock || 0),
      maxStock: String(product.maxStock || 0),
      safetyStock: String(product.safetyStock || 0),
      inventoryQty: product.inventoryQty || 0,
      quantity: addProductForm.quantity,
      customValues: itemCustomFields.value.map(f => ({
        fieldId: f.id,
        fieldName: f.name,
        fieldType: f.fieldType,
        value: f.fieldType === 'multiple_select' ? [] : (f.fieldType === 'number' ? null : '')
      }))
    })
  }
  showAddProduct.value = false
}

function removeFormItem(index: number) {
  formItems.value.splice(index, 1)
}

async function submitCreate() {
  if (formItems.value.length === 0) {
    emit('notice', '请至少添加一个产品')
    return
  }

  // Frontend stock check validation
  for (const item of formItems.value) {
    if (item.quantity <= 0) {
      emit('notice', '出库数量必须大于 0')
      return
    }
    const currentStock = item.inventoryQty || 0
    if (item.quantity > currentStock) {
      emit('notice', `产品 [${item.productName}] 库存不足，当前库存为 ${currentStock}，无法提交`)
      return
    }
  }

  try {
    const payload = {
      code: createForm.code.trim() || undefined,
      outboundType: createForm.outboundType,
      outboundTime: createForm.outboundTime,
      remark: createForm.remark.trim() || undefined,
      customValues: createForm.customValues.map(cv => ({
        fieldId: cv.fieldId,
        fieldName: cv.fieldName,
        fieldType: cv.fieldType,
        value: Array.isArray(cv.value) ? cv.value : (cv.value === null || cv.value === undefined ? null : cv.value)
      })),
      items: formItems.value.map(item => ({
        productId: item.productId,
        quantity: item.quantity,
        customValues: item.customValues ? item.customValues.map(cv => ({
          fieldId: cv.fieldId,
          fieldName: cv.fieldName,
          fieldType: cv.fieldType,
          value: Array.isArray(cv.value) ? cv.value : (cv.value === null || cv.value === undefined ? null : cv.value)
        })) : []
      }))
    }
    await createOutboundReceipt(props.token, payload)
    emit('notice', '创建出库单成功')
    view.value = 'list'
    currentPage.value = 1
    loadReceipts()
  } catch (e: any) {
    emit('notice', e.message || '创建出库单失败')
  }
}

function handleDelete(receipt: OutboundReceiptInfo) {
  deleteConfirm({
    title: '删除出库单',
    message: `确定要删除出库单【${receipt.code}】吗？删除后对应的产品库存将原样增加还原。`
  }).then(async (confirmed) => {
    if (!confirmed) return
    try {
      deleteLoading.value = true
      await deleteOutboundReceipt(props.token, receipt.id)
      emit('notice', '出库单已成功删除，产品库存已还原')
      loadReceipts()
    } catch (e: any) {
      emit('notice', e.message || '删除出库单失败')
    } finally {
      deleteLoading.value = false
      deleteVisible.value = false
    }
  })
}

function showReceiptDetail(receipt: OutboundReceiptInfo) {
  activeReceipt.value = receipt
  showDetailModal.value = true
}

onMounted(() => {
  loadCustomFieldConfigs()
  loadReceipts()
  loadProducts()
})
</script>

<template>
  <section class="content">
    <!-- List View -->
    <template v-if="view === 'list'">
      <!-- Search Filters -->
      <div class="filters inline-filters" style="margin-bottom:16px">
        <div class="filter-group">
          <label>出库单号</label>
          <input v-model="filterForm.code" placeholder="请输入出库单号" @keyup.enter="handleSearch" />
        </div>
        <div class="filter-group">
          <label>出库类型</label>
          <select v-model="filterForm.outboundType">
            <option value="">全部</option>
            <option v-for="t in outboundTypes" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="filter-group">
          <label>出库时间</label>
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
          <span style="color:#8a9693">至</span>
          <input
            type="date"
            v-model="filterForm.endTime"
            :class="{ focused: endTimeFocused }"
            @focus="endTimeFocused = true"
            @blur="endTimeFocused = false"
          />
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
        </div>
      </div>

      <!-- Action Toolbar -->
      <div class="toolbar" style="margin-bottom:16px">
        <button class="primary-btn" type="button" @click="handleCreate">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          创建出库单
        </button>

        <button class="toolbar-btn" type="button" disabled style="color:#c9cfcd; cursor:not-allowed">
          <UiIcon name="layers" />
          批量操作
        </button>

        <div style="margin-left: auto; display:flex; gap: 8px;">
          <button class="table-config-trigger" type="button" title="字段配置" aria-label="字段配置" @click="showColumnConfig = true">
            <UiIcon name="table-columns" :size="16" />
          </button>
        </div>
      </div>

      <!-- Data Table -->
      <div class="table-container">
        <table class="data-table outbound-receipt-data-table">
          <thead>
            <tr>
              <th v-for="col in visibleColumns" :key="col.key" :style="outboundColumnStyle(col, visibleColumns, true)">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in receipts" :key="item.id">
              <template v-for="col in visibleColumns" :key="col.key">
                <td v-if="col.key === 'index'" :style="outboundColumnStyle(col, visibleColumns)">{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
                <td v-else-if="col.key === 'code'" :style="outboundColumnStyle(col, visibleColumns)">{{ item.code }}</td>
                <td v-else-if="col.key === 'outboundType'" :style="outboundColumnStyle(col, visibleColumns)">
                  <span class="badge outbound-badge">{{ item.outboundType }}</span>
                </td>
                <td v-else-if="col.key === 'outboundTime'" :style="outboundColumnStyle(col, visibleColumns)">{{ item.outboundTime }}</td>
                <td v-else-if="col.key === 'remark'" :style="outboundColumnStyle(col, visibleColumns)" :title="item.remark">
                  {{ item.remark || '-' }}
                </td>
                <td v-else-if="col.key === 'createdAt'" :style="outboundColumnStyle(col, visibleColumns)">{{ item.createdAt }}</td>
                <td v-else-if="col.key === 'creator'" :style="outboundColumnStyle(col, visibleColumns)">{{ item.creator || '-' }}</td>
                <td v-else-if="col.key === 'actions'" class="action-cell" :style="outboundColumnStyle(col, visibleColumns)">
                  <a class="link-btn" style="font-size:12px;cursor:pointer;margin-right:12px" @click="showReceiptDetail(item)">详情</a>
                  <a class="link-btn" style="font-size:12px;cursor:pointer;color:#ff5a5a" @click="handleDelete(item)">删除</a>
                </td>
                <td v-else-if="col.isCustom" :style="outboundColumnStyle(col, visibleColumns)">
                  {{ getReceiptCustomValue(item, col.fieldId) }}
                </td>
              </template>
            </tr>
            <tr v-if="receipts.length === 0">
              <td :colspan="visibleColumns.length" style="text-align: center; color: #8a9693; padding: 32px 0;">
                暂无出库单数据
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="pagination" v-if="totalReceipts > 0" style="margin-top:16px">
        <span class="page-info">共{{ totalReceipts }}条结果</span>
        <div class="page-controls">
          <button :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">&lt;</button>
          <button class="active">{{ currentPage }}</button>
          <button :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">&gt;</button>
        </div>
        <select class="page-size-select" v-model="pageSize" @change="currentPage = 1; loadReceipts()">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
      </div>
    </template>

    <!-- Create View -->
    <template v-if="view === 'create'">
      <h3 style="margin:0 0 24px;font-size:16px;font-weight:600">创建出库单</h3>

      <div class="form-section">
        <div class="form-row">
          <label class="form-label">出库单号</label>
          <input class="form-input" v-model="createForm.code" placeholder="请输入，留空将自动生成" />
        </div>
        <div class="form-row">
          <label class="form-label required">出库类型</label>
          <select class="form-input" v-model="createForm.outboundType">
            <option v-for="t in outboundTypes" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="form-row">
          <label class="form-label required">出库时间</label>
          <input class="form-input" type="datetime-local" v-model="createForm.outboundTime" step="1" />
        </div>
        <div class="form-row">
          <label class="form-label">备注</label>
          <input class="form-input" v-model="createForm.remark" placeholder="请输入出库备注" />
        </div>

        <!-- 自定义单头字段 -->
        <div v-for="(field, fIdx) in customFields" :key="field.id" class="form-row">
          <label class="form-label" :class="{ required: field.required }">{{ field.name }}</label>
          
          <select 
            v-if="field.fieldType === 'single_select' && createForm.customValues[fIdx]" 
            class="form-input" 
            v-model="createForm.customValues[fIdx].value"
          >
            <option value="">请选择</option>
            <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          
          <div v-else-if="field.fieldType === 'multiple_select' && createForm.customValues[fIdx]" style="display:flex; flex-wrap:wrap; gap:8px; align-items:center; min-height:34px;">
            <label v-for="opt in field.options" :key="opt.value" style="display:flex; align-items:center; gap:4px; font-size:13px; cursor:pointer">
              <input type="checkbox" :value="opt.value" v-model="createForm.customValues[fIdx].value" style="accent-color:var(--accent)" />
              {{ opt.label }}
            </label>
          </div>
          
          <input 
            v-else-if="field.fieldType === 'number' && createForm.customValues[fIdx]" 
            class="form-input" 
            type="number" 
            v-model.number="createForm.customValues[fIdx].value" 
            :placeholder="field.placeholder || '请输入数字'" 
          />
          
          <input 
            v-else-if="field.fieldType === 'date' && createForm.customValues[fIdx]" 
            class="form-input" 
            type="date" 
            v-model="createForm.customValues[fIdx].value" 
          />
          
          <input 
            v-else-if="createForm.customValues[fIdx]" 
            class="form-input" 
            v-model="createForm.customValues[fIdx].value" 
            :placeholder="field.placeholder || '请输入'" 
          />
        </div>
      </div>

      <div style="border-top:1px solid #dce3e0; margin:24px 0"></div>

      <!-- Outbound Items -->
      <h4 style="margin:0 0 16px;font-size:14px;font-weight:600">出库明细</h4>

      <div class="toolbar" style="margin-bottom:16px">
        <button class="primary-btn" type="button" @click="openAddProduct">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          添加产品
        </button>

        <div style="margin-left: auto">
          <button class="table-config-trigger" type="button" title="字段配置" aria-label="字段配置" @click="showItemColumnConfig = true">
            <UiIcon name="table-columns" :size="16" />
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table outbound-receipt-data-table">
          <thead>
            <tr>
              <th v-for="col in visibleItemColumns" :key="col.key" :style="outboundColumnStyle(col, visibleItemColumns, true)">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in formItems" :key="item.productId">
              <template v-for="col in visibleItemColumns" :key="col.key">
                <td v-if="col.key === 'index'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ idx + 1 }}</td>
                <td v-else-if="col.key === 'productCode'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.productCode }}</td>
                <td v-else-if="col.key === 'productName'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.productName }}</td>
                <td v-else-if="col.key === 'productSpec'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.productSpec || '-' }}</td>
                <td v-else-if="col.key === 'productAttribute'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.productAttribute || '-' }}</td>
                <td v-else-if="col.key === 'unitName'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.unitName || '-' }}</td>
                <td v-else-if="col.key === 'minStock'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.minStock }}</td>
                <td v-else-if="col.key === 'maxStock'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.maxStock }}</td>
                <td v-else-if="col.key === 'safetyStock'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.safetyStock }}</td>
                <td v-else-if="col.key === 'inventoryQty'" :style="outboundColumnStyle(col, visibleItemColumns)">{{ item.inventoryQty }}</td>
                <td v-else-if="col.key === 'quantity'" :style="outboundColumnStyle(col, visibleItemColumns)">
                  <input
                    type="number"
                    v-model.number="item.quantity"
                    class="form-input qty-input"
                    :class="{ 'has-error': item.quantity > (item.inventoryQty || 0) }"
                    style="width: 80px; text-align:center; height:28px; padding:2px 4px;"
                    min="1"
                  />
                  <span v-if="item.quantity > (item.inventoryQty || 0)" class="inline-err">不足</span>
                </td>
                <td v-else-if="col.key === 'actions'" class="action-cell" :style="outboundColumnStyle(col, visibleItemColumns)">
                  <a class="link-btn" style="font-size:12px;cursor:pointer;color:#ff5a5a" @click="removeFormItem(idx)">删除</a>
                </td>
                <td v-else-if="col.isCustom" :style="outboundColumnStyle(col, visibleItemColumns)">
                  <template v-if="getItemCustomValueObj(item, col.fieldId)">
                    <select 
                      v-if="col.field.fieldType === 'single_select'" 
                      v-model="getItemCustomValueObj(item, col.fieldId).value" 
                      class="form-input table-input-sm"
                      style="width: 100px; padding: 2px 4px; height: 28px;"
                    >
                      <option value="">请选择</option>
                      <option v-for="opt in col.field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </select>
                    
                    <div v-else-if="col.field.fieldType === 'multiple_select'" style="display:flex; gap:6px; align-items:center;">
                      <label v-for="opt in col.field.options" :key="opt.value" style="display:flex; align-items:center; gap:2px; font-size:11px; cursor:pointer">
                        <input type="checkbox" :value="opt.value" v-model="getItemCustomValueObj(item, col.fieldId).value" />
                        {{ opt.label }}
                      </label>
                    </div>
                    
                    <input 
                      v-else-if="col.field.fieldType === 'number'" 
                      type="number" 
                      v-model.number="getItemCustomValueObj(item, col.fieldId).value" 
                      class="form-input table-input-sm" 
                      style="width: 80px; padding: 2px 4px; height: 28px; text-align:center;"
                      :placeholder="col.field.placeholder || '数字'"
                    />
                    
                    <input 
                      v-else-if="col.field.fieldType === 'date'" 
                      type="date" 
                      v-model="getItemCustomValueObj(item, col.fieldId).value" 
                      class="form-input table-input-sm"
                      style="width: 120px; padding: 2px 4px; height: 28px;"
                    />
                    
                    <input 
                      v-else 
                      v-model="getItemCustomValueObj(item, col.fieldId).value" 
                      class="form-input table-input-sm" 
                      style="width: 100px; padding: 2px 4px; height: 28px;"
                      :placeholder="col.field.placeholder || '请输入'"
                    />
                  </template>
                </td>
              </template>
            </tr>
            <tr v-if="formItems.length === 0">
              <td :colspan="visibleItemColumns.length" style="text-align: center; color: #8a9693; padding: 32px 0;">
                暂无出库明细，请添加产品
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="form-actions" style="margin-top:24px; display:flex; justify-content: flex-end; gap: 12px;">
        <button class="ghost-btn" style="width: 90px;" type="button" @click="cancelCreate">取消</button>
        <button class="primary-btn" style="width: 90px;" type="button" @click="submitCreate">保存</button>
      </div>
    </template>

    <!-- Detail Receipt Modal -->
    <div v-if="showDetailModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 900px;">
        <div class="modal-header">
          <h2>出库单明细详情</h2>
          <button class="close-btn" type="button" @click="showDetailModal = false">×</button>
        </div>
        <div style="padding: 24px; max-height: 520px; overflow-y: auto;">
          <div class="detail-top-summary" style="display:grid; grid-template-columns: 1fr 1fr 1fr; gap:16px; margin-bottom: 24px">
            <div><span class="label" style="color:#8a9693">出库单号:</span> <span style="font-weight:500;color:#1e2724">{{ activeReceipt?.code }}</span></div>
            <div><span class="label" style="color:#8a9693">出库类型:</span> <span class="badge outbound-badge">{{ activeReceipt?.outboundType }}</span></div>
            <div><span class="label" style="color:#8a9693">出库时间:</span> <span style="color:#1e2724">{{ activeReceipt?.outboundTime }}</span></div>
            <div><span class="label" style="color:#8a9693">创建人:</span> <span style="color:#1e2724">{{ activeReceipt?.creator || '-' }}</span></div>
            <div><span class="label" style="color:#8a9693">创建时间:</span> <span style="color:#1e2724">{{ activeReceipt?.createdAt }}</span></div>
            <div><span class="label" style="color:#8a9693">备注:</span> <span style="color:#1e2724" :title="activeReceipt?.remark">{{ activeReceipt?.remark || '-' }}</span></div>
            
            <!-- 单头只读自定义字段 -->
            <div v-for="field in customFields" :key="field.id">
              <span class="label" style="color:#8a9693">{{ field.name }}:</span>
              <span style="color:#1e2724">{{ getReceiptCustomValue(activeReceipt, field.id) }}</span>
            </div>
          </div>
          <div class="order-detail-header">
            <h4 style="margin:0;font-size:14px;font-weight:600">出库物料明细</h4>
            <TableConfigButton table-id="outbound-readonly-item-table" />
          </div>
          <div class="table-container">
            <table id="outbound-readonly-item-table" class="data-table">
              <thead>
                <tr>
                  <th style="width:50px">序号</th>
                  <th>产品编号</th>
                  <th>产品名称</th>
                  <th>产品规格</th>
                  <th>属性</th>
                  <th>单位</th>
                  <th>出库数量</th>
                  
                  <!-- 明细只读自定义字段标题 -->
                  <th v-for="col in itemCustomFields" :key="col.id">{{ col.name }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, idx) in activeReceipt?.items" :key="item.id">
                  <td>{{ idx + 1 }}</td>
                  <td>{{ item.productCode }}</td>
                  <td>{{ item.productName }}</td>
                  <td>{{ item.productSpec || '-' }}</td>
                  <td>{{ item.productAttribute || '-' }}</td>
                  <td>{{ item.unitName || '-' }}</td>
                  <td style="font-weight:bold;color:#00b889">{{ item.quantity }}</td>
                  
                  <!-- 明细只读自定义字段值 -->
                  <td v-for="col in itemCustomFields" :key="col.id">
                    {{ getDetailItemCustomValue(item, col.id) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="modal-footer">
          <button class="primary-btn wide" type="button" @click="showDetailModal = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Column Config Modals -->
    <ColumnSettingsPanel
      v-if="showColumnConfig"
      v-model="columns"
      :defaults="defaultColumnSettings"
      title="列设置"
      @confirm="saveColumnSettings"
      @cancel="showColumnConfig = false"
    />

    <ColumnSettingsPanel
      v-if="showItemColumnConfig"
      v-model="itemColumns"
      :defaults="defaultItemColumnSettings"
      title="列设置"
      @confirm="saveItemColumnSettings"
      @cancel="showItemColumnConfig = false"
    />

    <!-- Add Product Modal -->
    <div v-if="showAddProduct" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 480px; overflow:visible;">
        <div class="modal-header">
          <h2>添加产品</h2>
          <button class="close-btn" type="button" @click="showAddProduct = false">×</button>
        </div>
        <div style="padding: 24px; display:flex; flex-direction:column; gap:16px;">
          <div class="form-row" style="position:relative">
            <label class="form-label required">产品</label>
            <input
              class="form-input"
              v-model="productSearchQuery"
              placeholder="输入编号/名称搜索并选择产品..."
              @focus="productDropdownOpen = true"
            />
            <div v-if="productDropdownOpen" class="search-dropdown-list">
              <div
                v-for="p in filteredProducts"
                :key="p.id"
                class="dropdown-item"
                @click="selectProductFromHelper(p); productDropdownOpen = false"
              >
                <span class="p-code">{{ p.code }}</span>
                <span class="p-name">{{ p.name }}</span>
                <span class="p-stock">库存: {{ p.inventoryQty || 0 }}</span>
              </div>
              <div v-if="filteredProducts.length === 0" class="dropdown-empty">没有匹配的产品</div>
            </div>
          </div>

          <div v-if="selectedProduct" class="product-preview-card">
            <div style="font-weight:bold; margin-bottom:8px; color:#1e2724">{{ selectedProduct.name }}</div>
            <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px; font-size:12px; color:#586360">
              <div>编号: {{ selectedProduct.code }}</div>
              <div>单位: {{ selectedProduct.unitName || '-' }}</div>
              <div>规格: {{ selectedProduct.spec || '-' }}</div>
              <div>当前库存: <span style="font-weight:bold; color:#00b889">{{ selectedProduct.inventoryQty || 0 }}</span></div>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label required">出库数量</label>
            <input class="form-input" type="number" v-model.number="addProductForm.quantity" min="1" />
            <span
              v-if="selectedProduct && addProductForm.quantity > (selectedProduct.inventoryQty || 0)"
              style="color:#ff5a5a; font-size:12px; margin-top:4px;"
            >
              出库数已超出当前库存量
            </span>
          </div>
        </div>
        <div class="modal-footer" style="margin-top:0">
          <button class="ghost-btn" style="margin-right:12px" @click="showAddProduct = false">取消</button>
          <button class="primary-btn" @click="addProductToItems">确定</button>
        </div>
      </div>
    </div>

    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      v-model:visible="deleteVisible"
      :title="deleteTitle"
      :message="deleteMessage"
      :loading="deleteLoading"
      @confirm="deleteHandleConfirm"
      @cancel="deleteHandleCancel"
    />
  </section>
</template>

<style scoped>
.outbound-badge {
  background: #f4fbf9;
  color: #00b889;
  border: 1px solid #ccefe7;
}

.qty-input.has-error {
  border-color: #ff5a5a;
  box-shadow: 0 0 0 2px rgba(255, 90, 90, 0.1);
}

.inline-err {
  color: #ff5a5a;
  font-size: 11px;
  margin-left: 4px;
  font-weight: 500;
}

.secondary-btn-green {
  background: white !important;
  color: #00b889 !important;
  border: 1px solid #00b889 !important;
}
.secondary-btn-green:hover {
  background: #f4fbf9 !important;
}

.product-preview-card {
  background: #fcfdfd;
  border: 1px dashed #dce3e0;
  border-radius: 8px;
  padding: 12px;
  margin-top: -8px;
}

.search-dropdown-list {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #dce3e0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  max-height: 180px;
  overflow-y: auto;
  z-index: 1050;
  margin-top: 4px;
}
.dropdown-item {
  padding: 8px 12px;
  display: flex;
  justify-content: space-between;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.2s;
}
.dropdown-item:hover {
  background: #f4fbf9;
}
.dropdown-item .p-code {
  font-weight: 500;
  color: #1e2724;
}
.dropdown-item .p-name {
  color: #586360;
  margin-left: 8px;
  flex-grow: 1;
}
.dropdown-item .p-stock {
  color: #00b889;
  font-weight: bold;
}
.dropdown-empty {
  padding: 12px;
  text-align: center;
  color: #8a9693;
  font-size: 13px;
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
