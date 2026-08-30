<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createCustomer,
  deleteCustomer,
  fetchCustomers,
  updateCustomer,
  type CustomerInfo
} from '../api'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import TableConfigButton from './TableConfigButton.vue'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading: deleteLoading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

const loading = ref(false)
const customers = ref<CustomerInfo[]>([])
const total = ref(0)
const showModal = ref(false)
const editing = ref<CustomerInfo | null>(null)
const filter = reactive({ code: '', name: '' })
const form = reactive({
  code: '',
  name: '',
  contactName: '',
  phone: '',
  address: '',
  remark: '',
  enabled: true
})

async function loadData() {
  loading.value = true
  try {
    const res = await fetchCustomers(props.token, {
      code: filter.code.trim() || undefined,
      name: filter.name.trim() || undefined,
      size: 500
    })
    customers.value = res.items
    total.value = res.total
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '客户列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    code: '',
    name: '',
    contactName: '',
    phone: '',
    address: '',
    remark: '',
    enabled: true
  })
}

function openCreate() {
  editing.value = null
  resetForm()
  showModal.value = true
}

function openEdit(item: CustomerInfo) {
  editing.value = item
  Object.assign(form, {
    code: item.code,
    name: item.name,
    contactName: item.contactName || '',
    phone: item.phone || '',
    address: item.address || '',
    remark: item.remark || '',
    enabled: item.enabled
  })
  showModal.value = true
}

async function saveCustomer() {
  if (!form.name.trim()) {
    emit('notice', '请输入客户名称')
    return
  }
  try {
    const payload = {
      code: form.code.trim() || undefined,
      name: form.name.trim(),
      contactName: form.contactName.trim() || undefined,
      phone: form.phone.trim() || undefined,
      address: form.address.trim() || undefined,
      remark: form.remark.trim() || undefined,
      enabled: form.enabled
    }
    if (editing.value) {
      await updateCustomer(props.token, editing.value.id, payload)
      emit('notice', '客户已更新')
    } else {
      await createCustomer(props.token, payload)
      emit('notice', '客户已创建')
    }
    showModal.value = false
    await loadData()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '保存失败')
  }
}

async function removeCustomer(item: CustomerInfo) {
  const ok = await confirm({ message: `确认删除客户「${item.name}」？` })
  if (!ok) return
  try {
    await deleteCustomer(props.token, item.id)
    emit('notice', '客户已删除')
    await loadData()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(loadData)
</script>

<template>
  <section class="content">
    <div class="filters">
      <label>
        <span>客户编码</span>
        <input v-model="filter.code" placeholder="请输入客户编码" @keydown.enter="loadData" />
      </label>
      <label>
        <span>客户名称</span>
        <input v-model="filter.name" placeholder="请输入客户名称" @keydown.enter="loadData" />
      </label>
      <div class="filter-actions">
        <button class="primary-btn query-btn" type="button" @click="loadData"><UiIcon name="search" />查询</button>
        <button class="ghost-btn" type="button" @click="filter.code = ''; filter.name = ''; loadData()"><UiIcon name="reset" />重置</button>
      </div>
    </div>

    <div class="toolbar">
      <button class="primary-btn" type="button" @click="openCreate"><UiIcon name="plus" />新建客户</button>
      <span class="muted">共 {{ total }} 个客户</span>
      <TableConfigButton table-id="customer-list-table" />
    </div>

    <div class="table-container">
      <table id="customer-list-table" class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>客户编码</th>
            <th>客户名称</th>
            <th>联系人</th>
            <th>电话</th>
            <th>地址</th>
            <th>状态</th>
            <th>更新时间</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="customers.length === 0">
            <td colspan="9" class="empty-cell">暂无客户</td>
          </tr>
          <tr v-for="(item, index) in customers" v-else :key="item.id">
            <td>{{ index + 1 }}</td>
            <td class="green-link">{{ item.code }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.contactName || '-' }}</td>
            <td>{{ item.phone || '-' }}</td>
            <td>{{ item.address || '-' }}</td>
            <td>
              <span class="status-badge" :class="{ disabled: !item.enabled }">{{ item.enabled ? '启用' : '停用' }}</span>
            </td>
            <td>{{ item.updatedAt || item.createdAt || '-' }}</td>
            <td class="action-cell">
              <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
              <button class="action-link danger" type="button" @click="removeCustomer(item)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 560px">
        <div class="modal-header">
          <h2>{{ editing ? '编辑客户' : '新建客户' }}</h2>
          <button class="close-btn" type="button" @click="showModal = false">x</button>
        </div>
        <div class="modal-body">
          <div class="form-grid one-col">
            <label>
              <span>客户编码</span>
              <input v-model="form.code" placeholder="留空自动生成" />
            </label>
            <label>
              <span>客户名称</span>
              <input v-model="form.name" placeholder="请输入客户名称" />
            </label>
            <label>
              <span>联系人</span>
              <input v-model="form.contactName" placeholder="请输入联系人" />
            </label>
            <label>
              <span>电话</span>
              <input v-model="form.phone" placeholder="请输入联系电话" />
            </label>
            <label>
              <span>地址</span>
              <input v-model="form.address" placeholder="请输入地址" />
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.enabled">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
            <label>
              <span>备注</span>
              <textarea v-model="form.remark" rows="3" placeholder="请输入备注"></textarea>
            </label>
          </div>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showModal = false">取消</button>
          <button class="primary-btn wide" type="button" @click="saveCustomer">保存</button>
        </div>
      </div>
    </div>

    <DeleteConfirmDialog
      v-model:visible="visible"
      :title="title"
      :message="message"
      :loading="deleteLoading"
      @confirm="handleConfirm"
      @update:visible="(val: boolean) => { if (!val) handleCancel() }"
    />
  </section>
</template>

<style scoped>
.empty-cell {
  text-align: center;
  color: #8a9693;
  padding: 32px 0;
}

.one-col {
  grid-template-columns: 1fr;
}

textarea {
  resize: vertical;
  min-height: 72px;
}
</style>
