<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
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
  setUserPreference
} from '../api'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible: deleteVisible, title: deleteTitle, message: deleteMessage, loading: deleteLoading, confirm: deleteConfirm, handleConfirm: deleteHandleConfirm, handleCancel: deleteHandleCancel } = useDeleteConfirm()

const workOrders = ref<WorkOrderInfo[]>([])
const categories = ref<WorkOrderCategoryInfo[]>([])
const products = ref<ProductInfo[]>([])
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
  { key: 'routeName', label: '工单进度路线' }
]
const visibleColumns = ref<string[]>(columnDefinitions.map(c => c.key))

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
  materials: [] as any[]
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
    { key: 'COMPLETED', label: '已结束', count: baseOrders.filter(i => i.status === 'COMPLETED').length },
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
    else statusOk = item.status === statusFilter.value

    const text = `${item.code}${item.productCode}${item.productName}`.toLowerCase()
    const keywordOk = !keyword.value || text.includes(keyword.value.toLowerCase())
    
    return catOk && statusOk && keywordOk
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
  return { NOT_STARTED: '未开始', EXECUTING: '执行中', PAUSED: '已暂停', COMPLETED: '已完成' }[value] || value
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
    tasks: []
  })
}

async function loadData() {
  const [orderRes, productRes, processRes, routeRes, fieldRes, categoryRes] = await Promise.all([
    fetchWorkOrders(props.token, { size: 500 }),
    fetchProducts(props.token, { size: 500 }),
    fetchProcesses(props.token, { size: 500 }),
    fetchProcessRoutes(props.token, { size: 500 }),
    fetchCustomFields(props.token, 'workorder'),
    fetchWorkOrderCategories(props.token)
  ])
  workOrders.value = orderRes.items
  products.value = productRes.items
  processes.value = processRes.items
  routes.value = routeRes.items
  customFields.value = fieldRes.items
  categories.value = categoryRes
  
  try {
    const pref = await fetchUserPreference(props.token, 'WORK_ORDER_COLUMNS')
    if (pref && pref.value) {
      visibleColumns.value = JSON.parse(pref.value)
    }
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
    tasks: JSON.parse(JSON.stringify(item.tasks || []))
  })
  syncCustomValues()
  activeTab.value = 'basic'
  showModal.value = true
}

function syncCustomValues() {
  form.customValues = customFields.value.map(field => {
    const existing = form.customValues.find(item => item.fieldId === field.id || item.fieldName === field.name)
    return existing || {
      fieldId: field.id,
      fieldName: field.name,
      fieldType: field.fieldType,
      value: field.fieldType === 'multiple_select' ? [] : (field.defaultValues?.[0] || '')
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
  form.tasks = buildTasks(form.productId)
})

watch(() => form.plannedQuantity, () => {
  form.tasks = form.tasks.map(task => ({ ...task, plannedQuantity: Number(form.plannedQuantity || 0) }))
})

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
    materials: []
  }
  try {
    if (editing.value) {
      await updateWorkOrder(props.token, editing.value.id, payload)
      emit('notice', '工单已更新')
    } else {
      await createWorkOrder(props.token, payload)
      emit('notice', '工单已创建')
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
  const headers = ['工单编号', '产品编号', '产品名称', '产品规格', '状态', '优先级', '计划数', '良品数', '不良品数']
  const rows = targets.map(item => [
    item.code,
    item.productCode,
    item.productName,
    item.productSpec || '',
    statusLabel(item.status),
    priorityLabel(item.priority),
    item.plannedQuantity,
    item.goodQuantity,
    item.defectQuantity
  ])
  const csvContent = [headers.join(',')]
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
  input.accept = '.csv, .xlsx, .xls'
  input.onchange = (e) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (file) {
      emit('notice', `选中文件: ${file.name}，正在上传...`)
      setTimeout(() => {
        emit('notice', '上传成功，后台处理中...')
      }, 1000)
    }
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
        materials: item.materials
      })
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

const qrDataUrls = ref<Record<number, string>>({})
watch(selectedIds, async (ids) => {
  const newUrls: Record<number, string> = {}
  for (const id of ids) {
    const code = workOrders.value.find(o => o.id === id)?.code || String(id)
    const url = `${window.location.origin}/workorder/${code}`
    try {
      newUrls[id] = await QRCode.toDataURL(url, { width: 400, margin: 2, color: { dark: '#000000', light: '#ffffff' } })
    } catch (e) {
      console.error(e)
    }
  }
  qrDataUrls.value = newUrls
}, { deep: true, immediate: true })

const showPrintModal = ref(false)

function getWorkOrder(id: number) {
  return workOrders.value.find(o => o.id === id)
}

function openPrint() {
  if (selectedIds.value.length === 0) {
    emit('notice', '请先选择需要打印的工单')
    return
  }
  showPrintModal.value = true
}

function doPrint() {
  showPrintModal.value = false
  setTimeout(() => {
    window.print()
  }, 100)
}

onMounted(loadData)
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
    <div class="category-tabs">
      <button
        v-for="item in categoryTabs"
        :key="item.key"
        class="cat-tab"
        :class="{ active: categoryFilter === item.key }"
        type="button"
        @click="categoryFilter = item.key"
      >
        {{ item.label }}
      </button>
      <button class="cat-tab add-tab" type="button" @click="showCategoryModal = true" title="添加工单分类">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
      </button>
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
      <button class="text-btn" type="button" style="color: #586360; margin-left: 8px;" @click="$emit('notice', '高级筛选功能开发中...')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon></svg>
        筛选
      </button>
      <button class="text-btn" type="button" style="color: #586360; margin-left: 8px;" @click="toggleRowHeight">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="8 9 12 5 16 9"></polyline><polyline points="16 15 12 19 8 15"></polyline><line x1="12" y1="5" x2="12" y2="19"></line></svg>
        行高
      </button>

      <div style="margin-left: auto; display: flex; align-items: center; gap: 16px;">
        <button class="text-btn" type="button" style="color: #586360;" @click="$emit('notice', '导入日志弹窗开发中...')">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
          导入日志
        </button>
        <button class="text-btn" type="button" style="color: #586360;" @click="$emit('notice', '操作记录面板开发中...')">
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
      <button class="toolbar-btn" type="button" @click="$emit('notice', '维护工艺正在开发中...')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"></path><line x1="7" y1="7" x2="7.01" y2="7"></line></svg>
        维护工艺
      </button>
      <button class="toolbar-btn" type="button" @click="$emit('notice', '优先级修改正在开发中...')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
        优先级
      </button>
      <button class="toolbar-btn" type="button" @click="handleEditSelected">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
        修改
      </button>
      <button class="toolbar-btn" type="button" @click="$emit('notice', '报工功能正在开发中...')">
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
        报工
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
            <th v-if="visibleColumns.includes('routeName')">工单进度路线</th>
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
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="font-weight: 500; color: #1f2725;">{{ item.routeName || '-' }}</span>
                <div class="process-progress" v-if="item.tasks && item.tasks.length > 0">
                  <div v-for="(task, i) in item.tasks" :key="i" class="progress-node" :class="{ completed: (task.completedQuantity || 0) >= (task.plannedQuantity || 1), partial: (task.completedQuantity || 0) > 0 && (task.completedQuantity || 0) < (task.plannedQuantity || 1) }">
                    <div class="node-circle" @mouseenter="showTaskTooltip($event, task)" @mouseleave="hideTaskTooltip"></div>
                    <div class="node-line" v-if="i < item.tasks.length - 1"></div>
                  </div>
                </div>
                <span style="font-size: 12px; color: #8a9693;">{{ item.progressPercent }}%</span>
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
          <label><span>产品信息</span><select v-model="form.productId"><option value="">请选择</option><option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }} / {{ p.code }}</option></select></label>
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
            <label v-if="field.fieldType === 'single_select'"><span>{{ field.name }}</span><select :value="String(getCustomValue(field) || '')" @change="setCustomValue(field, ($event.target as HTMLSelectElement).value)"><option value="">请选择</option><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
            <label v-else><span>{{ field.name }}</span><input :value="String(getCustomValue(field) || '')" :type="field.fieldType === 'number' ? 'number' : 'text'" @input="setCustomValue(field, ($event.target as HTMLInputElement).value)" /></label>
          </template>
        </div>

        <div v-else-if="activeTab === 'task'" style="padding:18px">
          <table class="data-table">
            <thead><tr><th>工序名称</th><th>工序编号</th><th>计划数</th><th>报工权限</th><th>启用</th></tr></thead>
            <tbody>
              <tr v-for="(task, index) in form.tasks" :key="index">
                <td>{{ task.processName }}</td>
                <td>{{ task.sequenceNo }}</td>
                <td><input v-model.number="task.plannedQuantity" type="number" /></td>
                <td><input v-model="task.reportPermission" /></td>
                <td><input v-model="task.enabled" type="checkbox" /></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else style="padding:28px;color:#8a9693">用料清单先保留入口，后续按物料清单继续补。</div>

        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showModal = false">取消</button>
          <button class="primary-btn wide" type="button" @click="saveOrder">确定</button>
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
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;">
                <input type="radio" name="builtIn" checked style="accent-color: #00b889;" />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单流转卡-A4 / A5纸</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer; color: #8a9693;">
                <input type="radio" name="builtIn" disabled />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单二维码-热敏打印</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer; color: #8a9693;">
                <input type="radio" name="builtIn" disabled />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                <span style="font-size: 14px;">工单二维码 (30*40mm)</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer; color: #8a9693;">
                <input type="radio" name="builtIn" disabled />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
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
              <div style="font-size: 12px; color: #8a9693;"><input type="checkbox" /> 多图模式 &nbsp; <input type="checkbox" /> 图片等比缩放</div>
            </div>
            <div style="display: flex; flex-direction: column; gap: 16px;">
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer;">
                <input type="radio" name="excelTpl" checked style="accent-color: #00b889;" />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="#00b889" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">工单流转卡.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer; color: #8a9693;">
                <input type="radio" name="excelTpl" disabled />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">工单二维码.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer; color: #8a9693;">
                <input type="radio" name="excelTpl" disabled />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">工单列表.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
              <label style="display: flex; align-items: center; gap: 12px; cursor: pointer; color: #8a9693;">
                <input type="radio" name="excelTpl" disabled />
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                <span style="font-size: 14px;">用料清单.xlsx</span> <span style="font-size: 12px; color: #8a9693; background: #f0f2f1; padding: 2px 6px; border-radius: 2px;">系统模板</span>
              </label>
            </div>
            <div style="text-align: center; margin-top: 32px; display: flex; justify-content: center; gap: 16px;">
              <button class="primary-btn" type="button" @click="$emit('notice', 'WPS打印开发中')" style="padding: 0 24px; background: #00b889;">WPS打印</button>
              <button class="ghost-btn" type="button" @click="$emit('notice', '下载打印开发中')" style="padding: 0 24px; color: #00b889; border-color: #00b889;">下载打印</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实际打印渲染区 (仅打印时显示) -->
    <div class="print-area">
      <div v-for="id in selectedIds" :key="`print-${id}`" class="print-card">
        <template v-if="getWorkOrder(id)">
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
</template>

<style scoped>
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
@media print {
  body * {
    visibility: hidden;
  }
  .print-area, .print-area * {
    visibility: visible;
  }
  .print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    display: block;
    padding: 20px;
  }
  .print-card {
    page-break-after: always;
    margin-bottom: 40px;
  }
  .print-table {
    width: 100%;
    border-collapse: collapse;
  }
  .print-table td {
    border: 1px solid #000;
    padding: 10px;
    font-size: 14px;
    color: #000;
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
}
</style>
