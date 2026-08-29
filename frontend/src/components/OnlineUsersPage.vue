<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchOnlineUsers, forceLogoutOnlineUser, type MonitorOnlineUserInfo } from '../api'
import TableConfigButton from './TableConfigButton.vue'
import UiIcon from './UiIcon.vue'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const rows = ref<MonitorOnlineUserInfo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const filters = reactive({ ipAddress: '', loginName: '' })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const {
  visible: confirmVisible,
  title: confirmTitle,
  message: confirmMessage,
  loading: confirmLoading,
  confirm: requestConfirm,
  handleConfirm,
  handleCancel
} = useDeleteConfirm()

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    const result = await fetchOnlineUsers(props.token, { ...filters, page: page.value, size: size.value })
    rows.value = result.items || []
    total.value = result.total || 0
    if (page.value > totalPages.value) page.value = totalPages.value
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '在线用户加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  filters.ipAddress = ''
  filters.loginName = ''
  search()
}

async function forceLogout(row: MonitorOnlineUserInfo) {
  const accepted = await requestConfirm({ title: '强制退出会话', message: `确定强制退出「${row.loginName}」的当前会话吗？` })
  confirmVisible.value = false
  if (!accepted) return
  try {
    await forceLogoutOnlineUser(props.token, row.id)
    emit('notice', `已强制退出 ${row.loginName}`)
    await load()
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '强退失败')
  }
}

function changePage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <section class="content monitor-page">
    <div class="monitor-page-header">
      <div>
        <h1>在线用户</h1>
        <p>查看当前工厂登录会话，并在需要时强制结束异常会话。</p>
      </div>
      <button class="toolbar-btn" type="button" title="刷新" @click="load">
        <UiIcon name="refresh" :size="15" />刷新
      </button>
    </div>

    <div class="filters monitor-filters">
      <label><span>登录地址</span><input v-model="filters.ipAddress" placeholder="请输入登录地址" @keydown.enter="search" /></label>
      <label><span>用户名称</span><input v-model="filters.loginName" placeholder="请输入用户名称" @keydown.enter="search" /></label>
      <div class="filter-actions">
        <button class="primary-btn query-btn" type="button" @click="search"><UiIcon name="search" :size="14" />查询</button>
        <button class="ghost-btn query-btn" type="button" @click="reset"><UiIcon name="reset" :size="14" />重置</button>
      </div>
    </div>

    <div class="monitor-section-caption">
      <div><span>在线会话</span><small>共 {{ total }} 条</small></div>
      <TableConfigButton table-id="online-users-table" />
    </div>
    <div class="table-container monitor-table-container">
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
            <td><button class="table-action danger" type="button" @click="forceLogout(row)"><UiIcon name="close" :size="14" />强退</button></td>
          </tr>
          <tr v-if="!loading && rows.length === 0"><td colspan="10" class="monitor-empty">暂无在线用户</td></tr>
          <tr v-if="loading"><td colspan="10" class="monitor-empty">加载中...</td></tr>
        </tbody>
      </table>
    </div>

    <div class="monitor-pagination">
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button type="button" :disabled="page === 1" @click="changePage(page - 1)"><UiIcon name="arrowLeft" :size="14" />上一页</button>
      <button type="button" :disabled="page === totalPages" @click="changePage(page + 1)">下一页<UiIcon name="arrowRight" :size="14" /></button>
    </div>

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
