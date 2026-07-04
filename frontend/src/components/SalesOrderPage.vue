<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createSalesOrder,
  deleteSalesOrder,
  fetchProducts,
  fetchSalesOrders,
  updateSalesOrder,
  type ProductInfo,
  type SalesOrderInfo,
  type SalesOrderItemInfo,
  type SalesOrderPayload,
  type SalesOrderStatus
} from '../api'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'

type SalesOrderFormItem = {
  productId: number | ''
  quantity: number
  remark: string
}

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading: deleteLoading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

const statusOptions: Array<{ value: SalesOrderStatus | ''; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'FINISHED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]

const loading = ref(false)
const orders = ref<SalesOrderInfo[]>([])
const products = ref<ProductInfo[]>([])
const total = ref(0)
const showModal = ref(false)
const editing = ref<SalesOrderInfo | null>(null)
const filter = reactive({ code: '', customerName: '', status: '' as SalesOrderStatus | '' })
const form = reactive({
  code: '',
  customerName: '',
  orderDate: '',
  deliveryDate: '',
  status: 'DRAFT' as SalesOrderStatus,
  remark: '',
  items: [] as SalesOrderFormItem[]
})

const productOptions = computed(() => products.value)

function productLabel(product: ProductInfo) {
  const spec = product.spec ? ` / ${product.spec}` : ''
  return `${product.code} - ${product.name}${spec}`
}

function findProduct(productId: number | '') {
  if (!productId) return null
  return products.value.find(item => item.id === productId) || null
}

function itemUnit(item: SalesOrderFormItem | SalesOrderItemInfo) {
  const product = findProduct(item.productId)
  return product?.unitName || ('unitName' in item ? item.unitName : '') || '-'
}

async function loadData() {
  loading.value = true
  try {
    const [orderRes, productRes] = await Promise.all([
      fetchSalesOrders(props.token, {
        code: filter.code.trim() || undefined,
        customerName: filter.customerName.trim() || undefined,
        status: filter.status,
        size: 500
      }),
      fetchProducts(props.token, { size: 1000 })
    ])
    orders.value = orderRes.items
    total.value = orderRes.total
    products.value = productRes.items
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '销售订单加载失败')
  } finally {
    loading.value = false
  }
}

function emptyItem(): SalesOrderFormItem {
  return {
    productId: '',
    quantity: 1,
    remark: ''
  }
}

function resetForm() {
  Object.assign(form, {
    code: '',
    customerName: '',
    orderDate: new Date().toISOString().slice(0, 10),
    deliveryDate: '',
    status: 'DRAFT',
    remark: '',
    items: [emptyItem()]
  })
}

function openCreate() {
  editing.value = null
  resetForm()
  showModal.value = true
}

function openEdit(item: SalesOrderInfo) {
  editing.value = item
  Object.assign(form, {
    code: item.code,
    customerName: item.customerName,
    orderDate: item.orderDate || '',
    deliveryDate: item.deliveryDate || '',
    status: item.status,
    remark: item.remark || '',
    items: item.items.length > 0
      ? item.items.map(detail => ({
          productId: detail.productId,
          quantity: detail.quantity,
          remark: detail.remark || ''
        }))
      : [emptyItem()]
  })
  showModal.value = true
}

function addItem() {
  form.items.push(emptyItem())
}

function removeItem(index: number) {
  if (form.items.length === 1) {
    emit('notice', '至少保留一条订单明细')
    return
  }
  form.items.splice(index, 1)
}

function buildPayload(): SalesOrderPayload | null {
  if (!form.customerName.trim()) {
    emit('notice', '请输入客户名称')
    return null
  }
  const seen = new Set<number>()
  const items = []
  for (const item of form.items) {
    if (!item.productId) {
      emit('notice', '请选择产品')
      return null
    }
    if (seen.has(item.productId)) {
      emit('notice', '订单明细不能重复选择同一产品')
      return null
    }
    if (!item.quantity || Number(item.quantity) <= 0) {
      emit('notice', '订单数量必须大于0')
      return null
    }
    seen.add(item.productId)
    items.push({
      productId: item.productId,
      quantity: Number(item.quantity),
      remark: item.remark.trim() || undefined
    })
  }
  return {
    code: form.code.trim() || undefined,
    customerName: form.customerName.trim(),
    orderDate: form.orderDate || undefined,
    deliveryDate: form.deliveryDate || undefined,
    status: form.status,
    remark: form.remark.trim() || undefined,
    items
  }
}

async function saveOrder() {
  const payload = buildPayload()
  if (!payload) return
  try {
    if (editing.value) {
      await updateSalesOrder(props.token, editing.value.id, payload)
      emit('notice', '销售订单已更新')
    } else {
      await createSalesOrder(props.token, payload)
      emit('notice', '销售订单已创建')
    }
    showModal.value = false
    await loadData()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '保存失败')
  }
}

async function removeOrder(item: SalesOrderInfo) {
  const ok = await confirm({ message: `确认删除销售订单「${item.code}」？` })
  if (!ok) return
  try {
    await deleteSalesOrder(props.token, item.id)
    emit('notice', '销售订单已删除')
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
        <span>订单编号</span>
        <input v-model="filter.code" placeholder="请输入订单编号" @keydown.enter="loadData" />
      </label>
      <label>
        <span>客户名称</span>
        <input v-model="filter.customerName" placeholder="请输入客户名称" @keydown.enter="loadData" />
      </label>
      <label>
        <span>状态</span>
        <select v-model="filter.status" @change="loadData">
          <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </label>
      <div class="filter-actions">
        <button class="primary-btn query-btn" type="button" @click="loadData">查询</button>
        <button class="ghost-btn" type="button" @click="filter.code = ''; filter.customerName = ''; filter.status = ''; loadData()">重置</button>
      </div>
    </div>

    <div class="toolbar">
      <button class="primary-btn" type="button" @click="openCreate">新建销售订单</button>
      <span class="muted">共 {{ total }} 个销售订单</span>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>订单编号</th>
            <th>客户名称</th>
            <th>订单日期</th>
            <th>交付日期</th>
            <th>明细数</th>
            <th>总数量</th>
            <th>状态</th>
            <th>更新时间</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="orders.length === 0">
            <td colspan="10" class="empty-cell">暂无销售订单</td>
          </tr>
          <tr v-for="(item, index) in orders" v-else :key="item.id">
            <td>{{ index + 1 }}</td>
            <td class="green-link">{{ item.code }}</td>
            <td>{{ item.customerName }}</td>
            <td>{{ item.orderDate || '-' }}</td>
            <td>{{ item.deliveryDate || '-' }}</td>
            <td>{{ item.items.length }}</td>
            <td>{{ item.totalQuantity || 0 }}</td>
            <td>
              <span class="status-badge" :class="{ disabled: item.status === 'CANCELLED' }">{{ item.statusLabel }}</span>
            </td>
            <td>{{ item.updatedAt || item.createdAt || '-' }}</td>
            <td class="action-cell">
              <button class="action-link" type="button" @click="openEdit(item)">编辑</button>
              <button class="action-link danger" type="button" @click="removeOrder(item)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-backdrop">
      <div class="modal workorder-modal sales-order-modal">
        <div class="modal-header">
          <h2>{{ editing ? '编辑销售订单' : '新建销售订单' }}</h2>
          <button class="close-btn" type="button" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-grid two-col">
            <label>
              <span>订单编号</span>
              <input v-model="form.code" placeholder="留空自动生成" />
            </label>
            <label>
              <span>客户名称</span>
              <input v-model="form.customerName" placeholder="请输入客户名称" />
            </label>
            <label>
              <span>订单日期</span>
              <input v-model="form.orderDate" type="date" />
            </label>
            <label>
              <span>交付日期</span>
              <input v-model="form.deliveryDate" type="date" />
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.status">
                <option value="DRAFT">草稿</option>
                <option value="CONFIRMED">已确认</option>
                <option value="FINISHED">已完成</option>
                <option value="CANCELLED">已取消</option>
              </select>
            </label>
            <label class="full-row">
              <span>备注</span>
              <textarea v-model="form.remark" rows="2" placeholder="请输入备注"></textarea>
            </label>
          </div>

          <div class="order-detail-header">
            <h3>订单明细</h3>
            <button class="ghost-btn" type="button" @click="addItem">添加产品</button>
          </div>
          <div class="table-container compact-table">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width:48%">产品</th>
                  <th style="width:16%">数量</th>
                  <th style="width:10%">单位</th>
                  <th>备注</th>
                  <th style="width:80px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in form.items" :key="index">
                  <td>
                    <select v-model="item.productId">
                      <option value="">请选择产品</option>
                      <option v-for="product in productOptions" :key="product.id" :value="product.id">
                        {{ productLabel(product) }}
                      </option>
                    </select>
                  </td>
                  <td>
                    <input v-model.number="item.quantity" type="number" min="1" step="1" />
                  </td>
                  <td>{{ itemUnit(item) }}</td>
                  <td>
                    <input v-model="item.remark" placeholder="备注" />
                  </td>
                  <td>
                    <button class="action-link danger" type="button" @click="removeItem(index)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showModal = false">取消</button>
          <button class="primary-btn wide" type="button" @click="saveOrder">保存</button>
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

.sales-order-modal {
  width: min(940px, calc(100vw - 48px));
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.full-row {
  grid-column: 1 / -1;
}

.order-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 10px;
}

.order-detail-header h3 {
  margin: 0;
  font-size: 15px;
  color: #26332f;
}

.compact-table {
  max-height: 360px;
  overflow: auto;
}

.compact-table select,
.compact-table input {
  width: 100%;
}

textarea {
  resize: vertical;
  min-height: 64px;
}

@media (max-width: 760px) {
  .two-col {
    grid-template-columns: 1fr;
  }

  .sales-order-modal {
    width: calc(100vw - 24px);
  }
}
</style>
