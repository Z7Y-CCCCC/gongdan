<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createProduct,
  deleteProduct,
  fetchProcessRoutes,
  fetchProductOperationLogs,
  fetchProducts,
  fetchUnits,
  updateProduct,
  fetchProductCategories,
  createProductCategory,
  deleteProductCategory,
  fetchSystemImportLogs,
  createSystemImportLog,
  type ProcessRouteInfo,
  type ProductInfo,
  type ProductPayload,
  type UnitInfo,
  type ProductCategoryInfo
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'
import SystemLogsDialog from '@/components/SystemLogsDialog.vue'
import TableConfigButton from './TableConfigButton.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

type ProductTab = 'all' | 'self' | 'purchase' | 'outsource'

const products = ref<ProductInfo[]>([])
const units = ref<UnitInfo[]>([])
const routes = ref<ProcessRouteInfo[]>([])
const view = ref<'list' | 'form'>('list')
const editingProduct = ref<ProductInfo | null>(null)
const logs = ref<string[]>([])
const showLogs = ref(false)
const filter = reactive({ code: '', name: '', spec: '' })
const activeTab = ref<ProductTab>('all')
const form = reactive({
  code: '',
  name: '',
  unitId: '' as number | '',
  spec: '',
  attribute: '自制',
  routeId: '' as number | '',
  supplierName: '',
  inventoryQty: 0,
  maxStock: '',
  minStock: '',
  safetyStock: '',
  costPrice: '',
  salePrice: '',
  keyPart: false,
  manufactureMode: '',
  quantity: '',
  fixedWeight: '10',
  customerName: '',
  productType: ''
})

// === 新增分类功能相关变量与方法 ===
const categories = ref<ProductCategoryInfo[]>([])
const customProductTypes = computed(() => categories.value.map(c => c.name))
const activeProductType = ref<string>('all')
const showCategoryModal = ref(false)
const newCategoryName = ref('')

const allProductTypes = computed(() => {
  const set = new Set<string>()
  products.value.forEach(p => {
    if (p.productType) {
      set.add(p.productType)
    }
  })
  customProductTypes.value.forEach(t => {
    if (t) {
      set.add(t)
    }
  })
  return ['all', ...Array.from(set)]
})

const productsInActiveType = computed(() => {
  return products.value.filter(p => {
    if (activeProductType.value === 'all') return true
    return p.productType === activeProductType.value
  })
})

function selectProductType(cat: string) {
  activeProductType.value = cat
  activeTab.value = 'all'
}

function openAddCategory() {
  newCategoryName.value = ''
  showCategoryModal.value = true
}

async function handleSaveCategory() {
  const name = newCategoryName.value.trim()
  if (!name) {
    emit('notice', '请输入分类名称')
    return
  }
  if (allProductTypes.value.includes(name)) {
    emit('notice', '分类已存在')
    return
  }
  try {
    const newCat = await createProductCategory(props.token, { name })
    categories.value.push(newCat)
    activeProductType.value = name
    activeTab.value = 'all'
    showCategoryModal.value = false
    emit('notice', '分类添加成功')
  } catch (e: any) {
    emit('notice', e.message || '分类添加失败')
  }
}

async function removeCategory(cat: string, event: Event) {
  event.stopPropagation()
  const target = categories.value.find(c => c.name === cat)
  if (target) {
    try {
      await deleteProductCategory(props.token, target.id)
      categories.value = categories.value.filter(c => c.id !== target.id)
      emit('notice', '分类已删除')
    } catch (e: any) {
      emit('notice', e.message || '分类删除失败')
      return
    }
  }
  if (activeProductType.value === cat) {
    activeProductType.value = 'all'
    activeTab.value = 'all'
  }
}

function isPersistedCategory(cat: string) {
  return categories.value.some(category => category.name === cat)
}

const tabItems = computed(() => [
  { key: 'all', label: '全部', count: productsInActiveType.value.length },
  { key: 'self', label: '自制', count: productsInActiveType.value.filter(p => p.attribute === '自制').length },
  { key: 'purchase', label: '外购', count: productsInActiveType.value.filter(p => p.attribute === '外购').length },
  { key: 'outsource', label: '委外', count: productsInActiveType.value.filter(p => p.attribute === '委外').length }
] as Array<{ key: ProductTab; label: string; count: number }>)

const filteredProducts = computed(() => {
  let list = productsInActiveType.value.filter(item => {
    const tabOk = activeTab.value === 'all' ||
      (activeTab.value === 'self' && item.attribute === '自制') ||
      (activeTab.value === 'purchase' && item.attribute === '外购') ||
      (activeTab.value === 'outsource' && item.attribute === '委外')
    const codeOk = !filter.code || item.code.includes(filter.code)
    const nameOk = !filter.name || item.name.includes(filter.name)
    const specOk = !filter.spec || (item.spec || '').includes(filter.spec)
    return tabOk && codeOk && nameOk && specOk
  })
  if (productSortField.value) {
    const field = productSortField.value
    list = [...list].sort((a, b) => {
      const valA = (a[field as keyof ProductInfo] || '').toString()
      const valB = (b[field as keyof ProductInfo] || '').toString()
      const compare = valA.localeCompare(valB, 'zh-CN', { numeric: true })
      return productSortOrder.value === 'asc' ? compare : -compare
    })
  }
  return list
})

const totalInventory = computed(() =>
  filteredProducts.value.reduce((sum, item) => sum + Number(item.inventoryQty || 0), 0)
)

function resetForm() {
  Object.assign(form, {
    code: '',
    name: '',
    unitId: '',
    spec: '',
    attribute: '自制',
    routeId: '',
    supplierName: '',
    inventoryQty: 0,
    maxStock: '',
    minStock: '',
    safetyStock: '',
    costPrice: '',
    salePrice: '',
    keyPart: false,
    manufactureMode: '',
    quantity: '',
    fixedWeight: '10',
    customerName: '',
    productType: ''
  })
}

async function loadData() {
  const [productRes, unitRes, routeRes, categoryRes] = await Promise.all([
    fetchProducts(props.token, { size: 500 }),
    fetchUnits(props.token, { size: 500 }),
    fetchProcessRoutes(props.token, { size: 500 }),
    fetchProductCategories(props.token)
  ])
  products.value = productRes.items
  units.value = unitRes.items
  routes.value = routeRes.items
  categories.value = categoryRes
  
  try {
    await refreshProductImportLogs()
  } catch (e) {
    // ignore
  }
}

function openCreate() {
  editingProduct.value = null
  resetForm()
  view.value = 'form'
}

function openEdit(item: ProductInfo) {
  editingProduct.value = item
  Object.assign(form, {
    code: item.code,
    name: item.name,
    unitId: item.unitId,
    spec: item.spec || '',
    attribute: item.attribute || '自制',
    routeId: item.routeId || '',
    supplierName: item.supplierName || '',
    inventoryQty: item.inventoryQty || 0,
    maxStock: item.maxStock || '',
    minStock: item.minStock || '',
    safetyStock: item.safetyStock || '',
    costPrice: item.costPrice || '',
    salePrice: item.salePrice || '',
    keyPart: item.keyPart,
    manufactureMode: item.manufactureMode || '',
    quantity: item.quantity || '',
    fixedWeight: item.fixedWeight || '10',
    customerName: item.customerName || '',
    productType: item.productType || ''
  })
  view.value = 'form'
}

function copyProduct(item: ProductInfo) {
  openEdit(item)
  editingProduct.value = null
  form.code = ''
  form.name = `${item.name}-复制`
}

async function saveProduct() {
  if (!form.unitId) {
    emit('notice', '请选择库存单位')
    return
  }
  const payload: ProductPayload = {
    code: form.code || undefined,
    name: form.name,
    unitId: Number(form.unitId),
    spec: form.spec || undefined,
    attribute: form.attribute,
    routeId: form.routeId ? Number(form.routeId) : null,
    supplierName: form.supplierName || undefined,
    inventoryQty: Number(form.inventoryQty || 0),
    maxStock: form.maxStock || undefined,
    minStock: form.minStock || undefined,
    safetyStock: form.safetyStock || undefined,
    costPrice: form.costPrice || undefined,
    salePrice: form.salePrice || undefined,
    keyPart: form.keyPart,
    manufactureMode: form.manufactureMode || undefined,
    quantity: form.quantity || undefined,
    fixedWeight: form.fixedWeight || undefined,
    customerName: form.customerName || undefined,
    productType: form.productType || undefined
  }
  if (editingProduct.value) {
    await updateProduct(props.token, editingProduct.value.id, payload)
    emit('notice', '产品已更新')
  } else {
    await createProduct(props.token, payload)
    emit('notice', '产品已创建')
  }
  view.value = 'list'
  await loadData()
}

async function removeProduct(item: ProductInfo) {
  const confirmed = await confirm({ message: `确认删除产品「${item.name}」？` })
  if (!confirmed) return
  loading.value = true
  try {
    await deleteProduct(props.token, item.id)
    emit('notice', '产品已删除')
    await loadData()
  } catch (err: any) {
    const errorMsg = err.message || '删除失败，请稍后再试'
    if (
      errorMsg.includes('工单') ||
      errorMsg.includes('关联') ||
      errorMsg.includes('foreign key') ||
      errorMsg.includes('constraint') ||
      errorMsg.includes('referred') ||
      errorMsg.includes('WorkOrder') ||
      errorMsg.includes('work_order')
    ) {
      emit('notice', '删除失败：该产品已存在于工单中或有其他业务关联，无法删除')
    } else {
      emit('notice', `删除失败：${errorMsg}`)
    }
  } finally {
    loading.value = false
    visible.value = false
  }
}

async function openLogs() {
  const res = await fetchProductOperationLogs(props.token, { size: 200 })
  logs.value = res.items.map(item => `${item.createdAt} ${item.operatorName} ${item.message}`)
  showLogs.value = true
}

// 产品扩展交互状态
const productColumns = ref([
  { key: 'checkbox', label: '选择', visible: true },
  { key: 'inventoryQty', label: '库存数量', visible: true },
  { key: 'unitName', label: '单位', visible: true },
  { key: 'name', label: '产品名称', visible: true },
  { key: 'code', label: '产品编号', visible: true },
  { key: 'routeName', label: '工艺路线', visible: true },
  { key: 'supplierName', label: '默认供应商', visible: true },
  { key: 'action', label: '操作', visible: true }
])
const productRowHeight = ref<'compact' | 'default' | 'relaxed'>('default')
const showProductRowHeightConfig = ref(false)

const productSortField = ref<'inventoryQty' | 'unitName' | 'name' | 'code' | 'routeName' | 'supplierName' | ''>('')
const productSortOrder = ref<'asc' | 'desc'>('asc')
const showProductSortConfig = ref(false)

const productFileInput = ref<HTMLInputElement | null>(null)
const showProductImportLogs = ref(false)
interface ImportLogItem {
  id: string
  fileName: string
  time: string
  count: number
  status: string
  operator: string
  errors?: string[]
}
const productImportLogs = ref<ImportLogItem[]>([])

async function refreshProductImportLogs() {
  try {
    const res = await fetchSystemImportLogs(props.token, 'PRODUCT')
    productImportLogs.value = res.items.map(item => ({
      id: String(item.id),
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
const productImporting = ref(false)
const productImportProgress = reactive({ total: 0, current: 0, success: 0, fail: 0 })

function isProductColumnVisible(key: string) {
  return productColumns.value.find(c => c.key === key)?.visible ?? true
}

function handleExportProducts() {
  if (filteredProducts.value.length === 0) {
    emit('notice', '当前无产品数据可供导出')
    return
  }
  const headers = '产品编号,产品名称,库存单位,产品规格,产品属性,工艺路线,默认供应商,库存数量\n'
  const rows = filteredProducts.value.map(p => {
    const code = p.code ? p.code.replace(/"/g, '""') : ''
    const name = p.name ? p.name.replace(/"/g, '""') : ''
    const unitName = p.unitName ? p.unitName.replace(/"/g, '""') : ''
    const spec = p.spec ? p.spec.replace(/"/g, '""') : ''
    const attribute = p.attribute ? p.attribute.replace(/"/g, '""') : ''
    const routeName = p.routeName ? p.routeName.replace(/"/g, '""') : ''
    const supplierName = p.supplierName ? p.supplierName.replace(/"/g, '""') : ''
    const inventoryQty = p.inventoryQty || 0
    return `"${code}","${name}","${unitName}","${spec}","${attribute}","${routeName}","${supplierName}",${inventoryQty}`
  }).join('\n')
  
  const blob = new Blob(['\uFEFF' + headers + rows], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `产品列表_${new Date().toLocaleDateString().replace(/\//g, '-')}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  emit('notice', '导出成功')
}

function triggerProductImport() {
  if (productFileInput.value) {
    productFileInput.value.value = ''
    productFileInput.value.click()
  }
}

async function handleImportProducts(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  const file = input.files[0]
  
  const reader = new FileReader()
  reader.onload = async (e) => {
    const text = e.target?.result as string
    if (!text) return
    
    const lines: string[][] = []
    let row: string[] = []
    let insideQuote = false
    let currentToken = ''
    
    for (let i = 0; i < text.length; i++) {
      const char = text[i]
      const nextChar = text[i + 1]
      
      if (insideQuote) {
        if (char === '"') {
          if (nextChar === '"') {
            currentToken += '"'
            i++
          } else {
            insideQuote = false
          }
        } else {
          currentToken += char
        }
      } else {
        if (char === '"') {
          insideQuote = true
        } else if (char === ',') {
          row.push(currentToken.trim())
          currentToken = ''
        } else if (char === '\r' || char === '\n') {
          if (char === '\r' && nextChar === '\n') {
            i++
          }
          row.push(currentToken.trim())
          lines.push(row)
          row = []
          currentToken = ''
        } else {
          currentToken += char
        }
      }
    }
    if (currentToken || row.length > 0) {
      row.push(currentToken.trim())
      lines.push(row)
    }
    
    if (lines.length <= 1) {
      emit('notice', 'CSV 文件格式不正确或无数据')
      return
    }
    
    const header = lines[0]
    let codeIdx = -1
    let nameIdx = -1
    let unitIdx = -1
    let specIdx = -1
    let attrIdx = -1
    let routeIdx = -1
    let supplierIdx = -1
    let qtyIdx = -1
    
    for (let j = 0; j < header.length; j++) {
      const h = header[j]
      if (h.includes('编号') || h.toLowerCase().includes('code')) codeIdx = j
      if (h.includes('名称') || h.toLowerCase().includes('name')) nameIdx = j
      if (h.includes('单位') || h.toLowerCase().includes('unit')) unitIdx = j
      if (h.includes('规格') || h.toLowerCase().includes('spec')) specIdx = j
      if (h.includes('属性') || h.toLowerCase().includes('attr')) attrIdx = j
      if (h.includes('工艺') || h.toLowerCase().includes('route')) routeIdx = j
      if (h.includes('供应商') || h.toLowerCase().includes('supplier')) supplierIdx = j
      if (h.includes('数量') || h.toLowerCase().includes('qty') || h.includes('库存')) qtyIdx = j
    }
    
    if (nameIdx === -1) {
      emit('notice', '未识别到“产品名称”列')
      return
    }
    
    const dataRows = lines.slice(1).filter(r => r.length > 0 && r.some(cell => cell.trim() !== ''))
    if (dataRows.length === 0) {
      emit('notice', '导入文件中无有效数据行')
      return
    }
    
    const importErrors: string[] = []
    let rowIndex = 1

    productImportProgress.total = dataRows.length
    productImportProgress.current = 0
    productImportProgress.success = 0
    productImportProgress.fail = 0
    productImporting.value = true
    
    for (const r of dataRows) {
      rowIndex++
      const name = r[nameIdx]
      if (!name) {
        productImportProgress.fail++
        productImportProgress.current++
        importErrors.push(`第${rowIndex}行: 产品名称为空`)
        continue
      }
      
      const code = codeIdx !== -1 ? r[codeIdx] : ''
      const unitName = unitIdx !== -1 ? r[unitIdx] : ''
      const spec = specIdx !== -1 ? r[specIdx] : ''
      const attribute = attrIdx !== -1 ? r[attrIdx] : '自制'
      const routeName = routeIdx !== -1 ? r[routeIdx] : ''
      const supplierName = supplierIdx !== -1 ? r[supplierIdx] : ''
      const qtyStr = qtyIdx !== -1 ? r[qtyIdx] : '0'
      
      let matchedUnit = units.value.find(u => u.name === unitName)
      let unitId = matchedUnit ? matchedUnit.id : (units.value[0] ? units.value[0].id : 1)
      
      let matchedRoute = routes.value.find(rt => rt.name === routeName)
      let routeId = matchedRoute ? matchedRoute.id : null
      
      const payload = {
        code: code || undefined,
        name: name,
        unitId: Number(unitId),
        spec: spec || undefined,
        attribute: ['自制', '外购', '委外'].includes(attribute) ? attribute : '自制',
        routeId: routeId,
        supplierName: supplierName || undefined,
        inventoryQty: Number(qtyStr || 0),
        keyPart: false
      }
      
      try {
        await createProduct(props.token, payload)
        productImportProgress.success++
      } catch (err: any) {
        console.error('产品导入失败', err, name)
        productImportProgress.fail++
        const errorMsg = err.message || '网络或接口异常'
        importErrors.push(`第${rowIndex}行[${name}]: ${errorMsg}`)
      }
      productImportProgress.current++
    }
    
    productImporting.value = false
    await loadData()
    
    try {
      await createSystemImportLog(props.token, {
        module: 'PRODUCT',
        fileName: file.name,
        totalCount: productImportProgress.total,
        successCount: productImportProgress.success,
        failCount: productImportProgress.fail,
        status: productImportProgress.fail === 0 ? '成功' : productImportProgress.success > 0 ? '部分成功' : '失败',
        errors: importErrors.length > 0 ? importErrors.join('\n') : undefined
      })
      await refreshProductImportLogs()
    } catch (e) {
      // ignore
    }
    
    let noticeMsg = `导入完成：成功 ${productImportProgress.success} 条，失败 ${productImportProgress.fail} 条`
    if (importErrors.length > 0) {
      noticeMsg += `。失败原因：${importErrors.slice(0, 2).join('；')}${importErrors.length > 2 ? ' 等' : ''}`
    }
    emit('notice', noticeMsg)
  }
  
  reader.readAsText(file, 'utf-8')
}

onMounted(loadData)
</script>

<template>
  <section class="content">
    <template v-if="view === 'list'">
      <div class="category-row">
        <div class="category-tabs">
          <button
            v-for="cat in allProductTypes"
            :key="cat"
            class="category-tab"
            :class="{ active: activeProductType === cat }"
            type="button"
            @click="selectProductType(cat)"
          >
            <!-- 全部：四宫格图标 -->
            <svg v-if="cat === 'all'" class="cat-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="7" height="7"></rect>
              <rect x="14" y="3" width="7" height="7"></rect>
              <rect x="14" y="14" width="7" height="7"></rect>
              <rect x="3" y="14" width="7" height="7"></rect>
            </svg>
            <!-- 其它：文件夹图标 -->
            <svg v-else class="cat-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
            </svg>
            <span class="cat-label">{{ cat === 'all' ? '全部' : cat }}</span>
            <span v-if="isPersistedCategory(cat)" class="cat-delete" title="删除此分类" @click="removeCategory(cat, $event)">×</span>
          </button>
          <button class="add-cat-btn" type="button" @click="openAddCategory" title="新增分类">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </button>
        </div>
      </div>

      <div class="tab-row">
        <button
          v-for="tab in tabItems"
          :key="tab.key"
          class="tab-pill"
          :class="{ active: activeTab === tab.key }"
          type="button"
          @click="activeTab = tab.key"
        >
          {{ tab.label }} {{ tab.count }}
        </button>
      </div>

      <div class="filters">
        <label><span>产品编号</span><input v-model="filter.code" placeholder="请输入产品编号" /></label>
        <label><span>产品名称</span><input v-model="filter.name" placeholder="请输入产品名称" /></label>
        <label><span>产品规格</span><input v-model="filter.spec" placeholder="请输入产品规格" /></label>
      </div>

      <div class="toolbar">
        <button class="primary-btn create-btn" type="button" @click="openCreate">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="16"></line><line x1="8" y1="12" x2="16" y2="12"></line></svg>
          创建产品
        </button>
        
        <button class="toolbar-btn" type="button" @click="handleExportProducts">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
          导出
        </button>
        
        <button class="toolbar-btn" type="button" @click="triggerProductImport">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          导入
        </button>
        <input type="file" ref="productFileInput" style="display:none" accept=".csv" @change="handleImportProducts" />

        <TableConfigButton table-id="product-list-table" />

        <!-- 排序配置下拉菜单 -->
        <div class="toolbar-dropdown-container">
          <button class="toolbar-btn" type="button" @click="showProductSortConfig = !showProductSortConfig; showProductRowHeightConfig = false" :class="{ 'active-btn': showProductSortConfig }">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>
            排序
          </button>
          <div v-if="showProductSortConfig" class="dropdown-bubble sort-bubble">
            <div class="dropdown-header">列表排序配置</div>
            <div class="dropdown-content">
              <div class="dropdown-section-title">排序字段</div>
              <div class="radio-group">
                <label class="dropdown-item">
                  <input type="radio" value="" v-model="productSortField" />
                  <span>无 (默认)</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="inventoryQty" v-model="productSortField" />
                  <span>库存数量</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="unitName" v-model="productSortField" />
                  <span>单位</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="name" v-model="productSortField" />
                  <span>产品名称</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="code" v-model="productSortField" />
                  <span>产品编号</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="routeName" v-model="productSortField" />
                  <span>工艺路线</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="supplierName" v-model="productSortField" />
                  <span>默认供应商</span>
                </label>
              </div>
              <div v-if="productSortField" class="dropdown-section-title" style="margin-top:10px">排序规则</div>
              <div v-if="productSortField" class="radio-group">
                <label class="dropdown-item">
                  <input type="radio" value="asc" v-model="productSortOrder" />
                  <span>升序</span>
                </label>
                <label class="dropdown-item">
                  <input type="radio" value="desc" v-model="productSortOrder" />
                  <span>降序</span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <!-- 行高配置下拉菜单 -->
        <div class="toolbar-dropdown-container">
          <button class="toolbar-btn" type="button" @click="showProductRowHeightConfig = !showProductRowHeightConfig; showProductSortConfig = false" :class="{ 'active-btn': showProductRowHeightConfig }">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="21" y1="10" x2="3" y2="10"/><line x1="21" y1="6" x2="3" y2="6"/><line x1="21" y1="14" x2="3" y2="14"/><line x1="21" y1="18" x2="3" y2="18"/></svg>
            行高
          </button>
          <div v-if="showProductRowHeightConfig" class="dropdown-bubble row-height-bubble">
            <div class="dropdown-header">表格行高配置</div>
            <div class="dropdown-content">
              <label class="dropdown-item">
                <input type="radio" value="compact" v-model="productRowHeight" />
                <span>紧凑</span>
              </label>
              <label class="dropdown-item">
                <input type="radio" value="default" v-model="productRowHeight" />
                <span>默认</span>
              </label>
              <label class="dropdown-item">
                <input type="radio" value="relaxed" v-model="productRowHeight" />
                <span>宽松</span>
              </label>
            </div>
          </div>
        </div>

        <button class="toolbar-btn" type="button" style="margin-left:auto; margin-right:8px" @click="openLogs">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          操作记录
        </button>
        <button class="toolbar-btn" type="button" @click="showProductImportLogs = true; refreshProductImportLogs()">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          导入日志
        </button>
      </div>

      <div class="table-container">
        <table id="product-list-table" class="data-table">
          <thead>
            <tr>
              <th v-if="isProductColumnVisible('checkbox')" style="width:50px"><input type="checkbox" /></th>
              <th v-if="isProductColumnVisible('inventoryQty')">库存数量</th>
              <th v-if="isProductColumnVisible('unitName')">单位</th>
              <th v-if="isProductColumnVisible('name')">产品名称</th>
              <th v-if="isProductColumnVisible('code')">产品编号</th>
              <th v-if="isProductColumnVisible('routeName')">工艺路线</th>
              <th v-if="isProductColumnVisible('supplierName')">默认供应商</th>
              <th v-if="isProductColumnVisible('action')" style="width:190px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredProducts" :key="item.id" :class="['product-row', 'row-height-' + productRowHeight]">
              <td v-if="isProductColumnVisible('checkbox')"><input type="checkbox" /></td>
              <td v-if="isProductColumnVisible('inventoryQty')">{{ item.inventoryQty }}</td>
              <td v-if="isProductColumnVisible('unitName')">{{ item.unitName }}</td>
              <td v-if="isProductColumnVisible('name')">{{ item.name }}</td>
              <td v-if="isProductColumnVisible('code')" class="green-link">{{ item.code }}</td>
              <td v-if="isProductColumnVisible('routeName')">{{ item.routeName || '-' }}</td>
              <td v-if="isProductColumnVisible('supplierName')">{{ item.supplierName || '-' }}</td>
              <td v-if="isProductColumnVisible('action')" class="action-cell">
                <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
                <button class="action-link" type="button">打印</button>
                <button class="action-link" type="button" @click="copyProduct(item)">复制</button>
                <button class="action-link danger" type="button" @click="removeProduct(item)">删除</button>
              </td>
            </tr>
            <tr v-if="filteredProducts.length === 0">
              <td :colspan="productColumns.filter(c => c.visible).length" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td :colspan="productColumns.filter(c => c.visible).length">合计 {{ totalInventory }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>

    <template v-else>
      <div class="process-form-page product-form-page">
        <h2 class="process-form-title">{{ editingProduct ? '编辑产品' : '创建产品' }}</h2>
        <div class="process-form">
          <div class="process-field"><label class="process-label">产品编号</label><input v-model="form.code" placeholder="请输入，忽略将自动生成" /></div>
          <div class="process-field"><label class="process-label required">产品名称</label><input v-model="form.name" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label required">库存单位</label><select v-model="form.unitId"><option value="">请选择</option><option v-for="u in units" :key="u.id" :value="u.id">{{ u.name }}</option></select></div>
          <div class="process-field"><label class="process-label">产品规格</label><input v-model="form.spec" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label required">产品属性</label><select v-model="form.attribute"><option value="自制">自制</option><option value="外购">外购</option><option value="委外">委外</option></select></div>
          <div class="process-field"><label class="process-label">工艺路线</label><select v-model="form.routeId"><option value="">请选择</option><option v-for="r in routes" :key="r.id" :value="r.id">{{ r.name }}</option></select></div>
          <div class="process-field"><label class="process-label">默认供应商</label><input v-model="form.supplierName" placeholder="请选择" /></div>
          <div class="process-field"><label class="process-label">最大库存</label><input v-model="form.maxStock" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label">最小库存</label><input v-model="form.minStock" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label">安全库存</label><input v-model="form.safetyStock" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label">库存数量</label><input v-model.number="form.inventoryQty" type="number" /></div>
          <div class="process-field"><label class="process-label">成本单价</label><input v-model="form.costPrice" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label">销售单价</label><input v-model="form.salePrice" placeholder="请输入" /></div>
          <div class="process-field"><label class="process-label">关键部件</label><select v-model="form.keyPart"><option :value="false">否</option><option :value="true">是</option></select></div>
          <div class="process-field"><label class="process-label">制造模式</label><input v-model="form.manufactureMode" placeholder="请选择" /></div>
          <div class="process-field"><label class="process-label">数量</label><input v-model="form.quantity" placeholder="请填写" /></div>
          <div class="process-field"><label class="process-label">固定单重量</label><input v-model="form.fixedWeight" /></div>
          <div class="process-field"><label class="process-label">客户名称</label><input v-model="form.customerName" placeholder="请填写" /></div>
          <div class="process-field">
            <label class="process-label">产品类型</label>
            <select v-model="form.productType">
              <option value="">无分类</option>
              <option v-for="cat in allProductTypes.filter(c => c !== 'all')" :key="cat" :value="cat">
                {{ cat }}
              </option>
            </select>
          </div>
        </div>
        <div class="form-bottom-bar">
          <button class="ghost-btn wide" type="button" @click="view = 'list'">取消</button>
          <button class="primary-btn wide" type="button" @click="saveProduct">保存</button>
        </div>
      </div>
    </template>

    <SystemLogsDialog
      v-model:visible="showLogs"
      :token="token"
      module="PRODUCT"
      type="operation"
      title="产品操作记录"
    />

    <DeleteConfirmDialog
      v-model:visible="visible"
      :title="title"
      :message="message"
      :loading="loading"
      @confirm="handleConfirm"
      @update:visible="val => { if (!val) handleCancel() }"
    />

    <!-- 新增大分类 Modal 弹窗 -->
    <div v-if="showCategoryModal" class="category-modal-backdrop" @click.self="showCategoryModal = false">
      <div class="category-modal">
        <div class="category-modal-header">
          <h3>新增分类</h3>
          <button class="close-btn" type="button" @click="showCategoryModal = false">×</button>
        </div>
        <div class="category-modal-body">
          <div class="category-input-group">
            <label>分类名称</label>
            <input v-model="newCategoryName" placeholder="请输入分类名称" @keyup.enter="handleSaveCategory" autofocus />
          </div>
        </div>
        <div class="category-modal-footer">
          <button class="ghost-btn" type="button" @click="showCategoryModal = false">取消</button>
          <button class="primary-btn" type="button" @click="handleSaveCategory">确定</button>
        </div>
      </div>
    </div>

    <!-- 导入进度遮罩层 -->
    <div v-if="productImporting" class="import-progress-overlay">
      <div class="import-progress-card">
        <div class="import-spinner"></div>
        <div class="import-title">正在导入产品...</div>
        <div class="import-progress-bar-container">
          <div class="import-progress-bar" :style="{ width: (productImportProgress.total > 0 ? (productImportProgress.current / productImportProgress.total * 100) : 0) + '%' }"></div>
        </div>
        <div class="import-stats">
          <span>已处理: {{ productImportProgress.current }} / {{ productImportProgress.total }}</span>
          <span class="success-text">成功: {{ productImportProgress.success }}</span>
          <span class="fail-text">失败: {{ productImportProgress.fail }}</span>
        </div>
      </div>
    </div>

    <!-- 导入日志毛玻璃弹窗 -->
    <SystemLogsDialog
      v-model:visible="showProductImportLogs"
      :token="token"
      module="PRODUCT"
      type="import"
      title="产品导入日志"
    />
  </section>
</template>

<style scoped>
/* 顶部大分类视觉增强 */
.category-row {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  padding: 4px 0;
}

.category-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.category-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid #e1e8e6;
  background-color: #ffffff;
  color: #5c6764;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  user-select: none;
}

.category-tab:hover {
  border-color: #00b889;
  color: #00b889;
  background-color: #f2faf8;
}

.category-tab.active {
  border-color: #00b889;
  background-color: #e6f6f3;
  color: #00b889;
  box-shadow: 0 2px 8px rgba(0, 184, 137, 0.15);
}

.cat-icon {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.category-tab:hover .cat-icon {
  transform: scale(1.1);
}

.cat-label {
  line-height: 1;
}

.cat-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.08);
  color: #8a9693;
  font-size: 10px;
  line-height: 1;
  margin-left: 4px;
  transition: all 0.2s ease;
  opacity: 0.4;
}

.category-tab:hover .cat-delete {
  opacity: 1;
}

.cat-delete:hover {
  background-color: #ef4444 !important;
  color: #ffffff !important;
}

/* 新增分类按钮样式 */
.add-cat-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px dashed #cbd6d3;
  background-color: transparent;
  color: #8a9693;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-cat-btn:hover {
  border-color: #00b889;
  color: #00b889;
  background-color: #f2faf8;
  transform: rotate(90deg);
}

/* 高清分类 Modal 弹出框 */
.category-modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(14, 24, 22, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  animation: fadeIn 0.25s ease-out;
}

.category-modal {
  width: 380px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(14, 24, 22, 0.15);
  overflow: hidden;
  border: 1px solid #e1e8e6;
  animation: slideUp 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.category-modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f4f3;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.category-modal-header h3 {
  margin: 0;
  font-size: 16px;
  color: #0e1816;
  font-weight: 600;
}

.category-modal-header .close-btn {
  background: none;
  border: none;
  font-size: 20px;
  color: #8a9693;
  cursor: pointer;
  transition: color 0.2s;
  line-height: 1;
}

.category-modal-header .close-btn:hover {
  color: #ef4444;
}

.category-modal-body {
  padding: 20px;
}

.category-input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-input-group label {
  font-size: 13.5px;
  color: #5c6764;
  font-weight: 500;
  text-align: left;
}

.category-input-group input {
  padding: 10px 12px;
  border: 1px solid #cbd6d3;
  border-radius: 6px;
  font-size: 14px;
  color: #0e1816;
  transition: all 0.2s ease;
  outline: none;
}

.category-input-group input:focus {
  border-color: #00b889;
  box-shadow: 0 0 0 3px rgba(0, 184, 137, 0.15);
}

.category-modal-footer {
  padding: 12px 20px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid #f0f4f3;
}

.category-modal-footer button {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.category-modal-footer .ghost-btn {
  background: transparent;
  border: 1px solid #cbd6d3;
  color: #5c6764;
}

.category-modal-footer .ghost-btn:hover {
  background: #f0f4f3;
  border-color: #cbd6d3;
}

.category-modal-footer .primary-btn {
  background: #00b889;
  border: 1px solid #00b889;
  color: #ffffff;
}

.category-modal-footer .primary-btn:hover {
  background: #009e74;
  border-color: #009e74;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.tab-row {
  margin-bottom: 12px;
}
</style>
