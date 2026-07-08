<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import QRCode from 'qrcode'
import {
  createWorkOrder,
  deleteWorkOrder,
  fetchCustomFields,
  fetchProcessRoutes,
  fetchProcesses,
  fetchProducts,
  fetchWorkOrders,
  updateWorkOrder,
  type CustomFieldInfo,
  type ProcessInfo,
  type ProcessRouteInfo,
  type ProductInfo,
  type WorkOrderCustomValue,
  type WorkOrderInfo,
  type WorkOrderTaskPayload,
  type WorkOrderCategoryInfo,
  fetchWorkOrderCategories,
  createWorkOrderCategory,
  deleteWorkOrderCategory,
  fetchUserPreference,
  setUserPreference,
  fetchSystemImportLogs,
  createSystemImportLog,
  fetchSystemOperationLogs,
  createSystemOperationLog,
  fetchCustomFieldAttachmentBlob,
  fetchReportImageBlob,
  fetchBoms,
  type BomInfo,
  type WorkOrderMaterialPayload
} from '../api'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import SystemLogsDialog from './SystemLogsDialog.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible: deleteVisible, title: deleteTitle, message: deleteMessage, loading: deleteLoading, confirm: deleteConfirm, handleConfirm: deleteHandleConfirm, handleCancel: deleteHandleCancel } = useDeleteConfirm()

const workOrders = ref<WorkOrderInfo[]>([])
const categories = ref<WorkOrderCategoryInfo[]>([])
const products = ref<ProductInfo[]>([])
const boms = ref<BomInfo[]>([])
const selectedProduct = computed(() => {
  if (!form.productId) return null
  return products.value.find(p => p.id === form.productId) || null
})
const processes = ref<ProcessInfo[]>([])
const routes = ref<ProcessRouteInfo[]>([])
const customFields = ref<CustomFieldInfo[]>([])
const selectedIds = ref<number[]>([])
const showModal = ref(false)
const activeTab = ref<'basic' | 'custom' | 'task' | 'material'>('basic')
const editing = ref<WorkOrderInfo | null>(null)
const categoryFilter = ref('all')
const statusFilter = ref('all')
const keyword = ref('')

const showColumnConfig = ref(false)
const showColumnModal = ref(false)
const showCategoryModal = ref(false)
const newCategoryName = ref('')

const taskTooltip = reactive({
  visible: false,
  x: 0,
  y: 0,
  task: null as any
})

function showTaskTooltip(e: MouseEvent, task: any) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  taskTooltip.task = task
  taskTooltip.x = rect.left + rect.width / 2
  taskTooltip.y = rect.bottom
  taskTooltip.visible = true
}

function hideTaskTooltip() {
  taskTooltip.visible = false
}

const columnDefinitions = [
  { key: 'code', label: '工单编号' },
  { key: 'productCode', label: '产品编号' },
  { key: 'productName', label: '产品名称' },
  { key: 'productSpec', label: '产品规格' },
  { key: 'plannedQuantity', label: '计划数' },
  { key: 'status', label: '状态' },
  { key: 'priority', label: '优先级' },
  { key: 'routeName', label: '工单进度条' }
]
const visibleColumns = ref<string[]>(columnDefinitions.map(c => c.key))

// 筛选、行高、导入日志、操作记录
const showFilterPanel = ref(false)
const filterParams = ref({
  priority: 'all',
  plannedQtyMin: null as number | null,
  plannedQtyMax: null as number | null,
  planStartMin: '',
  planStartMax: '',
  planEndMin: '',
  planEndMax: ''
})

function resetFilters() {
  filterParams.value = {
    priority: 'all',
    plannedQtyMin: null,
    plannedQtyMax: null,
    planStartMin: '',
    planStartMax: '',
    planEndMin: '',
    planEndMax: ''
  }
}

const showRowHeightPopover = ref(false)

async function selectRowHeight(h: 'normal' | 'compact' | 'loose') {
  rowHeight.value = h
  try {
    await setUserPreference(props.token, 'WORK_ORDER_ROW_HEIGHT', h)
  } catch (e) {
    // ignore
  }
  showRowHeightPopover.value = false
  emit('notice', h === 'compact' ? '已切换为紧凑行高' : h === 'loose' ? '已切换为宽松行高' : '已切换为标准行高')
}

const showImportLogModal = ref(false)
const workOrderImportLogs = ref<any[]>([])

const showLogs = ref(false)
const logs = ref<any[]>([])

async function refreshImportLogs() {
  try {
    const res = await fetchSystemImportLogs(props.token, 'WORK_ORDER')
    workOrderImportLogs.value = res.items.map(item => ({
      id: String(item.id),
      fileName: item.fileName,
      time: item.createdAt,
      count: item.totalCount,
      successCount: item.successCount,
      failCount: item.failCount,
      status: item.status,
      operator: item.operatorName,
      errors: item.errors ? item.errors.split('\n') : []
    }))
  } catch (e) {
    // ignore
  }
}

async function refreshLogs() {
  try {
    const res = await fetchSystemOperationLogs(props.token, 'WORK_ORDER')
    logs.value = res.items
  } catch (e) {
    // ignore
  }
}

async function openLogs() {
  showLogs.value = true
  await refreshLogs()
}

async function addOperationLog(message: string) {
  let actionType = 'OPERATE'
  if (message.includes('创建')) actionType = 'CREATE'
  else if (message.includes('修改')) actionType = 'UPDATE'
  else if (message.includes('删除')) actionType = 'DELETE'
  else if (message.includes('状态变更为')) actionType = 'BATCH_STATUS'

  try {
    await createSystemOperationLog(props.token, {
      module: 'WORK_ORDER',
      actionType: actionType,
      detail: message
    })
    await refreshLogs()
  } catch (e) {
    // ignore
  }
}

const form = reactive({
  code: '',
  productId: '' as number | '',
  plannedQuantity: 0,
  status: 'NOT_STARTED',
  priority: 'NORMAL',
  planStart: '',
  planEnd: '',
  relatedOrderCode: '',
  remark: '',
  goodQuantity: 0,
  defectQuantity: 0,
  defectRate: '',
  categoryId: '' as number | '',
  customValues: [] as WorkOrderCustomValue[],
  tasks: [] as WorkOrderTaskPayload[],
  materials: [] as WorkOrderMaterialPayload[],
  version: 0
})

const selectedProductBom = computed(() => {
  if (!form.productId) return null
  return boms.value.find(item => item.productId === Number(form.productId) && item.enabled) || null
})

function isToday(item: WorkOrderInfo) {
  if (!item.createdAt) return false
  const d = new Date(item.createdAt)
  const today = new Date()
  return d.getDate() === today.getDate() && d.getMonth() === today.getMonth() && d.getFullYear() === today.getFullYear()
}

function isDueSoon(item: WorkOrderInfo) {
  if (!item.planEnd) return false
  const d = new Date(item.planEnd)
  const now = new Date()
  const diffDays = (d.getTime() - now.getTime()) / (1000 * 3600 * 24)
  return diffDays >= 0 && diffDays <= 3
}

const categoryTabs = computed(() => {
  const tabs = [
    { key: 'all', label: '全部工单' },
    { key: 'today', label: '今日计划单' },
    { key: 'urgent', label: '加急订单' },
    { key: 'due_soon', label: '三天到期订单' }
  ]
  categories.value.forEach(cat => {
    tabs.push({
      key: `cat_${cat.id}`,
      label: cat.name
    })
  })
  return tabs
})

const statusCards = computed(() => {
  // Use categoryFilter to pre-filter items for counting status
  const baseOrders = workOrders.value.filter(item => {
    if (categoryFilter.value === 'all') return true
    if (categoryFilter.value === 'today') return isToday(item)
    if (categoryFilter.value === 'urgent') return item.priority === 'HIGH' || item.priority === 'URGENT'
    if (categoryFilter.value === 'due_soon') return isDueSoon(item)
    if (categoryFilter.value.startsWith('cat_')) {
      return item.categoryId === Number(categoryFilter.value.replace('cat_', ''))
    }
    return true
  })

  return [
    { key: 'all', label: '全部', count: baseOrders.length },
    { key: 'NOT_STARTED', label: '未开始', count: baseOrders.filter(i => i.status === 'NOT_STARTED').length },
    { key: 'EXECUTING', label: '执行中', count: baseOrders.filter(i => i.status === 'EXECUTING').length },
    { key: 'COMPLETED', label: '已结束', count: baseOrders.filter(i => ['COMPLETED', 'FINISHED', 'CLOSED'].includes(i.status)).length },
    { key: 'CANCELED', label: '已取消', count: baseOrders.filter(i => i.status === 'CANCELED').length }
  ]
})

const filteredOrders = computed(() => {
  const result = workOrders.value.filter(item => {
    let catOk = false
    if (categoryFilter.value === 'all') catOk = true
    else if (categoryFilter.value === 'today') catOk = isToday(item)
    else if (categoryFilter.value === 'urgent') catOk = item.priority === 'HIGH' || item.priority === 'URGENT'
    else if (categoryFilter.value === 'due_soon') catOk = isDueSoon(item)
    else if (categoryFilter.value.startsWith('cat_')) {
      catOk = item.categoryId === Number(categoryFilter.value.replace('cat_', ''))
    }

    let statusOk = false
    if (statusFilter.value === 'all') statusOk = true
    else if (statusFilter.value === 'COMPLETED') statusOk = ['COMPLETED', 'FINISHED', 'CLOSED'].includes(item.status)
    else statusOk = item.status === statusFilter.value

    const text = `${item.code}${item.productCode}${item.productName}`.toLowerCase()
    const keywordOk = !keyword.value || text.includes(keyword.value.toLowerCase())
    
    // 高级筛选过滤
    let advPriorityOk = true
    if (filterParams.value.priority !== 'all') {
      advPriorityOk = item.priority === filterParams.value.priority
    }

    let qtyMinOk = true
    if (filterParams.value.plannedQtyMin !== null && filterParams.value.plannedQtyMin !== undefined) {
      qtyMinOk = item.plannedQuantity >= filterParams.value.plannedQtyMin
    }

    let qtyMaxOk = true
    if (filterParams.value.plannedQtyMax !== null && filterParams.value.plannedQtyMax !== undefined) {
      qtyMaxOk = item.plannedQuantity <= filterParams.value.plannedQtyMax
    }

    let planStartMinOk = true
    if (filterParams.value.planStartMin) {
      planStartMinOk = !item.planStart || new Date(item.planStart) >= new Date(filterParams.value.planStartMin)
    }

    let planStartMaxOk = true
    if (filterParams.value.planStartMax) {
      planStartMaxOk = !item.planStart || new Date(item.planStart) <= new Date(filterParams.value.planStartMax)
    }

    let planEndMinOk = true
    if (filterParams.value.planEndMin) {
      planEndMinOk = !item.planEnd || new Date(item.planEnd) >= new Date(filterParams.value.planEndMin)
    }

    let planEndMaxOk = true
    if (filterParams.value.planEndMax) {
      planEndMaxOk = !item.planEnd || new Date(item.planEnd) <= new Date(filterParams.value.planEndMax)
    }

    return catOk && statusOk && keywordOk && advPriorityOk && qtyMinOk && qtyMaxOk && planStartMinOk && planStartMaxOk && planEndMinOk && planEndMaxOk
  })
  result.sort((a, b) => {
    if (sortAsc.value) return a.id - b.id
    return b.id - a.id
  })
  return result
})

const totalPlannedQuantity = computed(() => {
  return filteredOrders.value.reduce((sum, item) => sum + (item.plannedQuantity || 0), 0)
})

const allSelected = computed(() =>
  filteredOrders.value.length > 0 && filteredOrders.value.every(item => selectedIds.value.includes(item.id))
)

function statusLabel(value: string) {
  return { NOT_STARTED: '未开始', EXECUTING: '执行中', PAUSED: '已暂停', COMPLETED: '已完成', FINISHED: '已结束', CLOSED: '已关闭', CANCELED: '已取消' }[value] || value
}

function priorityLabel(value: string) {
  return { LOW: '低', NORMAL: '正常', HIGH: '高', URGENT: '加急' }[value] || value
}

function resetForm() {
  Object.assign(form, {
    code: '',
    productId: '',
    plannedQuantity: 0,
    status: 'NOT_STARTED',
    priority: 'NORMAL',
    planStart: '',
    planEnd: '',
    relatedOrderCode: '',
    remark: '',
    goodQuantity: 0,
    defectQuantity: 0,
    defectRate: '',
    customValues: [],
    tasks: [],
    materials: [],
    version: 0
  })
}

async function loadData() {
  const [orderRes, productRes, processRes, routeRes, fieldRes, categoryRes, bomRes] = await Promise.all([
    fetchWorkOrders(props.token, { size: 500 }),
    fetchProducts(props.token, { size: 500 }),
    fetchProcesses(props.token, { size: 500 }),
    fetchProcessRoutes(props.token, { size: 500 }),
    fetchCustomFields(props.token, 'workorder'),
    fetchWorkOrderCategories(props.token),
    fetchBoms(props.token, { size: 500 })
  ])
  workOrders.value = orderRes.items
  products.value = productRes.items
  processes.value = processRes.items
  routes.value = routeRes.items
  customFields.value = fieldRes.items
  categories.value = categoryRes
  boms.value = bomRes.items
  
  try {
    const pref = await fetchUserPreference(props.token, 'WORK_ORDER_COLUMNS')
    if (pref && pref.value) {
      visibleColumns.value = JSON.parse(pref.value)
    }
  } catch (e) {
    // ignore
  }

  try {
    const heightPref = await fetchUserPreference(props.token, 'WORK_ORDER_ROW_HEIGHT')
    if (heightPref && heightPref.value) {
      rowHeight.value = heightPref.value as 'normal' | 'compact' | 'loose'
    }
  } catch (e) {
    // ignore
  }

  try {
    await refreshImportLogs()
    await refreshLogs()
  } catch (e) {
    // ignore
  }
}

async function handleSaveCategory() {
  if (!newCategoryName.value.trim()) return
  try {
    await createWorkOrderCategory(props.token, { name: newCategoryName.value.trim() })
    emit('notice', '分类添加成功')
    newCategoryName.value = ''
    showCategoryModal.value = false
    await loadData()
  } catch (e: any) {
    emit('notice', e.message || '添加失败')
  }
}

async function removeCategory(item: any, event: Event) {
  event.stopPropagation()
  if (!item.key.startsWith('cat_')) return
  const catId = Number(item.key.replace('cat_', ''))
  const catName = item.label
  
  const confirmed = await deleteConfirm({ message: `确定要删除分类 "${catName}" 吗？` })
  if (!confirmed) return
  
  try {
    await deleteWorkOrderCategory(props.token, catId)
    emit('notice', '分类删除成功')
    
    if (categoryFilter.value === item.key) {
      categoryFilter.value = 'all'
    }
    
    await loadData()
  } catch (e: any) {
    emit('notice', e.message || '删除分类失败')
  }
}

async function handleSaveColumns() {
  try {
    await setUserPreference(props.token, 'WORK_ORDER_COLUMNS', JSON.stringify(visibleColumns.value))
    showColumnModal.value = false
    emit('notice', '列配置已保存')
  } catch (e) {
    emit('notice', '配置保存失败')
  }
}

function openCreate() {
  editing.value = null
  resetForm()
  syncCustomValues()
  activeTab.value = 'basic'
  showModal.value = true
}

function openEdit(item: WorkOrderInfo) {
  editing.value = item
  Object.assign(form, {
    code: item.code,
    productId: item.productId,
    plannedQuantity: item.plannedQuantity,
    status: item.status,
    priority: item.priority,
    planStart: toInputTime(item.planStart),
    planEnd: toInputTime(item.planEnd),
    relatedOrderCode: item.relatedOrderCode || '',
    remark: item.remark || '',
    goodQuantity: item.goodQuantity || 0,
    defectQuantity: item.defectQuantity || 0,
    defectRate: item.defectRate || '',
    categoryId: item.categoryId || '',
    customValues: JSON.parse(JSON.stringify(item.customValues || [])),
    tasks: JSON.parse(JSON.stringify(item.tasks || [])),
    materials: JSON.parse(JSON.stringify(item.materials || [])),
    version: item.version
  })
  syncCustomValues()
  activeTab.value = 'basic'
  showModal.value = true
}

function syncCustomValues() {
  form.customValues = customFields.value.map(field => {
    const existing = form.customValues.find(item => item.fieldId === field.id || item.fieldName === field.name)
    const defaultValue = field.fieldType === 'multiple_select' 
      ? (field.defaultValues || []) 
      : (field.defaultValues && field.defaultValues.length > 0 
          ? (field.fieldType === 'number' ? Number(field.defaultValues[0]) : field.defaultValues[0]) 
          : (field.fieldType === 'number' ? null : ''))

    if (existing) {
      const isEmpty = existing.value === null || 
                      existing.value === undefined || 
                      existing.value === '' || 
                      (Array.isArray(existing.value) && existing.value.length === 0)
      if (isEmpty) {
        existing.value = defaultValue
      }
      return existing
    }

    return {
      fieldId: field.id,
      fieldName: field.name,
      fieldType: field.fieldType,
      value: defaultValue
    }
  })
}

function getCustomValue(field: CustomFieldInfo) {
  return form.customValues.find(item => item.fieldId === field.id)?.value
}

function setCustomValue(field: CustomFieldInfo, value: string | string[] | number | null) {
  const target = form.customValues.find(item => item.fieldId === field.id)
  if (target) target.value = value
}

function toggleMultipleSelectValue(field: CustomFieldInfo, optValue: string, checked: boolean) {
  const current = form.customValues.find(item => item.fieldId === field.id)
  if (current) {
    let arr = Array.isArray(current.value) ? [...current.value] : []
    if (checked) {
      if (!arr.includes(optValue)) arr.push(optValue)
    } else {
      arr = arr.filter((v: any) => v !== optValue)
    }
    current.value = arr
  }
}

const uploadingFieldId = ref<number | null>(null)
const customFieldPreviewUrls = ref<Record<string, string>>({})
const loadingCustomFieldPreviews = new Set<string>()
const failedCustomFieldPreviews = new Set<string>()

async function handleCustomFieldUpload(field: CustomFieldInfo, event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  uploadingFieldId.value = field.id
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scope', 'workorder')
    formData.append('fieldId', String(field.id))
    formData.append('fieldType', field.fieldType)
    
    const response = await fetch('/api/custom-field-attachments', {
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
    const currentVal = String(getCustomValue(field) || '')
    const newPath = data.accessPath
    customFieldPreviewUrls.value[newPath] = URL.createObjectURL(file)
    const updatedVal = currentVal ? `${currentVal},${newPath}` : newPath
    setCustomValue(field, updatedVal)
    emit('notice', '文件上传成功')
  } catch (err: any) {
    emit('notice', err.message || '上传失败')
  } finally {
    uploadingFieldId.value = null
    target.value = ''
  }
}

function removeCustomFieldPath(field: CustomFieldInfo, index: number) {
  const currentVal = String(getCustomValue(field) || '')
  const paths = currentVal.split(',').filter(Boolean)
  const removed = paths[index]
  if (removed && customFieldPreviewUrls.value[removed]) {
    URL.revokeObjectURL(customFieldPreviewUrls.value[removed])
    delete customFieldPreviewUrls.value[removed]
  }
  paths.splice(index, 1)
  setCustomValue(field, paths.join(','))
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

onBeforeUnmount(() => {
  Object.values(customFieldPreviewUrls.value).forEach(url => URL.revokeObjectURL(url))
})

function toInputTime(value?: string) {
  return value ? value.replace(' ', 'T').slice(0, 16) : ''
}

function fromInputTime(value: string) {
  return value ? value.replace('T', ' ') : undefined
}

function buildTasks(productId: number | '') {
  if (!productId) return []
  const product = products.value.find(item => item.id === productId)
  const route = routes.value.find(item => item.id === product?.routeId)
  if (!route) return []
  return route.steps
    .map((step, index) => {
      const process = processes.value.find(item => item.id === step.processId)
      if (!process) return null
      return {
        processName: process.name,
        processCode: process.code,
        sequenceNo: index + 1,
        plannedQuantity: Number(form.plannedQuantity || 0),
        planDeadline: fromInputTime(form.planEnd) || '',
        reportPermission: process.reportPermission?.[0] || '所有人',
        assignee: '',
        enabled: true
      }
    })
    .filter(Boolean) as WorkOrderTaskPayload[]
}

watch(() => form.productId, () => {
  if (!editing.value) {
    form.tasks = buildTasks(form.productId)
  }
})

watch(() => form.plannedQuantity, () => {
  form.tasks = form.tasks.map(task => ({ ...task, plannedQuantity: Number(form.plannedQuantity || 0) }))
})

// --- 生产任务表单扩展逻辑 ---
const selectedTaskIndexes = ref<number[]>([])
const showTaskSelector = ref(false)

const taskSelectorActiveTab = ref<'process' | 'route'>('process')
const taskSelectorKeyword = ref('')
const taskSelectorSelectedProcesses = ref<any[]>([])
const taskSelectorSelectedRoutes = ref<any[]>([])

const filteredProcesses = computed(() => {
  const kw = taskSelectorKeyword.value.trim().toLowerCase()
  if (!kw) return processes.value
  return processes.value.filter(p => 
    p.name.toLowerCase().includes(kw) || 
    (p.code && p.code.toLowerCase().includes(kw))
  )
})

const filteredRoutes = computed(() => {
  const kw = taskSelectorKeyword.value.trim().toLowerCase()
  if (!kw) return routes.value
  return routes.value.filter(r => 
    r.name.toLowerCase().includes(kw) || 
    (r.code && r.code.toLowerCase().includes(kw))
  )
})

function getStepName(step: any): string {
  const process = processes.value.find(item => item.id === step.processId)
  if (process) return process.name
  const subRoute = routes.value.find(item => item.id === step.processId)
  if (subRoute) return subRoute.name
  return '未知步骤'
}

function getRouteStepsPreview(r: any): string {
  if (!r.steps || r.steps.length === 0) return '无步骤'
  return r.steps.map((step: any) => getStepName(step)).join(' ➔ ')
}

function toggleSelectProcessInSelector(p: any) {
  const idx = taskSelectorSelectedProcesses.value.findIndex(item => item.id === p.id)
  if (idx > -1) {
    taskSelectorSelectedProcesses.value.splice(idx, 1)
  } else {
    taskSelectorSelectedProcesses.value.push(p)
  }
}

function isProcessSelectedInSelector(p: any) {
  return taskSelectorSelectedProcesses.value.some(item => item.id === p.id)
}

function toggleSelectRouteInSelector(r: any) {
  const idx = taskSelectorSelectedRoutes.value.findIndex(item => item.id === r.id)
  if (idx > -1) {
    taskSelectorSelectedRoutes.value.splice(idx, 1)
  } else {
    taskSelectorSelectedRoutes.value.push(r)
  }
}

function isRouteSelectedInSelector(r: any) {
  return taskSelectorSelectedRoutes.value.some(item => item.id === r.id)
}

function handleConfirmSelectorAdd() {
  if (taskSelectorActiveTab.value === 'process') {
    taskSelectorSelectedProcesses.value.forEach(p => {
      addTaskFromProcess(p)
    })
  } else {
    taskSelectorSelectedRoutes.value.forEach(r => {
      addTaskFromRoute(r)
    })
  }
  showTaskSelector.value = false
  taskSelectorSelectedProcesses.value = []
  taskSelectorSelectedRoutes.value = []
  taskSelectorKeyword.value = ''
}

watch(showTaskSelector, (newVal) => {
  if (newVal) {
    taskSelectorSelectedProcesses.value = []
    taskSelectorSelectedRoutes.value = []
    taskSelectorKeyword.value = ''
    taskSelectorActiveTab.value = 'process'
  }
})

const taskDraggingIdx = ref(-1)
const taskDragOverIdx = ref(-1)
const showTaskEditModal = ref(false)

const taskEditForm = reactive({
  plannedQuantity: 0,
  planDeadline: '',
  reportPermission: '',
  enabled: true
})

const allTasksSelected = computed(() => form.tasks.length > 0 && selectedTaskIndexes.value.length === form.tasks.length)

function toggleTaskAll(checked: boolean) {
  selectedTaskIndexes.value = checked ? form.tasks.map((_, i) => i) : []
}

function toggleTaskOne(idx: number, checked: boolean) {
  if (checked) {
    if (!selectedTaskIndexes.value.includes(idx)) selectedTaskIndexes.value.push(idx)
  } else {
    selectedTaskIndexes.value = selectedTaskIndexes.value.filter(i => i !== idx)
  }
}

function removeSelectedTasks() {
  if (selectedTaskIndexes.value.length === 0) return
  form.tasks = form.tasks.filter((_, i) => !selectedTaskIndexes.value.includes(i))
  // 重排 sequenceNo
  form.tasks.forEach((t, i) => t.sequenceNo = i + 1)
  selectedTaskIndexes.value = []
}

function addTaskFromProcess(p: any) {
  form.tasks.push({
    processName: p.name,
    processCode: p.code,
    sequenceNo: form.tasks.length + 1,
    plannedQuantity: Number(form.plannedQuantity || 0),
    planDeadline: fromInputTime(form.planEnd) || '',
    reportPermission: p.reportPermission?.[0] || '所有人',
    assignee: '',
    enabled: true
  })
  showTaskSelector.value = false
}

function flattenRouteSteps(steps: any[]): any[] {
  const result: any[] = []
  if (!steps) return result
  steps.forEach((step: any) => {
    const process = processes.value.find(item => item.id === step.processId)
    if (process) {
      result.push(process)
    } else {
      const subRoute = routes.value.find(item => item.id === step.processId)
      if (subRoute && subRoute.steps) {
        result.push(...flattenRouteSteps(subRoute.steps))
      }
    }
  })
  return result
}

function addTaskFromRoute(r: any) {
  if (!r.steps) return
  
  const flatProcesses = flattenRouteSteps(r.steps)
  const newTasks = flatProcesses.map((process: any) => {
    return {
      processName: process.name,
      processCode: process.code,
      sequenceNo: 0,
      plannedQuantity: Number(form.plannedQuantity || 0),
      planDeadline: fromInputTime(form.planEnd) || '',
      reportPermission: process.reportPermission?.[0] || '所有人',
      assignee: '',
      enabled: true
    }
  }) as WorkOrderTaskPayload[]
  
  form.tasks.push(...newTasks)
  // 重排
  form.tasks.forEach((t, i) => t.sequenceNo = i + 1)
  showTaskSelector.value = false
}

function openTaskEdit(idx?: number) {
  if (idx !== undefined) {
    selectedTaskIndexes.value = [idx]
  }
  if (selectedTaskIndexes.value.length === 0) return
  
  const first = form.tasks[selectedTaskIndexes.value[0]]
  taskEditForm.plannedQuantity = first.plannedQuantity || 0
  taskEditForm.planDeadline = toInputTime(first.planDeadline)
  taskEditForm.reportPermission = first.reportPermission || ''
  taskEditForm.enabled = first.enabled ?? true
  showTaskEditModal.value = true
}

function saveTaskEdit() {
  selectedTaskIndexes.value.forEach(idx => {
    const t = form.tasks[idx]
    if (t) {
      t.plannedQuantity = taskEditForm.plannedQuantity
      t.planDeadline = fromInputTime(taskEditForm.planDeadline) || ''
      t.reportPermission = taskEditForm.reportPermission
      t.enabled = taskEditForm.enabled
    }
  })
  showTaskEditModal.value = false
}

function onTaskDragStart(idx: number, e: DragEvent) {
  taskDraggingIdx.value = idx
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(idx))
  }
}
function onTaskDragOver(idx: number, e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  taskDragOverIdx.value = idx
}
function onTaskDrop(idx: number) {
  const dragIdx = taskDraggingIdx.value
  taskDragOverIdx.value = -1
  taskDraggingIdx.value = -1
  if (dragIdx === -1 || dragIdx === idx) return
  
  const moved = form.tasks.splice(dragIdx, 1)[0]
  form.tasks.splice(idx, 0, moved)
  // 重排
  form.tasks.forEach((t, i) => t.sequenceNo = i + 1)
  selectedTaskIndexes.value = []
}
function onTaskDragEnd() {
  taskDragOverIdx.value = -1
  taskDraggingIdx.value = -1
}

function addMaterial() {
  form.materials.push({
    materialCode: '',
    materialName: '',
    materialSpec: '',
    quantity: Math.max(1, Number(form.plannedQuantity || 1))
  })
}

function removeMaterial(index: number) {
  form.materials.splice(index, 1)
}

function applyBomMaterials() {
  const bom = selectedProductBom.value
  if (!bom) {
    emit('notice', '当前产品没有启用的物料清单')
    return
  }
  const plannedQty = Math.max(1, Number(form.plannedQuantity || 1))
  const outputQty = Math.max(1, Number(bom.outputQuantity || 1))
  form.materials = bom.items.map(item => ({
    materialCode: item.productCode || '',
    materialName: item.productName || '',
    materialSpec: item.productSpec || '',
    quantity: Math.max(1, Math.ceil(Number(item.quantity || 0) * plannedQty / outputQty))
  }))
  emit('notice', `已按 BOM「${bom.code}」带入 ${form.materials.length} 条用料`)
}

function normalizedMaterials() {
  return form.materials
    .map(item => ({
      materialCode: (item.materialCode || '').trim(),
      materialName: (item.materialName || '').trim(),
      materialSpec: (item.materialSpec || '').trim(),
      quantity: Math.max(0, Math.floor(Number(item.quantity || 0)))
    }))
    .filter(item => item.materialName && item.quantity > 0)
}

async function saveOrder() {
  if (!form.productId) {
    emit('notice', '请选择产品')
    activeTab.value = 'basic'
    return
  }
  if (Number(form.plannedQuantity || 0) <= 0) {
    emit('notice', '计划数必须大于 0')
    activeTab.value = 'basic'
    return
  }
  const payload = {
    code: form.code || undefined,
    productId: Number(form.productId),
    plannedQuantity: Number(form.plannedQuantity || 0),
    status: form.status,
    priority: form.priority,
    planStart: fromInputTime(form.planStart),
    planEnd: fromInputTime(form.planEnd),
    relatedOrderCode: form.relatedOrderCode || undefined,
    remark: form.remark || undefined,
    goodQuantity: Number(form.goodQuantity || 0),
    defectQuantity: Number(form.defectQuantity || 0),
    defectRate: form.defectRate || undefined,
    categoryId: form.categoryId ? Number(form.categoryId) : undefined,
    customValues: form.customValues,
    tasks: form.tasks,
    materials: normalizedMaterials(),
    version: editing.value ? form.version : undefined
  }
  try {
    if (editing.value) {
      await updateWorkOrder(props.token, editing.value.id, payload)
      emit('notice', '工单已更新')
      addOperationLog(`修改了工单 ${editing.value.code} 的基本配置或任务详情`)
    } else {
      const res = await createWorkOrder(props.token, payload)
      emit('notice', '工单已创建')
      addOperationLog(`创建了新工单 ${payload.code || (res && res.code) || ''}，计划数 ${payload.plannedQuantity} 件`)
    }
    showModal.value = false
    await loadData()
  } catch (e: any) {
    emit('notice', e.message || '操作失败')
  }
}

async function removeOrder(item: WorkOrderInfo) {
  const confirmed = await deleteConfirm({ message: `确认删除工单「${item.code}」？` })
  if (!confirmed) return
  deleteLoading.value = true
  try {
    await deleteWorkOrder(props.token, item.id)
    emit('notice', '工单已删除')
    addOperationLog(`删除了工单 ${item.code}`)
    selectedIds.value = selectedIds.value.filter(id => id !== item.id)
    await loadData()
  } catch (e: any) {
    emit('notice', e.message || '删除失败')
  } finally {
    deleteLoading.value = false
    deleteVisible.value = false
  }
}

function toggleAll(checked: boolean) {
  selectedIds.value = checked ? filteredOrders.value.map(item => item.id) : []
}

function toggleOne(id: number, checked: boolean) {
  selectedIds.value = checked
    ? Array.from(new Set([...selectedIds.value, id]))
    : selectedIds.value.filter(item => item !== id)
}

const sortAsc = ref(false)
function toggleSort() {
  sortAsc.value = !sortAsc.value
  emit('notice', sortAsc.value ? '已按创建顺序升序排列' : '已按创建顺序降序排列')
}

const rowHeight = ref<'normal' | 'compact' | 'loose'>('normal')
function toggleRowHeight() {
  if (rowHeight.value === 'normal') {
    rowHeight.value = 'compact'
    emit('notice', '已切换为紧凑行高')
  } else if (rowHeight.value === 'compact') {
    rowHeight.value = 'loose'
    emit('notice', '已切换为宽松行高')
  } else {
    rowHeight.value = 'normal'
    emit('notice', '已切换为标准行高')
  }
}

function handleExport() {
  const targets = selectedIds.value.length > 0 
    ? filteredOrders.value.filter(o => selectedIds.value.includes(o.id))
    : filteredOrders.value

  if (targets.length === 0) {
    emit('notice', '没有可导出的数据')
    return
  }

  // CSV 单元格转义：含逗号、换行、双引号时用双引号包裹
  function csvEscape(val: any): string {
    if (val === null || val === undefined) return ''
    const str = String(val)
    if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
      return '"' + str.replace(/"/g, '""') + '"'
    }
    return str
  }

  // 计算最大工序数和最大物料数
  let maxTasks = 0
  let maxMaterials = 0
  targets.forEach(item => {
    if (item.tasks && item.tasks.length > maxTasks) maxTasks = item.tasks.length
    if (item.materials && item.materials.length > maxMaterials) maxMaterials = item.materials.length
  })

  // 固定表头
  const fixedHeaders = [
    '工单编号', '产品编号', '产品名称', '产品规格',
    '状态', '优先级', '计划数', '良品数', '不良品数', '不良率',
    '工单进度', '工艺路线', '分类',
    '计划开始', '计划结束', '关联订单号', '客户名称',
    '备注', '创建人', '创建时间', '更新时间'
  ]

  // 动态工序表头
  const taskHeaders: string[] = []
  for (let t = 1; t <= maxTasks; t++) {
    taskHeaders.push(
      `工序${t}-名称`, `工序${t}-编号`, `工序${t}-序号`,
      `工序${t}-计划数`, `工序${t}-完成数`, `工序${t}-不良数`,
      `工序${t}-已审数`, `工序${t}-未审数`,
      `工序${t}-负责人`, `工序${t}-截止时间`, `工序${t}-报工权限`,
      `工序${t}-最后报工`, `工序${t}-启用`
    )
  }

  // 动态物料表头
  const materialHeaders: string[] = []
  for (let m = 1; m <= maxMaterials; m++) {
    materialHeaders.push(
      `物料${m}-编号`, `物料${m}-名称`, `物料${m}-规格`, `物料${m}-数量`
    )
  }

  // 动态自定义字段表头
  const customHeaders = customFields.value.map(f => f.name)
  const headers = [...fixedHeaders, ...taskHeaders, ...materialHeaders, ...customHeaders]

  const rows = targets.map(item => {
    // 自定义字段值
    const customCols = customFields.value.map(field => {
      const cv = item.customValues?.find(v => v.fieldId === field.id || v.fieldName === field.name)
      if (!cv || cv.value === null || cv.value === undefined) return ''
      if (Array.isArray(cv.value)) return cv.value.join('；')
      return String(cv.value)
    })

    // 工序列
    const taskCols: string[] = []
    for (let t = 0; t < maxTasks; t++) {
      const task = item.tasks?.[t]
      if (task) {
        taskCols.push(
          task.processName || '',
          task.processCode || '',
          task.sequenceNo != null ? String(task.sequenceNo) : '',
          task.plannedQuantity != null ? String(task.plannedQuantity) : '',
          task.completedQuantity != null ? String(task.completedQuantity) : '',
          task.defectQuantity != null ? String(task.defectQuantity) : '',
          task.approvedQuantity != null ? String(task.approvedQuantity) : '',
          task.unapprovedQuantity != null ? String(task.unapprovedQuantity) : '',
          task.assignee || '',
          task.planDeadline || '',
          task.reportPermission || '',
          task.lastReportTime || '',
          task.enabled === false ? '否' : '是'
        )
      } else {
        taskCols.push('', '', '', '', '', '', '', '', '', '', '', '', '')
      }
    }

    // 物料列
    const materialCols: string[] = []
    for (let m = 0; m < maxMaterials; m++) {
      const mat = item.materials?.[m]
      if (mat) {
        materialCols.push(
          mat.materialCode || '',
          mat.materialName || '',
          mat.materialSpec || '',
          mat.quantity != null ? String(mat.quantity) : ''
        )
      } else {
        materialCols.push('', '', '', '')
      }
    }

    return [
      item.code,
      item.productCode,
      item.productName,
      item.productSpec || '',
      statusLabel(item.status),
      priorityLabel(item.priority),
      item.plannedQuantity,
      item.goodQuantity,
      item.defectQuantity,
      item.defectRate || '',
      item.progressPercent != null ? item.progressPercent + '%' : '',
      item.routeName || '',
      item.categoryName || '',
      item.planStart || '',
      item.planEnd || '',
      item.relatedOrderCode || '',
      item.customerName || '',
      item.remark || '',
      item.creator || '',
      item.createdAt || '',
      item.updatedAt || '',
      ...taskCols,
      ...materialCols,
      ...customCols
    ].map(csvEscape)
  })

  const csvContent = [headers.map(csvEscape).join(',')]
    .concat(rows.map(row => row.join(',')))
    .join('\n')
  
  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `工单列表_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  emit('notice', '已导出 CSV 文件')
}

function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.csv'
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return

    emit('notice', `正在解析并上传 CSV 文件: ${file.name}...`)
    const reader = new FileReader()
    reader.onload = async (event) => {
      const text = event.target?.result as string
      if (!text) {
        emit('notice', '文件内容为空')
        return
      }

      // 按行分割 CSV 内容
      const lines = text.split(/\r?\n/)
      if (lines.length <= 1) {
        emit('notice', '导入数据为空')
        return
      }

      // 提取表头 — 支持带双引号的 CSV 字段
      const headerLine = lines[0].replace(/^\ufeff/, '').trim()

      // CSV 行解析器：正确处理双引号包裹的字段（含逗号、换行、双引号转义）
      function parseCsvLine(line: string): string[] {
        const result: string[] = []
        let current = ''
        let inQuotes = false
        for (let c = 0; c < line.length; c++) {
          const ch = line[c]
          if (inQuotes) {
            if (ch === '"') {
              if (c + 1 < line.length && line[c + 1] === '"') {
                current += '"'
                c++ // skip escaped quote
              } else {
                inQuotes = false
              }
            } else {
              current += ch
            }
          } else {
            if (ch === '"') {
              inQuotes = true
            } else if (ch === ',') {
              result.push(current.trim())
              current = ''
            } else {
              current += ch
            }
          }
        }
        result.push(current.trim())
        return result
      }

      const cols = parseCsvLine(headerLine)
      
      // 标准固定列
      const idxCode = cols.indexOf('工单编号')
      const idxProductCode = cols.indexOf('产品编号')
      const idxProductName = cols.indexOf('产品名称')
      const idxProductSpec = cols.indexOf('产品规格')
      const idxStatus = cols.indexOf('状态')
      const idxPriority = cols.indexOf('优先级')
      const idxPlannedQty = cols.indexOf('计划数')
      const idxGoodQty = cols.indexOf('良品数')
      const idxDefectQty = cols.indexOf('不良品数')
      // 新增字段列索引
      const idxDefectRate = cols.indexOf('不良率')
      const idxPlanStart = cols.indexOf('计划开始')
      const idxPlanEnd = cols.indexOf('计划结束')
      const idxRelatedOrder = cols.indexOf('关联订单号')
      const idxRemark = cols.indexOf('备注')
      const idxCategory = cols.indexOf('分类')

      // 动态工序列索引检测：工序1-名称, 工序1-编号, ..., 工序N-名称, ...
      const taskColGroups: { name: number; code: number; seq: number; planned: number; completed: number; defect: number; approved: number; unapproved: number; assignee: number; deadline: number; permission: number; lastReport: number; enabled: number }[] = []
      for (let t = 1; t <= 50; t++) {
        const nameIdx = cols.indexOf(`工序${t}-名称`)
        if (nameIdx === -1) break
        taskColGroups.push({
          name: nameIdx,
          code: cols.indexOf(`工序${t}-编号`),
          seq: cols.indexOf(`工序${t}-序号`),
          planned: cols.indexOf(`工序${t}-计划数`),
          completed: cols.indexOf(`工序${t}-完成数`),
          defect: cols.indexOf(`工序${t}-不良数`),
          approved: cols.indexOf(`工序${t}-已审数`),
          unapproved: cols.indexOf(`工序${t}-未审数`),
          assignee: cols.indexOf(`工序${t}-负责人`),
          deadline: cols.indexOf(`工序${t}-截止时间`),
          permission: cols.indexOf(`工序${t}-报工权限`),
          lastReport: cols.indexOf(`工序${t}-最后报工`),
          enabled: cols.indexOf(`工序${t}-启用`)
        })
      }

      // 动态物料列索引检测：物料1-编号, 物料1-名称, ..., 物料N-编号, ...
      const materialColGroups: { code: number; name: number; spec: number; qty: number }[] = []
      for (let m = 1; m <= 50; m++) {
        const nameIdx = cols.indexOf(`物料${m}-名称`)
        if (nameIdx === -1) break
        materialColGroups.push({
          code: cols.indexOf(`物料${m}-编号`),
          name: nameIdx,
          spec: cols.indexOf(`物料${m}-规格`),
          qty: cols.indexOf(`物料${m}-数量`)
        })
      }

      // 自定义字段列索引映射
      const customFieldIdxMap: { fieldId: number; fieldName: string; fieldType: string; colIdx: number }[] = []
      customFields.value.forEach(field => {
        const idx = cols.indexOf(field.name)
        if (idx !== -1) {
          customFieldIdxMap.push({ fieldId: field.id!, fieldName: field.name, fieldType: field.fieldType, colIdx: idx })
        }
      })

      if (idxProductCode === -1 && idxProductName === -1) {
        emit('notice', 'CSV文件格式不正确，缺少产品编号或产品名称列')
        return
      }

      let successCount = 0
      let failCount = 0
      const errorsList: string[] = []

      for (let i = 1; i < lines.length; i++) {
        const line = lines[i].trim()
        if (!line) continue // 忽略空行

        const rowValues = parseCsvLine(line)
        if (rowValues.length < 2) continue

        const pCode = idxProductCode !== -1 ? rowValues[idxProductCode] : ''
        const pName = idxProductName !== -1 ? rowValues[idxProductName] : ''
        
        // 1. 根据产品编号或名称寻找真实的产品
        const matchedProduct = products.value.find(p => 
          (pCode && p.code === pCode) || (pName && p.name === pName)
        )

        if (!matchedProduct) {
          failCount++
          errorsList.push(`第 ${i} 行：未匹配到系统已有的产品编号/名称 (${pCode || pName})`)
          continue
        }

        // 2. 转换状态和优先级
        let priorityVal = 'NORMAL'
        if (idxPriority !== -1 && rowValues[idxPriority]) {
          const csvPriority = rowValues[idxPriority]
          if (csvPriority === '加急' || csvPriority === 'URGENT') priorityVal = 'URGENT'
          else if (csvPriority === '高' || csvPriority === 'HIGH') priorityVal = 'HIGH'
          else if (csvPriority === '低' || csvPriority === 'LOW') priorityVal = 'LOW'
          else if (csvPriority === '正常' || csvPriority === 'NORMAL') priorityVal = 'NORMAL'
        }

        let statusVal = 'NOT_STARTED'
        if (idxStatus !== -1 && rowValues[idxStatus]) {
          const csvStatus = rowValues[idxStatus]
          if (csvStatus === '执行中' || csvStatus === '运行中' || csvStatus === 'EXECUTING') statusVal = 'EXECUTING'
          else if (csvStatus === '已暂停' || csvStatus === 'PAUSED') statusVal = 'PAUSED'
          else if (csvStatus === '已完成' || csvStatus === '已结束' || csvStatus === 'COMPLETED') statusVal = 'COMPLETED'
          else if (csvStatus === '已取消' || csvStatus === 'CANCELLED') statusVal = 'CANCELLED'
          else if (csvStatus === '未开始' || csvStatus === 'NOT_STARTED') statusVal = 'NOT_STARTED'
        }

        const plannedQty = idxPlannedQty !== -1 ? Number(rowValues[idxPlannedQty] || 0) : 0
        if (plannedQty <= 0) {
          failCount++
          errorsList.push(`第 ${i} 行：计划数必须大于 0`)
          continue
        }

        // 3. 分类匹配
        let categoryId: number | undefined = undefined
        if (idxCategory !== -1 && rowValues[idxCategory]) {
          const catName = rowValues[idxCategory]
          const matchedCat = categories.value.find(c => c.name === catName)
          if (matchedCat) categoryId = matchedCat.id
        }

        // 4. 自定义字段值
        const importedCustomValues: { fieldId: number; fieldName: string; fieldType: string; value: any }[] = []
        customFieldIdxMap.forEach(mapping => {
          const raw = rowValues[mapping.colIdx] || ''
          if (!raw) return
          let val: any = raw
          if (mapping.fieldType === 'number') {
            val = Number(raw) || 0
          } else if (mapping.fieldType === 'multiple_select') {
            val = raw.split(/[；;]/).map(s => s.trim()).filter(Boolean)
          }
          importedCustomValues.push({
            fieldId: mapping.fieldId,
            fieldName: mapping.fieldName,
            fieldType: mapping.fieldType,
            value: val
          })
        })

        // 5. 构建工单 Payload 并调用接口创建
        const payload: any = {
          code: idxCode !== -1 ? rowValues[idxCode] || undefined : undefined,
          productId: matchedProduct.id,
          plannedQuantity: plannedQty,
          status: statusVal,
          priority: priorityVal,
          goodQuantity: idxGoodQty !== -1 ? Number(rowValues[idxGoodQty] || 0) : 0,
          defectQuantity: idxDefectQty !== -1 ? Number(rowValues[idxDefectQty] || 0) : 0,
          defectRate: idxDefectRate !== -1 ? rowValues[idxDefectRate] || undefined : undefined,
          planStart: idxPlanStart !== -1 ? rowValues[idxPlanStart] || undefined : undefined,
          planEnd: idxPlanEnd !== -1 ? rowValues[idxPlanEnd] || undefined : undefined,
          relatedOrderCode: idxRelatedOrder !== -1 ? rowValues[idxRelatedOrder] || undefined : undefined,
          remark: idxRemark !== -1 ? rowValues[idxRemark] || undefined : undefined,
          categoryId,
          customValues: importedCustomValues,
          tasks: [] as any[],
          materials: [] as any[]
        }

        // 解析工序数据
        taskColGroups.forEach((g, tIdx) => {
          const tName = g.name !== -1 ? rowValues[g.name] || '' : ''
          if (!tName) return // 工序名称为空则跳过
          payload.tasks.push({
            processName: tName,
            processCode: g.code !== -1 ? rowValues[g.code] || '' : '',
            sequenceNo: g.seq !== -1 ? Number(rowValues[g.seq] || (tIdx + 1)) : (tIdx + 1),
            plannedQuantity: g.planned !== -1 ? Number(rowValues[g.planned] || 0) : plannedQty,
            completedQuantity: g.completed !== -1 ? Number(rowValues[g.completed] || 0) : 0,
            defectQuantity: g.defect !== -1 ? Number(rowValues[g.defect] || 0) : 0,
            approvedQuantity: g.approved !== -1 ? Number(rowValues[g.approved] || 0) : 0,
            unapprovedQuantity: g.unapproved !== -1 ? Number(rowValues[g.unapproved] || 0) : 0,
            assignee: g.assignee !== -1 ? rowValues[g.assignee] || '' : '',
            planDeadline: g.deadline !== -1 ? rowValues[g.deadline] || '' : '',
            reportPermission: g.permission !== -1 ? rowValues[g.permission] || '' : '',
            lastReportTime: g.lastReport !== -1 ? rowValues[g.lastReport] || '' : '',
            enabled: g.enabled !== -1 ? (rowValues[g.enabled] !== '否') : true
          })
        })

        // 解析物料数据
        materialColGroups.forEach(g => {
          const mName = g.name !== -1 ? rowValues[g.name] || '' : ''
          if (!mName) return // 物料名称为空则跳过
          payload.materials.push({
            materialCode: g.code !== -1 ? rowValues[g.code] || '' : '',
            materialName: mName,
            materialSpec: g.spec !== -1 ? rowValues[g.spec] || '' : '',
            quantity: g.qty !== -1 ? Number(rowValues[g.qty] || 0) : 0
          })
        })

        try {
          await createWorkOrder(props.token, payload)
          successCount++
        } catch (err: any) {
          failCount++
          errorsList.push(`第 ${i} 行：导入失败 (${err.message || '创建接口异常'})`)
        }
      }

      // 4. 重载数据并记录导入日志
      await loadData()
      
      const status = errorsList.length === 0 ? '成功' : successCount > 0 ? '部分成功' : '失败'
      try {
        await createSystemImportLog(props.token, {
          module: 'WORK_ORDER',
          fileName: file.name,
          totalCount: successCount + failCount,
          successCount,
          failCount,
          status,
          errors: errorsList.length > 0 ? errorsList.join('\n') : undefined
        })
        await refreshImportLogs()
      } catch (e) {
        // ignore
      }

      if (failCount === 0) {
        emit('notice', `导入完成：成功 ${successCount} 条，全部导入成功`)
      } else {
        emit('notice', `导入完成：成功 ${successCount} 条，失败 ${failCount} 条`)
      }
    }
    reader.readAsText(file)
  }
  input.click()
}

async function handleBulkStatus(status: string) {
  for (const id of selectedIds.value) {
    const item = workOrders.value.find(o => o.id === id)
    if (item) {
      await updateWorkOrder(props.token, id, {
        code: item.code,
        productId: item.productId,
        plannedQuantity: item.plannedQuantity,
        status,
        priority: item.priority,
        planStart: item.planStart,
        planEnd: item.planEnd,
        relatedOrderCode: item.relatedOrderCode,
        remark: item.remark,
        goodQuantity: item.goodQuantity,
        defectQuantity: item.defectQuantity,
        defectRate: item.defectRate,
        customValues: item.customValues,
        tasks: item.tasks,
        materials: item.materials,
        version: item.version
      })
      addOperationLog(`将工单 ${item.code} 的状态变更为 [${statusLabel(status)}]`)
    }
  }
  emit('notice', '状态更新成功')
  await loadData()
}

async function handleDeleteSelected() {
  const confirmed = await deleteConfirm({ message: `确认删除选中的 ${selectedIds.value.length} 个工单？` })
  if (!confirmed) return
  deleteLoading.value = true
  try {
    for (const id of selectedIds.value) {
      await deleteWorkOrder(props.token, id)
    }
    selectedIds.value = []
    emit('notice', '删除成功')
    await loadData()
  } catch (e: any) {
    emit('notice', e.message || '删除失败')
  } finally {
    deleteLoading.value = false
    deleteVisible.value = false
  }
}

function handleEditSelected() {
  if (selectedIds.value.length > 1) {
    emit('notice', '请仅选择一个工单进行修改')
    return
  }
  const item = workOrders.value.find(o => o.id === selectedIds.value[0])
  if (item) openEdit(item)
}

const showPrintModal = ref(false)
const selectedBuiltIn = ref<'flow' | 'qr-thermal' | 'qr-30-40' | 'qr-80-60'>('flow')
const selectedExcelTpl = ref<'flow' | 'qr' | 'list' | 'materials'>('flow')
const excelMultiImage = ref(false)
const excelScaleImage = ref(false)

const customTemplates = ref({
  qr_30_40: { qrSource: '工单二维码', row1: '产品名称', row2: '工单编号' },
  qr_80_60: {
    qrSource: '工单外链',
    titleLabel: '工单编号',
    titleField: '工单编号',
    row1Label: '产品编号',
    row1Field: ['产品编号'],
    row2Label: '产品名称',
    row2Field: ['产品名称'],
    row3Label: '产品规格',
    row3Field: ['产品规格'],
    row4Label: '工单计划数',
    row4Field: ['计划数'],
    row5Label: '计划时间',
    row5Field: ['计划开始时间', '计划结束时间'],
    row6Label: '备注',
    row6Field: ['工单备注']
  }
})

function getQr8060RowLabel(row: number): string {
  const template = customTemplates.value.qr_80_60 as Record<string, string | string[]>
  const value = template[`row${row}Label`]
  return typeof value === 'string' ? value : ''
}

function getQr8060RowFields(row: number): string[] {
  const template = customTemplates.value.qr_80_60 as Record<string, string | string[]>
  return ensureFieldArray(template[`row${row}Field`], [])
}

function loadCustomTemplates() {
  try {
    const saved = localStorage.getItem('print_templates_data')
    if (saved) {
      const parsed = JSON.parse(saved)
      const t3040 = parsed.find((t: any) => t.type === '30x40')
      const t8060 = parsed.find((t: any) => t.type === '80x60')
      if (t3040) {
        customTemplates.value.qr_30_40 = {
          qrSource: t3040.qrSource || '工单二维码',
          row1: t3040.row1 || '产品名称',
          row2: t3040.row2 || '工单编号'
        }
      }
      if (t8060) {
        customTemplates.value.qr_80_60 = {
          qrSource: t8060.qrSource || '工单外链',
          titleLabel: t8060.titleLabel || '工单编号',
          titleField: t8060.titleField || '工单编号',
          row1Label: t8060.row1Label || '产品编号',
          row1Field: ensureFieldArray(t8060.row1Field, ['产品编号']),
          row2Label: t8060.row2Label || '产品名称',
          row2Field: ensureFieldArray(t8060.row2Field, ['产品名称']),
          row3Label: t8060.row3Label || '产品规格',
          row3Field: ensureFieldArray(t8060.row3Field, ['产品规格']),
          row4Label: t8060.row4Label || '工单计划数',
          row4Field: ensureFieldArray(t8060.row4Field, ['计划数']),
          row5Label: t8060.row5Label || '计划时间',
          row5Field: ensureFieldArray(t8060.row5Field, ['计划开始时间', '计划结束时间']),
          row6Label: t8060.row6Label || '备注',
          row6Field: ensureFieldArray(t8060.row6Field, ['工单备注'])
        }
      }
    }
  } catch (e) {
    console.error('Load custom templates error:', e)
  }
}

function ensureFieldArray(val: any, fallback: string[]): string[] {
  if (Array.isArray(val)) return val
  if (typeof val === 'string' && val) {
    // 兼容旧版字符串格式，把“计划时间”自动转换为拆分后的两个字段
    if (val === '计划时间') return ['计划开始时间', '计划结束时间']
    return [val]
  }
  return fallback
}

function getFieldValue(order: any, fieldName: string) {
  if (!order) return ''
  switch(fieldName) {
    case '工单编号': return order.code || ''
    case '产品编号': return order.productCode || ''
    case '产品名称': return order.productName || ''
    case '产品规格': return order.productSpec || ''
    case '计划数': return order.plannedQuantity != null ? String(order.plannedQuantity) : ''
    case '计划开始时间': return order.planStart || ''
    case '计划结束时间': return order.planEnd || ''
    case '计划时间': return (order.planStart || '') + ' | ' + (order.planEnd || '')
    case '工单备注': return order.remark || ''
    case '工单外链': return `${window.location.origin}/workorder/${order.code || ''}`
    case '单位': return 'pcs'
    case '创建人': return order.creator || '王艺儒'
    case '创建时间': return order.createdAt || '2026-05-25 10:00'
    case '更新人': return order.updator || '王艺儒'
    case '更新时间': return order.updatedAt || '2026-05-25 10:05'
    default: return ''
  }
}

function getMultiFieldValues(order: any, fields: any): string[] {
  if (!order) return []
  const arr = Array.isArray(fields) ? fields : (typeof fields === 'string' && fields ? [fields] : [])
  return arr.map((f: string) => getFieldValue(order, f))
}

const qrDataUrls = ref<Record<number, string>>({})

async function generateAllQrs() {
  loadCustomTemplates()
  const newUrls: Record<number, string> = {}
  for (const id of selectedIds.value) {
    const order = workOrders.value.find(o => o.id === id)
    if (!order) continue
    const currentTpl = selectedBuiltIn.value === 'qr-30-40' ? customTemplates.value.qr_30_40 : customTemplates.value.qr_80_60
    const source = currentTpl.qrSource
    let qrText = ''
    if (source === '工单外链') {
      // 使用与当前页面相同的 hostname（局域网 IP），但端口改为移动端 H5 的 5174
      const hostname = window.location.hostname
      const mobileBase = `http://${hostname}:5174`
      qrText = `${mobileBase}/#/pages/work-order-detail/index?id=${order.id}&code=${encodeURIComponent(order.code || '')}`
    } else {
      qrText = getFieldValue(order, source) || order.code || String(id)
    }
    try {
      newUrls[id] = await QRCode.toDataURL(qrText, { width: 400, margin: 2, color: { dark: '#000000', light: '#ffffff' } })
    } catch (e) {
      console.error('QR Generate Error:', e)
    }
  }
  qrDataUrls.value = newUrls
}

watch([selectedIds, selectedBuiltIn], () => {
  generateAllQrs()
}, { deep: true, immediate: true })

function getWorkOrder(id: number) {
  return workOrders.value.find(o => o.id === id)
}

async function openPrint() {
  if (selectedIds.value.length === 0) {
    emit('notice', '请先选择需要打印的工单')
    return
  }
  loadCustomTemplates()
  await generateAllQrs() // 弹窗前确保二维码已全部生成并完成状态更新
  showPrintModal.value = true
}

function doPrint() {
  showPrintModal.value = false
  setTimeout(() => {
    window.print()
  }, 100)
}

async function doExcelPrint(action: 'wps' | 'download') {
  const targets = selectedIds.value.length > 0
    ? workOrders.value.filter(o => selectedIds.value.includes(o.id))
    : []
  if (targets.length === 0) {
    emit('notice', '请先选择需要打印的工单')
    return
  }

  const [{ default: ExcelJS }, { saveAs }] = await Promise.all([
    import('exceljs'),
    import('file-saver')
  ])
  const tplType = selectedExcelTpl.value
  const wb = new ExcelJS.Workbook()
  wb.creator = '小工单管理系统'
  wb.created = new Date()

  if (tplType === 'flow') {
    // ====== 工单流转卡模板 ======
    for (const item of targets) {
      const ws = wb.addWorksheet(`流转卡-${item.code}`)
      ws.columns = [
        { width: 16 }, { width: 30 }, { width: 30 }
      ]
      // 标题行
      ws.mergeCells('A1:C1')
      const titleCell = ws.getCell('A1')
      titleCell.value = '工单流转卡'
      titleCell.font = { bold: true, size: 18 }
      titleCell.alignment = { horizontal: 'center', vertical: 'middle' }
      titleCell.border = { bottom: { style: 'medium' } }
      ws.getRow(1).height = 36

      const fields = [
        ['工单编号', item.code],
        ['产品编号', item.productCode],
        ['产品名称', item.productName],
        ['产品规格', item.productSpec || '-'],
        ['工单计划数', item.plannedQuantity],
        ['良品数', item.goodQuantity],
        ['不良品数', item.defectQuantity],
        ['不良率', item.defectRate || '-'],
        ['状态', statusLabel(item.status)],
        ['优先级', priorityLabel(item.priority)],
        ['工艺路线', item.routeName || '-'],
        ['工单分类', item.categoryName || '-'],
        ['计划开始', item.planStart || '-'],
        ['计划结束', item.planEnd || '-'],
        ['关联订单号', item.relatedOrderCode || '-'],
        ['客户名称', item.customerName || '-'],
        ['备注', item.remark || '']
      ]

      // 动态自定义字段值
      customFields.value.forEach(field => {
        const cv = item.customValues?.find(v => v.fieldId === field.id || v.fieldName === field.name)
        let valStr = '-'
        if (cv && cv.value !== null && cv.value !== undefined) {
          if (Array.isArray(cv.value)) {
            valStr = cv.value.join('；')
          } else {
            valStr = String(cv.value)
          }
        }
        fields.push([field.name, valStr])
      })

      fields.forEach((pair, idx) => {
        const row = ws.getRow(idx + 2)
        row.getCell(1).value = pair[0] as string
        row.getCell(1).font = { bold: true }
        row.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' }
        ws.mergeCells(idx + 2, 2, idx + 2, 3)
        row.getCell(2).value = pair[1] as any
        row.getCell(2).alignment = { vertical: 'middle', wrapText: true }
        row.eachCell(c => {
          c.border = {
            top: { style: 'thin' }, bottom: { style: 'thin' },
            left: { style: 'thin' }, right: { style: 'thin' }
          }
        })
      })

      // 工序信息
      let lastRowIdx = fields.length + 1
      if (item.tasks && item.tasks.length > 0) {
        const startRow = lastRowIdx + 2
        ws.mergeCells(startRow, 1, startRow, 3)
        const secTitle = ws.getCell(startRow, 1)
        secTitle.value = '生产任务（工序）'
        secTitle.font = { bold: true, size: 12 }
        secTitle.alignment = { horizontal: 'center' }
        secTitle.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }

        const headerRow = ws.getRow(startRow + 1)
        const taskHeaders = ['工序名称', '计划数', '完成数']
        taskHeaders.forEach((h, ci) => {
          const cell = headerRow.getCell(ci + 1)
          cell.value = h
          cell.font = { bold: true }
          cell.alignment = { horizontal: 'center' }
          cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFE8F5E9' } }
          cell.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
        })

        item.tasks.forEach((task, ti) => {
          const row = ws.getRow(startRow + 2 + ti)
          row.getCell(1).value = task.processName
          row.getCell(2).value = task.plannedQuantity
          row.getCell(3).value = task.completedQuantity || 0
          row.eachCell(c => {
            c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
          })
        })
        lastRowIdx = startRow + 1 + item.tasks.length
      }

      // 用料清单 (物料信息)
      if (item.materials && item.materials.length > 0) {
        const matStartRow = lastRowIdx + 2
        ws.mergeCells(matStartRow, 1, matStartRow, 3)
        const matTitle = ws.getCell(matStartRow, 1)
        matTitle.value = '生产用料（物料）'
        matTitle.font = { bold: true, size: 12 }
        matTitle.alignment = { horizontal: 'center' }
        matTitle.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }

        const matHeaderRow = ws.getRow(matStartRow + 1)
        const matHeaders = ['物料编号', '物料名称', '需求数量']
        matHeaders.forEach((h, ci) => {
          const cell = matHeaderRow.getCell(ci + 1)
          cell.value = h
          cell.font = { bold: true }
          cell.alignment = { horizontal: 'center' }
          cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFE8F5E9' } }
          cell.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
        })

        item.materials.forEach((mat, mi) => {
          const row = ws.getRow(matStartRow + 2 + mi)
          row.getCell(1).value = mat.materialCode || ''
          row.getCell(2).value = mat.materialName || ''
          row.getCell(3).value = mat.quantity || 0
          row.eachCell(c => {
            c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
          })
        })
      }
    }
  } else if (tplType === 'qr') {
    // ====== 工单二维码模板 ======
    const ws = wb.addWorksheet('工单二维码')
    ws.columns = [{ width: 20 }, { width: 30 }, { width: 25 }]
    ws.getRow(1).values = ['工单编号', '产品名称', '二维码链接']
    ws.getRow(1).eachCell(c => {
      c.font = { bold: true, color: { argb: 'FFFFFFFF' } }
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF00B889' } }
      c.alignment = { horizontal: 'center' }
      c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
    })
    targets.forEach((item, idx) => {
      const row = ws.getRow(idx + 2)
      row.getCell(1).value = item.code
      row.getCell(2).value = item.productName
      row.getCell(3).value = `${window.location.origin}/workorder/${item.code}`
      row.eachCell(c => {
        c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
      })
    })
  } else if (tplType === 'list') {
    // ====== 工单列表模板 ======
    const ws = wb.addWorksheet('工单列表')
    const listHeaders = [
      '工单编号', '产品编号', '产品名称', '产品规格',
      '状态', '优先级', '计划数', '良品数', '不良品数', '不良率',
      '进度', '工艺路线', '分类', '计划开始', '计划结束', '关联订单号',
      '客户名称', '备注', '创建人', '创建时间'
    ]
    // 动态加入自定义字段表头
    customFields.value.forEach(f => {
      listHeaders.push(f.name)
    })

    ws.columns = listHeaders.map(h => ({ header: h, width: h.length > 4 ? 18 : 12 }))
    ws.getRow(1).eachCell(c => {
      c.font = { bold: true, color: { argb: 'FFFFFFFF' } }
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF00B889' } }
      c.alignment = { horizontal: 'center' }
      c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
    })
    targets.forEach(item => {
      const baseValues = [
        item.code, item.productCode, item.productName, item.productSpec || '',
        statusLabel(item.status), priorityLabel(item.priority),
        item.plannedQuantity, item.goodQuantity, item.defectQuantity, item.defectRate || '',
        item.progressPercent != null ? item.progressPercent + '%' : '',
        item.routeName || '', item.categoryName || '',
        item.planStart || '', item.planEnd || '', item.relatedOrderCode || '',
        item.customerName || '', item.remark || '', item.creator || '', item.createdAt || ''
      ]
      
      // 动态自定义字段值
      const customValues = customFields.value.map(field => {
        const cv = item.customValues?.find(v => v.fieldId === field.id || v.fieldName === field.name)
        if (!cv || cv.value === null || cv.value === undefined) return ''
        if (Array.isArray(cv.value)) return cv.value.join('；')
        return String(cv.value)
      })

      const row = ws.addRow([...baseValues, ...customValues])
      row.eachCell(c => {
        c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
      })
    })
  } else {
    // ====== 用料清单模板 ======
    const ws = wb.addWorksheet('用料清单')
    ws.columns = [
      { header: '工单编号', width: 20 },
      { header: '产品名称', width: 20 },
      { header: '物料编号', width: 18 },
      { header: '物料名称', width: 20 },
      { header: '物料规格', width: 18 },
      { header: '需求数量', width: 12 }
    ]
    ws.getRow(1).eachCell(c => {
      c.font = { bold: true, color: { argb: 'FFFFFFFF' } }
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF00B889' } }
      c.alignment = { horizontal: 'center' }
      c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
    })
    targets.forEach(item => {
      if (item.materials && item.materials.length > 0) {
        item.materials.forEach(mat => {
          const row = ws.addRow([
            item.code, item.productName,
            mat.materialCode || '', mat.materialName || '',
            mat.materialSpec || '', mat.quantity
          ])
          row.eachCell(c => {
            c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
          })
        })
      } else {
        const row = ws.addRow([item.code, item.productName, '', '(无物料)', '', 0])
        row.eachCell(c => {
          c.border = { top: { style: 'thin' }, bottom: { style: 'thin' }, left: { style: 'thin' }, right: { style: 'thin' } }
        })
      }
    })
  }

  // 生成并下载
  const tplLabel = tplType === 'flow' ? '工单流转卡'
    : tplType === 'qr' ? '工单二维码'
    : tplType === 'list' ? '工单列表'
    : '用料清单'
  const fileName = `${tplLabel}_${new Date().toISOString().slice(0, 10)}.xlsx`

  try {
    const buffer = await wb.xlsx.writeBuffer()
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })

    if (action === 'download') {
      saveAs(blob, fileName)
      emit('notice', `已下载：${fileName}`)
    } else {
      // WPS打印：下载文件后提示用 WPS 打开打印
      saveAs(blob, fileName)
      emit('notice', `已生成 ${fileName}，请用 WPS 打开后打印`)
    }
  } catch (err: any) {
    emit('notice', `生成 Excel 失败: ${err.message || '未知错误'}`)
  }

  showPrintModal.value = false
}

import { onUnmounted } from 'vue'

function closeDropdowns(e: Event) {
  const target = e.target as HTMLElement
  if (!target.closest('.add-task-wrap') && !target.closest('.task-selector-modal')) {
    showTaskSelector.value = false
  }
  if (!target.closest('.toolbar-dropdown-container')) {
    showRowHeightPopover.value = false
  }
}

onMounted(() => {
  loadData()
  window.addEventListener('click', closeDropdowns)
})

onUnmounted(() => {
  window.removeEventListener('click', closeDropdowns)
})
</script>

<template>
  <section class="content">
    <div v-if="taskTooltip.visible && taskTooltip.task" class="task-tooltip" :style="{ left: taskTooltip.x + 'px', top: taskTooltip.y + 'px' }">
      <div class="tooltip-title">{{ taskTooltip.task.processName }}</div>
      <div class="tooltip-progress">工序进度: {{ taskTooltip.task.completedQuantity || 0 }}/{{ taskTooltip.task.plannedQuantity || 0 }}</div>
      <hr class="tooltip-divider" />
      <div class="tooltip-stat">工序计划数: <span>{{ taskTooltip.task.plannedQuantity || 0 }}</span></div>
      <div class="tooltip-stat">已报工良品数: <span>{{ taskTooltip.task.completedQuantity || 0 }}</span></div>
      <div class="tooltip-stat">已报工不良品数: <span>{{ taskTooltip.task.defectQuantity || 0 }}</span></div>
      <div class="tooltip-stat">已审批良品数: <span>{{ taskTooltip.task.approvedQuantity || 0 }}</span></div>
      <div class="tooltip-stat">未审批良品数: <span>{{ taskTooltip.task.unapprovedQuantity || 0 }}</span></div>
      <div class="tooltip-stat" v-if="taskTooltip.task.lastReportTime">最近报工时间: <span>{{ taskTooltip.task.lastReportTime }}</span></div>
    </div>
    <div class="category-row">
      <div class="category-tabs">
        <button
          v-for="item in categoryTabs"
          :key="item.key"
          class="category-tab"
          :class="{ active: categoryFilter === item.key }"
          type="button"
          @click="categoryFilter = item.key"
        >
          <!-- 全部工单：四宫格图标 -->
          <svg v-if="item.key === 'all'" class="cat-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="7" height="7"></rect>
            <rect x="14" y="3" width="7" height="7"></rect>
            <rect x="14" y="14" width="7" height="7"></rect>
            <rect x="3" y="14" width="7" height="7"></rect>
          </svg>
          <!-- 其它：文件夹图标 -->
          <svg v-else class="cat-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
          </svg>
          <span class="cat-label">{{ item.label }}</span>
          <!-- 仅限非内置分类：显示删除 × 按钮 -->
          <span
            v-if="item.key !== 'all' && item.key !== 'today' && item.key !== 'urgent' && item.key !== 'due_soon'"
            class="cat-delete"
            title="删除此分类"
            @click="removeCategory(item, $event)"
          >
            ×
          </span>
        </button>
        <button class="add-cat-btn" type="button" @click="showCategoryModal = true" title="添加工单分类">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        </button>
      </div>
    </div>

    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;">
      <div class="tab-row" style="margin-bottom: 0;">
        <button
          v-for="item in statusCards"
          :key="item.key"
          class="tab-pill"
          :class="{ active: statusFilter === item.key }"
          type="button"
          @click="statusFilter = item.key"
        >
          {{ item.label }} <span class="count-badge">{{ item.count }}</span>
        </button>
      </div>

      <div style="display: flex; align-items: center; gap: 16px;">
        <div style="display: flex; align-items: center; background: #fff; border: 1px solid #dce3e0; border-radius: 4px; height: 34px; overflow: hidden;">
          <select style="border: none; outline: none; background: transparent; padding: 0 8px; font-size: 13px; height: 100%; border-right: 1px solid #dce3e0; cursor: pointer; color: #586360;">
            <option value="工单、产品">默认</option>
          </select>
          <input v-model="keyword" placeholder="搜工单、查产品" style="border: none; outline: none; height: 100%; width: 140px; font-size: 13px; padding: 0 12px;" />
          <button style="background: #00b889; border: none; color: white; height: 100%; display: flex; align-items: center; justify-content: center; padding: 0 12px; cursor: pointer;">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          </button>
        </div>
      </div>
    </div>

    <div class="toolbar" v-if="selectedIds.length === 0">
      <button class="primary-btn create-btn" type="button" @click="openCreate">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="16"></line><line x1="8" y1="12" x2="16" y2="12"></line></svg>
        创建工单
      </button>
      <button class="toolbar-btn" type="button" @click="handleExport">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
        导出
      </button>
      <button class="toolbar-btn" type="button" @click="handleImport">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
        导入
      </button>
      <button class="text-btn" type="button" style="color: #586360; margin-left: 8px;" @click="showColumnModal = true">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"></line><line x1="4" y1="10" x2="4" y2="3"></line><line x1="12" y1="21" x2="12" y2="12"></line><line x1="12" y1="8" x2="12" y2="3"></line><line x1="20" y1="21" x2="20" y2="16"></line><line x1="20" y1="12" x2="20" y2="3"></line><line x1="1" y1="14" x2="7" y2="14"></line><line x1="9" y1="8" x2="15" y2="8"></line><line x1="17" y1="16" x2="23" y2="16"></line></svg>
        字段配置
      </button>
      <button class="text-btn" type="button" style="color: #586360; margin-left: 8px;" @click="toggleSort">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="6" x2="13" y2="6"></line><line x1="4" y1="12" x2="11" y2="12"></line><line x1="4" y1="18" x2="11" y2="18"></line><polyline points="15 16 18 19 21 16"></polyline><line x1="18" y1="5" x2="18" y2="19"></line></svg>
        排序
      </button>
      <button class="text-btn" type="button" style="color: #586360; margin-left: 8px;" @click="showFilterPanel = !showFilterPanel" :class="{ 'active-btn': showFilterPanel }">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon></svg>
        筛选
      </button>

      <!-- 行高配置下拉菜单 -->
      <div class="toolbar-dropdown-container">
        <button class="text-btn" type="button" style="color: #586360; margin-left: 8px;" @click.stop="showRowHeightPopover = !showRowHeightPopover" :class="{ 'active-btn': showRowHeightPopover }">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="8 9 12 5 16 9"></polyline><polyline points="16 15 12 19 8 15"></polyline><line x1="12" y1="5" x2="12" y2="19"></line></svg>
          行高
        </button>
        <div v-if="showRowHeightPopover" class="dropdown-bubble row-height-bubble" @click.stop>
          <div class="dropdown-header">表格行高配置</div>
          <div class="dropdown-content">
            <label class="dropdown-item">
              <input type="radio" value="compact" :checked="rowHeight === 'compact'" @change="selectRowHeight('compact')" />
              <span>紧凑</span>
            </label>
            <label class="dropdown-item">
              <input type="radio" value="normal" :checked="rowHeight === 'normal'" @change="selectRowHeight('normal')" />
              <span>默认</span>
            </label>
            <label class="dropdown-item">
              <input type="radio" value="loose" :checked="rowHeight === 'loose'" @change="selectRowHeight('loose')" />
              <span>宽松</span>
            </label>
          </div>
        </div>
      </div>

      <div style="margin-left: auto; display: flex; align-items: center; gap: 16px;">
        <button class="text-btn" type="button" style="color: #586360;" @click="showImportLogModal = true">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
          导入日志
        </button>
        <button class="text-btn" type="button" style="color: #586360;" @click="openLogs">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
          操作记录
        </button>
      </div>
    </div>

    <div class="toolbar" v-else style="background: #fdfdfd; border: 1px solid #dce3e0;">
      <span style="font-size: 13px; color: #00b889; margin: 0 12px; font-weight: 500;">已选 {{ selectedIds.length }} 条</span>
      
      <button class="toolbar-btn" type="button" @click="handleExport">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
        导出
      </button>
      <button class="toolbar-btn" type="button" @click="handleBulkStatus('EXECUTING')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
        开始
      </button>
      <button class="toolbar-btn" type="button" @click="handleBulkStatus('PAUSED')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>
        暂停
      </button>
      <button class="toolbar-btn" type="button" @click="handleBulkStatus('FINISHED')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="9" y1="9" x2="15" y2="15"></line><line x1="15" y1="9" x2="9" y2="15"></line></svg>
        结束
      </button>
      <button class="toolbar-btn" type="button" @click="handleBulkStatus('CLOSED')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>
        关闭
      </button>
      <button class="toolbar-btn" type="button" @click="openPrint">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
        打印
      </button>
      <button class="toolbar-btn" type="button" @click="handleEditSelected">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
        修改
      </button>
      <button class="toolbar-btn" type="button" @click="handleDeleteSelected" style="color: #e74c3c;">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
        删除
      </button>

      <div style="margin-left: auto;">
        <button class="toolbar-btn" type="button" @click="selectedIds = []">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="15 9 9 15"></polyline><polyline points="9 9 15 15"></polyline></svg>
          撤销多选
        </button>
      </div>
    </div>

    <!-- 高级筛选面板 (平铺滑出) -->
    <div v-if="showFilterPanel" class="filters" style="margin-top: 12px; border-top: 1px dashed #cbd6d3; padding-top: 16px; animation: dropdownFadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1) forwards;">
      <label>
        <span>优先级</span>
        <select v-model="filterParams.priority" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;">
          <option value="all">全部</option>
          <option value="LOW">低</option>
          <option value="NORMAL">中</option>
          <option value="HIGH">高</option>
          <option value="URGENT">紧急</option>
        </select>
      </label>
      <label>
        <span>计划数下限</span>
        <input type="number" v-model.number="filterParams.plannedQtyMin" placeholder="最小数量" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;" />
      </label>
      <label>
        <span>计划数上限</span>
        <input type="number" v-model.number="filterParams.plannedQtyMax" placeholder="最大数量" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;" />
      </label>
      <label>
        <span>计划开始自</span>
        <input type="datetime-local" v-model="filterParams.planStartMin" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;" />
      </label>
      <label>
        <span>计划开始至</span>
        <input type="datetime-local" v-model="filterParams.planStartMax" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;" />
      </label>
      <label>
        <span>计划结束自</span>
        <input type="datetime-local" v-model="filterParams.planEndMin" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;" />
      </label>
      <label>
        <span>计划结束至</span>
        <input type="datetime-local" v-model="filterParams.planEndMax" style="width: 100%; height: 36px; padding: 0 10px; border: 1px solid #cbd6d3; border-radius: 6px; font-size: 13px; outline: none; transition: border-color 0.2s;" />
      </label>
      <div class="filter-actions">
        <button class="ghost-btn query-btn" type="button" @click="resetFilters" style="padding: 0 16px; height: 36px; border-radius: 6px; font-size: 13px;">重置</button>
        <button class="primary-btn query-btn" type="button" @click="showFilterPanel = false" style="padding: 0 16px; height: 36px; border-radius: 6px; font-size: 13px;">收起</button>
      </div>
    </div>

    <div class="table-container">
      <table class="data-table" :class="`row-height-${rowHeight}`">
        <thead>
          <tr>
            <th style="width:48px"><input type="checkbox" :checked="allSelected" @change="toggleAll(($event.target as HTMLInputElement).checked)" /></th>
            <th v-if="visibleColumns.includes('code')">工单编号</th>
            <th v-if="visibleColumns.includes('productCode')">产品编号</th>
            <th v-if="visibleColumns.includes('productName')">产品名称</th>
            <th v-if="visibleColumns.includes('productSpec')">产品规格</th>
            <th v-if="visibleColumns.includes('plannedQuantity')">计划数</th>
            <th v-if="visibleColumns.includes('status')">状态</th>
            <th v-if="visibleColumns.includes('priority')">优先级</th>
            <th v-if="visibleColumns.includes('routeName')">工单进度条</th>
            <th style="width:130px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredOrders" :key="item.id">
            <td><input type="checkbox" :checked="selectedIds.includes(item.id)" @change="toggleOne(item.id, ($event.target as HTMLInputElement).checked)" /></td>
            <td v-if="visibleColumns.includes('code')">{{ item.code }}</td>
            <td v-if="visibleColumns.includes('productCode')">{{ item.productCode }}</td>
            <td v-if="visibleColumns.includes('productName')">{{ item.productName }}</td>
            <td v-if="visibleColumns.includes('productSpec')">{{ item.productSpec || '-' }}</td>
            <td v-if="visibleColumns.includes('plannedQuantity')">{{ item.plannedQuantity }}</td>
            <td v-if="visibleColumns.includes('status')" class="green-link">{{ statusLabel(item.status) }}</td>
            <td v-if="visibleColumns.includes('priority')">{{ priorityLabel(item.priority) }}</td>
            <td v-if="visibleColumns.includes('routeName')">
              <div style="display: flex; align-items: center; flex-wrap: nowrap; overflow-x: auto; padding: 4px 0 6px 0; gap: 4px; max-width: 320px;" class="scroll-thin">
                <div v-for="(task, i) in item.tasks" :key="i" style="display: flex; align-items: center; position: relative;">
                  <!-- 工序步骤节点 -->
                  <div 
                    style="display: flex; flex-direction: column; align-items: center; justify-content: center; width: 68px; flex-shrink: 0; cursor: pointer; position: relative; z-index: 2;"
                    @mouseenter="showTaskTooltip($event, task)"
                    @mouseleave="hideTaskTooltip"
                  >
                    <!-- 进度圆环 -->
                    <div 
                      :style="{
                        width: '48px',
                        height: '48px',
                        borderRadius: '50%',
                        background: '#ffffff',
                        border: '2.5px solid',
                        borderColor: (task.completedQuantity || 0) >= (task.plannedQuantity || 1) ? '#0066cc' : (task.completedQuantity || 0) > 0 ? '#fa8c16' : '#d9d9d9',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '12.5px',
                        fontWeight: 'bold',
                        color: (task.completedQuantity || 0) >= (task.plannedQuantity || 1) ? '#0066cc' : (task.completedQuantity || 0) > 0 ? '#fa8c16' : '#8a9693',
                        boxShadow: '0 2px 6px rgba(0,0,0,0.05)',
                        transition: 'all 0.2s'
                      }"
                    >
                      {{ Math.round(((task.completedQuantity || 0) / (task.plannedQuantity || 1)) * 100) }}%
                    </div>
                    <!-- 工序名称 -->
                    <span 
                      :style="{
                        fontSize: '13px',
                        color: (task.completedQuantity || 0) >= (task.plannedQuantity || 1) ? '#0e1816' : '#8a9693',
                        fontWeight: (task.completedQuantity || 0) >= (task.plannedQuantity || 1) ? '600' : 'normal',
                        marginTop: '4px',
                        whiteSpace: 'nowrap',
                        textOverflow: 'ellipsis',
                        overflow: 'hidden',
                        maxWidth: '68px'
                      }"
                      :title="task.processName"
                    >
                      {{ task.processName }}
                    </span>
                  </div>
                  
                  <!-- 连接线 -->
                  <div 
                    v-if="i < item.tasks.length - 1" 
                    :style="{
                      width: '28px',
                      height: '2px',
                      background: (task.completedQuantity || 0) >= (task.plannedQuantity || 1) && (item.tasks[i+1]?.completedQuantity || 0) > 0 ? '#0066cc' : '#e5ebe9',
                      marginTop: '-24px',
                      flexShrink: 0,
                      position: 'relative',
                      zIndex: 1
                    }"
                  ></div>
                </div>
                <!-- 暂无工序 -->
                <div v-if="!item.tasks || item.tasks.length === 0" style="color: #cbd6d3; font-size: 13px; padding-left: 8px;">-</div>
              </div>
            </td>
            <td class="action-cell">
              <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
              <button class="action-link danger" type="button" @click="removeOrder(item)">删除</button>
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td :colspan="visibleColumns.length + 2" style="font-size: 13px; color: #586360; background: #fdfdfd; position: sticky; bottom: 0; text-align: left; font-weight: 500;">
              当前页合计计划数: <span style="color: #00b889;">{{ totalPlannedQuantity }}</span>
            </td>
          </tr>
        </tfoot>
      </table>
    </div>

    <div v-if="showModal" class="modal-backdrop">
      <div class="modal workorder-modal">
        <div class="modal-header">
          <h2>{{ editing ? '编辑工单' : '创建工单' }}</h2>
          <button class="close-btn" type="button" @click="showModal = false">×</button>
        </div>
        <div class="modal-tabs">
          <button :class="{ active: activeTab === 'basic' }" @click="activeTab = 'basic'">基本信息</button>
          <button :class="{ active: activeTab === 'custom' }" @click="activeTab = 'custom'">自定义信息</button>
          <button :class="{ active: activeTab === 'task' }" @click="activeTab = 'task'">生产任务</button>
          <button :class="{ active: activeTab === 'material' }" @click="activeTab = 'material'">用料清单</button>
        </div>

        <div v-if="activeTab === 'basic'" class="create-form two-column">
          <label><span>工单编号</span><input v-model="form.code" placeholder="请输入，忽略将自动生成" /></label>
          <label><span>产品信息</span><select v-model="form.productId"><option value="">请选择</option><option v-for="p in products" :key="p.id" :value="p.id">{{ p.code }} - {{ p.name }}</option></select></label>
          
          <!-- 核心！将卡片独立提取到 label 外部，并指定 grid-column: 2，既让上层输入框完美平齐，又仅占右半屏一半宽度 -->
          <div v-if="selectedProduct" style="grid-column: 2; display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border: 1.5px dashed #dce3e0; border-radius: 8px; background: #fafbfc; margin-bottom: 4px; box-shadow: inset 0 1px 2px rgba(0,0,0,0.01);">
            <!-- 左侧产品详细属性 -->
            <div style="display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #586360;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: #8a9693; width: 64px; flex-shrink: 0;">产品编号</span>
                <span style="font-weight: 600; color: #1e2724; font-family: monospace; letter-spacing: 0.5px;">{{ selectedProduct.code }}</span>
              </div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: #8a9693; width: 64px; flex-shrink: 0;">产品名称</span>
                <span style="font-weight: 600; color: #1e2724;">{{ selectedProduct.name }}</span>
              </div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: #8a9693; width: 64px; flex-shrink: 0;">产品规格</span>
                <span style="font-weight: 500; color: #586360;">{{ selectedProduct.spec || '-' }}</span>
              </div>
            </div>
            <!-- 右侧特大字号库存数量 -->
            <div style="text-align: center; display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 4px 16px; border-left: 1px dashed #dce3e0; flex-shrink: 0;">
              <span style="font-size: 26px; font-weight: 700; color: #00b889; line-height: 1; font-family: monospace;">
                {{ selectedProduct.inventoryQty ?? 0 }}
              </span>
              <span style="font-size: 11px; color: #8a9693; font-weight: 500; white-space: nowrap;">
                库存数量 ({{ selectedProduct.unitName || '个' }})
              </span>
            </div>
          </div>
          <label><span>计划数</span><input v-model.number="form.plannedQuantity" type="number" /></label>
          <label><span>所属分类</span><select v-model="form.categoryId"><option value="">未分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
          <label><span>状态</span><select v-model="form.status"><option value="NOT_STARTED">未开始</option><option value="EXECUTING">执行中</option><option value="PAUSED">已暂停</option><option value="COMPLETED">已完成</option></select></label>
          <label><span>优先级</span><select v-model="form.priority"><option value="NORMAL">正常</option><option value="HIGH">高</option><option value="URGENT">加急</option><option value="LOW">低</option></select></label>
          <label><span>计划开始时间</span><input v-model="form.planStart" type="datetime-local" /></label>
          <label><span>计划结束时间</span><input v-model="form.planEnd" type="datetime-local" /></label>
          <label><span>关联单据</span><input v-model="form.relatedOrderCode" /></label>
          <label class="full-row"><span>备注</span><textarea v-model="form.remark"></textarea></label>
        </div>

        <div v-else-if="activeTab === 'custom'" class="create-form two-column">
          <template v-for="field in customFields" :key="field.id">
            <!-- 单选框 (single_select) -->
            <label v-if="field.fieldType === 'single_select'">
              <span>{{ field.name }}</span>
              <select :value="String(getCustomValue(field) || '')" @change="setCustomValue(field, ($event.target as HTMLSelectElement).value)">
                <option value="">请选择</option>
                <option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            
            <!-- 复选框 (multiple_select) -->
            <div v-else-if="field.fieldType === 'multiple_select'" class="custom-checkbox-group-row">
              <span>{{ field.name }}</span>
              <div style="display:flex; flex-wrap:wrap; gap:16px; min-height:38px; align-items:center; width: 100%;">
                <label v-for="opt in field.options" :key="opt.value" style="display:inline-flex; align-items:center; gap:6px; cursor:pointer; font-weight:normal; width:auto; padding:0; margin:0;">
                  <input 
                    type="checkbox" 
                    :value="opt.value" 
                    :checked="Array.isArray(getCustomValue(field)) ? (getCustomValue(field) as string[]).includes(opt.value) : false"
                    @change="toggleMultipleSelectValue(field, opt.value, ($event.target as HTMLInputElement).checked)"
                    style="width:16px; height:16px; accent-color:#00b889;"
                  />
                  <span>{{ opt.label }}</span>
                </label>
              </div>
            </div>
            
            <!-- 数字 (number) -->
            <label v-else-if="field.fieldType === 'number'">
              <span>{{ field.name }}</span>
              <input :value="String(getCustomValue(field) || '')" type="number" @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" />
            </label>
            
            <!-- 时间 (date) -->
            <label v-else-if="field.fieldType === 'date'">
              <span>{{ field.name }}</span>
              <input :value="String(getCustomValue(field) || '')" type="date" @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" />
            </label>
            
            <!-- 图片 (image) -->
            <label v-else-if="field.fieldType === 'image'">
              <span>{{ field.name }}</span>
              <div style="flex:1; display:flex; flex-direction:column; gap:6px; min-width: 0;">
                <div style="display:flex; gap:8px; align-items:center; width: 100%;">
                  <input 
                    :value="String(getCustomValue(field) || '')" 
                    type="text" 
                    placeholder="请输入图片链接或点击上传" 
                    @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" 
                    style="flex:1;"
                  />
                  <button 
                    class="toolbar-btn" 
                    type="button" 
                    @click="(($event.currentTarget as HTMLElement).nextElementSibling as HTMLInputElement)?.click()"
                    style="height:34px; margin:0; white-space:nowrap;"
                  >
                    {{ uploadingFieldId === field.id ? '上传中...' : '上传' }}
                  </button>
                  <input 
                    type="file" 
                    accept="image/*"
                    style="display:none;" 
                    @change="handleCustomFieldUpload(field, $event)"
                  />
                </div>
                <div v-if="getCustomValue(field)" style="margin-top:6px; display:flex; flex-wrap:wrap; gap:8px;">
                  <div 
                    v-for="(path, pIdx) in String(getCustomValue(field) || '').split(',').filter(Boolean)" 
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
                      @click="removeCustomFieldPath(field, pIdx)" 
                      style="position:absolute; top:-6px; right:-6px; width:16px; height:16px; border-radius:50%; background:#ff5a5a; color:#fff; border:none; display:flex; align-items:center; justify-content:center; cursor:pointer; font-size:9px; line-height:1; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.15); padding:0; z-index:10;"
                      title="取消"
                    >
                      ×
                    </button>
                  </div>
                </div>
              </div>
            </label>
            
            <!-- 附件 (file) -->
            <label v-else-if="field.fieldType === 'file'">
              <span>{{ field.name }}</span>
              <div style="flex:1; display:flex; flex-direction:column; gap:6px; min-width: 0;">
                <div style="display:flex; gap:8px; align-items:center; width: 100%;">
                  <input 
                    :value="String(getCustomValue(field) || '')" 
                    type="text" 
                    placeholder="请输入附件链接或点击上传" 
                    @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" 
                    style="flex:1;"
                  />
                  <button 
                    class="toolbar-btn" 
                    type="button" 
                    @click="(($event.currentTarget as HTMLElement).nextElementSibling as HTMLInputElement)?.click()"
                    style="height:34px; margin:0; white-space:nowrap;"
                  >
                    {{ uploadingFieldId === field.id ? '上传中...' : '上传' }}
                  </button>
                  <input 
                    type="file" 
                    style="display:none;" 
                    @change="handleCustomFieldUpload(field, $event)"
                  />
                </div>
                <div v-if="getCustomValue(field)" style="margin-top:6px; display:flex; flex-wrap:wrap; gap:8px;">
                  <div 
                    v-for="(path, pIdx) in String(getCustomValue(field) || '').split(',').filter(Boolean)" 
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
                      @click="removeCustomFieldPath(field, pIdx)" 
                      style="position:absolute; top:-6px; right:-6px; width:16px; height:16px; border-radius:50%; background:#ff5a5a; color:#fff; border:none; display:flex; align-items:center; justify-content:center; cursor:pointer; font-size:9px; line-height:1; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.15); padding:0; z-index:10;"
                      title="取消"
                    >
                      ×
                    </button>
                  </div>
                </div>
              </div>
            </label>
            
            <!-- 关联对象 (reference) -->
            <label v-else-if="field.fieldType === 'reference'">
              <span>{{ field.name }}</span>
              <input :value="String(getCustomValue(field) || '')" type="text" placeholder="请输入关联对象编号" @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" />
            </label>
            
            <!-- 默认/文本 (text) -->
            <label v-else>
              <span>{{ field.name }}</span>
              <input :value="String(getCustomValue(field) || '')" type="text" @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" />
            </label>
          </template>
        </div>

        <div v-else-if="activeTab === 'task'" style="padding:18px; display:flex; flex-direction:column; min-height: 300px;">
          <div class="toolbar" style="margin-bottom: 12px; padding: 0; border: none; background: transparent;">
            <template v-if="selectedTaskIndexes.length === 0">
              <div style="position: relative;" class="add-task-wrap">
                <button class="primary-btn" type="button" @click.stop="showTaskSelector = !showTaskSelector">添加生产任务</button>
              </div>
            </template>
            <template v-else>
              <div style="display:flex; align-items:center; width:100%; background: #fdfdfd; border: 1px solid #dce3e0; border-radius: 4px; padding: 6px 12px;">
                <span style="font-size: 13px; color: #00b889; font-weight: 500; margin-right: 16px;">已选 {{ selectedTaskIndexes.length }} 条</span>
                <button class="toolbar-btn" type="button" @click="openTaskEdit()">
                  <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                  修改
                </button>
                <button class="toolbar-btn" type="button" @click="removeSelectedTasks" style="color: #e74c3c; margin-right: auto;">
                  <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
                  删除
                </button>
                <button class="toolbar-btn" type="button" @click="selectedTaskIndexes = []">
                  <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"></circle><polyline points="15 9 9 15"></polyline><polyline points="9 9 15 15"></polyline></svg>
                  撤销多选
                </button>
              </div>
            </template>
          </div>

          <div class="table-container" style="flex:1; border: 1px solid #dce3e0; border-radius: 4px; overflow: hidden; background: #fff;">
            <table class="data-table" style="border: none;">
              <thead>
                <tr>
                  <th style="width: 48px; text-align: center;"><input type="checkbox" :checked="allTasksSelected" @change="toggleTaskAll(($event.target as HTMLInputElement).checked)" /></th>
                  <th style="width: 36px; text-align: center;"></th>
                  <th>工序名称</th>
                  <th>工序编号</th>
                  <th>计划数</th>
                  <th>计划结束时间</th>
                  <th>报工权限</th>
                  <th style="width: 100px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(task, index) in form.tasks" :key="index"
                    :class="{ 'drag-over': taskDragOverIdx === index }"
                    draggable="true"
                    @dragstart="onTaskDragStart(index, $event)"
                    @dragover="onTaskDragOver(index, $event)"
                    @drop="onTaskDrop(index)"
                    @dragend="onTaskDragEnd">
                  <td style="text-align: center;"><input type="checkbox" :checked="selectedTaskIndexes.includes(index)" @change="toggleTaskOne(index, ($event.target as HTMLInputElement).checked)" /></td>
                  <td style="text-align: center; cursor: grab; color: #bbb; font-size: 16px; letter-spacing: 1px;">&#8942;&#8942;</td>
                  <td>{{ task.processName }}</td>
                  <td>{{ task.sequenceNo }}</td>
                  <td>{{ task.plannedQuantity }}</td>
                  <td>{{ task.planDeadline || '-' }}</td>
                  <td>{{ task.reportPermission || '-' }}</td>
                  <td class="action-cell">
                    <button class="action-link" type="button" @click="openTaskEdit(index)">编辑</button>
                    <button class="action-link danger" type="button" @click="toggleTaskOne(index, true); removeSelectedTasks()">删除</button>
                  </td>
                </tr>
                <tr v-if="form.tasks.length === 0">
                  <td colspan="8" style="text-align: center; padding: 40px; color: #8a9693;">暂无生产任务，请先添加</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-else-if="activeTab === 'material'" style="padding:18px; display:flex; flex-direction:column; min-height: 300px;">
          <div class="toolbar" style="margin-bottom: 12px; padding: 0; border: none; background: transparent;">
            <div class="toolbar-left">
              <button class="primary-btn" type="button" @click="addMaterial">添加物料</button>
              <button class="ghost-btn" type="button" :disabled="!selectedProductBom" @click="applyBomMaterials">按BOM带入</button>
              <span v-if="selectedProductBom" class="muted">已匹配 BOM：{{ selectedProductBom.code }}</span>
              <span v-else class="muted">当前产品暂无启用 BOM，可手工添加</span>
            </div>
          </div>

          <div class="table-container" style="flex:1; border: 1px solid #dce3e0; border-radius: 4px; overflow: hidden; background: #fff;">
            <table class="data-table" style="border: none;">
              <thead>
                <tr>
                  <th style="width: 22%">物料编号</th>
                  <th style="width: 28%">物料名称</th>
                  <th style="width: 24%">物料规格</th>
                  <th style="width: 16%">需求数量</th>
                  <th style="width: 96px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(material, index) in form.materials" :key="index">
                  <td><input v-model.trim="material.materialCode" class="table-input-sm" placeholder="请输入编号" /></td>
                  <td><input v-model.trim="material.materialName" class="table-input-sm" placeholder="请输入名称" /></td>
                  <td><input v-model.trim="material.materialSpec" class="table-input-sm" placeholder="请输入规格" /></td>
                  <td>
                    <input
                      v-model.number="material.quantity"
                      class="table-input-sm"
                      type="number"
                      min="1"
                      step="1"
                      style="width: 96px; text-align: center;"
                    />
                  </td>
                  <td class="action-cell">
                    <button class="action-link danger" type="button" @click="removeMaterial(index)">删除</button>
                  </td>
                </tr>
                <tr v-if="form.materials.length === 0">
                  <td colspan="5" style="text-align: center; padding: 40px; color: #8a9693;">暂无用料，请添加物料或按 BOM 带入</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showModal = false">取消</button>
          <button class="primary-btn wide" type="button" @click="saveOrder">确定</button>
        </div>
      </div>
    </div>

    <!-- 任务编辑微弹窗 -->
    <div v-if="showTaskEditModal" class="modal-backdrop" style="z-index: 1010;">
      <div class="modal workorder-modal" style="width: 400px;">
        <div class="modal-header">
          <h2>修改任务</h2>
          <button class="close-btn" type="button" @click="showTaskEditModal = false">×</button>
        </div>
        <div style="padding: 24px;">
          <div class="form-row">
            <label style="width: 80px;">计划数</label>
            <div class="process-input-wrap">
              <input v-model.number="taskEditForm.plannedQuantity" type="number" class="process-input" />
            </div>
          </div>
          <div class="form-row" style="margin-top: 16px;">
            <label style="width: 80px;">结束时间</label>
            <div class="process-input-wrap">
              <input v-model="taskEditForm.planDeadline" type="datetime-local" class="process-input" />
            </div>
          </div>
          <div class="form-row" style="margin-top: 16px;">
            <label style="width: 80px;">报工权限</label>
            <div class="process-input-wrap">
              <input v-model="taskEditForm.reportPermission" class="process-input" />
            </div>
          </div>
          <div style="text-align: right; margin-top: 24px;">
            <button class="ghost-btn" style="margin-right: 12px;" @click="showTaskEditModal = false">取消</button>
            <button class="primary-btn" @click="saveTaskEdit">保存</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 打印模板选择弹窗 -->
    <!-- 添加分类弹窗 -->
    <div v-if="showCategoryModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 480px;">
        <div class="modal-header">
          <h2>添加工单分类</h2>
          <button class="close-btn" type="button" @click="showCategoryModal = false">×</button>
        </div>
        <div style="padding: 24px;">
          <div class="form-row">
            <label style="width: 80px;">分类名称</label>
            <div class="process-input-wrap">
              <input v-model="newCategoryName" class="process-input" placeholder="请输入分类名称" />
            </div>
          </div>
          <div style="text-align: right; margin-top: 24px;">
            <button class="ghost-btn" style="margin-right: 12px;" @click="showCategoryModal = false">取消</button>
            <button class="primary-btn" @click="handleSaveCategory">保存</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 字段配置弹窗 -->
    <div v-if="showColumnModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 520px;">
        <div class="modal-header">
          <h2>字段配置</h2>
          <button class="close-btn" type="button" @click="showColumnModal = false">×</button>
        </div>
        <div style="padding: 24px; max-height: 480px; overflow-y: auto;">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
            <label v-for="col in columnDefinitions" :key="col.key" style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
              <input type="checkbox" :value="col.key" v-model="visibleColumns" style="accent-color: #00b889; width: 16px; height: 16px;" />
              <span style="font-size: 14px; color: #1e2724;">{{ col.label }}</span>
            </label>
          </div>
          <div style="text-align: right; margin-top: 24px;">
            <button class="ghost-btn" style="margin-right: 12px;" @click="showColumnModal = false">取消</button>
            <button class="primary-btn" @click="handleSaveColumns">保存配置</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showPrintModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 760px;">
        <div class="modal-header">
          <h2>选择打印模板</h2>
          <button class="close-btn" type="button" @click="showPrintModal = false">×</button>
        </div>
        <div style="display: flex; padding: 24px; min-height: 300px;">
          <!-- 内置打印 -->
          <div style="flex: 1; border-right: 1px dashed #dce3e0; padding-right: 24px;">
            <div style="font-weight: bold; margin-bottom: 16px; color: #586360;">内置打印</div>
            <div style="display: flex; flex-direction: column; gap: 16px;">
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedBuiltIn === 'flow' ? '#00b889' : '#586360', fontWeight: selectedBuiltIn === 'flow' ? 'bold' : 'normal' }">
                <input type="radio" name="builtIn" value="flow" v-model="selectedBuiltIn" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedBuiltIn === 'flow' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单流转卡-A4 / A5纸</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedBuiltIn === 'qr-thermal' ? '#00b889' : '#586360', fontWeight: selectedBuiltIn === 'qr-thermal' ? 'bold' : 'normal' }">
                <input type="radio" name="builtIn" value="qr-thermal" v-model="selectedBuiltIn" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedBuiltIn === 'qr-thermal' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单二维码-热敏打印</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedBuiltIn === 'qr-30-40' ? '#00b889' : '#586360', fontWeight: selectedBuiltIn === 'qr-30-40' ? 'bold' : 'normal' }">
                <input type="radio" name="builtIn" value="qr-30-40" v-model="selectedBuiltIn" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedBuiltIn === 'qr-30-40' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单二维码 (30*40mm)</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedBuiltIn === 'qr-80-60' ? '#00b889' : '#586360', fontWeight: selectedBuiltIn === 'qr-80-60' ? 'bold' : 'normal' }">
                <input type="radio" name="builtIn" value="qr-80-60" v-model="selectedBuiltIn" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedBuiltIn === 'qr-80-60' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单二维码 (80*60mm)</span>
              </label>
            </div>
            <div style="text-align: center; margin-top: 32px;">
              <button class="primary-btn" type="button" @click="doPrint" style="padding: 0 32px;">直接打印</button>
            </div>
          </div>
          <!-- Excel 模板打印 -->
          <div style="flex: 1; padding-left: 24px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
              <div style="font-weight: bold; color: #586360;">Excel模板打印</div>
              <div style="font-size: 12px; color: #586360; display: flex; align-items: center; gap: 4px;">
                <input type="checkbox" v-model="excelMultiImage" style="accent-color: #00b889; cursor: pointer;" id="excelMultiImage" />
                <label for="excelMultiImage" style="cursor: pointer; margin-right: 8px;">多图模式</label>
                <input type="checkbox" v-model="excelScaleImage" style="accent-color: #00b889; cursor: pointer;" id="excelScaleImage" />
                <label for="excelScaleImage" style="cursor: pointer;">图片等比缩放</label>
              </div>
            </div>
            <div style="display: flex; flex-direction: column; gap: 16px;">
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedExcelTpl === 'flow' ? '#00b889' : '#586360', fontWeight: selectedExcelTpl === 'flow' ? 'bold' : 'normal' }">
                <input type="radio" name="excelTpl" value="flow" v-model="selectedExcelTpl" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedExcelTpl === 'flow' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">工单流转卡.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedExcelTpl === 'qr' ? '#00b889' : '#586360', fontWeight: selectedExcelTpl === 'qr' ? 'bold' : 'normal' }">
                <input type="radio" name="excelTpl" value="qr" v-model="selectedExcelTpl" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedExcelTpl === 'qr' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">工单二维码.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedExcelTpl === 'list' ? '#00b889' : '#586360', fontWeight: selectedExcelTpl === 'list' ? 'bold' : 'normal' }">
                <input type="radio" name="excelTpl" value="list" v-model="selectedExcelTpl" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedExcelTpl === 'list' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">工单列表.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;" :style="{ color: selectedExcelTpl === 'materials' ? '#00b889' : '#586360', fontWeight: selectedExcelTpl === 'materials' ? 'bold' : 'normal' }">
                <input type="radio" name="excelTpl" value="materials" v-model="selectedExcelTpl" style="accent-color: #00b889; width: 16px; height: 16px; cursor: pointer;" />
                <svg viewBox="0 0 24 24" width="16" height="16" :stroke="selectedExcelTpl === 'materials' ? '#00b889' : 'currentColor'" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">用料清单.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
            </div>
            <div style="text-align: center; margin-top: 32px; display: flex; justify-content: center; gap: 16px;">
              <button class="primary-btn" type="button" @click="doExcelPrint('wps')" style="padding: 0 24px; background: #00b889;">WPS打印</button>
              <button class="ghost-btn" type="button" @click="doExcelPrint('download')" style="padding: 0 24px; color: #00b889; border-color: #00b889;">下载打印</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实际打印渲染区 (仅打印时显示) -->
    <div class="print-area">
      <div v-for="id in selectedIds" :key="`print-${id}`" class="print-card" :class="`tpl-${selectedBuiltIn}`">
        <template v-if="getWorkOrder(id)">
          <!-- 1. 工单流转卡版式 -->
          <div v-if="selectedBuiltIn === 'flow'">
            <div style="font-size: 18px; font-weight: bold; text-align: center; margin-bottom: 12px; color: #000;">工单流转卡</div>
            <table class="print-table">
              <tbody>
                <tr><td class="lbl">产品编号</td><td>{{ getWorkOrder(id)?.productCode }}</td><td rowspan="6" class="qr-cell"><img v-if="qrDataUrls[id]" :src="qrDataUrls[id]" width="120" height="120" style="margin: 0 auto; display: block;" /></td></tr>
                <tr><td class="lbl">产品名称</td><td>{{ getWorkOrder(id)?.productName }}</td></tr>
                <tr><td class="lbl">产品规格</td><td>{{ getWorkOrder(id)?.productSpec || '-' }}</td></tr>
                <tr><td class="lbl">工单计划数</td><td>{{ getWorkOrder(id)?.plannedQuantity }}</td></tr>
                <tr><td class="lbl">计划时间</td><td>{{ getWorkOrder(id)?.planStart || '-' }} | {{ getWorkOrder(id)?.planEnd || '-' }}</td></tr>
                <tr><td class="lbl" style="height: 60px;">备注</td><td>{{ getWorkOrder(id)?.remark || '' }}</td></tr>
              </tbody>
            </table>
          </div>

          <!-- 2. 热敏二维码标签版式 -->
          <div v-else-if="selectedBuiltIn === 'qr-thermal'" class="qr-thermal-layout">
            <div class="qr-label-title">工单二维码标签</div>
            <div class="qr-img-wrap">
              <img v-if="qrDataUrls[id]" :src="qrDataUrls[id]" class="qr-img-large" />
            </div>
            <div class="qr-info-row"><strong>工单编号：</strong>{{ getWorkOrder(id)?.code }}</div>
            <div class="qr-info-row"><strong>产品名称：</strong>{{ getWorkOrder(id)?.productName }}</div>
          </div>

          <!-- 3. 30x40mm坚版二维码标签 -->
          <div v-else-if="selectedBuiltIn === 'qr-30-40'" class="qr-30-40-layout">
            <div class="qr-mini-title">{{ customTemplates.qr_30_40.row1 }}</div>
            <div class="qr-mini-img-wrap">
              <img v-if="qrDataUrls[id]" :src="qrDataUrls[id]" class="qr-img-mini" />
            </div>
            <div class="qr-mini-info font-mono">{{ getFieldValue(getWorkOrder(id), customTemplates.qr_30_40.row1) }}</div>
            <div class="qr-mini-info text-ellipsis">{{ getFieldValue(getWorkOrder(id), customTemplates.qr_30_40.row2) }}</div>
          </div>

          <!-- 4. 80x60mm横版二维码标签 -->
          <div v-else-if="selectedBuiltIn === 'qr-80-60'" class="qr-80-60-card">
            <div class="qr-80-60-title-row">
              {{ customTemplates.qr_80_60.titleLabel }}: {{ getFieldValue(getWorkOrder(id), customTemplates.qr_80_60.titleField) }}
            </div>
            <div class="qr-80-60-body">
              <div class="qr-80-60-left">
                <div v-for="i in 5" :key="'print-row-'+i" class="qr-80-60-info-row">
                  <div class="qr-80-60-lbl">{{ getQr8060RowLabel(i) }}</div>
                  <div class="qr-80-60-val">
                    <div v-for="(v, vi) in getMultiFieldValues(getWorkOrder(id), getQr8060RowFields(i))" :key="vi">{{ v }}</div>
                  </div>
                </div>
                <div class="qr-80-60-info-row qr-80-60-remark-row">
                  <div class="qr-80-60-lbl">{{ customTemplates.qr_80_60.row6Label }}</div>
                  <div class="qr-80-60-val">
                    <div v-for="(v, vi) in getMultiFieldValues(getWorkOrder(id), customTemplates.qr_80_60.row6Field)" :key="vi">{{ v }}</div>
                  </div>
                </div>
              </div>
              <div class="qr-80-60-right">
                <img v-if="qrDataUrls[id]" :src="qrDataUrls[id]" class="qr-img-80-60" />
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </section>

  <DeleteConfirmDialog
    v-model:visible="deleteVisible"
    :title="deleteTitle"
    :message="deleteMessage"
    :loading="deleteLoading"
    @confirm="deleteHandleConfirm"
    @update:visible="val => { if (!val) deleteHandleCancel() }"
  />

  <!-- 生产任务高级选择器弹窗 (工序/工艺路线) -->
  <div v-if="showTaskSelector" class="modal-backdrop" style="z-index: 1020;">
    <div class="modal workorder-modal task-selector-modal" style="width: 680px; max-height: min(650px, calc(100vh - 40px)); display: flex; flex-direction: column; overflow: hidden; border-radius: 8px;">
      <!-- 弹窗头部 -->
      <div class="modal-header" style="flex-shrink: 0; background: #fafcfa; border-bottom: 1px solid #eef2f1;">
        <h2 style="font-weight: 600; color: #1e2724; display: flex; align-items: center; gap: 8px;">
          <svg viewBox="0 0 24 24" width="18" height="18" stroke="#00b889" stroke-width="2.5" fill="none" style="vertical-align: middle;"><rect x="3" y="3" width="7" height="9"></rect><rect x="14" y="3" width="7" height="5"></rect><rect x="14" y="12" width="7" height="9"></rect><rect x="3" y="16" width="7" height="5"></rect></svg>
          选择生产任务
        </h2>
        <button class="close-btn" type="button" @click="showTaskSelector = false" style="cursor: pointer;">×</button>
      </div>

      <!-- 弹窗主体 -->
      <div style="flex: 1; display: flex; flex-direction: column; overflow: hidden; padding: 20px;">
        <!-- 搜索框与 Tab 导航行 -->
        <div style="display: flex; gap: 16px; margin-bottom: 16px; align-items: center; flex-shrink: 0;">
          <!-- Tab 控制器 -->
          <div style="display: flex; background: #f0f4f2; padding: 3px; border-radius: 6px; border: 1px solid #e2e8e6;">
            <button 
              type="button" 
              @click="taskSelectorActiveTab = 'process'" 
              :style="{
                border: 'none',
                background: taskSelectorActiveTab === 'process' ? '#fff' : 'transparent',
                color: taskSelectorActiveTab === 'process' ? '#00b889' : '#586360',
                fontWeight: taskSelectorActiveTab === 'process' ? '600' : '400',
                boxShadow: taskSelectorActiveTab === 'process' ? '0 1px 3px rgba(0,0,0,0.06)' : 'none',
                padding: '6px 16px',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '13px',
                transition: 'all 0.2s'
              }"
            >
              按工序添加
            </button>
            <button 
              type="button" 
              @click="taskSelectorActiveTab = 'route'" 
              :style="{
                border: 'none',
                background: taskSelectorActiveTab === 'route' ? '#fff' : 'transparent',
                color: taskSelectorActiveTab === 'route' ? '#00b889' : '#586360',
                fontWeight: taskSelectorActiveTab === 'route' ? '600' : '400',
                boxShadow: taskSelectorActiveTab === 'route' ? '0 1px 3px rgba(0,0,0,0.06)' : 'none',
                padding: '6px 16px',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '13px',
                transition: 'all 0.2s'
              }"
            >
              按工艺路线添加
            </button>
          </div>

          <!-- 搜索过滤输入框 -->
          <div style="flex: 1; position: relative; display: flex; align-items: center;">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="#8a9693" stroke-width="2" fill="none" style="position: absolute; left: 12px; pointer-events: none;"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            <input 
              v-model="taskSelectorKeyword" 
              type="text" 
              :placeholder="taskSelectorActiveTab === 'process' ? '输入工序名称、工序编码搜索...' : '输入工艺路线名称搜索...'" 
              class="selector-search-input"
            />
            <button 
              v-if="taskSelectorKeyword" 
              type="button" 
              @click="taskSelectorKeyword = ''" 
              style="position: absolute; right: 10px; border: none; background: transparent; color: #8a9693; font-size: 16px; cursor: pointer;"
            >
              ×
            </button>
          </div>
        </div>

        <!-- 选项网格展示区域 -->
        <div style="flex: 1; overflow-y: auto; padding-right: 4px;">
          <!-- 暂无数据提示 -->
          <div v-if="taskSelectorActiveTab === 'process' && filteredProcesses.length === 0" style="padding: 48px 0; text-align: center; color: #8a9693; display: flex; flex-direction: column; align-items: center; gap: 8px;">
            <svg viewBox="0 0 24 24" width="32" height="32" stroke="#dce3e0" stroke-width="1.5" fill="none"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
            <span style="font-size: 13px;">没有找到匹配的工序</span>
          </div>
          <div v-if="taskSelectorActiveTab === 'route' && filteredRoutes.length === 0" style="padding: 48px 0; text-align: center; color: #8a9693; display: flex; flex-direction: column; align-items: center; gap: 8px;">
            <svg viewBox="0 0 24 24" width="32" height="32" stroke="#dce3e0" stroke-width="1.5" fill="none"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
            <span style="font-size: 13px;">没有找到匹配的工艺路线</span>
          </div>

          <!-- 工序选择列表 (Grid) -->
          <div v-if="taskSelectorActiveTab === 'process'" style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px;">
            <div 
              v-for="p in filteredProcesses" 
              :key="`sel-proc-${p.id}`" 
              @click="toggleSelectProcessInSelector(p)"
              @dblclick="addTaskFromProcess(p)"
              :style="{
                border: isProcessSelectedInSelector(p) ? '1.5px solid #00b889' : '1px solid #dce3e0',
                background: isProcessSelectedInSelector(p) ? '#f4fcf9' : '#fff',
                padding: '14px 16px',
                borderRadius: '6px',
                cursor: 'pointer',
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                transition: 'all 0.15s ease-in-out',
                boxShadow: isProcessSelectedInSelector(p) ? '0 2px 8px rgba(0, 184, 137, 0.08)' : '0 1px 3px rgba(0,0,0,0.02)'
              }"
              class="selector-card-hover"
            >
              <!-- 复选圈 -->
              <div :style="{
                width: '18px',
                height: '18px',
                borderRadius: '50%',
                border: isProcessSelectedInSelector(p) ? 'none' : '1.5px solid #bdc7c4',
                background: isProcessSelectedInSelector(p) ? '#00b889' : 'transparent',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }">
                <svg v-if="isProcessSelectedInSelector(p)" viewBox="0 0 24 24" width="10" height="10" stroke="#fff" stroke-width="3.5" fill="none"><polyline points="20 6 9 17 4 12"></polyline></svg>
              </div>
              <!-- 工序基础信息 -->
              <div style="flex: 1; overflow: hidden; display: flex; flex-direction: column; gap: 4px;">
                <span style="font-weight: 600; color: #1e2724; font-size: 14px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">
                  {{ p.name }}
                </span>
                <div style="display: flex; gap: 6px; align-items: center; flex-wrap: wrap;">
                  <span v-if="p.code" style="font-size: 11px; background: #eef2f1; color: #586360; padding: 1px 5px; border-radius: 3px;">
                    {{ p.code }}
                  </span>
                  <span style="font-size: 11px; background: #f0fbf8; color: #00b889; padding: 1px 5px; border-radius: 3px; display: inline-flex; align-items: center; gap: 2px;">
                    <svg viewBox="0 0 24 24" width="10" height="10" stroke="currentColor" stroke-width="2.5" fill="none"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                    {{ p.reportPermission?.[0] || '所有人' }}
                  </span>
                </div>
              </div>
              <!-- 单独极速添加按钮 -->
              <button 
                type="button" 
                @click.stop="addTaskFromProcess(p)" 
                style="border: none; background: transparent; padding: 4px; border-radius: 4px; cursor: pointer; color: #bdc7c4; transition: color 0.2s;"
                title="直接单选添加"
                onmouseover="this.style.color='#00b889'; this.style.background='#eefcf7';"
                onmouseout="this.style.color='#bdc7c4'; this.style.background='transparent';"
              >
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              </button>
            </div>
          </div>

          <!-- 工艺路线选择列表 (Grid) -->
          <div v-if="taskSelectorActiveTab === 'route'" style="display: flex; flex-direction: column; gap: 12px;">
            <div 
              v-for="r in filteredRoutes" 
              :key="`sel-route-${r.id}`" 
              @click="toggleSelectRouteInSelector(r)"
              @dblclick="addTaskFromRoute(r)"
              :style="{
                border: isRouteSelectedInSelector(r) ? '1.5px solid #00b889' : '1px solid #dce3e0',
                background: isRouteSelectedInSelector(r) ? '#f4fcf9' : '#fff',
                padding: '14px 16px',
                borderRadius: '6px',
                cursor: 'pointer',
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                transition: 'all 0.15s ease-in-out',
                boxShadow: isRouteSelectedInSelector(r) ? '0 2px 8px rgba(0, 184, 137, 0.08)' : '0 1px 3px rgba(0,0,0,0.02)'
              }"
              class="selector-card-hover"
            >
              <!-- 复选圈 -->
              <div :style="{
                width: '18px',
                height: '18px',
                borderRadius: '50%',
                border: isRouteSelectedInSelector(r) ? 'none' : '1.5px solid #bdc7c4',
                background: isRouteSelectedInSelector(r) ? '#00b889' : 'transparent',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }">
                <svg v-if="isRouteSelectedInSelector(r)" viewBox="0 0 24 24" width="10" height="10" stroke="#fff" stroke-width="3.5" fill="none"><polyline points="20 6 9 17 4 12"></polyline></svg>
              </div>
              <!-- 工艺路线基础信息与可视化链条 -->
              <div style="flex: 1; overflow: hidden; display: flex; flex-direction: column; gap: 6px;">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="font-weight: 600; color: #1e2724; font-size: 14px;">
                    {{ r.name }}
                  </span>
                  <span v-if="r.code" style="font-size: 11px; background: #eef2f1; color: #586360; padding: 1px 5px; border-radius: 3px;">
                    {{ r.code }}
                  </span>
                  <span style="font-size: 11px; background: #e6f7ff; color: #1890ff; padding: 1px 5px; border-radius: 3px;">
                    {{ r.steps?.length || 0 }} 个步骤
                  </span>
                </div>
                <!-- 核心！极其直观的工序链条预览 -->
                <div style="display: flex; align-items: center; flex-wrap: wrap; gap: 4px; padding: 6px 10px; background: rgba(0,0,0,0.02); border-radius: 4px; border: 1.5px dashed rgba(0,0,0,0.03);">
                  <template v-if="r.steps && r.steps.length > 0">
                    <span v-for="(step, idx) in r.steps" :key="`route-preview-step-${idx}`" style="display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #586360;">
                      <span style="font-weight: 500; color: #1e2724;">{{ getStepName(step) }}</span>
                      <svg v-if="idx < r.steps.length - 1" viewBox="0 0 24 24" width="12" height="12" stroke="#8a9693" stroke-width="2.5" fill="none" style="vertical-align: middle;"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
                    </span>
                  </template>
                  <span v-else style="font-size: 12px; color: #aaa;">暂无步骤工序</span>
                </div>
              </div>
              <!-- 单独极速添加按钮 -->
              <button 
                type="button" 
                @click.stop="addTaskFromRoute(r)" 
                style="border: none; background: transparent; padding: 4px; border-radius: 4px; cursor: pointer; color: #bdc7c4; transition: color 0.2s; align-self: center;"
                title="直接单选添加工艺路线"
                onmouseover="this.style.color='#00b889'; this.style.background='#eefcf7';"
                onmouseout="this.style.color='#bdc7c4'; this.style.background='transparent';"
              >
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 弹窗底部操作栏 -->
      <div class="modal-footer" style="flex-shrink: 0; background: #fafcfa; border-top: 1px solid #eef2f1; padding: 14px 20px; display: flex; justify-content: space-between; align-items: center;">
        <div style="font-size: 13px; color: #586360;">
          <span v-if="taskSelectorActiveTab === 'process' && taskSelectorSelectedProcesses.length > 0" style="color: #00b889; font-weight: 500;">
            已选中 {{ taskSelectorSelectedProcesses.length }} 个工序
          </span>
          <span v-else-if="taskSelectorActiveTab === 'route' && taskSelectorSelectedRoutes.length > 0" style="color: #00b889; font-weight: 500;">
            已选中 {{ taskSelectorSelectedRoutes.length }} 条工艺路线
          </span>
          <span v-else>
            提示：可双击卡片直接单选添加，也可多选批量导入
          </span>
        </div>
        <div style="display: flex; gap: 12px;">
          <button class="ghost-btn" type="button" @click="showTaskSelector = false" style="padding: 8px 20px; font-size: 13px;">取消</button>
          <button 
            class="primary-btn" 
            type="button" 
            @click="handleConfirmSelectorAdd" 
            :disabled="taskSelectorActiveTab === 'process' ? taskSelectorSelectedProcesses.length === 0 : taskSelectorSelectedRoutes.length === 0"
            :style="{
              padding: '8px 24px',
              fontSize: '13px',
              opacity: (taskSelectorActiveTab === 'process' ? taskSelectorSelectedProcesses.length === 0 : taskSelectorSelectedRoutes.length === 0) ? '0.5' : '1',
              cursor: (taskSelectorActiveTab === 'process' ? taskSelectorSelectedProcesses.length === 0 : taskSelectorSelectedRoutes.length === 0) ? 'not-allowed' : 'pointer'
            }"
          >
            确认添加
            <span v-if="taskSelectorActiveTab === 'process' && taskSelectorSelectedProcesses.length > 0">({{ taskSelectorSelectedProcesses.length }})</span>
            <span v-else-if="taskSelectorActiveTab === 'route' && taskSelectorSelectedRoutes.length > 0">({{ taskSelectorSelectedRoutes.length }})</span>
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- 导入日志毛玻璃弹窗 -->
  <SystemLogsDialog
    v-model:visible="showImportLogModal"
    :token="token"
    module="WORK_ORDER"
    type="import"
    title="工单导入日志"
  />

  <!-- 操作记录毛玻璃弹窗 -->
  <SystemLogsDialog
    v-model:visible="showLogs"
    :token="token"
    module="WORK_ORDER"
    type="operation"
    title="工单操作记录"
  />
</template>

<style scoped>
/* 精致超薄的进度条横向拖拉条样式 */
.scroll-thin {
  overflow-x: auto;
  scrollbar-width: thin;
  scrollbar-color: #cbd6d3 transparent;
  padding-bottom: 2px !important;
}

.scroll-thin::-webkit-scrollbar {
  height: 4px !important;
}

.scroll-thin::-webkit-scrollbar-track {
  background: transparent !important;
}

.scroll-thin::-webkit-scrollbar-thumb {
  background-color: #bdc7c4 !important;
  border-radius: 2px !important;
  transition: background-color 0.2s;
}

.scroll-thin::-webkit-scrollbar-thumb:hover {
  background-color: #0066cc !important;
}

.table-input-sm {
  width: 100%;
  min-width: 0;
  height: 30px;
  border: 1px solid #d5dedb;
  border-radius: 4px;
  padding: 0 8px;
  color: #1e2724;
  background: #fff;
  font-size: 13px;
  box-sizing: border-box;
  outline: none;
}

.table-input-sm:focus {
  border-color: #00b889;
  box-shadow: 0 0 0 2px rgba(0, 184, 137, 0.12);
}

.row-height-compact th, .row-height-compact td {
  height: 34px !important;
  padding: 0 8px !important;
}
.row-height-loose th, .row-height-loose td {
  height: 58px !important;
  padding: 0 16px !important;
}
.row-height-normal th, .row-height-normal td {
  height: 46px !important;
}

.column-config-popover {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  background: white;
  border: 1px solid #e2e8e6;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  padding: 16px;
  z-index: 50;
  width: 220px;
}
.column-config-popover h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
}
.col-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}
.col-list label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
}

.process-progress {
  display: flex;
  align-items: center;
}
.progress-node {
  display: flex;
  align-items: center;
}
.node-circle {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #e2e8e6;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #dce3e0;
}
.progress-node.completed .node-circle {
  background: #00b889;
  box-shadow: 0 0 0 1px #00b889;
}
.progress-node.partial .node-circle {
  background: #f5a623;
  box-shadow: 0 0 0 1px #f5a623;
}
.node-line {
  width: 16px;
  height: 2px;
  background: #e2e8e6;
  margin: 0 2px;
}
.progress-node.completed .node-line {
  background: #00b889;
}

.print-area {
  display: none;
}

.task-selector-item {
  padding: 8px 16px;
  font-size: 13px;
  color: #1e2724;
  cursor: pointer;
  transition: background 0.2s;
}
.task-selector-item:hover {
  background: #f0f4f2;
}
tr.drag-over {
  border-top: 2px solid #00b889;
}
.selector-card-hover {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
}
.selector-card-hover:hover {
  border-color: #00b889 !important;
  box-shadow: 0 4px 12px rgba(0, 184, 137, 0.12) !important;
  transform: translateY(-2px);
}
.selector-search-input {
  width: 100%;
  padding: 8px 12px 8px 34px;
  border: 1px solid #dce3e0;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
  background: #fff;
}
.selector-search-input:focus {
  border-color: #00b889 !important;
  box-shadow: 0 0 0 2px rgba(0, 184, 137, 0.1) !important;
}

/* 顶部大分类视觉增强 */
.category-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  padding: 4px 0;
}

.category-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.category-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid #e1e8e6;
  background-color: #ffffff;
  color: #5c6764;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  user-select: none;
}

.category-tab:hover {
  border-color: #00b889;
  color: #00b889;
  background-color: #f2faf8;
}

.category-tab.active {
  border-color: #00b889;
  background-color: #e6f6f3;
  color: #00b889;
  box-shadow: 0 2px 8px rgba(0, 184, 137, 0.15);
}

.cat-icon {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.category-tab:hover .cat-icon {
  transform: scale(1.1);
}

.cat-label {
  line-height: 1;
}

.cat-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.08);
  color: #8a9693;
  font-size: 10px;
  line-height: 1;
  margin-left: 4px;
  transition: all 0.2s ease;
  opacity: 0.4;
}

.category-tab:hover .cat-delete {
  opacity: 1;
}

.cat-delete:hover {
  background-color: #ef4444 !important;
  color: #ffffff !important;
}

/* 新增分类按钮样式 */
.add-cat-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px dashed #cbd6d3;
  background-color: transparent;
  color: #8a9693;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-cat-btn:hover {
  border-color: #00b889;
  color: #00b889;
  background-color: #f2faf8;
  transform: rotate(90deg);
}
</style>

<!-- 非 scoped 的打印样式，确保能作用到 #app / .sidebar / .admin-shell 等外部元素 -->
<style>
@media print {
  /* 彻底隐藏所有非打印区域 */
  .sidebar,
  .admin-shell > header,
  .collapse-toggle,
  .category-row,
  .toolbar,
  .table-container,
  .pagination,
  .modal-backdrop,
  .task-tooltip,
  .notice-bar,
  .toolbar-dropdown-container {
    display: none !important;
  }

  /* 消除包裹容器的布局限制 */
  html, body {
    background: #ffffff !important;
    height: auto !important;
    overflow: visible !important;
    margin: 0 !important;
    padding: 0 !important;
  }
  #app,
  .admin-shell,
  .admin-shell > section,
  .content {
    display: block !important;
    position: static !important;
    width: 100% !important;
    height: auto !important;
    min-height: auto !important;
    overflow: visible !important;
    margin: 0 !important;
    padding: 0 !important;
    box-shadow: none !important;
    background: #ffffff !important;
  }
  /* admin-shell 取消 grid/flex 布局 */
  .admin-shell {
    grid-template-columns: none !important;
  }

  /* 仅显示打印区域 */
  body * {
    visibility: hidden;
  }
  .print-area, .print-area * {
    visibility: visible;
  }
  .print-area {
    display: block !important;
    position: absolute !important;
    top: 0 !important;
    left: 0 !important;
    width: 100% !important;
    padding: 10mm !important;
    box-sizing: border-box !important;
  }

  /* 流转卡打印样式 */
  .print-card {
    page-break-after: always;
    margin-bottom: 40px;
  }
  .print-card:last-child {
    page-break-after: avoid;
  }
  .print-table {
    width: 100%;
    border-collapse: collapse;
  }
  .print-table td {
    border: 1px solid #000000;
    padding: 10px;
    font-size: 14px;
    color: #000000;
  }
  .print-table .lbl {
    width: 100px;
    font-weight: bold;
    text-align: center;
  }
  .print-table .qr-cell {
    width: 160px;
    text-align: center;
    vertical-align: middle;
  }

  /* 二维码热敏版式 */
  .qr-thermal-layout {
    width: 100%;
    padding: 10px;
    box-sizing: border-box;
    background: #ffffff;
    text-align: center;
  }
  .qr-label-title {
    font-size: 16px;
    font-weight: bold;
    margin-bottom: 8px;
    color: #000000;
    border-bottom: 1px dashed #000000;
    padding-bottom: 4px;
  }
  .qr-img-wrap {
    margin: 12px 0;
    display: flex;
    justify-content: center;
  }
  .qr-img-large {
    width: 140px;
    height: 140px;
    display: block;
  }
  .qr-info-row {
    font-size: 12px;
    color: #000000;
    text-align: left;
    margin-top: 4px;
    line-height: 1.4;
  }

  /* 30x40mm 竖版二维码标签 */
  .tpl-qr-30-40 {
    width: 30mm !important;
    height: 40mm !important;
    padding: 2mm !important;
    margin: 0 auto 10mm auto !important;
    box-sizing: border-box !important;
    border: 1px solid #000000 !important;
    background: #ffffff !important;
    display: flex !important;
    flex-direction: column !important;
    justify-content: space-between !important;
    align-items: center !important;
    page-break-after: always !important;
  }
  .qr-mini-title {
    font-size: 9px;
    font-weight: bold;
    color: #000000;
    margin: 0;
    padding: 0;
    line-height: 1;
    text-align: center;
  }
  .qr-mini-img-wrap {
    display: flex;
    justify-content: center;
    align-items: center;
    flex: 1;
  }
  .qr-img-mini {
    width: 22mm !important;
    height: 22mm !important;
    display: block;
  }
  .qr-mini-info {
    font-size: 8px;
    color: #000000;
    line-height: 1.1;
    text-align: center;
    width: 100%;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 80x60mm 横版二维码标签 */
  .tpl-qr-80-60 {
    width: 80mm !important;
    height: 60mm !important;
    padding: 2mm !important;
    margin: 0 auto 10mm auto !important;
    box-sizing: border-box !important;
    border: none !important;
    background: #ffffff !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    page-break-after: always !important;
  }
  .qr-80-60-card {
    width: 76mm !important;
    border: 2px solid #333 !important;
    padding: 0 !important;
    background: #fff !important;
    display: flex !important;
    flex-direction: column !important;
    box-sizing: border-box !important;
  }
  .qr-80-60-title-row {
    text-align: center !important;
    font-size: 11px !important;
    font-weight: bold !important;
    border-bottom: 1px solid #333 !important;
    padding: 5px 4px !important;
    word-break: break-all !important;
    color: #000 !important;
    line-height: 1.3 !important;
  }
  .qr-80-60-body {
    display: flex !important;
  }
  .qr-80-60-left {
    flex: 1 !important;
    border-right: 1px solid #333 !important;
    display: flex !important;
    flex-direction: column !important;
  }
  .qr-80-60-info-row {
    display: flex !important;
    border-bottom: 1px solid #333 !important;
    font-size: 9px !important;
    line-height: 1.2 !important;
    color: #000 !important;
  }
  .qr-80-60-info-row:last-child {
    border-bottom: none !important;
  }
  .qr-80-60-lbl {
    width: 16mm !important;
    min-width: 16mm !important;
    max-width: 16mm !important;
    border-right: 1px solid #333 !important;
    padding: 3px 2px !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    font-weight: bold !important;
    text-align: center !important;
    box-sizing: border-box !important;
    word-break: break-all !important;
  }
  .qr-80-60-val {
    flex: 1 !important;
    padding: 3px 3px !important;
    display: flex !important;
    flex-direction: column !important;
    justify-content: center !important;
    word-break: break-all !important;
    box-sizing: border-box !important;
  }
  .qr-80-60-remark-row {
    min-height: 10mm !important;
  }
  .qr-80-60-right {
    width: 26mm !important;
    min-width: 26mm !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    padding: 2mm !important;
    box-sizing: border-box !important;
  }
  .qr-img-80-60 {
    width: 22mm !important;
    height: 22mm !important;
    display: block !important;
    margin: 0 auto !important;
  }
}
</style>
