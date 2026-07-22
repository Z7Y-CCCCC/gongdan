<template>
  <section class="content advanced-page">
    <div class="category-tabs advanced-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="cat-tab"
        :class="{ active: activeTab === tab.key }"
        type="button"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="count-badge">{{ tab.count }}</span>
      </button>
    </div>

    <div class="toolbar advanced-toolbar">
      <div class="toolbar-left">
        <button v-if="currentModule" class="primary-btn create-btn" type="button" @click="openCreate">
          新建{{ currentTabLabel }}
        </button>
        <button class="ghost-btn" type="button" @click="loadActive">刷新</button>
      </div>
      <div class="filters inline-filters">
        <label v-if="currentModule">
          <span>状态</span>
          <select v-model="statusFilter" @change="loadRecords">
            <option value="">全部</option>
            <option v-for="status in currentStatuses" :key="status.value" :value="status.value">{{ status.label }}</option>
          </select>
        </label>
      </div>
    </div>

    <div v-if="activeTab === 'SOP'" class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>文档名称</th>
            <th>适用工序</th>
            <th>版本</th>
            <th>负责人</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.title }}</td>
            <td>{{ payloadOf(record).processName || '-' }}</td>
            <td>{{ payloadOf(record).version || '-' }}</td>
            <td>{{ payloadOf(record).owner || record.createdByName || '-' }}</td>
            <td><span class="status-pill" :class="record.status.toLowerCase()">{{ statusText(record.status) }}</span></td>
            <td>{{ record.updatedAt || '-' }}</td>
            <td class="row-actions">
              <button class="toolbar-btn" type="button" @click="openEdit(record)">编辑</button>
              <button class="toolbar-btn" type="button" @click="changeStatus(record, record.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE')">
                {{ record.status === 'ACTIVE' ? '归档' : '启用' }}
              </button>
              <button class="toolbar-btn danger" type="button" @click="removeRecord(record)">删除</button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无 SOP 文档</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'MESSAGE'" class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>推送名称</th>
            <th>渠道</th>
            <th>接收对象</th>
            <th>状态</th>
            <th>最后更新</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.title }}</td>
            <td>{{ channelText(payloadOf(record).channel) }}</td>
            <td>{{ payloadOf(record).target || '-' }}</td>
            <td><span class="status-pill" :class="record.status.toLowerCase()">{{ statusText(record.status) }}</span></td>
            <td>{{ record.updatedAt || '-' }}</td>
            <td class="row-actions">
              <button class="toolbar-btn" type="button" @click="openEdit(record)">编辑</button>
              <button class="toolbar-btn" type="button" @click="sendTest(record)">发送测试</button>
              <button class="toolbar-btn danger" type="button" @click="removeRecord(record)">删除</button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="6" class="empty-cell">暂无推送配置</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'QUALITY'" class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>问题标题</th>
            <th>工单</th>
            <th>工序</th>
            <th>不良数</th>
            <th>返工人</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.title }}</td>
            <td>{{ payloadOf(record).workOrderCode || '-' }}</td>
            <td>{{ payloadOf(record).processName || '-' }}</td>
            <td>{{ payloadOf(record).defectQuantity || 0 }}</td>
            <td>{{ payloadOf(record).reworkOwner || '-' }}</td>
            <td><span class="status-pill" :class="record.status.toLowerCase()">{{ statusText(record.status) }}</span></td>
            <td class="row-actions">
              <button class="toolbar-btn" type="button" @click="openEdit(record)">{{ record.status === 'CLOSED' ? '查看/编辑' : '编辑' }}</button>
              <button v-if="record.status === 'OPEN'" class="toolbar-btn" type="button" @click="changeStatus(record, 'REWORKING')">转返工</button>
              <button v-if="record.status !== 'CLOSED'" class="toolbar-btn" type="button" @click="changeStatus(record, 'CLOSED')">关闭</button>
              <button class="toolbar-btn danger" type="button" @click="removeRecord(record)">删除</button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无质检返工记录</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'EQUIPMENT'" class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>设备/工位</th>
            <th>编号</th>
            <th>位置</th>
            <th>责任人</th>
            <th>下次保养</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.title }}</td>
            <td>{{ record.businessKey || '-' }}</td>
            <td>{{ payloadOf(record).location || '-' }}</td>
            <td>{{ payloadOf(record).owner || '-' }}</td>
            <td>{{ payloadOf(record).nextMaintenance || '-' }}</td>
            <td><span class="status-pill" :class="record.status.toLowerCase()">{{ statusText(record.status) }}</span></td>
            <td class="row-actions">
              <button class="toolbar-btn" type="button" @click="openEdit(record)">编辑</button>
              <button v-if="record.status !== 'MAINTENANCE'" class="toolbar-btn" type="button" @click="changeStatus(record, 'MAINTENANCE')">保养</button>
              <button v-if="record.status !== 'ACTIVE'" class="toolbar-btn" type="button" @click="changeStatus(record, 'ACTIVE')">启用</button>
              <button class="toolbar-btn danger" type="button" @click="removeRecord(record)">删除</button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无设备/工位</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'GANTT'" class="gantt-wrap">
      <div class="gantt-header">
        <strong>排产甘特图</strong>
        <span>按工单计划开始/结束时间生成，缺少计划时间的工单会单独提示。</span>
      </div>
      <div class="gantt-list">
        <div v-for="item in ganttItems" :key="item.id" class="gantt-row">
          <div class="gantt-name">
            <strong>{{ item.code }}</strong>
            <span>{{ item.productName }}</span>
          </div>
          <div class="gantt-line">
            <div class="gantt-bar" :style="{ left: item.left + '%', width: item.width + '%' }">
              {{ item.progress }}%
            </div>
          </div>
          <div class="gantt-date">{{ formatDate(item.planStart) }} - {{ formatDate(item.planEnd) }}</div>
        </div>
        <div v-if="ganttItems.length === 0" class="empty-cell">暂无带计划时间的工单</div>
      </div>
      <div v-if="unscheduledCount" class="gantt-warning">{{ unscheduledCount }} 个工单缺少计划开始或结束时间，暂未进入甘特图。</div>
    </div>

    <div v-else-if="activeTab === 'PAYROLL'" class="payroll-layout">
      <div class="table-container payroll-rules">
        <div class="section-heading">
          <strong>计件规则</strong>
          <span>按工序名称匹配，未匹配时单价为 0。</span>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>规则</th>
              <th>工序</th>
              <th>良品单价</th>
              <th>不良扣款</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td>{{ record.title }}</td>
              <td>{{ payloadOf(record).processName || '-' }}</td>
              <td>{{ money(payloadOf(record).unitPrice) }}</td>
              <td>{{ money(payloadOf(record).defectPenalty) }}</td>
              <td><span class="status-pill" :class="record.status.toLowerCase()">{{ statusText(record.status) }}</span></td>
              <td class="row-actions">
                <button class="toolbar-btn" type="button" @click="openEdit(record)">编辑</button>
                <button class="toolbar-btn danger" type="button" @click="removeRecord(record)">删除</button>
              </td>
            </tr>
            <tr v-if="!loading && records.length === 0"><td colspan="6" class="empty-cell">暂无工资规则</td></tr>
          </tbody>
        </table>
      </div>
      <div class="table-container payroll-result">
        <div class="section-heading">
          <strong>绩效核算</strong>
          <span>基于最近 500 条报工记录估算。</span>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>员工</th>
              <th>良品数</th>
              <th>不良数</th>
              <th>工序数</th>
              <th>预估工资</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in payrollRows" :key="row.name">
              <td>{{ row.name }}</td>
              <td>{{ row.goodQuantity }}</td>
              <td>{{ row.defectQuantity }}</td>
              <td>{{ row.processCount }}</td>
              <td>{{ money(row.amount) }}</td>
            </tr>
            <tr v-if="payrollRows.length === 0"><td colspan="5" class="empty-cell">暂无可核算报工</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="activeTab === 'FIELD_EVENT'" class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>现场事件</th>
            <th>类型</th>
            <th>工单</th>
            <th>工位</th>
            <th>上报人</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.title }}</td>
            <td>{{ payloadOf(record).eventType || '-' }}</td>
            <td>{{ payloadOf(record).workOrderCode || '-' }}</td>
            <td>{{ payloadOf(record).station || '-' }}</td>
            <td>{{ record.createdByName || '-' }}</td>
            <td><span class="status-pill" :class="record.status.toLowerCase()">{{ statusText(record.status) }}</span></td>
            <td class="row-actions">
              <button class="toolbar-btn" type="button" @click="openEdit(record)">{{ record.status === 'CLOSED' ? '查看/编辑' : '处理' }}</button>
              <button v-if="record.status !== 'CLOSED'" class="toolbar-btn" type="button" @click="changeStatus(record, 'CLOSED')">关闭</button>
              <button class="toolbar-btn danger" type="button" @click="removeRecord(record)">删除</button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无现场事件</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="loading" class="loading-line">加载中...</div>

    <div v-if="showModal" class="modal-backdrop">
      <section class="modal advanced-modal">
        <header class="modal-header">
          <h2>{{ editingRecord ? '编辑' : '新建' }}{{ currentTabLabel }}</h2>
          <button class="close-btn" type="button" @click="showModal = false">x</button>
        </header>
        <form class="create-form two-column" @submit.prevent="saveRecord">
          <label class="full-row">
            <span><em>*</em> 名称</span>
            <input v-model.trim="form.title" required placeholder="请输入名称" />
          </label>

          <template v-if="activeTab === 'SOP'">
            <label><span>适用工序</span><input v-model.trim="form.payload.processName" /></label>
            <label><span>版本</span><input v-model.trim="form.payload.version" placeholder="v1.0" /></label>
            <label><span>负责人</span><input v-model.trim="form.payload.owner" /></label>
            <label><span>外链/附件</span><input v-model.trim="form.payload.attachmentUrl" /></label>
            <label class="full-row"><span>作业内容</span><textarea v-model.trim="form.payload.content" rows="5"></textarea></label>
          </template>

          <template v-else-if="activeTab === 'MESSAGE'">
            <label>
              <span>渠道</span>
              <select v-model="form.payload.channel">
                <option value="wechat_work">企业微信</option>
                <option value="wechat_mp">微信公众号</option>
                <option value="webhook">Webhook</option>
              </select>
            </label>
            <label><span>接收对象</span><input v-model.trim="form.payload.target" placeholder="部门/群/用户" /></label>
            <label><span>Webhook</span><input v-model.trim="form.payload.webhook" /></label>
            <label><span>AppId</span><input v-model.trim="form.payload.appId" /></label>
            <label class="full-row"><span>消息模板</span><textarea v-model.trim="form.payload.template" rows="4"></textarea></label>
          </template>

          <template v-else-if="activeTab === 'QUALITY'">
            <label><span>工单编号</span><input v-model.trim="form.payload.workOrderCode" /></label>
            <label><span>工序</span><input v-model.trim="form.payload.processName" /></label>
            <label><span>不良项</span><input v-model.trim="form.payload.defectName" /></label>
            <label><span>不良数量</span><input v-model.number="form.payload.defectQuantity" type="number" min="0" /></label>
            <label><span>返工负责人</span><input v-model.trim="form.payload.reworkOwner" /></label>
            <label><span>返工期限</span><input v-model="form.payload.reworkDeadline" type="date" /></label>
            <label class="full-row"><span>原因与措施</span><textarea v-model.trim="form.payload.action" rows="4"></textarea></label>
          </template>

          <template v-else-if="activeTab === 'EQUIPMENT'">
            <label><span>编号</span><input v-model.trim="form.businessKey" /></label>
            <label>
              <span>类型</span>
              <select v-model="form.payload.type">
                <option value="equipment">设备</option>
                <option value="station">工位</option>
              </select>
            </label>
            <label><span>位置</span><input v-model.trim="form.payload.location" /></label>
            <label><span>责任人</span><input v-model.trim="form.payload.owner" /></label>
            <label><span>保养周期</span><input v-model.trim="form.payload.maintenanceCycle" placeholder="每月/每周" /></label>
            <label><span>下次保养</span><input v-model="form.payload.nextMaintenance" type="date" /></label>
          </template>

          <template v-else-if="activeTab === 'PAYROLL'">
            <label><span>工序名称</span><input v-model.trim="form.payload.processName" placeholder="与报工工序名一致" /></label>
            <label><span>生效月份</span><input v-model="form.payload.effectiveMonth" type="month" /></label>
            <label><span>良品单价</span><input v-model.number="form.payload.unitPrice" type="number" min="0" step="0.01" /></label>
            <label><span>不良扣款</span><input v-model.number="form.payload.defectPenalty" type="number" min="0" step="0.01" /></label>
            <label class="full-row"><span>备注</span><textarea v-model.trim="form.payload.description" rows="3"></textarea></label>
          </template>

          <template v-else-if="activeTab === 'FIELD_EVENT'">
            <label><span>类型</span><input v-model.trim="form.payload.eventType" /></label>
            <label><span>工单编号</span><input v-model.trim="form.payload.workOrderCode" /></label>
            <label><span>工位</span><input v-model.trim="form.payload.station" /></label>
            <label><span>处理人</span><input v-model.trim="form.payload.owner" /></label>
            <label class="full-row"><span>描述/处理结果</span><textarea v-model.trim="form.payload.description" rows="4"></textarea></label>
          </template>

          <label>
            <span>状态</span>
            <select v-model="form.status">
              <option v-for="status in currentStatuses" :key="status.value" :value="status.value">{{ status.label }}</option>
            </select>
          </label>
          <footer class="modal-footer full-row">
            <button class="ghost-btn wide" type="button" @click="showModal = false">取消</button>
            <button class="primary-btn wide" :disabled="saving" type="submit">{{ saving ? '保存中...' : '保存' }}</button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createAdvancedRecord,
  deleteAdvancedRecord,
  fetchAdvancedRecords,
  fetchReports,
  fetchWorkOrders,
  updateAdvancedRecord,
  type AdvancedRecordInfo,
  type AdvancedRecordModule,
  type WorkOrderInfo,
  type WorkOrderReportInfo
} from '../api'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ (e: 'notice', message: string): void }>()

type TabKey = Exclude<AdvancedRecordModule, 'PAYROLL_RULE'> | 'PAYROLL' | 'GANTT'
type StatusItem = { value: string; label: string }

const activeTab = ref<TabKey>('SOP')
const records = ref<AdvancedRecordInfo[]>([])
const workOrders = ref<WorkOrderInfo[]>([])
const reports = ref<WorkOrderReportInfo[]>([])
const loading = ref(false)
const saving = ref(false)
const statusFilter = ref('')
const showModal = ref(false)
const editingRecord = ref<AdvancedRecordInfo | null>(null)

const form = reactive({
  title: '',
  businessKey: '',
  status: 'ACTIVE',
  payload: {} as Record<string, any>
})

const moduleTabs: Array<{ key: TabKey; label: string }> = [
  { key: 'SOP', label: 'SOP 文档库' },
  { key: 'MESSAGE', label: '微信/企微推送' },
  { key: 'QUALITY', label: '质检返工' },
  { key: 'EQUIPMENT', label: '设备/工位' },
  { key: 'GANTT', label: '排产甘特图' },
  { key: 'PAYROLL', label: '工资绩效' },
  { key: 'FIELD_EVENT', label: '现场事件' }
]

const statusMap: Record<string, StatusItem[]> = {
  SOP: [
    { value: 'ACTIVE', label: '启用' },
    { value: 'ARCHIVED', label: '归档' }
  ],
  MESSAGE: [
    { value: 'DRAFT', label: '草稿' },
    { value: 'ACTIVE', label: '启用' },
    { value: 'SENT', label: '已测试' },
    { value: 'DISABLED', label: '停用' }
  ],
  QUALITY: [
    { value: 'OPEN', label: '待处理' },
    { value: 'REWORKING', label: '返工中' },
    { value: 'CLOSED', label: '已关闭' }
  ],
  EQUIPMENT: [
    { value: 'ACTIVE', label: '运行' },
    { value: 'MAINTENANCE', label: '保养中' },
    { value: 'OFFLINE', label: '停用' }
  ],
  PAYROLL_RULE: [
    { value: 'ACTIVE', label: '启用' },
    { value: 'DISABLED', label: '停用' }
  ],
  FIELD_EVENT: [
    { value: 'OPEN', label: '待处理' },
    { value: 'PROCESSING', label: '处理中' },
    { value: 'CLOSED', label: '已关闭' }
  ]
}

const currentModule = computed<AdvancedRecordModule | null>(() => activeTab.value === 'GANTT' ? null : moduleNameFor(activeTab.value))
const currentStatuses = computed(() => currentModule.value ? statusMap[currentModule.value] || statusMap.SOP : [])
const currentTabLabel = computed(() => moduleTabs.find(tab => tab.key === activeTab.value)?.label || '')
const tabs = computed(() => moduleTabs.map(tab => ({
  ...tab,
  count: tab.key === activeTab.value && activeTab.value !== 'GANTT' ? records.value.length : undefined
})))

const payrollRows = computed(() => {
  const rules = records.value
    .filter(record => record.status === 'ACTIVE')
    .map(record => payloadOf(record))
  const rows = new Map<string, { name: string; goodQuantity: number; defectQuantity: number; processSet: Set<string>; amount: number }>()
  reports.value.filter(report => report.status === 'APPROVED').forEach(report => {
    const name = report.reporterName || '未指定'
    const row = rows.get(name) || { name, goodQuantity: 0, defectQuantity: 0, processSet: new Set<string>(), amount: 0 }
    const good = Number(report.goodQuantity || 0)
    const defect = Number(report.defectQuantity || 0)
    const processName = report.processName || ''
    const rule = rules.find(item => item.processName === processName)
    row.goodQuantity += good
    row.defectQuantity += defect
    if (processName) row.processSet.add(processName)
    row.amount += good * Number(rule?.unitPrice || 0) - defect * Number(rule?.defectPenalty || 0)
    rows.set(name, row)
  })
  return Array.from(rows.values()).map(row => ({
    name: row.name,
    goodQuantity: row.goodQuantity,
    defectQuantity: row.defectQuantity,
    processCount: row.processSet.size,
    amount: Math.max(0, Number(row.amount.toFixed(2)))
  }))
})

const scheduledOrders = computed(() => workOrders.value.filter(order => order.planStart && order.planEnd))
const unscheduledCount = computed(() => workOrders.value.length - scheduledOrders.value.length)
const ganttItems = computed(() => {
  const dates = scheduledOrders.value.flatMap(order => [new Date(order.planStart as string).getTime(), new Date(order.planEnd as string).getTime()])
  const min = Math.min(...dates, Date.now())
  const max = Math.max(...dates, Date.now() + 86400000)
  const span = Math.max(1, max - min)
  return scheduledOrders.value.map(order => {
    const start = new Date(order.planStart as string).getTime()
    const end = new Date(order.planEnd as string).getTime()
    const left = Math.max(0, Math.min(96, ((start - min) / span) * 100))
    const width = Math.max(4, Math.min(100 - left, ((end - start) / span) * 100))
    return {
      id: order.id,
      code: order.code,
      productName: order.productName,
      planStart: order.planStart,
      planEnd: order.planEnd,
      progress: order.plannedQuantity
        ? Math.min(100, Math.round(((order.goodQuantity || 0) / order.plannedQuantity) * 100))
        : 0,
      left: Number(left.toFixed(2)),
      width: Number(width.toFixed(2))
    }
  })
})

watch(() => props.token, () => loadActive())

onMounted(() => {
  loadActive()
})

function moduleNameFor(tab: TabKey): AdvancedRecordModule {
  return tab === 'PAYROLL' ? 'PAYROLL_RULE' : tab as AdvancedRecordModule
}

async function switchTab(tab: TabKey) {
  activeTab.value = tab
  statusFilter.value = ''
  await loadActive()
}

async function loadActive() {
  if (activeTab.value === 'GANTT') {
    await loadGantt()
    return
  }
  await loadRecords()
  if (activeTab.value === 'PAYROLL') {
    await loadReports()
  }
}

async function loadRecords() {
  if (!props.token || !currentModule.value) return
  loading.value = true
  try {
    const result = await fetchAdvancedRecords(props.token, {
      moduleName: currentModule.value,
      status: statusFilter.value,
      page: 1,
      size: 200
    })
    records.value = result.items || []
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadGantt() {
  if (!props.token) return
  loading.value = true
  try {
    const result = await fetchWorkOrders(props.token, { page: 1, size: 500 })
    workOrders.value = result.items || []
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '排产加载失败')
  } finally {
    loading.value = false
  }
}

async function loadReports() {
  if (!props.token) return
  try {
    const result = await fetchReports(props.token, { page: 1, size: 500 })
    reports.value = result.items || []
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '绩效数据加载失败')
  }
}

function openCreate() {
  editingRecord.value = null
  form.title = ''
  form.businessKey = ''
  form.status = currentStatuses.value[0]?.value || 'ACTIVE'
  form.payload = defaultPayload(activeTab.value)
  showModal.value = true
}

function openEdit(record: AdvancedRecordInfo) {
  editingRecord.value = record
  form.title = record.title
  form.businessKey = record.businessKey || ''
  form.status = record.status
  form.payload = { ...defaultPayload(activeTab.value), ...payloadOf(record) }
  showModal.value = true
}

async function saveRecord() {
  if (!currentModule.value || !form.title.trim()) return
  saving.value = true
  try {
    const payload = {
      moduleName: currentModule.value,
      businessKey: form.businessKey,
      title: form.title,
      status: form.status,
      payloadJson: JSON.stringify(form.payload)
    }
    if (editingRecord.value) {
      await updateAdvancedRecord(props.token, editingRecord.value.id, payload)
      emit('notice', '已更新')
    } else {
      await createAdvancedRecord(props.token, payload)
      emit('notice', '已创建')
    }
    showModal.value = false
    await loadActive()
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function changeStatus(record: AdvancedRecordInfo, status: string) {
  try {
    await updateAdvancedRecord(props.token, record.id, {
      moduleName: record.moduleName,
      businessKey: record.businessKey,
      title: record.title,
      status,
      payloadJson: record.payloadJson
    })
    emit('notice', '状态已更新')
    await loadActive()
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '状态更新失败')
  }
}

async function removeRecord(record: AdvancedRecordInfo) {
  if (!window.confirm(`确定删除「${record.title}」吗？`)) return
  try {
    await deleteAdvancedRecord(props.token, record.id)
    emit('notice', '已删除')
    await loadActive()
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '删除失败')
  }
}

async function sendTest(record: AdvancedRecordInfo) {
  const payload = payloadOf(record)
  await updateAdvancedRecord(props.token, record.id, {
    moduleName: record.moduleName,
    businessKey: record.businessKey,
    title: record.title,
    status: 'SENT',
    payloadJson: JSON.stringify({
      ...payload,
      lastTestAt: new Date().toISOString(),
      lastTestResult: 'SIMULATED_OK'
    })
  })
  emit('notice', '已记录测试推送')
  await loadActive()
}

function defaultPayload(tab: TabKey) {
  if (tab === 'SOP') return { processName: '', version: 'v1.0', owner: '', attachmentUrl: '', content: '' }
  if (tab === 'MESSAGE') return { channel: 'wechat_work', target: '', webhook: '', appId: '', template: '' }
  if (tab === 'QUALITY') return { workOrderCode: '', processName: '', defectName: '', defectQuantity: 0, reworkOwner: '', reworkDeadline: '', action: '' }
  if (tab === 'EQUIPMENT') return { type: 'equipment', location: '', owner: '', maintenanceCycle: '', nextMaintenance: '' }
  if (tab === 'PAYROLL') return { processName: '', effectiveMonth: new Date().toISOString().slice(0, 7), unitPrice: 0, defectPenalty: 0, description: '' }
  if (tab === 'FIELD_EVENT') return { eventType: '', workOrderCode: '', station: '', owner: '', description: '' }
  return {}
}

function payloadOf(record: AdvancedRecordInfo): Record<string, any> {
  if (!record.payloadJson) return {}
  try {
    return JSON.parse(record.payloadJson)
  } catch {
    return {}
  }
}

function statusText(status: string) {
  return currentStatuses.value.find(item => item.value === status)?.label || status
}

function channelText(channel?: string) {
  if (channel === 'wechat_work') return '企业微信'
  if (channel === 'wechat_mp') return '微信公众号'
  if (channel === 'webhook') return 'Webhook'
  return channel || '-'
}

function money(value: any) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
</script>

<style scoped>
.advanced-page {
  gap: 14px;
}

.advanced-tabs {
  margin-bottom: 0;
}

.advanced-toolbar {
  justify-content: space-between;
}

.row-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.toolbar-btn.danger {
  color: #c2410c;
}

.empty-cell,
.loading-line {
  text-align: center;
  color: #8a9693;
  padding: 24px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #eef3f1;
  color: #586360;
}

.status-pill.active,
.status-pill.sent,
.status-pill.closed {
  background: #e8f7f3;
  color: #00a77c;
}

.status-pill.open,
.status-pill.reworking,
.status-pill.processing,
.status-pill.maintenance {
  background: #fff4e6;
  color: #b45309;
}

.status-pill.archived,
.status-pill.disabled,
.status-pill.offline,
.status-pill.draft {
  background: #f1f5f4;
  color: #6b7280;
}

.advanced-modal {
  width: min(880px, 92vw);
}

.advanced-modal textarea {
  min-height: 88px;
}

.gantt-wrap,
.payroll-layout {
  display: grid;
  gap: 14px;
}

.gantt-header,
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: #586360;
  font-size: 13px;
}

.gantt-header strong,
.section-heading strong {
  color: #1f2d2a;
  font-size: 16px;
}

.gantt-list {
  display: grid;
  gap: 10px;
}

.gantt-row {
  display: grid;
  grid-template-columns: 190px minmax(280px, 1fr) 230px;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border: 1px solid #edf1f0;
  border-radius: 6px;
}

.gantt-name {
  display: grid;
  gap: 4px;
}

.gantt-name span,
.gantt-date {
  color: #8a9693;
  font-size: 12px;
}

.gantt-line {
  position: relative;
  height: 28px;
  background: #eef3f1;
  border-radius: 4px;
  overflow: hidden;
}

.gantt-bar {
  position: absolute;
  top: 4px;
  bottom: 4px;
  min-width: 42px;
  border-radius: 4px;
  background: #00b889;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.gantt-warning {
  color: #b45309;
  font-size: 13px;
}

.payroll-layout {
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
}

@media (max-width: 1100px) {
  .payroll-layout,
  .gantt-row {
    grid-template-columns: 1fr;
  }
}
</style>
