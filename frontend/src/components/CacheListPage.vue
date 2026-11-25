<script setup lang="ts">
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { clearAllCache, clearCacheKey, clearCacheName, fetchCacheKeys, fetchCacheNames, fetchCacheRecoveries,
  fetchCacheValue, restoreCacheRecovery, type MonitorCacheNameInfo, type MonitorCacheRecoveryInfo } from '../api'
import UiIcon from './UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { formatUiDate, t } from '../i18n'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()
const cacheNames = ref<MonitorCacheNameInfo[]>([])
const cacheKeys = ref<string[]>([])
const recoveries = ref<MonitorCacheRecoveryInfo[]>([])
const selectedCacheName = ref('')
const selectedCacheKey = ref('')
const cacheValue = ref('')
const loadingNames = ref(false), loadingKeys = ref(false), loadingValue = ref(false), loadingRecoveries = ref(false)
const namesError = ref(''), keysError = ref(''), valueError = ref(''), recoveriesError = ref(''), mutationError = ref('')
const writing = ref(false)
const restoring = ref(false)
const beginNames = useLatestRequest(), beginKeys = useLatestRequest(), beginValue = useLatestRequest(), beginRecoveries = useLatestRequest()
const { visible: confirmVisible, title: confirmTitle, message: confirmMessage, loading: confirmLoading,
  confirm: requestConfirm, handleConfirm, handleCancel } = useDeleteConfirm()
let active = true, disposed = false, generation = 0
function context() {
  const token = props.token, epoch = generation
  return { token, current: () => active && !disposed && props.token === token && epoch === generation }
}
function invalidateReads() { beginNames(); beginKeys(); beginValue(); beginRecoveries() }
function clearSelection() { selectedCacheName.value = ''; selectedCacheKey.value = ''; cacheKeys.value = []; cacheValue.value = '' }
const formatDate = (value?: string) => value ? formatUiDate(value, true) : '-'
async function loadNames() {
  const request = context(), latest = beginNames()
  if (!request.token || !active) return
  loadingNames.value = true; namesError.value = ''
  try {
    const result = await fetchCacheNames(request.token)
    if (!request.current() || !latest()) return
    cacheNames.value = result
    if (selectedCacheName.value && !result.some(item => item.cacheName === selectedCacheName.value)) { beginKeys(); beginValue(); clearSelection() }
  } catch (error) {
    if (request.current() && latest()) { namesError.value = error instanceof Error ? error.message : '缓存列表加载失败'; emit('notice', namesError.value) }
  } finally { if (request.current() && latest()) loadingNames.value = false }
}
async function loadRecoveries() {
  const request = context(), latest = beginRecoveries()
  if (!request.token || !active) return
  loadingRecoveries.value = true; recoveriesError.value = ''
  try {
    const result = await fetchCacheRecoveries(request.token)
    if (request.current() && latest()) recoveries.value = result
  } catch (error) {
    if (request.current() && latest()) { recoveriesError.value = error instanceof Error ? error.message : '恢复记录加载失败'; emit('notice', recoveriesError.value) }
  } finally { if (request.current() && latest()) loadingRecoveries.value = false }
}
async function refresh() { await Promise.all([loadNames(), loadRecoveries()]) }
async function loadKeys(name: string) {
  const request = context(), latest = beginKeys()
  const current = () => request.current() && latest() && selectedCacheName.value === name
  if (!request.token || !name || !active) return
  loadingKeys.value = true; keysError.value = ''
  try {
    const result = await fetchCacheKeys(request.token, name)
    if (!current()) return
    cacheKeys.value = result
    if (selectedCacheKey.value && !result.includes(selectedCacheKey.value)) { beginValue(); selectedCacheKey.value = ''; cacheValue.value = ''; valueError.value = '' }
  } catch (error) {
    if (current()) { keysError.value = error instanceof Error ? error.message : '缓存键加载失败'; emit('notice', keysError.value) }
  } finally { if (current()) loadingKeys.value = false }
}
async function selectCache(name: string) {
  if (writing.value || confirmVisible.value) return
  if (name !== selectedCacheName.value) {
    beginValue(); cacheKeys.value = []; selectedCacheKey.value = ''; cacheValue.value = ''; valueError.value = ''; loadingValue.value = false
  }
  selectedCacheName.value = name
  await loadKeys(name)
}
async function selectKey(key: string) {
  if (!selectedCacheName.value || writing.value || confirmVisible.value || loadingKeys.value || keysError.value) return
  const request = context(), latest = beginValue(), name = selectedCacheName.value
  if (key !== selectedCacheKey.value) cacheValue.value = ''
  selectedCacheKey.value = key
  const current = () => request.current() && latest() && selectedCacheName.value === name && selectedCacheKey.value === key
  loadingValue.value = true; valueError.value = ''
  try {
    const result = await fetchCacheValue(request.token, name, key)
    if (current()) cacheValue.value = result.cacheValue ?? ''
  } catch (error) {
    if (current()) { valueError.value = error instanceof Error ? error.message : '缓存内容加载失败'; emit('notice', valueError.value) }
  } finally { if (current()) loadingValue.value = false }
}
async function mutate(title: string, message: string, action: (token: string) => Promise<{ affected: number; recoveryWindowMinutes?: number }>,
  notice: (result: { affected: number; recoveryWindowMinutes?: number }) => string, done: () => void = () => {}) {
  if (writing.value || confirmVisible.value || !props.token) return
  restoring.value = title === '恢复缓存'
  const request = context()
  const accepted = await requestConfirm({ title, message })
  if (!accepted || !request.current()) { if (request.current()) handleCancel(); return }
  writing.value = true; confirmLoading.value = true; mutationError.value = ''
  beginKeys(); beginValue(); loadingKeys.value = false; loadingValue.value = false
  try {
    const result = await action(request.token)
    if (!request.current()) return
    done(); emit('notice', notice(result))
    await refresh()
    if (request.current() && selectedCacheName.value) await loadKeys(selectedCacheName.value)
  } catch (error) {
    if (request.current()) { mutationError.value = error instanceof Error ? error.message : '缓存操作失败'; emit('notice', mutationError.value) }
  } finally { if (request.current()) { writing.value = false; confirmLoading.value = false; handleCancel() } }
}
function clearName(item: MonitorCacheNameInfo) {
  const name = item.cacheName
  return mutate('清理缓存', `确定清理缓存「${name}」吗？清理后可在恢复窗口内撤销。`, token => clearCacheName(token, name),
    result => `已清理 ${result.affected} 个键，${result.recoveryWindowMinutes} 分钟内可恢复`, () => {
      if (selectedCacheName.value === name) { selectedCacheKey.value = ''; cacheValue.value = ''; valueError.value = '' }
    })
}
function clearKey(key: string) {
  const name = selectedCacheName.value
  if (!name || loadingKeys.value || keysError.value || !cacheKeys.value.includes(key)) return
  return mutate('清理缓存键', `确定清理缓存键「${key}」吗？`, token => clearCacheKey(token, name, key),
    result => `已清理缓存键，${result.recoveryWindowMinutes} 分钟内可恢复`, () => {
      if (selectedCacheName.value === name && selectedCacheKey.value === key) { selectedCacheKey.value = ''; cacheValue.value = ''; valueError.value = '' }
    })
}
function clearEverything() {
  return mutate('清理全部缓存', '确定清理当前工厂的全部监控缓存吗？清理后可在恢复窗口内撤销。', clearAllCache,
    result => `已清理 ${result.affected} 个键，${result.recoveryWindowMinutes} 分钟内可恢复`, clearSelection)
}
function restore(item: MonitorCacheRecoveryInfo) {
  const id = item.id
  return mutate('恢复缓存', `恢复「${item.cacheName}${item.cacheKey ? ` / ${item.cacheKey}` : ''}」吗？如果键已生成新值，系统会阻止覆盖。`,
    token => restoreCacheRecovery(token, id), result => `已恢复 ${result.affected} 个缓存键`)
}
function cancelContext() {
  generation++; invalidateReads(); handleCancel(); writing.value = false; confirmLoading.value = false
  loadingNames.value = false; loadingKeys.value = false; loadingValue.value = false; loadingRecoveries.value = false
}
onMounted(refresh)
onActivated(() => { active = true })
useRefreshOnActivated(refresh)
onDeactivated(() => { active = false; cancelContext() })
watch(() => props.token, () => {
  cancelContext(); clearSelection(); cacheNames.value = []; recoveries.value = []
  namesError.value = ''; keysError.value = ''; valueError.value = ''; recoveriesError.value = ''; mutationError.value = ''
  void refresh()
}, { flush: 'sync' })
onBeforeUnmount(() => { disposed = true; active = false; cancelContext() })
</script>

<template>
  <section class="content monitor-page cache-list-page">
    <div class="monitor-page-header">
      <div><h1>缓存列表</h1><p>清理后可在恢复期限内还原。</p></div>
      <button class="toolbar-btn ui-icon-button" type="button" :disabled="writing" title="刷新缓存列表" @click="refresh" aria-label="刷新缓存列表"><UiIcon name="refresh" /></button>
    </div>

    <p v-if="mutationError" role="alert">{{ mutationError }}</p>

    <div class="cache-list-layout">
      <section class="monitor-panel cache-column">
        <div class="monitor-panel-head"><div><UiIcon name="list" :size="16" /><strong>缓存列表</strong></div><span class="monitor-panel-actions"><TableConfigButton table-id="cache-names-table" /><button class="icon-btn ui-icon-button" title="刷新缓存名称" type="button" @click="loadNames" aria-label="刷新缓存名称"><UiIcon name="refresh" :size="14" /></button></span></div>
        <div class="monitor-inner-table">
          <p v-if="namesError" role="alert">{{ namesError }} <button class="ui-icon-button" type="button" @click="loadNames" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
          <table id="cache-names-table" class="data-table compact-table"><thead><tr><th>序号</th><th>缓存名称</th><th>备注</th><th>数量</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(item, index) in cacheNames" :key="item.cacheName" :class="{ selected: selectedCacheName === item.cacheName }" @click="selectCache(item.cacheName)">
                <td>{{ index + 1 }}</td><td class="strong-text"><button class="cache-select-btn" type="button" :disabled="writing || confirmVisible" :aria-pressed="selectedCacheName === item.cacheName" @click.stop="selectCache(item.cacheName)">{{ item.cacheName }}</button></td><td>{{ item.remark }}</td><td>{{ item.keyCount }}</td>
                <td><button class="icon-btn danger ui-icon-button" :disabled="writing || confirmVisible" title="清理缓存名称" type="button" @click.stop="clearName(item)" aria-label="清理缓存名称"><UiIcon name="trash" :size="14" /></button></td>
              </tr>
              <tr v-if="!loadingNames && !namesError && cacheNames.length === 0"><td colspan="5" class="monitor-empty">暂无缓存</td></tr>
              <tr v-if="loadingNames"><td colspan="5" class="monitor-empty" role="status">加载中...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="monitor-panel cache-column">
        <div class="monitor-panel-head"><div><UiIcon name="layers" :size="16" /><strong>键名列表</strong></div><span class="monitor-panel-actions"><TableConfigButton table-id="cache-keys-table" /><button class="icon-btn ui-icon-button" title="刷新缓存键" type="button" :disabled="!selectedCacheName || writing || confirmVisible" @click="selectCache(selectedCacheName)" aria-label="刷新缓存键"><UiIcon name="refresh" :size="14" /></button></span></div>
        <div class="selected-cache-hint">{{ selectedCacheName || t('请先选择缓存名称') }}</div>
        <div class="monitor-inner-table">
          <p v-if="keysError" role="alert">{{ keysError }} <button class="ui-icon-button" type="button" :disabled="writing" @click="loadKeys(selectedCacheName)" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
          <table id="cache-keys-table" class="data-table compact-table"><thead><tr><th>序号</th><th>缓存键名</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(key, index) in cacheKeys" :key="key" :class="{ selected: selectedCacheKey === key }" @click="selectKey(key)">
                <td>{{ index + 1 }}</td><td class="mono-text"><button class="cache-select-btn" type="button" :disabled="writing || confirmVisible || loadingKeys || !!keysError" :aria-pressed="selectedCacheKey === key" @click.stop="selectKey(key)">{{ key }}</button></td><td><button class="icon-btn danger ui-icon-button" :disabled="writing || confirmVisible || loadingKeys || !!keysError" title="清理缓存键" type="button" @click.stop="clearKey(key)" aria-label="清理缓存键"><UiIcon name="trash" :size="14" /></button></td>
              </tr>
              <tr v-if="!loadingKeys && !keysError && cacheKeys.length === 0"><td colspan="3" class="monitor-empty">{{ selectedCacheName ? '暂无缓存键' : '选择左侧缓存名称' }}</td></tr>
              <tr v-if="loadingKeys"><td colspan="3" class="monitor-empty" role="status">加载中...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="monitor-panel cache-column cache-value-column">
        <div class="monitor-panel-head"><div><UiIcon name="file" :size="16" /><strong>缓存内容</strong></div><button class="text-icon-btn" :disabled="writing || confirmVisible" type="button" @click="clearEverything"><UiIcon name="trash" :size="14" />清理全部</button></div>
        <p v-if="valueError" role="alert">{{ valueError }} <button class="ui-icon-button" type="button" :disabled="writing" @click="selectKey(selectedCacheKey)" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
        <div class="cache-value-form">
          <label>缓存名称<input :value="selectedCacheName" readonly placeholder="请选择缓存名称" /></label>
          <label>缓存键名<input :value="selectedCacheKey" readonly placeholder="请选择缓存键名" /></label>
          <label>缓存内容<textarea v-model="cacheValue" readonly :placeholder="loadingValue ? '加载中...' : '请选择缓存键名'" /></label>
        </div>
      </section>
    </div>

    <section class="monitor-panel recovery-panel">
      <div class="monitor-panel-head"><div><UiIcon name="clock" :size="16" /><strong>最近清理，可恢复</strong></div><small>超过恢复窗口后自动失效</small></div>
      <p v-if="recoveriesError" role="alert">{{ recoveriesError }} <button class="ui-icon-button" type="button" :disabled="writing" @click="loadRecoveries" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
      <div v-if="loadingRecoveries" class="monitor-panel-loading" role="status">加载中...</div>
      <div v-else-if="!recoveriesError && recoveries.length === 0" class="recovery-empty">暂无可恢复记录</div>
      <div v-else class="recovery-list">
        <div v-for="item in recoveries" :key="item.id" class="recovery-row">
          <div><strong>{{ item.cacheName }}</strong><span v-if="item.cacheKey" class="mono-text"> / {{ item.cacheKey }}</span></div>
          <span>{{ item.entryCount }} 个键</span><span>{{ formatDate(item.deletedAt) }} 清理</span><span>截止 {{ formatDate(item.expiresAt) }}</span>
          <button class="primary-btn small-btn" type="button" :disabled="writing || confirmVisible" @click="restore(item)" title="恢复" aria-label="恢复"><UiIcon name="reset" />恢复</button>
        </div>
      </div>
    </section>

    <DeleteConfirmDialog
      v-model:visible="confirmVisible"
      :title="confirmTitle"
      :message="confirmMessage"
      :loading="confirmLoading"
      :confirm-label="restoring ? t('恢复缓存') : t('清理缓存')"
      :confirm-icon="restoring ? 'reset' : 'trash'"
      :confirm-tone="restoring ? 'primary' : 'danger'"
      @confirm="handleConfirm"
      @update:visible="(value: boolean) => { if (!value) handleCancel() }"
    />
  </section>
</template>

<style scoped>
.cache-select-btn { border: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; text-align: left; min-height: 36px; max-width: 100%; overflow-wrap: anywhere; }
.cache-select-btn:focus-visible { outline: 2px solid var(--color-primary, #007a60); outline-offset: 2px; }
.selected-cache-hint, .recovery-row strong, .recovery-row .mono-text { overflow-wrap: anywhere; }
.cache-list-page [role="alert"] { padding: 8px 12px; overflow-wrap: anywhere; }
</style>
