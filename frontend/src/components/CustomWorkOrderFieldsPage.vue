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

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const fields = ref<CustomFieldInfo[]>([])
const view = ref<'list' | 'form'>('list')
const editing = ref<CustomFieldInfo | null>(null)
const showBatch = ref(false)
const batchText = ref('')

const form = reactive({
  name: '',
  fieldType: '',
  placeholder: '',
  displayMode: 'dropdown',
  required: false,
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
  { value: 'multiple_select', label: '复选框' }
]

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
    allowUserOptions: false,
    helperText: '',
    description: '',
    sortOrder: 0,
    options: []
  })
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
  const res = await fetchCustomFields(props.token, 'workorder')
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
  view.value = 'form'
}

watch(() => form.fieldType, (type) => {
  if ((type === 'single_select' || type === 'multiple_select') && form.options.length === 0) {
    form.options = [emptyOption()]
  }
})

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
  const options = form.options
    .filter(item => item.label.trim())
    .map(item => ({
      label: item.label.trim(),
      value: item.value?.trim() || item.label.trim(),
      color: item.color || null,
      defaultSelected: Boolean(item.defaultSelected)
    }))
  const payload = {
    scope: 'workorder',
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
  if (editing.value) {
    await updateCustomField(props.token, editing.value.id, payload)
    emit('notice', '字段已更新')
  } else {
    await createCustomField(props.token, payload)
    emit('notice', '字段已创建')
  }
  view.value = 'list'
  await loadFields()
}

async function removeField(item: CustomFieldInfo) {
  if (!window.confirm(`确认删除字段 ${item.name}？`)) return
  await deleteCustomField(props.token, item.id)
  emit('notice', '字段已删除')
  await loadFields()
}

onMounted(loadFields)
</script>

<template>
  <section class="content">
    <template v-if="view === 'list'">
      <div class="toolbar">
        <button class="primary-btn create-btn" type="button" @click="openCreate">添加新字段</button>
        <span class="muted">最多支持添加 80 个</span>
        <button class="toolbar-btn" type="button" style="margin-left:auto">列配置</button>
      </div>
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:80px">序号</th>
              <th>字段名称</th>
              <th>字段类型</th>
              <th>创建时间</th>
              <th>创建人</th>
              <th>最近更新时间</th>
              <th style="width:140px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in sortedFields" :key="item.id">
              <td>{{ index + 1 }}</td>
              <td>{{ item.name }}</td>
              <td>{{ fieldTypeLabel(item.fieldType) }}</td>
              <td>{{ item.createdAt || '-' }}</td>
              <td>{{ item.creator || '-' }}</td>
              <td>{{ item.updatedAt || '-' }}</td>
              <td class="action-cell">
                <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
                <button class="action-link danger" type="button" @click="removeField(item)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <div class="process-form-page custom-field-page">
        <h2 class="process-form-title">{{ editing ? '编辑自定义字段' : '创建自定义字段' }}</h2>
        <div class="process-form">
          <div class="process-field"><label class="process-label required">字段名称</label><input v-model="form.name" placeholder="请输入字段名称" /></div>
          <div class="process-field"><label class="process-label required">字段类型</label><select v-model="form.fieldType"><option value="">请选择</option><option v-for="item in fieldTypes" :key="item.value" :value="item.value">{{ item.label }}</option></select></div>
        </div>

        <div v-if="form.fieldType === 'single_select' || form.fieldType === 'multiple_select'" class="option-box">
          <div class="toolbar">
            <span class="toolbar-title">设置可选项</span>
            <button class="toolbar-btn" type="button" @click="addOption">新增可选项</button>
            <button class="toolbar-btn" type="button" @click="openBatch">批量编辑</button>
          </div>
          <table class="data-table">
            <thead><tr><th>可选项</th><th>可选项值</th><th>设为默认值</th><th>颜色</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="option in form.options" :key="option.id">
                <td><input v-model="option.label" placeholder="请输入" /></td>
                <td><input v-model="option.value" placeholder="默认等于名称" /></td>
                <td><input v-model="option.defaultSelected" type="checkbox" /></td>
                <td><input v-model="option.color" type="color" /></td>
                <td><button class="action-link danger" type="button" @click="removeOption(option.id)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="process-form">
          <div class="process-field"><label class="process-label">提示条件</label><input v-model="form.placeholder" placeholder="添加" /></div>
          <div class="process-field"><label class="process-label">显示方式</label><select v-model="form.displayMode"><option value="dropdown">下拉选择</option><option value="radio">单选按钮</option><option value="checkbox">复选按钮</option></select></div>
          <div class="process-field"><label class="process-label">引导文字</label><input v-model="form.helperText" placeholder="填写后显示在表单中" /></div>
          <div class="process-field"><label class="process-label">字段排序</label><input v-model.number="form.sortOrder" type="number" /></div>
          <div class="process-field full-width"><label class="process-label">提示说明</label><textarea v-model="form.description" maxlength="50"></textarea></div>
        </div>
        <div class="option-flags">
          <label><input v-model="form.required" type="checkbox" /> 必填</label>
          <label><input v-model="form.allowUserOptions" type="checkbox" /> 允许用户添加选项</label>
        </div>
        <div class="form-bottom-bar">
          <button class="ghost-btn wide" type="button" @click="view = 'list'">取消</button>
          <button class="primary-btn wide" type="button" @click="saveField">保存</button>
        </div>
      </div>
    </template>

    <div v-if="showBatch" class="modal-backdrop">
      <div class="modal batch-modal">
        <div class="modal-header">
          <h2>批量编辑</h2>
          <button class="close-btn" type="button" @click="showBatch = false">×</button>
        </div>
        <div style="padding:18px">
          <p class="muted">每个选项单独一行，多个选项之间用换行符分隔。</p>
          <textarea v-model="batchText" rows="12" style="width:100%"></textarea>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showBatch = false">取消</button>
          <button class="primary-btn wide" type="button" @click="applyBatch">确定</button>
        </div>
      </div>
    </div>
  </section>
</template>
