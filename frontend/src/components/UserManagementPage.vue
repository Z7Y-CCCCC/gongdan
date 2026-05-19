<script setup lang="ts">
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
      filterForm.department === '全部' || item.departmentName === filterForm.department
    const matchStatus =
      filterForm.status === '全部' ||
      (filterForm.status === '全部' ? true : filterForm.status === '启用中' ? item.enabled : !item.enabled)
    const matchVirtual =
      filterForm.virtual === '全部' ||
      (filterForm.virtual === '全部' ? true : filterForm.virtual === '是' ? item.virtualUser : !item.virtualUser)
    return matchName && matchAccount && matchRole && matchDepartment && matchStatus && matchVirtual
  })
})

const isAllSelected = computed({
  get() {
    return filteredUsers.value.length > 0 && selectedUserIds.value.length === filteredUsers.value.length
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

async function handleCreateUser() {
  if (!props.token) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const created = await createUser(props.token, {
      ...createForm,
      username: createForm.username.trim(),
      phone: createForm.phone?.trim(),
      name: createForm.name.trim(),
      password: createForm.password,
      role: createForm.role as Role,
      departmentId: createForm.departmentId,
      virtualUser: Boolean(createForm.virtualUser)
    })
    emit('reload-users')
    successUser.value = created
    successUserPassword.value = createForm.password
    showCreateModal.value = false
    resetCreateForm()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '创建用户失败'
  } finally {
    loading.value = false
  }
}

function syncCurrentUser(updated: UserInfo) {
  if (props.currentUser?.id === updated.id) {
    emit('update-current-user', updated)
    localStorage.setItem('heihu_user', JSON.stringify(updated))
  }
}

async function copySuccessUser() {
  if (!successUser.value) return
  const text = `账号：${successUser.value.username}\n姓名：${successUser.value.name}\n角色：${successUser.value.roleLabel}\n部门：${successUser.value.departmentName || '无'}\n密码：${successUserPassword.value}`
  try {
    await navigator.clipboard.writeText(text)
    showAction('账号信息已复制到剪贴板')
  } catch (err) {
    window.alert('复制失败，请手动复制')
  }
}

function showAction(message: string) {
  emit('notice', message)
}

function openEditUser(user: UserInfo) {
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
  if (!props.token || !editingUser.value) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const updated = await updateUser(props.token, editingUser.value.id, {
      username: editForm.username.trim(),
      phone: editForm.phone?.trim(),
      name: editForm.name.trim(),
      role: editForm.role,
      departmentId: editForm.departmentId,
      virtualUser: Boolean(editForm.virtualUser)
    })
    syncCurrentUser(updated)
    emit('reload-users')
    showEditModal.value = false
    showAction(`用户「${updated.name}」已更新`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '更新用户失败'
  } finally {
    loading.value = false
  }
}

function openPermissionConfig(user: UserInfo) {
  permissionUser.value = user
  permissionRole.value = user.role
  showPermissionModal.value = true
}

async function handleUpdatePermission() {
  if (!props.token || !permissionUser.value) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const updated = await updateUserPermission(props.token, permissionUser.value.id, permissionRole.value)
    syncCurrentUser(updated)
    emit('reload-users')
    showPermissionModal.value = false
    showAction(`用户「${updated.name}」权限已更新为${updated.roleLabel}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '权限配置失败'
  } finally {
    loading.value = false
  }
}

function openResetPassword(user: UserInfo) {
  resetPasswordUser.value = user
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
  showNewPassword.value = false
  showConfirmPassword.value = false
  showResetPasswordModal.value = true
}

async function handleResetPassword() {
  if (!props.token || !resetPasswordUser.value) {
    return
  }
  const newPassword = resetPasswordForm.password
  const confirmPassword = resetPasswordForm.confirmPassword
  if (!newPassword.trim()) {
    window.alert('新密码不能为空')
    return
  }
  if (newPassword !== confirmPassword) {
    window.alert('两次输入的密码不一致')
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const result = await resetUserPassword(props.token, resetPasswordUser.value.id, newPassword)
    showAction(`用户「${resetPasswordUser.value.name}」的密码已重置，新密码：${newPassword}`)
    showResetPasswordModal.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '重置密码失败'
  } finally {
    loading.value = false
  }
}

function confirmUnbindPhone(user: UserInfo) {
  if (!user.phone) {
    showAction(`用户「${user.name}」当前没有绑定手机号`)
    return
  }
  emit('confirm', { title: '解绑手机号', message: `是否解绑「${user.name}」的手机号 ${user.phone} 吗？`, action: () => handleUnbindPhone(user) })
}

async function handleUnbindPhone(user: UserInfo) {
  if (!props.token) {
    return
  }
    errorMessage.value = ''
  loading.value = true
  try {
    const updated = await unbindUserPhone(props.token, user.id)
    syncCurrentUser(updated)
    emit('reload-users')
    showAction(`用户「${updated.name}」手机号已解绑`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '解绑手机号失败'
  } finally {
    loading.value = false
  }
}

function confirmToggleEnabled(user: UserInfo) {
  const nextEnabled = !user.enabled
  const action = nextEnabled ? '启用' : '停用'
  emit('confirm', { title: `${action}用户`, message: `是否${action}用户「${user.name}」？`, action: () => handleToggleEnabled(user, nextEnabled, action) })
}

async function handleToggleEnabled(user: UserInfo, nextEnabled: boolean, actionName: string) {
  if (!props.token) {
    return
  }
    errorMessage.value = ''
  loading.value = true
  try {
    const updated = await updateUserStatus(props.token, user.id, nextEnabled)
    syncCurrentUser(updated)
    emit('reload-users')
    showAction(`用户「${updated.name}」已${actionName}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : `${actionName}用户失败`
  } finally {
    loading.value = false
  }
}

function handlePrintUser(user: UserInfo) {
  const printWindow = window.open('', '_blank', 'width=560,height=680')
  if (!printWindow) {
    errorMessage.value = '浏览器阻止打印窗口，请允许弹窗后重试'
    return
  }
  const loginScope = user.role === 'ADMIN' ? '电脑端 + 手机端' : '仅手机端'
  printWindow.document.write(`
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <meta charset="UTF-8" />
        <title>用户信息 - ${user.name}</title>
        <style>
          body { font-family: "Microsoft YaHei", Arial, sans-serif; padding: 32px; color: #1f2725; }
          h1 { font-size: 22px; margin: 0 0 24px; }
          table { width: 100%; border-collapse: collapse; }
          td { border: 1px solid #dfe7e4; padding: 12px; font-size: 14px; }
          td:first-child { width: 120px; background: #f6f8f7; color: #59635f; }
        </style>
      </head>
      <body>
        <h1>黑湖小工单用户信息</h1>
        <table>
          <tr><td>账号</td><td>${user.username}</td></tr>
          <tr><td>姓名</td><td>${user.name}</td></tr>
          <tr><td>角色</td><td>${user.roleLabel}</td></tr>
          <tr><td>登录范围</td><td>${loginScope}</td></tr>
          <tr><td>手机号</td><td>${user.phone || '-'}</td></tr>
          <tr><td>部门</td><td>${user.departmentName || '-'}</td></tr>
          <tr><td>状态</td><td>${user.enabled ? '启用中' : '停用中'}</td></tr>
          <tr><td>虚拟用户</td><td>${user.virtualUser ? '是' : '否'}</td></tr>
        </table>
      </body>
    </html>
  `)
  printWindow.document.close()
  printWindow.focus()
  printWindow.print()
}

async function handleExport() {
  if (!props.token) {
    errorMessage.value = '请先登录'
    return
  }
  try {
    await exportUsersCsv(props.token)
    showAction('用户数据导出成功（含密码哈希）')
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '导出失败'
  }
}

function triggerImport() {
  if (fileInput.value) {
    fileInput.value.value = ''
    fileInput.value.click()
  }
}

async function handleImport(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  if (!props.token) {
    errorMessage.value = '请先登录'
    return
  }

  const file = target.files[0]
  const fileName = file.name

  try {
    const result = await importUsersCsv(props.token, file)
    importResult.value = result

    importLogs.value.unshift({
      time: new Date().toLocaleString('zh-CN'),
      fileName,
      total: result.total,
      success: result.success,
      fail: result.fail,
      errors: result.errors
    })

    emit('reload-users')

    if (result.fail === 0) {
      showAction(`导入完成：共 ${result.total} 条，全部成功`)
    } else {
      showAction(`导入完成：共 ${result.total} 条，成功 ${result.success} 条，失败 ${result.fail} 条`)
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '导入失败'
  }
  target.value = ''
}

function openImportLog() {
  showImportLogModal.value = true
}

function handlePrintTemplate() {
  const dataToprint = selectedUserIds.value.length > 0
    ? filteredUsers.value.filter(u => selectedUserIds.value.includes(u.id))
    : filteredUsers.value
  if (dataToprint.length === 0) {
    errorMessage.value = '当前没有可打印的用户数据'
    return
  }
  const printWindow = window.open('', '_blank', 'width=900,height=700')
  if (!printWindow) {
    errorMessage.value = '浏览器阻止打印窗口，请允许弹窗后重试'
    return
  }
  const rows = dataToprint.map((u, i) => `
    <tr>
      <td>${i + 1}</td>
      <td>${u.departmentName || '-'}</td>
      <td>${u.username}</td>
      <td>${u.name}</td>
      <td>${u.roleLabel}</td>
      <td>${u.phone || '-'}</td>
      <td>${u.enabled ? '启用' : '停用'}</td>
    </tr>
  `).join('')
  printWindow.document.write(`
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <meta charset="UTF-8" />
        <title>用户列表打印</title>
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
        <h1>黑湖小工单 - 用户列表</h1>
        <p class="info">打印时间：${new Date().toLocaleString('zh-CN')}，共 ${dataToprint.length} 人</p>
        <table>
          <thead>
            <tr><th>序号</th><th>部门</th><th>账号</th><th>姓名</th><th>角色</th><th>手机号</th><th>状态</th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
        <script>window.print();<\/script>
      </body>
    </html>
  `)
  printWindow.document.close()
  printWindow.focus()
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
            <button class="primary-btn query-btn" type="button" @click="$emit('reload-users')">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-left">
            <button class="primary-btn" type="button" @click="showCreateModal = true">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              新增用户
            </button>
            <button class="ghost-btn" type="button" @click="handleExport">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              导出
            </button>
            <label class="ghost-btn" style="cursor:pointer">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              导入
              <input type="file" accept=".csv" style="display:none" @change="handleImport" />
            </label>
          </div>
        </div>

        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>姓名</th>
                <th>账号</th>
                <th>手机号</th>
                <th>角色</th>
                <th>部门</th>
                <th>状态</th>
                <th>虚拟用户</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in filteredUsers" :key="user.id">
                <td>{{ user.name }}</td>
                <td>{{ user.username }}</td>
                <td>
                  <span>{{ user.phone || '-' }}</span>
                  <button v-if="user.phone" class="inline-link" type="button" @click="confirmUnbindPhone(user)">解绑</button>
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
                  <button class="action-link" type="button" @click="openEditUser(user)">编辑</button>
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
          <span class="page-total">共 {{ users.length }} 条结果</span>
          <button class="page-btn" type="button" :disabled="currentPage <= 1" @click="currentPage--">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="page-num">{{ currentPage }}</span>
          <button class="page-btn" type="button" :disabled="currentPage >= totalPages" @click="currentPage++">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <select class="size-select" v-model="pageSize">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </section>

  <!-- 导入日志弹窗 -->
  <div v-if="showImportLogModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>导入结果</h2>
        <button class="close-btn" type="button" @click="showImportLogModal = false">×</button>
      </header>
      <div style="padding: 22px 44px 24px; min-width: 340px;">
        <p>总计：{{ importResult?.total }} 条</p>
        <p style="color:#0c9d82">成功：{{ importResult?.success }} 条</p>
        <p v-if="importResult?.fail" style="color:#d74434">失败：{{ importResult?.fail }} 条</p>
        <ul v-if="importResult?.errors?.length" style="margin-top:8px;padding-left:16px;color:#d74434;font-size:13px">
          <li v-for="err in importResult?.errors" :key="err">{{ err }}</li>
        </ul>
      </div>
    </section>
  </div>

  <!-- 新增用户弹窗 -->
  <div v-if="showCreateModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>新增用户</h2>
        <button class="close-btn" type="button" @click="showCreateModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleCreateUser">
        <label>
          <span><em>*</em> 姓名</span>
          <input v-model="createForm.name" placeholder="请输入姓名" required />
        </label>
        <label>
          <span><em>*</em> 账号</span>
          <input v-model="createForm.username" placeholder="请输入账号" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="createForm.phone" placeholder="请输入手机号" />
        </label>
        <label>
          <span><em>*</em> 密码</span>
          <input v-model="createForm.password" placeholder="请输入密码" type="password" required />
        </label>
        <label>
          <span>角色</span>
          <select v-model="createForm.role">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <label>
          <span>部门</span>
          <select v-model="createForm.departmentId">
            <option :value="undefined">无</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>
          <span>虚拟用户</span>
          <input type="checkbox" v-model="createForm.virtualUser" style="width:auto;justify-self:start" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showCreateModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 编辑用户弹窗 -->
  <div v-if="showEditModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>编辑用户</h2>
        <button class="close-btn" type="button" @click="showEditModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleUpdateUser">
        <label>
          <span><em>*</em> 姓名</span>
          <input v-model="editForm.name" placeholder="请输入姓名" required />
        </label>
        <label>
          <span><em>*</em> 账号</span>
          <input v-model="editForm.username" placeholder="请输入账号" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="editForm.phone" placeholder="请输入手机号" />
        </label>
        <label>
          <span>角色</span>
          <select v-model="editForm.role">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <label>
          <span>部门</span>
          <select v-model="editForm.departmentId">
            <option :value="undefined">无</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>
          <span>虚拟用户</span>
          <input type="checkbox" v-model="editForm.virtualUser" style="width:auto;justify-self:start" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showEditModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 重置密码弹窗 -->
  <div v-if="showResetPasswordModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>重置密码</h2>
        <button class="close-btn" type="button" @click="showResetPasswordModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleResetPassword">
        <label>
          <span><em>*</em> 新密码</span>
          <div style="position:relative">
            <input v-model="resetPasswordForm.password" :type="showNewPassword ? 'text' : 'password'" placeholder="请输入新密码" required />
            <button type="button" class="password-toggle" @click="showNewPassword = !showNewPassword">
              <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            </button>
          </div>
        </label>
        <label>
          <span><em>*</em> 确认密码</span>
          <div style="position:relative">
            <input v-model="resetPasswordForm.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" placeholder="请再次输入密码" required />
            <button type="button" class="password-toggle" @click="showConfirmPassword = !showConfirmPassword">
              <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            </button>
          </div>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showResetPasswordModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 权限配置弹窗 -->
  <div v-if="showPermissionModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>权限配置</h2>
        <button class="close-btn" type="button" @click="showPermissionModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleUpdatePermission">
        <label>
          <span>角色</span>
          <select v-model="permissionRole">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showPermissionModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 新建用户成功弹窗 -->
  <div v-if="successUser" class="modal-backdrop">
    <section class="modal success-modal">
      <div class="success-icon">
        <svg viewBox="0 0 24 24" width="48" height="48" stroke="#0c9d82" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><polyline points="9 12 11 14 15 10"/></svg>
      </div>
      <h2>新建用户成功</h2>
      <div class="success-info">
        <p><span>账号：</span>{{ successUser?.username }}</p>
        <p><span>姓名：</span>{{ successUser?.name }}</p>
        <p><span>角色：</span>{{ successUser?.roleLabel }}</p>
        <p><span>密码：</span>{{ successUserPassword }}</p>
      </div>
      <footer class="modal-footer">
        <button class="ghost-btn wide" type="button" @click="copySuccessUser">复制信息</button>
        <button class="primary-btn wide" type="button" @click="successUser = null">关闭</button>
      </footer>
    </section>
  </div>
</template>
