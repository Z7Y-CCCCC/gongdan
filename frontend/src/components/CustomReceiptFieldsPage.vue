<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createCustomField,
  deleteCustomField,
  fetchCustomFields,
  updateCustomField,
  type CustomFieldInfo,
  type CustomFieldOption
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'

const props = defineProps<{ token: string; scope: string; scopeLabel: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

const fields = ref<CustomFieldInfo[]>([])
const view = ref<'list' | 'form'>('list')
const editing = ref<CustomFieldInfo | null>(null)
const showBatch = ref(false)
const batchText = ref('')

// Column config
const allColumns = [
  { key: 'index', label: '序号', visible: true },
  { key: 'name', label: '字段名称', visible: true },
  { key: 'fieldType', label: '字段类型', visible: true },
  { key: 'createdAt', label: '创建时间', visible: true },
  { key: 'creator', label: '创建人', visible: true },
  { key: 'updatedAt', label: '最后更新时间', visible: true },
  { key: 'actions', label: '操作', visible: true }
]
const columns = ref(allColumns.map(c => ({ ...c })))
const showColumnConfig = ref(false)

const form = reactive({
  name: '',
  fieldType: '',
  placeholder: '',
  displayMode: 'dropdown',
  required: false,
  readonly: false,
  multiline: false,
  allowScanInput: false,
  canModifyScanResult: false,
  allowUserOptions: false,
  helperText: '',
  description: '',
  sortOrder: 0,
  options: [] as Array<CustomFieldOption & { id: string }>
})

const sortedFields = computed(() =>
  fields.value.slice().sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
)

const fieldTypes = [
  { value: 'text', label: '文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '时间' },
  { value: 'file', label: '附件' },
  { value: 'image', label: '图片' },
  { value: 'single_select', label: '单选框' },
  { value: 'multiple_select', label: '复选框' },
  { value: 'reference', label: '关联对象' }
]

// Field type search
const fieldTypeSearchQuery = ref('')
const fieldTypeDropdownOpen = ref(false)

const filteredFieldTypes = computed(() => {
  if (!fieldTypeSearchQuery.value) return fieldTypes
  const q = fieldTypeSearchQuery.value.toLowerCase()
  return fieldTypes.filter(t => t.label.toLowerCase().includes(q))
})

const selectedFieldTypeLabel = computed(() => {
  if (!form.fieldType) return ''
  return fieldTypes.find(t => t.value === form.fieldType)?.label || form.fieldType
})

// Whether to show conditional options based on field type
const hasFieldType = computed(() => !!form.fieldType)
const isTextType = computed(() => form.fieldType === 'text')
const isSelectType = computed(() => form.fieldType === 'single_select' || form.fieldType === 'multiple_select')

function fieldTypeLabel(value: string) {
  return fieldTypes.find(item => item.value === value)?.label || value
}

function resetForm() {
  Object.assign(form, {
    name: '',
    fieldType: '',
    placeholder: '',
    displayMode: 'dropdown',
    required: false,
    readonly: false,
    multiline: false,
    allowScanInput: false,
    canModifyScanResult: false,
    allowUserOptions: false,
    helperText: '',
    description: '',
    sortOrder: 0,
    options: []
  })
  fieldTypeSearchQuery.value = ''
  fieldTypeDropdownOpen.value = false
}

function emptyOption(label = '') {
  return {
    id: `${Date.now()}_${Math.random()}`,
    label,
    value: label,
    color: '',
    defaultSelected: false
  }
}

async function loadFields() {
  const res = await fetchCustomFields(props.token, props.scope)
  fields.value = res.items
}

function openCreate() {
  editing.value = null
  resetForm()
  view.value = 'form'
}

function openEdit(item: CustomFieldInfo) {
  editing.value = item
  Object.assign(form, {
    name: item.name,
    fieldType: item.fieldType,
    placeholder: item.placeholder || '',
    displayMode: item.displayMode || 'dropdown',
    required: item.required,
    readonly: false,
    multiline: false,
    allowScanInput: false,
    canModifyScanResult: false,
    allowUserOptions: item.allowUserOptions,
    helperText: item.helperText || '',
    description: item.description || '',
    sortOrder: item.sortOrder || 0,
    options: item.options.map(option => ({
      id: `${Date.now()}_${Math.random()}`,
      label: option.label,
      value: option.value,
      color: option.color || '',
      defaultSelected: Boolean(option.defaultSelected)
    }))
  })
  fieldTypeSearchQuery.value = fieldTypeLabel(item.fieldType)
  view.value = 'form'
}

watch(() => form.fieldType, (type) => {
  if ((type === 'single_select' || type === 'multiple_select') && form.options.length === 0) {
    form.options = [emptyOption()]
  }
})

function selectFieldType(ft: { value: string; label: string }) {
  form.fieldType = ft.value
  fieldTypeSearchQuery.value = ft.label
  fieldTypeDropdownOpen.value = false
}

function addOption() {
  form.options.push(emptyOption())
}

function removeOption(id: string) {
  form.options = form.options.filter(item => item.id !== id)
}

function openBatch() {
  batchText.value = form.options.map(item => item.label).join('\n')
  showBatch.value = true
}

function applyBatch() {
  form.options = batchText.value
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(Boolean)
    .map(item => emptyOption(item))
  showBatch.value = false
}

async function saveField() {
  if (!form.name.trim()) {
    emit('notice', '请输入字段名称')
    return
  }
  if (!form.fieldType) {
    emit('notice', '请选择字段类型')
    return
  }
  const options = form.options
    .filter(item => item.label.trim())
    .map(item => ({
      label: item.label.trim(),
      value: item.value?.trim() || item.label.trim(),
      color: item.color || null,
      defaultSelected: Boolean(item.defaultSelected)
    }))
  const payload = {
    scope: props.scope,
    name: form.name,
    fieldType: form.fieldType,
    placeholder: form.placeholder || undefined,
    displayMode: form.displayMode || undefined,
    required: form.required,
    allowUserOptions: form.allowUserOptions,
    helperText: form.helperText || undefined,
    description: form.description || undefined,
    sortOrder: Number(form.sortOrder || 0),
    options,
    defaultValues: options.filter(item => item.defaultSelected).map(item => item.value)
  }
  try {
    if (editing.value) {
      await updateCustomField(props.token, editing.value.id, payload)
      emit('notice', '字段已更新')
    } else {
      await createCustomField(props.token, payload)
      emit('notice', '字段已创建')
    }
    view.value = 'list'
    await loadFields()
  } catch (e: any) {
    emit('notice', e.message || '保存失败')
  }
}

async function removeField(item: CustomFieldInfo) {
  const confirmed = await confirm({ message: `确认删除字段「${item.name}」？` })
  if (!confirmed) return
  loading.value = true
  try {
    await deleteCustomField(props.token, item.id)
    emit('notice', '字段已删除')
    await loadFields()
  } finally {
    loading.value = false
    visible.value = false
  }
}

onMounted(loadFields)
</script>

<template>
  <section class="content">
    <!-- List View -->
    <template v-if="view === 'list'">
      <div class="toolbar" style="margin-bottom: 16px">
        <button class="primary-btn" type="button" @click="openCreate">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          添加新字段
        </button>
        <span style="color:#8a9693; font-size:13px; margin-left:8px">最多支持添加80个</span>

        <div style="margin-left: auto; display:flex; gap: 8px; align-items:center;">
          <button class="toolbar-btn" type="button" @click="emit('notice', '表单提交校验规则功能开发中...')" style="color:#00b889">
            表单提交校验规则
          </button>
          <button class="text-btn" type="button" style="color:#586360;" @click="showColumnConfig = true">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none">
              <line x1="4" y1="21" x2="4" y2="14"></line>
              <line x1="4" y1="10" x2="4" y2="3"></line>
              <line x1="12" y1="21" x2="12" y2="12"></line>
              <line x1="12" y1="8" x2="12" y2="3"></line>
              <line x1="20" y1="21" x2="20" y2="16"></line>
              <line x1="20" y1="12" x2="20" y2="3"></line>
              <line x1="1" y1="14" x2="7" y2="14"></line>
              <line x1="9" y1="8" x2="15" y2="8"></line>
              <line x1="17" y1="16" x2="23" y2="16"></line>
            </svg>
            列配置
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th v-for="col in columns.filter(c => c.visible)" :key="col.key">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in sortedFields" :key="item.id">
              <td v-if="columns[0].visible">{{ index + 1 }}</td>
              <td v-if="columns[1].visible">{{ item.name }}</td>
              <td v-if="columns[2].visible">{{ fieldTypeLabel(item.fieldType) }}</td>
              <td v-if="columns[3].visible">{{ item.createdAt || '-' }}</td>
              <td v-if="columns[4].visible">{{ item.creator || '-' }}</td>
              <td v-if="columns[5].visible">{{ item.updatedAt || '-' }}</td>
              <td v-if="columns[6].visible" class="action-cell">
                <a class="link-btn" style="font-size:12px;cursor:pointer;margin-right:12px;color:#00b889" @click="openEdit(item)">编辑</a>
                <a class="link-btn" style="font-size:12px;cursor:pointer;color:#ff5a5a" @click="removeField(item)">删除</a>
              </td>
            </tr>
            <tr v-if="sortedFields.length === 0">
              <td :colspan="columns.filter(c => c.visible).length" style="text-align: center; color: #8a9693; padding: 32px 0;">
                暂无自定义字段
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination" v-if="sortedFields.length > 0" style="margin-top:16px">
        <span class="page-info">共{{ sortedFields.length }}条结果</span>
        <div class="page-controls">
          <button class="active">1</button>
        </div>
        <select class="page-size-select">
          <option :value="10">10 条/页</option>
        </select>
      </div>
    </template>

    <!-- Create / Edit Form View -->
    <template v-else>
      <div class="cf-page">
        <h3 class="cf-title">{{ editing ? '编辑自定义字段' : '创建自定义字段' }}</h3>

        <!-- 字段名称 -->
        <div class="cf-field-row">
          <div class="cf-field-label-cell">
            <span class="cf-asterisk">*</span>字段名称
          </div>
          <div class="cf-field-input-cell">
            <input class="cf-input" v-model="form.name" placeholder="请输入字段名称" />
          </div>
        </div>

        <!-- 字段类型 -->
        <div class="cf-field-row" style="position:relative">
          <div class="cf-field-label-cell">
            <span class="cf-asterisk">*</span>字段类型
          </div>
          <div class="cf-field-input-cell">
            <div class="cf-type-wrap">
              <input
                class="cf-input cf-select-trigger"
                v-model="fieldTypeSearchQuery"
                placeholder="请选择"
                @focus="fieldTypeDropdownOpen = true"
                @input="fieldTypeDropdownOpen = true"
              />
              <svg class="cf-select-arrow" viewBox="0 0 24 24" width="16" height="16" stroke="#bfc6c3" stroke-width="2" fill="none">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
              <div v-if="fieldTypeDropdownOpen" class="cf-type-dropdown">
                <div
                  v-for="ft in filteredFieldTypes"
                  :key="ft.value"
                  class="cf-type-item"
                  :class="{ active: form.fieldType === ft.value }"
                  @click="selectFieldType(ft)"
                >
                  {{ ft.label }}
                </div>
                <div v-if="filteredFieldTypes.length === 0" class="cf-type-empty">无匹配项</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Conditional fields: only shown after selecting a field type -->
        <template v-if="hasFieldType">
          <!-- Text: 允许扫码输入 -->
          <template v-if="isTextType">
            <div class="cf-field-row">
              <div class="cf-field-label-cell">允许扫码输入</div>
              <div class="cf-field-input-cell">
                <select class="cf-select" v-model="form.allowScanInput">
                  <option :value="false">否</option>
                  <option :value="true">是</option>
                </select>
              </div>
            </div>
            <div class="cf-field-row">
              <div class="cf-field-label-cell">可修改扫码结果</div>
              <div class="cf-field-input-cell">
                <select class="cf-select" v-model="form.canModifyScanResult">
                  <option :value="false">否</option>
                  <option :value="true">是</option>
                </select>
              </div>
            </div>
          </template>

          <!-- 隐藏条件 -->
          <div class="cf-field-row">
            <div class="cf-field-label-cell">隐藏条件</div>
            <div class="cf-field-input-cell">
              <button class="cf-add-btn" type="button" @click="emit('notice', '隐藏条件功能开发中...')">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" style="margin-right:4px;">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="16"></line>
                  <line x1="8" y1="12" x2="16" y2="12"></line>
                </svg>
                添加
              </button>
              <div class="cf-hint-msg">
                <svg viewBox="0 0 24 24" width="13" height="13" stroke="#bfc6c3" stroke-width="2" fill="none" class="cf-hint-icon">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="16" x2="12" y2="12"></line>
                  <line x1="12" y1="8" x2="12.01" y2="8"></line>
                </svg>
                <span>当满足此条件时字段隐藏</span>
              </div>
            </div>
          </div>

          <!-- 字段属性 -->
          <div class="cf-field-row">
            <div class="cf-field-label-cell">字段属性</div>
            <div class="cf-field-input-cell">
              <div class="cf-checkbox-row">
                <label class="cf-checkbox-item">
                  <input type="checkbox" v-model="form.readonly" class="cf-checkbox" />
                  <span class="cf-checkbox-label">只读</span>
                </label>
                <label v-if="isTextType" class="cf-checkbox-item">
                  <input type="checkbox" v-model="form.multiline" class="cf-checkbox" />
                  <span class="cf-checkbox-label">多行</span>
                </label>
                <label class="cf-checkbox-item">
                  <input type="checkbox" v-model="form.required" class="cf-checkbox" />
                  <span class="cf-checkbox-label">必填</span>
                </label>
              </div>
            </div>
          </div>

          <!-- 引导文字 -->
          <div class="cf-field-row">
            <div class="cf-field-label-cell">引导文字</div>
            <div class="cf-field-input-cell">
              <input class="cf-input" v-model="form.helperText" placeholder="填写完显示在框里" />
            </div>
          </div>

          <!-- 提示说明 -->
          <div class="cf-field-row cf-align-start">
            <div class="cf-field-label-cell" style="margin-top:6px;">提示说明</div>
            <div class="cf-field-input-cell">
              <div class="cf-textarea-wrap">
                <textarea class="cf-textarea" v-model="form.description" placeholder="" maxlength="50" rows="3"></textarea>
                <div class="cf-char-count">{{ (form.description || '').length }}/50</div>
              </div>
            </div>
          </div>
        </template>

        <!-- Select options section -->
        <div v-if="isSelectType" class="cf-options-section">
          <div class="toolbar" style="margin-bottom:16px">
            <span style="font-weight:600; font-size:14px">设置可选项</span>
            <button class="toolbar-btn" type="button" @click="addOption" style="margin-left:16px">新增可选项</button>
            <button class="toolbar-btn" type="button" @click="openBatch">批量编辑</button>
          </div>
          <table class="data-table">
            <thead>
              <tr>
                <th>可选项</th>
                <th>可选项值</th>
                <th>设为默认值</th>
                <th>颜色</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="option in form.options" :key="option.id">
                <td><input v-model="option.label" placeholder="请输入" class="cf-input" /></td>
                <td><input v-model="option.value" placeholder="默认等于名称" class="cf-input" /></td>
                <td><input v-model="option.defaultSelected" type="checkbox" style="accent-color:#00b889" /></td>
                <td><input v-model="option.color" type="color" style="width:32px; height:28px; border:none; cursor:pointer" /></td>
                <td><a style="font-size:12px;cursor:pointer;color:#ff5a5a" @click="removeOption(option.id)">删除</a></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Bottom actions -->
        <div class="cf-bottom">
          <button class="cf-btn-cancel" type="button" @click="view = 'list'">取消</button>
          <button class="cf-btn-save" type="button" @click="saveField">保存</button>
        </div>
      </div>
    </template>

    <!-- Column Config Modal -->
    <div v-if="showColumnConfig" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 480px;">
        <div class="modal-header">
          <h2>字段显示配置</h2>
          <button class="close-btn" type="button" @click="showColumnConfig = false">×</button>
        </div>
        <div style="padding: 24px;">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
            <label v-for="col in columns" :key="col.key" style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
              <input type="checkbox" v-model="col.visible" style="accent-color: #00b889; width: 16px; height: 16px;" :disabled="col.key === 'name' || col.key === 'actions'" />
              <span style="font-size: 14px; color: #1e2724;">{{ col.label }}</span>
            </label>
          </div>
          <div style="text-align: right; margin-top: 24px;">
            <button class="primary-btn" @click="showColumnConfig = false">确定</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Batch Edit Modal -->
    <div v-if="showBatch" class="modal-backdrop">
      <div class="modal workorder-modal" style="width: 480px;">
        <div class="modal-header">
          <h2>批量编辑</h2>
          <button class="close-btn" type="button" @click="showBatch = false">×</button>
        </div>
        <div style="padding:18px">
          <p style="color:#8a9693; font-size:13px; margin-bottom:12px">每个选项单独一行，多个选项之间用换行符分隔。</p>
          <textarea v-model="batchText" rows="12" class="cf-textarea" style="width:100%"></textarea>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn" style="margin-right:12px" @click="showBatch = false">取消</button>
          <button class="primary-btn" @click="applyBatch">确定</button>
        </div>
      </div>
    </div>

    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      v-model:visible="visible"
      :title="title"
      :message="message"
      :loading="loading"
      @confirm="handleConfirm"
      @update:visible="(val: boolean) => { if (!val) handleCancel() }"
    />
  </section>
</template>

<style scoped>
/* ===== Form Page Layout 改为横向对齐 ===== */
.cf-page {
  max-width: 620px;
  padding: 0px 10px 40px;
  background: transparent;
}

.cf-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e2724;
  margin: 0 0 32px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebf0ee;
}

.cf-field-row {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
}

.cf-field-row.cf-align-start {
  align-items: flex-start;
}

.cf-field-label-cell {
  width: 140px;
  font-size: 14px;
  color: #4a5552;
  text-align: right;
  padding-right: 18px;
  box-sizing: border-box;
  font-weight: 500;
  user-select: none;
}

.cf-asterisk {
  color: #ff5a5a;
  margin-right: 4px;
  font-weight: bold;
}

.cf-field-input-cell {
  flex: 1;
}

/* ===== 精美表单输入控件 ===== */
.cf-input, .cf-select {
  display: block;
  width: 100%;
  max-width: 440px;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  color: #1e2724;
  background: #fff;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.cf-input:focus, .cf-select:focus, .cf-textarea:focus {
  border-color: #34bfa3;
  box-shadow: 0 0 0 3px rgba(52, 191, 163, 0.15);
}

.cf-input::placeholder {
  color: #bfc6c3;
}

/* ===== Select 下拉箭头美化 ===== */
.cf-select {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  background: #fff url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='%23bfc6c3' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'></polyline></svg>") no-repeat right 12px center;
  background-size: 16px;
  padding-right: 36px !important;
  cursor: pointer;
}

/* ===== Field type searchable dropdown ===== */
.cf-type-wrap {
  position: relative;
  max-width: 440px;
}

.cf-select-trigger {
  cursor: pointer;
  padding-right: 36px !important;
}

.cf-select-arrow {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
}

.cf-type-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #c8e6c9;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  max-height: 200px;
  overflow-y: auto;
  z-index: 1050;
}

.cf-type-item {
  padding: 9px 16px;
  cursor: pointer;
  font-size: 14px;
  color: #1e2724;
  transition: background 0.15s;
}
.cf-type-item:hover {
  background: #e8f5e9;
}
.cf-type-item.active {
  background: #e0f2f1;
  color: #00897b;
  font-weight: 600;
}

.cf-type-empty {
  padding: 14px;
  text-align: center;
  color: #8a9693;
  font-size: 13px;
}

/* ===== 隐藏条件添加按钮 ===== */
.cf-add-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 16px;
  border: 1px solid #34bfa3;
  border-radius: 4px;
  background: #fff;
  color: #34bfa3;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.cf-add-btn:hover {
  background: rgba(52, 191, 163, 0.05);
  border-color: #2ca58c;
  color: #2ca58c;
}

.cf-hint-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8a9693;
  font-size: 13px;
  margin-top: 8px;
  font-weight: 400;
}

.cf-hint-icon {
  flex-shrink: 0;
  stroke: #bfc6c3;
}

/* ===== Checkbox Row ===== */
.cf-checkbox-row {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 38px;
}

.cf-checkbox-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.cf-checkbox {
  appearance: none;
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background-color: #fff;
  outline: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.cf-checkbox:hover {
  border-color: #34bfa3;
}

.cf-checkbox:checked {
  border-color: #34bfa3;
  background-color: #34bfa3;
}

.cf-checkbox:checked::after {
  content: '';
  width: 8px;
  height: 4px;
  border-left: 2px solid #fff;
  border-bottom: 2px solid #fff;
  transform: rotate(-45deg) translate(0.5px, -1px);
}

.cf-checkbox-label {
  font-size: 14px;
  color: #586360;
}

/* ===== Textarea ===== */
.cf-textarea-wrap {
  width: 100%;
  max-width: 440px;
  position: relative;
}

.cf-textarea {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  color: #1e2724;
  background: #fff;
  outline: none;
  box-sizing: border-box;
  resize: none;
  min-height: 80px;
  font-family: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.cf-char-count {
  text-align: right;
  color: #bfc6c3;
  font-size: 13px;
  margin-top: 4px;
}

/* ===== Options section ===== */
.cf-options-section {
  margin-top: 28px;
  border-top: 1px solid #dce3e0;
  padding-top: 24px;
}

/* ===== Bottom Actions ===== */
.cf-bottom {
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid #ebf0ee;
  display: flex;
  justify-content: center;
  gap: 16px;
  max-width: 620px;
}

.cf-btn-cancel {
  height: 38px;
  padding: 0 36px;
  border: 1px solid #bfc6c3;
  border-radius: 4px;
  background: #fff;
  color: #586360;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.cf-btn-cancel:hover {
  border-color: #8a9693;
  color: #1e2724;
}

.cf-btn-save {
  height: 38px;
  padding: 0 36px;
  border: none;
  border-radius: 4px;
  background: #34bfa3;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.cf-btn-save:hover {
  background: #2ca58c;
}

/* ===== Pagination ===== */
.page-size-select {
  border: 1px solid #dce3e0;
  border-radius: 4px;
  background: white;
  padding: 4px 8px;
  font-size: 12px;
  color: #586360;
  outline: none;
  cursor: pointer;
  height: 28px;
}
.page-size-select:hover {
  border-color: #34bfa3;
}
</style>
