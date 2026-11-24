<script setup lang="ts">
import { reactive, ref } from 'vue'
import {
  createSupplier,
  deleteSupplier,
  fetchSuppliers,
  updateSupplier,
  type SupplierInfo
} from '../api'
import { useMasterDataPage } from '../composables/useMasterDataPage'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import TableConfigButton from './TableConfigButton.vue'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const {
  filter, items: suppliers, total, page, pageSize, totalPages, loading, loadError, saving,
  deletion: { visible, title, message, loading: deleteLoading, handleConfirm, handleCancel },
  search, reset, goPage, retry, save, remove
} = useMasterDataPage({
  label: '供应商',
  list: query => fetchSuppliers(props.token, query),
  remove: id => deleteSupplier(props.token, id),
  notice: text => emit('notice', text)
})

const showModal = ref(false)
const editing = ref<SupplierInfo | null>(null)
const form = reactive({
  code: '',
  name: '',
  contactName: '',
  phone: '',
  address: '',
  remark: '',
  enabled: true
})

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
  if (saving.value || visible.value) return
  editing.value = null
  resetForm()
  showModal.value = true
}

function openEdit(item: SupplierInfo) {
  if (saving.value || visible.value) return
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

async function saveSupplier() {
  if (saving.value || !showModal.value) return
  if (!form.name.trim()) {
    emit('notice', '请输入供应商名称')
    return
  }
  const payload = {
    code: form.code.trim() || undefined,
    name: form.name.trim(),
    contactName: form.contactName.trim() || undefined,
    phone: form.phone.trim() || undefined,
    address: form.address.trim() || undefined,
    remark: form.remark.trim() || undefined,
    enabled: form.enabled
  }
  const id = editing.value?.id
  await save(
    () => id == null ? createSupplier(props.token, payload) : updateSupplier(props.token, id, payload),
    id != null,
    () => { showModal.value = false }
  )
}
</script>

<template>
  <section class="content">
    <div class="filters">
      <label>
        <span>供应商编码</span>
        <input v-model="filter.code" placeholder="请输入供应商编码" @keydown.enter="search" />
      </label>
      <label>
        <span>供应商名称</span>
        <input v-model="filter.name" placeholder="请输入供应商名称" @keydown.enter="search" />
      </label>
      <div class="filter-actions">
        <button class="primary-btn query-btn ui-icon-button" type="button" @click="search" title="查询供应商" aria-label="查询供应商"><UiIcon name="search" /></button>
        <button class="ghost-btn ui-icon-button" type="button" @click="reset" title="重置筛选" aria-label="重置筛选"><UiIcon name="reset" /></button>
      </div>
    </div>

    <div class="toolbar">
      <button class="primary-btn ui-icon-button" type="button" @click="openCreate" title="新建供应商" aria-label="新建供应商"><UiIcon name="plus" /></button>
      <span v-if="!loadError" class="muted">共 {{ total }} 个供应商</span>
      <TableConfigButton table-id="supplier-list-table" />
    </div>

    <div class="table-container" :aria-busy="loading">
      <table id="supplier-list-table" class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>供应商编码</th>
            <th>供应商名称</th>
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
          <tr v-else-if="loadError">
            <td colspan="9" class="empty-cell">
              <span role="alert">{{ loadError }}</span>
              <button class="action-link ui-icon-button" type="button" @click="retry" title="重试" aria-label="重试"><UiIcon name="refresh" /></button>
            </td>
          </tr>
          <tr v-else-if="suppliers.length === 0">
            <td colspan="9" class="empty-cell">暂无供应商</td>
          </tr>
          <tr v-for="(item, index) in suppliers" v-else :key="item.id">
            <td>{{ (page - 1) * pageSize + index + 1 }}</td>
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
              <button class="action-link ui-icon-button" type="button" @click="openEdit(item)" title="编辑供应商" aria-label="编辑供应商"><UiIcon name="edit" /></button>
              <button class="action-link danger ui-icon-button" type="button" @click="remove(item)" title="删除供应商" aria-label="删除供应商"><UiIcon name="trash" /></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <nav v-if="!loadError && total > 0" class="pagination" aria-label="供应商列表分页">
      <span class="page-info" aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
      <div class="page-controls">
        <button class="page-btn ui-icon-button" type="button" :disabled="loading || page <= 1" @click="goPage(page - 1)" title="上一页" aria-label="上一页"><UiIcon name="arrowLeft" /></button>
        <button class="page-btn ui-icon-button" type="button" :disabled="loading || page >= totalPages" @click="goPage(page + 1)" title="下一页" aria-label="下一页"><UiIcon name="arrowRight" /></button>
      </div>
    </nav>

    <div v-if="showModal" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 560px">
        <div class="modal-header">
          <h2>{{ editing ? '编辑供应商' : '新建供应商' }}</h2>
          <button class="close-btn ui-icon-button" type="button" :disabled="saving" @click="showModal = false" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
        </div>
        <div class="modal-body">
          <fieldset class="form-grid one-col" :disabled="saving">
            <label>
              <span>供应商编码</span>
              <input v-model="form.code" placeholder="留空自动生成" />
            </label>
            <label>
              <span>供应商名称</span>
              <input v-model="form.name" placeholder="请输入供应商名称" />
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
          </fieldset>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" :disabled="saving" @click="showModal = false">取消</button>
          <button class="primary-btn wide" type="button" :disabled="saving" @click="saveSupplier" title="保存供应商" aria-label="保存供应商">{{ saving ? '保存中...' : '保存' }}</button>
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
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
  grid-template-columns: 1fr;
}

textarea {
  resize: vertical;
  min-height: 72px;
}
</style>
