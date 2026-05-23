<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createInboundReceipt,
  deleteInboundReceipt,
  fetchInboundReceipts,
  fetchProducts,
  fetchCustomFields,
  type InboundReceiptInfo,
  type InboundReceiptItemPayload,
  type ProductInfo
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible: deleteVisible, title: deleteTitle, message: deleteMessage, loading: deleteLoading, confirm: deleteConfirm, handleConfirm: deleteHandleConfirm, handleCancel: deleteHandleCancel } = useDeleteConfirm()

// ===== State =====
const view = ref<'list' | 'create'>('list')
const receipts = ref<InboundReceiptInfo[]>([])
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
  inboundType: '',
  timeRange: '',
  startTime: '',
  endTime: ''
})

// Create form state
const inboundTypes = ['普通入库', '采购入库', '成品入库', '调拨入库', '销售退货入库', '其它入库']

const customFields = ref<any[]>([])
const itemCustomFields = ref<any[]>([])

const createForm = reactive({
  code: '',
  inboundType: '普通入库',
  inboundTime: formatDateTime(new Date()),
  remark: '',
  customValues: [] as any[]
})

const formItems = ref<Array<InboundReceiptItemPayload & { productCode?: string; productName?: string; productSpec?: string; productAttribute?: string; unitName?: string; minStock?: string; maxStock?: string; safetyStock?: string; inventoryQty?: number; customValues?: any[] }>>([])

// Add product modal
const showAddProduct = ref(false)
const addProductForm = reactive({
  productId: null as number | null,
  quantity: 1
})

// Import modal
const showImportModal = ref(false)

// Column config
interface ReceiptTableColumn {
  key: string
  label: string
  visible: boolean
  isCustom?: boolean
  fieldId?: number
  field?: any
}

const allColumns = [
  { key: 'index', label: '序号', visible: true },
  { key: 'code', label: '入库单号', visible: true },
  { key: 'inboundType', label: '入库类型', visible: true },
  { key: 'inboundTime', label: '入库时间', visible: true },
  { key: 'remark', label: '备注', visible: true },
  { key: 'createdAt', label: '创建时间', visible: true },
  { key: 'creator', label: '创建人', visible: true },
  { key: 'actions', label: '操作', visible: true }
]
const columns = ref<ReceiptTableColumn[]>(allColumns.map(c => ({ ...c })))
const showColumnConfig = ref(false)

// Detail columns
const detailColumns = [
  { key: 'index', label: '序号', visible: true },
  { key: 'productCode', label: '产品编号', visible: true },
  { key: 'productName', label: '产品名称', visible: true },
  { key: 'productSpec', label: '产品规格', visible: true },
  { key: 'productAttribute', label: '产品属性', visible: true },
  { key: 'unitName', label: '库存单位', visible: true },
  { key: 'minStock', label: '最小库存', visible: true },
  { key: 'maxStock', label: '最大库存', visible: true },
  { key: 'safetyStock', label: '安全库存', visible: true },
  { key: 'inventoryQty', label: '当前库存数量', visible: true },
  { key: 'quantity', label: '入库数量', visible: true },
  { key: 'actions', label: '操作', visible: true }
]
const itemColumns = ref<ReceiptTableColumn[]>(detailColumns.map(c => ({ ...c })))
const showItemColumnConfig = ref(false)

// ===== Computed =====
const visibleColumns = computed(() => columns.value.filter(c => c.visible))
const visibleItemColumns = computed(() => itemColumns.value.filter(c => c.visible))
const totalPages = computed(() => Math.ceil(totalReceipts.value / pageSize.value))

const selectedProduct = computed(() => {
  if (!addProductForm.productId) return null
  return products.value.find(p => p.id === addProductForm.productId) || null
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
    const customCols = customFields.value.map(f => ({
      key: `custom_${f.id}`,
      fieldId: f.id,
      label: f.name,
      visible: true,
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

    // 合并明细自定义字段到 itemColumns
    const baseItemCols = detailColumns.map(c => ({ ...c }))
    const customItemCols = itemCustomFields.value.map(f => ({
      key: `custom_${f.id}`,
      fieldId: f.id,
      label: f.name,
      visible: true,
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

async function loadReceipts() {
  loading.value = true
  try {
    const res = await fetchInboundReceipts(props.token, {
      code: filterForm.code || undefined,
      inboundType: filterForm.inboundType || undefined,
      startTime: filterForm.startTime || undefined,
      endTime: filterForm.endTime || undefined,
      page: currentPage.value,
      size: pageSize.value
    })
    receipts.value = res.items
    totalReceipts.value = res.total
  } catch (e: any) {
    emit('notice', e.message || '加载入库单失败')
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  try {
    const res = await fetchProducts(props.token, { size: 1000 })
    products.value = res.items
  } catch (e: any) {
    // silent
  }
}

function handleSearch() {
  currentPage.value = 1
  loadReceipts()
}

function handleReset() {
  filterForm.code = ''
  filterForm.inboundType = ''
  filterForm.timeRange = ''
  filterForm.startTime = ''
  filterForm.endTime = ''
  handleSearch()
}

function goToCreate() {
  createForm.code = ''
  createForm.inboundType = '普通入库'
  createForm.inboundTime = formatDateTime(new Date())
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
  loadProducts()
}

function cancelCreate() {
  view.value = 'list'
}

function openAddProduct() {
  addProductForm.productId = null
  addProductForm.quantity = 1
  showAddProduct.value = true
}

function confirmAddProduct() {
  if (!addProductForm.productId) {
    emit('notice', '请选择产品')
    return
  }
  if (addProductForm.quantity <= 0) {
    emit('notice', '入库数量必须大于0')
    return
  }
  const product = products.value.find(p => p.id === addProductForm.productId)
  if (!product) return

  // Check if product already added
  const existing = formItems.value.find(item => item.productId === addProductForm.productId)
  if (existing) {
    emit('notice', '该产品已添加，请直接编辑数量')
    return
  }

  formItems.value.push({
    productId: product.id,
    quantity: addProductForm.quantity,
    productCode: product.code,
    productName: product.name,
    productSpec: product.spec,
    productAttribute: product.attribute,
    unitName: product.unitName,
    minStock: product.minStock,
    maxStock: product.maxStock,
    safetyStock: product.safetyStock,
    inventoryQty: product.inventoryQty,
    customValues: itemCustomFields.value.map(f => ({
      fieldId: f.id,
      fieldName: f.name,
      fieldType: f.fieldType,
      value: f.fieldType === 'multiple_select' ? [] : (f.fieldType === 'number' ? null : '')
    }))
  })
  showAddProduct.value = false
}

function removeItem(index: number) {
  formItems.value.splice(index, 1)
}

async function handleSave() {
  if (!createForm.inboundType) {
    emit('notice', '请选择入库类型')
    return
  }
  if (!createForm.inboundTime) {
    emit('notice', '请选择入库时间')
    return
  }
  if (formItems.value.length === 0) {
    emit('notice', '请至少添加一个产品')
    return
  }

  loading.value = true
  try {
    await createInboundReceipt(props.token, {
      code: createForm.code || undefined,
      inboundType: createForm.inboundType,
      inboundTime: createForm.inboundTime,
      remark: createForm.remark || undefined,
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
    })
    emit('notice', '入库单创建成功')
    view.value = 'list'
    loadReceipts()
  } catch (e: any) {
    emit('notice', e.message || '创建失败')
  } finally {
    loading.value = false
  }
}

function handleDelete(receipt: InboundReceiptInfo) {
  deleteConfirm({
    title: '删除入库单',
    message: `确定要删除入库单【${receipt.code}】吗？删除后库存将回退。`
  }).then(async (confirmed) => {
    if (!confirmed) return
    try {
      deleteLoading.value = true
      await deleteInboundReceipt(props.token, receipt.id)
      emit('notice', '删除成功')
      loadReceipts()
    } catch (e: any) {
      emit('notice', e.message || '删除失败')
    } finally {
      deleteLoading.value = false
      deleteVisible.value = false
    }
  })
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
  loadReceipts()
}

function handleTimeRangeChange() {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  if (filterForm.timeRange === 'today') {
    filterForm.startTime = formatDateTime(today)
    filterForm.endTime = formatDateTime(new Date(today.getTime() + 86400000 - 1))
  } else if (filterForm.timeRange === 'week') {
    const weekStart = new Date(today.getTime() - today.getDay() * 86400000)
    filterForm.startTime = formatDateTime(weekStart)
    filterForm.endTime = formatDateTime(now)
  } else if (filterForm.timeRange === 'month') {
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
    filterForm.startTime = formatDateTime(monthStart)
    filterForm.endTime = formatDateTime(now)
  } else {
    filterForm.startTime = ''
    filterForm.endTime = ''
  }
}

const uploadingFieldId = ref<number | null>(null)

async function handleCustomFieldUpload(field: any, event: Event, customValueObj: any) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  uploadingFieldId.value = field.id
  try {
    const formData = new FormData()
    formData.append('file', file)
    
    const response = await fetch('/api/mobile/reports/images', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${props.token}`
      },
      body: formData
    })
    
    if (!response.ok) {
      let msg = '上传失败'
      try {
        const body = await response.json()
        msg = body.message || msg
      } catch {}
      throw new Error(msg)
    }
    
    const data = await response.json()
    const currentVal = String(customValueObj.value || '')
    const newPath = data.accessPath
    const updatedVal = currentVal ? `${currentVal},${newPath}` : newPath
    customValueObj.value = updatedVal
    emit('notice', '文件上传成功')
  } catch (err: any) {
    emit('notice', err.message || '上传失败')
  } finally {
    uploadingFieldId.value = null
    target.value = ''
  }
}

function removeReceiptCustomFieldPath(customValueObj: any, index: number) {
  const currentVal = String(customValueObj.value || '')
  const paths = currentVal.split(',').filter(Boolean)
  paths.splice(index, 1)
  customValueObj.value = paths.join(',')
}

onMounted(() => {
  loadCustomFieldConfigs()
  loadReceipts()
})
</script>

<template>
  <section class="content">
    <!-- List View -->
    <template v-if="view === 'list'">
      <div class="filters inbound-filters">
        <label>
          <span>入库单号</span>
          <input v-model="filterForm.code" placeholder="请输入入库单号" />
        </label>
        <label>
          <span>入库类型</span>
          <select v-model="filterForm.inboundType">
            <option value="">请选择入库类型</option>
            <option v-for="t in inboundTypes" :key="t" :value="t">{{ t }}</option>
          </select>
        </label>
        <div class="time-filter-group">
          <label class="time-range-label">
            <span>入库时间</span>
            <select v-model="filterForm.timeRange" @change="handleTimeRangeChange">
              <option value="">选择范围</option>
              <option value="today">今天</option>
              <option value="week">本周</option>
              <option value="month">本月</option>
            </select>
          </label>
          <div class="date-range-picker-wrapper">
            <div class="date-range-picker">
              <input 
                :type="startTimeFocused || filterForm.startTime ? 'date' : 'text'" 
                v-model="filterForm.startTime" 
                placeholder="开始日期" 
                class="date-picker-input"
                @focus="startTimeFocused = true"
                @blur="startTimeFocused = false"
              />
              <span class="range-separator">→</span>
              <input 
                :type="endTimeFocused || filterForm.endTime ? 'date' : 'text'" 
                v-model="filterForm.endTime" 
                placeholder="结束日期" 
                class="date-picker-input"
                @focus="endTimeFocused = true"
                @blur="endTimeFocused = false"
              />
              <span class="calendar-icon">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
              </span>
            </div>
          </div>
        </div>
        <div class="filter-actions">
          <button class="primary-btn query-btn" type="button" @click="handleSearch">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
            查询
          </button>
          <button class="toolbar-btn" type="button" @click="handleReset">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
          </button>
        </div>
      </div>

      <div class="toolbar">
        <div class="toolbar-left">
          <button class="primary-btn create-btn" type="button" @click="goToCreate">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建入库单
          </button>
          <button class="toolbar-btn" type="button" disabled>
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
            批量操作
          </button>
          <button class="link-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            编辑打印模板
          </button>
        </div>
        <div style="margin-left:auto;display:flex;gap:12px;align-items:center">
          <button class="text-btn" type="button" style="color: #586360;" @click="showColumnConfig = !showColumnConfig">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
            列配置
          </button>
        </div>
      </div>

      <!-- Column config dropdown -->
      <div v-if="showColumnConfig" class="column-config-dropdown" style="position:absolute;right:20px;top:180px;background:#fff;border:1px solid #e4e9e7;border-radius:6px;padding:12px;z-index:100;box-shadow:0 4px 12px rgba(0,0,0,0.1)">
        <label v-for="col in columns" :key="col.key" style="display:flex;align-items:center;gap:6px;padding:4px 0;cursor:pointer">
          <input type="checkbox" v-model="col.visible" :disabled="col.key === 'actions'" />
          <span style="font-size:13px">{{ col.label }}</span>
        </label>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th v-for="col in visibleColumns" :key="col.key">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="receipts.length === 0">
              <td :colspan="visibleColumns.length" style="text-align:center;padding:60px 0;color:#aaa">
                <div style="display:flex;flex-direction:column;align-items:center;gap:8px">
                  <svg viewBox="0 0 24 24" width="40" height="40" stroke="#ccc" stroke-width="1" fill="none"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>
                  <span>暂无数据</span>
                </div>
              </td>
            </tr>
            <tr v-for="(receipt, idx) in receipts" :key="receipt.id">
              <template v-for="col in visibleColumns" :key="col.key">
                <td v-if="col.key === 'index'">{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
                <td v-else-if="col.key === 'code'">{{ receipt.code }}</td>
                <td v-else-if="col.key === 'inboundType'">{{ receipt.inboundType }}</td>
                <td v-else-if="col.key === 'inboundTime'">{{ receipt.inboundTime }}</td>
                <td v-else-if="col.key === 'remark'">{{ receipt.remark || '-' }}</td>
                <td v-else-if="col.key === 'createdAt'">{{ receipt.createdAt }}</td>
                <td v-else-if="col.key === 'creator'">{{ receipt.creator || '-' }}</td>
                <td v-else-if="col.key === 'actions'">
                  <a class="link-btn" style="font-size:12px;cursor:pointer" @click="handleDelete(receipt)">删除</a>
                </td>
                <td v-else-if="col.isCustom">
                  {{ getReceiptCustomValue(receipt, col.fieldId) }}
                </td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination" v-if="totalReceipts > 0">
        <span class="page-info">共{{ totalReceipts }}条结果</span>
        <div class="page-controls">
          <button :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">&lt;</button>
          <button class="active">{{ currentPage }}</button>
          <button :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">&gt;</button>
        </div>
        <span class="page-size-label">{{ pageSize }} 条/页</span>
      </div>
    </template>

    <!-- Create View -->
    <template v-if="view === 'create'">
      <h3 style="margin:0 0 24px;font-size:16px;font-weight:600">创建入库单</h3>

      <div class="form-section">
        <div class="form-row">
          <label class="form-label">入库单号</label>
          <input class="form-input" v-model="createForm.code" placeholder="请输入，留空将自动生成" />
        </div>
        <div class="form-row">
          <label class="form-label required">入库类型</label>
          <select class="form-input" v-model="createForm.inboundType">
            <option v-for="t in inboundTypes" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="form-row">
          <label class="form-label required">入库时间</label>
          <input class="form-input" type="datetime-local" v-model="createForm.inboundTime" step="1" />
        </div>
        <div class="form-row">
          <label class="form-label">备注</label>
          <textarea class="form-input" v-model="createForm.remark" rows="3" placeholder="请输入备注"></textarea>
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
          
          <div v-else-if="field.fieldType === 'image' && createForm.customValues[fIdx]" style="flex:1; display:flex; flex-direction:column; gap:6px;">
            <div style="display:flex; gap:8px; align-items:center; width:100%; max-width:300px;">
              <input class="form-input" v-model="createForm.customValues[fIdx].value" :placeholder="field.placeholder || '请输入链接或上传'" style="flex:1;" />
              <button 
                class="toolbar-btn" 
                type="button" 
                @click="(($event.currentTarget as HTMLElement).nextElementSibling as HTMLInputElement)?.click()"
                style="height:34px; margin:0;"
              >
                {{ uploadingFieldId === field.id ? '上传中...' : '上传' }}
              </button>
              <input type="file" accept="image/*" style="display:none;" @change="handleCustomFieldUpload(field, $event, createForm.customValues[fIdx])" />
            </div>
            <div v-if="createForm.customValues[fIdx].value" style="margin-top:6px; display:flex; flex-wrap:wrap; gap:8px;">
              <div 
                v-for="(path, pIdx) in String(createForm.customValues[fIdx].value || '').split(',').filter(Boolean)" 
                :key="path" 
                style="position:relative; display:inline-block; width: fit-content;"
              >
                <img 
                  :src="path" 
                  alt="预览" 
                  style="max-width:80px; max-height:80px; object-fit:cover; border:1px solid #dce3e0; border-radius:4px; display:block;" 
                  @error="($event.target as HTMLElement).style.display='none'" 
                />
                <button 
                  type="button" 
                  @click="removeReceiptCustomFieldPath(createForm.customValues[fIdx], pIdx)" 
                  style="position:absolute; top:-6px; right:-6px; width:16px; height:16px; border-radius:50%; background:#ff5a5a; color:#fff; border:none; display:flex; align-items:center; justify-content:center; cursor:pointer; font-size:9px; line-height:1; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.15); padding:0; z-index:10;"
                  title="取消图片"
                >
                  ×
                </button>
              </div>
            </div>
          </div>
          
          <div v-else-if="field.fieldType === 'file' && createForm.customValues[fIdx]" style="flex:1; display:flex; flex-direction:column; gap:6px;">
            <div style="display:flex; gap:8px; align-items:center; width:100%; max-width:300px;">
              <input class="form-input" v-model="createForm.customValues[fIdx].value" :placeholder="field.placeholder || '请输入链接或上传'" style="flex:1;" />
              <button 
                class="toolbar-btn" 
                type="button" 
                @click="(($event.currentTarget as HTMLElement).nextElementSibling as HTMLInputElement)?.click()"
                style="height:34px; margin:0;"
              >
                {{ uploadingFieldId === field.id ? '上传中...' : '上传' }}
              </button>
              <input type="file" style="display:none;" @change="handleCustomFieldUpload(field, $event, createForm.customValues[fIdx])" />
            </div>
            <div v-if="createForm.customValues[fIdx].value" style="margin-top:6px; display:flex; flex-wrap:wrap; gap:8px;">
              <div 
                v-for="(path, pIdx) in String(createForm.customValues[fIdx].value || '').split(',').filter(Boolean)" 
                :key="path" 
                style="position:relative; display:inline-block; width: fit-content;"
              >
                <img 
                  :src="path" 
                  alt="预览" 
                  style="max-width:80px; max-height:80px; object-fit:cover; border:1px solid #dce3e0; border-radius:4px; display:block;" 
                  @error="($event.target as HTMLElement).style.display='none'" 
                />
                <button 
                  type="button" 
                  @click="removeReceiptCustomFieldPath(createForm.customValues[fIdx], pIdx)" 
                  style="position:absolute; top:-6px; right:-6px; width:16px; height:16px; border-radius:50%; background:#ff5a5a; color:#fff; border:none; display:flex; align-items:center; justify-content:center; cursor:pointer; font-size:9px; line-height:1; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.15); padding:0; z-index:10;"
                  title="取消附件"
                >
                  ×
                </button>
              </div>
            </div>
          </div>

          <input 
            v-else-if="createForm.customValues[fIdx]" 
            class="form-input" 
            v-model="createForm.customValues[fIdx].value" 
            :placeholder="field.placeholder || '请输入'" 
          />
        </div>
      </div>

      <div style="margin-top:32px">
        <h4 style="margin:0 0 12px;font-size:14px;font-weight:500">入库明细</h4>
        <div class="toolbar" style="margin-bottom:12px">
          <div class="toolbar-left">
            <button class="primary-btn create-btn" type="button" @click="openAddProduct">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              添加产品
            </button>
            <button class="toolbar-btn" type="button" disabled>
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
              按BOM添加
            </button>
            <button class="toolbar-btn" type="button" @click="showImportModal = true">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              导入数据
            </button>
          </div>
          <div style="margin-left:auto; position: relative;">
            <button class="text-btn" type="button" style="color: #586360;" @click="showItemColumnConfig = !showItemColumnConfig">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
              列配置
            </button>
            <div v-if="showItemColumnConfig" class="column-config-dropdown" style="position:absolute;right:0;top:32px;background:#fff;border:1px solid #e4e9e7;border-radius:6px;padding:12px;z-index:100;box-shadow:0 4px 12px rgba(0,0,0,0.1); width: 150px;">
              <label v-for="col in itemColumns" :key="col.key" style="display:flex;align-items:center;gap:6px;padding:4px 0;cursor:pointer">
                <input type="checkbox" v-model="col.visible" :disabled="col.key === 'productCode' || col.key === 'quantity'" />
                <span style="font-size:13px">{{ col.label }}</span>
              </label>
            </div>
          </div>
        </div>

        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th v-for="col in visibleItemColumns" :key="col.key">{{ col.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="formItems.length === 0">
                <td :colspan="visibleItemColumns.length" style="text-align:center;padding:60px 0;color:#aaa">
                  <div style="display:flex;flex-direction:column;align-items:center;gap:8px">
                    <svg viewBox="0 0 24 24" width="40" height="40" stroke="#ccc" stroke-width="1" fill="none"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>
                    <span>暂无数据</span>
                  </div>
                </td>
              </tr>
              <tr v-for="(item, idx) in formItems" :key="idx">
                <template v-for="col in visibleItemColumns" :key="col.key">
                  <td v-if="col.key === 'index'">{{ idx + 1 }}</td>
                  <td v-else-if="col.key === 'productCode'">{{ item.productCode }}</td>
                  <td v-else-if="col.key === 'productName'">{{ item.productName }}</td>
                  <td v-else-if="col.key === 'productSpec'">{{ item.productSpec || '-' }}</td>
                  <td v-else-if="col.key === 'productAttribute'">{{ item.productAttribute || '-' }}</td>
                  <td v-else-if="col.key === 'unitName'">{{ item.unitName || '-' }}</td>
                  <td v-else-if="col.key === 'minStock'">{{ item.minStock || '-' }}</td>
                  <td v-else-if="col.key === 'maxStock'">{{ item.maxStock || '-' }}</td>
                  <td v-else-if="col.key === 'safetyStock'">{{ item.safetyStock || '-' }}</td>
                  <td v-else-if="col.key === 'inventoryQty'">{{ item.inventoryQty ?? 0 }}</td>
                  <td v-else-if="col.key === 'quantity'">
                    <input type="number" v-model.number="item.quantity" class="form-input qty-input" style="width: 80px; text-align:center; height:28px; padding:2px 4px;" min="1" />
                  </td>
                  <td v-else-if="col.key === 'actions'">
                    <a class="link-btn" style="font-size:12px;cursor:pointer;color:#ff5a5a" @click="removeItem(idx)">删除</a>
                  </td>
                  <td v-else-if="col.isCustom">
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
            </tbody>
          </table>
        </div>

        <div class="pagination" v-if="formItems.length > 0">
          <span class="page-info">共{{ formItems.length }}条结果</span>
        </div>
      </div>

      <div style="margin-top:24px;display:flex;gap:12px;align-items:center">
        <button class="btn-cancel" type="button" @click="cancelCreate">取消</button>
        <button class="btn-save" type="button" @click="handleSave" :disabled="loading">保存</button>
      </div>
    </template>

    <!-- Add Product Modal -->
    <div v-if="showAddProduct" class="modal-overlay" @click.self="showAddProduct = false">
      <div class="modal" style="width:520px">
        <div class="modal-header">
          <span>添加产品</span>
          <button class="modal-close" @click="showAddProduct = false">&times;</button>
        </div>
        <div class="modal-body" style="padding:24px">
          <div class="form-row">
            <label class="form-label required">产品</label>
            <select class="form-input" v-model="addProductForm.productId">
              <option :value="null" disabled>请选择产品</option>
              <option v-for="p in products" :key="p.id" :value="p.id">{{ p.code }} - {{ p.name }}</option>
            </select>
          </div>

          <div v-if="selectedProduct" class="product-info-box" style="margin:16px 0;padding:16px;background:#f8f9fa;border-radius:6px">
            <div class="form-row" style="margin-bottom:0">
              <label class="form-label" style="color:#666">产品数据</label>
              <div>
                <p style="margin:4px 0;font-size:13px;color:#555"><strong>库存单位：</strong>{{ selectedProduct.unitName || '-' }}</p>
                <p style="margin:4px 0;font-size:13px;color:#555"><strong>当前库存数量：</strong>{{ selectedProduct.inventoryQty ?? 0 }}</p>
              </div>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label required">入库数量</label>
            <input class="form-input" type="number" v-model.number="addProductForm.quantity" min="1" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn" type="button" @click="showAddProduct = false">取 消</button>
          <button class="primary-btn" type="button" @click="confirmAddProduct">确 定</button>
        </div>
      </div>
    </div>

    <!-- Import Modal -->
    <div v-if="showImportModal" class="modal-overlay" @click.self="showImportModal = false">
      <div class="modal" style="width:560px">
        <div class="modal-header">
          <span>导入产品明细</span>
          <button class="modal-close" @click="showImportModal = false">&times;</button>
        </div>
        <div class="modal-body" style="padding:24px">
          <div style="margin-bottom:16px">
            <p style="margin:0 0 8px;font-weight:500">操作步骤:</p>
            <p style="margin:4px 0;font-size:13px">1.下载 <a href="#" style="color:var(--accent)" @click.prevent="emit('notice', '模板下载功能开发中...')">《导入产品明细模板》</a></p>
            <p style="margin:4px 0;font-size:13px">2.打开下载表，将对应信息填入或粘贴进本表。为保证导入成功，请使用纯文本或数字。</p>
            <p style="margin:4px 0;font-size:13px">3.信息输入完毕并保存后，点击下方&lt;上传文件&gt;按钮选择已保存的excel文档。</p>
            <p style="margin:4px 0;font-size:13px">4.点击&lt;开始导入&gt;</p>
          </div>
          <div style="display:flex;align-items:center;gap:12px;margin-top:20px">
            <button class="toolbar-btn" type="button" @click="emit('notice', '上传功能开发中...')">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              上传文件
            </button>
            <span style="font-size:12px;color:#999">附件支持类型: XLSX，最大不能超过10M</span>
          </div>
          <div style="margin-top:16px">
            <button class="toolbar-btn" type="button" disabled>开始导入</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      :visible="deleteVisible"
      :title="deleteTitle"
      :message="deleteMessage"
      :loading="deleteLoading"
      @confirm="deleteHandleConfirm"
      @cancel="deleteHandleCancel"
    />
  </section>
</template>

<style scoped>
.form-section {
  max-width: 480px;
}
.form-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 12px;
}
.form-label {
  min-width: 80px;
  font-size: 13px;
  color: #333;
  line-height: 34px;
  text-align: right;
}
.form-label.required::before {
  content: '* ';
  color: #e53935;
}
.form-input {
  flex: 1;
  max-width: 300px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #dce3e0;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.form-input:focus {
  border-color: var(--accent);
}
textarea.form-input {
  height: auto;
  padding: 8px 10px;
  resize: vertical;
}
select.form-input {
  appearance: auto;
}

.inbound-filters {
  align-items: start !important;
}
.time-filter-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.time-range-label {
  width: 280px;
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  align-items: center;
  gap: 0 8px;
}
.date-range-picker-wrapper {
  padding-left: 94px;
  width: 280px;
  box-sizing: border-box;
}
.date-range-picker {
  display: flex;
  align-items: center;
  height: 36px;
  border: 1px solid #dce3e0;
  border-radius: 6px;
  background: #fff;
  padding: 0 10px;
  width: 100%;
  box-sizing: border-box;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.date-range-picker:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(0, 184, 137, 0.12);
}
.date-picker-input {
  flex: 1;
  border: none !important;
  background: transparent !important;
  padding: 0 !important;
  height: 100% !important;
  font-size: 13px !important;
  color: #1e2724 !important;
  box-shadow: none !important;
  outline: none !important;
  min-width: 0;
  position: relative;
}
.date-picker-input::-webkit-calendar-picker-indicator {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: auto;
  height: auto;
  background: transparent;
  color: transparent;
  cursor: pointer;
  opacity: 0;
}
.range-separator {
  color: #909d99;
  font-size: 13px;
  user-select: none;
  margin: 0 4px;
}
.calendar-icon {
  margin-left: auto;
  color: #909d99;
  display: flex;
  align-items: center;
  pointer-events: none;
}
</style>
