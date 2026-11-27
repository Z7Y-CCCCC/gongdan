<template>
  <section class="content advanced-page">
    <div class="category-tabs advanced-tabs" role="group" aria-label="高级运营模块">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="cat-tab"
        :class="{ active: activeTab === tab.key }"
        :aria-pressed="activeTab === tab.key"
        type="button"
        :disabled="saving || pendingRecords.size > 0"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="count-badge">{{ tab.count }}</span>
      </button>
    </div>

    <div class="toolbar advanced-toolbar">
      <div class="toolbar-left">
        <button v-if="currentModule" class="primary-btn create-btn ui-icon-button" type="button" :title="'新建' + currentTabLabel" :aria-label="'新建' + currentTabLabel" @click="openCreate"><UiIcon name="plus" /></button>
        <button class="ghost-btn ui-icon-button" type="button" @click="loadActive" title="刷新" aria-label="刷新"><UiIcon name="refresh" /></button>
      </div>
      <div class="filters inline-filters">
        <label v-if="currentModule">
          <span>状态</span>
          <select v-model="statusFilter" @change="loadActive">
            <option value="">全部</option>
            <option v-for="status in currentStatuses" :key="status.value" :value="status.value">{{ status.label }}</option>
          </select>
        </label>
        <label v-if="activeTab === 'PAYROLL'"><span>核算月份</span><input v-model="payrollMonth" type="month" aria-label="核算月份" /></label>
      </div>
    </div>

    <div v-if="loading" class="loading-line" role="status">加载中...</div>
    <div v-else-if="loadError" class="loading-line" role="alert">{{ loadError }} <button class="ghost-btn ui-icon-button" type="button" title="重试加载高级运营" aria-label="重试加载高级运营" @click="loadActive"><UiIcon name="refresh" /></button></div>
    <div v-else-if="activeTab === 'SOP'" class="table-container">
      <div class="table-config-line"><TableConfigButton table-id="advanced-sop-table" /></div>
      <table id="advanced-sop-table" class="data-table">
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
              <button class="toolbar-btn ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="openEdit(record)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
              <button class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="changeStatus(record, record.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE')">
                <UiIcon :name="record.status === 'ACTIVE' ? 'archive' : 'check'" />
                {{ record.status === 'ACTIVE' ? '归档' : '启用' }}
              </button>
              <button class="toolbar-btn danger ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="removeRecord(record)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无 SOP 文档</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'MESSAGE'" class="table-container">
      <div class="table-config-line"><TableConfigButton table-id="advanced-message-table" /></div>
      <table id="advanced-message-table" class="data-table">
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
              <button class="toolbar-btn ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="openEdit(record)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
              <button class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" :aria-busy="pendingRecords.has(record.id)" @click="sendTest(record)"><UiIcon name="send" />发送测试</button>
              <button class="toolbar-btn danger ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="removeRecord(record)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="6" class="empty-cell">暂无推送配置</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'QUALITY'" class="table-container">
      <div class="table-config-line"><TableConfigButton table-id="advanced-quality-table" /></div>
      <table id="advanced-quality-table" class="data-table">
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
              <button class="toolbar-btn ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="openEdit(record)" :title="record.status === 'CLOSED' ? '查看/编辑' : '编辑'" :aria-label="record.status === 'CLOSED' ? '查看/编辑' : '编辑'"><UiIcon :name="record.status === 'CLOSED' ? 'eye' : 'edit'" /></button>
              <button v-if="record.status === 'OPEN'" class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="changeStatus(record, 'REWORKING')"><UiIcon name="rework" />转返工</button>
              <button v-if="record.status !== 'CLOSED'" class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="changeStatus(record, 'CLOSED')" title="关闭记录" aria-label="关闭记录"><UiIcon name="close" />关闭</button>
              <button class="toolbar-btn danger ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="removeRecord(record)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无质检返工记录</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeTab === 'EQUIPMENT'" class="table-container">
      <div class="table-config-line"><TableConfigButton table-id="advanced-equipment-table" /></div>
      <table id="advanced-equipment-table" class="data-table">
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
              <button class="toolbar-btn ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="openEdit(record)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
              <button v-if="record.status !== 'MAINTENANCE'" class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="changeStatus(record, 'MAINTENANCE')"><UiIcon name="wrench" />保养</button>
              <button v-if="record.status !== 'ACTIVE'" class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="changeStatus(record, 'ACTIVE')"><UiIcon name="check" />启用</button>
              <button class="toolbar-btn danger ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="removeRecord(record)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
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
          <TableConfigButton table-id="advanced-payroll-rules-table" />
        </div>
        <table id="advanced-payroll-rules-table" class="data-table">
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
                <button class="toolbar-btn ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="openEdit(record)" title="编辑" aria-label="编辑"><UiIcon name="edit" /></button>
                <button class="toolbar-btn danger ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="removeRecord(record)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
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
          <TableConfigButton table-id="advanced-payroll-result-table" />
        </div>
        <table id="advanced-payroll-result-table" class="data-table">
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
      <div class="table-config-line"><TableConfigButton table-id="advanced-field-event-table" /></div>
      <table id="advanced-field-event-table" class="data-table">
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
              <button class="toolbar-btn ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="openEdit(record)" :title="record.status === 'CLOSED' ? '查看/编辑' : '处理'" :aria-label="record.status === 'CLOSED' ? '查看/编辑' : '处理'"><UiIcon :name="record.status === 'CLOSED' ? 'eye' : 'edit'" /></button>
              <button v-if="record.status !== 'CLOSED'" class="toolbar-btn" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="changeStatus(record, 'CLOSED')" title="关闭记录" aria-label="关闭记录"><UiIcon name="close" />关闭</button>
              <button class="toolbar-btn danger ui-icon-button" type="button" :disabled="saving || pendingRecords.has(record.id)" @click="removeRecord(record)" title="删除" aria-label="删除"><UiIcon name="trash" /></button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0"><td colspan="7" class="empty-cell">暂无现场事件</td></tr>
        </tbody>
      </table>
    </div>


    <Transition v-bind="workspaceDialogMotion">
    <div v-if="showModal" class="modal-backdrop workspace-dialog-motion-backdrop">
      <section ref="formDialog" class="modal advanced-modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" aria-labelledby="advanced-editor-title" tabindex="-1">
        <header class="modal-header">
          <h2 id="advanced-editor-title">{{ editingRecord ? '编辑' : '新建' }}{{ currentTabLabel }}</h2>
          <button class="close-btn ui-icon-button" type="button" :disabled="saving" @click="closeEditor" title="关闭" aria-label="关闭"><UiIcon name="close" /></button>
        </header>
        <form class="create-form two-column" @submit.prevent="saveRecord">
          <fieldset class="advanced-form-fields" :disabled="saving">
          <label class="full-row">
            <span><em>*</em> 名称</span>
            <input ref="titleInput" v-model.trim="form.title" required placeholder="请输入名称" />
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
            <label><span>{{ form.payload.channel === 'wechat_mp' ? 'OpenID' : '接收对象' }}</span><input v-model.trim="form.payload.target" :required="form.payload.channel === 'wechat_mp'" /></label>
            <label v-if="form.payload.channel !== 'wechat_mp'"><span>Webhook</span><input v-model.trim="form.payload.webhook" type="url" required /></label>
            <label v-if="form.payload.channel === 'wechat_mp'"><span>AppId</span><input v-model.trim="form.payload.appId" required /></label>
            <label v-if="form.payload.channel === 'wechat_mp'"><span>模板ID</span><input v-model.trim="form.payload.templateId" required /></label>
            <label class="full-row"><span>{{ form.payload.channel === 'wechat_mp' ? '模板数据（JSON）' : '消息内容' }}</span><textarea v-model.trim="form.payload.template" rows="4" required></textarea></label>
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
            <button class="ghost-btn wide ui-icon-button" type="button" :disabled="saving" @click="closeEditor" title="取消" aria-label="取消"><UiIcon name="close" /></button>
            <button class="primary-btn wide" :disabled="saving" type="submit"><UiIcon name="save" />{{ saving ? '保存中...' : '保存' }}</button>
          </footer>
          <p v-if="saveError" class="full-row" role="alert">{{ saveError }}</p>
          </fieldset>
        </form>
      </section>
    </div>
    </Transition>

    <DeleteConfirmDialog
      v-model:visible="confirmVisible"
      :title="confirmTitle"
      :message="confirmMessage"
      :loading="confirmLoading"
      @confirm="handleConfirm"
      @update:visible="(value: boolean) => { if (!value) handleCancel() }"
    />
  </section>
</template>

<script setup lang="ts">
import { t } from '../i18n'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  createAdvancedRecord,
  deleteAdvancedRecord,
  fetchAdvancedRecords,
  fetchReports,
  fetchWorkOrders,
  updateAdvancedRecord,
  sendAdvancedMessageTest,
  type AdvancedRecordInfo,
  type AdvancedRecordModule,
  type WorkOrderInfo,
  type WorkOrderReportInfo
} from '../api'
import UiIcon from './UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useDialogFocus } from '../composables/useDialogFocus'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchAllPages } from '../utils/pagination'
import { advancedWorkspaceSections, type AdvancedWorkspaceSection } from '../utils/advancedWorkspace'
import { workspaceDialogMotion } from '../workspaceDialogMotion'

const props = defineProps<{ token: string; section?: AdvancedWorkspaceSection }>()
const emit = defineEmits<{ (e: 'notice', message: string): void; (e: 'section-change', section: AdvancedWorkspaceSection): void }>()
const {
  visible: confirmVisible,
  title: confirmTitle,
  message: confirmMessage,
  loading: confirmLoading,
  confirm: requestConfirm,
  handleConfirm,
  handleCancel
} = useDeleteConfirm()

type TabKey = AdvancedWorkspaceSection
type StatusItem = { value: string; label: string }

const activeTab = ref<TabKey>(props.section ?? 'SOP')
const records = ref<AdvancedRecordInfo[]>([])
const workOrders = ref<WorkOrderInfo[]>([])
const reports = ref<WorkOrderReportInfo[]>([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const pendingRecords = ref(new Set<number>())
const beginLoad = useLatestRequest()
let sessionGeneration = 0
const payrollMonth = ref(`${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`)
const statusFilter = ref('')
const showModal = ref(false)
const editingRecord = ref<AdvancedRecordInfo | null>(null)
const formDialog = ref<HTMLElement | null>(null)
const titleInput = ref<HTMLInputElement | null>(null)
let initialForm = ''
useDialogFocus(formDialog, () => showModal.value, closeEditor, () => titleInput.value)

const form = reactive({
  title: '',
  businessKey: '',
  status: 'ACTIVE',
  payload: {} as Record<string, any>
})

const moduleTabs = advancedWorkspaceSections

const statusMap: Record<string, StatusItem[]> = {
  SOP: [
    { value: 'ACTIVE', label: '启用' },
    { value: 'ARCHIVED', label: '归档' }
  ],
  MESSAGE: [
    { value: 'DRAFT', label: '草稿' },
    { value: 'ACTIVE', label: '启用' },
    { value: 'SENT', label: '已发送' },
    { value: 'FAILED', label: '发送失败' },
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
  count: tab.key === activeTab.value && activeTab.value !== 'GANTT' && !loading.value && !loadError.value ? records.value.length : undefined
})))

const payrollRows = computed(() => {
  const rules = records.value
    .filter(record => record.status === 'ACTIVE')
    .map(record => payloadOf(record))
    .filter(rule => !rule.effectiveMonth || rule.effectiveMonth === payrollMonth.value)
  const rows = new Map<string, { name: string; goodQuantity: number; defectQuantity: number; processSet: Set<string>; amount: number }>()
  reports.value.filter(report => report.status === 'APPROVED' && report.createdAt?.slice(0, 7) === payrollMonth.value).forEach(report => {
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

watch(() => props.token, () => {
  sessionGeneration++
  beginLoad()
  records.value = []
  workOrders.value = []
  reports.value = []
  pendingRecords.value = new Set()
  saving.value = false
  showModal.value = false
  handleCancel()
  void loadActive()
})

onMounted(() => {
  loadActive()
})
watch(() => props.section, section => { if (section && section !== activeTab.value) void switchTab(section) })
useRefreshOnActivated(loadActive)
onBeforeUnmount(() => { sessionGeneration++; beginLoad(); handleCancel() })

function moduleNameFor(tab: TabKey): AdvancedRecordModule {
  return tab === 'PAYROLL' ? 'PAYROLL_RULE' : tab as AdvancedRecordModule
}

async function switchTab(tab: TabKey) {
  if (tab === activeTab.value) return
  if (saving.value || pendingRecords.value.size > 0 || !closeEditor()) {
    emit('section-change', activeTab.value)
    return
  }
  activeTab.value = tab
  emit('section-change', tab)
  statusFilter.value = ''
  records.value = []
  workOrders.value = []
  reports.value = []
  showModal.value = false
  await loadActive()
}

function closeEditor() {
  if (!showModal.value) return true
  if (saving.value) return false
  if (JSON.stringify(form) !== initialForm && !window.confirm(t('关闭后，本次修改不会保存。'))) return false
  showModal.value = false
  return true
}

async function loadActive() {
  const isCurrent = beginLoad()
  const requestToken = props.token
  const tab = activeTab.value
  const status = statusFilter.value
  if (!requestToken) { loading.value = false; return }
  const current = () => isCurrent() && props.token === requestToken && activeTab.value === tab && statusFilter.value === status
  loading.value = true
  loadError.value = ''
  try {
    if (tab === 'GANTT') {
      const result = await fetchAllPages(params => fetchWorkOrders(requestToken, params), 500, current)
      if (current()) workOrders.value = result.items
    } else {
      const [recordResult, reportResult] = await Promise.all([
        fetchAllPages(params => fetchAdvancedRecords(requestToken, { ...params, moduleName: moduleNameFor(tab), status }), 200, current),
        tab === 'PAYROLL' ? fetchAllPages(params => fetchReports(requestToken, params), 500, current) : Promise.resolve({ items: [], total: 0 })
      ])
      if (!current()) return
      records.value = recordResult.items
      reports.value = reportResult.items
    }
  } catch (error) {
    if (current()) loadError.value = error instanceof Error ? error.message : '加载失败，请重试'
  } finally {
    if (current()) loading.value = false
  }
}

function openCreate() {
  if (saving.value) return
  saveError.value = ''
  editingRecord.value = null
  form.title = ''
  form.businessKey = ''
  form.status = currentStatuses.value[0]?.value || 'ACTIVE'
  form.payload = defaultPayload(activeTab.value)
  initialForm = JSON.stringify(form)
  showModal.value = true
}

function openEdit(record: AdvancedRecordInfo) {
  if (saving.value || pendingRecords.value.has(record.id)) return
  saveError.value = ''
  editingRecord.value = record
  form.title = record.title
  form.businessKey = record.businessKey || ''
  form.status = record.status
  form.payload = { ...defaultPayload(activeTab.value), ...payloadOf(record) }
  initialForm = JSON.stringify(form)
  showModal.value = true
}

async function saveRecord() {
  if (saving.value || !currentModule.value || !form.title.trim() || !props.token) return
  const requestToken = props.token
  const requestGeneration = sessionGeneration
  const current = () => props.token === requestToken && sessionGeneration === requestGeneration
  saving.value = true
  saveError.value = ''
  try {
    const payload = {
      moduleName: currentModule.value,
      businessKey: form.businessKey,
      title: form.title,
      status: form.status,
      payloadJson: JSON.stringify(form.payload)
    }
    if (editingRecord.value) {
      await updateAdvancedRecord(requestToken, editingRecord.value.id, payload)
      if (!current()) return
      emit('notice', '已更新')
    } else {
      await createAdvancedRecord(requestToken, payload)
      if (!current()) return
      emit('notice', '已创建')
    }
    showModal.value = false
    await loadActive()
  } catch (error) {
    if (current()) saveError.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    if (current()) saving.value = false
  }
}

async function changeStatus(record: AdvancedRecordInfo, status: string) {
  if (saving.value || pendingRecords.value.has(record.id) || !props.token) return
  const requestToken = props.token
  const generation = sessionGeneration
  const current = () => props.token === requestToken && sessionGeneration === generation
  pendingRecords.value.add(record.id)
  try {
    await updateAdvancedRecord(requestToken, record.id, {
      moduleName: record.moduleName,
      businessKey: record.businessKey,
      title: record.title,
      status,
      payloadJson: record.payloadJson
    })
    if (!current()) return
    emit('notice', '状态已更新')
    await loadActive()
  } catch (error) {
    if (current()) emit('notice', error instanceof Error ? error.message : '状态更新失败')
  } finally {
    if (current()) pendingRecords.value.delete(record.id)
  }
}

async function removeRecord(record: AdvancedRecordInfo) {
  if (saving.value || pendingRecords.value.has(record.id) || confirmVisible.value || !props.token) return
  const requestToken = props.token
  const generation = sessionGeneration
  const current = () => props.token === requestToken && sessionGeneration === generation
  pendingRecords.value.add(record.id)
  try {
    const accepted = await requestConfirm({ title: '删除记录', message: `确定删除「${record.title}」吗？` })
    confirmVisible.value = false
    if (!accepted || !current()) return
    await deleteAdvancedRecord(requestToken, record.id)
    if (!current()) return
    emit('notice', '已删除')
    await loadActive()
  } catch (error) {
    if (current()) emit('notice', error instanceof Error ? error.message : '删除失败')
  } finally {
    if (current()) pendingRecords.value.delete(record.id)
  }
}

async function sendTest(record: AdvancedRecordInfo) {
  if (saving.value || pendingRecords.value.has(record.id) || !props.token) return
  const requestToken = props.token
  const generation = sessionGeneration
  const current = () => props.token === requestToken && sessionGeneration === generation
  pendingRecords.value.add(record.id)
  try {
    const result = await sendAdvancedMessageTest(requestToken, record.id)
    if (!current()) return
    if (!result.record || result.record.id !== record.id) throw new Error('测试消息响应不一致，请刷新后重试')
    records.value = records.value.map(item => item.id === record.id ? result.record : item)
    emit('notice', result.message || (result.success ? '测试消息已发送' : '测试消息发送失败'))
    await loadActive()
  } catch (error) {
    if (current()) emit('notice', error instanceof Error ? error.message : '测试消息发送失败')
  } finally {
    if (current()) pendingRecords.value.delete(record.id)
  }
}

function defaultPayload(tab: TabKey) {
  if (tab === 'SOP') return { processName: '', version: 'v1.0', owner: '', attachmentUrl: '', content: '' }
  if (tab === 'MESSAGE') return { channel: 'wechat_work', target: '', webhook: '', appId: '', templateId: '', template: '' }
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
  if (channel === 'wechat_work') return t('企业微信')
  if (channel === 'wechat_mp') return t('微信公众号')
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
.advanced-form-fields { display: contents; }
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
