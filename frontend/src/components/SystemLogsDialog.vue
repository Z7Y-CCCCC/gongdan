<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchSystemImportLogs, fetchSystemOperationLogs } from '../api'
import TableConfigButton from './TableConfigButton.vue'
import UiIcon from './UiIcon.vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useDialogFocus } from '../composables/useDialogFocus'
import { formatUiDate } from '../i18n'
import { workspaceDialogMotion } from '../workspaceDialogMotion'
const props = defineProps<{ visible: boolean; token: string; module: string; type: 'import' | 'operation'; title: string }>()
const emit = defineEmits<{ (e: 'update:visible', visible: boolean): void }>()
const loading = ref(false), errorMessage = ref(''), currentPage = ref(1), pageSize = ref(15), totalItems = ref(0)
const logs = ref<any[]>([]), dialogRef = ref<HTMLElement | null>(null)
const titleId = 'system-logs-title-' + Math.random().toString(36).slice(2, 10)
const beginLoad = useLatestRequest()
let attemptedPage = 1
function close() { beginLoad(); loading.value = false; emit('update:visible', false) }
useDialogFocus(dialogRef, () => props.visible, close)
async function loadLogs(targetPage = currentPage.value) {
  const token = props.token, module = props.module, type = props.type, latest = beginLoad(), size = pageSize.value
  const current = () => latest() && props.visible && props.token === token && props.module === module && props.type === type
  if (!props.visible || !token || !module || !Number.isInteger(targetPage) || targetPage < 1) return
  loading.value = true; errorMessage.value = ''; attemptedPage = targetPage
  try {
    for (;;) {
      const res = type === 'import' ? await fetchSystemImportLogs(token, module, targetPage, size)
        : await fetchSystemOperationLogs(token, module, targetPage, size)
      if (!current()) return
      const maximum = Math.max(1, Math.ceil(res.total / size))
      if (res.total > 0 && targetPage > maximum) { targetPage = maximum; attemptedPage = maximum; continue }
      logs.value = type === 'import' ? res.items.map((item: any) => ({ id: String(item.id), fileName: item.fileName,
        time: item.createdAt, count: item.totalCount, successCount: item.successCount, failCount: item.failCount,
        status: item.status, operator: item.operatorName, errors: item.errors ? item.errors.split('\n') : [] })) : res.items
      totalItems.value = res.total; currentPage.value = res.total === 0 ? 1 : targetPage
      return
    }
  } catch (error) {
    if (current()) errorMessage.value = error instanceof Error ? error.message : '日志加载失败'
  } finally { if (current()) loading.value = false }
}
function changePage(page: number) {
  if (!loading.value && !errorMessage.value && page >= 1 && page <= Math.max(1, Math.ceil(totalItems.value / pageSize.value))) void loadLogs(page)
}
watch([() => props.visible, () => props.token, () => props.module, () => props.type], () => {
  beginLoad(); loading.value = false; errorMessage.value = ''; logs.value = []; totalItems.value = 0; currentPage.value = 1; attemptedPage = 1
  if (props.visible) void loadLogs(1)
}, { immediate: true, flush: 'sync' })
</script>

<template>
  <Transition v-bind="workspaceDialogMotion">
  <div v-if="visible" class="import-logs-overlay workspace-dialog-motion-backdrop" @click.self="close">
    <div ref="dialogRef" class="import-logs-modal workspace-dialog-motion-panel" role="dialog" aria-modal="true" tabindex="-1" :aria-labelledby="titleId" :style="{ width: type === 'operation' ? 'min(600px, calc(100vw - 32px))' : 'min(900px, calc(100vw - 32px))' }">
      <div class="modal-header">
        <h3 :id="titleId">{{ title }}</h3>
        <div class="modal-header-actions">
          <TableConfigButton v-if="type === 'import'" :table-id="`system-import-logs-${module}`" />
          <button class="close-btn ui-icon-button" type="button" title="关闭" aria-label="关闭" @click="close">
            <UiIcon name="close" :size="18" />
          </button>
        </div>
      </div>
      
      <div class="modal-body" :style="{ maxHeight: '500px', overflowY: 'auto', padding: '20px 24px 24px', position: 'relative' }">
        <p v-if="loading" role="status">加载中...</p>
        <p v-if="errorMessage" role="alert">{{ errorMessage }} <button class="ui-icon-button" type="button" @click="loadLogs(attemptedPage)" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>

        <!-- 导入日志类型 -->
        <template v-if="type === 'import'">
          <table :id="`system-import-logs-${module}`" class="modal-table" :aria-busy="loading" style="width:100%; border-collapse:collapse;">
            <thead>
              <tr>
                <th style="width:70px">ID</th>
                <th>文件名</th>
                <th style="width:160px">导入时间</th>
                <th style="width:100px">导入条数</th>
                <th style="width:100px">导入状态</th>
                <th style="width:100px">操作者</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="log in logs" :key="log.id">
                <tr>
                  <td class="log-id">{{ log.id }}</td>
                  <td style="word-break:break-all">{{ log.fileName }}</td>
                  <td>{{ formatUiDate(log.time, true) }}</td>
                  <td>{{ log.count }} 条</td>
                  <td>
                    <span :class="['status-badge', log.status === '成功' ? 'success' : log.status === '部分成功' ? 'warning' : 'danger']">
                      {{ log.status }}
                    </span>
                  </td>
                  <td>{{ log.operator }}</td>
                </tr>
                <tr v-if="log.errors && log.errors.length > 0">
                  <td colspan="6" class="log-error-details">
                    <div style="font-weight: 600; margin-bottom: 4px;">失败原因：</div>
                    <ul style="margin: 0; padding-left: 18px; list-style-type: decimal;">
                      <li v-for="(errMsg, eIdx) in log.errors" :key="eIdx">{{ errMsg }}</li>
                    </ul>
                  </td>
                </tr>
              </template>
              <tr v-if="logs.length === 0 && !loading && !errorMessage">
                <td colspan="6" style="text-align:center;color:#aaa;padding:40px 0">暂无导入记录</td>
              </tr>
            </tbody>
          </table>
        </template>

        <!-- 操作记录时间轴类型 -->
        <template v-else>
          <div v-if="logs.length === 0 && !loading && !errorMessage" style="text-align: center; color: #8a9693; padding: 48px 0;">暂无记录</div>
          <div v-else style="display: flex; flex-direction: column; gap: 20px; position: relative; padding-left: 8px;">
            <!-- 纵向时间轴线 -->
            <div style="position: absolute; left: 23px; top: 8px; bottom: 8px; width: 2px; background: #e5ebe9; z-index: 1;"></div>
            
            <div v-for="(item, index) in logs" :key="index" style="display: flex; gap: 16px; position: relative; z-index: 2;">
              <!-- 时间点圆圈 -->
              <div style="width: 32px; height: 32px; border-radius: 50%; background: #ffffff; border: 2px solid #00b889; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 2px 6px rgba(0,184,137,0.15);">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="#00b889" stroke-width="2.5" fill="none"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              </div>
              
              <!-- 内容卡片 -->
              <div class="logs-timeline-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                  <span class="logs-operator">{{ item.operatorName }}</span>
                  <span class="logs-time">{{ formatUiDate(item.createdAt, true) }}</span>
                </div>
                <div class="logs-message">{{ item.message }}</div>
              </div>
            </div>
          </div>
        </template>
        
        <!-- 分页控件栏 -->
        <div v-if="totalItems > pageSize" class="pagination-bar" style="display:flex; justify-content:space-between; align-items:center; margin-top:20px; padding-top:16px; border-top:1px solid #f0f4f3; z-index:10; position:relative;">
          <span style="font-size:13px; color:#5c6764">{{ totalItems }} 条 · {{ currentPage }}/{{ Math.max(1, Math.ceil(totalItems / pageSize)) }}</span>
          <div style="display:flex; gap:8px">
            <button class="ghost-btn ui-icon-button" type="button" title="上一页" aria-label="上一页" :disabled="currentPage <= 1 || loading || !!errorMessage" @click="changePage(currentPage - 1)"><UiIcon name="arrowLeft" /></button>
            <button class="ghost-btn ui-icon-button" type="button" title="下一页" aria-label="下一页" :disabled="currentPage * pageSize >= totalItems || loading || !!errorMessage" @click="changePage(currentPage + 1)"><UiIcon name="arrowRight" /></button>
          </div>
        </div>
      </div>
    </div>
  </div>
  </Transition>
</template>

<style scoped>
.modal-table { min-width: 560px; }
.import-logs-modal .modal-body { overflow: auto; }
.log-error-details { padding: 10px 16px; background: #fff8f8; color: #a52a25; font-size: 12.5px; border-bottom: 1px solid #f0d8d8; text-align: left; }
.logs-timeline-card { flex: 1; min-width: 0; background: #f7faf9; padding: 12px 16px; border-radius: 8px; border: 1px solid #eef2f1; text-align: left; }
.logs-operator { font-weight: 600; font-size: 13px; color: #1e2724; overflow-wrap: anywhere; }
.logs-time { font-size: 11px; color: #64736d; }
.logs-message { font-size: 13px; color: #586360; line-height: 1.5; overflow-wrap: anywhere; }
:global(.dark) .logs-timeline-card { background: #1b2723; border-color: #344641; }
:global(.dark) .logs-operator, :global(.dark) .logs-message { color: #dfebe5; }
:global(.dark) .logs-time { color: #b0c0b8; }
:global(.dark) .log-error-details { background: #30201f; color: #ffb8b2; border-color: #6c3e3e; }
</style>
