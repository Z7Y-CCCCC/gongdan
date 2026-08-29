<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  clearAllCache,
  clearCacheKey,
  clearCacheName,
  fetchCacheKeys,
  fetchCacheNames,
  fetchCacheRecoveries,
  fetchCacheValue,
  restoreCacheRecovery,
  type MonitorCacheNameInfo,
  type MonitorCacheRecoveryInfo
} from '../api'
import UiIcon from './UiIcon.vue'
import TableConfigButton from './TableConfigButton.vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const cacheNames = ref<MonitorCacheNameInfo[]>([])
const cacheKeys = ref<string[]>([])
const recoveries = ref<MonitorCacheRecoveryInfo[]>([])
const selectedCacheName = ref('')
const selectedCacheKey = ref('')
const cacheValue = ref('')
const loadingNames = ref(false)
const loadingKeys = ref(false)
const loadingValue = ref(false)
const loadingRecoveries = ref(false)

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function loadNames() {
  loadingNames.value = true
  try {
    cacheNames.value = await fetchCacheNames(props.token)
    await loadRecoveries()
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '缓存列表加载失败')
  } finally {
    loadingNames.value = false
  }
}

async function loadRecoveries() {
  loadingRecoveries.value = true
  try {
    recoveries.value = await fetchCacheRecoveries(props.token)
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '恢复记录加载失败')
  } finally {
    loadingRecoveries.value = false
  }
}

async function selectCache(name: string) {
  selectedCacheName.value = name
  selectedCacheKey.value = ''
  cacheValue.value = ''
  loadingKeys.value = true
  try {
    cacheKeys.value = await fetchCacheKeys(props.token, name)
  } catch (error) {
    cacheKeys.value = []
    emit('notice', error instanceof Error ? error.message : '缓存键加载失败')
  } finally {
    loadingKeys.value = false
  }
}

async function selectKey(key: string) {
  if (!selectedCacheName.value) return
  selectedCacheKey.value = key
  loadingValue.value = true
  try {
    const result = await fetchCacheValue(props.token, selectedCacheName.value, key)
    cacheValue.value = result.cacheValue || ''
  } catch (error) {
    cacheValue.value = ''
    emit('notice', error instanceof Error ? error.message : '缓存内容加载失败')
  } finally {
    loadingValue.value = false
  }
}

async function clearName(item: MonitorCacheNameInfo) {
  if (!window.confirm(`确定清理缓存「${item.cacheName}」吗？清理后可在恢复窗口内撤销。`)) return
  try {
    const result = await clearCacheName(props.token, item.cacheName)
    emit('notice', `已清理 ${result.affected} 个键，${result.recoveryWindowMinutes} 分钟内可恢复`)
    if (selectedCacheName.value === item.cacheName) {
      selectedCacheKey.value = ''
      cacheValue.value = ''
    }
    await loadNames()
    if (selectedCacheName.value === item.cacheName) await selectCache(item.cacheName)
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '清理缓存失败')
  }
}

async function clearKey(key: string) {
  if (!selectedCacheName.value) return
  if (!window.confirm(`确定清理缓存键「${key}」吗？`)) return
  try {
    const result = await clearCacheKey(props.token, selectedCacheName.value, key)
    emit('notice', `已清理缓存键，${result.recoveryWindowMinutes} 分钟内可恢复`)
    await selectCache(selectedCacheName.value)
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '清理缓存键失败')
  }
}

async function clearEverything() {
  if (!window.confirm('确定清理当前工厂的全部监控缓存吗？清理后可在恢复窗口内撤销。')) return
  try {
    const result = await clearAllCache(props.token)
    emit('notice', `已清理 ${result.affected} 个键，${result.recoveryWindowMinutes} 分钟内可恢复`)
    selectedCacheName.value = ''
    selectedCacheKey.value = ''
    cacheValue.value = ''
    await loadNames()
    cacheKeys.value = []
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '清理全部缓存失败')
  }
}

async function restore(item: MonitorCacheRecoveryInfo) {
  if (!window.confirm(`恢复「${item.cacheName}${item.cacheKey ? ` / ${item.cacheKey}` : ''}」吗？如果键已生成新值，系统会阻止覆盖。`)) return
  try {
    const result = await restoreCacheRecovery(props.token, item.id)
    emit('notice', `已恢复 ${result.affected} 个缓存键`)
    await loadNames()
    if (selectedCacheName.value) await selectCache(selectedCacheName.value)
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '缓存恢复失败')
  }
}

onMounted(loadNames)
</script>

<template>
  <section class="content monitor-page cache-list-page">
    <div class="monitor-page-header">
      <div><h1>缓存列表</h1><p>按缓存名称查看键和值，清理操作会保留短时恢复记录。</p></div>
      <button class="toolbar-btn" type="button" title="刷新缓存列表" @click="loadNames"><UiIcon name="refresh" :size="15" />刷新</button>
    </div>

    <div class="cache-list-layout">
      <section class="monitor-panel cache-column">
        <div class="monitor-panel-head"><div><UiIcon name="list" :size="16" /><strong>缓存列表</strong></div><span class="monitor-panel-actions"><TableConfigButton table-id="cache-names-table" /><button class="icon-btn" title="刷新缓存名称" type="button" @click="loadNames"><UiIcon name="refresh" :size="14" /></button></span></div>
        <div class="monitor-inner-table">
          <table id="cache-names-table" class="data-table compact-table"><thead><tr><th>序号</th><th>缓存名称</th><th>备注</th><th>数量</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(item, index) in cacheNames" :key="item.cacheName" :class="{ selected: selectedCacheName === item.cacheName }" @click="selectCache(item.cacheName)">
                <td>{{ index + 1 }}</td><td class="strong-text">{{ item.cacheName }}</td><td>{{ item.remark }}</td><td>{{ item.keyCount }}</td>
                <td><button class="icon-btn danger" title="清理缓存名称" type="button" @click.stop="clearName(item)"><UiIcon name="trash" :size="14" /></button></td>
              </tr>
              <tr v-if="!loadingNames && cacheNames.length === 0"><td colspan="5" class="monitor-empty">暂无缓存</td></tr>
              <tr v-if="loadingNames"><td colspan="5" class="monitor-empty">加载中...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="monitor-panel cache-column">
        <div class="monitor-panel-head"><div><UiIcon name="layers" :size="16" /><strong>键名列表</strong></div><span class="monitor-panel-actions"><TableConfigButton table-id="cache-keys-table" /><button class="icon-btn" title="刷新缓存键" type="button" :disabled="!selectedCacheName" @click="selectCache(selectedCacheName)"><UiIcon name="refresh" :size="14" /></button></span></div>
        <div class="selected-cache-hint">{{ selectedCacheName || '请先选择缓存名称' }}</div>
        <div class="monitor-inner-table">
          <table id="cache-keys-table" class="data-table compact-table"><thead><tr><th>序号</th><th>缓存键名</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(key, index) in cacheKeys" :key="key" :class="{ selected: selectedCacheKey === key }" @click="selectKey(key)">
                <td>{{ index + 1 }}</td><td class="mono-text">{{ key }}</td><td><button class="icon-btn danger" title="清理缓存键" type="button" @click.stop="clearKey(key)"><UiIcon name="trash" :size="14" /></button></td>
              </tr>
              <tr v-if="!loadingKeys && cacheKeys.length === 0"><td colspan="3" class="monitor-empty">{{ selectedCacheName ? '暂无缓存键' : '选择左侧缓存名称' }}</td></tr>
              <tr v-if="loadingKeys"><td colspan="3" class="monitor-empty">加载中...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="monitor-panel cache-column cache-value-column">
        <div class="monitor-panel-head"><div><UiIcon name="file" :size="16" /><strong>缓存内容</strong></div><button class="text-icon-btn" type="button" @click="clearEverything"><UiIcon name="trash" :size="14" />清理全部</button></div>
        <div class="cache-value-form">
          <label>缓存名称<input :value="selectedCacheName" readonly placeholder="请选择缓存名称" /></label>
          <label>缓存键名<input :value="selectedCacheKey" readonly placeholder="请选择缓存键名" /></label>
          <label>缓存内容<textarea v-model="cacheValue" readonly :placeholder="loadingValue ? '加载中...' : '请选择缓存键名'" /></label>
        </div>
      </section>
    </div>

    <section class="monitor-panel recovery-panel">
      <div class="monitor-panel-head"><div><UiIcon name="clock" :size="16" /><strong>最近清理，可恢复</strong></div><small>超过恢复窗口后自动失效</small></div>
      <div v-if="loadingRecoveries" class="monitor-panel-loading">加载中...</div>
      <div v-else-if="recoveries.length === 0" class="recovery-empty">暂无可恢复记录</div>
      <div v-else class="recovery-list">
        <div v-for="item in recoveries" :key="item.id" class="recovery-row">
          <div><strong>{{ item.cacheName }}</strong><span v-if="item.cacheKey" class="mono-text"> / {{ item.cacheKey }}</span></div>
          <span>{{ item.entryCount }} 个键</span><span>{{ formatDate(item.deletedAt) }} 清理</span><span>截止 {{ formatDate(item.expiresAt) }}</span>
          <button class="primary-btn small-btn" type="button" @click="restore(item)"><UiIcon name="reset" :size="14" />恢复</button>
        </div>
      </div>
    </section>
  </section>
</template>
