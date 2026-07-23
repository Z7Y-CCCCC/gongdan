<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createWarehouse,
  deleteWarehouse,
  fetchWarehouses,
  updateWarehouse,
  type WarehouseInfo
} from '../api'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading: deleteLoading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

const loading = ref(false)
const warehouses = ref<WarehouseInfo[]>([])
const total = ref(0)
const showModal = ref(false)
const editing = ref<WarehouseInfo | null>(null)
const filter = reactive({ code: '', name: '' })
const form = reactive({
  code: '',
  name: '',
  location: '',
  remark: '',
  enabled: true
})

async function loadData() {
  loading.value = true
  try {
    const res = await fetchWarehouses(props.token, {
      code: filter.code.trim() || undefined,
      name: filter.name.trim() || undefined,
      size: 500
    })
    warehouses.value = res.items
    total.value = res.total
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '仓库列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    code: '',
    name: '',
    location: '',
    remark: '',
    enabled: true
  })
}

function openCreate() {
  editing.value = null
  resetForm()
  showModal.value = true
}

function openEdit(item: WarehouseInfo) {
  editing.value = item
  Object.assign(form, {
    code: item.code,
    name: item.name,
    location: item.location || '',
    remark: item.remark || '',
    enabled: item.enabled
  })
  showModal.value = true
}

async function saveWarehouse() {
  if (!form.name.trim()) {
    emit('notice', '请输入仓库名称')
    return
  }
  try {
    const payload = {
      code: form.code.trim() || undefined,
      name: form.name.trim(),
      location: form.location.trim() || undefined,
      remark: form.remark.trim() || undefined,
      enabled: form.enabled
    }
    if (editing.value) {
      await updateWarehouse(props.token, editing.value.id, payload)
      emit('notice', '仓库已更新')
    } else {
      await createWarehouse(props.token, payload)
      emit('notice', '仓库已创建')
    }
    showModal.value = false
    await loadData()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '保存失败')
  }
}

async function removeWarehouse(item: WarehouseInfo) {
  const ok = await confirm({ message: `确认删除仓库「${item.name}」？` })
  if (!ok) return
  try {
    await deleteWarehouse(props.token, item.id)
    emit('notice', '仓库已删除')
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
        <span>仓库编码</span>
        <input v-model="filter.code" placeholder="请输入仓库编码" @keydown.enter="loadData" />
      </label>
      <label>
        <span>仓库名称</span>
        <input v-model="filter.name" placeholder="请输入仓库名称" @keydown.enter="loadData" />
      </label>
      <div class="filter-actions">
        <button class="primary-btn query-btn" type="button" @click="loadData"><UiIcon name="search" />查询</button>
        <button class="ghost-btn" type="button" @click="filter.code = ''; filter.name = ''; loadData()"><UiIcon name="reset" />重置</button>
      </div>
    </div>

    <div class="toolbar">
      <button class="primary-btn" type="button" @click="openCreate"><UiIcon name="plus" />新建仓库</button>
      <span class="muted">共 {{ total }} 个仓库</span>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>仓库编码</th>
            <th>仓库名称</th>
            <th>位置</th>
            <th>备注</th>
            <th>状态</th>
            <th>更新时间</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="warehouses.length === 0">
            <td colspan="8" class="empty-cell">暂无仓库</td>
          </tr>
          <tr v-for="(item, index) in warehouses" v-else :key="item.id">
            <td>{{ index + 1 }}</td>
            <td class="green-link">{{ item.code }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.location || '-' }}</td>
            <td>{{ item.remark || '-' }}</td>
            <td>
              <span class="status-badge" :class="{ disabled: !item.enabled }">{{ item.enabled ? '启用' : '停用' }}</span>
            </td>
            <td>{{ item.updatedAt || item.createdAt || '-' }}</td>
            <td class="action-cell">
              <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
              <button class="action-link danger" type="button" @click="removeWarehouse(item)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 520px">
        <div class="modal-header">
          <h2>{{ editing ? '编辑仓库' : '新建仓库' }}</h2>
          <button class="close-btn" type="button" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-grid one-col">
            <label>
              <span>仓库编码</span>
              <input v-model="form.code" placeholder="留空自动生成" />
            </label>
            <label>
              <span>仓库名称</span>
              <input v-model="form.name" placeholder="请输入仓库名称" />
            </label>
            <label>
              <span>位置</span>
              <input v-model="form.location" placeholder="如：一号车间东侧" />
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
          <button class="primary-btn wide" type="button" @click="saveWarehouse">保存</button>
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
