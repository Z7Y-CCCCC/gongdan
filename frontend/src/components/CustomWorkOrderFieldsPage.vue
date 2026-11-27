<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, type CSSProperties } from 'vue'
import {
  createCustomField,
  deleteCustomField,
  fetchCustomFields,
  updateCustomField,
  type CustomFieldInfo,
  type CustomFieldOption,
  fetchSystemImportLogs,
  createSystemImportLog
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import { useLatestRequest } from '../composables/useLatestRequest'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'
import SystemLogsDialog from '@/components/SystemLogsDialog.vue'
import UiIcon from '@/components/UiIcon.vue'
import ColumnSettingsPanel, { type ColumnSetting } from '@/components/ColumnSettingsPanel.vue'
import TableConfigButton from '@/components/TableConfigButton.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

const fields = ref<CustomFieldInfo[]>([])
const view = ref<'list' | 'form'>('list')
const editing = ref<CustomFieldInfo | null>(null)
const saving = ref(false)
const loadError = ref('')
const beginLoad = useLatestRequest()
const showBatch = ref(false)
const batchText = ref('')

// Column config
const allColumns: ColumnSetting[] = [
  { key: 'index', label: '序号', visible: true, width: 80, fixed: 'none' },
  { key: 'name', label: '字段名称', visible: true, width: 160, fixed: 'none' },
  { key: 'fieldType', label: '字段类型', visible: true, width: 140, fixed: 'none' },
  { key: 'createdAt', label: '创建时间', visible: true, width: 160, fixed: 'none' },
  { key: 'creator', label: '创建人', visible: true, width: 120, fixed: 'none' },
  { key: 'updatedAt', label: '最后更新时间', visible: true, width: 160, fixed: 'none' },
  { key: 'actions', label: '操作', visible: true, width: 140, fixed: 'right' }
]
const columns = ref<ColumnSetting[]>(allColumns.map(c => ({ ...c })))
const defaultColumnSettings = allColumns.map(c => ({ ...c }))
const showColumnConfig = ref(false)

const visibleColumns = computed(() => columns.value.filter(column => column.visible))

function customFieldColumnStyle(column: ColumnSetting, header = false): CSSProperties {
  const visible = visibleColumns.value
  const currentIndex = visible.findIndex(item => item.key === column.key)
  const left = visible.slice(0, currentIndex).filter(item => item.fixed === 'left').reduce((sum, item) => sum + item.width, 0)
  const right = visible.slice(currentIndex + 1).filter(item => item.fixed === 'right').reduce((sum, item) => sum + item.width, 0)
  const style: CSSProperties = { width: `${column.width}px`, minWidth: `${column.width}px` }
  if (column.fixed !== 'none') {
    style.position = 'sticky'
    style.zIndex = header ? 12 : 3
    style.background = header ? 'var(--table-head-bg)' : 'var(--table-row-bg)'
    if (column.fixed === 'left') style.left = `${left}px`
    if (column.fixed === 'right') style.right = `${right}px`
  }
  return style
}

function saveColumnSettings(settings: ColumnSetting[]) {
  columns.value = settings.map(column => ({ ...column }))
  showColumnConfig.value = false
}

// 导入导出与日志
const fieldFileInput = ref<HTMLInputElement | null>(null)
const showImportLogs = ref(false)
const importLogs = ref<any[]>([])

async function refreshImportLogs() {
  try {
    const res = await fetchSystemImportLogs(props.token, 'CUSTOM_FIELD_WO')
    importLogs.value = res.items.map(item => ({
      id: item.id,
      fileName: item.fileName,
      time: item.createdAt,
      count: item.successCount,
      status: item.status,
      operator: item.operatorName,
      errors: item.errors ? item.errors.split('\n') : []
    }))
  } catch (e) {
    // ignore
  }
}
const importing = ref(false)
const importProgress = reactive({
  current: 0,
  total: 0,
  success: 0,
  fail: 0
})

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
  { value: 'multiple_select', label: '复选框' },
  { value: 'reference', label: '关联对象' }
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
  const isCurrent = beginLoad()
  loadError.value = ''
  try {
    const res = await fetchCustomFields(props.token, 'workorder')
    if (isCurrent()) fields.value = res.items
  } catch (e: any) {
    if (!isCurrent()) return
    loadError.value = e.message || '加载字段失败'
    emit('notice', loadError.value)
  }
}

function openCreate() {
  if (saving.value) return
  editing.value = null
  resetForm()
  view.value = 'form'
}

function openEdit(item: CustomFieldInfo) {
  if (saving.value) return
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
      defaultSelected: item.defaultValues?.includes(option.value) || item.defaultValues?.includes(option.label) || Boolean(option.defaultSelected)
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

function handleDefaultChange(optionId: string, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (form.fieldType === 'single_select') {
    form.options.forEach(opt => {
      opt.defaultSelected = (opt.id === optionId) ? checked : false
    })
  }
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
  if (saving.value || loading.value || importing.value) return
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
  saving.value = true
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
  } finally {
    saving.value = false
  }
}

async function removeField(item: CustomFieldInfo) {
  if (loading.value || saving.value || importing.value) return
  const confirmed = await confirm({ message: `确认删除字段「${item.name}」？` })
  if (!confirmed) return
  loading.value = true
  try {
    await deleteCustomField(props.token, item.id)
    emit('notice', '字段已删除')
    visible.value = false
    await loadFields()
  } catch (e: any) {
    emit('notice', e.message || '删除失败')
  } finally {
    loading.value = false
    visible.value = false
  }
}

// 导出字段功能
function handleExportFields() {
  const headers = ['字段名称', '字段类型', '必填', '允许用户添加选项', '提示条件', '显示方式', '引导文字', '字段排序', '提示说明', '可选项列表']
  const rows = sortedFields.value.map(item => {
    const typeLabel = fieldTypes.find(t => t.value === item.fieldType)?.label || item.fieldType
    const isRequired = item.required ? '是' : '否'
    const isAllowUserOpts = item.allowUserOptions ? '是' : '否'
    const dispMode = item.displayMode === 'radio' ? '单选按钮' : item.displayMode === 'checkbox' ? '复选按钮' : '下拉选择'
    const optStr = item.options.map(o => o.label).join('、')
    return [
      item.name,
      typeLabel,
      isRequired,
      isAllowUserOpts,
      item.placeholder || '',
      dispMode,
      item.helperText || '',
      String(item.sortOrder || 0),
      item.description || '',
      optStr
    ]
  })
  
  const content = [headers, ...rows].map(row => row.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `自定义字段配置_工单_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  emit('notice', '导出成功')
}

// 导入字段功能
async function handleImportFields(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  
  const reader = new FileReader()
  reader.onload = async (evt) => {
    const text = evt.target?.result as string
    if (!text) return
    
    const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
    if (lines.length <= 1) {
      emit('notice', 'CSV文件内容为空')
      return
    }
    
    // 解析 CSV 行（考虑双引号包裹逗号的冲突）
    function parseCSVLine(line: string): string[] {
      const result: string[] = []
      let current = ''
      let inQuotes = false
      for (let i = 0; i < line.length; i++) {
        const char = line[i]
        if (char === '"') {
          if (inQuotes && line[i + 1] === '"') {
            current += '"'
            i++
          } else {
            inQuotes = !inQuotes
          }
        } else if (char === ',' && !inQuotes) {
          result.push(current)
          current = ''
        } else {
          current += char
        }
      }
      result.push(current)
      return result
    }
    
    const headers = parseCSVLine(lines[0])
    let nameIdx = -1, typeIdx = -1, reqIdx = -1, allowUserIdx = -1, placeholderIdx = -1, modeIdx = -1, helperIdx = -1, sortIdx = -1, descIdx = -1, optIdx = -1
    
    headers.forEach((h, j) => {
      const cleanH = h.trim()
      if (cleanH === '字段名称') nameIdx = j
      else if (cleanH === '字段类型') typeIdx = j
      else if (cleanH === '必填') reqIdx = j
      else if (cleanH === '允许用户添加选项') allowUserIdx = j
      else if (cleanH === '提示条件') placeholderIdx = j
      else if (cleanH === '显示方式') modeIdx = j
      else if (cleanH === '引导文字') helperIdx = j
      else if (cleanH === '字段排序') sortIdx = j
      else if (cleanH === '提示说明') descIdx = j
      else if (cleanH === '可选项列表') optIdx = j
    })
    
    if (nameIdx === -1 || typeIdx === -1) {
      emit('notice', '无法识别必要的字段表头（请包含「字段名称」、「字段类型」）')
      return
    }
    
    const fieldsToImport: any[] = []
    const parseErrors: string[] = []
    
    for (let i = 1; i < lines.length; i++) {
      const row = parseCSVLine(lines[i])
      if (row.length < 2) continue
      
      const name = row[nameIdx]?.trim() || ''
      if (!name) {
        parseErrors.push(`第 ${i + 1} 行：字段名称为空`)
        continue
      }
      
      const rawType = row[typeIdx]?.trim() || ''
      let fieldType = ''
      if (rawType.toLowerCase() === 'text' || rawType === '文本') fieldType = 'text'
      else if (rawType.toLowerCase() === 'number' || rawType === '数字') fieldType = 'number'
      else if (rawType.toLowerCase() === 'date' || rawType === '时间') fieldType = 'date'
      else if (rawType.toLowerCase() === 'file' || rawType === '附件') fieldType = 'file'
      else if (rawType.toLowerCase() === 'image' || rawType === '图片') fieldType = 'image'
      else if (rawType.toLowerCase() === 'single_select' || rawType === '单选框') fieldType = 'single_select'
      else if (rawType.toLowerCase() === 'multiple_select' || rawType === '复选框') fieldType = 'multiple_select'
      else if (rawType.toLowerCase() === 'reference' || rawType === '关联对象') fieldType = 'reference'
      
      if (!fieldType) {
        parseErrors.push(`第 ${i + 1} 行：未知的字段类型「${rawType}」`)
        continue
      }
      
      const required = row[reqIdx]?.trim() === '是' || row[reqIdx]?.trim() === 'true'
      const allowUserOptions = row[allowUserIdx]?.trim() === '是' || row[allowUserIdx]?.trim() === 'true'
      const placeholder = row[placeholderIdx]?.trim() || undefined
      
      const rawMode = row[modeIdx]?.trim() || ''
      let displayMode = 'dropdown'
      if (rawMode === '单选按钮' || rawMode.toLowerCase() === 'radio') displayMode = 'radio'
      else if (rawMode === '复选按钮' || rawMode.toLowerCase() === 'checkbox') displayMode = 'checkbox'
      
      const helperText = row[helperIdx]?.trim() || undefined
      const sortOrder = Number(row[sortIdx]?.trim() || 0)
      const description = row[descIdx]?.trim() || undefined
      
      const optStr = row[optIdx]?.trim() || ''
      const optArr = optStr.split(/[、;；,，]/).map(o => o.trim()).filter(Boolean)
      const options = optArr.map(o => ({
        label: o,
        value: o,
        color: null,
        defaultSelected: false
      }))
      
      fieldsToImport.push({
        lineNum: i + 1,
        payload: {
          scope: 'workorder',
          name,
          fieldType,
          placeholder,
          displayMode,
          required,
          allowUserOptions,
          helperText,
          description,
          sortOrder,
          options,
          defaultValues: []
        }
      })
    }
    
    importing.value = true
    importProgress.total = fieldsToImport.length + parseErrors.length
    importProgress.current = parseErrors.length
    importProgress.success = 0
    importProgress.fail = parseErrors.length
    
    const errorsList = [...parseErrors]
    
    for (const item of fieldsToImport) {
      try {
        await createCustomField(props.token, item.payload)
        importProgress.success++
      } catch (err: any) {
        importProgress.fail++
        errorsList.push(`第 ${item.lineNum} 行：创建失败 (${err.message || '网络异常'})`)
      } finally {
        importProgress.current++
      }
    }
    
    importing.value = false
    await loadFields()
    
    const status = errorsList.length === 0 ? '成功' : importProgress.success > 0 ? '部分成功' : '失败'
    try {
      await createSystemImportLog(props.token, {
        module: 'CUSTOM_FIELD_WO',
        fileName: file.name,
        totalCount: importProgress.total,
        successCount: importProgress.success,
        failCount: importProgress.fail,
        status,
        errors: errorsList.length > 0 ? errorsList.join('\n') : undefined
      })
      await refreshImportLogs()
    } catch (e) {
      // ignore
    }
    showImportLogs.value = true
    emit('notice', `导入完成：成功 ${importProgress.success} 条，失败 ${importProgress.fail} 条`)
  }
  reader.readAsText(file)
  target.value = ''
}

onMounted(async () => {
  await loadFields()
  try {
    await refreshImportLogs()
  } catch (e) {
    // ignore
  }
})
</script>

<template>
  <section class="content">
    <template v-if="view === 'list'">
      <div v-if="loadError" role="alert">{{ loadError }} <button type="button" @click="loadFields" title="重试" aria-label="重试" class="ui-icon-button"><UiIcon name="refresh" /></button></div>
      <div class="toolbar" style="margin-bottom: 16px">
        <button class="primary-btn ui-icon-button" type="button" @click="openCreate" title="添加新字段" aria-label="添加新字段"><UiIcon name="plus" /></button>
        <span class="muted" style="margin-left:8px">最多支持添加 80 个</span>

        <button class="toolbar-btn ui-icon-button" type="button" @click="handleExportFields" title="导出" aria-label="导出"><UiIcon name="download" /></button>
        <button class="toolbar-btn ui-icon-button" type="button" @click="fieldFileInput?.click()" title="导入" aria-label="导入"><UiIcon name="upload" /></button>
        <input type="file" ref="fieldFileInput" style="display:none" accept=".csv" @change="handleImportFields" />

        <div style="margin-left:auto; display:flex; gap:8px; align-items:center;">
          <button class="toolbar-btn" type="button" @click="showImportLogs = true; refreshImportLogs()" title="导入日志" aria-label="导入日志"><UiIcon name="file" />日志</button>
          <button class="table-config-trigger ui-icon-button" type="button" title="字段配置" aria-label="字段配置" @click="showColumnConfig = true"><UiIcon name="table-columns" :size="16" /></button>
        </div>
      </div>
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th v-for="column in visibleColumns" :key="column.key" :style="customFieldColumnStyle(column, true)">
                {{ column.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in sortedFields" :key="item.id">
              <td
                v-for="column in visibleColumns"
                :key="column.key"
                :class="{ 'action-cell': column.key === 'actions' }"
                :style="customFieldColumnStyle(column)"
              >
                <template v-if="column.key === 'index'">{{ index + 1 }}</template>
                <template v-else-if="column.key === 'name'">{{ item.name }}</template>
                <template v-else-if="column.key === 'fieldType'">{{ fieldTypeLabel(item.fieldType) }}</template>
                <template v-else-if="column.key === 'createdAt'">{{ item.createdAt || '-' }}</template>
                <template v-else-if="column.key === 'creator'">{{ item.creator || '-' }}</template>
                <template v-else-if="column.key === 'updatedAt'">{{ item.updatedAt || '-' }}</template>
                <template v-else-if="column.key === 'actions'">
                  <button class="action-link ui-icon-button" type="button" @click="openEdit(item)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
                  <button class="action-link danger ui-icon-button" type="button" @click="removeField(item)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
                </template>
              </td>
            </tr>
            <tr v-if="sortedFields.length === 0">
              <td :colspan="visibleColumns.length" style="text-align: center; color: #8a9693; padding: 32px 0;">
                暂无自定义字段
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
            <button class="toolbar-btn ui-icon-button" type="button" @click="addOption" title="新增可选项" aria-label="新增可选项"><UiIcon name="plus" /></button>
            <button class="toolbar-btn" type="button" @click="openBatch" title="批量编辑" aria-label="批量编辑"><UiIcon name="edit" />批量编辑</button>
            <TableConfigButton table-id="custom-work-order-options-table" />
          </div>
          <table id="custom-work-order-options-table" class="data-table">
            <thead><tr><th>可选项</th><th>可选项值</th><th>设为默认值</th><th>颜色</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="option in form.options" :key="option.id">
                <td><input v-model="option.label" placeholder="请输入" /></td>
                <td><input v-model="option.value" placeholder="默认等于名称" /></td>
                <td><input v-model="option.defaultSelected" type="checkbox" @change="handleDefaultChange(option.id, $event)" /></td>
                <td>
                  <select 
                    v-model="option.color" 
                    :style="{ borderLeft: option.color ? `8px solid ${option.color}` : '1px solid #d5dedb', paddingLeft: '8px', width: '110px', height: '38px', borderRadius: '4px', outline: 'none', border: '1px solid #d5dedb', boxSizing: 'border-box' }"
                  >
                    <option value="">无颜色</option>
                    <option value="#00b889" style="color: #00b889;">■ 清新绿</option>
                    <option value="#007aff" style="color: #007aff;">■ 常规蓝</option>
                    <option value="#ff9500" style="color: #ff9500;">■ 警告橙</option>
                    <option value="#ff3b30" style="color: #ff3b30;">■ 紧急红</option>
                    <option value="#af52de" style="color: #af52de;">■ 特别紫</option>
                    <option value="#8e8e93" style="color: #8e8e93;">■ 次要灰</option>
                  </select>
                </td>
                <td><button class="action-link danger ui-icon-button" type="button" @click="removeOption(option.id)" title="删除" aria-label="删除"><UiIcon name="trash" /></button></td>
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
          <button class="ghost-btn wide ui-icon-button" type="button" :disabled="saving" @click="view = 'list'" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" type="button" :disabled="saving" @click="saveField">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </template>

    <div v-if="showBatch" class="modal-backdrop">
      <div class="modal batch-modal">
        <div class="modal-header">
          <h2>批量编辑</h2>
          <button class="close-btn ui-icon-button" type="button" @click="showBatch = false" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
        </div>
        <div style="padding:18px">
          <p class="muted">每行一个选项。</p>
          <textarea v-model="batchText" rows="12" style="width:100%"></textarea>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide ui-icon-button" type="button" @click="showBatch = false" title="取消" aria-label="取消"><UiIcon name="close" /></button>
          <button class="primary-btn wide" type="button" @click="applyBatch">确定</button>
        </div>
      </div>
    </div>

    <ColumnSettingsPanel
      :visible="showColumnConfig"
      v-model="columns"
      :defaults="defaultColumnSettings"
      title="字段配置"
      @confirm="saveColumnSettings"
      @cancel="showColumnConfig = false"
    />

    <!-- 导入进度遮罩层 -->
    <div v-if="importing" class="import-progress-overlay">
      <div class="import-progress-card">
        <div class="import-spinner"></div>
        <div class="import-title">正在导入数据...</div>
        <div class="import-progress-bar-container">
          <div class="import-progress-bar" :style="{ width: (importProgress.current / importProgress.total * 100) + '%' }"></div>
        </div>
        <div class="import-stats">
          <span>已处理: {{ importProgress.current }} / {{ importProgress.total }}</span>
          <span class="success-text">成功: {{ importProgress.success }}</span>
          <span class="fail-text">失败: {{ importProgress.fail }}</span>
        </div>
      </div>
    </div>

    <!-- 导入日志毛玻璃弹窗 -->
    <SystemLogsDialog
      v-model:visible="showImportLogs"
      :token="token"
      module="CUSTOM_FIELD_WO"
      type="import"
      title="自定义字段导入日志"
    />

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
