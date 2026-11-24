<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchSystemImportLogs, fetchSystemOperationLogs } from '../api'
import TableConfigButton from './TableConfigButton.vue'
import UiIcon from './UiIcon.vue'

const props = defineProps<{
  visible: boolean
  token: string
  module: string
  type: 'import' | 'operation'
  title: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
}>()

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(15) // 每页展示 15 条以实现流畅性能
const totalItems = ref(0)
const logs = ref<any[]>([])

async function loadLogs() {
  loading.value = true
  try {
    if (props.type === 'import') {
      const res = await fetchSystemImportLogs(props.token, props.module, currentPage.value, pageSize.value)
      logs.value = res.items.map(item => ({
        id: String(item.id),
        fileName: item.fileName,
        time: item.createdAt,
        count: item.totalCount,
        successCount: item.successCount,
        failCount: item.failCount,
        status: item.status,
        operator: item.operatorName,
        errors: item.errors ? item.errors.split('\n') : []
      }))
      totalItems.value = res.total || 0
    } else {
      const res = await fetchSystemOperationLogs(props.token, props.module, currentPage.value, pageSize.value)
      logs.value = res.items || []
      totalItems.value = res.total || 0
    }
  } catch (e) {
    console.error('获取日志失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (newVal) => {
  if (newVal) {
    currentPage.value = 1
    loadLogs()
  }
})

watch(currentPage, () => {
  loadLogs()
})
</script>

<template>
  <div v-if="visible" class="import-logs-overlay" @click.self="emit('update:visible', false)">
    <div class="import-logs-modal" :style="{ width: type === 'operation' ? '600px' : '900px' }">
      <div class="modal-header">
        <h3>{{ title }}</h3>
        <div class="modal-header-actions">
          <TableConfigButton v-if="type === 'import'" :table-id="`system-import-logs-${module}`" />
          <button class="close-btn ui-icon-button" type="button" title="关闭" aria-label="关闭" @click="emit('update:visible', false)">
            <UiIcon name="close" :size="18" />
          </button>
        </div>
      </div>
      
      <div class="modal-body" :style="{ maxHeight: '500px', overflowY: 'auto', padding: '20px 24px 24px', position: 'relative' }">
        <!-- 加载遮罩 -->
        <div v-if="loading" style="position:absolute; top:0; left:0; right:0; bottom:0; background:rgba(255,255,255,0.7); backdrop-filter:blur(1px); display:flex; align-items:center; justify-content:center; z-index:100;">
          <div class="import-spinner" style="width:32px; height:32px; border:3px solid #f3f3f3; border-top:3px solid #00b889; border-radius:50%; animation:spin 1s linear infinite;"></div>
        </div>

        <!-- 导入日志类型 -->
        <template v-if="type === 'import'">
          <table :id="`system-import-logs-${module}`" class="modal-table" style="width:100%; border-collapse:collapse;">
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
                  <td>{{ log.time }}</td>
                  <td>{{ log.count }} 条</td>
                  <td>
                    <span :class="['status-badge', log.status === '成功' ? 'success' : log.status === '部分成功' ? 'warning' : 'danger']">
                      {{ log.status }}
                    </span>
                  </td>
                  <td>{{ log.operator }}</td>
                </tr>
                <tr v-if="log.errors && log.errors.length > 0">
                  <td colspan="6" style="padding: 10px 16px; background-color: #fff8f8; color: #d32f2f; font-size: 12.5px; border-bottom: 1px solid #f0d8d8; text-align: left;">
                    <div style="font-weight: 600; margin-bottom: 4px;">失败原因：</div>
                    <ul style="margin: 0; padding-left: 18px; list-style-type: decimal;">
                      <li v-for="(errMsg, eIdx) in log.errors" :key="eIdx">{{ errMsg }}</li>
                    </ul>
                  </td>
                </tr>
              </template>
              <tr v-if="logs.length === 0 && !loading">
                <td colspan="6" style="text-align:center;color:#aaa;padding:40px 0">暂无导入记录</td>
              </tr>
            </tbody>
          </table>
        </template>

        <!-- 操作记录时间轴类型 -->
        <template v-else>
          <div v-if="logs.length === 0 && !loading" style="text-align: center; color: #8a9693; padding: 48px 0;">暂无记录</div>
          <div v-else style="display: flex; flex-direction: column; gap: 20px; position: relative; padding-left: 8px;">
            <!-- 纵向时间轴线 -->
            <div style="position: absolute; left: 23px; top: 8px; bottom: 8px; width: 2px; background: #e5ebe9; z-index: 1;"></div>
            
            <div v-for="(item, index) in logs" :key="index" style="display: flex; gap: 16px; position: relative; z-index: 2;">
              <!-- 时间点圆圈 -->
              <div style="width: 32px; height: 32px; border-radius: 50%; background: #ffffff; border: 2px solid #00b889; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 2px 6px rgba(0,184,137,0.15);">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="#00b889" stroke-width="2.5" fill="none"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              </div>
              
              <!-- 内容卡片 -->
              <div style="flex: 1; background: #f7faf9; padding: 12px 16px; border-radius: 8px; border: 1px solid #eef2f1; text-align: left;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                  <span style="font-weight: 600; font-size: 13px; color: #1e2724;">{{ item.operatorName }}</span>
                  <span style="font-size: 11px; color: #8a9693;">{{ item.createdAt }}</span>
                </div>
                <div style="font-size: 13px; color: #586360; line-height: 1.5; word-break: break-all;">{{ item.message }}</div>
              </div>
            </div>
          </div>
        </template>
        
        <!-- 分页控件栏 -->
        <div v-if="totalItems > pageSize" class="pagination-bar" style="display:flex; justify-content:space-between; align-items:center; margin-top:20px; padding-top:16px; border-top:1px solid #f0f4f3; z-index:10; position:relative;">
          <span style="font-size:13px; color:#5c6764">{{ totalItems }} 条 · {{ currentPage }}/{{ Math.max(1, Math.ceil(totalItems / pageSize)) }}</span>
          <div style="display:flex; gap:8px">
            <button class="ghost-btn ui-icon-button" type="button" title="上一页" aria-label="上一页" :disabled="currentPage <= 1 || loading" @click="currentPage--"><UiIcon name="arrowLeft" /></button>
            <button class="ghost-btn ui-icon-button" type="button" title="下一页" aria-label="下一页" :disabled="currentPage * pageSize >= totalItems || loading" @click="currentPage++"><UiIcon name="arrowRight" /></button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
