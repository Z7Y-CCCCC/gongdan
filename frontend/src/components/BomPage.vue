<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createBom,
  deleteBom,
  fetchBoms,
  fetchProducts,
  updateBom,
  type BomInfo,
  type BomItemInfo,
  type BomPayload,
  type ProductInfo
} from '../api'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchAllPages } from '../utils/pagination'
import { formatBomDecimal, normalizeBomQuantity, normalizeBomScrapRate } from '../utils/bomDecimal'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import UiIcon from './UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'

type BomFormItem = {
  productId: number | ''
  quantity: string
  scrapRate: string
  remark: string
}

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const { visible, title, message, loading: deleteLoading, confirm, handleConfirm, handleCancel } = useDeleteConfirm()

const loading = ref(false)
const saving = ref(false)
const beginLoad = useLatestRequest()
const boms = ref<BomInfo[]>([])
const products = ref<ProductInfo[]>([])
const total = ref(0)
const showModal = ref(false)
const editing = ref<BomInfo | null>(null)
const filter = reactive({ code: '', productName: '' })
const form = reactive({
  code: '',
  productId: '' as number | '',
  outputQuantity: '1',
  remark: '',
  enabled: true,
  items: [] as BomFormItem[]
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

function itemUnit(item: BomFormItem | BomItemInfo) {
  const product = findProduct(item.productId)
  return product?.unitName || ('unitName' in item ? item.unitName : '') || '-'
}

async function loadData() {
  const isCurrent = beginLoad()
  loading.value = true
  try {
    const search = { code: filter.code.trim() || undefined, productName: filter.productName.trim() || undefined }
    const [bomRes, productRes] = await Promise.all([
      fetchAllPages(params => fetchBoms(props.token, { ...search, ...params }), 500, isCurrent),
      fetchAllPages(params => fetchProducts(props.token, params), 500, isCurrent)
    ])
    if (!isCurrent()) return
    boms.value = bomRes.items
    total.value = bomRes.total
    products.value = productRes.items
  } catch (e) {
    if (isCurrent()) emit('notice', e instanceof Error ? e.message : '物料清单加载失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

function emptyItem(): BomFormItem {
  return {
    productId: '',
    quantity: '1',
    scrapRate: '0',
    remark: ''
  }
}

function resetForm() {
  Object.assign(form, {
    code: '',
    productId: '',
    outputQuantity: '1',
    remark: '',
    enabled: true,
    items: [emptyItem()]
  })
}

function openCreate() {
  editing.value = null
  resetForm()
  showModal.value = true
}

function openEdit(item: BomInfo) {
  editing.value = item
  Object.assign(form, {
    code: item.code,
    productId: item.productId,
    outputQuantity: String(item.outputQuantity ?? '1'),
    remark: item.remark || '',
    enabled: item.enabled,
    items: item.items.length > 0
      ? item.items.map(detail => ({
          productId: detail.productId,
          quantity: String(detail.quantity),
          scrapRate: String(detail.scrapRate ?? '0'),
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
    emit('notice', '至少保留一条BOM明细')
    return
  }
  form.items.splice(index, 1)
}

function buildPayload(): BomPayload | null {
  if (!form.productId) {
    emit('notice', '请选择成品')
    return null
  }
  let outputQuantity: string
  try {
    outputQuantity = normalizeBomQuantity(form.outputQuantity)
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '产出数量无效')
    return null
  }
  const seen = new Set<number>()
  const items = []
  for (const item of form.items) {
    if (!item.productId) {
      emit('notice', '请选择子件')
      return null
    }
    if (item.productId === form.productId) {
      emit('notice', '子件不能和成品相同')
      return null
    }
    if (seen.has(item.productId)) {
      emit('notice', 'BOM明细不能重复选择同一产品')
      return null
    }
    let quantity: string
    let scrapRate: string
    try {
      quantity = normalizeBomQuantity(item.quantity, '子件用量')
      scrapRate = normalizeBomScrapRate(item.scrapRate === '' ? '0' : item.scrapRate)
    } catch (e) {
      emit('notice', e instanceof Error ? e.message : '子件数量或损耗率无效')
      return null
    }
    seen.add(item.productId)
    items.push({
      productId: item.productId,
      quantity,
      scrapRate,
      remark: item.remark.trim() || undefined
    })
  }
  return {
    code: form.code.trim() || undefined,
    productId: form.productId,
    outputQuantity,
    remark: form.remark.trim() || undefined,
    enabled: form.enabled,
    items
  }
}

async function saveBom() {
  if (saving.value) return
  const payload = buildPayload()
  if (!payload) return
  saving.value = true
  try {
    if (editing.value) {
      await updateBom(props.token, editing.value.id, payload)
      emit('notice', '物料清单已更新')
    } else {
      await createBom(props.token, payload)
      emit('notice', '物料清单已创建')
    }
    showModal.value = false
    await loadData()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeBom(item: BomInfo) {
  if (visible.value || saving.value || loading.value) return
  const ok = await confirm({ message: `确认删除物料清单「${item.code}」？` })
  if (!ok) return
  deleteLoading.value = true
  try {
    await deleteBom(props.token, item.id)
    emit('notice', '物料清单已删除')
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '删除失败')
    return
  } finally {
    deleteLoading.value = false
    handleCancel()
  }
  await loadData()
}

useRefreshOnActivated(loadData)
onMounted(loadData)
</script>

<template>
  <section class="content">
    <div class="filters">
      <label>
        <span>BOM编码</span>
        <input v-model="filter.code" placeholder="请输入BOM编码" @keydown.enter="loadData" />
      </label>
      <label>
        <span>成品名称</span>
        <input v-model="filter.productName" placeholder="请输入成品名称" @keydown.enter="loadData" />
      </label>
      <div class="filter-actions">
        <button class="primary-btn query-btn ui-icon-button" type="button" @click="loadData" title="查询物料清单" aria-label="查询物料清单"><UiIcon name="search" /></button>
        <button class="ghost-btn ui-icon-button" type="button" @click="filter.code = ''; filter.productName = ''; loadData()" title="重置筛选" aria-label="重置筛选"><UiIcon name="reset" /></button>
      </div>
    </div>

    <div class="toolbar">
      <button class="primary-btn ui-icon-button" type="button" @click="openCreate" title="新建物料清单" aria-label="新建物料清单"><UiIcon name="plus" /></button>
      <span class="muted">共 {{ total }} 个BOM</span>
      <TableConfigButton table-id="bom-list-table" />
    </div>

    <div class="table-container">
      <table id="bom-list-table" class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>BOM编码</th>
            <th>成品编码</th>
            <th>成品名称</th>
            <th>规格</th>
            <th>产出数量</th>
            <th>子件数</th>
            <th>状态</th>
            <th>更新时间</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="boms.length === 0">
            <td colspan="10" class="empty-cell">暂无物料清单</td>
          </tr>
          <tr v-for="(item, index) in boms" v-else :key="item.id">
            <td>{{ index + 1 }}</td>
            <td class="green-link">{{ item.code }}</td>
            <td>{{ item.productCode }}</td>
            <td>{{ item.productName }}</td>
            <td>{{ item.productSpec || '-' }}</td>
            <td>{{ formatBomDecimal(item.outputQuantity) }} {{ item.unitName || '' }}</td>
            <td>{{ item.items.length }}</td>
            <td>
              <span class="status-badge" :class="{ disabled: !item.enabled }">{{ item.enabled ? '启用' : '停用' }}</span>
            </td>
            <td>{{ item.updatedAt || item.createdAt || '-' }}</td>
            <td class="action-cell">
              <button class="action-link ui-icon-button" type="button" @click="openEdit(item)" title="编辑物料清单" aria-label="编辑物料清单"><UiIcon name="edit" /></button>
              <button class="action-link danger ui-icon-button" type="button" @click="removeBom(item)" title="删除物料清单" aria-label="删除物料清单"><UiIcon name="trash" /></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-backdrop">
      <div class="modal workorder-modal bom-modal">
        <div class="modal-header">
          <h2>{{ editing ? '编辑物料清单' : '新建物料清单' }}</h2>
          <button class="close-btn ui-icon-button" type="button" @click="showModal = false" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
        </div>
        <div class="modal-body">
          <div class="form-grid two-col">
            <label>
              <span>BOM编码</span>
              <input v-model="form.code" placeholder="留空自动生成" />
            </label>
            <label>
              <span>成品</span>
              <select v-model="form.productId">
                <option value="">请选择成品</option>
                <option v-for="product in productOptions" :key="product.id" :value="product.id">
                  {{ productLabel(product) }}
                </option>
              </select>
            </label>
            <label>
              <span>产出数量</span>
              <input v-model="form.outputQuantity" type="text" inputmode="decimal" maxlength="128" aria-label="产出数量" placeholder="大于0，最多4位小数" />
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.enabled">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
            <label class="full-row">
              <span>备注</span>
              <textarea v-model="form.remark" rows="2" placeholder="请输入备注"></textarea>
            </label>
          </div>

          <div class="bom-detail-header">
            <h3>子件明细</h3>
            <div class="bom-detail-actions">
              <TableConfigButton table-id="bom-item-table" />
              <button class="ghost-btn ui-icon-button" type="button" @click="addItem" title="添加子件" aria-label="添加子件"><UiIcon name="plus" /></button>
            </div>
          </div>
          <div class="table-container compact-table">
            <table id="bom-item-table" class="data-table">
              <thead>
                <tr>
                  <th style="width:40%">子件</th>
                  <th style="width:13%">用量</th>
                  <th style="width:9%">单位</th>
                  <th style="width:14%">损耗率%</th>
                  <th style="width:14%">备注</th>
                  <th style="width:96px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in form.items" :key="index">
                  <td>
                    <select v-model="item.productId">
                      <option value="">请选择子件</option>
                      <option v-for="product in productOptions" :key="product.id" :value="product.id">
                        {{ productLabel(product) }}
                      </option>
                    </select>
                  </td>
                  <td>
                    <input v-model="item.quantity" type="text" inputmode="decimal" maxlength="128" aria-label="子件用量" placeholder="大于0，最多4位小数" />
                  </td>
                  <td>{{ itemUnit(item) }}</td>
                  <td>
                    <input v-model="item.scrapRate" type="text" inputmode="decimal" maxlength="128" aria-label="损耗率" placeholder="0到100，最多4位小数" />
                  </td>
                  <td>
                    <input v-model="item.remark" placeholder="备注" />
                  </td>
                  <td>
                    <button class="action-link danger ui-icon-button" type="button" @click="removeItem(index)" title="删除子件" aria-label="删除子件"><UiIcon name="trash" /></button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showModal = false">取消</button>
          <button class="primary-btn wide" type="button" @click="saveBom" :disabled="saving" title="保存物料清单" aria-label="保存物料清单">{{ saving ? '保存中...' : '保存' }}</button>
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

.bom-modal {
  width: min(960px, calc(100vw - 48px));
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.full-row {
  grid-column: 1 / -1;
}

.bom-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 10px;
}

.bom-detail-header h3 {
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

  .bom-modal {
    width: calc(100vw - 24px);
  }
}
</style>
