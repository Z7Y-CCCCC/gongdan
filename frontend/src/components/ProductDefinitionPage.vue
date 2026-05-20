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
  type ProcessRouteInfo,
  type ProductInfo,
  type ProductPayload,
  type UnitInfo
} from '../api'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'

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

const tabItems = computed(() => [
  { key: 'all', label: '全部', count: products.value.length },
  { key: 'self', label: '自制', count: products.value.filter(p => p.attribute === '自制').length },
  { key: 'purchase', label: '外购', count: products.value.filter(p => p.attribute === '外购').length },
  { key: 'outsource', label: '委外', count: products.value.filter(p => p.attribute === '委外').length }
] as Array<{ key: ProductTab; label: string; count: number }>)

const filteredProducts = computed(() => {
  return products.value.filter(item => {
    const tabOk = activeTab.value === 'all' ||
      (activeTab.value === 'self' && item.attribute === '自制') ||
      (activeTab.value === 'purchase' && item.attribute === '外购') ||
      (activeTab.value === 'outsource' && item.attribute === '委外')
    const codeOk = !filter.code || item.code.includes(filter.code)
    const nameOk = !filter.name || item.name.includes(filter.name)
    const specOk = !filter.spec || (item.spec || '').includes(filter.spec)
    return tabOk && codeOk && nameOk && specOk
  })
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
  const [productRes, unitRes, routeRes] = await Promise.all([
    fetchProducts(props.token, { size: 500 }),
    fetchUnits(props.token, { size: 500 }),
    fetchProcessRoutes(props.token, { size: 500 })
  ])
  products.value = productRes.items
  units.value = unitRes.items
  routes.value = routeRes.items
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

function exportProducts() {
  emit('notice', '产品导出功能已触发')
}

onMounted(loadData)
</script>

<template>
  <section class="content">
    <template v-if="view === 'list'">
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
        <button class="primary-btn create-btn" type="button" @click="openCreate">创建产品</button>
        <button class="toolbar-btn" type="button" @click="exportProducts">导出</button>
        <button class="toolbar-btn" type="button">导入</button>
        <button class="toolbar-btn" type="button">字段配置</button>
        <button class="toolbar-btn" type="button">排序</button>
        <button class="toolbar-btn" type="button">行高</button>
        <button class="toolbar-btn" type="button" style="margin-left:auto" @click="openLogs">操作记录</button>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:50px"><input type="checkbox" /></th>
              <th>库存数量</th>
              <th>单位</th>
              <th>产品名称</th>
              <th>产品编号</th>
              <th>工艺路线</th>
              <th>默认供应商</th>
              <th style="width:190px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredProducts" :key="item.id">
              <td><input type="checkbox" /></td>
              <td>{{ item.inventoryQty }}</td>
              <td>{{ item.unitName }}</td>
              <td>{{ item.name }}</td>
              <td class="green-link">{{ item.code }}</td>
              <td>{{ item.routeName || '-' }}</td>
              <td>{{ item.supplierName || '-' }}</td>
              <td class="action-cell">
                <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
                <button class="action-link" type="button">打印</button>
                <button class="action-link" type="button" @click="copyProduct(item)">复制</button>
                <button class="action-link danger" type="button" @click="removeProduct(item)">删除</button>
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="8">合计 {{ totalInventory }}</td>
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
          <div class="process-field"><label class="process-label">产品类型</label><input v-model="form.productType" placeholder="请选择" /></div>
        </div>
        <div class="form-bottom-bar">
          <button class="ghost-btn wide" type="button" @click="view = 'list'">取消</button>
          <button class="primary-btn wide" type="button" @click="saveProduct">保存</button>
        </div>
      </div>
    </template>

    <div v-if="showLogs" class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>操作记录</h2>
          <button class="close-btn" type="button" @click="showLogs = false">×</button>
        </div>
        <div style="padding:18px">
          <p v-for="(item, index) in logs" :key="index">{{ item }}</p>
          <p v-if="logs.length === 0" style="color:#8a9693">暂无记录</p>
        </div>
      </div>
    </div>

    <DeleteConfirmDialog
      v-model:visible="visible"
      :title="title"
      :message="message"
      :loading="loading"
      @confirm="handleConfirm"
      @update:visible="val => { if (!val) handleCancel() }"
    />
  </section>
</template>
