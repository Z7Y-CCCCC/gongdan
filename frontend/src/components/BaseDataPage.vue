<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  fetchUnits, createUnit, updateUnit, deleteUnit,
  fetchDefects, createDefect, updateDefect, deleteDefect,
  fetchProcesses, createProcess, updateProcess, deleteProcess,
  fetchProcessRoutes, createProcessRoute, updateProcessRoute, deleteProcessRoute,
  fetchDepartments,
  type UnitInfo, type UnitPayload,
  type DefectInfo, type DefectPayload,
  type ProcessInfo, type ProcessPayload,
  type ProcessRouteInfo, type ProcessRoutePayload,
  type DepartmentInfo, type UserInfo
} from '../api'

const props = defineProps<{
  token: string
  activeBasedataMenu: string
  users: UserInfo[]
}>()

const emit = defineEmits<{
  (e: 'error', msg: string): void
  (e: 'notice', msg: string): void
  (e: 'confirm', payload: { title: string; message: string; action: () => void }): void
}>()

const errorMessage = ref('')
watch(errorMessage, (v) => { if (v) emit('error', v) })

// 全局 Tooltip
const tooltipState = reactive({ visible: false, text: '', x: 0, y: 0 })
function showTooltip(e: MouseEvent, text: string) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  tooltipState.x = rect.left + rect.width / 2
  tooltipState.y = rect.top - 8
  tooltipState.text = text
  tooltipState.visible = true
}
function hideTooltip() { tooltipState.visible = false }

// 单位管理
const units = ref<UnitInfo[]>([])
const unitFilterForm = reactive({ name: '', remark: '' })
const unitFilteredList = computed(() =>
  units.value.filter(u => {
    const nameOk = !unitFilterForm.name || u.name.includes(unitFilterForm.name)
    const remarkOk = !unitFilterForm.remark || !u.remark || u.remark.includes(unitFilterForm.remark)
    return nameOk && remarkOk
  })
)
const showUnitModal = ref(false)
const editingUnit = ref<UnitInfo | null>(null)
const unitForm = reactive({ name: '', remark: '' })
const unitPage = ref(1)
const unitPageSize = ref(10)
const unitTotalPages = computed(() => Math.max(1, Math.ceil(unitFilteredList.value.length / unitPageSize.value)))

async function loadUnits() {
  try {
    const res = await fetchUnits(props.token)
    units.value = res.items
  } catch (e) { console.error('加载单位失败', e) }
}
function openCreateUnit() {
  editingUnit.value = null
  unitForm.name = ''
  unitForm.remark = ''
  showUnitModal.value = true
}
function openEditUnit(unit: UnitInfo) {
  editingUnit.value = unit
  unitForm.name = unit.name
  unitForm.remark = unit.remark || ''
  showUnitModal.value = true
}
async function handleSaveUnit() {
  if (!unitForm.name.trim()) return
  try {
    if (editingUnit.value) {
      const updated = await updateUnit(props.token, editingUnit.value.id, { name: unitForm.name, remark: unitForm.remark })
      const idx = units.value.findIndex(u => u.id === editingUnit.value!.id)
      if (idx !== -1) units.value[idx] = updated
    } else {
      const created = await createUnit(props.token, { name: unitForm.name, remark: unitForm.remark })
      units.value.unshift(created)
    }
    showUnitModal.value = false
  } catch (e: any) { errorMessage.value = e.message || '保存单位失败' }
}
async function handleDeleteUnit(unit: UnitInfo) {
  try {
    await deleteUnit(props.token, unit.id)
    units.value = units.value.filter(u => u.id !== unit.id)
  } catch (e: any) { errorMessage.value = e.message || '删除单位失败' }
}

// 不良品项管理
const defects = ref<DefectInfo[]>([])
const defectFilterForm = reactive({ code: '', name: '' })
const defectFilteredList = computed(() =>
  defects.value.filter(d => {
    const codeOk = !defectFilterForm.code || d.code.includes(defectFilterForm.code)
    const nameOk = !defectFilterForm.name || d.name.includes(defectFilterForm.name)
    return codeOk && nameOk
  })
)
const showDefectModal = ref(false)
const editingDefect = ref<DefectInfo | null>(null)
const defectForm = reactive({ code: '', name: '' })
const defectPage = ref(1)
const defectPageSize = ref(10)
const defectTotalPages = computed(() => Math.max(1, Math.ceil(defectFilteredList.value.length / defectPageSize.value)))

async function loadDefects() {
  try {
    const res = await fetchDefects(props.token)
    defects.value = res.items
  } catch (e) { console.error('加载不良品项失败', e) }
}
function openCreateDefect() {
  editingDefect.value = null
  defectForm.code = ''
  defectForm.name = ''
  showDefectModal.value = true
}
function openEditDefect(d: DefectInfo) {
  editingDefect.value = d
  defectForm.code = d.code
  defectForm.name = d.name
  showDefectModal.value = true
}
async function handleSaveDefect() {
  if (!defectForm.name.trim()) return
  try {
    if (editingDefect.value) {
      const updated = await updateDefect(props.token, editingDefect.value.id, { code: defectForm.code, name: defectForm.name })
      const idx = defects.value.findIndex(d => d.id === editingDefect.value!.id)
      if (idx !== -1) defects.value[idx] = updated
    } else {
      const created = await createDefect(props.token, { code: defectForm.code || undefined, name: defectForm.name })
      defects.value.unshift(created)
    }
    showDefectModal.value = false
  } catch (e: any) { errorMessage.value = e.message || '保存不良品项失败' }
}
async function handleDeleteDefect(d: DefectInfo) {
  try {
    await deleteDefect(props.token, d.id)
    defects.value = defects.value.filter(item => item.id !== d.id)
  } catch (e: any) { errorMessage.value = e.message || '删除不良品项失败' }
}

// 工序管理
interface CollectDataRow {
  type: string
  name: string
}
interface ProcessItem {
  id: number
  code: string
  name: string
  reportPermission: string[]
  reportRatio: number
  defectItemIds: number[]
  taskPunchIn: boolean
  goodQuantity: string
  machineName: string
  feedCount: string
  correspondingMachine: string
  requirements: string
  collectData: CollectDataRow[]
}
const processes = ref<ProcessItem[]>([])
const processFilterForm = reactive({ code: '', name: '', creator: '' })
const processFilteredList = computed(() =>
  processes.value.filter(p => {
    const codeOk = !processFilterForm.code || p.code.includes(processFilterForm.code)
    const nameOk = !processFilterForm.name || p.name.includes(processFilterForm.name)
    return codeOk && nameOk
  })
)
// 'list' | 'create' | 'edit'
const processView = ref<'list' | 'create' | 'edit'>('list')
const editingProcess = ref<ProcessItem | null>(null)
const processNextId = ref(1)
const processForm = reactive<{
  code: string
  name: string
  reportPermission: string[]
  reportRatio: number
  defectItemIds: number[]
  taskPunchIn: boolean
  goodQuantity: string
  machineName: string
  feedCount: string
  correspondingMachine: string
  requirements: string
  collectData: CollectDataRow[]
}>({
  code: '',
  name: '',
  reportPermission: ['所有人'],
  reportRatio: 1,
  defectItemIds: [],
  taskPunchIn: false,
  goodQuantity: '',
  machineName: '',
  feedCount: '',
  correspondingMachine: '',
  requirements: '',
  collectData: []
})
const processPage = ref(1)
const processPageSize = ref(10)
const processTotalPages = computed(() => Math.max(1, Math.ceil(processFilteredList.value.length / processPageSize.value)))

// ===== 工艺路线管理 =====
const routePage = ref(1)
const routePageSize = ref(10)
const routeTotalPages = computed(() => Math.max(1, Math.ceil(routeFilteredList.value.length / routePageSize.value)))
interface RouteStep {
  id: number          // 行内唯一id，用于拖拽key
  type: 'process' | 'route'
  processId: number | null  // 选中的工序或工艺路线 id
  reportRatio: number
  remark: string
}
interface RouteItem {
  id: number
  code: string
  name: string
  steps: RouteStep[]
  creator: string
  createdAt: string
}
const routes = ref<RouteItem[]>([])
const routeFilterForm = reactive({ code: '', name: '', processId: '' })
const routeFilteredList = computed(() =>
  routes.value.filter(r => {
    const codeOk = !routeFilterForm.code || r.code.includes(routeFilterForm.code)
    const nameOk = !routeFilterForm.name || r.name.includes(routeFilterForm.name)
    return codeOk && nameOk
  })
)
const routeView = ref<'list' | 'create' | 'edit'>('list')
const editingRoute = ref<RouteItem | null>(null)
const routeNextId = ref(1)
const routeStepNextId = ref(1)
const routeForm = reactive<{ code: string; name: string; steps: RouteStep[] }>({
  code: '',
  name: '',
  steps: []
})

// 工序下拉搜索（每行独立）
const routeStepSearchText = ref<Record<number, string>>({})
const routeStepDropdownOpen = ref<Record<number, boolean>>({})

function filteredItemsForStep(stepId: number) {
  const step = routeForm.steps.find(s => s.id === stepId)
  const s = (routeStepSearchText.value[stepId] || '').trim().toLowerCase()
  if (step && step.type === 'route') {
    // 类型为工艺路线 → 返回路线列表（排除当前正在编辑的路线，防止自引用）
    const editingId = editingRoute.value?.id
    const list = routes.value.filter(r => r.id !== editingId)
    if (!s) return list.map(r => ({ id: r.id, name: r.name, code: r.code }))
    return list.filter(r => (r.name || '').toLowerCase().includes(s) || (r.code || '').toLowerCase().includes(s))
      .map(r => ({ id: r.id, name: r.name, code: r.code }))
  }
  // 类型为工序 → 返回工序列表
  if (!s) return processes.value.map(p => ({ id: p.id, name: p.name, code: p.code }))
  return processes.value.filter(p => (p.name || '').toLowerCase().includes(s) || (p.code || '').toLowerCase().includes(s))
    .map(p => ({ id: p.id, name: p.name, code: p.code }))
}

function openRouteStepDropdown(stepId: number) {
  const isOpen = routeStepDropdownOpen.value[stepId]
  const newMap: Record<number, boolean> = {}
  Object.keys(routeStepDropdownOpen.value).forEach(k => {
    newMap[Number(k)] = false
  })
  newMap[stepId] = !isOpen
  routeStepDropdownOpen.value = newMap
  console.log('Toggle route step dropdown', stepId, 'isOpen:', !isOpen)
}

function closeAllRouteStepDropdowns() {
  const newMap: Record<number, boolean> = {}
  Object.keys(routeStepDropdownOpen.value).forEach(k => {
    newMap[Number(k)] = false
  })
  routeStepDropdownOpen.value = newMap
}
function selectRouteStepItem(step: RouteStep, itemId: number) {
  step.processId = itemId
  if (step.type === 'process') {
    const p = processes.value.find(x => x.id === itemId)
    step.reportRatio = p ? p.reportRatio : 1
  } else {
    step.reportRatio = 1
  }
  routeStepDropdownOpen.value[step.id] = false
}
function getStepItemName(step: RouteStep) {
  if (!step.processId) return ''
  if (step.type === 'route') {
    const r = routes.value.find(x => x.id === step.processId)
    return r ? r.name : ''
  }
  const p = processes.value.find(x => x.id === step.processId)
  return p ? p.name : ''
}
function getProcessName(pid: number | null) {
  if (!pid) return ''
  const p = processes.value.find(x => x.id === pid)
  if (p) return p.name
  const r = routes.value.find(x => x.id === pid)
  return r ? r.name : ''
}

function addRouteStep() {
  const id = routeStepNextId.value++
  routeForm.steps.push({ id, type: 'process', processId: null, reportRatio: 1, remark: '' })
  routeStepSearchText.value[id] = ''
  routeStepDropdownOpen.value[id] = false
}
function removeRouteStep(idx: number) {
  const step = routeForm.steps[idx]
  delete routeStepSearchText.value[step.id]
  delete routeStepDropdownOpen.value[step.id]
  routeForm.steps.splice(idx, 1)
}

// 拖拽排序
const routeDragIdx = ref<number | null>(null)
const routeDragOverIdx = ref<number | null>(null)
function onRouteDragStart(idx: number, e: DragEvent) {
  routeDragIdx.value = idx
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onRouteDragOver(idx: number, e: DragEvent) {
  e.preventDefault()
  routeDragOverIdx.value = idx
}
function onRouteDrop(idx: number) {
  if (routeDragIdx.value === null || routeDragIdx.value === idx) {
    routeDragIdx.value = null
    routeDragOverIdx.value = null
    return
  }
  const moved = routeForm.steps.splice(routeDragIdx.value, 1)[0]
  routeForm.steps.splice(idx, 0, moved)
  routeDragIdx.value = null
  routeDragOverIdx.value = null
}
function onRouteDragEnd() {
  routeDragIdx.value = null
  routeDragOverIdx.value = null
}

function openCreateRoute() {
  editingRoute.value = null
  routeForm.code = ''
  routeForm.name = ''
  routeForm.steps = []
  routeStepSearchText.value = {}
  routeStepDropdownOpen.value = {}
  routeView.value = 'create'
}
function openEditRoute(r: RouteItem) {
  editingRoute.value = r
  routeForm.code = r.code
  routeForm.name = r.name
  routeForm.steps = r.steps.map(s => ({ ...s }))
  routeStepSearchText.value = {}
  routeStepDropdownOpen.value = {}
  routeForm.steps.forEach(s => {
    routeStepSearchText.value[s.id] = ''
    routeStepDropdownOpen.value[s.id] = false
  })
  routeView.value = 'edit'
}
async function handleSaveRoute() {
  if (!routeForm.name.trim()) { errorMessage.value = '工艺路线名称不能为空'; return }
  if (!props.token) return
  const payload: ProcessRoutePayload = {
    code: routeForm.code.trim() || undefined,
    name: routeForm.name.trim(),
    steps: routeForm.steps.map(s => ({
      type: s.type,
      processId: s.processId,
      reportRatio: s.reportRatio,
      remark: s.remark
    }))
  }
  try {
    if (editingRoute.value) {
      await updateProcessRoute(props.token, editingRoute.value.id, payload)
    } else {
      await createProcessRoute(props.token, payload)
    }
    await loadRoutes()
    routeView.value = 'list'
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '保存失败'
  }
}
function confirmDeleteRoute(r: RouteItem) {
  emit('confirm', {
    title: '删除确认',
    message: `是否确要删除工艺路线「${r.name}」？`,
    action: async () => {
    if (!props.token) return
    try {
      await deleteProcessRoute(props.token, r.id)
      await loadRoutes()
    } catch (e) {
      errorMessage.value = e instanceof Error ? e.message : '删除失败'
    }
  }
  })
}
async function loadRoutes() {
  if (!props.token) return
  try {
    const res = await fetchProcessRoutes(props.token, { size: 999 })
    routes.value = res.items.map(r => ({
      id: r.id,
      code: r.code,
      name: r.name,
      steps: (r.steps || []).map((s, i) => ({
        id: i + 1,
        type: (s.type as 'process' | 'route') || 'process',
        processId: s.processId,
        reportRatio: s.reportRatio ?? 1,
        remark: s.remark || ''
      })),
      creator: r.creator || '',
      createdAt: r.createdAt ? r.createdAt.slice(0, 10) : ''
    }))
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '工艺路线加载失败'
  }
}

// 部门数据（提前声明，供报工权限树使用）
const departments = ref<DepartmentInfo[]>([])
const departmentTotal = ref(0)
const departmentsLoading = ref(false)
const departmentFilterForm = reactive({ code: '', name: '', page: 1, size: 10 })

// 报工权限选项 - 树形结构（部门 + 员工，动态从 departments & users 生成）
interface PermEmployee { id: string; name: string }
interface PermDept { id: string; name: string; employees: PermEmployee[] }
const permTree = computed<PermDept[]>(() =>
  departments.value.map(dept => ({
    id: `dept-${dept.id}`,
    name: dept.name,
    employees: props.users
      .filter(u => u.departmentId === dept.id && u.enabled)
      .map(u => ({ id: `emp-${u.id}`, name: u.name }))
  }))
)
const permSearchText = ref('')
const permExpandedDepts = ref<Set<string>>(new Set())
const filteredPermTree = computed(() => {
  const s = permSearchText.value.trim().toLowerCase()
  if (!s) return permTree.value
  return permTree.value.map(dept => ({
    ...dept,
    employees: dept.employees.filter(e => e.name.toLowerCase().includes(s))
  })).filter(dept => dept.name.toLowerCase().includes(s) || dept.employees.length > 0)
})
function togglePermDeptExpand(deptId: string) {
  if (permExpandedDepts.value.has(deptId)) permExpandedDepts.value.delete(deptId)
  else permExpandedDepts.value.add(deptId)
}
function isDeptExpanded(deptId: string) { return permExpandedDepts.value.has(deptId) }
// 判断员工 key 是否显示为选中（直接选中、或部门已选、或所有人已选）
function isPermSelected(key: string) {
  const perms = processForm.reportPermission
  if (perms.includes('all')) return true
  if (perms.includes(key)) return true
  // 如果是员工 key，检查所在部门是否已选
  if (key.startsWith('emp-')) {
    for (const dept of permTree.value) {
      if (dept.employees.some(e => e.id === key) && perms.includes(dept.id)) return true
    }
  }
  return false
}
function isDeptPartialSelected(dept: PermDept) {
  const perms = processForm.reportPermission
  // 'all' 或整个部门已选 → 不是"部分"
  if (perms.includes('all')) return false
  if (perms.includes(dept.id)) return false
  // 没有员工的部门不存在部分选中
  if (dept.employees.length === 0) return false
  const anySelected = dept.employees.some(e => perms.includes(e.id))
  const allSelected = dept.employees.every(e => perms.includes(e.id))
  return anySelected && !allSelected
}
function isDeptAllSelected(dept: PermDept) {
  const perms = processForm.reportPermission
  // 'all' 全选
  if (perms.includes('all')) return true
  // dept-id 在数组里 表示整个部门已选
  if (perms.includes(dept.id)) return true
  // 或者每个员工都单独被选中
  return dept.employees.length > 0 && dept.employees.every(e => perms.includes(e.id))
}
function togglePermAll() {
  // 已是全选 → 清空；否则 → 全选（设为 all）
  if (processForm.reportPermission.includes('all')) {
    processForm.reportPermission = []
  } else {
    processForm.reportPermission = ['all']
  }
}
function togglePermDept(dept: PermDept) {
  // 取消"所有人"
  const allIdx = processForm.reportPermission.indexOf('all')
  if (allIdx !== -1) processForm.reportPermission.splice(allIdx, 1)

  if (isDeptAllSelected(dept)) {
    // 已全选 → 取消部门id和所有员工id
    processForm.reportPermission = processForm.reportPermission.filter(
      k => k !== dept.id && !dept.employees.some(e => e.id === k)
    )
  } else {
    // 未全选 → 只存 dept-id，移除旧的散装员工id（避免重复）
    processForm.reportPermission = processForm.reportPermission.filter(
      k => !dept.employees.some(e => e.id === k)
    )
    if (!processForm.reportPermission.includes(dept.id)) {
      processForm.reportPermission.push(dept.id)
    }
  }
}
function togglePermEmployee(dept: PermDept, emp: PermEmployee) {
  // 取消"所有人"
  const allIdx = processForm.reportPermission.indexOf('all')
  if (allIdx !== -1) processForm.reportPermission.splice(allIdx, 1)

  const deptSelected = processForm.reportPermission.includes(dept.id)

  if (deptSelected) {
    // 部门整体已选 → 展开为散装员工，再取消该员工
    processForm.reportPermission = processForm.reportPermission.filter(k => k !== dept.id)
    dept.employees.forEach(e => {
      if (e.id !== emp.id && !processForm.reportPermission.includes(e.id)) {
        processForm.reportPermission.push(e.id)
      }
    })
  } else {
    const empIdx = processForm.reportPermission.indexOf(emp.id)
    if (empIdx !== -1) {
      // 已选员工 → 取消
      processForm.reportPermission.splice(empIdx, 1)
    } else {
      // 未选员工 → 加入
      processForm.reportPermission.push(emp.id)
      // 如果部门所有员工都被单独选中了，合并成 dept-id
      if (dept.employees.every(e => processForm.reportPermission.includes(e.id))) {
        processForm.reportPermission = processForm.reportPermission.filter(
          k => !dept.employees.some(e => e.id === k)
        )
        if (!processForm.reportPermission.includes(dept.id)) {
          processForm.reportPermission.push(dept.id)
        }
      }
    }
  }
}
function getPermLabel(key: string): string {
  if (key === 'all') return '所有人'
  for (const dept of permTree.value) {
    if (dept.id === key) return dept.name
    const emp = dept.employees.find(e => e.id === key)
    if (emp) return emp.name
  }
  return key
}
function removeProcessPermission(key: string) {
  processForm.reportPermission = processForm.reportPermission.filter(x => x !== key)
}

/**
 * 将报工权限 key 数组转换为展示用标签列表
 * 返回 [{ label, tooltip, partial }]
 *   label: 显示文本
 *   tooltip: 鼠标悬停提示（有权限的员工列表，仅 partial 时有值）
 *   partial: 是否"有部分员工无权限"
 */
function formatPermissionTags(perms: string[]): { label: string; tooltip: string; partial: boolean }[] {
  if (!perms || perms.length === 0) return [{ label: '—', tooltip: '', partial: false }]
  if (perms.includes('all')) return [{ label: '所有人', tooltip: '', partial: false }]

  const tags: { label: string; tooltip: string; partial: boolean }[] = []
  const handled = new Set<string>()

  for (const dept of permTree.value) {
    const deptSelected = perms.includes(dept.id)
    const selectedEmps = dept.employees.filter(e => perms.includes(e.id))
    const allEmps = dept.employees

    if (deptSelected || (allEmps.length > 0 && selectedEmps.length === allEmps.length)) {
      // 整个部门都有权限
      tags.push({ label: dept.name, tooltip: '', partial: false })
      handled.add(dept.id)
      allEmps.forEach(e => handled.add(e.id))
    } else if (selectedEmps.length > 0) {
      // 部分员工有权限
      const names = selectedEmps.map(e => e.name).join('、')
      tags.push({
        label: `${dept.name}（有部分员工无权限）`,
        tooltip: `有权限的员工：${names}`,
        partial: true
      })
      handled.add(dept.id)
      selectedEmps.forEach(e => handled.add(e.id))
    }
  }

  // 未被部门覆盖的单独员工 key
  for (const key of perms) {
    if (!handled.has(key) && key.startsWith('emp-')) {
      const name = getPermLabel(key)
      tags.push({ label: name, tooltip: '', partial: false })
    }
  }

  return tags.length > 0 ? tags : [{ label: '—', tooltip: '', partial: false }]
}

// 工序不良品项下拉
const showProcessDefectDropdown = ref(false)
// 工序报工权限下拉
const showProcessPermDropdown = ref(false)

/**
 * 标签框展示用的 key 列表（过滤掉被 dept-id 覆盖的散装员工 key，避免重复显示）
 */
const visiblePermKeys = computed(() => {
  const perms = processForm.reportPermission
  if (perms.includes('all')) return ['all']
  // 找出所有已选 dept-id 覆盖的员工 id 集合
  const coveredEmpIds = new Set<string>()
  for (const dept of permTree.value) {
    if (perms.includes(dept.id)) {
      dept.employees.forEach(e => coveredEmpIds.add(e.id))
    }
  }
  // 只显示 dept-id 和未被覆盖的散装员工 key
  return perms.filter(k => !coveredEmpIds.has(k))
})

function openCreateProcess() {
  editingProcess.value = null
  processForm.code = ''
  processForm.name = ''
  processForm.reportPermission = ['all']
  processForm.reportRatio = 1
  processForm.defectItemIds = []
  processForm.taskPunchIn = false
  processForm.goodQuantity = ''
  processForm.machineName = ''
  processForm.feedCount = ''
  processForm.correspondingMachine = ''
  processForm.requirements = ''
  processForm.collectData = []
  permSearchText.value = ''
  permExpandedDepts.value = new Set()
  processView.value = 'create'
}
function openEditProcess(p: ProcessItem) {
  editingProcess.value = p
  processForm.code = p.code
  processForm.name = p.name
  processForm.reportPermission = [...p.reportPermission]
  processForm.reportRatio = p.reportRatio
  processForm.defectItemIds = [...p.defectItemIds]
  processForm.taskPunchIn = p.taskPunchIn
  processForm.goodQuantity = p.goodQuantity
  processForm.machineName = p.machineName
  processForm.feedCount = p.feedCount
  processForm.correspondingMachine = p.correspondingMachine
  processForm.requirements = p.requirements
  processForm.collectData = p.collectData.map(r => ({ ...r }))
  processView.value = 'edit'
}
async function handleSaveProcess() {
  if (!processForm.name.trim()) { errorMessage.value = '工序名称不能为空'; return }
  if (!props.token) return
  const payload: ProcessPayload = {
    code: processForm.code.trim() || undefined,
    name: processForm.name.trim(),
    reportPermission: [...processForm.reportPermission],
    reportRatio: processForm.reportRatio,
    defectItemIds: [...processForm.defectItemIds],
    taskPunchIn: processForm.taskPunchIn,
    goodQuantity: processForm.goodQuantity,
    machineName: processForm.machineName,
    feedCount: processForm.feedCount,
    correspondingMachine: processForm.correspondingMachine,
    requirements: processForm.requirements,
    collectData: processForm.collectData.map(r => ({ type: r.type, name: r.name }))
  }
  try {
    if (editingProcess.value) {
      await updateProcess(props.token, editingProcess.value.id, payload)
    } else {
      await createProcess(props.token, payload)
    }
    await loadProcesses()
    processView.value = 'list'
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '保存失败'
  }
}
async function handleDeleteProcess(p: ProcessItem) {
  if (!props.token) return
  try {
    await deleteProcess(props.token, p.id)
    await loadProcesses()
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '删除失败'
  }
}
async function loadProcesses() {
  if (!props.token) return
  try {
    const res = await fetchProcesses(props.token, { size: 999 })
    processes.value = res.items.map(p => ({
      id: p.id,
      code: p.code,
      name: p.name,
      reportPermission: p.reportPermission || ['所有人'],
      reportRatio: p.reportRatio ?? 1,
      defectItemIds: p.defectItemIds || [],
      taskPunchIn: p.taskPunchIn ?? false,
      goodQuantity: p.goodQuantity || '',
      machineName: p.machineName || '',
      feedCount: p.feedCount || '',
      correspondingMachine: p.correspondingMachine || '',
      requirements: p.requirements || '',
      collectData: (p.collectData || []).map(c => ({ type: c.type, name: c.name }))
    }))
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '工序加载失败'
  }
}
function addCollectDataRow() {
  if (processForm.collectData.length < 50) {
    processForm.collectData.push({ type: '', name: '' })
  }
}
function removeCollectDataRow(idx: number) {
  processForm.collectData.splice(idx, 1)
}
function toggleProcessDefect(id: number) {
  const pos = processForm.defectItemIds.indexOf(id)
  if (pos !== -1) processForm.defectItemIds.splice(pos, 1)
  else processForm.defectItemIds.push(id)
}
function getDefectLabel(id: number) {
  const d = defects.value.find(x => x.id === id)
  return d ? `${d.code} | ${d.name}` : String(id)
}

defineExpose({ loadUnits, loadDefects, loadProcesses, loadRoutes })

onMounted(() => {
  if (props.token) {
    loadUnits()
    loadDefects()
    loadProcesses()
    loadRoutes()
    // 加载全量部门（供报工权限树）
    fetchDepartments(props.token, { code: '', name: '', page: 1, size: 999 })
      .then(res => { departments.value = res.items })
      .catch(() => {})
  }
})
</script>

<template>
      <!-- 单位管理 -->
      <section class="content" v-if="activeBasedataMenu === 'unit'">
        <div class="filters">
          <label>
            <span>名称</span>
            <input v-model="unitFilterForm.name" placeholder="请输入" />
          </label>
          <label>
            <span>备注</span>
            <input v-model="unitFilterForm.remark" placeholder="请输入备注" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button" @click="">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn" type="button" @click="openCreateUnit">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建单位
          </button>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:50px"><input type="checkbox" /></th>
                <th style="width:60px">序号</th>
                <th>名称</th>
                <th>备注</th>
                <th style="width:120px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(unit, idx) in unitFilteredList" :key="unit.id">
                <td><input type="checkbox" /></td>
                <td>{{ idx + 1 }}</td>
                <td>{{ unit.name }}</td>
                <td>{{ unit.remark || '-' }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditUnit(unit)">编辑</button>
                  <button class="action-link danger" type="button" @click="handleDeleteUnit(unit)">删除</button>
                </td>
              </tr>
              <tr v-if="unitFilteredList.length === 0">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>


      <!-- 不良品项管理 -->
      <section class="content" v-if="activeBasedataMenu === 'defect'">
        <div class="filters">
          <label>
            <span style="white-space:nowrap">不良品项编号</span>
            <input v-model="defectFilterForm.code" placeholder="请输入不良品项编号" />
          </label>
          <label>
            <span style="white-space:nowrap">不良品项名称</span>
            <input v-model="defectFilterForm.name" placeholder="请输入不良品项名称" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn" type="button" @click="openCreateDefect">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建不良品项
          </button>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:50px"><input type="checkbox" /></th>
                <th style="width:60px">序号</th>
                <th>不良品项编号</th>
                <th>不良品项名称</th>
                <th style="width:120px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(d, idx) in defectFilteredList" :key="d.id">
                <td><input type="checkbox" /></td>
                <td>{{ idx + 1 }}</td>
                <td>{{ d.code }}</td>
                <td>{{ d.name }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditDefect(d)">编辑</button>
                  <button class="action-link danger" type="button" @click="handleDeleteDefect(d)">删除</button>
                </td>
              </tr>
              <tr v-if="defectFilteredList.length === 0">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>


      <!-- 工序管理 - 列表 -->
      <section class="content" v-if="activeBasedataMenu === 'process' && processView === 'list'">
        <div class="filters">
          <label>
            <span>工序编号</span>
            <input v-model="processFilterForm.code" placeholder="请输入工序编号" />
          </label>
          <label>
            <span>工序名称</span>
            <select v-model="processFilterForm.name">
              <option value="">请选择</option>
              <option v-for="p in processes" :key="p.id" :value="p.name">{{ p.name }}</option>
            </select>
          </label>
          <label>
            <span>创建人</span>
            <select v-model="processFilterForm.creator">
              <option value="">请选择</option>
              <option value="管理员">管理员</option>
            </select>
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn create-btn" type="button" @click="openCreateProcess()">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            创建工序
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            导出
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            导入
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
            默认SOP模板
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
            字段配置
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>
            排序
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="21" y1="10" x2="3" y2="10"/><line x1="21" y1="6" x2="3" y2="6"/><line x1="21" y1="14" x2="3" y2="14"/><line x1="21" y1="18" x2="3" y2="18"/></svg>
            行高
          </button>
          <div style="margin-left:auto">
            <button class="toolbar-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              导入日志
            </button>
          </div>
        </div>
        <div class="table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:40px"><input type="checkbox" /></th>
                <th style="width:50px">序号</th>
                <th>工序编号</th>
                <th>工序名称</th>
                <th>报工权限</th>
                <th>报工数配比</th>
                <th>任务打卡</th>
                <th class="action-cell">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(p, index) in processFilteredList" :key="p.id">
                <td><input type="checkbox" /></td>
                <td>{{ index + 1 }}</td>
                <td>{{ p.code }}</td>
                <td>{{ p.name }}</td>
                <td class="perm-cell">
                  <template v-for="(tag, ti) in formatPermissionTags(p.reportPermission)" :key="ti">
                    <span
                      class="perm-cell-tag"
                      :class="{ 'perm-partial': tag.partial }"
                      @mouseenter="tag.tooltip ? showTooltip($event, tag.tooltip) : null"
                      @mouseleave="hideTooltip"
                    >{{ tag.label }}</span>
                  </template>
                </td>
                <td>{{ p.reportRatio }}</td>
                <td>{{ p.taskPunchIn ? '开启' : '关闭' }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditProcess(p)">编辑</button>
                  <button class="action-link" type="button">打印</button>
                  <button class="action-link" type="button">SOP模版</button>
                  <button class="action-link danger" type="button" @click="handleDeleteProcess(p)">删除</button>
                </td>
              </tr>
              <tr v-if="processFilteredList.length === 0">
                <td colspan="8" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>


      <!-- 工序管理 - 创建/编辑表单 -->
      <section class="content process-form-page" v-if="activeBasedataMenu === 'process' && (processView === 'create' || processView === 'edit')">
        <h2 class="process-form-title">{{ processView === 'edit' ? '编辑工序' : '创建工序' }}</h2>
        <div class="process-form">
          <!-- 工序编号 -->
          <div class="process-field">
            <label class="process-label">工序编号</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.code" placeholder="请输入，否则将自动生成" />
            </div>
          </div>
          <!-- 工序名称 -->
          <div class="process-field">
            <label class="process-label required">工序名称</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.name" placeholder="请填写" />
            </div>
          </div>
          <!-- 报工权限 -->
          <div class="process-field">
            <label class="process-label required">报工权限</label>
            <div class="process-input-wrap perm-dropdown-wrap">
              <div class="perm-tag-box" @click="showProcessPermDropdown = !showProcessPermDropdown">
                <span class="perm-tag" v-for="k in visiblePermKeys" :key="k">
                  {{ getPermLabel(k) }}
                  <span class="tag-close" @click.stop="removeProcessPermission(k)">×</span>
                </span>
                <span class="perm-placeholder" v-if="visiblePermKeys.length === 0">请选择</span>
              </div>
              <div class="perm-dropdown perm-tree-dropdown" v-show="showProcessPermDropdown">
                <!-- 搜索 -->
                <div class="perm-search-bar">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="#aaa" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                  <input v-model="permSearchText" placeholder="搜索字段" @click.stop />
                </div>
                <!-- 所有人 -->
                <div class="perm-tree-row" @click.stop="togglePermAll()">
                  <span class="perm-checkbox" :class="{ checked: isPermSelected('all') }">
                    <svg v-if="isPermSelected('all')" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="#fff" stroke-width="2"><polyline points="2,6 5,9 10,3"/></svg>
                  </span>
                  <span class="perm-tree-label">所有人</span>
                </div>
                <!-- 部门列表 -->
                <template v-for="dept in filteredPermTree" :key="dept.id">
                  <div class="perm-tree-row perm-dept-row">
                    <span class="perm-expand-icon" @click.stop="togglePermDeptExpand(dept.id)">
                      <svg :style="{ transform: isDeptExpanded(dept.id) ? 'rotate(90deg)' : 'rotate(0)' }" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2.5" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
                    </span>
                    <span class="perm-checkbox"
                      :class="{ checked: isDeptAllSelected(dept), partial: isDeptPartialSelected(dept) }"
                      @click.stop="togglePermDept(dept)">
                      <svg v-if="isDeptAllSelected(dept)" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="#fff" stroke-width="2"><polyline points="2,6 5,9 10,3"/></svg>
                      <span v-else-if="isDeptPartialSelected(dept)" style="width:8px;height:2px;background:#fff;display:block;margin:auto;"></span>
                    </span>
                    <span class="perm-tree-label" @click.stop="togglePermDeptExpand(dept.id)">
                      {{ dept.name }} ({{ dept.employees.length }}人)
                    </span>
                  </div>
                  <!-- 员工列表 -->
                  <template v-if="isDeptExpanded(dept.id)">
                    <div class="perm-tree-row perm-emp-row" v-for="emp in dept.employees" :key="emp.id"
                      @click.stop="togglePermEmployee(dept, emp)">
                      <span style="width:20px"></span>
                      <span class="perm-checkbox" :class="{ checked: isPermSelected(emp.id) }">
                        <svg v-if="isPermSelected(emp.id)" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="#fff" stroke-width="2"><polyline points="2,6 5,9 10,3"/></svg>
                      </span>
                      <span class="perm-tree-label">{{ emp.name }}</span>
                    </div>
                  </template>
                </template>
              </div>
            </div>
          </div>
          <!-- 计划数默认值 -->
          <div class="process-field">
            <label class="process-label required">计划数默认值</label>
            <div class="process-input-wrap">
              <div class="formula-box">
                <button class="formula-edit-btn" type="button">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  修改
                </button>
                <div class="formula-text">
                  <span class="formula-fx">fx</span>
                  <span>$(任务.工单计划数) * $(任务.报工效配比)</span>
                </div>
              </div>
            </div>
          </div>
          <!-- 报工数配比 -->
          <div class="process-field">
            <label class="process-label required">报工数配比</label>
            <div class="process-input-wrap">
              <input class="process-input" type="number" v-model.number="processForm.reportRatio" min="1" />
            </div>
          </div>
          <!-- 不良品项列表 -->
          <div class="process-field">
            <label class="process-label">不良品项列表</label>
            <div class="process-input-wrap defect-dropdown-wrap" style="position:relative">
              <div class="perm-tag-box" @click="showProcessDefectDropdown = !showProcessDefectDropdown">
                <span class="perm-tag" v-for="id in processForm.defectItemIds" :key="id">
                  {{ getDefectLabel(id) }}
                  <span class="tag-close" @click.stop="toggleProcessDefect(id)">×</span>
                </span>
                <span class="perm-placeholder" v-if="processForm.defectItemIds.length === 0">请选择不良品项</span>
              </div>
              <div class="perm-dropdown" v-show="showProcessDefectDropdown">
                <div class="perm-option" v-for="d in defects" :key="d.id"
                  :class="{ active: processForm.defectItemIds.includes(d.id) }"
                  @click="toggleProcessDefect(d.id)">
                  {{ d.code }} | {{ d.name }}
                  <span v-if="processForm.defectItemIds.includes(d.id)" style="margin-left:auto;color:var(--accent)">✓</span>
                </div>
                <div v-if="defects.length === 0" style="padding:12px;color:#aaa;text-align:center">暂无不良品项</div>
              </div>
            </div>
          </div>
          <!-- 任务打卡 -->
          <div class="process-field">
            <label class="process-label">任务打卡</label>
            <div class="process-input-wrap">
              <div class="radio-group">
                <label class="radio-label">
                  <input type="radio" :value="false" v-model="processForm.taskPunchIn" />
                  <span>关闭</span>
                </label>
                <label class="radio-label">
                  <input type="radio" :value="true" v-model="processForm.taskPunchIn" />
                  <span>开启</span>
                </label>
              </div>
            </div>
          </div>
          <!-- 良品数量 -->
          <div class="process-field">
            <label class="process-label">良品数量</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.goodQuantity" placeholder="请填写" />
            </div>
          </div>
          <!-- 工序名称机械 -->
          <div class="process-field">
            <label class="process-label">工序名称机械</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.machineName" placeholder="请填写" />
            </div>
          </div>
          <!-- 投料数 -->
          <div class="process-field">
            <label class="process-label">投料数</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.feedCount" placeholder="请填写" />
            </div>
          </div>
          <!-- 对应机台 -->
          <div class="process-field">
            <label class="process-label">对应机台</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.correspondingMachine" placeholder="请填写" />
            </div>
          </div>
          <!-- 要求 -->
          <div class="process-field">
            <label class="process-label">要求</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.requirements" placeholder="请填写" />
            </div>
          </div>
          <!-- 工序采集数据 -->
          <div class="process-field process-field-collect">
            <label class="process-label">工序采集数据</label>
            <div class="process-input-wrap">
              <div class="collect-wrap">
                <table class="collect-table">
                  <thead>
                    <tr>
                      <th>类型</th>
                      <th>名称</th>
                      <th style="width:40px"></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, idx) in processForm.collectData" :key="idx">
                      <td><input class="collect-input" v-model="row.type" placeholder="请填写" /></td>
                      <td><input class="collect-input" v-model="row.name" placeholder="请填写" /></td>
                      <td><button type="button" class="collect-del" @click="removeCollectDataRow(idx)">×</button></td>
                    </tr>
                  </tbody>
                </table>
                <div class="collect-empty" v-if="processForm.collectData.length === 0">
                  <svg viewBox="0 0 80 60" width="72" height="54" fill="none">
                    <rect x="10" y="8" width="60" height="44" rx="4" fill="#f2f4f3" stroke="#dde3e0" stroke-width="1.5"/>
                    <rect x="18" y="18" width="44" height="6" rx="2" fill="#e2e8e5"/>
                    <rect x="18" y="30" width="32" height="5" rx="2" fill="#eaeeec"/>
                    <rect x="18" y="41" width="20" height="4" rx="2" fill="#eaeeec"/>
                  </svg>
                  <span>暂无采集数据</span>
                </div>
                <button type="button" class="add-collect-row" @click="addCollectDataRow()"
                  :disabled="processForm.collectData.length >= 50">
                  <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none"><circle cx="12" cy="12" r="9"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                  添加一行 最多支持添加50个
                </button>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部按钮 -->
        <div class="process-form-footer">
          <button class="btn-cancel" type="button" @click="processView = 'list'">取消</button>
          <button class="btn-save" type="button" @click="handleSaveProcess()">保存</button>
        </div>
      </section>


      <!-- 工艺路线 - 列表 -->
      <section class="content" v-if="activeBasedataMenu === 'route' && routeView === 'list'">
        <div class="filters">
          <label>
            <span>工艺路线编号</span>
            <input v-model="routeFilterForm.code" placeholder="请输入工艺路线编号" />
          </label>
          <label>
            <span>工艺路线名称</span>
            <input v-model="routeFilterForm.name" placeholder="请输入工艺路线名称" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn create-btn" type="button" @click="openCreateRoute()">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建工艺路线
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            导出
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            导入
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="9" y1="3" x2="9" y2="21"/></svg>
            字段配置
          </button>
          <button class="toolbar-btn" style="margin-left:auto" type="button">导入日志</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:40px"><input type="checkbox" /></th>
              <th>工艺路线编号</th>
              <th>工艺路线名称</th>
              <th>工序/工艺路线列表</th>
              <th>创建人</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, idx) in routeFilteredList" :key="r.id">
              <td style="text-align:center">{{ idx + 1 }}</td>
              <td>
                <a href="#" style="color:var(--accent);text-decoration:none" @click.prevent="openEditRoute(r)">{{ r.code }}</a>
              </td>
              <td>{{ r.name }}</td>
              <td style="color:#586360">
                {{ r.steps.map(s => getProcessName(s.processId)).filter(Boolean).join('、') || '-' }}
              </td>
              <td>{{ r.creator }}</td>
              <td>{{ r.createdAt }}</td>
              <td style="white-space:nowrap">
                <button class="link-btn" type="button" @click="openEditRoute(r)" style="margin-right:8px">编辑</button>
                <button class="link-btn" type="button" style="margin-right:8px">复制</button>
                <button class="link-btn danger" type="button" @click="confirmDeleteRoute(r)">删除</button>
              </td>
            </tr>
            <tr v-if="routeFilteredList.length === 0">
              <td colspan="7" style="text-align:center;padding:48px;color:#bbb">
                <svg viewBox="0 0 64 64" width="40" height="40" style="display:block;margin:0 auto 10px" fill="none"><rect x="8" y="12" width="48" height="40" rx="4" fill="#f2f4f3" stroke="#dde3e0" stroke-width="1.5"/><line x1="18" y1="24" x2="46" y2="24" stroke="#dde3e0" stroke-width="2"/><line x1="18" y1="32" x2="38" y2="32" stroke="#dde3e0" stroke-width="2"/></svg>
                暂无工艺路线
              </td>
            </tr>
          </tbody>
        </table>
      </section>


      <!-- 工艺路线 - 创建/编辑 -->
      <section class="process-form-page" v-if="activeBasedataMenu === 'route' && routeView !== 'list'">
        <div class="process-form-scroll">
          <h2 class="process-form-title">{{ routeView === 'create' ? '创建工艺路线' : '编辑工艺路线' }}</h2>
          <!-- 基本信息 -->
          <div class="process-field">
            <label class="process-label">工艺路线编号</label>
            <div class="process-input-wrap" style="max-width:50%">
              <input class="process-input" v-model="routeForm.code" placeholder="请输入，忽略将自动生成" />
            </div>
          </div>
          <div class="process-field">
            <label class="process-label required">工艺路线名称</label>
            <div class="process-input-wrap" style="max-width:50%">
              <input class="process-input" v-model="routeForm.name" placeholder="请填写" />
            </div>
          </div>

          <!-- 工序列表 -->
          <div class="route-steps-section">
            <div class="route-steps-header">
              <span class="route-steps-title">工序列表</span>
              <div style="display:flex;gap:8px;margin-top:8px">
                <button type="button" class="primary-btn" style="height:32px;padding:0 14px;font-size:13px" @click="addRouteStep()">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2.5" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                  添加一行
                </button>
                <button type="button" class="toolbar-btn" style="height:32px;font-size:13px">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="9" y1="3" x2="9" y2="21"/></svg>
                  字段配置
                </button>
              </div>
            </div>

            <!-- 工序表格 -->
            <div class="collect-wrap route-steps-wrap">
              <table class="collect-table route-steps-table">
                <thead>
                  <tr>
                    <th style="width:36px"></th>
                    <th style="width:36px"><input type="checkbox" /></th>
                    <th>类型</th>
                    <th>工序/工艺路线 <span style="color:#e5534b">*</span></th>
                    <th style="width:120px">报工数配比 <span style="color:#e5534b">*</span></th>
                    <th style="width:120px">备注</th>
                    <th style="width:60px">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(step, idx) in routeForm.steps" :key="step.id"
                    :class="{ 'drag-over': routeDragOverIdx === idx }"
                    draggable="true"
                    @dragstart="onRouteDragStart(idx, $event)"
                    @dragover="onRouteDragOver(idx, $event)"
                    @drop="onRouteDrop(idx)"
                    @dragend="onRouteDragEnd()"
                  >
                    <!-- 拖拽手柄 -->
                    <td style="text-align:center;cursor:grab;color:#bbb;font-size:16px;letter-spacing:1px">&#8942;&#8942;</td>
                    <td style="text-align:center"><input type="checkbox" /></td>
                    <!-- 类型 -->
                    <td>
                      <div class="radio-group" style="gap:8px">
                        <label class="radio-label">
                          <input type="radio" value="process" v-model="step.type" @change="step.processId = null" />
                          <span>工序</span>
                        </label>
                        <label class="radio-label">
                          <input type="radio" value="route" v-model="step.type" @change="step.processId = null" />
                          <span>工艺路线</span>
                        </label>
                      </div>
                    </td>
                    <!-- 工序/工艺路线下拉选择 -->
                    <td style="position:relative">
                      <div class="route-step-select" @click.stop="openRouteStepDropdown(step.id)">
                        <span v-if="step.processId" class="route-step-selected">{{ getStepItemName(step) }}</span>
                        <span v-else class="route-step-placeholder">请选择</span>
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="#aaa" stroke-width="2" fill="none"
                          :style="{ transform: routeStepDropdownOpen[step.id] ? 'rotate(180deg)' : 'none' }">
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                      </div>
                      <div class="route-step-dropdown" v-show="routeStepDropdownOpen[step.id]">
                        <div class="perm-search-bar">
                          <svg viewBox="0 0 24 24" width="13" height="13" stroke="#aaa" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                          <input v-model="routeStepSearchText[step.id]" placeholder="搜索字段" @click.stop />
                        </div>
                        <div v-if="filteredItemsForStep(step.id).length === 0" style="padding:12px;color:#aaa;text-align:center;font-size:13px">暂无数据</div>
                        <div
                          class="perm-option"
                          v-for="item in filteredItemsForStep(step.id)" :key="item.id"
                          :class="{ active: step.processId === item.id }"
                          @click.stop="selectRouteStepItem(step, item.id)">
                          {{ item.name }}
                          <span v-if="step.processId === item.id" style="margin-left:auto;color:var(--accent)">✓</span>
                        </div>
                      </div>
                    </td>
                    <!-- 报工数配比 -->
                    <td>
                      <input class="collect-input" type="number" v-model.number="step.reportRatio" min="1" style="width:100%" />
                    </td>
                    <!-- 备注 -->
                    <td>
                      <input class="collect-input" v-model="step.remark" placeholder="请填写" style="width:100%" />
                    </td>
                    <!-- 操作 -->
                    <td style="text-align:center">
                      <button type="button" class="link-btn danger" @click="removeRouteStep(idx)">删除</button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="collect-empty" v-if="routeForm.steps.length === 0">
                <svg viewBox="0 0 80 60" width="72" height="54" fill="none">
                  <rect x="10" y="8" width="60" height="44" rx="4" fill="#f2f4f3" stroke="#dde3e0" stroke-width="1.5"/>
                  <rect x="18" y="18" width="44" height="6" rx="2" fill="#e2e8e5"/>
                  <rect x="18" y="30" width="32" height="5" rx="2" fill="#eaeeec"/>
                  <rect x="18" y="41" width="20" height="4" rx="2" fill="#eaeeec"/>
                </svg>
                <span>暂无数据</span>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部按钮 -->
        <div class="process-form-footer">
          <button class="btn-cancel" type="button" @click="routeView = 'list'">取消</button>
          <button class="btn-save" type="button" @click="handleSaveRoute()">保存</button>
        </div>
      </section>

  <!-- 单位弹窗 -->
  <div v-if="showUnitModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>{{ editingUnit ? '编辑单位' : '创建单位' }}</h2>
        <button class="close-btn" type="button" @click="showUnitModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveUnit">
        <label>
          <span><em>*</em> 名称</span>
          <input v-model="unitForm.name" placeholder="请输入单位名称" required />
        </label>
        <label>
          <span>备注</span>
          <input v-model="unitForm.remark" placeholder="请输入备注" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showUnitModal = false">取消</button>
          <button class="primary-btn wide" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 不良品项弹窗 -->
  <div v-if="showDefectModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>{{ editingDefect ? '编辑不良品项' : '创建不良品项' }}</h2>
        <button class="close-btn" type="button" @click="showDefectModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveDefect">
        <label>
          <span style="white-space:nowrap;min-width:90px"><em>*</em> 不良品项名称</span>
          <input v-model="defectForm.name" placeholder="请输入" required />
        </label>
        <label>
          <span style="white-space:nowrap;min-width:90px">不良品项编号</span>
          <input v-model="defectForm.code" placeholder="请输入，您略将自动生成" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showDefectModal = false">取消</button>
          <button class="primary-btn wide" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>
</template>
