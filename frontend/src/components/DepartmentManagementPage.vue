<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useDialogFocus } from '../composables/useDialogFocus'
import { workspaceDialogMotion } from '../workspaceDialogMotion'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import {
  fetchDepartments, createDepartment, updateDepartment, deleteDepartment,
  fetchUsers,
  type DepartmentInfo, type CreateDepartmentPayload, type UserInfo
} from '../api'
import TableConfigButton from './TableConfigButton.vue'
import { t } from '../i18n'

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
  (e: 'reload-users'): void
}>()

const loading = ref(false)
const departmentsLoading = ref(false)
const displayedDepartments = ref<DepartmentInfo[]>([...props.departments])
const displayedTotal = ref(props.departmentTotal)
const departmentLoadError = ref('')
const usersLoading = ref(false)
const usersLoadError = ref('')
const membershipReady = ref(false)
const beginDepartments = useLatestRequest()
const beginUsers = useLatestRequest()
let sessionGeneration = 0
let modalGeneration = 0
const departmentFilterForm = reactive({ code: '', name: '', page: 1, size: 10 })
const displayedPageSize = ref(10)
let appliedDepartmentQuery = { code: '', name: '', page: 1, size: 10 }
const showDepartmentModal = ref(false)
const departmentDialogRef = ref<HTMLElement | null>(null)
const departmentDialogTitleId = 'department-dialog-' + Math.random().toString(36).slice(2, 10)
useDialogFocus(departmentDialogRef, () => showDepartmentModal.value, closeDepartmentModal)
const editingDepartment = ref<DepartmentInfo | null>(null)
const departmentForm = reactive<CreateDepartmentPayload>({ code: '', name: '', userIds: [] })
const allUsersList = ref<UserInfo[]>([])
const showUserSelectDropdown = ref(false)
const userSelectTriggerRef = ref<HTMLElement | null>(null)
const memberListId = 'department-members-' + Math.random().toString(36).slice(2, 10)

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

function openUserSelectDropdown() {
  if (usersLoading.value || !membershipReady.value || loading.value) return
  showUserSelectDropdown.value = !showUserSelectDropdown.value
}
function closeMemberList() { showUserSelectDropdown.value = false; userSelectTriggerRef.value?.focus() }
function onMemberTriggerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !showUserSelectDropdown.value) return
  event.preventDefault(); event.stopPropagation(); closeMemberList()
}

const selectedUserNames = computed(() => {
  if (!departmentForm.userIds || departmentForm.userIds.length === 0) return t('请选择部门人员')
  return allUsersList.value
    .filter(u => departmentForm.userIds?.includes(u.id))
    .map(u => u.name)
    .join('、')
})

async function loadDepartments() {
  const token = props.token
  const isLatest = beginDepartments()
  if (!token) { departmentsLoading.value = false; return }
  const params = { ...appliedDepartmentQuery }
  const current = () => isLatest() && props.token === token
  departmentsLoading.value = true
  departmentLoadError.value = ''
  try {
    const res = await fetchDepartments(token, params)
    if (!current()) return
    const maximum = Math.max(1, Math.ceil(res.total / params.size))
    if (params.page > maximum) { appliedDepartmentQuery.page = maximum; await loadDepartments(); return }
    displayedDepartments.value = res.items
    displayedTotal.value = res.total
    departmentFilterForm.page = params.page
    departmentFilterForm.size = params.size
    displayedPageSize.value = params.size
  } catch (error) {
    if (!current()) return
    departmentFilterForm.size = displayedPageSize.value
    departmentLoadError.value = error instanceof Error ? error.message : '部门加载失败'
    emit('error', departmentLoadError.value)
  } finally {
    if (current()) departmentsLoading.value = false
  }
}

function queryDepartments() {
  if (loading.value) return
  appliedDepartmentQuery = {
    code: departmentFilterForm.code.trim(), name: departmentFilterForm.name.trim(),
    page: 1, size: departmentFilterForm.size
  }
  void loadDepartments()
}

async function loadAllUsersForDepartment() {
  const token = props.token
  const modal = modalGeneration
  const departmentId = editingDepartment.value?.id
  const isLatest = beginUsers()
  const current = () => isLatest() && props.token === token && modalGeneration === modal && showDepartmentModal.value
  if (!token) return
  usersLoading.value = true
  usersLoadError.value = ''
  membershipReady.value = false
  try {
    const allUsers = await fetchUsers(token)
    if (!current()) return
    allUsersList.value = allUsers
    if (departmentId) departmentForm.userIds = allUsers.filter(user => user.departmentId === departmentId).map(user => user.id)
    membershipReady.value = true
  } catch (error) {
    if (current()) usersLoadError.value = error instanceof Error ? error.message : '用户列表加载失败'
  } finally {
    if (current()) usersLoading.value = false
  }
}

function handleDepartmentPageChange(dir: number) {
  const newPage = departmentFilterForm.page + dir
  if (newPage < 1) return
  if (departmentsLoading.value || loading.value || departmentLoadError.value) return
  const maxPage = Math.ceil(displayedTotal.value / displayedPageSize.value) || 1
  if (newPage > maxPage) return
  appliedDepartmentQuery = { ...appliedDepartmentQuery, page: newPage }
  void loadDepartments()
}

function openCreateDepartmentModal() {
  if (loading.value) return
  modalGeneration++
  showUserSelectDropdown.value = false
  editingDepartment.value = null
  departmentForm.code = ''
  departmentForm.name = ''
  departmentForm.userIds = []
  showDepartmentModal.value = true
  loadAllUsersForDepartment()
}

function openEditDepartmentModal(dept: DepartmentInfo) {
  if (loading.value) return
  modalGeneration++
  showUserSelectDropdown.value = false
  editingDepartment.value = dept
  departmentForm.code = dept.code
  departmentForm.name = dept.name
  departmentForm.userIds = []
  showDepartmentModal.value = true
  void loadAllUsersForDepartment()
}

function closeDepartmentModal() {
  if (loading.value) return
  modalGeneration++; beginUsers(); usersLoading.value = false
  showUserSelectDropdown.value = false; showDepartmentModal.value = false
}

async function handleSaveDepartment() {
  if (!props.token || !showDepartmentModal.value || loading.value || usersLoading.value || !membershipReady.value) return
  if (!departmentForm.name.trim()) { emit('error', '请输入部门名称'); return }
  const token = props.token
  const generation = sessionGeneration
  const modal = modalGeneration
  const id = editingDepartment.value?.id
  const payload = { name: departmentForm.name.trim(), code: departmentForm.code.trim(), userIds: [...(departmentForm.userIds || [])] }
  const current = () => props.token === token && sessionGeneration === generation
  beginDepartments()
  departmentsLoading.value = false
  loading.value = true
  try {
    if (id) {
      await updateDepartment(token, id, payload)
    } else {
      await createDepartment(token, payload)
    }
    if (!current()) return
    if (modal === modalGeneration) {
      modalGeneration++; beginUsers(); showUserSelectDropdown.value = false; showDepartmentModal.value = false
    }
    emit('notice', id ? '部门已更新' : '部门已创建')
    emit('reload-departments')
    emit('reload-users')
    await loadDepartments()
  } catch (error) {
    if (current()) emit('error', error instanceof Error ? error.message : '保存失败')
  } finally {
    if (current()) loading.value = false
  }
}

function confirmDeleteDepartment(dept: DepartmentInfo) {
  if (loading.value) return
  const token = props.token
  const generation = sessionGeneration
  emit('confirm', {
    title: '删除部门',
    message: `是否确要删除「${dept.name}」？其下用户的部门信息将被清空。`,
    action: async () => {
      if (!token || props.token !== token || generation !== sessionGeneration || loading.value) return
      beginDepartments(); departmentsLoading.value = false
      loading.value = true
      try {
        await deleteDepartment(token, dept.id)
        if (props.token !== token || generation !== sessionGeneration) return
        const rowWasVisible = displayedDepartments.value.some(row => row.id === dept.id)
        displayedDepartments.value = displayedDepartments.value.filter(row => row.id !== dept.id)
        if (rowWasVisible) displayedTotal.value = Math.max(0, displayedTotal.value - 1)
        emit('notice', '部门已删除')
        emit('reload-departments')
        emit('reload-users')
        await loadDepartments()
      } catch (error) {
        if (props.token === token && generation === sessionGeneration) emit('error', error instanceof Error ? error.message : '删除失败')
      } finally {
        if (props.token === token && generation === sessionGeneration) loading.value = false
      }
    }
  })
}
onMounted(loadDepartments)
useRefreshOnActivated(loadDepartments)
watch(() => props.token, () => {
  loading.value = false; departmentsLoading.value = false
  sessionGeneration++; beginDepartments(); beginUsers(); closeDepartmentModal()
  displayedDepartments.value = []; displayedTotal.value = 0
  allUsersList.value = []; membershipReady.value = false; usersLoadError.value = ''
  Object.assign(departmentFilterForm, { code: '', name: '', page: 1, size: 10 })
  displayedPageSize.value = 10
  appliedDepartmentQuery = { code: '', name: '', page: 1, size: 10 }
  void loadDepartments()
}, { flush: 'sync' })
onBeforeUnmount(() => { sessionGeneration++; modalGeneration++; beginDepartments(); beginUsers() })
</script>

<template>
      <section class="content">
        <div class="filters">
          <label>
            <span>部门名称</span>
            <input v-model="departmentFilterForm.name" placeholder="请输入部门名称" @keydown.enter="queryDepartments" />
          </label>
          <label>
            <span>部门编码</span>
            <input v-model="departmentFilterForm.code" placeholder="请输入部门编码" @keydown.enter="queryDepartments" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn ui-icon-button" type="button" :disabled="loading" @click="queryDepartments" title="查询" aria-label="查询"><UiIcon name="search" /></button>
          </div>
        </div>

        <div class="toolbar">
          <button class="primary-btn ui-icon-button" @click="openCreateDepartmentModal" title="新建部门" aria-label="新建部门" type="button"><UiIcon name="plus" /></button>
          <TableConfigButton table-id="department-list-table" />
        </div>

        <p v-if="departmentsLoading" role="status">加载中...</p>
        <p v-if="departmentLoadError" role="alert">{{ departmentLoadError }} <button class="ui-icon-button" type="button" :disabled="loading" @click="loadDepartments" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
        <div class="table-container" :aria-busy="departmentsLoading">
          <table id="department-list-table" class="data-table">
            <thead>
              <tr>
                <th>部门名称</th>
                <th>部门编码</th>
                <th>创建时间</th>
                <th>创建人</th>
                <th style="width:120px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dept in displayedDepartments" :key="dept.id">
                <td>{{ dept.name }}</td>
                <td>{{ dept.code }}</td>
                <td>{{ formatDateTime(dept.createdAt) }}</td>
                <td>{{ dept.createdBy || '-' }}</td>
                <td class="action-cell">
                  <button class="action-link ui-icon-button" type="button" @click="openEditDepartmentModal(dept)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
                  <button class="action-link danger ui-icon-button" type="button" @click="confirmDeleteDepartment(dept)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
                </td>
              </tr>
              <tr v-if="displayedDepartments.length === 0 && !departmentsLoading && !departmentLoadError">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无部门数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span class="page-total">共 {{ displayedTotal }} 条结果</span>
          <button class="page-btn ui-icon-button" type="button" :disabled="loading || departmentsLoading || !!departmentLoadError || departmentFilterForm.page <= 1" @click="handleDepartmentPageChange(-1)" title="上一页" aria-label="上一页"><UiIcon name="arrowLeft" /></button>
          <span class="page-num">{{ departmentFilterForm.page }}</span>
          <button class="page-btn ui-icon-button" type="button" :disabled="loading || departmentsLoading || !!departmentLoadError || departmentFilterForm.page >= Math.ceil(displayedTotal / displayedPageSize) || displayedTotal === 0" @click="handleDepartmentPageChange(1)" title="下一页" aria-label="下一页"><UiIcon name="arrowRight" /></button>
          <select class="size-select" :disabled="loading" v-model="departmentFilterForm.size" @change="queryDepartments">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </section>

  <!-- 部门弹窗 -->
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="showDepartmentModal" class="modal-backdrop workspace-dialog-motion-backdrop">
    <section ref="departmentDialogRef" class="modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" :aria-labelledby="departmentDialogTitleId" :aria-busy="loading || usersLoading" tabindex="-1">
      <header class="modal-header">
        <h2 :id="departmentDialogTitleId">{{ editingDepartment ? '编辑部门' : '新建部门' }}</h2>
        <button class="close-btn ui-icon-button" type="button" :disabled="loading" @click="closeDepartmentModal" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveDepartment">
        <p v-if="usersLoading" role="status">加载中...</p>
        <p v-if="usersLoadError" role="alert">{{ usersLoadError }} <button class="ui-icon-button" type="button" @click="loadAllUsersForDepartment" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
        <fieldset :disabled="loading" class="department-fields">
        <label>
          <span><em>*</em> 部门名称</span>
          <input v-model="departmentForm.name" placeholder="请输入部门名称" required />
        </label>
        <label>
          <span>部门编码</span>
          <input v-model="departmentForm.code" placeholder="留空系统自动生成" />
        </label>
        <label style="align-items: flex-start;">
          <span style="padding-top: 8px;">部门人员</span>
          <div class="custom-multiselect">
            <button type="button" ref="userSelectTriggerRef" class="multiselect-trigger" style="width:100%;text-align:left" :disabled="loading || usersLoading || !membershipReady" :aria-expanded="showUserSelectDropdown" :aria-controls="memberListId" aria-label="部门人员" @click.stop="openUserSelectDropdown" @keydown="onMemberTriggerKeydown">
              <span :class="{ placeholder: !departmentForm.userIds || departmentForm.userIds?.length === 0 }">
                {{ selectedUserNames }}
              </span>
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" :style="{ transform: showUserSelectDropdown ? 'rotate(180deg)' : '', transition: 'transform 0.2s' }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </button>
              <div v-if="showUserSelectDropdown" :id="memberListId" class="multiselect-dropdown department-member-list" role="group" aria-label="部门人员" @click.stop @keydown.esc.stop.prevent="closeMemberList">
                <label v-for="u in allUsersList" :key="u.id" class="multiselect-option">
                  <input type="checkbox" :disabled="loading" :value="u.id" v-model="departmentForm.userIds" />
                  <span>{{ u.name }} ({{ u.username }})</span>
                </label>
                <div v-if="allUsersList.length === 0" style="padding:8px 12px;color:#aaa;font-size:13px">暂无人员</div>
              </div>
          </div>
        </label>
        </fieldset>
        <footer class="modal-footer">
          <button class="ghost-btn wide ui-icon-button" type="button" :disabled="loading" @click="closeDepartmentModal" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" :disabled="loading || usersLoading || !membershipReady" type="submit">{{ loading ? '保存中...' : '保存' }}</button>
        </footer>
      </form>
    </section>
  </div>
  </Transition>

</template>

<style scoped>
.department-fields { display: contents; border: 0; padding: 0; margin: 0; }
.department-member-list { position: static; width: 100%; max-height: min(240px, 35vh); margin-top: 4px; box-shadow: none; }
.department-member-list .multiselect-option span { overflow-wrap: anywhere; min-width: 0; }
</style>
