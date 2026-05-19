/**
 * 提取组件文件生成脚本 v2
 * 从 App.vue 中读取指定行范围，生成独立组件文件
 * 内置变量替换，确保子组件正确引用 props 而非 App.vue 局部变量
 */
const fs = require('fs')
const path = require('path')

const appPath = path.join(__dirname, 'frontend/src/App.vue')
const compDir = path.join(__dirname, 'frontend/src/components')
const lines = fs.readFileSync(appPath, 'utf-8').split(/\r?\n/)

function getLines(start1, end1) {
  return lines.slice(start1 - 1, end1).join('\r\n')
}

/**
 * 对 BaseDataPage 的代码做变量替换：
 * - token.value → props.token
 * - confirmState.xxx → emit('confirm', ...)  (这个比较复杂，先注释掉 confirmState)
 * - 保留 departments / users 作为 props 引用
 */
function fixBaseDataScript(code) {
  // token.value → props.token（但不要匹配 props.token.value）
  code = code.replace(/([^a-zA-Z0-9_.])token\.value/g, '$1props.token')
  // 开头的 token.value
  code = code.replace(/^token\.value/gm, 'props.token')

  // 修复 confirmDeleteRoute：把 confirmState 改为 emit
  // 原始代码模式：
  //   confirmState.title = '...'
  //   confirmState.message = `...`
  //   confirmState.action = async () => { ... }
  //   confirmState.show = true
  // 替换方案：
  code = code.replace(
    /function confirmDeleteRoute\(r: RouteItem\) \{\r?\n\s*confirmState\.title = '删除确认'\r?\n\s*confirmState\.message = `是否确要删除工艺路线「\$\{r\.name\}」？`\r?\n\s*confirmState\.action = (async \(\) => \{[\s\S]*?\})\r?\n\s*confirmState\.show = true\r?\n\}/,
    (match, actionBody) => {
      return `function confirmDeleteRoute(r: RouteItem) {
  emit('confirm', {
    title: '删除确认',
    message: \`是否确要删除工艺路线「\${r.name}」？\`,
    action: ${actionBody}
  })
}`
    }
  )

  // users.value → props.users（在报工权限树的 filter 中用到）
  // 但要小心不匹配 allUsersList.value
  code = code.replace(/([^a-zA-Z0-9_.])users\.value/g, '$1props.users')
  code = code.replace(/^users\.value/gm, 'props.users')

  // departments.value → 由于 BaseDataPage 有自己的本地 departments (用于报工权限树的完整列表)
  // 这里保留本地的 departments 变量不动

  return code
}

function fixUserMgmtScript(code) {
  // token.value → props.token
  code = code.replace(/([^a-zA-Z0-9_.])token\.value/g, '$1props.token')
  code = code.replace(/^token\.value/gm, 'props.token')

  // users.value → props.users
  code = code.replace(/([^a-zA-Z0-9_.])users\.value/g, '$1props.users')
  code = code.replace(/^users\.value/gm, 'props.users')

  // currentUser.value → props.currentUser
  // 但 syncCurrentUser 里要做 emit 而非直接赋值
  code = code.replace(/currentUser\.value\?\.id/g, 'props.currentUser?.id')
  code = code.replace(/currentUser\.value = updated/, "emit('update-current-user', updated)")
  code = code.replace(/currentUser\.value/g, 'props.currentUser')

  // loadUsers() → emit('reload-users')
  code = code.replace(/await loadUsers\(\)/g, "emit('reload-users')")
  // standalone loadUsers() calls (not awaited)
  code = code.replace(/([^a-zA-Z0-9_])loadUsers\(\)/g, "$1emit('reload-users')")

  // confirmState.title = ... confirmState.message = ... confirmState.action = ... confirmState.show = true
  // 使用简单的多行匹配
  code = code.replace(
    /confirmState\.title = (.*)\r?\n\s*confirmState\.message = (.*)\r?\n\s*confirmState\.action = (.*)\r?\n\s*confirmState\.show = true/g,
    (match, title, message, action) => {
      return `emit('confirm', { title: ${title}, message: ${message}, action: ${action} })`
    }
  )

  // confirmState.show = false
  code = code.replace(/confirmState\.show = false\r?\n/g, '')

  // actionMessage.value = message → emit('notice', message)
  code = code.replace(/actionMessage\.value = message/g, "emit('notice', message)")
  code = code.replace(/actionMessage\.value = (['"`])/g, "emit('notice', $1")

  return code
}

function fixUserMgmtTemplate(code) {
  // 移除 v-show 条件（因为父组件通过 v-show 控制这个组件的显示）
  code = code.replace(/v-show="activeMainMenu === 'sysconfig' && activeConfigMenu === 'user'"/g, '')

  // loadUsers → $emit('reload-users') or just remove the ref
  code = code.replace(/@click="loadUsers\(\)"/g, '@click="$emit(\'reload-users\')"')
  code = code.replace(/@click="loadUsers"/g, '@click="$emit(\'reload-users\')"')

  // formatDateTime → 需要在子组件内定义
  return code
}

function fixDeptMgmtTemplate(code) {
  code = code.replace(/v-show="activeMainMenu === 'sysconfig' && activeConfigMenu === 'department'"/g, '')
  return code
}

function fixBaseDataTemplate(code) {
  // 移除 activeMainMenu 条件，保留 activeBasedataMenu 条件
  code = code.replace(/v-show="activeMainMenu === 'basedata' && activeBasedataMenu/g, 'v-show="activeBasedataMenu')
  return code
}

// =====================================================
// 1. BaseDataPage.vue - 单位+不良品+工序+工艺路线
// =====================================================
console.log('Generating BaseDataPage.vue...')

// Script: lines 306-1021 (单位管理到 getDefectLabel)
// 但注意 lines 698-703 声明了 departments/departmentTotal/departmentsLoading/departmentFilterForm
// 这些在 BaseDataPage 里实际上还需要，因为报工权限树用它
let baseDataScript = getLines(309, 1024)
baseDataScript = fixBaseDataScript(baseDataScript)

// Template sections (all basedata-related content sections + modals)
let baseDataTemplate = [
  getLines(2221, 2274),   // 单位管理 section
  getLines(2275, 2328),   // 不良品项 section
  getLines(2329, 2439),   // 工序列表 section
  getLines(2440, 2654),   // 工序表单 section (含报工权限树)
  getLines(2655, 2731),   // 工艺路线列表 section
  getLines(2732, 2864),   // 工艺路线表单 section
  getLines(3609, 3631),   // 单位弹窗
  getLines(3633, 3655),   // 不良品项弹窗
].join('\r\n\r\n')
baseDataTemplate = fixBaseDataTemplate(baseDataTemplate)

const baseDataPage = `<script setup lang="ts">
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

${baseDataScript}

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
${baseDataTemplate}
</template>
`

fs.writeFileSync(path.join(compDir, 'BaseDataPage.vue'), baseDataPage, 'utf-8')
console.log('  Written! Size:', baseDataPage.length, 'bytes')

// =====================================================
// 2. UserManagementPage.vue
// =====================================================
console.log('Generating UserManagementPage.vue...')

// Script sections:
// - filterForm + createForm + editForm: 1099-1153
// - readStoredUser is NOT needed (App.vue keeps it)
// - User CRUD functions: 1446-1819
let userScript1 = getLines(1099, 1156)
let userScript2 = getLines(1449, 1822)
userScript1 = fixUserMgmtScript(userScript1)
userScript2 = fixUserMgmtScript(userScript2)

// Template:
let userTemplate = [
  getLines(2881, 3011),   // 用户管理 section (含分页)
  getLines(3345, 3361),   // 导入日志弹窗
  getLines(3363, 3411),   // 新增用户弹窗
  getLines(3413, 3457),   // 编辑用户弹窗
  getLines(3459, 3491),   // 重置密码弹窗
  getLines(3493, 3514),   // 权限配置弹窗
  getLines(3516, 3534),   // 新建用户成功弹窗
].join('\r\n\r\n')
userTemplate = fixUserMgmtTemplate(userTemplate)

const userMgmtPage = `<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  createUser, exportUsersCsv, fetchUsers, importUsersCsv,
  resetUserPassword, unbindUserPhone, updateUser, updateUserPermission, updateUserStatus,
  type CreateUserPayload, type Role, type UpdateUserPayload, type UserInfo, type DepartmentInfo
} from '../api'

const props = defineProps<{
  token: string
  users: UserInfo[]
  departments: DepartmentInfo[]
  currentUser: UserInfo | null
}>()

const emit = defineEmits<{
  (e: 'error', msg: string): void
  (e: 'notice', msg: string): void
  (e: 'confirm', payload: { title: string; message: string; action: () => void }): void
  (e: 'reload-users'): void
  (e: 'update-current-user', user: UserInfo): void
}>()

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showPermissionModal = ref(false)
const selectedUserIds = ref<number[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const importLogs = ref<{ time: string; fileName: string; total: number; success: number; fail: number; errors: string[] }[]>([])
const importResult = ref<{ total: number; success: number; fail: number; errors: string[] } | null>(null)
const showImportLogModal = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(10)
const successUser = ref<UserInfo | null>(null)
const successUserPassword = ref('')
const editingUser = ref<UserInfo | null>(null)
const permissionUser = ref<UserInfo | null>(null)
const permissionRole = ref<Role>('ADMIN')
const showResetPasswordModal = ref(false)
const resetPasswordUser = ref<UserInfo | null>(null)
const resetPasswordForm = reactive({ password: '', confirmPassword: '' })
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')
watch(errorMessage, (v) => { if (v) emit('error', v) })

const formatDateTime = (val: string) => {
  if (!val) return '-'
  const d = new Date(val)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return \`\${year}-\${month}-\${day} \${hours}:\${minutes}:\${seconds}\`
}

${userScript1}

${userScript2}
</script>

<template>
${userTemplate}
</template>
`

fs.writeFileSync(path.join(compDir, 'UserManagementPage.vue'), userMgmtPage, 'utf-8')
console.log('  Written! Size:', userMgmtPage.length, 'bytes')

// =====================================================
// 3. DepartmentManagementPage.vue
// =====================================================
console.log('Generating DepartmentManagementPage.vue...')

// DepartmentManagementPage 独立性较好，之前的脚本生成就是对的
// 直接保留现有生成逻辑
let deptTemplate = [
  getLines(3026, 3095),   // 部门管理 section (含分页)
  getLines(3563, 3608),   // 部门弹窗
].join('\r\n\r\n')
deptTemplate = fixDeptMgmtTemplate(deptTemplate)

const deptMgmtPage = `<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  fetchDepartments, createDepartment, updateDepartment, deleteDepartment,
  fetchUsers,
  type DepartmentInfo, type CreateDepartmentPayload, type UpdateDepartmentPayload, type UserInfo
} from '../api'

const props = defineProps<{
  token: string
  departments: DepartmentInfo[]
  departmentTotal: number
}>()

const emit = defineEmits<{
  (e: 'error', msg: string): void
  (e: 'notice', msg: string): void
  (e: 'confirm', payload: { title: string; message: string; action: () => void }): void
  (e: 'reload-departments'): void
}>()

const errorMessage = ref('')
const actionMessage = ref('')
watch(errorMessage, (v) => { if (v) emit('error', v) })
watch(actionMessage, (v) => { if (v) emit('notice', v) })

const loading = ref(false)
const departmentsLoading = ref(false)
const departmentFilterForm = reactive({ code: '', name: '', page: 1, size: 10 })
const showDepartmentModal = ref(false)
const editingDepartment = ref<DepartmentInfo | null>(null)
const departmentForm = reactive<CreateDepartmentPayload>({ code: '', name: '', userIds: [] })
const allUsersList = ref<UserInfo[]>([])
const showUserSelectDropdown = ref(false)
const userSelectTriggerRef = ref<HTMLElement | null>(null)
const dropdownStyle = ref<{ top: string; left: string; width: string }>({
  top: '0px', left: '0px', width: '200px'
})

const formatDateTime = (val: string) => {
  if (!val) return '-'
  const d = new Date(val)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return \`\${year}-\${month}-\${day} \${hours}:\${minutes}:\${seconds}\`
}

function openUserSelectDropdown() {
  if (userSelectTriggerRef.value) {
    const rect = userSelectTriggerRef.value.getBoundingClientRect()
    dropdownStyle.value = {
      top: rect.bottom + 4 + 'px',
      left: rect.left + 'px',
      width: rect.width + 'px'
    }
  }
  showUserSelectDropdown.value = !showUserSelectDropdown.value
}

const selectedUserNames = computed(() => {
  if (!departmentForm.userIds || departmentForm.userIds.length === 0) return '请选择部门人员'
  return allUsersList.value
    .filter(u => departmentForm.userIds!.includes(u.id))
    .map(u => u.name)
    .join('、')
})

async function loadDepartments() {
  if (!props.token) return
  departmentsLoading.value = true
  try {
    const res = await fetchDepartments(props.token, departmentFilterForm)
    // Data is managed by parent, just emit reload
    emit('reload-departments')
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '部门加载失败')
  } finally {
    departmentsLoading.value = false
  }
}

async function loadAllUsersForDepartment() {
  if (!props.token) return
  try {
    const allUsers = await fetchUsers(props.token)
    allUsersList.value = allUsers
  } catch (error) {
    console.error('Failed to load all users', error)
  }
}

function handleDepartmentPageChange(dir: number) {
  const newPage = departmentFilterForm.page + dir
  if (newPage < 1) return
  const maxPage = Math.ceil(props.departmentTotal / departmentFilterForm.size) || 1
  if (newPage > maxPage) return
  departmentFilterForm.page = newPage
  loadDepartments()
}

function resetDepartmentFilter() {
  departmentFilterForm.code = ''
  departmentFilterForm.name = ''
  departmentFilterForm.page = 1
  loadDepartments()
}

function openCreateDepartmentModal() {
  editingDepartment.value = null
  departmentForm.code = ''
  departmentForm.name = ''
  departmentForm.userIds = []
  showDepartmentModal.value = true
  loadAllUsersForDepartment()
}

function openEditDepartmentModal(dept: DepartmentInfo) {
  editingDepartment.value = dept
  departmentForm.code = dept.code
  departmentForm.name = dept.name
  loadAllUsersForDepartment().then(() => {
    departmentForm.userIds = allUsersList.value
      .filter(u => u.departmentId === dept.id)
      .map(u => u.id)
    showDepartmentModal.value = true
  })
}

async function handleSaveDepartment() {
  if (!props.token) return
  loading.value = true
  try {
    if (editingDepartment.value) {
      await updateDepartment(props.token, editingDepartment.value.id, departmentForm)
    } else {
      await createDepartment(props.token, departmentForm)
    }
    showDepartmentModal.value = false
    emit('reload-departments')
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '保存失败')
  } finally {
    loading.value = false
  }
}

function confirmDeleteDepartment(dept: DepartmentInfo) {
  emit('confirm', {
    title: '删除确认',
    message: \`是否确要删除「\${dept.name}」？其下用户的部门信息将被清空。\`,
    action: async () => {
      if (!props.token) return
      loading.value = true
      try {
        await deleteDepartment(props.token, dept.id)
        emit('reload-departments')
      } catch (error) {
        emit('error', error instanceof Error ? error.message : '删除失败')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<template>
${deptTemplate}
</template>
`

fs.writeFileSync(path.join(compDir, 'DepartmentManagementPage.vue'), deptMgmtPage, 'utf-8')
console.log('  Written! Size:', deptMgmtPage.length, 'bytes')

console.log('\nAll component files generated successfully!')
console.log('Next step: run vue-tsc to verify')
