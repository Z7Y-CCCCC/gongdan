<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { fetchOnlineUsers, forceLogoutOnlineUser, type MonitorOnlineUserInfo } from '../api'
import TableConfigButton from './TableConfigButton.vue'
import UiIcon from './UiIcon.vue'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { formatUiDate, t } from '../i18n'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()
const rows = ref<MonitorOnlineUserInfo[]>([]), total = ref(0), page = ref(1), size = ref(10)
const loading = ref(false), writing = ref(false), loadError = ref(''), mutationError = ref('')
const filters = reactive({ ipAddress: '', loginName: '' })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const { visible: confirmVisible, title: confirmTitle, message: confirmMessage, loading: confirmLoading,
  confirm: requestConfirm, handleConfirm, handleCancel } = useDeleteConfirm()
const beginLoad = useLatestRequest()
let appliedFilters = { ipAddress: '', loginName: '' }, attemptedPage = 1, generation = 0, active = true, disposed = false
function context() {
  const token = props.token, epoch = generation
  return { token, current: () => active && !disposed && props.token === token && epoch === generation }
}
const formatDate = (value?: string) => value ? formatUiDate(value, true) : '-'
async function load(targetPage = page.value) {
  const request = context(), latest = beginLoad(), criteria = { ...appliedFilters }, pageSize = size.value
  if (!request.token || !active) return
  const current = () => request.current() && latest()
  attemptedPage = targetPage; loading.value = true; loadError.value = ''
  try {
    for (;;) {
      const result = await fetchOnlineUsers(request.token, { ...criteria, page: targetPage, size: pageSize })
      if (!current()) return
      const lastPage = Math.max(1, Math.ceil(result.total / pageSize))
      if (result.total > 0 && targetPage > lastPage) { targetPage = lastPage; attemptedPage = lastPage; continue }
      rows.value = result.items; total.value = result.total; page.value = result.total === 0 ? 1 : targetPage
      return
    }
  } catch (error) {
    if (current()) { loadError.value = error instanceof Error ? error.message : '在线用户加载失败'; emit('notice', loadError.value) }
  } finally { if (current()) loading.value = false }
}
function search() {
  if (writing.value) return
  appliedFilters = { ipAddress: filters.ipAddress.trim(), loginName: filters.loginName.trim() }
  void load(1)
}
function reset() { filters.ipAddress = ''; filters.loginName = ''; search() }
function changePage(next: number) {
  if (loading.value || writing.value || loadError.value || next < 1 || next > totalPages.value || next === page.value) return
  void load(next)
}
async function forceLogout(row: MonitorOnlineUserInfo) {
  if (writing.value || confirmVisible.value || !props.token) return
  const request = context(), id = row.id, loginName = row.loginName
  const accepted = await requestConfirm({ title: '强制退出会话', message: `确定强制退出「${loginName}」的当前会话吗？` })
  if (!accepted || !request.current()) { if (request.current()) handleCancel(); return }
  writing.value = true; confirmLoading.value = true; mutationError.value = ''; beginLoad(); loading.value = false
  try {
    await forceLogoutOnlineUser(request.token, id)
    if (!request.current()) return
    if (rows.value.some(item => item.id === id)) total.value = Math.max(0, total.value - 1)
    rows.value = rows.value.filter(item => item.id !== id)
    emit('notice', `已强制退出 ${loginName}`)
    await load()
  } catch (error) {
    if (request.current()) { mutationError.value = error instanceof Error ? error.message : '强退失败'; emit('notice', mutationError.value) }
  } finally { if (request.current()) { writing.value = false; confirmLoading.value = false; handleCancel() } }
}
function cancelContext() { generation++; beginLoad(); handleCancel(); writing.value = false; confirmLoading.value = false; loading.value = false }
onMounted(() => { void load() })
onActivated(() => { active = true })
useRefreshOnActivated(() => load())
onDeactivated(() => { active = false; cancelContext() })
watch(() => props.token, () => {
  cancelContext(); rows.value = []; total.value = 0; page.value = 1; attemptedPage = 1; loadError.value = ''; mutationError.value = ''
  filters.ipAddress = ''; filters.loginName = ''; appliedFilters = { ipAddress: '', loginName: '' }; void load(1)
}, { flush: 'sync' })
onBeforeUnmount(() => { active = false; disposed = true; cancelContext() })
</script>

<template>
  <section class="content monitor-page">
    <div class="monitor-page-header">
      <div>
        <h1>在线用户</h1>
      </div>
      <button class="toolbar-btn ui-icon-button" type="button" :disabled="writing" title="刷新" @click="load()" aria-label="刷新"><UiIcon name="refresh" /></button>
    </div>

    <div class="filters monitor-filters">
      <label><span>登录地址</span><input v-model="filters.ipAddress" :disabled="writing" placeholder="请输入登录地址" @keydown.enter="search" /></label>
      <label><span>用户名称</span><input v-model="filters.loginName" :disabled="writing" placeholder="请输入用户名称" @keydown.enter="search" /></label>
      <div class="filter-actions">
        <button class="primary-btn query-btn ui-icon-button" type="button" :disabled="writing" @click="search" title="查询" aria-label="查询"><UiIcon name="search" /></button>
        <button class="ghost-btn query-btn ui-icon-button" type="button" :disabled="writing" @click="reset" title="重置" aria-label="重置"><UiIcon name="reset" /></button>
      </div>
    </div>

    <div class="monitor-section-caption">
      <div><span>在线会话</span><small>共 {{ total }} 条</small></div>
      <TableConfigButton table-id="online-users-table" />
    </div>
    <p v-if="loadError" role="alert">{{ loadError }} <button class="ui-icon-button" type="button" :disabled="writing" @click="load(attemptedPage)" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
    <p v-if="mutationError" role="alert">{{ mutationError }}</p>
    <div class="table-container monitor-table-container" :aria-busy="loading">
      <table id="online-users-table" class="data-table monitor-table">
        <thead>
          <tr>
            <th>序号</th><th>会话编号</th><th>登录名称</th><th>所属部门</th><th>主机</th><th>登录地点</th><th>操作系统</th><th>浏览器</th><th>登录时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in rows" :key="row.id">
            <td>{{ (page - 1) * size + index + 1 }}</td>
            <td class="mono-text">{{ row.sessionId }}</td>
            <td class="strong-text">{{ row.loginName }}</td>
            <td>{{ row.departmentName || '-' }}</td>
            <td>{{ row.ipAddress || '-' }}</td>
            <td>{{ row.loginLocation || '-' }}</td>
            <td>{{ row.operatingSystem || '-' }}</td>
            <td>{{ row.browser || '-' }}</td>
            <td>{{ formatDate(row.loginTime) }}</td>
            <td><button class="table-action danger" :disabled="writing || confirmVisible" type="button" @click="forceLogout(row)"><UiIcon name="close" :size="14" />强退</button></td>
          </tr>
          <tr v-if="!loading && !loadError && rows.length === 0"><td colspan="10" class="monitor-empty">暂无在线用户</td></tr>
          <tr v-if="loading"><td colspan="10" class="monitor-empty" role="status">加载中...</td></tr>
        </tbody>
      </table>
    </div>

    <div class="monitor-pagination">
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button type="button" :disabled="loading || writing || !!loadError || page === 1" @click="changePage(page - 1)" title="上一页" aria-label="上一页" class="ui-icon-button"><UiIcon name="arrowLeft" /></button>
      <button type="button" :disabled="loading || writing || !!loadError || page === totalPages" @click="changePage(page + 1)" title="下一页" aria-label="下一页" class="ui-icon-button"><UiIcon name="arrowRight" /></button>
    </div>

    <DeleteConfirmDialog
      v-model:visible="confirmVisible"
      :title="confirmTitle"
      :message="confirmMessage"
      :loading="confirmLoading"
      :confirm-label="t('强制退出')"
      confirm-icon="close"
      confirm-tone="danger"
      @confirm="handleConfirm"
      @update:visible="(value: boolean) => { if (!value) handleCancel() }"
    />
  </section>
</template>
