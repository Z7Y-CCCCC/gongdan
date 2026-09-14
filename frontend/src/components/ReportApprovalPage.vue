<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  approveReport,
  fetchReportImageBlob,
  fetchReports,
  rejectReport,
  type ReportStatus,
  type WorkOrderReportInfo
} from '../api'
import UiIcon from './UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchAllPages } from '../utils/pagination'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const status = ref<ReportStatus>('PENDING')
const allReports = ref<WorkOrderReportInfo[]>([])
const reports = computed(() => allReports.value.filter(item => item.status === status.value))
const loading = ref(false)
const beginLoad = useLatestRequest()
const detail = ref<WorkOrderReportInfo | null>(null)
const imageObjectUrls = ref<Record<number, string>>({})
const rejectTarget = ref<WorkOrderReportInfo | null>(null)
const rejectRemark = ref('')
const filters = reactive({
  workOrderCode: '',
  reporterName: '',
  period: ''
})

const tabs = computed(() => [
  { key: 'PENDING' as ReportStatus, label: '未审批', count: allReports.value.filter(item => item.status === 'PENDING').length },
  { key: 'APPROVED' as ReportStatus, label: '已审批', count: allReports.value.filter(item => item.status === 'APPROVED').length },
  { key: 'REJECTED' as ReportStatus, label: '已驳回', count: allReports.value.filter(item => item.status === 'REJECTED').length }
])

async function loadReports() {
  const isCurrent = beginLoad()
  loading.value = true
  try {
    const search = { ...filters }
    const res = await fetchAllPages(params => fetchReports(props.token, { ...search, ...params }), 500, isCurrent)
    if (!isCurrent()) return
    allReports.value = res.items
  } catch (e) {
    if (isCurrent()) emit('notice', e instanceof Error ? e.message : '报工列表加载失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

async function handleApprove(item: WorkOrderReportInfo) {
  try {
    const updated = await approveReport(props.token, item.id)
    emit('notice', `报工「${updated.workOrderCode} / ${updated.processName}」已审批通过`)
    await openDetail(updated)
    await loadReports()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '审批失败')
  }
}

function openReject(item: WorkOrderReportInfo) {
  rejectTarget.value = item
  rejectRemark.value = ''
}

async function submitReject() {
  if (!rejectTarget.value) return
  try {
    const updated = await rejectReport(props.token, rejectTarget.value.id, rejectRemark.value)
    emit('notice', `报工「${updated.workOrderCode} / ${updated.processName}」已驳回`)
    rejectTarget.value = null
    await openDetail(updated)
    await loadReports()
  } catch (e) {
    emit('notice', e instanceof Error ? e.message : '驳回失败')
  }
}

function statusLabel(value: ReportStatus) {
  return value === 'PENDING' ? '未审批' : value === 'APPROVED' ? '已审批' : '已驳回'
}

function minutesLabel(value: number) {
  const hours = Math.floor((value || 0) / 60)
  const minutes = (value || 0) % 60
  return hours > 0 ? `${hours}小时${minutes}分钟` : `${minutes}分钟`
}

async function openDetail(item: WorkOrderReportInfo) {
  revokeImageObjectUrls()
  detail.value = item
  for (const image of item.images) {
    try {
      const blob = await fetchReportImageBlob(props.token, image.id)
      imageObjectUrls.value = {
        ...imageObjectUrls.value,
        [image.id]: URL.createObjectURL(blob)
      }
    } catch (e) {
      emit('notice', e instanceof Error ? e.message : '图片加载失败')
    }
  }
}

function closeDetail() {
  detail.value = null
  revokeImageObjectUrls()
}

function imageUrl(id: number) {
  return imageObjectUrls.value[id] || ''
}

function revokeImageObjectUrls() {
  Object.values(imageObjectUrls.value).forEach(url => URL.revokeObjectURL(url))
  imageObjectUrls.value = {}
}

useRefreshOnActivated(loadReports)
onMounted(loadReports)
onBeforeUnmount(revokeImageObjectUrls)
</script>

<template>
  <section class="content">
    <div class="tab-row">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-pill"
        :class="{ active: status === tab.key }"
        type="button"
        @click="status = tab.key"
      >
        {{ tab.label }} {{ tab.count }}
      </button>
    </div>

    <div class="filters">
      <label>
        <span>工单编号</span>
        <input v-model="filters.workOrderCode" placeholder="请输入工单编号" />
      </label>
      <label>
        <span>报工人</span>
        <input v-model="filters.reporterName" placeholder="请输入报工人" />
      </label>
      <label>
        <span>时间</span>
        <select v-model="filters.period">
          <option value="">全部</option>
          <option value="today">今天</option>
          <option value="lastMonth">上月起</option>
        </select>
      </label>
      <div class="filter-actions">
        <button class="primary-btn query-btn" type="button" @click="loadReports">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          查询
        </button>
      </div>
    </div>

    <div class="toolbar">
      <button class="toolbar-btn" type="button" @click="loadReports">
        <UiIcon name="refresh" />
        刷新
      </button>
      <span class="muted">审批通过后才会同步更新工单工序进度</span>
      <TableConfigButton table-id="report-approval-list-table" />
    </div>

    <div class="table-container">
      <table id="report-approval-list-table" class="data-table">
        <thead>
          <tr>
            <th style="width:70px">序号</th>
            <th>工单编号</th>
            <th>产品名称</th>
            <th>工序</th>
            <th>报工人</th>
            <th>报工数</th>
            <th>良品数</th>
            <th>不良数</th>
            <th>报工时长</th>
            <th>状态</th>
            <th style="width:180px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in reports" :key="item.id">
            <td>{{ index + 1 }}</td>
            <td class="green-link">{{ item.workOrderCode }}</td>
            <td>{{ item.productName || '-' }}</td>
            <td>{{ item.processSequenceNo }}. {{ item.processName }}</td>
            <td>{{ item.reporterName }}</td>
            <td>{{ item.reportQuantity }}</td>
            <td>{{ item.goodQuantity }}</td>
            <td>{{ item.defectQuantity }}</td>
            <td>{{ minutesLabel(item.reportDurationMinutes) }}</td>
            <td>
              <span class="status-badge" :class="{ disabled: item.status === 'REJECTED' }">{{ statusLabel(item.status) }}</span>
            </td>
            <td class="action-cell">
              <button class="action-link" type="button" @click="openDetail(item)"><UiIcon name="eye" />详情</button>
              <button v-if="item.status === 'PENDING'" class="action-link" type="button" @click="handleApprove(item)"><UiIcon name="check" />通过</button>
              <button v-if="item.status === 'PENDING'" class="action-link danger" type="button" @click="openReject(item)"><UiIcon name="close" />驳回</button>
            </td>
          </tr>
          <tr v-if="!loading && reports.length === 0">
            <td colspan="11" style="text-align:center;color:var(--text-muted);padding:32px">暂无报工记录</td>
          </tr>
          <tr v-if="loading">
            <td colspan="11" style="text-align:center;color:#8a9693;padding:32px">加载中...</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="detail" class="modal-backdrop">
      <div class="modal report-detail-modal">
        <div class="modal-header">
          <h2>报工详情</h2>
          <button class="close-btn" type="button" @click="closeDetail">×</button>
        </div>
        <div class="report-detail">
          <div><span>工单编号</span><strong>{{ detail.workOrderCode }}</strong></div>
          <div><span>产品信息</span><strong>{{ detail.productName || '-' }} / {{ detail.productSpec || '-' }}</strong></div>
          <div><span>工序</span><strong>{{ detail.processSequenceNo }}. {{ detail.processName }}</strong></div>
          <div><span>报工人</span><strong>{{ detail.reporterName }}</strong></div>
          <div><span>报工数</span><strong>{{ detail.reportQuantity }}</strong></div>
          <div><span>良品数</span><strong>{{ detail.goodQuantity }}</strong></div>
          <div><span>不良数</span><strong>{{ detail.defectQuantity }}</strong></div>
          <div><span>不良品项</span><strong>{{ detail.defectItemName || '-' }}</strong></div>
          <div><span>报工时长</span><strong>{{ minutesLabel(detail.reportDurationMinutes) }}</strong></div>
          <div><span>开始时间</span><strong>{{ detail.startTime || '-' }}</strong></div>
          <div><span>结束时间</span><strong>{{ detail.endTime || '-' }}</strong></div>
          <div><span>领料产品</span><strong>{{ detail.materialName || '-' }}</strong></div>
          <div class="full"><span>备注</span><strong>{{ detail.remark || '-' }}</strong></div>
          <div class="full"><span>审批备注</span><strong>{{ detail.approvalRemark || '-' }}</strong></div>
          <div class="full">
            <span>报工图片</span>
            <div class="report-images">
              <a v-for="image in detail.images" :key="image.id" :href="imageUrl(image.id)" target="_blank">
                <img v-if="imageUrl(image.id)" :src="imageUrl(image.id)" :alt="image.originalFileName" />
              </a>
              <em v-if="detail.images.length === 0">暂无图片</em>
            </div>
          </div>
        </div>
        <div v-if="detail.status === 'PENDING'" class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="openReject(detail)"><UiIcon name="close" />驳回</button>
          <button class="primary-btn wide" type="button" @click="handleApprove(detail)"><UiIcon name="check" />审批通过</button>
        </div>
      </div>
    </div>

    <div v-if="rejectTarget" class="modal-backdrop">
      <div class="modal batch-modal">
        <div class="modal-header">
          <h2>驳回报工</h2>
          <button class="close-btn" type="button" @click="rejectTarget = null">×</button>
        </div>
        <div style="padding:18px">
          <p class="muted">请填写驳回原因，手机端会在已驳回列表中看到。</p>
          <textarea v-model="rejectRemark" class="reject-textarea" placeholder="请输入驳回原因"></textarea>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="rejectTarget = null"><UiIcon name="close" />取消</button>
          <button class="primary-btn wide" type="button" @click="submitReject"><UiIcon name="check" />确定驳回</button>
        </div>
      </div>
    </div>
  </section>
</template>
