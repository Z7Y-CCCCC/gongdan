<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  fetchDepartments, createDepartment, updateDepartment, deleteDepartment,
  fetchUsers,
  type DepartmentInfo, type CreateDepartmentPayload, type UpdateDepartmentPayload, type UserInfo
} from '../api'
import TableConfigButton from './TableConfigButton.vue'

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
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
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
    .filter(u => departmentForm.userIds?.includes(u.id))
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
    message: `是否确要删除「${dept.name}」？其下用户的部门信息将被清空。`,
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
      <section class="content">
        <div class="filters">
          <label>
            <span>部门名称</span>
            <input v-model="departmentFilterForm.name" placeholder="请输入部门名称" />
          </label>
          <label>
            <span>部门编码</span>
            <input v-model="departmentFilterForm.code" placeholder="请输入部门编码" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button" @click="loadDepartments">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>

        <div class="toolbar">
          <button class="primary-btn" @click="openCreateDepartmentModal">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            新建部门
          </button>
          <TableConfigButton table-id="department-list-table" />
        </div>

        <div class="table-container">
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
              <tr v-for="dept in departments" :key="dept.id">
                <td>{{ dept.name }}</td>
                <td>{{ dept.code }}</td>
                <td>{{ formatDateTime(dept.createdAt) }}</td>
                <td>{{ dept.createdBy || '-' }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditDepartmentModal(dept)">编辑</button>
                  <button class="action-link danger" type="button" @click="confirmDeleteDepartment(dept)">删除</button>
                </td>
              </tr>
              <tr v-if="departments.length === 0">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无部门数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span class="page-total">共 {{ departmentTotal }} 条结果</span>
          <button class="page-btn" type="button" :disabled="departmentFilterForm.page <= 1" @click="handleDepartmentPageChange(-1)">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="page-num">{{ departmentFilterForm.page }}</span>
          <button class="page-btn" type="button" :disabled="departmentFilterForm.page >= Math.ceil(departmentTotal / departmentFilterForm.size) || departmentTotal === 0" @click="handleDepartmentPageChange(1)">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <select class="size-select" v-model="departmentFilterForm.size" @change="loadDepartments">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </section>

  <!-- 部门弹窗 -->
  <div v-if="showDepartmentModal" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>{{ editingDepartment ? '编辑部门' : '新建部门' }}</h2>
        <button class="close-btn" type="button" @click="showDepartmentModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveDepartment">
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
            <div ref="userSelectTriggerRef" class="multiselect-trigger" @click.stop="openUserSelectDropdown">
              <span :class="{ placeholder: !departmentForm.userIds || departmentForm.userIds?.length === 0 }">
                {{ selectedUserNames }}
              </span>
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" :style="{ transform: showUserSelectDropdown ? 'rotate(180deg)' : '', transition: 'transform 0.2s' }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </div>
            <Teleport to="body">
              <div v-if="showUserSelectDropdown" class="multiselect-dropdown multiselect-dropdown-fixed" :style="dropdownStyle" @click.stop>
                <label v-for="u in allUsersList" :key="u.id" class="multiselect-option">
                  <input type="checkbox" :value="u.id" v-model="departmentForm.userIds" />
                  <span>{{ u.name }} ({{ u.username }})</span>
                </label>
                <div v-if="allUsersList.length === 0" style="padding:8px 12px;color:#aaa;font-size:13px">暂无人员</div>
              </div>
            </Teleport>
          </div>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showDepartmentModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

</template>
