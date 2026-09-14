<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, type CSSProperties } from 'vue'
import {
  createInboundReceipt,
  deleteInboundReceipt,
  fetchInboundReceipts,
  fetchProducts,
  fetchCustomFields,
  fetchCustomFieldAttachmentBlob,
  fetchReportImageBlob,
  uploadCustomFieldAttachment,
  type InboundReceiptInfo,
  type InboundReceiptItemPayload,
  type ProductInfo
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchAllPages } from '../utils/pagination'
import { resolveInventoryTimeRange } from '../utils/inventoryTimeRange'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'
import UiIcon from '@/components/UiIcon.vue'
import ColumnSettingsPanel, { type ColumnSetting } from '@/components/ColumnSettingsPanel.vue'

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
const saving = ref(false)
const beginReceiptsLoad = useLatestRequest()
const beginProductsLoad = useLatestRequest()

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

// Column config
interface ReceiptTableColumn extends ColumnSetting {
  isCustom?: boolean
  fieldId?: number
  field?: any
}

const allColumns = [
  { key: 'index', label: '序号', visible: true, width: 70, fixed: 'left' as const },
  { key: 'code', label: '入库单号', visible: true, width: 180, fixed: 'none' as const },
  { key: 'inboundType', label: '入库类型', visible: true, width: 140, fixed: 'none' as const },
  { key: 'inboundTime', label: '入库时间', visible: true, width: 160, fixed: 'none' as const },
  { key: 'remark', label: '备注', visible: true, width: 180, fixed: 'none' as const },
  { key: 'createdAt', label: '创建时间', visible: true, width: 160, fixed: 'none' as const },
  { key: 'creator', label: '创建人', visible: true, width: 120, fixed: 'none' as const },
  { key: 'actions', label: '操作', visible: true, width: 120, fixed: 'right' as const }
]
const columns = ref<ReceiptTableColumn[]>(allColumns.map(c => ({ ...c })))
const defaultColumnSettings = ref<ReceiptTableColumn[]>(allColumns.map(c => ({ ...c })))
const showColumnConfig = ref(false)

// Detail columns
const detailColumns = [
  { key: 'index', label: '序号', visible: true, width: 70, fixed: 'left' as const },
  { key: 'productCode', label: '产品编号', visible: true, width: 140, fixed: 'none' as const },
  { key: 'productName', label: '产品名称', visible: true, width: 150, fixed: 'none' as const },
  { key: 'productSpec', label: '产品规格', visible: true, width: 150, fixed: 'none' as const },
  { key: 'productAttribute', label: '产品属性', visible: true, width: 140, fixed: 'none' as const },
  { key: 'unitName', label: '库存单位', visible: true, width: 100, fixed: 'none' as const },
  { key: 'minStock', label: '最小库存', visible: true, width: 110, fixed: 'none' as const },
  { key: 'maxStock', label: '最大库存', visible: true, width: 110, fixed: 'none' as const },
  { key: 'safetyStock', label: '安全库存', visible: true, width: 110, fixed: 'none' as const },
  { key: 'inventoryQty', label: '当前库存数量', visible: true, width: 140, fixed: 'none' as const },
  { key: 'quantity', label: '入库数量', visible: true, width: 120, fixed: 'none' as const },
  { key: 'actions', label: '操作', visible: true, width: 120, fixed: 'right' as const }
]
const itemColumns = ref<ReceiptTableColumn[]>(detailColumns.map(c => ({ ...c })))
const showItemColumnConfig = ref(false)

const showPrintTemplateModal = ref(false)
const inboundPrintFields = [
  { key: 'code', label: '入库单号' },
  { key: 'inboundType', label: '入库类型' },
  { key: 'inboundTime', label: '入库时间' },
  { key: 'remark', label: '备注' },
  { key: 'creator', label: '创建人' },
  { key: 'items', label: '入库明细' }
]
const inboundPrintTemplate = reactive({
  name: '默认入库单模板',
  title: '入库单',
  showFields: ['code', 'inboundType', 'inboundTime', 'items'] as string[],
  footerText: '请核对入库产品、数量与库存单位'
})

// ===== Computed =====
const visibleColumns = computed(() => columns.value.filter(c => c.visible))
const visibleItemColumns = computed(() => itemColumns.value.filter(c => c.visible))
const totalPages = computed(() => Math.max(1, Math.ceil(totalReceipts.value / pageSize.value)))

function inboundColumnStyle(column: ReceiptTableColumn, header = false): CSSProperties {
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
    minWidth: `${column.width}px`,
  }
  if (column.fixed !== 'none') {
    style.position = 'sticky'
    style.zIndex = 3
    style.background = header ? 'var(--table-head-bg)' : 'var(--table-row-bg)'
    if (column.fixed === 'left') style.left = `${left}px`
    else style.right = `${right}px`
  }
  return style
}

function restoreInboundColumnSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem('inbound_receipt_columns') || 'null')
    if (!Array.isArray(saved) || saved.length === 0) return
    const savedByKey = new Map(saved.map((column: any) => [column.key, column]))
    const orderedKeys = [
      ...saved.map((column: any) => column.key),
      ...defaultColumnSettings.value.map(column => column.key).filter(key => !savedByKey.has(key))
    ]
    columns.value = orderedKeys
      .map(key => {
        const base = defaultColumnSettings.value.find(column => column.key === key)
        const stored = savedByKey.get(key)
        if (!base) return null
        const fixed = stored?.fixed === 'left' || stored?.fixed === 'right' || stored?.fixed === 'none'
          ? stored.fixed
          : base.fixed
        return { ...base, ...(stored || {}), fixed, locked: false }
      })
      .filter((column): column is ReceiptTableColumn => Boolean(column))
  } catch {
    // Ignore an invalid local column preference and keep defaults.
  }
}

function saveInboundColumnSettings(settings: ReceiptTableColumn[]) {
  columns.value = settings.map(column => ({ ...column }))
  localStorage.setItem('inbound_receipt_columns', JSON.stringify(columns.value))
  showColumnConfig.value = false
  emit('notice', '列配置已保存')
}

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
      width: 140,
      fixed: 'none' as const,
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
    restoreInboundColumnSettings()

    // 合并明细自定义字段到 itemColumns
    const baseItemCols = detailColumns.map(c => ({ ...c }))
    const customItemCols = itemCustomFields.value.map(f => ({
      key: `custom_${f.id}`,
      fieldId: f.id,
      label: f.name,
      visible: true,
      width: 140,
      fixed: 'none' as const,
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
  const isCurrent = beginReceiptsLoad()
  loading.value = true
  try {
    const res = await fetchInboundReceipts(props.token, {
      code: filterForm.code || undefined,
      inboundType: filterForm.inboundType || undefined,
      ...resolveInventoryTimeRange(filterForm),
      page: currentPage.value,
      size: pageSize.value
    })
    if (!isCurrent()) return
    receipts.value = res.items
    totalReceipts.value = res.total
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
      await loadReceipts()
    }
  } catch (e: any) {
    if (isCurrent()) emit('notice', e.message || '加载入库单失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

async function loadProducts() {
  const isCurrent = beginProductsLoad()
  try {
    const res = await fetchAllPages(params => fetchProducts(props.token, params), 500, isCurrent)
    if (isCurrent()) products.value = res.items
  } catch (e: any) {
    if (isCurrent()) emit('notice', e.message || '加载产品失败')
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
  if (!Number.isInteger(addProductForm.quantity) || addProductForm.quantity <= 0) {
    emit('notice', '入库数量必须为大于 0 的整数')
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
  if (saving.value) return
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
  if (formItems.value.some(item => !Number.isInteger(item.quantity) || item.quantity <= 0)) {
    emit('notice', '入库数量必须为大于 0 的整数')
    return
  }

  saving.value = true
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
    currentPage.value = 1
    await loadReceipts()
  } catch (e: any) {
    emit('notice', e.message || '创建失败')
  } finally {
    saving.value = false
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

function loadInboundPrintTemplate() {
  try {
    const raw = localStorage.getItem('inbound_print_template')
    if (!raw) return
    const saved = JSON.parse(raw)
    inboundPrintTemplate.name = saved.name || inboundPrintTemplate.name
    inboundPrintTemplate.title = saved.title || inboundPrintTemplate.title
    inboundPrintTemplate.showFields = Array.isArray(saved.showFields) && saved.showFields.length > 0
      ? saved.showFields
      : inboundPrintTemplate.showFields
    inboundPrintTemplate.footerText = saved.footerText || inboundPrintTemplate.footerText
  } catch (e) {
    console.error('读取入库打印模板失败:', e)
  }
}

function openPrintTemplateModal() {
  loadInboundPrintTemplate()
  showPrintTemplateModal.value = true
}

function saveInboundPrintTemplate() {
  if (!inboundPrintTemplate.name.trim()) {
    emit('notice', '请输入模板名称')
    return
  }
  if (!inboundPrintTemplate.title.trim()) {
    emit('notice', '请输入打印标题')
    return
  }
  if (inboundPrintTemplate.showFields.length === 0) {
    emit('notice', '请至少选择一个打印字段')
    return
  }
  localStorage.setItem('inbound_print_template', JSON.stringify(inboundPrintTemplate))
  showPrintTemplateModal.value = false
  emit('notice', '入库打印模板已保存')
}

function handleTimeRangeChange() {
  filterForm.startTime = ''
  filterForm.endTime = ''
}

const uploadingFieldId = ref<number | null>(null)
const customFieldPreviewUrls = ref<Record<string, string>>({})
const loadingCustomFieldPreviews = new Set<string>()
const failedCustomFieldPreviews = new Set<string>()

async function handleCustomFieldUpload(field: any, event: Event, customValueObj: any) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  uploadingFieldId.value = field.id
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scope', 'receipt')
    formData.append('fieldId', String(field.id))
    formData.append('fieldType', field.fieldType)
    
    const data = await uploadCustomFieldAttachment(props.token, formData)
    const currentVal = String(customValueObj.value || '')
    const newPath = data.accessPath
    customFieldPreviewUrls.value[newPath] = URL.createObjectURL(file)
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
  const removed = paths[index]
  if (removed && customFieldPreviewUrls.value[removed]) {
    URL.revokeObjectURL(customFieldPreviewUrls.value[removed])
    delete customFieldPreviewUrls.value[removed]
  }
  paths.splice(index, 1)
  customValueObj.value = paths.join(',')
}

function customFieldPreview(path: string) {
  if (customFieldPreviewUrls.value[path]) return customFieldPreviewUrls.value[path]
  if (isProtectedCustomFieldPath(path)) {
    void loadProtectedCustomFieldPreview(path)
    return ''
  }
  return path
}

function isProtectedCustomFieldPath(path: string) {
  return customFieldAttachmentId(path) !== null || reportImageId(path) !== null
}

function customFieldAttachmentId(path: string) {
  const match = path.match(/^\/api\/custom-field-attachments\/(\d+)\/file$/)
  return match ? Number(match[1]) : null
}

function reportImageId(path: string) {
  const match = path.match(/^\/api\/(?:mobile\/)?reports\/images\/(\d+)\/file$/)
  return match ? Number(match[1]) : null
}

async function loadProtectedCustomFieldPreview(path: string) {
  if (customFieldPreviewUrls.value[path] || loadingCustomFieldPreviews.has(path) || failedCustomFieldPreviews.has(path)) {
    return
  }
  loadingCustomFieldPreviews.add(path)
  try {
    const attachmentId = customFieldAttachmentId(path)
    const blob = attachmentId !== null
      ? await fetchCustomFieldAttachmentBlob(props.token, attachmentId)
      : await fetchReportImageBlob(props.token, reportImageId(path) as number)
    customFieldPreviewUrls.value = {
      ...customFieldPreviewUrls.value,
      [path]: URL.createObjectURL(blob)
    }
  } catch (e) {
    failedCustomFieldPreviews.add(path)
  } finally {
    loadingCustomFieldPreviews.delete(path)
  }
}

useRefreshOnActivated(() => {
  void loadReceipts()
  void loadProducts()
  void loadCustomFieldConfigs()
})

onMounted(() => {
  loadInboundPrintTemplate()
  loadCustomFieldConfigs()
  loadReceipts()
})

onBeforeUnmount(() => {
  Object.values(customFieldPreviewUrls.value).forEach(url => URL.revokeObjectURL(url))
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
          <button class="link-btn" type="button" @click="openPrintTemplateModal">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            编辑打印模板
          </button>
        </div>
        <div style="margin-left:auto;display:flex;gap:12px;align-items:center">
          <button class="table-config-trigger" type="button" title="列设置" @click="showColumnConfig = true">
            <UiIcon name="table-columns" :size="16" />
          </button>
        </div>
      </div>

      <ColumnSettingsPanel
        v-if="showColumnConfig"
        v-model="columns"
        :defaults="defaultColumnSettings"
        title="列设置"
        @confirm="saveInboundColumnSettings"
        @cancel="showColumnConfig = false"
      />

      <div class="table-container">
        <table class="data-table inbound-receipt-data-table">
          <thead>
            <tr>
              <th v-for="col in visibleColumns" :key="col.key" :style="inboundColumnStyle(col, true)">{{ col.label }}</th>
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
                <td v-if="col.key === 'index'" :style="inboundColumnStyle(col)">{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
                <td v-else-if="col.key === 'code'" :style="inboundColumnStyle(col)">{{ receipt.code }}</td>
                <td v-else-if="col.key === 'inboundType'" :style="inboundColumnStyle(col)">{{ receipt.inboundType }}</td>
                <td v-else-if="col.key === 'inboundTime'" :style="inboundColumnStyle(col)">{{ receipt.inboundTime }}</td>
                <td v-else-if="col.key === 'remark'" :style="inboundColumnStyle(col)">{{ receipt.remark || '-' }}</td>
                <td v-else-if="col.key === 'createdAt'" :style="inboundColumnStyle(col)">{{ receipt.createdAt }}</td>
                <td v-else-if="col.key === 'creator'" :style="inboundColumnStyle(col)">{{ receipt.creator || '-' }}</td>
                <td v-else-if="col.key === 'actions'" :style="inboundColumnStyle(col)">
                  <a class="link-btn" style="font-size:12px;cursor:pointer" @click="handleDelete(receipt)">删除</a>
                </td>
                <td v-else-if="col.isCustom" :style="inboundColumnStyle(col)">
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

      <div v-if="showPrintTemplateModal" class="modal-backdrop">
        <div class="modal workorder-modal inbound-print-template-modal">
          <div class="modal-header">
            <h2>编辑打印模板</h2>
            <button class="close-btn" type="button" @click="showPrintTemplateModal = false">×</button>
          </div>
          <div class="modal-body">
            <div class="form-grid one-col">
              <label>
                <span>模板名称</span>
                <input v-model.trim="inboundPrintTemplate.name" placeholder="请输入模板名称" />
              </label>
              <label>
                <span>打印标题</span>
                <input v-model.trim="inboundPrintTemplate.title" placeholder="请输入打印标题" />
              </label>
              <label>
                <span>页脚说明</span>
                <textarea v-model.trim="inboundPrintTemplate.footerText" rows="2" placeholder="请输入页脚说明"></textarea>
              </label>
            </div>
            <div class="print-field-panel">
              <div class="print-field-title">打印字段</div>
              <div class="print-field-grid">
                <label v-for="field in inboundPrintFields" :key="field.key" class="print-field-option">
                  <input v-model="inboundPrintTemplate.showFields" type="checkbox" :value="field.key" />
                  <span>{{ field.label }}</span>
                </label>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="ghost-btn wide" type="button" @click="showPrintTemplateModal = false">取消</button>
            <button class="primary-btn wide" type="button" @click="saveInboundPrintTemplate">保存</button>
          </div>
        </div>
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
                <UiIcon name="upload" />
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
                  v-if="customFieldPreview(path)"
                  :src="customFieldPreview(path)"
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
                <UiIcon name="upload" />
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
                  v-if="customFieldPreview(path)"
                  :src="customFieldPreview(path)"
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
          </div>
          <div style="margin-left:auto; position: relative;">
            <button class="table-config-trigger" type="button" title="字段配置" aria-label="字段配置" @click="showItemColumnConfig = !showItemColumnConfig">
              <UiIcon name="table-columns" :size="16" />
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
        <button class="btn-save" type="button" @click="handleSave" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
      </div>
    </template>

    <!-- Add Product Modal -->
    <div v-if="showAddProduct" class="modal-backdrop" @click.self="showAddProduct = false">
      <div class="modal workorder-modal inbound-add-product-modal">
        <div class="modal-header">
          <h2>添加产品</h2>
          <button class="close-btn" type="button" @click="showAddProduct = false">×</button>
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
.inbound-add-product-modal {
  width: min(520px, calc(100vw - 40px));
  overflow: visible;
}

.inbound-print-template-modal {
  width: min(620px, calc(100vw - 40px));
  overflow: hidden;
}

.inbound-print-template-modal .modal-footer {
  padding: 0 24px 24px;
}

.print-field-panel {
  margin-top: 18px;
  padding: 14px 16px;
  border: 1px solid #e1e9e6;
  border-radius: 6px;
  background: #f8fbfa;
}

.print-field-title {
  margin-bottom: 12px;
  color: #26332f;
  font-size: 14px;
  font-weight: 600;
}

.print-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.print-field-option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #485652;
  font-size: 13px;
  cursor: pointer;
}

.print-field-option input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

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
