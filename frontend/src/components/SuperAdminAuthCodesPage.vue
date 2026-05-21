<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import {
  generateAuthCode,
  fetchAuthCodes,
  disableAuthCode,
  createFactory,
  fetchFactories,
  disableFactory,
  enableFactory,
  createFactoryAdmin,
  fetchFactoryAdmins,
  disableFactoryAdmin,
  enableFactoryAdmin,
  createSuperAdmin,
  fetchSuperAdmins,
  resetSuperAdminUserPassword,
  deleteFactoryAdmin,
  deleteAuthCode,
  deleteSuperAdmin,
  type AuthCodeListItem,
  type AuthCodeResponse,
  type FactoryListItem,
  type FactoryAdminListItem,
  type FactoryAdminResponse
} from '../api'

import DeleteConfirmDialog from './DeleteConfirmDialog.vue'

type TabKey = 'factories' | 'admins' | 'authcodes' | 'superadmins'

const token = ref(localStorage.getItem('heihu_token') || '')
const activeTab = ref<TabKey>('factories')
const loading = ref(false)
const listLoading = ref(false)
const errorMessage = ref('')

// Success modal
const showSuccessModal = ref(false)
const successModalContent = ref('')
const successModalShowSave = ref(false)

let errorTimer: ReturnType<typeof setTimeout> | null = null

watch(errorMessage, (v) => {
  if (errorTimer) clearTimeout(errorTimer)
  if (v) errorTimer = setTimeout(() => { errorMessage.value = '' }, 5000)
})

function showSuccess(message: string, showSave: boolean = false) {
  successModalContent.value = message
  successModalShowSave.value = showSave
  showSuccessModal.value = true
}

function copySuccessContent() {
  navigator.clipboard.writeText(successModalContent.value)
}

function saveSuccessToFile() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
  const blob = new Blob([successModalContent.value], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `操作结果_${timestamp}.txt`
  a.click()
  URL.revokeObjectURL(url)
}

// ===== Import/Export =====
const factoryImportInput = ref<HTMLInputElement | null>(null)
const adminImportInput = ref<HTMLInputElement | null>(null)
const authcodeImportInput = ref<HTMLInputElement | null>(null)

function formatDateForFilename() {
  const d = new Date()
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
}

function downloadJson(data: unknown, filename: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function exportFactories() {
  const data = factories.value.map(f => ({
    factoryCode: f.factoryCode,
    factoryName: f.factoryName,
    enabled: f.enabled
  }))
  downloadJson(data, `factories_export_${formatDateForFilename()}.json`)
}

function exportAdmins() {
  const data = factoryAdmins.value.map(a => ({
    factoryCode: a.factoryCode,
    username: a.username,
    enabled: a.enabled
  }))
  downloadJson(data, `admins_export_${formatDateForFilename()}.json`)
}

function exportAuthCodes() {
  const data = authCodes.value.map(c => ({
    code: c.code,
    factoryCode: c.factoryCode,
    status: c.status,
    createdAt: c.createdAt,
    expiresAt: c.expiresAt
  }))
  downloadJson(data, `authcodes_export_${formatDateForFilename()}.json`)
}

function triggerImportFactories() {
  factoryImportInput.value?.click()
}

function triggerImportAdmins() {
  adminImportInput.value?.click()
}

function triggerImportAuthCodes() {
  authcodeImportInput.value?.click()
}

async function handleImportFactories(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  try {
    const text = await file.text()
    const items = JSON.parse(text) as Array<{ factoryCode: string; factoryName: string }>
    let success = 0
    let fail = 0
    const errors: string[] = []
    for (const item of items) {
      try {
        await createFactory(token.value.trim(), {
          factoryCode: item.factoryCode,
          factoryName: item.factoryName
        })
        success++
      } catch (e: any) {
        fail++
        errors.push(`${item.factoryCode}: ${e.message || '失败'}`)
      }
    }
    await loadFactories()
    const detail = errors.length > 0 ? `\n失败详情:\n${errors.join('\n')}` : ''
    showSuccess(`导入完成：成功 ${success} 条，失败 ${fail} 条${detail}`)
  } catch (e: any) {
    errorMessage.value = '导入文件解析失败: ' + (e.message || '未知错误')
  }
}

async function handleImportAdmins(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  try {
    const text = await file.text()
    const items = JSON.parse(text) as Array<{ factoryCode: string; username: string }>
    let success = 0
    let fail = 0
    const errors: string[] = []
    for (const item of items) {
      try {
        await createFactoryAdmin(token.value.trim(), {
          factoryCode: item.factoryCode,
          username: item.username
        })
        success++
      } catch (e: any) {
        fail++
        errors.push(`${item.username}: ${e.message || '失败'}`)
      }
    }
    await loadFactoryAdmins()
    const detail = errors.length > 0 ? `\n失败详情:\n${errors.join('\n')}` : ''
    showSuccess(`导入完成：成功 ${success} 条，失败 ${fail} 条${detail}`)
  } catch (e: any) {
    errorMessage.value = '导入文件解析失败: ' + (e.message || '未知错误')
  }
}

async function handleImportAuthCodes(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  try {
    const text = await file.text()
    const items = JSON.parse(text) as Array<{ factoryCode: string }>
    let success = 0
    let fail = 0
    const errors: string[] = []
    for (const item of items) {
      try {
        await generateAuthCode(token.value.trim(), {
          factoryCode: item.factoryCode,
          expirationMinutes: 30
        })
        success++
      } catch (e: any) {
        fail++
        errors.push(`${item.factoryCode}: ${e.message || '失败'}`)
      }
    }
    await loadAuthCodes()
    const detail = errors.length > 0 ? `\n失败详情:\n${errors.join('\n')}` : ''
    showSuccess(`导入完成：成功 ${success} 条，失败 ${fail} 条${detail}`)
  } catch (e: any) {
    errorMessage.value = '导入文件解析失败: ' + (e.message || '未知错误')
  }
}

// ===== 工厂管理 =====
const factories = ref<FactoryListItem[]>([])
const newFactoryCode = ref('')
const newFactoryName = ref('')

async function loadFactories() {
  if (!token.value.trim()) return
  listLoading.value = true
  try {
    factories.value = await fetchFactories(token.value.trim())
  } catch (e: any) {
    errorMessage.value = e.message || '加载工厂列表失败'
  } finally {
    listLoading.value = false
  }
}

async function handleCreateFactory() {
  if (!token.value.trim()) {
    errorMessage.value = '未登录，请先登录超级管理员账号'
    return
  }
  if (!newFactoryCode.value.trim()) {
    errorMessage.value = '请输入工厂代码'
    return
  }
  if (!newFactoryName.value.trim()) {
    errorMessage.value = '请输入工厂名称'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await createFactory(token.value.trim(), {
      factoryCode: newFactoryCode.value.trim(),
      factoryName: newFactoryName.value.trim()
    })
    showSuccess(`工厂 "${newFactoryCode.value.trim()}" 创建成功`)
    newFactoryCode.value = ''
    newFactoryName.value = ''
    await loadFactories()
  } catch (e: any) {
    errorMessage.value = e.message || '创建工厂失败'
  } finally {
    loading.value = false
  }
}

async function handleDisableFactory(code: string) {
  if (!token.value.trim()) return
  try {
    await disableFactory(token.value.trim(), code)
    showSuccess(`工厂 "${code}" 已停用`)
    await loadFactories()
  } catch (e: any) {
    errorMessage.value = e.message || '停用工厂失败'
  }
}

async function handleEnableFactory(code: string) {
  if (!token.value.trim()) return
  try {
    await enableFactory(token.value.trim(), code)
    showSuccess(`工厂 "${code}" 已启用`)
    await loadFactories()
  } catch (e: any) {
    errorMessage.value = e.message || '启用工厂失败'
  }
}

// ===== 管理员账号 =====
const factoryAdmins = ref<FactoryAdminListItem[]>([])
const adminFactoryCode = ref('')
const adminUsername = ref('')
const adminPassword = ref('')
const lastCreatedAdmin = ref<FactoryAdminResponse | null>(null)

async function loadFactoryAdmins() {
  if (!token.value.trim()) return
  listLoading.value = true
  try {
    factoryAdmins.value = await fetchFactoryAdmins(token.value.trim())
  } catch (e: any) {
    errorMessage.value = e.message || '加载管理员列表失败'
  } finally {
    listLoading.value = false
  }
}

async function handleCreateFactoryAdmin() {
  if (!token.value.trim()) {
    errorMessage.value = '未登录，请先登录超级管理员账号'
    return
  }
  if (!adminFactoryCode.value.trim()) {
    errorMessage.value = '请选择工厂代码'
    return
  }
  if (!adminUsername.value.trim()) {
    errorMessage.value = '请输入用户名'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await createFactoryAdmin(token.value.trim(), {
      factoryCode: adminFactoryCode.value.trim(),
      username: adminUsername.value.trim(),
      password: adminPassword.value || undefined
    })
    lastCreatedAdmin.value = result
    showSuccess(`工厂代码: ${adminFactoryCode.value.trim()}\n用户名: ${result.username}\n密码: ${result.password}`, true)
    adminFactoryCode.value = ''
    adminUsername.value = ''
    adminPassword.value = ''
    await loadFactoryAdmins()
  } catch (e: any) {
    errorMessage.value = e.message || '创建管理员失败'
  } finally {
    loading.value = false
  }
}

async function handleDisableFactoryAdmin(id: number) {
  if (!token.value.trim()) return
  try {
    await disableFactoryAdmin(token.value.trim(), id)
    showSuccess('管理员已停用')
    await loadFactoryAdmins()
  } catch (e: any) {
    errorMessage.value = e.message || '停用管理员失败'
  }
}

async function handleEnableFactoryAdmin(id: number) {
  if (!token.value.trim()) return
  try {
    await enableFactoryAdmin(token.value.trim(), id)
    showSuccess('管理员已启用')
    await loadFactoryAdmins()
  } catch (e: any) {
    errorMessage.value = e.message || '启用管理员失败'
  }
}

// ===== 授权码管理 =====
const authCodes = ref<AuthCodeListItem[]>([])
const authCodeFactoryCode = ref('')
const expirationMinutes = ref(30)
const lastGeneratedCode = ref<AuthCodeResponse | null>(null)

async function loadAuthCodes() {
  if (!token.value.trim()) return
  listLoading.value = true
  try {
    authCodes.value = await fetchAuthCodes(token.value.trim())
  } catch (e: any) {
    errorMessage.value = e.message || '加载授权码列表失败'
  } finally {
    listLoading.value = false
  }
}

async function handleGenerateAuthCode() {
  if (!token.value.trim()) {
    errorMessage.value = '未登录，请先登录超级管理员账号'
    return
  }
  if (!authCodeFactoryCode.value.trim()) {
    errorMessage.value = '请选择工厂代码'
    return
  }
  if (expirationMinutes.value < 1 || expirationMinutes.value > 1440) {
    errorMessage.value = '有效期必须在1-1440分钟之间'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await generateAuthCode(token.value.trim(), {
      factoryCode: authCodeFactoryCode.value.trim(),
      expirationMinutes: expirationMinutes.value
    })
    lastGeneratedCode.value = result
    showSuccess(`工厂代码: ${authCodeFactoryCode.value.trim()}\n授权码: ${result.code}\n过期时间: ${result.expiresAt}`, true)
    await loadAuthCodes()
  } catch (e: any) {
    errorMessage.value = e.message || '生成授权码失败'
  } finally {
    loading.value = false
  }
}

async function handleDisableAuthCode(id: number) {
  if (!token.value.trim()) return
  try {
    await disableAuthCode(token.value.trim(), id)
    showSuccess('授权码已停用')
    await loadAuthCodes()
  } catch (e: any) {
    errorMessage.value = e.message || '停用授权码失败'
  }
}

// ===== 超级管理员 =====
const superAdmins = ref<FactoryAdminListItem[]>([])
const superAdminUsername = ref('')
const superAdminPassword = ref('')

async function loadSuperAdmins() {
  if (!token.value.trim()) return
  listLoading.value = true
  try {
    superAdmins.value = await fetchSuperAdmins(token.value.trim())
  } catch (e: any) {
    errorMessage.value = e.message || '加载超级管理员列表失败'
  } finally {
    listLoading.value = false
  }
}

// ===== 重置密码 =====
const showResetPasswordModal = ref(false)
const resetPasswordUserId = ref<number>(0)
const resetPasswordUsername = ref('')
const resetPasswordValue = ref('')

const resetPasswordFactoryCode = ref('')

function openResetPassword(userId: number, username: string, factoryCode: string) {
  resetPasswordUserId.value = userId
  resetPasswordUsername.value = username
  resetPasswordFactoryCode.value = factoryCode
  resetPasswordValue.value = ''
  showResetPasswordModal.value = true
}

async function handleResetPassword() {
  loading.value = true
  try {
    const result = await resetSuperAdminUserPassword(
      token.value.trim(),
      resetPasswordUserId.value,
      resetPasswordValue.value || undefined
    )
    showResetPasswordModal.value = false
    showSuccess(`工厂代码: ${resetPasswordFactoryCode.value}\n用户名: ${resetPasswordUsername.value}\n密码: ${result.password}`, true)
  } catch (e: any) {
    errorMessage.value = e.message || '重置密码失败'
  } finally {
    loading.value = false
  }
}

async function handleCreateSuperAdmin() {
  if (!token.value.trim()) {
    errorMessage.value = '未登录，请先登录超级管理员账号'
    return
  }
  const username = superAdminUsername.value.trim()
  const password = superAdminPassword.value
  if (!/^[A-Za-z0-9]{3,32}$/.test(username)) {
    errorMessage.value = '用户名必须为3-32位字母或数字'
    return
  }
  if (password.length < 6 || password.length > 64) {
    errorMessage.value = '密码长度必须为6-64位'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await createSuperAdmin(token.value.trim(), { username, password })
    showSuccess(`工厂代码: ADMIN\n用户名: ${username}\n密码: ${password}`, true)
    superAdminUsername.value = ''
    superAdminPassword.value = ''
    await loadSuperAdmins()
  } catch (e: any) {
    errorMessage.value = e.message || '创建超级管理员失败'
  } finally {
    loading.value = false
  }
}

// Delete confirmation dialog
const deleteDialogVisible = ref(false)
const deleteDialogTitle = ref('')
const deleteDialogMessage = ref('')
const deleteDialogLoading = ref(false)
const deleteActionType = ref<'admin' | 'authcode' | 'superadmin' | null>(null)
const deleteActionTargetId = ref<number | null>(null)
const deleteActionTargetName = ref('')

function handleDeleteFactoryAdmin(id: number, username: string) {
  deleteActionType.value = 'admin'
  deleteActionTargetId.value = id
  deleteActionTargetName.value = username
  deleteDialogTitle.value = '删除管理员账号确认'
  deleteDialogMessage.value = `确定要删除管理员账号 "${username}" 吗？此操作不可恢复。`
  deleteDialogVisible.value = true
}

function handleDeleteAuthCode(id: number, code: string) {
  deleteActionType.value = 'authcode'
  deleteActionTargetId.value = id
  deleteActionTargetName.value = code
  deleteDialogTitle.value = '删除授权码确认'
  deleteDialogMessage.value = `确定要删除授权码 "${code}" 吗？此操作不可恢复。`
  deleteDialogVisible.value = true
}

function handleDeleteSuperAdmin(id: number, username: string) {
  deleteActionType.value = 'superadmin'
  deleteActionTargetId.value = id
  deleteActionTargetName.value = username
  deleteDialogTitle.value = '删除超级管理员确认'
  deleteDialogMessage.value = `确定要删除超级管理员账号 "${username}" 吗？此操作不可恢复。`
  deleteDialogVisible.value = true
}

async function handleDeleteConfirm() {
  if (!deleteActionType.value || deleteActionTargetId.value === null || !token.value.trim()) return
  deleteDialogLoading.value = true
  try {
    if (deleteActionType.value === 'admin') {
      await deleteFactoryAdmin(token.value.trim(), deleteActionTargetId.value)
      showSuccess(`管理员账号 "${deleteActionTargetName.value}" 已删除`)
      await loadFactoryAdmins()
    } else if (deleteActionType.value === 'authcode') {
      await deleteAuthCode(token.value.trim(), deleteActionTargetId.value)
      showSuccess(`授权码 "${deleteActionTargetName.value}" 已删除`)
      await loadAuthCodes()
    } else if (deleteActionType.value === 'superadmin') {
      await deleteSuperAdmin(token.value.trim(), deleteActionTargetId.value)
      showSuccess(`超级管理员账号 "${deleteActionTargetName.value}" 已删除`)
      await loadSuperAdmins()
    }
    deleteDialogVisible.value = false
  } catch (e: any) {
    errorMessage.value = e.message || '操作失败'
  } finally {
    deleteDialogLoading.value = false
  }
}

// ===== 通用工具 =====
function formatDateTime(val: string) {
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

function getStatusLabel(status: string) {
  switch (status) {
    case 'active': return '有效'
    case 'expired': return '已过期'
    case 'consumed': return '已使用'
    case 'disabled': return '已停用'
    default: return status
  }
}

function getStatusClass(status: string) {
  switch (status) {
    case 'active': return 'status-active'
    case 'expired': return 'status-expired'
    case 'consumed': return 'status-consumed'
    case 'disabled': return 'status-disabled'
    default: return ''
  }
}

function getEnabledLabel(enabled: boolean) {
  return enabled ? '启用' : '停用'
}

function getEnabledClass(enabled: boolean) {
  return enabled ? 'status-active' : 'status-disabled'
}

function switchTab(tab: TabKey) {
  activeTab.value = tab
  errorMessage.value = ''
  lastCreatedAdmin.value = null
  lastGeneratedCode.value = null
  if (tab === 'factories') loadFactories()
  else if (tab === 'admins') { loadFactoryAdmins(); loadFactories() }
  else if (tab === 'authcodes') { loadAuthCodes(); loadFactories() }
  else if (tab === 'superadmins') { loadSuperAdmins() }
}

onMounted(() => {
  loadFactories()
})
</script>

<template>
  <section class="content super-admin-page">
    <!-- Error Message -->
    <div v-if="errorMessage" class="message message-error">{{ errorMessage }}</div>

    <!-- Success Modal -->
    <div v-if="showSuccessModal" class="modal-backdrop">
      <section class="modal success-modal">
        <div class="success-icon">
          <svg viewBox="0 0 24 24" width="48" height="48" stroke="#0c9d82" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><polyline points="9 12 11 14 15 10"/></svg>
        </div>
        <h2>操作成功</h2>
        <div class="success-info">
          <p>{{ successModalContent }}</p>
        </div>
        <footer class="modal-footer">
          <button v-if="successModalShowSave" class="ghost-btn wide" type="button" @click="copySuccessContent">复制信息</button>
          <button v-if="successModalShowSave" class="ghost-btn wide" type="button" @click="saveSuccessToFile">保存到本地</button>
          <button class="primary-btn wide" type="button" @click="showSuccessModal = false">关闭</button>
        </footer>
      </section>
    </div>

    <!-- Tab Navigation -->
    <div class="tab-nav">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'factories' }"
        @click="switchTab('factories')"
      >工厂管理</button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'admins' }"
        @click="switchTab('admins')"
      >管理员账号</button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'authcodes' }"
        @click="switchTab('authcodes')"
      >授权码管理</button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'superadmins' }"
        @click="switchTab('superadmins')"
      >超级管理员</button>
    </div>

    <!-- 工厂管理 Tab -->
    <div v-if="activeTab === 'factories'" class="tab-content">
      <div class="generate-section">
        <h3 class="section-title">创建工厂</h3>
        <div class="form-row">
          <label class="form-label">
            <span>工厂代码</span>
            <input
              v-model="newFactoryCode"
              type="text"
              placeholder="1-16位大写字母和数字"
              class="form-input"
            />
          </label>
          <label class="form-label">
            <span>工厂名称</span>
            <input
              v-model="newFactoryName"
              type="text"
              placeholder="请输入工厂名称"
              class="form-input"
            />
          </label>
          <div class="form-actions">
            <button
              class="primary-btn"
              type="button"
              :disabled="loading"
              @click="handleCreateFactory"
            >
              {{ loading ? '创建中...' : '创建工厂' }}
            </button>
          </div>
        </div>
      </div>

      <div class="list-section">
        <div class="list-header">
          <h3 class="section-title">工厂列表</h3>
          <div class="list-header-actions">
            <button class="secondary-btn" type="button" @click="exportFactories">导出</button>
            <button class="secondary-btn" type="button" @click="triggerImportFactories">导入</button>
            <input ref="factoryImportInput" type="file" accept=".json" style="display:none" @change="handleImportFactories" />
            <button class="secondary-btn" type="button" @click="loadFactories" :disabled="listLoading">
              刷新
            </button>
          </div>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>工厂代码</th>
                <th>工厂名称</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="listLoading">
                <td colspan="5" class="empty-cell">加载中...</td>
              </tr>
              <tr v-else-if="factories.length === 0">
                <td colspan="5" class="empty-cell">暂无工厂数据</td>
              </tr>
              <tr v-for="item in factories" :key="item.factoryCode">
                <td class="code-cell">{{ item.factoryCode }}</td>
                <td>{{ item.factoryName }}</td>
                <td>
                  <span class="status-badge" :class="getEnabledClass(item.enabled)">
                    {{ getEnabledLabel(item.enabled) }}
                  </span>
                </td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
                <td>
                  <button
                    v-if="item.enabled"
                    class="danger-btn-sm"
                    @click="handleDisableFactory(item.factoryCode)"
                  >停用</button>
                  <button
                    v-else
                    class="success-btn-sm"
                    @click="handleEnableFactory(item.factoryCode)"
                  >启用</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 管理员账号 Tab -->
    <div v-if="activeTab === 'admins'" class="tab-content">
      <div class="generate-section">
        <h3 class="section-title">创建工厂管理员</h3>
        <div class="form-row">
          <label class="form-label">
            <span>工厂代码</span>
            <select
              v-model="adminFactoryCode"
              class="form-input"
            >
              <option value="" disabled>请选择工厂</option>
              <option v-for="f in factories" :key="f.factoryCode" :value="f.factoryCode">
                {{ f.factoryCode }} - {{ f.factoryName }}
              </option>
            </select>
          </label>
          <label class="form-label">
            <span>用户名</span>
            <input
              v-model="adminUsername"
              type="text"
              placeholder="请输入用户名"
              class="form-input"
            />
          </label>
          <label class="form-label">
            <span>密码（可选）</span>
            <input
              v-model="adminPassword"
              type="text"
              placeholder="留空自动生成"
              class="form-input"
            />
          </label>
          <div class="form-actions">
            <button
              class="primary-btn"
              type="button"
              :disabled="loading"
              @click="handleCreateFactoryAdmin"
            >
              {{ loading ? '创建中...' : '创建管理员' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Last Created Admin -->
      <div v-if="lastCreatedAdmin" class="generated-result">
        <div class="result-card">
          <span class="result-label">创建成功：</span>
          <span class="result-code">{{ lastCreatedAdmin.username }}</span>
          <span class="result-expires">密码：{{ lastCreatedAdmin.password }}</span>
        </div>
      </div>

      <div class="list-section">
        <div class="list-header">
          <h3 class="section-title">管理员列表</h3>
          <div class="list-header-actions">
            <button class="secondary-btn" type="button" @click="exportAdmins">导出</button>
            <button class="secondary-btn" type="button" @click="triggerImportAdmins">导入</button>
            <input ref="adminImportInput" type="file" accept=".json" style="display:none" @change="handleImportAdmins" />
            <button class="secondary-btn" type="button" @click="loadFactoryAdmins" :disabled="listLoading">
              刷新
            </button>
          </div>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>工厂代码</th>
                <th>用户名</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="listLoading">
                <td colspan="5" class="empty-cell">加载中...</td>
              </tr>
              <tr v-else-if="factoryAdmins.length === 0">
                <td colspan="5" class="empty-cell">暂无管理员数据</td>
              </tr>
              <tr v-for="item in factoryAdmins" :key="item.id">
                <td class="code-cell">{{ item.factoryCode }}</td>
                <td>{{ item.username }}</td>
                <td>
                  <span class="status-badge" :class="getEnabledClass(item.enabled)">
                    {{ getEnabledLabel(item.enabled) }}
                  </span>
                </td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
                <td>
                  <button class="secondary-btn-sm" @click="openResetPassword(item.id, item.username, item.factoryCode)">重置密码</button>
                  <button
                    v-if="item.enabled"
                    class="danger-btn-sm"
                    @click="handleDisableFactoryAdmin(item.id)"
                  >停用</button>
                  <button
                    v-else
                    class="success-btn-sm"
                    @click="handleEnableFactoryAdmin(item.id)"
                  >启用</button>
                  <button
                    class="danger-btn-sm"
                    style="margin-left: 6px;"
                    @click="handleDeleteFactoryAdmin(item.id, item.username)"
                  >删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 授权码管理 Tab -->
    <div v-if="activeTab === 'authcodes'" class="tab-content">
      <div class="generate-section">
        <h3 class="section-title">生成授权码</h3>
        <div class="form-row">
          <label class="form-label">
            <span>工厂代码</span>
            <select
              v-model="authCodeFactoryCode"
              class="form-input"
            >
              <option value="" disabled>请选择工厂</option>
              <option v-for="f in factories" :key="f.factoryCode" :value="f.factoryCode">
                {{ f.factoryCode }} - {{ f.factoryName }}
              </option>
            </select>
          </label>
          <label class="form-label">
            <span>有效期（分钟）</span>
            <input
              v-model.number="expirationMinutes"
              type="number"
              min="1"
              max="1440"
              placeholder="30"
              class="form-input"
            />
          </label>
          <div class="form-actions">
            <button
              class="primary-btn"
              type="button"
              :disabled="loading"
              @click="handleGenerateAuthCode"
            >
              {{ loading ? '生成中...' : '生成授权码' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Last Generated Code -->
      <div v-if="lastGeneratedCode" class="generated-result">
        <div class="result-card">
          <span class="result-label">生成的授权码：</span>
          <span class="result-code">{{ lastGeneratedCode.code }}</span>
          <span class="result-expires">过期时间：{{ formatDateTime(lastGeneratedCode.expiresAt) }}</span>
        </div>
      </div>

      <div class="list-section">
        <div class="list-header">
          <h3 class="section-title">授权码列表</h3>
          <div class="list-header-actions">
            <button class="secondary-btn" type="button" @click="exportAuthCodes">导出</button>
            <button class="secondary-btn" type="button" @click="triggerImportAuthCodes">导入</button>
            <input ref="authcodeImportInput" type="file" accept=".json" style="display:none" @change="handleImportAuthCodes" />
            <button class="secondary-btn" type="button" @click="loadAuthCodes" :disabled="listLoading">
              刷新
            </button>
          </div>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>授权码</th>
                <th>工厂代码</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>过期时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="listLoading">
                <td colspan="6" class="empty-cell">加载中...</td>
              </tr>
              <tr v-else-if="authCodes.length === 0">
                <td colspan="6" class="empty-cell">暂无授权码数据</td>
              </tr>
              <tr v-for="item in authCodes" :key="item.id">
                <td class="code-cell">{{ item.code }}</td>
                <td class="code-cell">{{ item.factoryCode }}</td>
                <td>
                  <span class="status-badge" :class="getStatusClass(item.status)">
                    {{ getStatusLabel(item.status) }}
                  </span>
                </td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
                <td>{{ formatDateTime(item.expiresAt) }}</td>
                <td>
                  <button
                    v-if="item.status === 'active'"
                    class="danger-btn-sm"
                    style="margin-right: 6px;"
                    @click="handleDisableAuthCode(item.id)"
                  >停用</button>
                  <button
                    class="danger-btn-sm"
                    @click="handleDeleteAuthCode(item.id, item.code)"
                  >删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    <!-- 超级管理员 Tab -->
    <div v-if="activeTab === 'superadmins'" class="tab-content">
      <div class="generate-section">
        <h3 class="section-title">创建超级管理员</h3>
        <div class="form-row">
          <label class="form-label">
            <span>用户名</span>
            <input
              v-model="superAdminUsername"
              type="text"
              placeholder="3-32位字母或数字"
              class="form-input"
            />
          </label>
          <label class="form-label">
            <span>密码</span>
            <input
              v-model="superAdminPassword"
              type="password"
              placeholder="6-64位密码"
              class="form-input"
            />
          </label>
          <div class="form-actions">
            <button
              class="primary-btn"
              type="button"
              :disabled="loading"
              @click="handleCreateSuperAdmin"
            >
              {{ loading ? '创建中...' : '创建超级管理员' }}
            </button>
          </div>
        </div>
      </div>

      <div class="list-section">
        <div class="list-header">
          <h3 class="section-title">超级管理员列表</h3>
          <div class="list-header-actions">
            <button class="secondary-btn" type="button" @click="loadSuperAdmins" :disabled="listLoading">刷新</button>
          </div>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>用户名</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="listLoading"><td colspan="4" class="empty-cell">加载中...</td></tr>
              <tr v-else-if="superAdmins.length === 0"><td colspan="4" class="empty-cell">暂无数据</td></tr>
              <tr v-for="item in superAdmins" :key="item.id">
                <td>{{ item.username }}</td>
                <td><span class="status-badge" :class="getEnabledClass(item.enabled)">{{ getEnabledLabel(item.enabled) }}</span></td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
                <td>
                  <button class="secondary-btn-sm" @click="openResetPassword(item.id, item.username, 'ADMIN')">重置密码</button>
                  <button
                    class="danger-btn-sm"
                    @click="handleDeleteSuperAdmin(item.id, item.username)"
                  >删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Reset Password Modal -->
    <div v-if="showResetPasswordModal" class="modal-backdrop">
      <section class="modal">
        <header class="modal-header-bar">
          <h2>重置密码</h2>
          <button class="close-btn" type="button" @click="showResetPasswordModal = false">×</button>
        </header>
        <div class="modal-body">
          <p class="modal-hint">为用户 "{{ resetPasswordUsername }}" 重置密码</p>
          <label class="form-label">
            <span>新密码（留空随机生成）</span>
            <input v-model="resetPasswordValue" type="text" placeholder="留空则随机生成6位密码" class="form-input" />
          </label>
        </div>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showResetPasswordModal = false">取消</button>
          <button class="primary-btn wide" type="button" :disabled="loading" @click="handleResetPassword">
            {{ loading ? '处理中...' : '确定' }}
          </button>
        </footer>
      </section>
    </div>

    <!-- 统一删除确认弹窗 -->
    <DeleteConfirmDialog
      v-model:visible="deleteDialogVisible"
      :title="deleteDialogTitle"
      :message="deleteDialogMessage"
      :loading="deleteDialogLoading"
      @confirm="handleDeleteConfirm"
    />
  </section>
</template>

<style scoped>
.super-admin-page {
  padding: 24px;
  max-width: 1100px;
}

.message {
  padding: 10px 16px;
  border-radius: 6px;
  margin-bottom: 16px;
  font-size: 13px;
}

.message-error {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

/* Tab Navigation */
.tab-nav {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  border-bottom: 2px solid #e2e8f0;
}

.tab-btn {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #3b82f6;
}

.tab-btn.active {
  color: #3b82f6;
  border-bottom-color: #3b82f6;
}

.tab-content {
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Section styles */
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 16px 0;
}

.form-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label span {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}

.form-input {
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.generate-section {
  margin-bottom: 24px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.form-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-row .form-label {
  flex: 1;
  min-width: 150px;
}

.form-actions {
  display: flex;
  align-items: flex-end;
  padding-bottom: 1px;
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}

.primary-btn:hover:not(:disabled) {
  background: #2563eb;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.secondary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #fff;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.secondary-btn:hover:not(:disabled) {
  background: #f9fafb;
  border-color: #9ca3af;
}

.secondary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.danger-btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.danger-btn-sm:hover {
  background: #fee2e2;
  border-color: #f87171;
}

.success-btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #16a34a;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.success-btn-sm:hover {
  background: #dcfce7;
  border-color: #4ade80;
}

.generated-result {
  margin-bottom: 24px;
}

.result-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
}

.result-label {
  font-size: 13px;
  color: #475569;
}

.result-code {
  font-size: 16px;
  font-weight: 700;
  color: #1d4ed8;
  font-family: monospace;
  letter-spacing: 1px;
}

.result-expires {
  font-size: 12px;
  color: #64748b;
  margin-left: auto;
}

.list-section {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.list-header .section-title {
  margin: 0;
}

.list-header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.table-container {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  text-align: left;
  padding: 10px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-weight: 500;
}

.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
}

.data-table tr:hover td {
  background: #f8fafc;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  padding: 32px 12px !important;
}

.code-cell {
  font-family: monospace;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-active {
  background: #dcfce7;
  color: #16a34a;
}

.status-expired {
  background: #fef3c7;
  color: #d97706;
}

.status-consumed {
  background: #f1f5f9;
  color: #64748b;
}

.status-disabled {
  background: #fef2f2;
  color: #dc2626;
}

.action-na {
  color: #94a3b8;
  font-size: 12px;
}

/* Success Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.modal {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  width: 420px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}

.success-modal {
  text-align: center;
}

.success-modal h2 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin: 16px 0 12px;
}

.success-icon {
  margin-bottom: 4px;
}

.success-info {
  font-size: 14px;
  color: #475569;
  line-height: 1.6;
  margin-bottom: 24px;
  word-break: break-all;
  white-space: pre-line;
  text-align: left;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.modal-footer {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.ghost-btn {
  padding: 8px 16px;
  background: #fff;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.ghost-btn:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

.wide {
  min-width: 90px;
}

.modal-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.modal-header-bar h2 {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.modal-body {
  margin-bottom: 24px;
}

.modal-hint {
  font-size: 14px;
  color: #475569;
  margin: 0 0 16px 0;
}

.secondary-btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #3b82f6;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  margin-right: 6px;
}

.secondary-btn-sm:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}
</style>
