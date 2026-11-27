<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { formatUiDate, t, uiLocale } from '../i18n'
import { escapePrintHtml as escapeHtml, printLabel } from '../utils/printHtml'
import { computed, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, useId, watch } from 'vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useDialogFocus } from '../composables/useDialogFocus'
import { parseCsvRows } from '../utils/csv'
import { downloadXlsx } from '../utils/spreadsheet'
import {
  createUser, exportUsersCsv, fetchUsersExportBlob, fetchUsers, importUsersCsv,
  resetUserPassword, unbindUserPhone, updateUser, updateUserPermission, updateUserStatus,
  type CreateUserPayload, type Role, type UpdateUserPayload, type UserInfo, type DepartmentInfo,
  fetchSystemImportLogs, createSystemImportLog
} from '../api'
import TableConfigButton from './TableConfigButton.vue'
import { workspaceDialogMotion } from '../workspaceDialogMotion'

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
const importLogs = ref<any[]>([])
const importLogsLoading = ref(false)
const importLogsError = ref('')
const importLogsPage = ref(1)
const importLogsTotal = ref(0)
const importLogsPageSize = 20
const importLogsPages = computed(() => Math.max(1, Math.ceil(importLogsTotal.value / importLogsPageSize)))
let attemptedImportLogsPage = 1
const beginImportLogs = useLatestRequest()
let sessionGeneration = 0
let exportGeneration = 0, exportActive = true

function captureSession() {
  const token = props.token
  const generation = sessionGeneration
  return { token, current: () => props.token === token && sessionGeneration === generation }
}
function captureExportSession() {
  const session = captureSession(), generation = exportGeneration
  return { token: session.token, current: () => exportActive && generation === exportGeneration && session.current() }
}

async function refreshImportLogs(targetPage = importLogsPage.value) {
  const session = captureSession()
  const latest = beginImportLogs()
  const current = () => session.current() && latest()
  if (!session.token) return
  attemptedImportLogsPage = targetPage
  importLogsLoading.value = true
  importLogsError.value = ''
  try {
    const res = await fetchSystemImportLogs(session.token, 'USER', targetPage, importLogsPageSize)
    if (!current()) return
    importLogs.value = res.items.map(item => ({
      time: item.createdAt,
      fileName: item.fileName,
      total: item.totalCount,
      success: item.successCount,
      fail: item.failCount,
      errors: item.errors ? item.errors.split('\n') : []
    }))
    importLogsPage.value = targetPage
    importLogsTotal.value = res.total
  } catch (error) {
    if (current()) importLogsError.value = error instanceof Error ? error.message : '导入日志加载失败'
  } finally {
    if (current()) importLogsLoading.value = false
  }
}
const importResult = ref<{ total: number; success: number; fail: number; errors: string[] } | null>(null)
const showImportLogModal = ref(false)
const currentPage = ref(1)
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
const importing = ref(false)
const exporting = ref(false)
const exportError = ref('')
onActivated(() => { exportActive = true })
onDeactivated(() => { exportActive = false; exportGeneration++; exporting.value = false; exportError.value = '' })
const writing = computed(() => loading.value || importing.value)
const modalTitleId = useId()
const importLogDialog = ref<HTMLElement | null>(null)
// The new-account form and its confirmation are one logical dialog session.
const userCreateFlowDialog = ref<HTMLElement | null>(null)
const userEditDialog = ref<HTMLElement | null>(null)
const passwordDialog = ref<HTMLElement | null>(null)
const permissionDialog = ref<HTMLElement | null>(null)

function closeImportLogDialog() { showImportLogModal.value = false }
function closeUserCreateDialog() { if (!writing.value) showCreateModal.value = false }
function closeUserEditDialog() { if (!writing.value) showEditModal.value = false }
function closePasswordDialog() {
  if (writing.value) return
  showResetPasswordModal.value = false
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
}
function closePermissionDialog() { if (!writing.value) showPermissionModal.value = false }
function closeSuccessDialog() {
  successUser.value = null
  successUserPassword.value = ''
}

useDialogFocus(importLogDialog, () => showImportLogModal.value, closeImportLogDialog)
useDialogFocus(userCreateFlowDialog, () => showCreateModal.value || Boolean(successUser.value), () => {
  if (successUser.value) closeSuccessDialog()
  else closeUserCreateDialog()
})
watch(userCreateFlowDialog, dialog => {
  if (successUser.value && dialog?.isConnected) dialog.querySelector<HTMLElement>('.primary-btn')?.focus({ preventScroll: true })
}, { flush: 'post' })
useDialogFocus(userEditDialog, () => showEditModal.value, closeUserEditDialog)
useDialogFocus(passwordDialog, () => showResetPasswordModal.value, closePasswordDialog)
useDialogFocus(permissionDialog, () => showPermissionModal.value, closePermissionDialog)
const errorMessage = ref('')
watch(errorMessage, (v) => { if (v) emit('error', v) })

async function runUserMutation<T>(request: (token: string) => Promise<T>, done: (result: T) => void, fallback: string) {
  if (!props.token || writing.value) return
  const session = captureSession()
  errorMessage.value = ''
  loading.value = true
  try {
    const result = await request(session.token)
    if (session.current()) done(result)
  } catch (error) {
    if (session.current()) errorMessage.value = error instanceof Error ? error.message : fallback
  } finally {
    if (session.current()) loading.value = false
  }
}

const formatDateTime = (val: string) => {
  if (!val) return '-'
  const d = new Date(val)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const filterForm = reactive({
  name: '',
  status: '启用中',
  account: '',
  role: '全部',
  department: '',
  virtual: '全部'
})

const createForm = reactive<CreateUserPayload>({
  username: '',
  phone: '',
  name: '',
  password: '',
  role: 'ADMIN',
  departmentId: undefined,
  virtualUser: false
})

const editForm = reactive<UpdateUserPayload>({
  username: '',
  phone: '',
  name: '',
  role: 'ADMIN',
  departmentId: undefined,
  virtualUser: false
})

const isLoggedIn = computed(() => Boolean(props.token && props.currentUser))
const filteredUsers = computed(() => {
  return props.users.filter((item) => {
    const matchName = !filterForm.name || item.name.includes(filterForm.name)
    const matchAccount = !filterForm.account || item.username.includes(filterForm.account)
    const matchRole = filterForm.role === '全部' || item.roleLabel === filterForm.role
    const matchDepartment =
      !filterForm.department || filterForm.department === '全部' || item.departmentName === filterForm.department
    const matchStatus =
      filterForm.status === '全部' ||
      (filterForm.status === '全部' ? true : filterForm.status === '启用中' ? item.enabled : !item.enabled)
    const matchVirtual =
      filterForm.virtual === '全部' ||
      (filterForm.virtual === '全部' ? true : filterForm.virtual === '是' ? item.virtualUser : !item.virtualUser)
    return matchName && matchAccount && matchRole && matchDepartment && matchStatus && matchVirtual
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize.value)))
watch(pageSize, () => { currentPage.value = 1 })

const paginatedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredUsers.value.slice(start, end)
})

watch(filteredUsers, () => {
  currentPage.value = 1
  const available = new Set(filteredUsers.value.map(user => user.id))
  selectedUserIds.value = selectedUserIds.value.filter(id => available.has(id))
})

const isAllSelected = computed({
  get() {
    return filteredUsers.value.length > 0 && filteredUsers.value.every(user => selectedUserIds.value.includes(user.id))
  },
  set(val) {
    if (val) {
      selectedUserIds.value = filteredUsers.value.map(u => u.id)
    } else {
      selectedUserIds.value = []
    }
  }
})

function resetCreateForm() {
  createForm.username = ''
  createForm.phone = ''
  createForm.name = ''
  createForm.password = ''
  createForm.role = 'ADMIN'
  createForm.departmentId = undefined
  createForm.virtualUser = false
}

function openCreateUser() {
  if (writing.value) return
  showCreateModal.value = true
}

async function handleCreateUser() {
  if (!showCreateModal.value || writing.value) return
  const payload = { ...createForm, username: createForm.username.trim(), phone: createForm.phone?.trim(),
    name: createForm.name.trim(), password: createForm.password, role: createForm.role as Role,
    departmentId: createForm.departmentId, virtualUser: Boolean(createForm.virtualUser) }
  await runUserMutation(token => createUser(token, payload), created => {
    emit('reload-users')
    showCreateModal.value = false
    successUserPassword.value = payload.password
    successUser.value = created
    resetCreateForm()
  }, '创建用户失败')
}

function syncCurrentUser(updated: UserInfo) {
  if (props.currentUser?.id === updated.id) {
    emit('update-current-user', updated)
    localStorage.setItem('heihu_user', JSON.stringify(updated))
  }
}

async function copySuccessUser() {
  if (!successUser.value) return
  const session = captureSession()
  const text = `账号：${successUser.value.username}\n姓名：${successUser.value.name}\n角色：${successUser.value.roleLabel}\n部门：${successUser.value.departmentName || '无'}\n密码：${successUserPassword.value}`
  try {
    await navigator.clipboard.writeText(text)
    if (session.current()) showAction('账号信息已复制到剪贴板')
  } catch (err) {
    if (session.current()) errorMessage.value = '复制失败，请手动复制'
  }
}

function showAction(message: string) {
  emit('notice', message)
}

function openEditUser(user: UserInfo) {
  if (writing.value) return
  editingUser.value = user
  editForm.username = user.username
  editForm.phone = user.phone || ''
  editForm.name = user.name
  editForm.role = user.role
  editForm.departmentId = user.departmentId
  editForm.virtualUser = user.virtualUser
  showEditModal.value = true
}

async function handleUpdateUser() {
  if (!showEditModal.value || !editingUser.value || writing.value) return
  const id = editingUser.value.id
  const payload = { username: editForm.username.trim(), phone: editForm.phone?.trim(), name: editForm.name.trim(),
    role: editForm.role, departmentId: editForm.departmentId, virtualUser: Boolean(editForm.virtualUser) }
  await runUserMutation(token => updateUser(token, id, payload), updated => {
    syncCurrentUser(updated)
    emit('reload-users')
    showEditModal.value = false
    showAction(`用户「${updated.name}」已更新`)
  }, '更新用户失败')
}

function openPermissionConfig(user: UserInfo) {
  if (writing.value) return
  permissionUser.value = user
  permissionRole.value = user.role
  showPermissionModal.value = true
}

async function handleUpdatePermission() {
  if (!showPermissionModal.value || !permissionUser.value || writing.value) return
  const id = permissionUser.value.id
  const role = permissionRole.value
  await runUserMutation(token => updateUserPermission(token, id, role), updated => {
    syncCurrentUser(updated)
    emit('reload-users')
    showPermissionModal.value = false
    showAction(`用户「${updated.name}」权限已更新为${updated.roleLabel}`)
  }, '权限配置失败')
}

function openResetPassword(user: UserInfo) {
  if (writing.value) return
  resetPasswordUser.value = user
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
  showNewPassword.value = false
  showConfirmPassword.value = false
  showResetPasswordModal.value = true
}

async function handleResetPassword() {
  if (!showResetPasswordModal.value || !resetPasswordUser.value || writing.value) return
  const user = resetPasswordUser.value
  const newPassword = resetPasswordForm.password
  const confirmPassword = resetPasswordForm.confirmPassword
  if (!newPassword.trim()) {
    errorMessage.value = '新密码不能为空'
    return
  }
  if (newPassword !== confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  await runUserMutation(token => resetUserPassword(token, user.id, newPassword), () => {
    showAction(t('用户「{0}」的密码已重置', [user.name]))
    showResetPasswordModal.value = false
    resetPasswordForm.password = ''; resetPasswordForm.confirmPassword = ''
  }, '重置密码失败')
}

function confirmUnbindPhone(user: UserInfo) {
  if (writing.value) return
  const session = captureSession()
  if (!user.phone) {
    showAction(`用户「${user.name}」当前没有绑定手机号`)
    return
  }
  emit('confirm', { title: '解绑手机号', message: `是否解绑「${user.name}」的手机号 ${user.phone} 吗？`,
    action: () => { if (session.current()) return handleUnbindPhone(user) } })
}

async function handleUnbindPhone(user: UserInfo) {
  await runUserMutation(token => unbindUserPhone(token, user.id), updated => {
    syncCurrentUser(updated)
    emit('reload-users')
    showAction(`用户「${updated.name}」手机号已解绑`)
  }, '解绑手机号失败')
}

function confirmToggleEnabled(user: UserInfo) {
  if (writing.value) return
  const session = captureSession()
  const nextEnabled = !user.enabled
  const action = nextEnabled ? '启用' : '停用'
  emit('confirm', { title: `${action}用户`, message: `是否${action}用户「${user.name}」？`,
    action: () => { if (session.current()) return handleToggleEnabled(user, nextEnabled, action) } })
}

async function handleToggleEnabled(user: UserInfo, nextEnabled: boolean, actionName: string) {
  await runUserMutation(token => updateUserStatus(token, user.id, nextEnabled), updated => {
    syncCurrentUser(updated)
    emit('reload-users')
    showAction(`用户「${updated.name}」已${actionName}`)
  }, `${actionName}用户失败`)
}

function handlePrintUser(user: UserInfo) {
  const printWindow = window.open('', '_blank', 'width=560,height=680')
  if (!printWindow) {
    errorMessage.value = t('浏览器阻止打印窗口，请允许弹窗后重试')
    return
  }
  const loginScope = t(user.role === 'ADMIN' ? '电脑端 + 手机端' : '仅手机端')
  printWindow.document.write(`
    <!doctype html>
    <html lang="${uiLocale.value}">
      <head>
        <meta charset="UTF-8" />
        <title>${printLabel('小工单用户信息')} - ${escapeHtml(user.name)}</title>
        <style>
          body { font-family: "Microsoft YaHei", Arial, sans-serif; padding: 32px; color: #1f2725; }
          h1 { font-size: 22px; margin: 0 0 24px; }
          table { width: 100%; border-collapse: collapse; }
          td { border: 1px solid #dfe7e4; padding: 12px; font-size: 14px; }
          td:first-child { width: 120px; background: #f6f8f7; color: #59635f; }
        </style>
      </head>
      <body>
        <h1>${printLabel('小工单用户信息')}</h1>
        <table>
          <tr><td>${printLabel('账号')}</td><td>${escapeHtml(user.username)}</td></tr>
          <tr><td>${printLabel('姓名')}</td><td>${escapeHtml(user.name)}</td></tr>
          <tr><td>${printLabel('角色')}</td><td>${escapeHtml(printUserRole(user))}</td></tr>
          <tr><td>${printLabel('登录范围')}</td><td>${escapeHtml(loginScope)}</td></tr>
          <tr><td>${printLabel('手机号')}</td><td>${escapeHtml(user.phone || '-')}</td></tr>
          <tr><td>${printLabel('部门')}</td><td>${escapeHtml(user.departmentName || '-')}</td></tr>
          <tr><td>${printLabel('状态')}</td><td>${printLabel(user.enabled ? '启用中' : '停用中')}</td></tr>
          <tr><td>${printLabel('虚拟用户')}</td><td>${printLabel(user.virtualUser ? '是' : '否')}</td></tr>
        </table>
      </body>
    </html>
  `)
  printWindow.document.close()
  printWindow.focus()
  printWindow.print()
}

function printUserRole(user: UserInfo) {
  return user.role === 'ADMIN' ? t('管理员') : user.role === 'PRODUCTION' ? t('生产人员') : user.roleLabel
}

async function exportXlsx() {
  if (!props.token || exporting.value || writing.value || !exportActive) return
  const session = captureExportSession()
  exportError.value = ''; exporting.value = true
  try {
    const blob = await fetchUsersExportBlob(session.token)
    if (!session.current()) return
    const matrix = parseCsvRows(await blob.text(), { preserveWhitespace: true })
    if (!session.current()) return
    const downloaded = await downloadXlsx(`${t('用户列表')}_${formatUiDate(new Date()).slice(0, 10)}.xlsx`, t('用户'), matrix, session.current)
    if (downloaded && session.current()) showAction('导出Excel成功')
  } catch (error) { if (session.current()) { exportError.value = error instanceof Error ? error.message : '导出失败'; errorMessage.value = exportError.value } }
  finally { if (session.current()) exporting.value = false }
}

async function handleExport() {
  if (!exportActive) return
  if (!props.token) {
    errorMessage.value = '请先登录'
    return
  }
  if (exporting.value || writing.value) return
  const session = captureExportSession()
  exporting.value = true
  try {
    await exportUsersCsv(session.token, session.current)
    if (session.current()) showAction('用户数据导出成功（含密码哈希）')
  } catch (err) {
    if (session.current()) errorMessage.value = err instanceof Error ? err.message : '导出失败'
  } finally {
    if (session.current()) exporting.value = false
  }
}

function triggerImport() {
  if (writing.value) return
  if (fileInput.value) {
    fileInput.value.value = ''
    fileInput.value.click()
  }
}

async function handleImport(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  if (writing.value) return
  if (!props.token) {
    errorMessage.value = '请先登录'
    return
  }

  const file = target.files[0]
  const fileName = file.name
  const session = captureSession()
  importing.value = true
  errorMessage.value = ''

  try {
    const result = await importUsersCsv(session.token, file)
    if (!session.current()) return
    importResult.value = result
    emit('reload-users')

    if (result.fail === 0) {
      showAction(`导入完成：共 ${result.total} 条，全部成功`)
    } else {
      showAction(`导入完成：共 ${result.total} 条，成功 ${result.success} 条，失败 ${result.fail} 条`)
    }

    try {
      await createSystemImportLog(session.token, {
        module: 'USER',
        fileName,
        totalCount: result.total,
        successCount: result.success,
        failCount: result.fail,
        status: result.fail === 0 ? '成功' : result.success > 0 ? '部分成功' : '失败',
        errors: result.errors && result.errors.length > 0 ? result.errors.join('\n') : undefined
      })
      if (session.current()) await refreshImportLogs(1)
    } catch (error) {
      if (session.current()) errorMessage.value = t('导入已完成，但日志保存失败')
    }
  } catch (err) {
    if (session.current()) errorMessage.value = err instanceof Error ? err.message : '导入失败'
  } finally {
    if (session.current()) importing.value = false
    target.value = ''
  }
}

function openImportLog() {
  showImportLogModal.value = true
  void refreshImportLogs(1)
}

watch(() => props.token, () => {
  sessionGeneration++; beginImportLogs()
  loading.value = false; importing.value = false; exporting.value = false; exportError.value = ''; importLogsLoading.value = false
  showCreateModal.value = false; showEditModal.value = false; showPermissionModal.value = false
  showResetPasswordModal.value = false; showImportLogModal.value = false
  successUser.value = null; successUserPassword.value = ''; selectedUserIds.value = []
  editingUser.value = null; permissionUser.value = null; resetPasswordUser.value = null
  importLogs.value = []; importResult.value = null; importLogsError.value = ''; errorMessage.value = ''
  importLogsPage.value = 1; importLogsTotal.value = 0; attemptedImportLogsPage = 1
  resetCreateForm()
  Object.assign(editForm, { username: '', phone: '', name: '', role: 'ADMIN', departmentId: undefined, virtualUser: false })
  Object.assign(resetPasswordForm, { password: '', confirmPassword: '' })
  Object.assign(filterForm, { name: '', status: '启用中', account: '', role: '全部', department: '', virtual: '全部' })
  currentPage.value = 1
}, { flush: 'sync' })
onBeforeUnmount(() => { sessionGeneration++; beginImportLogs() })

function handlePrintTemplate() {
  const dataToprint = selectedUserIds.value.length > 0
    ? filteredUsers.value.filter(u => selectedUserIds.value.includes(u.id))
    : filteredUsers.value
  if (dataToprint.length === 0) {
    errorMessage.value = t('当前没有可打印的用户数据')
    return
  }
  const printWindow = window.open('', '_blank', 'width=900,height=700')
  if (!printWindow) {
    errorMessage.value = t('浏览器阻止打印窗口，请允许弹窗后重试')
    return
  }
  const rows = dataToprint.map((u, i) => `
    <tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(u.departmentName || '-')}</td>
      <td>${escapeHtml(u.username)}</td>
      <td>${escapeHtml(u.name)}</td>
      <td>${escapeHtml(printUserRole(u))}</td>
      <td>${escapeHtml(u.phone || '-')}</td>
      <td>${printLabel(u.enabled ? '启用中' : '停用中')}</td>
    </tr>
  `).join('')
  printWindow.document.write(`
    <!doctype html>
    <html lang="${uiLocale.value}">
      <head>
        <meta charset="UTF-8" />
        <title>${printLabel('用户列表打印')}</title>
        <style>
          body { font-family: "Microsoft YaHei", Arial, sans-serif; padding: 24px; color: #1f2725; }
          h1 { font-size: 20px; margin: 0 0 16px; text-align: center; }
          .info { font-size: 12px; color: #87918e; text-align: right; margin-bottom: 12px; }
          table { width: 100%; border-collapse: collapse; }
          th, td { border: 1px solid #dfe7e4; padding: 8px 12px; font-size: 13px; text-align: left; }
          th { background: #f6f8f7; color: #59635f; font-weight: 700; }
        </style>
      </head>
      <body>
        <h1>${printLabel('小工单 - 用户列表')}</h1>
        <p class="info">${printLabel('打印时间：{0}，共 {1} 人', [formatUiDate(new Date(), true), dataToprint.length])}</p>
        <table>
          <thead>
            <tr>${['序号', '部门', '账号', '姓名', '角色', '手机号', '状态'].map(label => `<th>${printLabel(label)}</th>`).join('')}</tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </body>
    </html>
  `)
  printWindow.document.close()
  printWindow.focus()
  printWindow.print()
}
</script>

<template>
      <section class="content">
        <div class="filters">
          <label>
            <span>姓名</span>
            <input v-model="filterForm.name" placeholder="请输入名称" />
          </label>
          <label>
            <span>状态</span>
            <select v-model="filterForm.status">
              <option>全部</option>
              <option>启用中</option>
              <option>停用中</option>
            </select>
          </label>
          <label>
            <span>账号</span>
            <input v-model="filterForm.account" placeholder="请输入账号" />
          </label>
          <label>
            <span>虚拟用户</span>
            <select v-model="filterForm.virtual">
              <option>全部</option>
              <option>是</option>
              <option>否</option>
            </select>
          </label>
          <label>
            <span>角色</span>
            <select v-model="filterForm.role">
              <option>全部</option>
              <option>管理员</option>
              <option>生产人员</option>
            </select>
          </label>
          <label>
            <span>部门</span>
            <select v-model="filterForm.department">
              <option value="">全部</option>
              <option v-for="dept in departments" :key="dept.id" :value="dept.name">{{ dept.name }}</option>
            </select>
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn ui-icon-button" type="button" @click="$emit('reload-users')" title="查询" aria-label="查询"><UiIcon name="search" /></button>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-left">
            <button class="primary-btn ui-icon-button" type="button" @click="openCreateUser" :disabled="writing" title="新增用户" aria-label="新增用户"><UiIcon name="plus" /></button>
            <button class="ghost-btn ui-action-button" type="button" @click="exportXlsx" :disabled="exporting || writing" title="导出Excel" aria-label="导出Excel"><UiIcon name="download" />Excel</button>
            <button class="ghost-btn ui-action-button" type="button" @click="handleExport" :disabled="exporting || writing" title="CSV备份" aria-label="CSV备份"><UiIcon name="archive" />CSV备份</button>
            <span class="export-scope-note">导出范围：当前工厂全部用户 · Excel不含密码哈希；CSV完整备份含密码哈希，不宜直接用Excel打开</span>
            <p v-if="exportError" class="export-error" role="alert">{{ exportError }}</p>
            <label class="ghost-btn" style="cursor:pointer">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              导入
              <input type="file" accept=".csv" :disabled="writing" style="display:none" @change="handleImport" />
            </label>
            <button class="ghost-btn ui-action-button" type="button" @click="openImportLog" title="导入日志" aria-label="导入日志"><UiIcon name="list" />导入日志</button>
            <TableConfigButton table-id="user-list-table" />
          </div>
        </div>

        <p v-if="writing" role="status">{{ importing ? '导入中...' : '保存中...' }}</p>

        <div class="table-container">
          <table id="user-list-table" class="data-table">
            <thead>
              <tr>
                <th style="min-width: 100px;">姓名</th>
                <th style="min-width: 120px;">账号</th>
                <th style="min-width: 180px;">手机号</th>
                <th style="min-width: 110px;">角色</th>
                <th style="min-width: 130px;">部门</th>
                <th style="min-width: 100px;">状态</th>
                <th style="min-width: 100px;">虚拟用户</th>
                <th style="min-width: 180px;">创建时间</th>
                <th style="min-width: 440px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in paginatedUsers" :key="user.id">
                <td>{{ user.name }}</td>
                <td>{{ user.username }}</td>
                <td>
                  <span>{{ user.phone || '-' }}</span>
                </td>
                <td>{{ user.roleLabel }}</td>
                <td>{{ user.departmentName || '-' }}</td>
                <td>
                  <span class="status-badge" :class="user.enabled ? 'enabled' : 'disabled'">
                    {{ user.enabled ? '启用中' : '停用中' }}
                  </span>
                </td>
                <td>{{ user.virtualUser ? '是' : '否' }}</td>
                <td>{{ formatDateTime(user.createdAt) }}</td>
                <td>
                  <button class="action-link" type="button" @click="openResetPassword(user)">重置密码</button>
                  <button class="action-link" type="button" @click="confirmUnbindPhone(user)">解绑手机号</button>
                  <button class="action-link" type="button" @click="handlePrintUser(user)">打印</button>
                  <button class="action-link ui-icon-button" type="button" @click="openEditUser(user)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
                  <button class="action-link" type="button" @click="confirmToggleEnabled(user)">{{ user.enabled ? '停用' : '启用' }}</button>
                  <button class="action-link" type="button" @click="openPermissionConfig(user)">权限配置</button>
                </td>
              </tr>
              <tr v-if="filteredUsers.length === 0">
                <td colspan="9" style="text-align:center;color:#aaa;padding:32px">暂无用户数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span class="page-total">共 {{ filteredUsers.length }} 条结果</span>
          <button class="page-btn ui-icon-button" type="button" :disabled="currentPage <= 1" @click="currentPage--" title="上一页" aria-label="上一页"><UiIcon name="arrowLeft" /></button>
          <span class="page-num">{{ currentPage }}</span>
          <button class="page-btn ui-icon-button" type="button" :disabled="currentPage >= totalPages" @click="currentPage++" title="下一页" aria-label="下一页"><UiIcon name="arrowRight" /></button>
          <select class="size-select" v-model="pageSize">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </section>

  <!-- 导入日志弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="showImportLogModal" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="importLogDialog" class="modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="`${modalTitleId}-import`" tabindex="-1">
      <header class="modal-header">
        <h2 :id="`${modalTitleId}-import`">导入日志</h2>
        <button class="close-btn ui-icon-button" type="button" @click="closeImportLogDialog" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </header>
      <div class="modal-body" style="padding:22px;overflow:auto">
        <section v-if="importResult" class="import-result">
          <h3>导入结果</h3>
          <p>总计：{{ importResult.total }} 条</p>
          <p>成功：{{ importResult.success }} 条</p>
          <p v-if="importResult.fail">失败：{{ importResult.fail }} 条</p>
          <ul v-if="importResult.errors.length">
            <li v-for="err in importResult.errors" :key="err">{{ err }}</li>
          </ul>
        </section>
        <p v-if="importLogsLoading" role="status">加载中...</p>
        <p v-if="importLogsError" role="alert">{{ importLogsError }} <button class="ui-icon-button" type="button" :disabled="importLogsLoading" @click="refreshImportLogs(attemptedImportLogsPage)" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
        <table v-if="importLogs.length" class="data-table import-history-table" :aria-busy="importLogsLoading">
          <thead><tr><th>时间</th><th>文件名</th><th>总计</th><th>成功</th><th>失败</th><th>错误</th></tr></thead>
          <tbody><tr v-for="(log, index) in importLogs" :key="index">
            <td>{{ formatDateTime(log.time) }}</td><td>{{ log.fileName }}</td><td>{{ log.total }}</td>
            <td>{{ log.success }}</td><td>{{ log.fail }}</td><td><ul v-if="log.errors.length"><li v-for="(error, errorIndex) in log.errors" :key="errorIndex">{{ error }}</li></ul><span v-else>-</span></td>
          </tr></tbody>
        </table>
        <p v-else-if="!importLogsLoading && !importLogsError">暂无导入日志</p>
        <nav v-if="importLogsTotal > 0" class="pagination import-history-pagination" aria-label="导入日志分页">
          <span>共 {{ importLogsTotal }} 条结果</span>
          <button class="page-btn ui-icon-button" type="button" :disabled="importLogsLoading || !!importLogsError || importLogsPage <= 1" @click="refreshImportLogs(importLogsPage - 1)" title="上一页" aria-label="上一页"><UiIcon name="arrowLeft" /></button>
          <span class="page-num">{{ importLogsPage }} / {{ importLogsPages }}</span>
          <button class="page-btn ui-icon-button" type="button" :disabled="importLogsLoading || !!importLogsError || importLogsPage >= importLogsPages" @click="refreshImportLogs(importLogsPage + 1)" title="下一页" aria-label="下一页"><UiIcon name="arrowRight" /></button>
        </nav>
      </div>
    </section>
  </div>
  </Transition>

  <!-- 新增用户弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="showCreateModal" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="userCreateFlowDialog" class="modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="`${modalTitleId}-create`" tabindex="-1">
      <header class="modal-header">
        <h2 :id="`${modalTitleId}-create`">新增用户</h2>
        <button class="close-btn ui-icon-button" type="button" @click="closeUserCreateDialog" :disabled="writing" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </header>
      <form class="create-form" @submit.prevent="handleCreateUser">
        <label>
          <span><em>*</em> 姓名</span>
          <input v-model="createForm.name" :disabled="writing" placeholder="请输入姓名" required />
        </label>
        <label>
          <span><em>*</em> 账号</span>
          <input v-model="createForm.username" :disabled="writing" placeholder="请输入账号" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="createForm.phone" :disabled="writing" placeholder="请输入手机号" />
        </label>
        <label>
          <span><em>*</em> 密码</span>
          <input v-model="createForm.password" :disabled="writing" placeholder="请输入密码" type="password" required />
        </label>
        <label>
          <span>角色</span>
          <select v-model="createForm.role" :disabled="writing">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <label>
          <span>部门</span>
          <select v-model="createForm.departmentId" :disabled="writing">
            <option :value="undefined">无</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>
          <span>虚拟用户</span>
          <input type="checkbox" v-model="createForm.virtualUser" :disabled="writing" style="width:auto;justify-self:start" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide ui-icon-button" type="button" @click="closeUserCreateDialog" :disabled="writing" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" :disabled="writing" type="submit">{{ loading ? '保存中...' : '保存' }}</button>
        </footer>
      </form>
    </section>
  </div>
  </Transition>

  <!-- 编辑用户弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="showEditModal" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="userEditDialog" class="modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="`${modalTitleId}-edit`" tabindex="-1">
      <header class="modal-header">
        <h2 :id="`${modalTitleId}-edit`">编辑用户</h2>
        <button class="close-btn ui-icon-button" type="button" @click="closeUserEditDialog" :disabled="writing" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </header>
      <form class="create-form" @submit.prevent="handleUpdateUser">
        <label>
          <span><em>*</em> 姓名</span>
          <input v-model="editForm.name" :disabled="writing" placeholder="请输入姓名" required />
        </label>
        <label>
          <span><em>*</em> 账号</span>
          <input v-model="editForm.username" :disabled="writing" placeholder="请输入账号" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="editForm.phone" :disabled="writing" placeholder="请输入手机号" />
        </label>
        <label>
          <span>角色</span>
          <select v-model="editForm.role" :disabled="writing">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <label>
          <span>部门</span>
          <select v-model="editForm.departmentId" :disabled="writing">
            <option :value="undefined">无</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>
          <span>虚拟用户</span>
          <input type="checkbox" v-model="editForm.virtualUser" :disabled="writing" style="width:auto;justify-self:start" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide ui-icon-button" type="button" @click="closeUserEditDialog" :disabled="writing" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" :disabled="writing" type="submit">{{ loading ? '保存中...' : '保存' }}</button>
        </footer>
      </form>
    </section>
  </div>
  </Transition>

  <!-- 重置密码弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="showResetPasswordModal" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="passwordDialog" class="modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="`${modalTitleId}-password`" tabindex="-1">
      <header class="modal-header">
        <h2 :id="`${modalTitleId}-password`">重置密码</h2>
        <button class="close-btn ui-icon-button" type="button" @click="closePasswordDialog" :disabled="writing" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </header>
      <form class="create-form" @submit.prevent="handleResetPassword">
        <label>
          <span><em>*</em> 新密码</span>
          <div style="position:relative">
            <input v-model="resetPasswordForm.password" :disabled="writing" :type="showNewPassword ? 'text' : 'password'" placeholder="请输入新密码" required />
            <button type="button" class="password-toggle ui-icon-button" :disabled="writing" @click="showNewPassword = !showNewPassword" :title="showNewPassword ? '隐藏密码' : '显示密码'" :aria-label="showNewPassword ? '隐藏密码' : '显示密码'"><UiIcon name="eye" :size="18" /></button>
          </div>
        </label>
        <label>
          <span><em>*</em> 确认密码</span>
          <div style="position:relative">
            <input v-model="resetPasswordForm.confirmPassword" :disabled="writing" :type="showConfirmPassword ? 'text' : 'password'" placeholder="请再次输入密码" required />
            <button type="button" class="password-toggle ui-icon-button" :disabled="writing" @click="showConfirmPassword = !showConfirmPassword" :title="showConfirmPassword ? '隐藏密码' : '显示密码'" :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"><UiIcon name="eye" :size="18" /></button>
          </div>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide ui-icon-button" type="button" @click="closePasswordDialog" :disabled="writing" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" :disabled="writing" type="submit">{{ loading ? '保存中...' : '保存' }}</button>
        </footer>
      </form>
    </section>
  </div>
  </Transition>

  <!-- 权限配置弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="showPermissionModal" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="permissionDialog" class="modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="`${modalTitleId}-permission`" tabindex="-1">
      <header class="modal-header">
        <h2 :id="`${modalTitleId}-permission`">权限配置</h2>
        <button class="close-btn ui-icon-button" type="button" @click="closePermissionDialog" :disabled="writing" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </header>
      <form class="create-form" @submit.prevent="handleUpdatePermission">
        <label>
          <span>角色</span>
          <select v-model="permissionRole" :disabled="writing">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide ui-icon-button" type="button" @click="closePermissionDialog" :disabled="writing" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" :disabled="writing" type="submit">{{ loading ? '保存中...' : '保存' }}</button>
        </footer>
      </form>
    </section>
  </div>
  </Transition>

  <!-- 新建用户成功弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="successUser" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="userCreateFlowDialog" class="modal success-modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="`${modalTitleId}-success`" tabindex="-1">
      <div class="success-icon">
        <svg viewBox="0 0 24 24" width="48" height="48" stroke="#0c9d82" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><polyline points="9 12 11 14 15 10"/></svg>
      </div>
      <h2 :id="`${modalTitleId}-success`">新建用户成功</h2>
      <div class="success-info">
        <p><span>账号：</span>{{ successUser?.username }}</p>
        <p><span>姓名：</span>{{ successUser?.name }}</p>
        <p><span>角色：</span>{{ successUser?.roleLabel }}</p>
        <p><span>密码：</span>{{ successUserPassword }}</p>
      </div>
      <footer class="modal-footer">
        <button class="ghost-btn wide ui-icon-button" type="button" @click="copySuccessUser" title="复制信息" aria-label="复制信息"><UiIcon name="copy" /></button>
        <button class="primary-btn wide ui-icon-button" type="button" @click="closeSuccessDialog" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </footer>
    </section>
  </div>
  </Transition>
</template>
